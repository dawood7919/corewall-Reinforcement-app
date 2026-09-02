package com.corewall.qaqc.pdfengine

import android.app.ActivityManager
import android.content.Context
import java.io.Closeable
import java.io.File

/**
 * نسخ إضافية من المستند عشان الرسم يمشي متوازي.
 *
 * ## المشكلة
 *
 * PDFium مابيسمحش بأكتر من رسمة في نفس الوقت على **نفس مؤشّر المستند**،
 * وكاش الصفحات المفتوحة في [PdfDocumentSession] مبني على إنه خيط واحد
 * فمالوش أقفال. النتيجة إن الرسم كله كان بيمشي **بلاطة واحدة في المرة**،
 * على خيط واحد، على جهاز فيه تمن أنوية. شاشة فيها عشرين بلاطة كانت
 * بتتملّى بالتسلسل حتى والسبع أنوية التانية فاضيين.
 *
 * ## الحل
 *
 * مش أقفال على المؤشّر الواحد — **مؤشّرات مستقلة**. كل عامل بيفتح الملف
 * لوحده، فبياخد مستنده وخيطه وكاش صفحاته. القاعدة اللي كانت بتخلّي الكود
 * من غير أقفال فضلت زي ما هي بالظبط، والتوازي اتحقّق من فوقها.
 *
 * ## الثمن
 *
 * كل نسخة بتاخد ذاكرة PDFium بتاعتها (جدول المراجع وشجرة الصفحات
 * المفتوحة). عشان كده العدد صغير وبيقلّ على الأجهزة الضعيفة، والفشل في
 * فتح أي نسخة **مش خطأ**: بنكمّل بالعدد اللي فتح، ولو مفتحش ولا واحد
 * بنرجع للسلوك القديم بالظبط.
 */
class PdfRenderPool private constructor(
    private val renderers: List<PdfDocumentSession>
) : Closeable {

    /** عدد الرسمات اللي ممكن تمشي مع بعض. */
    val size: Int get() = renderers.size

    fun sessionAt(slot: Int): PdfDocumentSession = renderers[slot % renderers.size]

    /** بيقفل النسخ الإضافية بس — النسخة الأساسية بتتقفل مع الشاشة. */
    override fun close() {
        renderers.drop(1).forEach { runCatching { it.close() } }
    }

    companion object {
        /** أكتر من كده مابيزودش حاجة: الرسم بيبقى محدود بالذاكرة مش بالنواة. */
        private const val MAX_RENDERERS = 3

        /** تحت الحد ده بنفضّل الذاكرة على السرعة. */
        private const val LOW_MEMORY_MB = 192

        fun open(context: Context, primary: PdfDocumentSession): PdfRenderPool {
            val target = targetSize(context)
            val list = ArrayList<PdfDocumentSession>(target)
            list += primary
            // الفشل هنا متوقّع ومقبول: ملف كبير على جهاز مضغوط ممكن
            // مايفتحش تاني مرة، والعرض المفروض يشتغل بالعدد المتاح.
            while (list.size < target) {
                val extra = runCatching { open(context, primary.file) }.getOrNull() ?: break
                list += extra
            }
            return PdfRenderPool(list)
        }

        private fun open(context: Context, file: File): PdfDocumentSession =
            PdfDocumentSession.open(context, file)

        private fun targetSize(context: Context): Int {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryMb = manager?.memoryClass ?: 0
            if (memoryMb in 1 until LOW_MEMORY_MB) return 1
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores / 2).coerceIn(1, MAX_RENDERERS)
        }
    }
}
