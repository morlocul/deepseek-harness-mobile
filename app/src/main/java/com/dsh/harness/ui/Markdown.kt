package com.dsh.harness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Seg(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)

private fun parseInline(src: String): List<Seg> {
    val out = ArrayList<Seg>()
    val pattern = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)")
    var pos = 0
    for (m in pattern.findAll(src)) {
        if (m.range.first > pos) out.add(Seg(src.substring(pos, m.range.first)))
        val v = m.value
        out.add(
            when {
                v.startsWith("`") -> Seg(v.substring(1, v.length - 1), code = true)
                v.startsWith("**") -> Seg(v.substring(2, v.length - 2), bold = true)
                else -> Seg(v.substring(1, v.length - 1), italic = true)
            }
        )
        pos = m.range.last + 1
    }
    if (pos < src.length) out.add(Seg(src.substring(pos)))
    if (out.isEmpty()) out.add(Seg(""))
    return out
}

private sealed interface Block
private data class CodeBlock(val text: String) : Block
private data class Para(val content: String) : Block
private data class H(val content: String, val level: Int) : Block
private data class ListBlock(val content: String) : Block

private fun parseBlocks(src: String): List<Block> {
    val lines = src.split("\n")
    val blocks = ArrayList<Block>()
    var i = 0
    val codeBuf = StringBuilder()
    fun flushCode() {
        if (codeBuf.isNotEmpty()) {
            blocks.add(CodeBlock(codeBuf.toString().trimEnd('\n')))
            codeBuf.clear()
        }
    }
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushCode()
            var j = i + 1
            while (j < lines.size && !lines[j].trimStart().startsWith("```")) {
                codeBuf.append(lines[j]).append('\n')
                j++
            }
            i = j + 1
            flushCode()
            continue
        }
        flushCode()
        val heading = Regex("^(#{1,3})\\s+(.*)$").find(trimmed)
        if (heading != null) {
            blocks.add(H(heading.groupValues[2], heading.groupValues[1].length))
        } else if (trimmed.matches(Regex("^\\s*([-*+]|\\d+\\.)\\s+.*"))) {
            val content = trimmed
                .replaceFirst(Regex("^\\s*[-*+]\\s+"), "• ")
                .replaceFirst(Regex("^\\s*\\d+\\.\\s+"), "• ")
            blocks.add(ListBlock(content))
        } else if (trimmed.isNotBlank()) {
            blocks.add(Para(line.trim()))
        }
        i++
    }
    flushCode()
    return blocks
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) {
            when (b) {
                is CodeBlock -> CodeCardBlock(b.text)
                is H -> Text(
                    inlineAnnotated(b.content),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold
                )
                is ListBlock -> Text(inlineAnnotated(b.content), style = MaterialTheme.typography.bodyMedium)
                is Para -> Text(inlineAnnotated(b.content), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun inlineAnnotated(text: String) = buildAnnotatedString {
    val segs = parseInline(text)
    for (s in segs) {
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (s.italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (s.code) FontFamily.Monospace else FontFamily.Default,
            background = if (s.code) Color(0xFF2A2F3A) else Color.Unspecified,
            color = if (s.code) Color(0xFF9CDCFC) else Color.Unspecified
        )
        withStyle(style) { append(s.text) }
    }
}

@Composable
private fun CodeCardBlock(text: String) {
    val dotColors = listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1218))
            .border(1.dp, Color(0xFF242933), RoundedCornerShape(10.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF171C24)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dotColors.forEach { c -> Box(Modifier.size(8.dp).background(c, CircleShape)); Spacer(Modifier.width(6.dp)) }
            Spacer(Modifier.weight(1f))
            Text("CODE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                letterSpacing = 1.sp, color = Color(0xFF5C6672))
        }
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color(0xFFE6EDF3),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            maxLines = 200,
            overflow = TextOverflow.Visible
        )
    }
}
