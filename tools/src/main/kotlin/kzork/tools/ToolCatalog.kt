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
    private const val TEXT = """{"type":"string"}"""
    private const val LIMIT = """{"type":"integer","minimum":1,"maximum":100}"""
    private val readers = ToolRole.entries.toSet()
    val definitions = listOf(
        ToolDefinition(
            name = "list_pages",
            description = "List game-relative Markdown paths and the current revision. Use empty prefix to list all pages.",
            parameters = schema(""""prefix":$TEXT,"offset":{"type":"integer","minimum":0},"limit":$LIMIT"""),
            roles = readers
        ),
        ToolDefinition(
            name = "read_page",
            description = "Read an existing Markdown page and revision. Paths are relative to the game root; follow page links relative to their containing page.",
            parameters = schema(""""path":$TEXT"""),
            roles = readers
        ),
        ToolDefinition(
            name = "search_pages",
            description = "Case-insensitive literal search across game Markdown, including history. Returns bounded snippets and line numbers.",
            parameters = schema(""""query":$TEXT,"limit":$LIMIT"""),
            roles = readers
        ),
        ToolDefinition(
            name = "validate_state",
            description = "Read-only validation of membership and grid references. Reports stale derived fields without modifying files.",
            parameters = schema(""),
            roles = readers
        ),
        ToolDefinition(
            name = "record_step",
            description = "Record the game master's completed turn. Include actor invitations, proposals and their resolutions in resolved_account. Instructions describe established changes only. Marks the step active for the updater. Requires the latest revision.",
            parameters = schema(""""expected_revision":$TEXT,"command":$TEXT,"resolved_account":$TEXT,"instructions":$TEXT"""),
            roles = setOf(ToolRole.GAME_MASTER),
        ),
        ToolDefinition(
            name = "apply_step",
            description = "Apply a resolved active turn as one validated batch. Each change supplies the complete YAML+Markdown page; omit Changelog or copy it unchanged. Edit membership lists to move entities; leave existing location and loop fields unchanged. New entity pages must be accompanied by membership changes. Locations, changelogs and turn advancement are automatic. An empty changes list completes a turn with no world changes.",
            parameters = schema(""""expected_revision":$TEXT,"step":$TEXT,"changes":{"type":"array","maxItems":100,"items":{"type":"object","properties":{"path":$TEXT,"content":$TEXT,"reason":$TEXT},"required":["path","content","reason"],"additionalProperties":false}}"""),
            roles = setOf(ToolRole.STATE_UPDATER)
        ),
    )
}
