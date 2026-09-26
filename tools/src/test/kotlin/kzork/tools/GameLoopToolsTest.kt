package kzork.tools

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class GameLoopToolsTest {
    private lateinit var root: Path
    private lateinit var gm: GameLoopTools
    private lateinit var updater: GameLoopTools

    @BeforeTest fun setup() {
        root = Files.createTempDirectory("ice-dungeon-tools-test")
        val source = Path.of(System.getProperty("fixture.directory"))
        Files.walk(source).use { paths -> paths.forEach { p ->
            val dest = root.resolve(source.relativize(p))
            if (Files.isDirectory(p)) Files.createDirectories(dest) else Files.copy(p, dest)
        } }
        gm = GameLoopTools(root, ToolRole.GAME_MASTER)
        updater = GameLoopTools(root, ToolRole.STATE_UPDATER)
    }

    @AfterTest fun cleanup() { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }

    private fun call(tools: GameLoopTools, name: String, vararg args: Pair<String, JsonElement>) = tools.call(name, JsonObject(args.toMap()))
    private fun read(path: String) = success(call(gm, "read_page", "path" to JsonPrimitive(path)))
    private fun revision() = read("state.md").getValue("revision")
    private fun success(response: JsonObject): JsonObject {
        assertEquals(JsonPrimitive(true), response["ok"], response.toString())
        return response.getValue("result").jsonObject
    }
    private fun failure(response: JsonObject, message: String) {
        assertEquals(JsonPrimitive(false), response["ok"], response.toString())
        assertTrue(response.getValue("error").jsonPrimitive.content.contains(message), response.toString())
    }
    private fun record(): JsonObject = success(call(gm, "record_step",
        "expected_revision" to revision(), "command" to JsonPrimitive("north"),
        "resolved_account" to JsonPrimitive("The player enters the Bell Court. The keeper greets them; the game master confirms the greeting."),
        "instructions" to JsonPrimitive("Move player membership north.")))
    private fun change(path: String, edit: (MarkdownDocument) -> MarkdownDocument): JsonObject = buildJsonObject {
        put("path", path); put("content", edit(MarkdownDocument.parse(root.resolve(path).readText())).render()); put("reason", "Resolved action")
    }
    private fun apply(step: JsonObject, changes: List<JsonObject>) = call(updater, "apply_step",
        "expected_revision" to step.getValue("revision"), "step" to step.getValue("step"), "changes" to JsonArray(changes))
    private fun contents() = Files.walk(root).use { paths -> paths.filter { it.extension == "md" }.toList().associate { root.relativize(it).toString() to it.readText() } }

    @Test fun `initial fixture validates and tools are discoverable`() {
        assertEquals(JsonPrimitive(true), success(call(gm, "validate_state"))["valid"])
        assertTrue(gm.definitions().any { it.name == "record_step" })
        assertFalse(gm.definitions().any { it.name == "apply_step" })
        val matches = success(call(gm, "search_pages", "query" to JsonPrimitive("sunstone"), "limit" to JsonPrimitive(2)))
        assertEquals(2, matches.getValue("matches").jsonArray.size)
        assertEquals("object", gm.definitions().first().declaration().getValue("parameters").jsonObject.text("type"))
    }

    @Test fun `movement derives player and state and preserves log history`() {
        val step = record()
        val entrance = change("cells/cell-1-2.md") { it.fields("actors" to JsonArray(emptyList())) }
        val court = change("cells/cell-1-1.md") {
            it.fields("actors" to JsonArray(it.metadata.strings("actors").map(::JsonPrimitive) + JsonPrimitive("../actors/player.md")))
        }
        success(apply(step, listOf(entrance, court)))
        val player = MarkdownDocument.parse(root.resolve("actors/player.md").readText())
        assertEquals("../cells/cell-1-1.md", player.metadata.text("location"))
        assertTrue(player.history.contains("Initialization")); assertTrue(player.history.contains("formal rule"))
        val state = MarkdownDocument.parse(root.resolve("state.md").readText()).metadata
        assertEquals("cells/cell-1-1.md", state.text("current_cell"))
        assertEquals("awaiting-command", state.text("loop_phase")); assertEquals(2, state.number("next_step_number"))
        val before = contents()
        failure(apply(step, listOf(entrance, court)), "Stale revision")
        assertEquals(before, contents())
    }

    @Test fun `inventory membership derives immediate holder`() {
        val step = record()
        // Tools apply GM outcomes; they do not decide action reachability or puzzle rules.
        val cell = change("cells/cell-0-1.md") { it.fields("objects" to JsonArray(emptyList())) }
        val player = change("actors/player.md") { it.fields("carried_objects" to JsonArray(listOf(JsonPrimitive("../objects/ice-pick.md")))) }
        success(apply(step, listOf(cell, player)))
        val pick = MarkdownDocument.parse(root.resolve("objects/ice-pick.md").readText())
        assertEquals("../actors/player.md", pick.metadata.text("location"))
        assertEquals(JsonPrimitive(true), pick.metadata["portable"])
    }

    @Test fun `conflicting memberships reject entire batch without modifying any file`() {
        val step = record(); val before = contents()
        val court = change("cells/cell-1-1.md") { it.fields("actors" to JsonArray(listOf(JsonPrimitive("../actors/keeper.md"), JsonPrimitive("../actors/player.md")))) }
        failure(apply(step, listOf(court)), "Multiple memberships")
        assertEquals(before, contents())
    }

    @Test fun `missing membership and direct location edit are rejected`() {
        val step = record(); val before = contents()
        failure(apply(step, listOf(change("cells/cell-1-2.md") { it.fields("actors" to JsonArray(emptyList())) })), "No membership")
        failure(apply(step, listOf(change("actors/player.md") { it.fields("location" to JsonPrimitive("../cells/cell-1-1.md")) })), "tool-managed")
        assertEquals(before, contents())
    }

    @Test fun `stale revision and unauthorized tools cannot write`() {
        val old = revision(); record(); val before = contents()
        failure(call(gm, "record_step", "expected_revision" to old), "Stale revision")
        failure(call(GameLoopTools(root, ToolRole.ACTOR), "record_step"), "unavailable")
        assertEquals(before, contents())
    }

    @Test fun `path traversal absolute paths and symlinks are rejected`() {
        failure(call(gm, "read_page", "path" to JsonPrimitive("../secret.md")), "escapes")
        failure(call(gm, "read_page", "path" to JsonPrimitive("/tmp/secret.md")), "relative")
        val outside = Files.createTempFile("secret", ".md")
        try {
            Files.createSymbolicLink(root.resolve("leak.md"), outside)
            failure(call(gm, "read_page", "path" to JsonPrimitive("leak.md")), "Symbolic")
        } finally { Files.delete(outside) }
    }

    @Test fun `unresolved journal blocks all subsequent calls`() {
        Files.createDirectory(root.resolve(".pending-update"))
        failure(call(gm, "validate_state"), "Interrupted update")
    }

    @Test fun `no-op turn advances once without touching entity files`() {
        val step = record(); val before = root.resolve("actors/player.md").readText()
        success(apply(step, emptyList()))
        assertEquals(before, root.resolve("actors/player.md").readText())
    }

    @Test fun `new object requires membership and gets a derived location and changelog`() {
        val step = record()
        val newObject = buildJsonObject {
            put("path", "objects/snowball.md"); put("content", "---\nid: snowball\nportable: true\n---\n\n# Snowball\n\nFresh snow.")
            put("reason", "Player formed snow")
        }
        val cell = change("cells/cell-1-2.md") { it.fields("objects" to JsonArray(listOf(JsonPrimitive("../objects/snowball.md")))) }
        success(apply(step, listOf(newObject, cell)))
        val obj = MarkdownDocument.parse(root.resolve("objects/snowball.md").readText())
        assertEquals("../cells/cell-1-2.md", obj.metadata.text("location")); assertTrue(obj.history.isNotEmpty())
    }

    @Test fun `unsafe YAML and forged changelog are rejected`() {
        assertFails { MarkdownDocument.parse("---\na: 1\na: 2\n---\n# Bad") }
        assertFails { MarkdownDocument.parse("---\na: !!java.net.URL [https://example.com]\n---\n# Bad") }
        val step = record(); val before = contents()
        failure(apply(step, listOf(change("objects/ember.md") { it.copy(history = "Forged") })), "tool-managed")
        assertEquals(before, contents())
    }
}
