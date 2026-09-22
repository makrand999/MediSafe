package com.example.medac.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {

    private fun frames(vararg lines: String): List<SseParser.Frame> {
        val parser = SseParser()
        return lines.mapNotNull { parser.accept(it) }
    }

    @Test
    fun parsesEventAndData() {
        val out = frames("event: delta", "data: {\"text\":\"hello\"}", "")
        assertEquals(1, out.size)
        assertEquals("delta", out[0].event)
        assertEquals("{\"text\":\"hello\"}", out[0].data)
    }

    @Test
    fun defaultsToMessageEventWhenOmitted() {
        val out = frames("data: plain", "")
        assertEquals(1, out.size)
        assertEquals("message", out[0].event)
        assertEquals("plain", out[0].data)
    }

    @Test
    fun joinsMultilineDataWithNewlines() {
        val out = frames("event: done", "data: line one", "data: line two", "")
        assertEquals(1, out.size)
        assertEquals("line one\nline two", out[0].data)
    }

    @Test
    fun ignoresCommentsAndStrayBlankLines() {
        val out = frames(": ping", "", "event: delta", "data: x", "")
        assertEquals(1, out.size)
        assertEquals("delta", out[0].event)
        assertEquals("x", out[0].data)
    }

    @Test
    fun doesNotEmitUntilFrameIsTerminated() {
        val parser = SseParser()
        assertNull(parser.accept("event: delta"))
        assertNull(parser.accept("data: partial"))
        assertEquals("delta", parser.accept("")?.event)
    }

    @Test
    fun toleratesCarriageReturnsAndSpacing() {
        val out = frames("event:delta\r", "data:{\"a\":1}\r", "\r")
        assertEquals(1, out.size)
        assertEquals("delta", out[0].event)
        assertEquals("{\"a\":1}", out[0].data)
    }

    @Test
    fun parsesConsecutiveFrames() {
        val out = frames(
            "event: delta", "data: one", "",
            "event: delta", "data: two", "",
            "event: done", "data: {}", ""
        )
        assertEquals(3, out.size)
        assertEquals(listOf("one", "two", "{}"), out.map { it.data })
        assertEquals("done", out.last().event)
    }
}
