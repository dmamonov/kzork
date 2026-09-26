package kzork.tools

import kzork.model.PageReference
import kotlinx.serialization.json.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/** Construct one instance per game and agent role. No API key or network access is needed. */
class GameLoopTools(gameDirectory: Path, val role: ToolRole) {
    private val root = gameDirectory.toRealPath()
    private val mutex = locks.computeIfAbsent(root) { Any() }

    fun definitions(): List<ToolDefinition> = ToolCatalog.definitions.filter { role in it.roles }

    /** Always returns a JSON envelope; bad arguments and rejected mutations never escape as tool exceptions. */
    fun call(name: String, arguments: JsonObject): JsonObject = synchronized(mutex) {
        try {
            require(definitions().any { it.name == name }) { "Tool is unavailable for this role: $name" }
            val lock = safePath(".game-loop.lock")
            FileChannel.open(lock, CREATE, WRITE).use { channel -> channel.lock().use {
                require(!Files.exists(safePath(".pending-update"), NOFOLLOW_LINKS)) {
                    "Interrupted update detected; recover .pending-update before continuing"
                }
                success(dispatch(name, arguments))
            } }
        } catch (e: Exception) {
            buildJsonObject { put("ok", false); put("error", e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun dispatch(name: String, args: JsonObject): JsonElement {
        val pages = snapshot()
        val revision = revision(pages)
        return when (name) {
            "list_pages" -> buildJsonObject {
                put("revision", revision)
                val offset = args.number("offset").also { require(it >= 0) }
                val limit = args.number("limit").also { require(it in 1..100) }
                val paths = pages.keys.sorted().filter { it.startsWith(args.text("prefix")) }
                put("paths", JsonArray(paths.drop(offset).take(limit).map(::JsonPrimitive)))
                put("total", paths.size)
            }
            "read_page" -> buildJsonObject {
                val path = pagePath(args.text("path"))
                put("path", path); put("content", pages[path] ?: error("Page not found: $path")); put("revision", revision)
            }
            "search_pages" -> buildJsonObject {
                val query = args.text("query").also { require(it.isNotBlank()) }
                val limit = args.number("limit").also { require(it in 1..100) }
                val matches = pages.toSortedMap().flatMap { (path, content) -> content.lineSequence().mapIndexedNotNull { i, line ->
                    val index = line.indexOf(query, ignoreCase = true)
                    if (index < 0) null else buildJsonObject {
                        put("path", path); put("line", i + 1)
                        put("text", line.substring((index - 60).coerceAtLeast(0)).take(240))
                    }
                }.toList() }
                put("revision", revision); put("matches", JsonArray(matches.take(limit))); put("total", matches.size)
            }
            "validate_state" -> {
                val before = documents(pages)
                val after = derive(before)
                buildJsonObject {
                    put("revision", revision); put("valid", before == after)
                    put("stale_derived_pages", JsonArray(after.keys.filter { after[it] != before[it] }.map(::JsonPrimitive)))
                }
            }
            "record_step" -> record(args, pages, revision)
            "apply_step" -> apply(args, pages, revision)
            else -> error("Unknown tool")
        }
    }

    private fun record(args: JsonObject, pages: Map<String, String>, revision: String): JsonElement {
        checkRevision(args, revision)
        val docs = documents(pages)
        require(derive(docs) == docs) { "Repair stale derived fields before recording a turn" }
        val state = docs.getValue("state.md")
        require(state.metadata.text("loop_phase") == "awaiting-command" && state.metadata["active_step"] == JsonNull) { "A step is already active" }
        val number = state.metadata.number("next_step_number").also { require(it > 0) }
        val path = "steps/step-${number.toString().padStart(4, '0')}.md"
        require(path !in pages) { "Step already exists" }
        val metadata = buildJsonObject {
            put("step_number", number); put("status", "resolved"); put("game_state", "../state.md")
            val previous = state.metadata["last_applied_step"]
            put("previous_step", if (previous == null || previous == JsonNull) JsonNull else JsonPrimitive(relative(path, resolve("state.md", previous.jsonPrimitive.content))))
            put("media", JsonArray(emptyList()))
        }
        val body = "# Step $number\n\n## Player command\n\n${args.text("command")}\n\n" +
            "## Final account\n\n${args.text("resolved_account")}\n\n" +
            "## Game-master update instructions\n\n${args.text("instructions")}\n\n" +
            "## Application record\n\nAwaiting state-update agent."
        require(!body.contains("\n## Changelog")) { "Reserved Changelog heading in turn input" }
        docs[path] = MarkdownDocument(metadata, body, "").logged("$path: game master recorded resolved events and instructions.")
        docs["state.md"] = state.fields("active_step" to JsonPrimitive(path), "loop_phase" to JsonPrimitive("applying"))
            .logged("$path: resolved turn queued for application.")
        commit(pages, docs)
        return result(path, listOf(path, "state.md"))
    }

    private fun apply(args: JsonObject, pages: Map<String, String>, revision: String): JsonElement {
        checkRevision(args, revision)
        val step = pagePath(args.text("step"))
        val original = documents(pages)
        val docs = original.toMutableMap()
        val state = docs.getValue("state.md")
        require(state.metadata.text("loop_phase") == "applying") { "No resolved step awaiting application" }
        require(resolve("state.md", state.metadata.text("active_step")) == step) { "Step is not active" }
        val record = docs[step] ?: error("Missing step record")
        require(record.metadata.text("status") == "resolved") { "Step must be resolved and unapplied" }
        val number = record.metadata.number("step_number")
        require(number > 0 && number < Int.MAX_VALUE && number == state.metadata.number("next_step_number")) { "Step number does not match state" }
        val changes = args["changes"] as? JsonArray ?: error("changes must be an array")
        require(changes.size <= 100) { "At most 100 page changes per batch" }
        val seen = mutableSetOf<String>()
        val reasons = mutableMapOf<String, String>()
        for (value in changes) {
            val change = value.jsonObject
            val path = pagePath(change.text("path"))
            require(isWorldPage(path) && seen.add(path)) { "Invalid or repeated update target: $path" }
            val proposed = MarkdownDocument.parse(change.text("content"))
            val previous = docs[path]
            require(proposed.history.isEmpty() || proposed.history == previous?.history) { "Changelog is tool-managed" }
            if (previous != null) {
                for (field in protectedFields(path)) {
                    require(proposed.metadata[field] == previous.metadata[field]) { "$path.$field is tool-managed or immutable" }
                }
            }
            docs[path] = proposed.copy(history = previous?.history.orEmpty())
            reasons[path] = change.text("reason").also { require(it.isNotBlank()) }.replace(Regex("\\s+"), " ")
        }
        val derived = derive(docs)
        val changed = derived.keys.filter { derived[it] != original[it] }.toMutableList()
        for (path in changed) {
            val reason = reasons[path] ?: "formal rule: derive location from authoritative membership"
            derived[path] = derived.getValue(path).logged("$step: $reason; formal location rules applied.")
        }
        derived["state.md"] = derived.getValue("state.md").fields(
            "loop_phase" to JsonPrimitive("awaiting-command"), "active_step" to JsonNull,
            "last_applied_step" to JsonPrimitive(step), "next_step_number" to JsonPrimitive(number + 1),
            "pending_work" to JsonArray(emptyList()),
        ).let { if ("state.md" in changed) it else it.logged("$step: applied turn and advanced game loop.") }
        derived[step] = record.fields("status" to JsonPrimitive("applied")).copy(
            body = record.body.replace("Awaiting state-update agent.", "Applied successfully. Membership validated; derived locations updated.") +
                "\n\n## Formal-logic update\n\nValidated the complete membership graph and derived entity locations and the player's current cell.\n\n" +
                "## Changed pages\n\n" + (changed + "state.md").distinct().joinToString("\n") { "- $it" }
        ).logged("$step: state-update agent applied the turn.")
        changed.addAll(listOf("state.md", step))
        commit(pages, derived)
        return result(step, changed.distinct())
    }

    private fun protectedFields(path: String): Set<String> = when {
        path == "state.md" -> setOf("game", "player", "current_cell", "loop_phase", "last_applied_step", "active_step", "next_step_number", "pending_work")
        path.startsWith("objects/") || path.startsWith("actors/") -> setOf("id", "location", "controlled_by")
        else -> setOf("id")
    }

    /** Validate all authoritative memberships before writing any derived field. */
    private fun derive(input: Map<String, MarkdownDocument>): MutableMap<String, MarkdownDocument> {
        val docs = input.toMutableMap()
        val cells = docs.keys.filter { it.startsWith("cells/") }
        val actors = docs.keys.filter { it.startsWith("actors/") }
        val objects = docs.keys.filter { it.startsWith("objects/") }
        val ids = mutableSetOf<String>()
        for (path in cells + actors + objects) require(ids.add(docs.getValue(path).metadata.text("id"))) { "Duplicate entity ID" }
        val owners = mutableMapOf<String, String>()
        fun membership(holder: String, field: String, allowed: List<String>) {
            for (ref in docs.getValue(holder).metadata.strings(field)) {
                val entity = resolve(holder, ref)
                require(entity in allowed) { "$holder.$field references a missing or wrong-type entity: $entity" }
                require(owners.putIfAbsent(entity, holder) == null) { "Multiple memberships for $entity" }
            }
        }
        for (cell in cells) { membership(cell, "actors", actors); membership(cell, "objects", objects) }
        for (actor in actors) membership(actor, "carried_objects", objects)
        for (entity in actors + objects) {
            val owner = owners[entity] ?: error("No membership for $entity")
            docs[entity] = docs.getValue(entity).fields("location" to JsonPrimitive(relative(entity, owner)))
        }
        val state = docs["state.md"] ?: error("Missing state.md")
        val game = docs["game.md"] ?: error("Missing game.md")
        require(resolve("state.md", state.metadata.text("game")) == "game.md")
        require(resolve("game.md", game.metadata.text("state_file")) == "state.md")
        val player = resolve("state.md", state.metadata.text("player"))
        require(player in actors && docs.getValue(player).metadata.text("controlled_by") == "player") { "Invalid player reference" }
        require(resolve("game.md", game.metadata.text("player")) == player) { "Game and state disagree about player" }
        for (actor in actors) require(docs.getValue(actor).metadata.text("controlled_by") in setOf("player", "actor-agent")) { "Invalid actor controller" }
        docs["state.md"] = state.fields("current_cell" to JsonPrimitive(relative("state.md", owners.getValue(player))))
        val worldPath = resolve("game.md", game.metadata.text("world"))
        val world = docs[worldPath] ?: error("Missing world page")
        val entries = world.metadata["cells"] as? JsonArray ?: error("World needs a cells index")
        val indexed = mutableSetOf<String>(); val coordinates = mutableSetOf<Pair<Int, Int>>()
        for (item in entries) {
            val entry = item.jsonObject
            val cell = resolve(worldPath, entry.text("page"))
            require(cell in cells && indexed.add(cell)) { "Invalid or duplicate world cell: $cell" }
            val metadata = docs.getValue(cell).metadata
            require(entry.text("id") == metadata.text("id")) { "World/cell ID mismatch" }
            require(entry["coordinates"] == metadata["coordinates"]) { "World/cell coordinates mismatch" }
            val coordinate = metadata.getValue("coordinates").jsonObject
            require(coordinates.add(coordinate.number("x") to coordinate.number("y"))) { "Duplicate cell coordinate" }
            require(resolve(cell, metadata.text("world")) == worldPath) { "Cell references another world" }
        }
        require(indexed == cells.toSet()) { "World index must contain every cell" }
        return docs
    }

    private fun snapshot(): Map<String, String> = Files.walk(root).use { stream ->
        val paths = stream.filter { it.extension == "md" }.toList()
        require(paths.size <= 2000) { "Game has more than 2000 Markdown pages" }
        paths.associate { path ->
            val relative = root.relativize(path).toString()
            val checked = safePath(relative)
            require(Files.isRegularFile(checked, NOFOLLOW_LINKS) && Files.size(checked) <= 1_000_000) { "Invalid or oversized page: $relative" }
            relative to checked.readText()
        }
    }

    private fun documents(pages: Map<String, String>) = pages.filterKeys { isWorldPage(it) || it.startsWith("steps/") }
        .mapValues { MarkdownDocument.parse(it.value) }.toMutableMap()

    private fun isWorldPage(path: String) = path in setOf("game.md", "state.md", "world.md") ||
        Regex("(?:cells|objects|actors)/[^/]+\\.md").matches(path)

    private fun pagePath(path: String): String {
        require(path.isNotBlank() && !Path.of(path).isAbsolute && path.endsWith(".md")) { "Expected game-relative Markdown path" }
        val normalized = root.relativize(safePath(path)).toString()
        return normalized
    }

    private fun safePath(relative: String): Path {
        require(!Path.of(relative).isAbsolute) { "Absolute paths are not allowed" }
        val target = root.resolve(relative).normalize()
        require(target.startsWith(root) && target != root) { "Path escapes game directory" }
        var current = root
        for (part in root.relativize(target)) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "Symbolic links are not allowed" }
        }
        return target
    }

    private fun resolve(from: String, reference: String): String {
        val parent = Path.of(from).parent ?: Path.of("")
        require(!Path.of(reference).isAbsolute) { "Absolute references are not allowed" }
        return pagePath(parent.resolve(reference).toString())
    }

    private fun relative(from: String, to: String) =
        (Path.of(from).parent ?: Path.of("")).relativize(Path.of(to)).toString()

    /** Journal remains after a process crash; the next call refuses to operate on partial state. */
    private fun commit(before: Map<String, String>, docs: Map<String, MarkdownDocument>) {
        val changed = docs.mapValues { (path, doc) ->
            if (before[path]?.let(MarkdownDocument::parse) == doc) before.getValue(path) else doc.render()
        }.filter { (path, content) -> before[path] != content }
        if (changed.isEmpty()) return
        val journal = safePath(".pending-update")
        Files.createDirectory(journal)
        try {
            val backup = buildJsonObject { changed.keys.forEach { put(it, before[it]?.let(::JsonPrimitive) ?: JsonNull) } }
            journal.resolve("before.json").writeText(backup.toString())
            journal.resolve("after.json").writeText(JsonObject(changed.mapValues { JsonPrimitive(it.value) }).toString())
            for ((path, content) in changed) {
                val target = safePath(path)
                Files.createDirectories(target.parent)
                target.writeText(content)
            }
        } catch (failure: Exception) {
            try {
                for (path in changed.keys) {
                    val target = safePath(path)
                    before[path]?.let { target.writeText(it) } ?: Files.deleteIfExists(target)
                }
                clearJournal(journal)
            } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        clearJournal(journal)
    }

    private fun clearJournal(journal: Path) {
        Files.deleteIfExists(journal.resolve("before.json")); Files.deleteIfExists(journal.resolve("after.json")); Files.delete(journal)
    }
    private fun checkRevision(args: JsonObject, current: String) {
        require(args.text("expected_revision") == current) { "Stale revision; reread state before retrying" }
    }
    private fun revision(pages: Map<String, String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((path, content) in pages.toSortedMap()) { digest.update(path.toByteArray()); digest.update(0.toByte()); digest.update(content.toByteArray()); digest.update(0.toByte()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun result(step: String, changed: List<String>) = buildJsonObject {
        put("step", PageReference(step).path); put("changed_pages", JsonArray(changed.map(::JsonPrimitive))); put("revision", revision(snapshot()))
    }
    private fun success(result: JsonElement) = buildJsonObject { put("ok", true); put("result", result) }
    companion object { private val locks = ConcurrentHashMap<Path, Any>() }
}
