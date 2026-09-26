package kzork.tools

import kotlinx.serialization.json.*
import kzork.model.MarkdownFormatting
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Unknown header fields and narrative sections survive edits. YAML comments are presentation only. */
internal data class MarkdownDocument(val metadata: JsonObject, val body: String, val history: String) {
    fun render(): String {
        val options = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; isPrettyFlow = true
            width = MarkdownFormatting.MAX_LINE_WIDTH; splitLines = true }
        // SnakeYAML's width is a preferred break column, not a hard maximum.
        // Tighten it until the emitted header meets the shared limit, or reject an indivisible value.
        var header: String
        while (true) {
            header = Yaml(options).dump(plainValue(metadata)).trimEnd()
            val overflow = header.lineSequence().maxOf { it.codePointCount(0, it.length) } -
                MarkdownFormatting.MAX_LINE_WIDTH
            if (overflow <= 0 || options.width == 1) break
            options.width = (options.width - overflow - 1).coerceAtLeast(1)
        }
        return MarkdownFormatting.format("---\n$header\n---\n\n${body.trim()}\n\n## Changelog\n\n${history.trim()}\n")
    }
    fun logged(entry: String) = copy(history = listOf(history.trim(), "- $entry").filter { it.isNotEmpty() }.joinToString("\n"))
    fun fields(vararg fields: Pair<String, JsonElement>) = copy(metadata = JsonObject(metadata + fields))

    companion object {
        private fun plainValue(value: JsonElement): Any? = when (value) {
            JsonNull -> null
            is JsonObject -> value.mapValues { plainValue(it.value) }
            is JsonArray -> value.map(::plainValue)
            is JsonPrimitive -> when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.boolean
                value.longOrNull != null -> value.long
                else -> value.double
            }
        }
        fun parse(text: String): MarkdownDocument {
            require(text.length <= 1_000_000) { "Page exceeds 1 MB text limit" }
            val normalized = text.replace("\r\n", "\n")
            val match = Regex("\\A---\\n(.*?)\\n---(?:\\n|$)", RegexOption.DOT_MATCHES_ALL).find(normalized)
                ?: error("Page must begin with YAML front matter")
            val options = LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 0; codePointLimit = 1_000_000 }
            val raw = Yaml(SafeConstructor(options)).load<Any?>(match.groupValues[1])
            val metadata = yamlValue(raw) as? JsonObject ?: error("Header must be a mapping")
            val narrative = normalized.substring(match.range.last + 1).trim()
            val headings = Regex("(?m)^## Changelog\\s*$").findAll(narrative).toList()
            require(headings.size <= 1) { "Only one Changelog section is allowed" }
            val split = headings.singleOrNull()
            val body = if (split == null) narrative else narrative.substring(0, split.range.first).trim()
            val history = if (split == null) "" else narrative.substring(split.range.last + 1).trim()
            require(!Regex("(?m)^#{1,2} ").containsMatchIn(history)) { "Changelog must be the final section" }
            require(body.startsWith("# ")) { "Page needs a Markdown title" }
            return MarkdownDocument(metadata, MarkdownFormatting.format(body).trim(), MarkdownFormatting.format(history).trim())
        }

        private fun yamlValue(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map(::yamlValue))
            is Map<*, *> -> JsonObject(value.entries.associate { (key, item) ->
                require(key is String) { "Header keys must be strings" }
                key to yamlValue(item)
            })
            else -> error("Unsupported YAML value: ${value.javaClass.simpleName}")
        }
    }
}

internal fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("$key must be a string")
internal fun JsonObject.strings(key: String): List<String> {
    val value = this[key] ?: return emptyList()
    require(value is JsonArray) { "$key must be a list" }
    return value.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: error("$key must contain strings") }
}
internal fun JsonObject.number(key: String): Int =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("$key must be an integer")
