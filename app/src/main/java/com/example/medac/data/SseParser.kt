package com.example.medac.data

/**
 * Minimal Server-Sent Events parser for the assistant stream endpoint.
 *
 * Feed it lines as they arrive; it returns a completed [Frame] whenever a blank
 * line terminates a frame. Comments (`: ping`) and unknown fields are ignored,
 * multi-line `data:` values are joined with newlines, and the frame's `event`
 * defaults to "message" when the server omits it.
 *
 * Kept deliberately dependency-free and stateful so it can be unit-tested
 * without Android or a network stack.
 */
class SseParser {

    data class Frame(val event: String, val data: String)

    private val dataLines = mutableListOf<String>()
    private var event: String? = null

    /** Consumes one line (without the trailing newline) and returns a frame when complete. */
    fun accept(rawLine: String): Frame? {
        val line = rawLine.trimEnd('\r')

        if (line.isEmpty()) {
            if (dataLines.isEmpty() && event == null) return null // keep-alive blank line
            val frame = Frame(event ?: "message", dataLines.joinToString("\n"))
            dataLines.clear()
            event = null
            return frame
        }

        if (line.startsWith(":")) return null // comment / heartbeat

        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        var value = if (separator < 0) "" else line.substring(separator + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        when (field) {
            "event" -> event = value
            "data" -> dataLines += value
            // "id", "retry" and anything else are not used by this endpoint.
        }
        return null
    }
}
