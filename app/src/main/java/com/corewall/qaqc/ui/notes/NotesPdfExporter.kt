package com.corewall.qaqc.ui.notes

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.notes.NotesDocument
import java.io.File

/** تصدير محلي للوثيقة؛ يبقي الصور والرسومات جزءاً مرئياً من ملف PDF المشترك. */
object NotesPdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42

    fun export(target: File, title: String, document: NotesDocument): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(26, 29, 34) }
        fun nextPage() {
            pdf.finishPage(page)
            pageNumber += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }
        fun line(text: String, size: Float = 13f, bold: Boolean = false, prefix: String = "") {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val words = (prefix + text).split(Regex("\\s+"))
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) > PAGE_W - (MARGIN * 2) && current.isNotBlank()) {
                    if (y + size + 12 > PAGE_H - MARGIN) nextPage()
                    canvas.drawText(current, MARGIN.toFloat(), y.toFloat(), paint)
                    y += (size + 9).toInt()
                    current = word
                } else current = candidate
            }
            if (current.isNotBlank()) {
                if (y + size + 12 > PAGE_H - MARGIN) nextPage()
                canvas.drawText(current, MARGIN.toFloat(), y.toFloat(), paint)
                y += (size + 11).toInt()
            }
        }
        line(title.ifBlank { "ملاحظة" }, 24f, true)
        y += 10
        document.blocks.forEach { block ->
            when (block.type) {
                NotesBlock.HEADING -> line(block.text, if (block.style.headingLevel <= 1) 20f else 17f, true)
                NotesBlock.TEXT, NotesBlock.QUOTE -> line(block.text, 13f, false, if (block.type == NotesBlock.QUOTE) "“ " else "")
                NotesBlock.CHECKLIST -> line(block.text, 13f, false, if (block.checked) "☑ " else "☐ ")
                NotesBlock.IMAGE, NotesBlock.DRAWING -> {
                    val bitmap = BitmapFactory.decodeFile(block.mediaPath)
                    if (bitmap != null) {
                        val maxW = PAGE_W - (MARGIN * 2)
                        val h = (bitmap.height * (maxW.toFloat() / bitmap.width)).toInt().coerceAtMost(300)
                        if (y + h + 20 > PAGE_H - MARGIN) nextPage()
                        canvas.drawBitmap(bitmap, null, android.graphics.Rect(MARGIN, y, MARGIN + maxW, y + h), paint)
                        y += h + 16
                        bitmap.recycle()
                    }
                }
                NotesBlock.AUDIO -> line("تسجيل صوتي: ${File(block.mediaPath).name}", 12f, false, "🎙 ")
                NotesBlock.DIVIDER -> { paint.strokeWidth = 1f; canvas.drawLine(MARGIN.toFloat(), y.toFloat(), (PAGE_W - MARGIN).toFloat(), y.toFloat(), paint); y += 18 }
            }
            y += 5
        }
        pdf.finishPage(page)
        target.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        target.exists() && target.length() > 0
    }.getOrDefault(false)
}
