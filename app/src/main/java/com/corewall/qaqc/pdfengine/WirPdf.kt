package com.corewall.qaqc.pdfengine

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ملف طلب الفحص (WIR) — صفحة بتتضاف في آخره.
 *
 * الـWIR بيتبنى صفحة صفحة وانت بتتصفّح الرسومات: تلاقي تفصيلة عايزة فحص،
 * تبعتها، تكمّل. فالعملية الوحيدة هنا هي "زوّد صفحة في الآخر".
 *
 * **الإضافة في الآخر مش تفصيلة**: تعليقات العارض متخزّنة بمسار الملف ورقم
 * الصفحة، فأي إدراج في النص كان هيزحلق كل هايلايت اتعمل قبل كده لصفحة
 * تانية. الإضافة في الآخر بتخلّي أرقام الصفحات الموجودة ثابتة.
 */
object WirPdf {

    /** أقل من كده مش PDF سليم — نفس الحد المستخدم في تبديل الملفات. */
    private const val MIN_PDF_BYTES = 400L

    /**
     * بيضيف صفحة [page] من [source] في آخر [dest]، وبيرجّع عدد الصفحات
     * بعد الإضافة.
     *
     * الملف بيتكتب جنب الأصل وبعدين بيتبدّل بيه بـ`renameTo`، مع نسخة
     * احتياطية قبل أي لمسة: `renameTo` بيفشل عبر أنظمة ملفات مختلفة،
     * وقطع الكهربا وسط الكتابة على WIR فيه شغل نص يوم سيناريو موقع مش
     * سيناريو نظري.
     */
    suspend fun appendPage(
        source: File,
        page: Int,
        dest: File,
        workDir: File,
        /**
         * التأشير اللي على الصفحة الأصلية.
         *
         * بيتكتب **جوّه الـPDF** كتعليقات حقيقية مش بيتنسخ كصفوف في
         * القاعدة. الفرق ده هو كل الموضوع: الـWIR مستند بيسيب التطبيق —
         * بيتبعت للاستشاري ويتطبع ويتوقّع عليه. تأشير عايش في قاعدة
         * بيانات على تليفون واحد **مش موجود** في الملف اللي بيوصلهم.
         */
        markup: List<PdfAnnotationEntity> = emptyList(),
        pointsOf: (PdfAnnotationEntity) -> List<Float> = { emptyList() }
    ): Result<Int> = withContext(Dispatchers.IO) {
        val single = File(workDir, "wir-page-${System.currentTimeMillis()}.pdf")
        val inked = File(workDir, "wir-inked-${System.currentTimeMillis()}.pdf")
        runCatching {
            require(source.exists()) { "الملف الأصلي مش موجود" }
            require(workDir.exists() || workDir.mkdirs()) { "مقدرناش نجهّز مجلد مؤقّت" }
            require(dest.parentFile?.let { it.exists() || it.mkdirs() } != false) {
                "مقدرناش نجهّز مجلد الـWIR"
            }

            // نفس [PdfOps.extract] بس بقراية على ملف مؤقّت: الرسمة اللي
            // بتتسحب منها الصفحة ممكن تبقى ست A0 كامل.
            PdfOps.applyPagePlan(
                src = source,
                dest = single,
                plan = listOf(PdfOps.PagePlan(page)),
                memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
            ).getOrThrow()
            check(single.exists() && single.length() > MIN_PDF_BYTES) { "الصفحة اتكتبت فاضية" }

            // الصفحة المستخرجة صفحة واحدة، ففهرسها جوّه ملفها صفر مهما كان
            // رقمها في الأصل.
            val ready = if (markup.isEmpty()) single else {
                PdfOps.writeAnnotations(
                    src = single,
                    dest = inked,
                    byPage = mapOf(0 to markup),
                    pointsOf = pointsOf
                ).getOrThrow()
                if (inked.exists() && inked.length() > MIN_PDF_BYTES) inked else single
            }

            if (!dest.exists()) {
                ready.copyTo(dest, overwrite = true)
                return@runCatching 1
            }

            val merged = File(dest.parentFile, ".${dest.name}.tmp")
            val backup = File(dest.parentFile, ".${dest.name}.bak")
            merged.delete()

            PDFMergerUtility().apply {
                destinationFileName = merged.absolutePath
                addSource(dest)
                addSource(ready)
            }.mergeDocuments(MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir))

            val pages = PDDocument.load(merged, MemoryUsageSetting.setupTempFileOnly())
                .use { it.numberOfPages }
            check(pages > 0) { "الملف الناتج فاضي" }

            if (backup.exists()) backup.delete()
            if (!dest.renameTo(backup)) error("مقدرناش نجهّز الملف للاستبدال")
            if (!merged.renameTo(dest)) {
                backup.renameTo(dest)
                error("مقدرناش نكتب الملف الجديد")
            }
            backup.delete()
            pages
        }.also { single.delete(); inked.delete() }
    }

    /** عدد صفحات ملف موجود — للمزامنة لو الملف اتعدّل من برّه. */
    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        }.getOrDefault(0)
    }
}
