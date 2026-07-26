package com.corewall.qaqc.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * محرّك Markdown خفيف مخصّص لدفتر الملاحظات الهندسي:
 * بيحوّل نص Markdown لقايمة بلوكات جاهزة للعرض، مع الحفاظ على ترتيب
 * كل العناصر (نص/صور/ملفات/تشيك ليست) زي ما اتكتبت بالظبط.
 */

private val jsonFmt = Json { ignoreUnknownKeys = true }

fun parseImagePaths(jsonStr: String): List<String> =
    runCatching { jsonFmt.decodeFromString<List<String>>(jsonStr) }.getOrDefault(emptyList())

fun encodeImagePaths(paths: List<String>): String = jsonFmt.encodeToString(paths)

enum class CalloutKind(val token: String, val label: String) {
    INFO("info", "معلومة"),
    WARNING("warning", "تحذير"),
    DANGER("danger", "خطر"),
    INSPECTION("inspection", "مطلوب فحص"),
    APPROVED("approved", "مقبول"),
    REJECTED("rejected", "مرفوض");

    companion object {
        fun from(token: String) = entries.firstOrNull { it.token.equals(token, true) } ?: INFO
    }
}

sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
    data class BulletList(val items: List<String>) : NoteBlock
    data class NumberedList(val items: List<String>) : NoteBlock
    data class CheckItem(val checked: Boolean, val text: String, val sourceLine: Int) : NoteBlock
    data class Quote(val text: String) : NoteBlock
    data class Code(val language: String, val code: String) : NoteBlock
    data object Divider : NoteBlock
    data class Callout(val kind: CalloutKind, val title: String, val body: List<String>) : NoteBlock
    data class Image(val caption: String, val path: String) : NoteBlock
    data class FileCard(val path: String) : NoteBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : NoteBlock
}

private val imageRegex = Regex("""^!\[(.*?)]\((.+?)\)\s*$""")
private val fileRegex = Regex("""^\[\[file:(.+?)]]\s*$""")
private val calloutRegex = Regex("""^>\s*\[!(\w+)]\s*(.*)$""")
private val checkRegex = Regex("""^[-*]\s+\[( |x|X)]\s+(.*)$""")

/** بيحلّل نص Markdown كامل لقايمة بلوكات مرتّبة. */
fun parseNote(markdown: String): List<NoteBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<NoteBlock>()
    var i = 0

    fun flushBullets(buf: MutableList<String>) {
        if (buf.isNotEmpty()) { blocks.add(NoteBlock.BulletList(buf.toList())); buf.clear() }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> i++

            // كود مسيّج ```
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++ // يتخطى ```
                blocks.add(NoteBlock.Code(lang, code.toString().trimEnd('\n')))
            }

            // Callout
            calloutRegex.matches(line) -> {
                val m = calloutRegex.find(line)!!
                val kind = CalloutKind.from(m.groupValues[1])
                val title = m.groupValues[2].trim()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trimStart().startsWith(">") &&
                    !calloutRegex.matches(lines[i].trimEnd())
                ) {
                    body.add(lines[i].trimStart().removePrefix(">").trim()); i++
                }
                blocks.add(NoteBlock.Callout(kind, title, body))
            }

            imageRegex.matches(line) -> {
                val m = imageRegex.find(line)!!
                blocks.add(NoteBlock.Image(m.groupValues[1], m.groupValues[2])); i++
            }

            fileRegex.matches(line) -> {
                blocks.add(NoteBlock.FileCard(fileRegex.find(line)!!.groupValues[1])); i++
            }

            trimmed == "---" || trimmed == "***" -> { blocks.add(NoteBlock.Divider); i++ }

            trimmed.startsWith("### ") -> { blocks.add(NoteBlock.Heading(3, trimmed.removePrefix("### "))); i++ }
            trimmed.startsWith("## ") -> { blocks.add(NoteBlock.Heading(2, trimmed.removePrefix("## "))); i++ }
            trimmed.startsWith("# ") -> { blocks.add(NoteBlock.Heading(1, trimmed.removePrefix("# "))); i++ }

            checkRegex.matches(line) -> {
                val m = checkRegex.find(line)!!
                blocks.add(NoteBlock.CheckItem(m.groupValues[1].lowercase() == "x", m.groupValues[2], i)); i++
            }

            line.trimStart().startsWith(">") -> {
                blocks.add(NoteBlock.Quote(line.trimStart().removePrefix(">").trim())); i++
            }

            // جدول
            trimmed.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("""^\|?[\s:|-]+\|?$""")) -> {
                val header = splitRow(trimmed)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows.add(splitRow(lines[i].trim())); i++
                }
                blocks.add(NoteBlock.Table(header, rows))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val buf = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if ((t.startsWith("- ") || t.startsWith("* ")) && !checkRegex.matches(lines[i].trimEnd())) {
                        buf.add(t.drop(2)); i++
                    } else break
                }
                flushBullets(buf)
            }

            Regex("""^\d+\.\s""").containsMatchIn(trimmed) -> {
                val buf = mutableListOf<String>()
                while (i < lines.size && Regex("""^\d+\.\s""").containsMatchIn(lines[i].trim())) {
                    buf.add(lines[i].trim().substringAfter(". ")); i++
                }
                blocks.add(NoteBlock.NumberedList(buf.toList()))
            }

            else -> { blocks.add(NoteBlock.Paragraph(line)); i++ }
        }
    }
    return blocks
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }

// ---------------------------------------------------------------- Inline

data class InlineColors(
    val code: Color,
    val codeBg: Color,
    val highlight: Color,
    val tag: Color,
    val tagBg: Color,
    val mention: Color,
    val mentionBg: Color
)

private val tokenRegex = Regex(
    """(\*\*.+?\*\*)|(__.+?__)|(~~.+?~~)|(\*.+?\*)|(_.+?_)|(`.+?`)|(==.+?==)|(#[\p{L}\w-]+)|(@[\p{L}\w-]+)"""
)

/** تحويل سطر Markdown لـ AnnotatedString بتنسيقات inline (غامق/مائل/شطب/كود/تظليل/وسم/منشن). */
fun inlineAnnotated(text: String, c: InlineColors): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in tokenRegex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val t = m.value
        when {
            t.startsWith("**") && t.endsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.removeSurrounding("**")) }
            t.startsWith("__") && t.endsWith("__") ->
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(t.removeSurrounding("__")) }
            t.startsWith("~~") && t.endsWith("~~") ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(t.removeSurrounding("~~")) }
            t.startsWith("==") && t.endsWith("==") ->
                withStyle(SpanStyle(background = c.highlight)) { append(t.removeSurrounding("==")) }
            t.startsWith("`") && t.endsWith("`") ->
                withStyle(SpanStyle(background = c.codeBg, color = c.code)) { append(" " + t.removeSurrounding("`") + " ") }
            (t.startsWith("*") && t.endsWith("*")) || (t.startsWith("_") && t.endsWith("_")) ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.substring(1, t.length - 1)) }
            t.startsWith("#") ->
                withStyle(SpanStyle(color = c.tag, background = c.tagBg, fontWeight = FontWeight.Medium)) { append(" $t ") }
            t.startsWith("@") ->
                withStyle(SpanStyle(color = c.mention, background = c.mentionBg, fontWeight = FontWeight.Medium)) { append(" $t ") }
            else -> append(t)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

/** عدّاد التشيك ليست (منجز/إجمالي) من نص الملاحظة. */
fun checklistProgress(markdown: String): Pair<Int, Int> {
    val items = markdown.split("\n").filter { checkRegex.matches(it.trimEnd()) }
    val done = items.count { checkRegex.find(it.trimEnd())!!.groupValues[1].lowercase() == "x" }
    return done to items.size
}

/** أول سطر نصّي مفيد كمعاينة للكارت. */
fun notePreview(markdown: String): String {
    for (raw in markdown.split("\n")) {
        val l = raw.trim()
        if (l.isEmpty()) continue
        if (imageRegex.matches(l) || fileRegex.matches(l) || l == "---") continue
        return l.replace(Regex("""[#>*_`~=\[\]]"""), "").trim().take(120)
    }
    return ""
}

fun countImages(markdown: String) = markdown.split("\n").count { imageRegex.matches(it.trim()) }
fun countFiles(markdown: String) = markdown.split("\n").count { fileRegex.matches(it.trim()) }
