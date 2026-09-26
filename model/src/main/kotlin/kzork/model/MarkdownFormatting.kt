package kzork.model

/** Shared on-disk Markdown policy. Width is measured in Unicode code points, not UTF-16 units. */
object MarkdownFormatting {
    const val MAX_LINE_WIDTH = 80

    /**
     * Reflow prose and list/quote continuations, starting each sentence on its own source line.
     * YAML, code, tables, headings, and reference definitions are preserved and width-checked.
     * Reject an indivisible overlong construct instead of silently corrupting its syntax.
     */
    fun format(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n').lines()
        val output = mutableListOf<String>()
        var i = 0
        if (lines.firstOrNull() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it] == "---" || lines[it] == "..." }
                ?: throw IllegalArgumentException("Unclosed YAML front matter")
            output.addAll(lines.take(end + 1))
            i = end + 1
        }
        while (i < lines.size) {
            val line = lines[i]
            val fence = fenceStart.find(line)
            if (fence != null) {
                val marker = fence.groupValues[1]
                output.add(line)
                i++
                while (i < lines.size) {
                    val code = lines[i++]
                    output.add(code)
                    val trimmed = code.trim()
                    if (trimmed.length >= marker.length && trimmed.all { it == marker[0] }) break
                }
                continue
            }
            if (line.isBlank() || isProtected(line)) {
                output.add(line.trimEnd())
                i++
                continue
            }
            val quote = quotePrefix.find(line)?.value.orEmpty()
            val unquoted = line.removePrefix(quote)
            val list = listPrefix.find(unquoted)?.value.orEmpty()
            val firstPrefix = quote + list
            val continuation = quote + " ".repeat(list.length)
            val content = mutableListOf(unquoted.removePrefix(list).trim())
            var hardBreak = isHardBreak(line)
            i++
            while (!hardBreak && i < lines.size) {
                val next = lines[i]
                if (next.isBlank() || fenceStart.containsMatchIn(next)) break
                val nextQuote = quotePrefix.find(next)?.value.orEmpty()
                if (nextQuote != quote) break
                val nextContent = next.removePrefix(nextQuote)
                if (listPrefix.containsMatchIn(nextContent) || isProtected(nextContent.trimStart())) break
                if (list.isEmpty() && (nextContent.startsWith("    ") || nextContent.startsWith('\t'))) break
                content.add(nextContent.trim())
                hardBreak = isHardBreak(next)
                i++
            }
            var text = content.joinToString(" ")
            val suffix = if (hardBreak) {
                if (text.endsWith('\\')) { text = text.dropLast(1).trimEnd(); "\\" } else "  "
            } else ""
            val wrapped = wrap(text, firstPrefix, continuation, suffix.length)
            output.addAll(wrapped.dropLast(1))
            output.add(wrapped.last() + suffix)
        }
        val result = output.joinToString("\n").trimEnd('\n') + "\n"
        checkWidth(result)
        return result
    }

    fun checkWidth(markdown: String) {
        markdown.lineSequence().forEachIndexed { index, line ->
            require(width(line) <= MAX_LINE_WIDTH) {
                "Markdown line ${index + 1} exceeds $MAX_LINE_WIDTH characters; " +
                    "shorten the indivisible token or restructure the code, table, heading, or YAML field"
            }
        }
    }

    private fun wrap(text: String, first: String, rest: String, reserved: Int): List<String> {
        val words = tokens.findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return listOf(first.trimEnd())
        val result = mutableListOf<String>()
        var prefix = first
        var current = prefix
        for ((index, word) in words.withIndex()) {
            val separator = if (current == prefix) "" else " "
            val limit = MAX_LINE_WIDTH - if (index == words.lastIndex) reserved else 0
            if (width(current + separator + word) > limit && current != prefix) {
                result.add(current)
                prefix = rest
                current = prefix
            }
            current += (if (current == prefix) "" else " ") + word
            if (sentenceEnd(word) && index != words.lastIndex) {
                result.add(current)
                prefix = rest
                current = prefix
            }
        }
        if (current != prefix) result.add(current)
        return result
    }

    private fun sentenceEnd(word: String): Boolean {
        val visible = inline.replace(word, "TOKEN").trimEnd('"', '\'', '”', '’', ')', ']', '}')
        if (visible.lastOrNull() !in listOf('.', '!', '?')) return false
        if (visible.lowercase() in abbreviations || Regex("[A-Za-z]\\.").matches(visible)) return false
        return true
    }

    private fun width(value: String) = value.codePointCount(0, value.length)
    private fun isHardBreak(line: String) = line.endsWith("  ") || line.endsWith('\\')
    private fun isProtected(line: String): Boolean =
        line.startsWith("    ") || line.startsWith('\t') ||
            Regex("^ {0,3}(#{1,6} |\\||(?:[-*_]\\s*){3,}$|={3,}$|\\[[^]]+]:|<)").containsMatchIn(line)

    private val fenceStart = Regex("^ {0,3}(`{3,}|~{3,})")
    private val quotePrefix = Regex("^(?: {0,3}> ?)+")
    private val listPrefix = Regex("^ *(?:[-+*]|\\d+[.)]) +")
    private const val INLINE = "`+[^`]*`+|!?\\[[^]]*]\\([^)]*\\)"
    private val inline = Regex(INLINE)
    private val tokens = Regex("(?:(?:$INLINE)|\\S)+")
    private val abbreviations = setOf("e.g.", "i.e.", "etc.", "mr.", "mrs.", "ms.", "dr.", "prof.", "vs.", "fig.", "no.")
}

/** Render the model's Markdown body; a storage codec supplies the YAML header separately. */
fun MarkdownPage<*>.renderBody(): String {
    require(!title.contains('\n') && !title.contains('\r')) { "Title must be one line" }
    require(sections.none { it.heading == "Changelog" || '\n' in it.heading || '\r' in it.heading }) {
        "Section headings must be single-line; Changelog is managed separately"
    }
    val body = buildString {
        append("# ").append(title).append("\n\n")
        if (preamble.isNotBlank()) append(preamble).append("\n\n")
        for (section in sections) append("## ").append(section.heading).append("\n\n").append(section.markdown).append("\n\n")
        append("## Changelog\n\n")
        for (entry in changelog) {
            append("- ")
            entry.step?.let { append('[').append(it.path).append("](").append(it.path).append("): ") }
            append(entry.description)
            entry.reason?.let { append(" Reason: ").append(it) }
            append('\n')
        }
    }
    return MarkdownFormatting.format(body)
}
