package com.corewall.qaqc.ai.docs

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.corewall.qaqc.ui.cad.DxfParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * استخراج محتوى الملفات **من غير أي مكتبة خارجية**:
 *  - نصوص (txt/csv/json/xml/md) تتقري مباشرة
 *  - XLSX و DOCX أصلاً ملفات ZIP جواها XML — بنفكّها ونقرا النص
 *  - DXF بيتقري بالـDxfParser الموجود في التطبيق
 *  - PDF والصور بتتحوّل لصور base64 وتتبعت لموديل يشوف (vision)
 */
object DocumentExtractor {

    /** أقصى نص بنبعته للموديل — عشان الطلب يفضل رخيص وسريع. */
    private const val MAX_CHARS = 24_000

    /** حدود الصور: عدد الصفحات ودقّتها وميزانية الحجم الكلي للطلب. */
    private const val MAX_PDF_PAGES = 4
    private const val RENDER_WIDTH = 1100
    private const val JPEG_QUALITY = 65

    /** سقف حجم الـbase64 المتجمّع (~3 ميجا) — فوقه الطلب بيوقع الذاكرة. */
    private const val IMAGE_BUDGET_CHARS = 3_000_000

    sealed interface Content {
        /** نص مقروء — أرخص وأدق مسار. */
        data class Text(val text: String, val kindHint: String) : Content
        /** صور base64 **JPEG** — للـPDF والصور؛ محتاج موديل بيشوف (vision). */
        data class Images(val base64Jpeg: List<String>, val kindHint: String) : Content
        data class Unsupported(val reason: String) : Content
    }

    private val TEXT_EXT = setOf("txt", "csv", "tsv", "json", "xml", "md", "log", "ini", "yaml", "yml")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif")

    suspend fun extract(file: File): Content = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext Content.Unsupported("الملف فاضي أو مش موجود")
        when (file.extension.lowercase()) {
            in TEXT_EXT -> runCatching { Content.Text(readTextCapped(file), "TEXT") as Content }
                .getOrElse { Content.Unsupported("تعذّر قراءة الملف النصي") }
            "xlsx", "xlsm" -> runCatching { Content.Text(readXlsx(file).take(MAX_CHARS), "SPREADSHEET") as Content }
                .getOrElse { Content.Unsupported("تعذّر قراءة ملف الإكسل") }
            "docx" -> runCatching { Content.Text(readDocx(file).take(MAX_CHARS), "DOCUMENT") as Content }
                .getOrElse { Content.Unsupported("تعذّر قراءة ملف الوورد") }
            "dxf" -> runCatching { Content.Text(readDxf(file).take(MAX_CHARS), "DRAWING") as Content }
                .getOrElse { Content.Unsupported("تعذّر قراءة ملف DXF") }
            "pdf" -> runCatching { Content.Images(renderPdf(file), "PDF") as Content }
                .getOrElse { Content.Unsupported("تعذّر فتح الـPDF") }
            in IMAGE_EXT -> runCatching { Content.Images(listOf(encodeImage(file)), "PHOTO") as Content }
                .getOrElse { Content.Unsupported("تعذّر قراءة الصورة") }
            // DWG صيغة مغلقة (binary) — مفيش مكتبة أندرويد تقراها. DXF هو البديل.
            "dwg" -> Content.Unsupported("صيغة DWG مقفولة — صدّر الرسمة DXF أو PDF عشان تتحلّل")
            "zip", "rar", "7z" -> Content.Unsupported("الأرشيفات مش بتتحلّل — فك الضغط وارفع الملفات")
            "mp4", "mov", "avi", "mkv" -> Content.Unsupported("الفيديو مش مدعوم للتحليل")
            else -> Content.Unsupported("نوع الملف غير مدعوم للتحليل")
        }
    }

    // ---------------------------------------------------------------- XLSX

    /** XLSX = ZIP فيه XML. بنقرا sharedStrings + خلايا كل شيت ونرجّعها صفوف. */
    private fun readXlsx(file: File): String = ZipFile(file).use { zip ->
        val shared = zip.getEntry("xl/sharedStrings.xml")?.let { e ->
            val xml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()

        val sheets = zip.entries().asSequence()
            .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
            .sortedBy { it.name }
            .toList()

        buildString {
            sheets.forEachIndexed { idx, entry ->
                appendLine("### ورقة ${idx + 1}")
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { row ->
                    val cells = Regex("<c[^>]*?(?:\\s+t=\"(\\w+)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                        .findAll(row.groupValues[1]).map { c ->
                            val type = c.groupValues[1]
                            val body = c.groupValues[2]
                            val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                            when {
                                type == "s" && v != null -> shared.getOrElse(v.toIntOrNull() ?: -1) { "" }
                                type == "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                                    .find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty()
                                else -> v.orEmpty()
                            }
                        }.toList()
                    if (cells.any { it.isNotBlank() }) appendLine(cells.joinToString(" | "))
                }
            }
        }
    }

    // ---------------------------------------------------------------- DOCX

    private fun readDocx(file: File): String = ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return "".also { }
        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(xml).joinToString("\n") { p ->
            Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(p.value).joinToString("") { unescape(it.groupValues[1]) }
        }.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    // ---------------------------------------------------------------- DXF

    /** من الرسمة بنطلع النصوص واللايرات — دي اللي فيها أكواد الحوائط والمناسيب. */
    private fun readDxf(file: File): String {
        val result = DxfParser.parseFile(file)
        if (result.isBinaryDwg) error("binary dwg")
        val drawing = result.drawing ?: error(result.error ?: "parse failed")
        val texts = drawing.entities
            .filterIsInstance<com.corewall.qaqc.ui.cad.CadEntity.TextEnt>()
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return buildString {
            appendLine("نصوص الرسمة (${texts.size}):")
            texts.take(400).forEach { appendLine(it) }
            appendLine()
            appendLine("اللايرات: ${drawing.layers.joinToString(", ") { it.name }}")
            appendLine("عدد العناصر: ${drawing.entities.size}")
        }
    }

    // ---------------------------------------------------------------- PDF / صور

    /**
     * بنرندر أول صفحات بس، وبنقف لو تعدّينا ميزانية الحجم —
     * الطلب اللي بيتخطّى الميزانية بيوقّع الذاكرة بدل ما يتحلّل.
     */
    private fun renderPdf(file: File): List<String> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd).use { renderer ->
            val pages = mutableListOf<String>()
            var budget = IMAGE_BUDGET_CHARS
            for (i in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                if (budget <= 0) break
                val encoded = renderer.openPage(i).use { page ->
                    val h = (RENDER_WIDTH.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                    // PdfRenderer بيتطلّب ARGB_8888 تحديداً
                    val bmp = Bitmap.createBitmap(RENDER_WIDTH, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    toBase64(bmp).also { bmp.recycle() }
                }
                pages += encoded
                budget -= encoded.length
            }
            if (pages.isEmpty()) error("مفيش صفحات اتقرت من الـPDF")
            pages
        }.also { runCatching { pfd.close() } }
    }

    private fun encodeImage(file: File): String {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > RENDER_WIDTH) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(
            file.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("decode failed")
        return toBase64(bmp).also { bmp.recycle() }
    }

    private fun toBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** بنقرا بحد أقصى [MAX_CHARS] حرف — ملف CSV ضخم مايتقريش كله في الذاكرة. */
    private fun readTextCapped(file: File): String {
        val buf = CharArray(MAX_CHARS)
        file.reader(Charsets.UTF_8).use { reader ->
            var filled = 0
            while (filled < MAX_CHARS) {
                val n = reader.read(buf, filled, MAX_CHARS - filled)
                if (n <= 0) break
                filled += n
            }
            return String(buf, 0, filled)
        }
    }

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
}
