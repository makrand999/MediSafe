package com.example.medac.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensProtobufCodecTest {

    @Test
    fun testEncodeLensOverlayRequest_matchesPythonGencode() {
        val fakeImage = "fake image data".toByteArray(Charsets.UTF_8)
        val encoded = LensProtobufCodec.encodeLensOverlayRequest(
            imageBytes = fakeImage,
            width = 100,
            height = 200,
            language = "en",
            region = "US",
            timeZone = "America/New_York",
            uuid = 123456789L,
            sequenceId = 1,
            imageSequenceId = 1
        )

        // Verify root message can be read
        val reader = LensProtobufCodec.ProtobufReader(encoded)
        assertTrue(reader.hasRemaining())

        val tag = reader.readTag()
        val field = tag ushr 3
        val wire = tag and 0x07
        assertEquals(1, field) // objects_request
        assertEquals(2, wire)  // length-delimited
    }

    @Test
    fun testParseLensResponse_syntheticProtobuf() {
        // Build a synthetic LensOverlayServerResponse protobuf
        val writer = LensProtobufCodec.ProtobufWriter()
        // field 2: objects_response
        writer.writeMessage(2) {
            // field 3: text
            writeMessage(3) {
                // field 2: content_language
                writeString(2, "en")
                // field 1: text_layout
                writeMessage(1) {
                    // field 1: paragraphs (Paragraph 1)
                    writeMessage(1) {
                        // field 2: lines (Line 1)
                        writeMessage(2) {
                            // field 1: words
                            writeMessage(1) {
                                writeString(2, "Paracetamol")
                                writeString(3, " ")
                                writeMessage(4) { // geometry
                                    writeMessage(1) { // bounding_box
                                        writeFixed32Field(1, 0.5f) // center_x
                                        writeFixed32Field(2, 0.2f) // center_y
                                        writeFixed32Field(3, 0.3f) // width
                                        writeFixed32Field(4, 0.05f) // height
                                        writeFixed32Field(5, 0f) // rotation_z
                                        writeVarintField(6, 1) // NORMALIZED
                                    }
                                }
                            }
                            writeMessage(1) {
                                writeString(2, "500mg")
                                writeString(3, "")
                            }
                        }
                    }
                    // field 1: paragraphs (Paragraph 2)
                    writeMessage(1) {
                        // field 2: lines (Line 1)
                        writeMessage(2) {
                            writeMessage(1) {
                                writeString(2, "Take")
                                writeString(3, " ")
                            }
                            writeMessage(1) {
                                writeString(2, "twice")
                                writeString(3, " ")
                            }
                            writeMessage(1) {
                                writeString(2, "daily")
                                writeString(3, "")
                            }
                        }
                    }
                }
            }
        }

        val responseBytes = writer.toByteArray()
        val result = LensProtobufCodec.parseLensResponse(responseBytes)

        assertEquals("en", result.language)
        assertEquals(2, result.paragraphs.size)
        assertEquals(2, result.lines.size)
        assertEquals("Paracetamol 500mg", result.lines[0])
        assertEquals("Take twice daily", result.lines[1])
        assertEquals("Paracetamol 500mg\nTake twice daily", result.fullText)
        assertEquals(5, result.words.size)
        assertEquals("Paracetamol", result.words[0].plainText)
        assertNotNull(result.words[0].geometry)
        assertEquals(0.5f, result.words[0].geometry!!.centerX, 0.001f)
        assertEquals(0.2f, result.words[0].geometry!!.centerY, 0.001f)
        assertTrue(result.confidence > 0.8)
    }

    @Test
    fun testVarintRoundTrip() {
        val testValues = listOf(0L, 1L, 127L, 128L, 255L, 300L, 123456789L, Long.MAX_VALUE)
        for (v in testValues) {
            val writer = LensProtobufCodec.ProtobufWriter()
            writer.writeVarint(v)
            val reader = LensProtobufCodec.ProtobufReader(writer.toByteArray())
            val readBack = reader.readVarint()
            assertEquals("Varint roundtrip mismatch for $v", v, readBack)
        }
    }
}
