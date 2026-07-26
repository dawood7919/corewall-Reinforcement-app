package com.corewall.qaqc.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val liveToken = Regex(
    """(\*\*.+?\*\*)|(__.+?__)|(~~.+?~~)|(\*.+?\*)|(_.+?_)|(`.+?`)|(==.+?==)|(#[\p{L}\w-]+)|(@[\p{L}\w-]+)"""
)

/**
 * تنسيق حيّ للـMarkdown أثناء الكتابة (زي Obsidian): بيلوّن ويكبّر العناوين
 * والغامق والوسوم… من غير ما يغيّر طول النص (فالمؤشر بيفضل مظبوط تماماً).
 * علامات الماركداون بتفضل ظاهرة لكن باهتة/منسّقة.
 */
fun liveMarkdownTransformation(
    colors: InlineColors,
    accent: Color,
    muted: Color,
    query: String
): VisualTransformation = VisualTransformation { text ->
    val src = text.text
    val annotated: AnnotatedString = buildAnnotatedString {
        append(src)
        var off = 0
        for (line in src.split("\n")) {
            val start = off
            val end = off + line.length
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("# ") -> addStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), start, end)
                trimmed.startsWith("## ") -> addStyle(SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold), start, end)
                trimmed.startsWith("### ") -> addStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), start, end)
                trimmed.startsWith(">") -> addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = muted), start, end)
                trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") ->
                    addStyle(SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough), start, end)
            }
            for (m in liveToken.findAll(line)) {
                val s = start + m.range.first
                val e = start + m.range.last + 1
                val t = m.value
                val style = when {
                    t.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold)
                    t.startsWith("__") -> SpanStyle(textDecoration = TextDecoration.Underline)
                    t.startsWith("~~") -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    t.startsWith("==") -> SpanStyle(background = colors.highlight)
                    t.startsWith("`") -> SpanStyle(color = colors.code, background = colors.codeBg)
                    t.startsWith("#") -> SpanStyle(color = colors.tag, fontWeight = FontWeight.Medium)
                    t.startsWith("@") -> SpanStyle(color = colors.mention, fontWeight = FontWeight.Medium)
                    t.startsWith("*") || t.startsWith("_") -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> SpanStyle()
                }
                addStyle(style, s, e)
            }
            if (query.isNotBlank()) {
                var idx = line.indexOf(query, ignoreCase = true)
                while (idx >= 0) {
                    addStyle(SpanStyle(background = Color(0xFFFFE066), color = Color(0xFF1A1A1A)), start + idx, start + idx + query.length)
                    idx = line.indexOf(query, idx + 1, ignoreCase = true)
                }
            }
            off = end + 1
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}
