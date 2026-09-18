package com.example.medac.ocr

data class LensGeometry(
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val rotationZ: Float = 0f,
    val angleDeg: Float = 0f,
    val coordinateType: String = "NORMALIZED"
)

data class LensWord(
    val plainText: String,
    val textSeparator: String = " ",
    val geometry: LensGeometry? = null
)

data class LensLine(
    val text: String,
    val words: List<LensWord> = emptyList(),
    val geometry: LensGeometry? = null
)

data class LensParagraph(
    val text: String,
    val lines: List<LensLine> = emptyList(),
    val geometry: LensGeometry? = null
)

data class LensOcrResult(
    val fullText: String,
    val language: String = "",
    val paragraphs: List<LensParagraph> = emptyList(),
    val lines: List<String> = emptyList(),
    val words: List<LensWord> = emptyList(),
    val confidence: Double = 0.0
)
