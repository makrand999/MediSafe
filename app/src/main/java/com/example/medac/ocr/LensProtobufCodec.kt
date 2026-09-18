package com.example.medac.ocr

import java.io.ByteArrayOutputStream
import java.io.EOFException
import kotlin.math.PI
import kotlin.random.Random

object LensProtobufCodec {

    // ──────────────────────────────────────────
    // Protobuf Request Encoder
    // ──────────────────────────────────────────

    fun encodeLensOverlayRequest(
        imageBytes: ByteArray,
        width: Int,
        height: Int,
        language: String = "en",
        region: String = "US",
        timeZone: String = "America/New_York",
        uuid: Long = Random.nextLong() and Long.MAX_VALUE,
        sequenceId: Int = 1,
        imageSequenceId: Int = 1
    ): ByteArray {
        val writer = ProtobufWriter()
        // LensOverlayServerRequest -> field 1: objects_request
        writer.writeMessage(1) {
            // objects_request -> field 1: request_context
            writeMessage(1) {
                // request_context -> field 3: request_id
                writeMessage(3) {
                    writeVarintField(1, uuid)
                    writeVarintField(2, sequenceId.toLong())
                    writeVarintField(3, imageSequenceId.toLong())
                }
                // request_context -> field 4: client_context
                writeMessage(4) {
                    writeVarintField(1, 3L) // Platform.PLATFORM_WEB = 3
                    writeVarintField(2, 4L) // Surface.SURFACE_CHROMIUM = 4
                    // client_context -> field 4: locale_context
                    writeMessage(4) {
                        if (language.isNotBlank()) writeString(1, language)
                        if (region.isNotBlank()) writeString(2, region)
                        if (timeZone.isNotBlank()) writeString(3, timeZone)
                    }
                }
            }
            // objects_request -> field 3: image_data
            writeMessage(3) {
                // image_data -> field 1: payload
                writeMessage(1) {
                    // payload -> field 1: image_bytes
                    writeBytes(1, imageBytes)
                }
                // image_data -> field 3: image_metadata
                writeMessage(3) {
                    writeVarintField(1, width.toLong())
                    writeVarintField(2, height.toLong())
                }
            }
        }
        return writer.toByteArray()
    }

    // ──────────────────────────────────────────
    // Protobuf Response Parser
    // ──────────────────────────────────────────

    fun parseLensResponse(responseBytes: ByteArray): LensOcrResult {
        var detectedLanguage = ""
        val paragraphs = mutableListOf<LensParagraph>()
        val allWords = mutableListOf<LensWord>()
        var serverErrorCode: Int? = null

        val rootReader = ProtobufReader(responseBytes)
        while (rootReader.hasRemaining()) {
            val tag = rootReader.readTag()
            if (tag == 0) break
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (fieldNumber) {
                // LensOverlayServerResponse -> field 1: error
                1 -> {
                    if (wireType == 2) {
                        val errorReader = rootReader.readSubMessage()
                        while (errorReader.hasRemaining()) {
                            val eTag = errorReader.readTag()
                            if (eTag == 0) break
                            val eField = eTag ushr 3
                            val eWire = eTag and 0x07
                            if (eField == 1 && eWire == 0) {
                                serverErrorCode = errorReader.readVarint().toInt()
                            } else {
                                errorReader.skipField(eWire)
                            }
                        }
                    } else {
                        rootReader.skipField(wireType)
                    }
                }
                // LensOverlayServerResponse -> field 2: objects_response
                2 -> {
                    if (wireType == 2) {
                        val objectsReader = rootReader.readSubMessage()
                        while (objectsReader.hasRemaining()) {
                            val oTag = objectsReader.readTag()
                            if (oTag == 0) break
                            val oField = oTag ushr 3
                            val oWire = oTag and 0x07

                            // objects_response -> field 3: text
                            if (oField == 3 && oWire == 2) {
                                val textReader = objectsReader.readSubMessage()
                                while (textReader.hasRemaining()) {
                                    val tTag = textReader.readTag()
                                    if (tTag == 0) break
                                    val tField = tTag ushr 3
                                    val tWire = tTag and 0x07

                                    when (tField) {
                                        // text -> field 2: content_language
                                        2 -> if (tWire == 2) detectedLanguage = textReader.readString() else textReader.skipField(tWire)
                                        // text -> field 1: text_layout
                                        1 -> {
                                            if (tWire == 2) {
                                                val layoutReader = textReader.readSubMessage()
                                                while (layoutReader.hasRemaining()) {
                                                    val lTag = layoutReader.readTag()
                                                    if (lTag == 0) break
                                                    val lField = lTag ushr 3
                                                    val lWire = lTag and 0x07

                                                    // text_layout -> field 1: paragraphs (repeated)
                                                    if (lField == 1 && lWire == 2) {
                                                        val p = parseParagraph(layoutReader.readSubMessage(), allWords)
                                                        if (p.text.isNotBlank()) {
                                                            paragraphs.add(p)
                                                        }
                                                    } else {
                                                        layoutReader.skipField(lWire)
                                                    }
                                                }
                                            } else {
                                                textReader.skipField(tWire)
                                            }
                                        }
                                        else -> textReader.skipField(tWire)
                                    }
                                }
                            } else {
                                objectsReader.skipField(oWire)
                            }
                        }
                    } else {
                        rootReader.skipField(wireType)
                    }
                }
                else -> rootReader.skipField(wireType)
            }
        }

        if (serverErrorCode != null && serverErrorCode != 0) {
            throw IllegalStateException("Lens API error code: $serverErrorCode")
        }

        val allLines = mutableListOf<String>()
        paragraphs.forEach { p ->
            p.lines.forEach { l ->
                if (l.text.isNotBlank()) {
                    allLines.add(l.text)
                }
            }
        }

        val fullText = paragraphs.joinToString("\n") { p ->
            p.lines.joinToString("\n") { it.text }
        }.trim()

        val chars = fullText.length
        val wordCount = allWords.size
        val confidence = when {
            fullText.isBlank() -> 0.0
            chars < 8 -> 0.2
            chars < 20 -> 0.4
            wordCount == 0 -> 0.3
            chars >= 30 && paragraphs.size >= 2 -> 0.95
            wordCount >= 3 -> 0.85
            else -> 0.6
        }

        return LensOcrResult(
            fullText = fullText,
            language = detectedLanguage,
            paragraphs = paragraphs,
            lines = allLines,
            words = allWords,
            confidence = confidence
        )
    }

    private fun parseParagraph(reader: ProtobufReader, allWordsCollector: MutableList<LensWord>): LensParagraph {
        val lines = mutableListOf<LensLine>()
        var paragraphGeometry: LensGeometry? = null

        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            if (tag == 0) break
            val field = tag ushr 3
            val wire = tag and 0x07

            when (field) {
                // paragraph -> field 2: lines (repeated)
                2 -> {
                    if (wire == 2) {
                        val line = parseLine(reader.readSubMessage(), allWordsCollector)
                        if (line.text.isNotBlank()) {
                            lines.add(line)
                        }
                    } else {
                        reader.skipField(wire)
                    }
                }
                // paragraph -> field 3: geometry
                3 -> {
                    if (wire == 2) {
                        paragraphGeometry = parseGeometry(reader.readSubMessage())
                    } else {
                        reader.skipField(wire)
                    }
                }
                else -> reader.skipField(wire)
            }
        }

        val paragraphText = lines.joinToString("\n") { it.text }.trim()
        return LensParagraph(
            text = paragraphText,
            lines = lines,
            geometry = paragraphGeometry
        )
    }

    private fun parseLine(reader: ProtobufReader, allWordsCollector: MutableList<LensWord>): LensLine {
        val words = mutableListOf<LensWord>()
        var lineGeometry: LensGeometry? = null

        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            if (tag == 0) break
            val field = tag ushr 3
            val wire = tag and 0x07

            when (field) {
                // line -> field 1: words (repeated)
                1 -> {
                    if (wire == 2) {
                        val word = parseWord(reader.readSubMessage())
                        if (word.plainText.isNotBlank()) {
                            words.add(word)
                            allWordsCollector.add(word)
                        }
                    } else {
                        reader.skipField(wire)
                    }
                }
                // line -> field 2: geometry
                2 -> {
                    if (wire == 2) {
                        lineGeometry = parseGeometry(reader.readSubMessage())
                    } else {
                        reader.skipField(wire)
                    }
                }
                else -> reader.skipField(wire)
            }
        }

        val lineText = words.joinToString("") { it.plainText + it.textSeparator }.trim()
        return LensLine(
            text = lineText,
            words = words,
            geometry = lineGeometry
        )
    }

    private fun parseWord(reader: ProtobufReader): LensWord {
        var plainText = ""
        var separator = " "
        var wordGeometry: LensGeometry? = null

        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            if (tag == 0) break
            val field = tag ushr 3
            val wire = tag and 0x07

            when (field) {
                // word -> field 2: plain_text
                2 -> if (wire == 2) plainText = reader.readString() else reader.skipField(wire)
                // word -> field 3: text_separator
                3 -> if (wire == 2) separator = reader.readString() else reader.skipField(wire)
                // word -> field 4: geometry
                4 -> if (wire == 2) wordGeometry = parseGeometry(reader.readSubMessage()) else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }

        return LensWord(
            plainText = plainText,
            textSeparator = separator,
            geometry = wordGeometry
        )
    }

    private fun parseGeometry(reader: ProtobufReader): LensGeometry? {
        var box: LensGeometry? = null
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            if (tag == 0) break
            val field = tag ushr 3
            val wire = tag and 0x07

            when (field) {
                // geometry -> field 1: bounding_box
                1 -> {
                    if (wire == 2) {
                        box = parseBoundingBox(reader.readSubMessage())
                    } else {
                        reader.skipField(wire)
                    }
                }
                else -> reader.skipField(wire)
            }
        }
        return box
    }

    private fun parseBoundingBox(reader: ProtobufReader): LensGeometry {
        var cx = 0f
        var cy = 0f
        var w = 0f
        var h = 0f
        var rotZ = 0f
        var coordType = "NORMALIZED"

        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            if (tag == 0) break
            val field = tag ushr 3
            val wire = tag and 0x07

            when (field) {
                // bounding_box -> field 1: center_x (float)
                1 -> if (wire == 5) cx = reader.readFloat() else reader.skipField(wire)
                // bounding_box -> field 2: center_y (float)
                2 -> if (wire == 5) cy = reader.readFloat() else reader.skipField(wire)
                // bounding_box -> field 3: width (float)
                3 -> if (wire == 5) w = reader.readFloat() else reader.skipField(wire)
                // bounding_box -> field 4: height (float)
                4 -> if (wire == 5) h = reader.readFloat() else reader.skipField(wire)
                // bounding_box -> field 5: rotation_z (float)
                5 -> if (wire == 5) rotZ = reader.readFloat() else reader.skipField(wire)
                // bounding_box -> field 6: coordinate_type (enum)
                6 -> {
                    if (wire == 0) {
                        val enumVal = reader.readVarint().toInt()
                        coordType = if (enumVal == 1) "NORMALIZED" else "IMAGE"
                    } else {
                        reader.skipField(wire)
                    }
                }
                else -> reader.skipField(wire)
            }
        }

        val angleDeg = (rotZ * (180.0 / PI)).toFloat()
        return LensGeometry(
            centerX = cx,
            centerY = cy,
            width = w,
            height = h,
            rotationZ = rotZ,
            angleDeg = angleDeg,
            coordinateType = coordType
        )
    }

    // ──────────────────────────────────────────
    // Core Low-Level Wire Primitives
    // ──────────────────────────────────────────

    class ProtobufWriter {
        private val stream = ByteArrayOutputStream()

        fun writeTag(fieldNumber: Int, wireType: Int) {
            writeVarint(((fieldNumber shl 3) or wireType).toLong())
        }

        fun writeVarint(value: Long) {
            var v = value
            while (true) {
                if ((v and 0x7FL.inv()) == 0L) {
                    stream.write(v.toInt())
                    return
                } else {
                    stream.write(((v.toInt() and 0x7F) or 0x80))
                    v = v ushr 7
                }
            }
        }

        fun writeVarintField(fieldNumber: Int, value: Long) {
            writeTag(fieldNumber, 0)
            writeVarint(value)
        }

        fun writeFixed32Field(fieldNumber: Int, value: Float) {
            writeTag(fieldNumber, 5)
            val bits = java.lang.Float.floatToRawIntBits(value)
            stream.write(bits and 0xFF)
            stream.write((bits ushr 8) and 0xFF)
            stream.write((bits ushr 16) and 0xFF)
            stream.write((bits ushr 24) and 0xFF)
        }

        fun writeBytes(fieldNumber: Int, bytes: ByteArray) {
            writeTag(fieldNumber, 2)
            writeVarint(bytes.size.toLong())
            stream.write(bytes)
        }

        fun writeString(fieldNumber: Int, value: String) {
            writeBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))
        }

        fun writeMessage(fieldNumber: Int, block: ProtobufWriter.() -> Unit) {
            val sub = ProtobufWriter()
            sub.block()
            val bytes = sub.toByteArray()
            writeBytes(fieldNumber, bytes)
        }

        fun toByteArray(): ByteArray = stream.toByteArray()
    }

    class ProtobufReader(
        private val buffer: ByteArray,
        private var position: Int = 0,
        private val limit: Int = buffer.size
    ) {
        fun hasRemaining(): Boolean = position < limit

        fun readTag(): Int {
            if (position >= limit) return 0
            return readVarint().toInt()
        }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (position >= limit) throw EOFException("Unexpected end of protobuf stream")
                val b = buffer[position++].toLong()
                result = result or ((b and 0x7FL) shl shift)
                if ((b and 0x80L) == 0L) return result
                shift += 7
            }
            throw IllegalStateException("Malformed varint")
        }

        fun readFixed32(): Int {
            if (position + 4 > limit) throw EOFException("Unexpected end of protobuf stream")
            val b0 = buffer[position++].toInt() and 0xFF
            val b1 = buffer[position++].toInt() and 0xFF
            val b2 = buffer[position++].toInt() and 0xFF
            val b3 = buffer[position++].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun readFloat(): Float = java.lang.Float.intBitsToFloat(readFixed32())

        fun readFixed64(): Long {
            val low = readFixed32().toLong() and 0xFFFFFFFFL
            val high = readFixed32().toLong() and 0xFFFFFFFFL
            return low or (high shl 32)
        }

        fun readDouble(): Double = java.lang.Double.longBitsToDouble(readFixed64())

        fun readBytes(): ByteArray {
            val len = readVarint().toInt()
            if (len < 0 || position + len > limit) throw EOFException("Invalid protobuf byte length: $len")
            val res = buffer.copyOfRange(position, position + len)
            position += len
            return res
        }

        fun readString(): String = String(readBytes(), Charsets.UTF_8)

        fun readSubMessage(): ProtobufReader {
            val len = readVarint().toInt()
            if (len < 0 || position + len > limit) throw EOFException("Invalid submessage byte length: $len")
            val sub = ProtobufReader(buffer, position, position + len)
            position += len
            return sub
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> {
                    if (position + 8 > limit) throw EOFException("Unexpected end skipping fixed64")
                    position += 8
                }
                2 -> {
                    val len = readVarint().toInt()
                    if (len < 0 || position + len > limit) throw EOFException("Invalid length skipping length-delimited")
                    position += len
                }
                5 -> {
                    if (position + 4 > limit) throw EOFException("Unexpected end skipping fixed32")
                    position += 4
                }
                else -> throw IllegalStateException("Unsupported wire type: $wireType")
            }
        }
    }
}
