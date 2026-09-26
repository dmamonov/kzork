package kzork.app

import kotlinx.serialization.json.*
import kzork.tools.*
import kotlin.io.path.*

/** Explicit offline demo for the authored ice dungeon. Live AI uses AiTurnEngine. */
class DemoTurnEngine(private val files: GameFiles) : TurnEngine {
    override val mode = "demo"
    private val gm = GameLoopTools(files.root, ToolRole.GAME_MASTER)
    private val updater = GameLoopTools(files.root, ToolRole.STATE_UPDATER)
    override suspend fun turn(command: String, visibleHistory: String, status: (String) -> Unit) {
        status("The game master is resolving your action")
        val changes = linkedMapOf<String, MarkdownDocument>()
        fun page(path: String) = changes[path] ?: files.read(path)
        fun set(path: String, value: MarkdownDocument) { changes[path] = value }
        val playerPath = files.playerPath(); val source = files.currentPath()
        var destination = source
        fun refs(path: String, field: String) = page(path).metadata.array(field).map { it.jsonPrimitive.content }.toMutableList()
        fun membership(path: String, field: String, values: List<String>) = set(path, page(path).fields(field to JsonArray(values.map(::JsonPrimitive))))
        fun relative(from: String, to: String) = (java.nio.file.Path.of(from).parent ?: java.nio.file.Path.of("")).relativize(java.nio.file.Path.of(to)).toString()
        fun carried(id: String) = refs(playerPath, "carried_objects").any { files.resolve(playerPath, it) == "objects/$id.md" }
        fun present(id: String) = refs(source, "objects").any { files.resolve(source, it) == "objects/$id.md" }
        fun objectId(text: String) = when {
            "pick" in text -> "ice-pick"; "ember" in text -> "ember"; "sunstone" in text || "sun stone" in text -> "sunstone"; else -> null
        }
        val normalized = command.trim().lowercase().removeSuffix(".")
        val aliases = mapOf("n" to "north", "e" to "east", "s" to "south", "w" to "west")
        val direction = aliases[normalized] ?: normalized.removePrefix("go ").removePrefix("walk ")
        var narration = when {
            direction in setOf("north", "east", "south", "west") -> {
                val coordinates = page(source).metadata.getValue("coordinates").jsonObject
                val x = coordinates.getValue("x").jsonPrimitive.int; val y = coordinates.getValue("y").jsonPrimitive.int
                val (dx, dy) = mapOf("north" to (0 to -1), "east" to (1 to 0), "south" to (0 to 1), "west" to (-1 to 0)).getValue(direction)
                val target = "cells/cell-${x + dx}-${y + dy}.md"
                if (!files.path(target).exists()) "The glacier closes that way. You remain where you are."
                else {
                    destination = target
                    membership(source, "actors", refs(source, "actors").filter { files.resolve(source, it) != playerPath })
                    membership(target, "actors", refs(target, "actors") + relative(target, playerPath))
                    set(playerPath, page(playerPath).withSection("Current state", "You are in ${page(target).title()}."))
                    "You follow the blue-lit passage ${direction}."
                }
            }
            normalized in setOf("look", "l") -> page(source).section("Description")
            normalized in setOf("inventory", "i") -> {
                val items = refs(playerPath, "carried_objects").map { files.read(files.resolve(playerPath, it)).title() }
                if (items.isEmpty()) "Your hands are empty." else "You carry ${items.joinToString(", ")}."
            }
            normalized.startsWith("examine ") || normalized.startsWith("x ") || normalized.startsWith("read ") -> {
                val id = objectId(normalized)
                if (id != null && (present(id) || carried(id))) page("objects/$id.md").section("Description")
                else if ("inscription" in normalized && source == "cells/cell-0-0.md") "Free the ember with iron. Let the ember free the sun."
                else "You examine the chamber. ${page(source).section("Description")}"
            }
            normalized.startsWith("take ") || normalized.startsWith("get ") -> {
                val id = objectId(normalized)
                when {
                    id == null -> "What would you like to take?"
                    carried(id) -> "You already carry it."
                    !present(id) -> "That object is not here."
                    page("objects/$id.md").metadata["portable"]?.jsonPrimitive?.booleanOrNull != true -> "The ${page("objects/$id.md").title()} is held fast by ice. You will need to free it first."
                    else -> {
                        val obj = "objects/$id.md"
                        membership(source, "objects", refs(source, "objects").filter { files.resolve(source, it) != obj })
                        membership(playerPath, "carried_objects", refs(playerPath, "carried_objects") + relative(playerPath, obj))
                        set(obj, page(obj).withSection("Current state", "Carried by the hearth keeper."))
                        val empty = when (id) { "ice-pick" -> "The workshop's low workbench is now empty."; "ember" -> "An empty socket glimmers above a little meltwater."; else -> "The altar stands empty, its ice melted into a shallow pool." }
                        set(source, page(source).withSection("Description", empty).withSection("Intro message", empty))
                        "You take the ${page(obj).title().lowercase()}."
                    }
                }
            }
            normalized.startsWith("drop ") -> {
                val id = objectId(normalized)
                if (id == null || !carried(id)) "You are not carrying that."
                else {
                    val obj = "objects/$id.md"
                    membership(playerPath, "carried_objects", refs(playerPath, "carried_objects").filter { files.resolve(playerPath, it) != obj })
                    membership(source, "objects", refs(source, "objects") + relative(source, obj))
                    set(obj, page(obj).withSection("Current state", "Resting in ${page(source).title()}."))
                    val description = "${page(source).section("Description")} The ${page(obj).title().lowercase()} rests on the floor."
                    set(source, page(source).withSection("Description", description).withSection("Intro message", description))
                    "You set it carefully on the floor."
                }
            }
            normalized.startsWith("use ") || normalized.startsWith("free ") || normalized.startsWith("thaw ") -> {
                val target = if ("sunstone" in normalized || "sun stone" in normalized) "sunstone" else "ember"
                val tool = if (target == "sunstone") "ember" else "ice-pick"
                when {
                    !present(target) -> "Your target is not in this chamber."
                    !carried(tool) -> "You need to be carrying the ${tool.replace('-', ' ')} first."
                    page("objects/$target.md").metadata["frozen"] == JsonPrimitive(false) -> "It is already free of the ice."
                    target == "ember" && "pick" !in normalized && !normalized.startsWith("free") -> "That does not loosen the crust. Something made for chipping ice might help."
                    else -> {
                        val obj = "objects/$target.md"
                        val description = if (target == "ember") "The warm red ember sits loose in its socket, ready to take." else "The golden sunstone rests on the wet altar, free of the ice."
                        set(obj, page(obj).fields("frozen" to JsonPrimitive(false), "portable" to JsonPrimitive(true))
                            .withSection("Description", description).withSection("Current state", "Free and ready to take."))
                        set(source, page(source).withSection("Description", description).withSection("Intro message", description))
                        if (target == "ember") "A few careful taps crack the brittle rim. The living ember comes free, warm and safe to touch."
                        else "The ember glows against the frozen altar. Ice runs away in silver threads, releasing the sunstone."
                    }
                }
            }
            normalized.startsWith("talk") || normalized.startsWith("ask") -> {
                if ("actors/keeper.md" !in files.references(source, "actors")) "There is no one here to answer."
                else {
                    status("The glacier keeper is responding")
                    "The keeper smiles. ‘An iron pick waits in the workshop to the west. Chip the ice around the ember in the east alcove. Its warmth will free the sunstone in the northeast sanctuary.’"
                }
            }
            normalized == "help" -> "Explore with north, east, south and west. Try look, examine ember, take ice pick, use ice pick on ember, talk to keeper, inventory, or drop ember. Bring the sunstone back to the entrance."
            else -> "I am not sure how to do that in the offline demo. Try help, look, a direction, examine, take, drop, talk, or use one object on another."
        }
        if (destination == "cells/cell-1-2.md" && carried("sunstone")) {
            set("state.md", page("state.md").fields("outcome" to JsonPrimitive("won")))
            narration += "\n\n" + files.read("game.md").section("Ending narration")
        }
        val planDirectory = files.root.parent.resolve("demo-plan").createDirectories()
        for ((path, doc) in changes) {
            val target = planDirectory.resolve(path)
            target.parent.createDirectories()
            target.writeText(doc.copy(history = "").render())
        }
        val pending = files.root.parent.resolve("demo-pending.md")
        pending.writeText(MarkdownDocument(buildJsonObject {
            put("targets", JsonArray(changes.keys.map(::JsonPrimitive)))
        }, "# Pending demo update", "").render())
        val revision = checked(gm.call("read_page", buildJsonObject { put("path", "state.md") })).getValue("revision")
        checked(gm.call("record_step", buildJsonObject {
            put("expected_revision", revision); put("command", command); put("resolved_account", narration)
            put("instructions", "Apply the resolved demo changes to: ${changes.keys.joinToString().ifBlank { "none" }}.")
        }))
        resume(status)
    }
    override suspend fun resume(status: (String) -> Unit) {
        status("Saving the changes to the world")
        val pending = files.root.parent.resolve("demo-pending.md")
        val plan = MarkdownDocument.parse(pending.readText()).metadata
        val changes = plan["changes"] ?: JsonArray(plan.array("targets").map { item ->
            val path = item.jsonPrimitive.content
            require(Regex("(?:cells|objects|actors)/[^/]+\\.md|state\\.md").matches(path))
            buildJsonObject {
                put("path", path)
                var planned = MarkdownDocument.parse(files.root.parent.resolve("demo-plan").resolve(path).readText())
                if (path == "state.md") {
                    val actual = files.state().metadata
                    planned = planned.fields(*listOf("game", "player", "current_cell", "loop_phase", "last_applied_step",
                        "active_step", "next_step_number", "pending_work").map { it to actual.getValue(it) }.toTypedArray())
                }
                put("content", planned.render())
                put("reason", "Applied the resolved demo action")
            }
        })
        val read = checked(updater.call("read_page", buildJsonObject { put("path", "state.md") }))
        checked(updater.call("apply_step", buildJsonObject {
            put("expected_revision", read.getValue("revision")); put("step", files.state().metadata.string("active_step")); put("changes", changes)
        }))
        pending.deleteIfExists()
    }
    override suspend fun playerCommand(observation: String): String {
        // Demo-only guide operates on the same public observation, never source files.
        val data = json.parseToJsonElement(observation).jsonObject
        val scene = json.decodeFromJsonElement<Scene>(data.getValue("scene"))
        val inventory = scene.inventory.joinToString(" ").lowercase()
        if ("sunstone" in inventory) return when (scene.id) { "cell-2-0" -> "south"; "cell-2-1" -> "south"; "cell-2-2" -> "west"; else -> "look" }
        if ("ember" in inventory) return when (scene.id) {
            "cell-2-1" -> "north"
            "cell-2-0" -> if (scene.description.contains("free of the ice")) "take sunstone" else "use ember on sunstone"
            else -> "east"
        }
        if ("pick" in inventory) return when (scene.id) {
            "cell-0-1" -> "east"; "cell-1-1" -> "east"
            "cell-2-1" -> if (scene.description.contains("ready to take")) "take ember" else "use ice pick on ember"
            else -> "north"
        }
        return when (scene.id) { "cell-1-2" -> "north"; "cell-1-1" -> "west"; "cell-0-1" -> "take ice pick"; "cell-0-0" -> "south"; "cell-0-2" -> "north"; else -> "west" }
    }
}
