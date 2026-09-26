package kzork.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kzork.tools.MarkdownDocument
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
fun JsonObject.array(name: String) = (this[name] as? JsonArray).orEmpty()
fun MarkdownDocument.title() = body.lineSequence().first().removePrefix("# ").trim()
fun MarkdownDocument.section(name: String): String = Regex(
    "(?ms)^## ${Regex.escape(name)}\\n+(.*?)(?=^## |\\z)"
).find(body)?.groupValues?.get(1)?.trim().orEmpty()
fun MarkdownDocument.withSection(name: String, value: String): MarkdownDocument {
    val expression = Regex("(?ms)^## ${Regex.escape(name)}\\n+.*?(?=^## |\\z)")
    val section = "## $name\n\n$value\n\n"
    return copy(body = if (expression.containsMatchIn(body)) expression.replace(body) { section } else "$body\n\n$section")
}

@Serializable data class Scene(
    val id: String, val title: String, val description: String, val intro: String,
    val image: String, val music: String? = null, val voice: String? = null,
    val exits: List<String> = emptyList(), val objects: List<String> = emptyList(),
    val actors: List<String> = emptyList(), val inventory: List<String> = emptyList(),
)
@Serializable data class ChatEvent(
    val id: String, val role: String, val title: String = "", val text: String = "",
    val image: String? = null, val voice: String? = null, val effect: String? = null,
    val scene: Scene? = null, val turn: Int = 0,
)

class GameFiles(val root: Path) {
    fun path(relative: String): Path {
        require(!Path.of(relative).isAbsolute) { "Expected a relative path" }
        val result = root.resolve(relative).normalize()
        require(result.startsWith(root) && result != root) { "Path escapes game" }
        var check = root
        for (part in root.relativize(result)) { check = check.resolve(part); require(!Files.isSymbolicLink(check)) }
        return result
    }
    fun read(relative: String) = MarkdownDocument.parse(path(relative).readText())
    fun resolve(from: String, ref: String) = root.relativize(path((Path.of(from).parent ?: Path.of("")).resolve(ref).toString())).toString()
    fun references(from: String, field: String) = read(from).metadata.array(field).map { resolve(from, it.jsonPrimitive.content) }
    fun state() = read("state.md")
    fun playerPath() = resolve("state.md", state().metadata.string("player"))
    fun currentPath() = resolve("state.md", state().metadata.string("current_cell"))
    fun outcome() = state().metadata.string("outcome").ifBlank { "in-progress" }
    fun media(from: String, key: String): String? {
        val entry = read(from).metadata["media"] as? JsonObject ?: return null
        val asset = entry[key] as? JsonObject ?: return null
        if (asset.string("status") != "ready") return null
        val relative = resolve(from, asset.string("path"))
        if (!relative.startsWith("media/") || !Files.isRegularFile(path(relative))) return null
        return "/media/$relative"
    }
    fun scene(): Scene {
        val file = currentPath(); val cell = read(file)
        val point = cell.metadata.getValue("coordinates").jsonObject
        val worldPath = resolve(file, cell.metadata.string("world"))
        val indexed = read(worldPath).metadata.array("cells").associate {
            val entry = it.jsonObject; val c = entry.getValue("coordinates").jsonObject
            (c.getValue("x").jsonPrimitive.int to c.getValue("y").jsonPrimitive.int) to entry.string("page")
        }
        val x = point.getValue("x").jsonPrimitive.int; val y = point.getValue("y").jsonPrimitive.int
        val exits = listOf("north" to (x to y - 1), "east" to (x + 1 to y), "south" to (x to y + 1), "west" to (x - 1 to y))
            .filter { it.second in indexed }.map { it.first }
        return Scene(cell.metadata.string("id"), cell.title(), cell.section("Description"), cell.section("Intro message"),
            media(file, "cover_image") ?: "/art/${cell.metadata.string("id")}.svg",
            media(file, "ambient_music"), media(file, "intro_speech"), exits,
            references(file, "objects").map { read(it).title() },
            references(file, "actors").filter { it != playerPath() }.map { read(it).title() },
            references(playerPath(), "carried_objects").map { read(it).title() })
    }
    fun context(): String = Files.walk(root).use { stream ->
        stream.filter { it.extension == "md" && (it.parent.fileName.toString() in setOf("cells", "objects", "actors") || it.parent == root && it.name in setOf("game.md", "world.md", "state.md")) }
            .sorted().toList().joinToString("\n\n") { "FILE ${root.relativize(it)}\n${it.readText()}" }
    }.also { require(it.length < 180_000) { "World is too large for the initial context loader" } }
}
