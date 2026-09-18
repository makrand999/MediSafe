package com.example.medac.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun testParseMarkdownBlocks_headersAndLists() {
        val markdown = """
            # Heading 1
            Here is a paragraph with **bold** text.
            
            ## Dosage Instructions
            - Take 1 tablet in the morning
            - Take 1 tablet at night
            
            1. First step
            2. Second step
            
            > Important note from doctor
            
            ```json
            {"dosage": "500mg"}
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue(blocks.isNotEmpty())

        assertTrue(blocks[0] is MarkdownBlock.Header)
        assertEquals(1, (blocks[0] as MarkdownBlock.Header).level)
        assertEquals("Heading 1", (blocks[0] as MarkdownBlock.Header).text)

        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertEquals("Here is a paragraph with **bold** text.", (blocks[1] as MarkdownBlock.Paragraph).text)

        assertTrue(blocks[2] is MarkdownBlock.Header)
        assertEquals(2, (blocks[2] as MarkdownBlock.Header).level)
        assertEquals("Dosage Instructions", (blocks[2] as MarkdownBlock.Header).text)

        assertTrue(blocks[3] is MarkdownBlock.BulletItem)
        assertEquals("Take 1 tablet in the morning", (blocks[3] as MarkdownBlock.BulletItem).text)

        assertTrue(blocks[4] is MarkdownBlock.BulletItem)
        assertEquals("Take 1 tablet at night", (blocks[4] as MarkdownBlock.BulletItem).text)

        assertTrue(blocks[5] is MarkdownBlock.NumberedItem)
        assertEquals("1.", (blocks[5] as MarkdownBlock.NumberedItem).number)
        assertEquals("First step", (blocks[5] as MarkdownBlock.NumberedItem).text)

        assertTrue(blocks[6] is MarkdownBlock.NumberedItem)
        assertEquals("2.", (blocks[6] as MarkdownBlock.NumberedItem).number)
        assertEquals("Second step", (blocks[6] as MarkdownBlock.NumberedItem).text)

        assertTrue(blocks[7] is MarkdownBlock.Blockquote)
        assertEquals("Important note from doctor", (blocks[7] as MarkdownBlock.Blockquote).text)

        assertTrue(blocks[8] is MarkdownBlock.CodeBlock)
        assertEquals("json", (blocks[8] as MarkdownBlock.CodeBlock).language)
        assertEquals("{\"dosage\": \"500mg\"}", (blocks[8] as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun testParseInlineMarkdown_styles() {
        val raw = "Take `Paracetamol` with **food** and *water*."
        val annotated = parseInlineMarkdown(raw)
        assertEquals("Take  Paracetamol  with food and water.", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
