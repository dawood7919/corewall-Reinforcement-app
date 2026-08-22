package com.corewall.qaqc.notes

import com.corewall.qaqc.data.db.NoteEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * مصدر حقيقة محتوى الملاحظة الجديد. الملاحظة وثيقة من كتل مستقلة وليس HTML
 * أو Markdown ضخم؛ لذلك يمكن إعادة ترتيب كتلة صوت أو صورة أو قائمة من غير
 * تحليل النص كله أو كسر تنسيقه.
 */
@Serializable
data class NotesDocument(
    val version: Int = 1,
    val blocks: List<NotesBlock> = listOf(NotesBlock.text())
)

@Serializable
data class NotesBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val text: String = "",
    val checked: Boolean = false,
    val mediaPath: String = "",
    val caption: String = "",
    val durationMs: Long = 0L,
    val style: TextBlockStyle = TextBlockStyle(),
    val spans: List<TextSpan> = emptyList()
) {
    companion object {
        const val TEXT = "TEXT"
        const val HEADING = "HEADING"
        const val CHECKLIST = "CHECKLIST"
        const val IMAGE = "IMAGE"
        const val DRAWING = "DRAWING"
        const val AUDIO = "AUDIO"
        const val QUOTE = "QUOTE"
        const val DIVIDER = "DIVIDER"
        const val LINK = "LINK"

        fun text(value: String = "") = NotesBlock(type = TEXT, text = value)
        fun checklist(value: String = "", checked: Boolean = false) = NotesBlock(type = CHECKLIST, text = value, checked = checked)
        fun image(path: String, caption: String = "") = NotesBlock(type = IMAGE, mediaPath = path, caption = caption)
        fun drawing(path: String, caption: String = "") = NotesBlock(type = DRAWING, mediaPath = path, caption = caption)
        fun audio(path: String, durationMs: Long = 0L) = NotesBlock(type = AUDIO, mediaPath = path, durationMs = durationMs)
        fun divider() = NotesBlock(type = DIVIDER)
    }
}

@Serializable
data class TextBlockStyle(
    val headingLevel: Int = 0,
    val alignment: String = "START",
    val bullet: Boolean = false,
    val numbered: Boolean = false
)

@Serializable
data class TextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val foregroundArgb: Long? = null,
    val highlightArgb: Long? = null
)

/** ترميز وفك ترميز وترحيل الملاحظات القديمة بأمان. */
object NotesDocumentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val image = Regex("""^!\[(.*?)]\((.+?)\)\s*$""")
    private val audio = Regex("""^\[\[audio:(.+?)]]\s*$""")
    private val file = Regex("""^\[\[file:(.+?)]]\s*$""")
    private val checklist = Regex("""^[-*]\s+\[([ xX])]\s+(.*)$""")
    private val heading = Regex("""^(#{1,3})\s+(.*)$""")

    fun decode(note: NoteEntity): NotesDocument {
        val stored = note.documentJson.trim()
        if (stored.isNotBlank()) {
            json.decodeFromString<NotesDocument>(stored).let { return ensureEditable(it) }
        }
        return migrateLegacy(note)
    }

    fun encode(document: NotesDocument): String = json.encodeToString(ensureEditable(document))

    fun mediaJson(document: NotesDocument): String = json.encodeToString(mediaPaths(document))

    fun summary(document: NotesDocument): String = document.blocks
        .asSequence()
        .filter { it.type in setOf(NotesBlock.TEXT, NotesBlock.HEADING, NotesBlock.CHECKLIST, NotesBlock.QUOTE) }
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .take(1600)

    fun mediaPaths(document: NotesDocument): List<String> = document.blocks
        .filter { it.type in setOf(NotesBlock.IMAGE, NotesBlock.DRAWING, NotesBlock.AUDIO) }
        .map { it.mediaPath }
        .filter { it.isNotBlank() }
        .distinct()

    fun migrateLegacy(note: NoteEntity): NotesDocument {
        val blocks = mutableListOf<NotesBlock>()
        note.body.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> Unit
                image.matches(line) -> {
                    val match = image.find(line)!!
                    blocks += NotesBlock.image(match.groupValues[2], match.groupValues[1])
                }
                audio.matches(line) -> blocks += NotesBlock.audio(audio.find(line)!!.groupValues[1])
                file.matches(line) -> blocks += NotesBlock(type = NotesBlock.LINK, mediaPath = file.find(line)!!.groupValues[1])
                checklist.matches(line) -> {
                    val match = checklist.find(line)!!
                    blocks += NotesBlock.checklist(match.groupValues[2], match.groupValues[1].equals("x", true))
                }
                heading.matches(line) -> {
                    val match = heading.find(line)!!
                    blocks += NotesBlock(type = NotesBlock.HEADING, text = match.groupValues[2], style = TextBlockStyle(headingLevel = match.groupValues[1].length))
                }
                line == "---" -> blocks += NotesBlock.divider()
                else -> blocks += NotesBlock.text(raw)
            }
        }
        return ensureEditable(NotesDocument(blocks = blocks))
    }

    private fun ensureEditable(document: NotesDocument): NotesDocument =
        if (document.blocks.isEmpty()) document.copy(blocks = listOf(NotesBlock.text())) else document
}
