package com.corewall.qaqc.creative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.corewall.qaqc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** مخرجات مساندة من نفس نموذج المستند: صورة صفحة أولى، Word متوافق، وحزمة نسخ. */
object CreativeSecondaryExporters {
    private fun outputDir(context: Context) = File(context.filesDir, "creative-documents").apply { mkdirs() }
    private fun safeName(title: String) = title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(48).ifBlank { "corewall_report" }
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    suspend fun image(context: Context, content: CreativeDocumentContent): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val width = 1240; val height = 1754; val margin = 88f
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val regular = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_regular) ?: Typeface.DEFAULT
            val bold = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_semibold) ?: Typeface.DEFAULT_BOLD
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; color = Color.rgb(31, 41, 55); textAlign = Paint.Align.RIGHT }
            var y = margin
            fun text(value: String, size: Float, strong: Boolean = false, color: Int = Color.rgb(31, 41, 55)) {
                paint.textSize = size; paint.typeface = if (strong) bold else regular; paint.color = color
                val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }; var line = ""
                val lines = mutableListOf<String>()
                words.forEach { word -> val next = if (line.isBlank()) word else "$line $word"; if (paint.measureText(next) < width - margin * 2) line = next else { lines += line; line = word } }
                if (line.isNotBlank()) lines += line
                lines.forEach { lineText ->
                    if (y > height - margin) return@forEach
                    canvas.drawText(lineText, width - margin, y, paint); y += size * 1.5f
                }
                y += size * .3f
            }
            paint.color = Color.rgb(22, 119, 255); canvas.drawRect(margin, y, width - margin, y + 8f, paint); y += 60f
            text(content.title, 54f, true, Color.rgb(14, 47, 85)); text(content.subtitle, 24f, false, Color.rgb(102, 112, 122)); y += 24f
            content.blocks.forEach { block ->
                when (block.kind) {
                    CreativeBlockKind.HEADING -> text(block.text, 36f, true, Color.rgb(18, 70, 135))
                    CreativeBlockKind.BULLETS -> block.items.forEach { text("• $it", 27f) }
                    CreativeBlockKind.TABLE -> block.rows.forEach { text(it.cells.joinToString(" | "), 23f) }
                    else -> text(block.text, 28f, false)
                }
            }
            val out = File(outputDir(context), "${safeName(content.title)}_${stamp()}.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle(); out
        }
    }

    /** ملف .doc مبني HTML/RTL يفتحه Microsoft Word للتحرير دون مكتبات مكتبية ثقيلة داخل APK. */
    suspend fun wordCompatible(context: Context, content: CreativeDocumentContent): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>")
            val body = buildString {
                append("<h1>${escape(content.title)}</h1><p class='sub'>${escape(content.subtitle)}</p>")
                content.blocks.forEach { block -> when (block.kind) {
                    CreativeBlockKind.HEADING -> append("<h2>${escape(block.text)}</h2>")
                    CreativeBlockKind.PARAGRAPH, CreativeBlockKind.CALLOUT -> append("<p>${escape(block.text)}</p>")
                    CreativeBlockKind.BULLETS -> append("<ul>${block.items.joinToString("") { "<li>${escape(it)}</li>" }}</ul>")
                    CreativeBlockKind.TABLE -> append("<table>${block.rows.joinToString("") { row -> "<tr>${row.cells.joinToString("") { "<td>${escape(it)}</td>" }}</tr>" }}</table>")
                    CreativeBlockKind.IMAGE -> if (block.imagePath.isNotBlank()) append("<p>[صورة مرفقة في النسخة الأصلية: ${escape(block.caption)}]</p>")
                } }
            }
            val html = """<!DOCTYPE html><html dir='rtl'><head><meta charset='utf-8'><style>body{font-family:Arial,sans-serif;line-height:1.8;color:#18212b;margin:42px}h1{color:#0e2f55;border-bottom:4px solid #1677ff;padding-bottom:12px}h2{color:#124687;margin-top:28px}.sub{color:#66707a}table{border-collapse:collapse;width:100%;margin:14px 0}td{border:1px solid #cbd5e1;padding:8px}tr:first-child td{font-weight:bold;background:#e2efff}</style></head><body>$body</body></html>"""
            val out = File(outputDir(context), "${safeName(content.title)}_${stamp()}.doc")
            out.writeText(html, Charsets.UTF_8)
            out
        }
    }

    suspend fun packageFiles(context: Context, title: String, files: List<File>): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(outputDir(context), "${safeName(title)}_${stamp()}_package.zip")
            ZipOutputStream(FileOutputStream(out)).use { zip ->
                files.filter(File::exists).forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out
        }
    }
}
