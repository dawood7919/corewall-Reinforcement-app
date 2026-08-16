package com.corewall.qaqc.creative

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.corewall.qaqc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** رندر حتمي للمسودة إلى PDF؛ يستخدم Canvas أندرويد ليحافظ على تشكيل العربية واتجاه RTL. */
object CreativePdfExporter {
    private const val PAGE_W = 1240
    private const val PAGE_H = 1754
    private const val MARGIN = 92f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    suspend fun export(context: Context, documentId: Long, content: CreativeDocumentContent): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "creative-documents").apply { mkdirs() }
                val safe = content.title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(48).ifBlank { "corewall_report" }
                val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val out = File(dir, "${safe}_${date}.pdf")
                val pdf = PdfDocument()
                val typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_regular) ?: Typeface.DEFAULT
                val bold = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_semibold) ?: Typeface.DEFAULT_BOLD
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; color = Color.rgb(24, 33, 43) }
                var pageNo = 0
                var page: PdfDocument.Page? = null
                var canvas: Canvas? = null
                var y = MARGIN

                fun newPage() {
                    page?.let(pdf::finishPage)
                    pageNo++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                    canvas = page!!.canvas
                    y = MARGIN
                    canvas!!.drawColor(Color.WHITE)
                    paint.color = Color.rgb(22, 119, 255)
                    canvas!!.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 8f, paint)
                    y += 34f
                    paint.color = Color.rgb(102, 112, 122)
                    paint.textSize = 22f
                    paint.typeface = typeface
                    canvas!!.drawText("Core Wall · ${content.subtitle}", PAGE_W - MARGIN, y, paint.apply { textAlign = Paint.Align.RIGHT })
                    y += 38f
                }

                fun ensure(height: Float) { if (y + height > PAGE_H - MARGIN - 56f) newPage() }
                fun lines(text: String, textSize: Float): List<String> {
                    paint.textSize = textSize
                    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (words.isEmpty()) return emptyList()
                    val output = mutableListOf<String>(); var line = ""
                    words.forEach { word ->
                        val next = if (line.isBlank()) word else "$line $word"
                        if (paint.measureText(next) <= CONTENT_W) line = next else { output += line; line = word }
                    }
                    if (line.isNotBlank()) output += line
                    return output
                }
                fun paragraph(text: String, size: Float = 29f, color: Int = Color.rgb(43, 53, 63)) {
                    paint.typeface = typeface; paint.color = color; paint.textSize = size; paint.textAlign = Paint.Align.RIGHT
                    val rows = lines(text, size)
                    ensure(rows.size * (size * 1.65f) + 20f)
                    rows.forEach { row -> canvas!!.drawText(row, PAGE_W - MARGIN, y, paint); y += size * 1.65f }
                    y += 16f
                }
                fun heading(text: String) {
                    paint.typeface = bold; paint.color = Color.rgb(18, 70, 135); paint.textSize = 40f; paint.textAlign = Paint.Align.RIGHT
                    ensure(84f); canvas!!.drawText(text, PAGE_W - MARGIN, y, paint); y += 62f
                }
                fun table(rows: List<CreativeTableRow>) {
                    if (rows.isEmpty()) return
                    val cols = rows.maxOf { it.cells.size }.coerceAtLeast(1)
                    val cellW = CONTENT_W / cols
                    val rowH = 56f
                    rows.forEachIndexed { rowIndex, row ->
                        ensure(rowH + 8f)
                        row.cells.forEachIndexed { col, cell ->
                            val left = MARGIN + col * cellW
                            paint.style = Paint.Style.FILL
                            paint.color = if (rowIndex == 0) Color.rgb(226, 239, 255) else Color.rgb(248, 250, 252)
                            canvas!!.drawRect(left, y, left + cellW - 2f, y + rowH - 2f, paint)
                            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f; paint.color = Color.rgb(190, 200, 211)
                            canvas!!.drawRect(left, y, left + cellW - 2f, y + rowH - 2f, paint)
                            paint.style = Paint.Style.FILL; paint.typeface = if (rowIndex == 0) bold else typeface; paint.color = Color.rgb(36, 48, 60); paint.textSize = 19f; paint.textAlign = Paint.Align.CENTER
                            canvas!!.drawText(cell.take(32), left + cellW / 2f, y + 35f, paint)
                        }
                        y += rowH
                    }
                    y += 20f
                }

                newPage()
                paint.typeface = bold; paint.color = Color.rgb(14, 47, 85); paint.textSize = 58f; paint.textAlign = Paint.Align.RIGHT
                val titleLines = lines(content.title, 58f)
                titleLines.forEach { canvas!!.drawText(it, PAGE_W - MARGIN, y, paint); y += 80f }
                y += 36f
                content.blocks.forEach { block ->
                    when (block.kind) {
                        CreativeBlockKind.HEADING -> heading(block.text)
                        CreativeBlockKind.PARAGRAPH -> paragraph(block.text)
                        CreativeBlockKind.CALLOUT -> paragraph(block.text, color = Color.rgb(18, 110, 90))
                        CreativeBlockKind.BULLETS -> block.items.forEach { paragraph("• $it", 27f) }
                        CreativeBlockKind.TABLE -> table(block.rows)
                        CreativeBlockKind.IMAGE -> if (block.imagePath.isNotBlank()) {
                            val bitmap = BitmapFactory.decodeFile(block.imagePath)
                            if (bitmap != null) {
                                val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
                                val h = (CONTENT_W * ratio).coerceAtMost(480f)
                                ensure(h + 48f)
                                canvas!!.drawBitmap(bitmap, null, android.graphics.RectF(MARGIN, y, PAGE_W - MARGIN, y + h), paint)
                                y += h + 16f
                                if (block.caption.isNotBlank()) paragraph(block.caption, 22f, Color.rgb(102, 112, 122))
                            }
                        }
                    }
                }
                paint.typeface = typeface; paint.color = Color.rgb(122, 132, 143); paint.textSize = 18f; paint.textAlign = Paint.Align.CENTER
                canvas!!.drawText("تم الإنشاء بواسطة Core Wall", PAGE_W / 2f, PAGE_H - 48f, paint)
                page?.let(pdf::finishPage)
                FileOutputStream(out).use(pdf::writeTo)
                pdf.close()
                out
            }
        }
}
