package kzork.app

import io.ktor.client.request.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kzork.tools.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class GameRuntimeTest {
    private val source = Path.of(System.getProperty("fixture.directory"))
    private lateinit var temporary: Path
    private val sessions = mutableListOf<GameSession>()
    @BeforeTest fun setup() { temporary = Files.createTempDirectory("kzork-runtime") }
    @AfterTest fun cleanup() {
        sessions.forEach { it.close() }
        Files.walk(temporary).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
    private fun session(): GameSession = GameSession.create(source, temporary) { DemoTurnEngine(it) }.also { sessions.add(it) }
    private suspend fun settle(session: GameSession) {
        withTimeout(15000) { while (session.view().busy) delay(10) }
        assertNull(session.view().error, session.view().error)
    }
    private suspend fun command(session: GameSession, text: String) { session.submit(text); settle(session) }

    @Test fun `full playable route persists victory and resumes the same transcript`() = runBlocking {
        val before = source.resolve("state.md").readText()
        val s = session(); s.begin()
        for (c in listOf("north", "west", "take ice pick", "east", "east", "use ice pick on ember", "take ember", "north", "use ember on sunstone", "take sunstone", "south", "south", "west")) command(s, c)
        assertEquals("won", s.view().outcome)
        assertEquals(13, s.view().events.count { it.role == "narrator" })
        val state = s.files.state().metadata
        assertEquals(14, state.getValue("next_step_number").jsonPrimitive.int)
        assertEquals("../actors/player.md", s.files.read("objects/sunstone.md").metadata.string("location"))
        assertEquals(before, source.resolve("state.md").readText())
        val resumed = GameSession.resume(s.directory) { DemoTurnEngine(it) }.also { sessions.add(it) }
        assertEquals(s.view().events, resumed.view().events)
        assertEquals("won", resumed.view().outcome)
    }
    @Test fun `invalid actions leave puzzle state intact and a command creates one turn`() = runBlocking {
        val s = session()
        command(s, "south")
        assertEquals("cell-1-2", s.view().scene.id)
        command(s, "east"); command(s, "north"); command(s, "take ember")
        assertEquals(JsonPrimitive(true), s.files.read("objects/ember.md").metadata["frozen"])
        assertTrue(s.view().scene.inventory.isEmpty())
        assertEquals(4, s.view().events.count { it.role == "narrator" })
    }
    @Test fun `agent mode wins using public observations and stops on victory`() = runBlocking {
        val original = session()
        val auto = GameSession(original.directory, original.files, DemoTurnEngine(original.files), autoplayDelayMillis = 0).also { sessions.add(it) }
        auto.autoplay(20); settle(auto)
        assertEquals("won", auto.view().outcome)
        assertFalse(auto.view().automatic)
        assertTrue(auto.view().events.any { it.role == "agent" })
        val observation = auto.playerObservation()
        assertFalse(observation.contains("Game-master walkthrough"))
        assertFalse(observation.contains("Interaction rules"))
        assertFalse(observation.contains("portable:"))
    }
    @Test fun `server serves UI and rejects unauthenticated mutations and source files`() = testApplication {
        val s = session()
        application { gameApplication(s, "test-token") }
        val browser = createClient { defaultRequest { header(HttpHeaders.Host, "localhost") } }
        val html = browser.get("/")
        assertEquals(HttpStatusCode.OK, html.status)
        assertTrue(html.bodyAsText().contains("Begin adventure"))
        assertEquals(HttpStatusCode.Forbidden, browser.post("/api/begin").status)
        assertEquals(HttpStatusCode.OK, browser.post("/api/begin") { header("X-Game-Token", "test-token") }.status)
        assertEquals(HttpStatusCode.OK, browser.get("/api/state") { header("X-Game-Token", "test-token") }.status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/media/state.md").status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/.env").status)
        assertEquals(HttpStatusCode.OK, browser.get("/art/cell-1-2.svg").status)
        assertEquals(HttpStatusCode.Forbidden, browser.get("/") { header(HttpHeaders.Host, "attacker.example") }.status)
    }
    @Test fun `AI calls actors resolves proposals and delegates tool application`() = runBlocking {
        val s = session()
        command(s, "north")
        val prompts = mutableListOf<String>()
        val client = object : AgentClient {
            override suspend fun run(instructions: String, input: String, tools: List<JsonObject>, execute: suspend (String, JsonObject) -> JsonObject): String {
                prompts.add(instructions)
                return when {
                    instructions.contains("Plan the action") -> """{"assessment":"Ask keeper for help","actors":["actors/keeper.md"]}"""
                    instructions.contains("You play only") -> "I propose telling the player about the pick."
                    instructions.contains("Resolve the player's") -> {
                        assertTrue(input.contains("I propose telling"))
                        """{"narration":"The keeper points west toward the workshop.","instructions":"No state changes."}"""
                    }
                    instructions.contains("state-update agent") -> {
                        assertTrue(tools.any { it.string("name") == "apply_step" })
                        val read = checked(execute("read_page", buildJsonObject { put("path", "state.md") }))
                        checked(execute("apply_step", buildJsonObject {
                            put("expected_revision", read.getValue("revision")); put("step", s.files.state().metadata.string("active_step")); put("changes", JsonArray(emptyList()))
                        }))
                        "Applied"
                    }
                    else -> error("Unexpected role")
                }
            }
        }
        AiTurnEngine(s.files, client).turn("talk to keeper", "", {})
        assertEquals(4, prompts.size)
        assertEquals("awaiting-command", s.files.state().metadata.string("loop_phase"))
        assertTrue(s.files.read("steps/step-0002.md").body.contains("keeper points west"))
    }
    @Test fun `pending resolved update can be retried without recording another step`() = runBlocking {
        val s = session()
        val gm = GameLoopTools(s.files.root, ToolRole.GAME_MASTER)
        val read = checked(gm.call("read_page", buildJsonObject { put("path", "state.md") }))
        checked(gm.call("record_step", buildJsonObject {
            put("expected_revision", read.getValue("revision")); put("command", "look"); put("resolved_account", "You look around."); put("instructions", "No state changes.")
        }))
        s.directory.resolve("demo-pending.md").writeText(MarkdownDocument(buildJsonObject { put("changes", JsonArray(emptyList())) }, "# Pending", "").render())
        s.retry(); settle(s)
        assertEquals(2, s.files.state().metadata.getValue("next_step_number").jsonPrimitive.int)
        assertEquals(1, s.view().events.count { it.role == "narrator" })
    }
}
