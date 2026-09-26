package kzork.tools

import kotlinx.serialization.json.*

enum class ToolRole { GAME_MASTER, ACTOR, STATE_UPDATER }

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val roles: Set<ToolRole>,
) {
    /** Provider-neutral function declaration; wrap as required by the selected AI client. */
    fun declaration(): JsonObject = buildJsonObject {
        put("name", name); put("description", description); put("parameters", parameters)
    }
}

internal object ToolCatalog {
    private fun schema(properties: String): JsonObject {
        val parsed = Json.parseToJsonElement("{$properties}").jsonObject
        return buildJsonObject {
            put("type", "object"); put("properties", parsed)
            put("required", JsonArray(parsed.keys.map(::JsonPrimitive))); put("additionalProperties", false)
        }
    }
    private const val text = """{"type":"string"}"""
    private const val limit = """{"type":"integer","minimum":1,"maximum":100}"""
    private val readers = ToolRole.entries.toSet()
    val definitions = listOf(
        ToolDefinition("list_pages", "List game-relative Markdown paths and the current revision. Use empty prefix to list all pages.",
            schema(""""prefix":$text,"offset":{"type":"integer","minimum":0},"limit":$limit"""), readers),
        ToolDefinition("read_page", "Read an existing Markdown page and revision. Paths are relative to the game root; follow page links relative to their containing page.",
            schema(""""path":$text"""), readers),
        ToolDefinition("search_pages", "Case-insensitive literal search across game Markdown, including history. Returns bounded snippets and line numbers.",
            schema(""""query":$text,"limit":$limit"""), readers),
        ToolDefinition("validate_state", "Read-only validation of membership and grid references. Reports stale derived fields without modifying files.", schema(""), readers),
        ToolDefinition("record_step", "Record the game master's completed turn. Include actor invitations, proposals and their resolutions in resolved_account. Instructions describe established changes only. Marks the step active for the updater. Requires the latest revision.",
            schema(""""expected_revision":$text,"command":$text,"resolved_account":$text,"instructions":$text"""), setOf(ToolRole.GAME_MASTER)),
        ToolDefinition("apply_step", "Apply a resolved active turn as one validated batch. Each change supplies the complete YAML+Markdown page; omit Changelog or copy it unchanged. Edit membership lists to move entities; leave existing location and loop fields unchanged. New entity pages must be accompanied by membership changes. Locations, changelogs and turn advancement are automatic. An empty changes list completes a turn with no world changes.",
            schema(""""expected_revision":$text,"step":$text,"changes":{"type":"array","maxItems":100,"items":{"type":"object","properties":{"path":$text,"content":$text,"reason":$text},"required":["path","content","reason"],"additionalProperties":false}}"""), setOf(ToolRole.STATE_UPDATER)),
    )
}
