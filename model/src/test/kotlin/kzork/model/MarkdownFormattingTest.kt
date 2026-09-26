package kzork.model

import kotlin.test.*

class MarkdownFormattingTest {
    @Test fun `sentences start new source lines and formatting is idempotent`() {
        val text = "First sentence. Second sentence! Third sentence?\nA final statement."
        val expected = "First sentence.\nSecond sentence!\nThird sentence?\nA final statement.\n"
        assertEquals(expected, MarkdownFormatting.format(text))
        assertEquals(expected, MarkdownFormatting.format(expected))
    }

    @Test fun `wraps long sentences and keeps list continuation indentation`() {
        val text = "1. " + "word ".repeat(60) + "end. Another statement."
        val actual = MarkdownFormatting.format(text)
        assertTrue(actual.lines().all { it.length <= 80 })
        assertTrue(actual.contains("\n   Another statement."))
        assertEquals(actual, MarkdownFormatting.format(actual))
        assertEquals(text.trim(), actual.trim().replace(Regex("\\s+"), " "))
    }

    @Test fun `does not split inline code links decimals and abbreviations`() {
        val text = "Use `x.y. z` and [a link](https://example.com/a.b). Try e.g. version 2.4 with Dr. Frost. Done."
        assertEquals("Use `x.y. z` and [a link](https://example.com/a.b).\nTry e.g. version 2.4 with Dr. Frost.\nDone.\n", MarkdownFormatting.format(text))
    }

    @Test fun `preserves structured blocks and hard breaks`() {
        val text = "---\nid: game\n---\n\n# Title\n\n```kotlin\nval a = 1; val b = 2\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\nFirst  \nSecond\\\nThird\n"
        assertEquals(text, MarkdownFormatting.format(text))
    }

    @Test fun `preserves quote and nested list prefixes`() {
        val text = "> First. Second.\n\n- Parent.\n  - Child. Another.\n"
        assertEquals("> First.\n> Second.\n\n- Parent.\n  - Child.\n    Another.\n", MarkdownFormatting.format(text))
    }

    @Test fun `rejects unbreakable content instead of corrupting it`() {
        assertFailsWith<IllegalArgumentException> { MarkdownFormatting.format("`" + "x".repeat(81) + "`") }
        assertFailsWith<IllegalArgumentException> { MarkdownFormatting.format("```\n" + "x".repeat(81) + "\n```") }
    }

    @Test fun `width counts unicode code points`() {
        val text = "😀".repeat(80)
        assertEquals("$text\n", MarkdownFormatting.format(text))
    }

    @Test fun `page model renders sections and changelog through policy`() {
        val page = MarkdownPage(
            ObjectMetadata(EntityId("key"), PageReference("../cells/entry.md")), "Key",
            sections = listOf(MarkdownSection("Description", "An iron key. It is cold.")),
            changelog = listOf(ChangelogEntry("Created the key. Placed it in the entry.")),
        )
        val body = page.renderBody()
        assertTrue(body.contains("An iron key.\nIt is cold."))
        assertTrue(body.contains("- Created the key.\n  Placed it in the entry."))
        assertEquals(body, MarkdownFormatting.format(body))
    }
}
