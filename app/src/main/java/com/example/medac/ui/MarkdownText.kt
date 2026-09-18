package com.example.medac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medac.ui.theme.BorderSubtle
import com.example.medac.ui.theme.CardSurface
import com.example.medac.ui.theme.DividerMuted
import com.example.medac.ui.theme.TextPrimary
import com.example.medac.ui.theme.TextSecondary

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = color)
                        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = color)
                        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = color)
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, color),
                        style = headerStyle,
                        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, color),
                        style = style,
                        color = color
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 12).dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, color),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            style = style.copy(fontWeight = FontWeight.Medium),
                            color = TextSecondary
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, color),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .background(TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                    .padding(vertical = 4.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text, color),
                                style = style.copy(fontStyle = FontStyle.Italic),
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            )
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        color = DividerMuted,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val level: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var inCodeBlock = false
    var codeBlockLang = ""
    val codeLines = mutableListOf<String>()
    var quoteLines = mutableListOf<String>()

    fun flushQuote() {
        if (quoteLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
            quoteLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeBlockLang, codeLines.joinToString("\n")))
                codeLines.clear()
                inCodeBlock = false
            } else {
                flushQuote()
                inCodeBlock = true
                codeBlockLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            continue
        }

        if (trimmed.startsWith(">")) {
            quoteLines.add(trimmed.removePrefix(">").trim())
            continue
        } else {
            flushQuote()
        }

        if (trimmed.isEmpty()) {
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.HorizontalRule)
            continue
        }

        // Headers
        if (trimmed.startsWith("#")) {
            val headerMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val text = headerMatch.groupValues[2]
                blocks.add(MarkdownBlock.Header(level, text))
                continue
            }
        }

        // Bullet lists
        val bulletMatch = Regex("^([\\s]*)[-*+•]\\s+(.*)$").find(line)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1].length / 2
            val text = bulletMatch.groupValues[2]
            blocks.add(MarkdownBlock.BulletItem(indent, text))
            continue
        }

        // Numbered lists
        val numMatch = Regex("^(\\d+[.)])\\s+(.*)$").find(trimmed)
        if (numMatch != null) {
            val number = numMatch.groupValues[1]
            val text = numMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(number, text))
            continue
        }

        // Default: Paragraph
        blocks.add(MarkdownBlock.Paragraph(trimmed))
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.CodeBlock(codeBlockLang, codeLines.joinToString("\n")))
    }
    flushQuote()

    return blocks
}

fun parseInlineMarkdown(text: String, defaultColor: Color = Color.Unspecified): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val tokenRegex = Regex(
            """(`[^`]+`)|""" +                                      // 1: inline code
            """(\*\*\*[^*]+\*\*\*|___[^_]+___)|""" +               // 2: bold italic
            """(\*\*[^*]+\*\*|__[^_]+__)|""" +                     // 3: bold
            """(\*[^*]+\*|_[^_]+_)|""" +                           // 4: italic
            """(~~[^~]+~~)|""" +                                   // 5: strikethrough
            """(\[[^\]]+\]\([^)]+\))"""                            // 6: link
        )

        tokenRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val matchedValue = match.value
            when {
                // Inline code: `code`
                matchedValue.startsWith("`") && matchedValue.endsWith("`") -> {
                    val code = matchedValue.removeSurrounding("`")
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            background = Color(0x18000000),
                            color = defaultColor
                        )
                    )
                    append(" $code ")
                    pop()
                }
                // Bold + Italic: ***text*** or ___text___
                matchedValue.startsWith("***") || matchedValue.startsWith("___") -> {
                    val content = matchedValue.substring(3, matchedValue.length - 3)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = defaultColor))
                    append(content)
                    pop()
                }
                // Bold: **text** or __text__
                matchedValue.startsWith("**") || matchedValue.startsWith("__") -> {
                    val content = matchedValue.substring(2, matchedValue.length - 2)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(content)
                    pop()
                }
                // Italic: *text* or _text_
                matchedValue.startsWith("*") || matchedValue.startsWith("_") -> {
                    val content = matchedValue.substring(1, matchedValue.length - 1)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(content)
                    pop()
                }
                // Strikethrough: ~~text~~
                matchedValue.startsWith("~~") && matchedValue.endsWith("~~") -> {
                    val content = matchedValue.removeSurrounding("~~")
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = defaultColor))
                    append(content)
                    pop()
                }
                // Link: [label](url)
                matchedValue.startsWith("[") && matchedValue.contains("](") -> {
                    val linkMatch = Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(matchedValue)
                    if (linkMatch != null) {
                        val label = linkMatch.groupValues[1]
                        pushStyle(
                            SpanStyle(
                                color = Color(0xFF2563EB),
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(label)
                        pop()
                    } else {
                        append(matchedValue)
                    }
                }
                else -> append(matchedValue)
            }

            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
