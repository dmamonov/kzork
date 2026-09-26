package kzork.tools

import kotlinx.serialization.json.*
import kzork.model.MarkdownFormatting
import kotlin.test.*

class MarkdownDocumentTest {
    @Test fun `serialization wraps header and prose without losing metadata or history`() {
        val value = "a long authored description ".repeat(20).trim()
        val doc = MarkdownDocument(
            buildJsonObject { put("id", "test"); put("description", value); put("portable", true) },
            "# Object\n\n## Description\n\nFirst statement. Second statement. " + "words ".repeat(50) + "end.",
            "- Initialization: created object. It is ready."
        )
        val output = doc.render()
        MarkdownFormatting.checkWidth(output)
        assertTrue(output.contains("First statement.\nSecond statement."))
        assertTrue(output.contains("- Initialization: created object.\n  It is ready."))
        val parsed = MarkdownDocument.parse(output)
        assertEquals(doc.metadata, parsed.metadata)
        assertEquals(output, parsed.render())
    }

    @Test fun `formatting history twice is stable`() {
        val doc = MarkdownDocument.parse("---\nid: test\n---\n# Object\n\n## Changelog\n\n- First. Second.")
        assertEquals("- First.\n  Second.", doc.history)
        assertEquals(doc, MarkdownDocument.parse(doc.render()))
    }

    @Test fun `overlong code is rejected during serialization`() {
        val doc = MarkdownDocument(buildJsonObject { put("id", "test") }, "# Object\n\n```\n" + "x".repeat(81) + "\n```", "")
        assertFailsWith<IllegalArgumentException> { doc.render() }
    }
}
