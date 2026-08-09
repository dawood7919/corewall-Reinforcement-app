package com.corewall.qaqc.pdfengine

import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * تصدير الصفحات كصور.
 *
 * الرسم بيتعمل بـPDFium (نفس محرّك العرض) مش بـPDFBox: PDFBox-Android
 * بيرسم بالبطيء وبنتيجة أقل، وإحنا أصلاً عندنا المحرّك السريع مفتوح.
 */
object PdfImageExport {

    enum class Format(val label: String, val ext: String, val lossy: Boolean) {
        PNG("PNG", "png", false),
        JPEG("JPEG", "jpg", true),
        WEBP("WEBP", "webp", true)
    }

    /** الدقّات المعروضة. ٧٢ = مقاس الشاشة، ٣٠٠ = مقاس طباعة. */
    val DPI_CHOICES = listOf(72, 150, 300, 600)

    data class Outcome(
        val files: List<File>,
        /**
         * الصفحات اللي الدقّة المطلوبة فيها اتخفّضت عشان الذاكرة، مع
         * الدقّة الفعلية. الواجهة **بتقول** ده للمستخدم — تصدير بيقول
         * ٦٠٠ وبيطلع ١٥٠ من غير كلمة هو أسوأ من رفض العملية.
         */
        val downscaled: Map<Int, Int>
    )

    /**
     * بيصدّر [pages] كصور في [dir].
     *
     * **سقف البكسل مقصود**: رسمة A0 عند ٦٠٠ نقطة/بوصة = ٨٣٠ مليون بكسل =
     * ٣.٣ جيجا في الذاكرة. مفيش جهاز بيعمل ده. بنقرّب لأكبر دقّة تقدر
     * تتحمّلها، وبنسجّلها في [Outcome.downscaled] عشان المستخدم يعرف إن
     * اللي في إيده مش اللي طلبه.
     */
    suspend fun export(
        session: PdfDocumentSession,
        pages: List<Int>,
        dpi: Int,
        format: Format,
        quality: Int = 92,
        dir: File,
        baseName: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "مفيش صفحات مختارة" }
            dir.mkdirs()

            val out = ArrayList<File>(pages.size)
            val reduced = LinkedHashMap<Int, Int>()

            pages.forEachIndexed { done, page ->
                session.measure(page)
                val size = session.sizeOrEstimate(page)

                var scale = dpi / 72f
                var width = (size.width * scale).toInt().coerceAtLeast(1)
                var height = (size.height * scale).toInt().coerceAtLeast(1)

                // التخفيض بالنص كل مرة — بيحافظ على حدّة أحسن من قسمة
                // على رقم كسري، ولأنه على السلّم بيتوافق مع كاش المربّعات.
                while (width.toLong() * height > MAX_PIXELS && scale > MIN_SCALE) {
                    scale /= 2f
                    width = (size.width * scale).toInt().coerceAtLeast(1)
                    height = (size.height * scale).toInt().coerceAtLeast(1)
                }
                val effectiveDpi = (scale * 72f).toInt().coerceAtLeast(1)
                if (effectiveDpi < dpi) reduced[page] = effectiveDpi

                val bitmap = renderOrShrink(session, page, width, height)
                    ?: error("مقدرناش نرسم صفحة ${page + 1}")

                val file = File(dir, "$baseName-${(page + 1).toString().padStart(3, '0')}.${format.ext}")
                file.outputStream().use { stream ->
                    bitmap.compress(compressFormat(format), quality.coerceIn(1, 100), stream)
                }
                bitmap.recycle()
                out += file
                onProgress(done + 1, pages.size)
            }
            Outcome(out, reduced)
        }
    }

    /**
     * بيحاول يرسم، ولو الذاكرة رفضت بينزل مستوى ويحاول تاني.
     *
     * `OutOfMemoryError` مش استثناء عادي وrunCatching مابيمسكهوش لوحده
     * في كل الحالات — بنمسكه هنا صراحةً. البديل إن التطبيق يموت وسط
     * تصدير، والمستخدم يفتكر إن الملف هو السبب.
     */
    private suspend fun renderOrShrink(
        session: PdfDocumentSession,
        page: Int,
        startWidth: Int,
        startHeight: Int
    ): Bitmap? {
        var w = startWidth
        var h = startHeight
        repeat(SHRINK_ATTEMPTS) {
            val bitmap = try {
                session.renderTile(page, w, h, 0, 0, w, h)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (bitmap != null) return bitmap
            w = (w / 2).coerceAtLeast(1)
            h = (h / 2).coerceAtLeast(1)
        }
        return null
    }

    private fun compressFormat(format: Format): Bitmap.CompressFormat = when (format) {
        Format.PNG -> Bitmap.CompressFormat.PNG
        Format.JPEG -> Bitmap.CompressFormat.JPEG
        Format.WEBP ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
    }

    /** ٢٤ مليون بكسل ≈ ٩٦ ميجا في ARGB_8888 — سقف واقعي على جهاز موقع. */
    private const val MAX_PIXELS = 24_000_000L
    private const val MIN_SCALE = 0.05f
    private const val SHRINK_ATTEMPTS = 4
}
