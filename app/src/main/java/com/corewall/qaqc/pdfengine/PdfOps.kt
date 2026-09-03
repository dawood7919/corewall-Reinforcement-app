package com.corewall.qaqc.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderEffectDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * عمليات على بنية المستند — إعادة ترتيب، دمج، استخراج، قصّ، علامة مائية،
 * وكتابة التعليقات كتعليقات PDF حقيقية.
 *
 * ليه PDFBox جنب PDFium: الاتنين بيعملوا حاجتين مختلفتين تماماً. PDFium
 * **بيرسم** (وبيعمله كويس وبسرعة، وده كل شغل العارض)، لكن الواجهة اللي
 * المكتبة بتكشفها مافيهاش أي تعديل على كائنات المستند. PDFBox شغّال على
 * مستوى الكائنات نفسها، فهو اللي بيقدر يكتب `/Annots` بمظهر (`/AP`) يفتح
 * في Acrobat ويتعدّل — بدل النسخة المرسترة اللي كنا بنطلّعها.
 *
 * كل العمليات **بتكتب ملف جديد** ومابتلمسش الأصل. الكتابة فوق الأصل
 * بتحصل في طبقة الواجهة بعد ما الملف الجديد يخلص ويتأكد إنه مقروء —
 * قطع الكهربا وسط عملية على رسمة تنفيذية مش سيناريو نظري في الموقع.
 */
object PdfOps {

    @Volatile
    private var initialised = false

    /**
     * PDFBox محتاج يحمّل موارده (خطوط وجداول ترميز) من أصول التطبيق قبل
     * أول استخدام. النداء ده رخيص وآمن يتكرر.
     */
    fun ensureInit(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            PDFBoxResourceLoader.init(context.applicationContext)
            initialised = true
        }
    }

    // ══════════════════════════════════════════════════ ترتيب الصفحات

    /**
     * خطوة واحدة في خطة الصفحات: صفحة من الأصل + تدوير إضافي.
     *
     * الخطة دي هي اللي بتخلّي التنظيم **معاينة حيّة**: الشاشة بتعرض
     * الخطة، والملف مابيتكتبش غير لما المستخدم يحفظ. الحذف = العنصر مش
     * موجود في القايمة. التكرار = العنصر مكرر. الترتيب = ترتيب القايمة.
     */
    data class PagePlan(val source: Int, val extraRotation: Int = 0)

    /**
     * بيبني الملف حسب [plan].
     *
     * التكرار بينسخ **قاموس** الصفحة مش محتواها: النسختين بيشاوروا على نفس
     * تيار المحتوى، فالملف مابيكبرش. ده السلوك الصح — نسخة كاملة من محتوى
     * رسمة A0 معناها ملف بالضعف من غير أي فايدة.
     */
    suspend fun applyPagePlan(
        src: File,
        dest: File,
        plan: List<PagePlan>,
        /**
         * فين المستند يتقري: الرام (الافتراضي) ولا ملف مؤقّت. ست رسومات
         * A0 ملزوق ممكن يبقى مئات الميجات، وقرايته في الرام بتقفل التطبيق.
         */
        memory: MemoryUsageSetting? = null
    ): Result<Unit> = io {
        require(plan.isNotEmpty()) { "لازم تسيب صفحة واحدة على الأقل" }
        PDDocument.load(src, memory ?: MemoryUsageSetting.setupMainMemoryOnly()).use { doc ->
            val tree = doc.pages
            val original = (0 until tree.count).map { tree.get(it) }
            require(plan.all { it.source in original.indices }) { "رقم صفحة خارج المستند" }

            original.forEach { tree.remove(it) }

            val used = HashSet<Int>()
            for (step in plan) {
                val source = original[step.source]
                val page =
                    if (used.add(step.source)) source
                    else PDPage(COSDictionary(source.cosObject))
                page.rotation = normaliseRotation(page.rotation + step.extraRotation)
                tree.add(page)
            }
            doc.save(dest)
        }
    }

    /** استخراج صفحات لملف جديد — نفس المسار، خطة من غير تدوير. */
    suspend fun extract(src: File, dest: File, pages: List<Int>): Result<Unit> =
        applyPagePlan(src, dest, pages.sorted().map { PagePlan(it) })

    /**
     * تقسيم المستند إلى ملفات صفحة-بصفحة.
     *
     * كل ملف يتكتب مستقلاً؛ لو صفحة واحدة تالفة أو التخزين امتلأ، الأصل
     * وباقي الصفحات التي كُتبت قبلها يظلون سالمين ولا تتأثر نسخة المستخدم.
     */
    suspend fun splitIntoPages(src: File, outputDir: File, baseName: String): Result<List<File>> = io {
        require(outputDir.exists() || outputDir.mkdirs()) { "مقدرناش ننشئ مجلد التقسيم" }
        PDDocument.load(src).use { source ->
            val outputs = ArrayList<File>(source.numberOfPages)
            for (index in 0 until source.numberOfPages) {
                val out = File(outputDir, "$baseName — صفحة ${index + 1}.pdf")
                PDDocument().use { single ->
                    single.importPage(source.getPage(index))
                    single.save(out)
                }
                check(out.exists() && out.length() > MIN_PDF_BYTES) { "فشل حفظ الصفحة ${index + 1}" }
                outputs += out
            }
            outputs
        }
    }

    /**
     * دمج ملفات.
     *
     * `PDFMergerUtility` مش رفاهية هنا: نسخ صفحة من مستند لمستند تاني
     * بالإيد بيسيب مراجع بتشاور على كائنات في المستند الأصلي (خطوط،
     * صور، حالات رسم)، والنتيجة ملف بيفتح فاضي أو بيقع. الأداة دي بتعمل
     * الاستنساخ العميق ده صح.
     */
    suspend fun merge(sources: List<File>, dest: File): Result<Int> = io {
        require(sources.size >= 2) { "الدمج محتاج ملفين على الأقل" }
        val merger = PDFMergerUtility()
        merger.destinationFileName = dest.absolutePath
        sources.forEach { merger.addSource(it) }
        merger.mergeDocuments(null)
        PDDocument.load(dest).use { it.numberOfPages }
    }

    /**
     * قصّ صفحات على مستطيل منسّب (٠..١) بأصل **أعلى-يسار**.
     *
     * القصّ بيغيّر `/CropBox` بس — المحتوى بيفضل مكانه كامل. يعني العملية
     * قابلة للتراجع (ترجّع الـCropBox لمقاس الورقة) والملف مابيخسرش حاجة.
     * ده الفرق بين "قصّ" في عارض محترم و"قصّ" اللي بيرمي البيانات.
     */
    suspend fun crop(
        src: File,
        dest: File,
        pages: List<Int>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Result<Unit> = io {
        require(right > left && bottom > top) { "مستطيل القصّ فاضي" }
        PDDocument.load(src).use { doc ->
            val targets = pages.ifEmpty { (0 until doc.numberOfPages).toList() }
            for (index in targets) {
                val page = doc.pages.getOrNull(index) ?: continue
                val box = page.cropBox ?: page.mediaBox
                val w = box.width
                val h = box.height
                // المنسّب من أعلى → إحداثيات PDF من أسفل
                page.cropBox = PDRectangle(
                    box.lowerLeftX + left * w,
                    box.upperRightY - bottom * h,
                    (right - left) * w,
                    (bottom - top) * h
                )
            }
            doc.save(dest)
        }
    }

    // ══════════════════════════════════════════════════ العلامة المائية

    data class Watermark(
        val text: String,
        /** ٠..١ */
        val opacity: Float = 0.18f,
        /** درجات، عكس عقارب الساعة. */
        val angle: Float = 35f,
        /** نسبة من عرض الصفحة (٠..١) — العلامة بتتقاس بالصفحة مش بالنقط. */
        val widthFraction: Float = 0.7f,
        val colorArgb: Long = 0xFFD32F2F,
        val pages: List<Int> = emptyList()
    )

    /**
     * علامة مائية نصّية.
     *
     * النص بيتحوّل **لصورة** مرسومة بمحرّك أندرويد، مش بيتكتب كنص PDF.
     * السبب عربي بحت: كتابة النص كنص محتاجة تشكيل الحروف العربية
     * (الأشكال المتصلة) وترتيب من اليمين للشمال، وPDFBox مابيعملش ولا
     * واحدة منهم — "مسودة" كانت هتطلع "م س و د ة" مقلوبة. محرّك أندرويد
     * بيعمل ده صح، فبنرسم عنده وبنحطّ الناتج كصورة.
     *
     * والتكلفة مقبولة: صورة شفافة بعرض الصفحة تقريباً، مرة واحدة، بتتعاد
     * على كل الصفحات كنفس الكائن.
     */
    suspend fun watermark(src: File, dest: File, spec: Watermark): Result<Unit> = io {
        require(spec.text.isNotBlank()) { "اكتب نص العلامة" }
        PDDocument.load(src).use { doc ->
            val stamp = LosslessFactory.createFromImage(doc, renderStamp(spec))
            val targets = spec.pages.ifEmpty { (0 until doc.numberOfPages).toList() }

            val alpha = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = spec.opacity.coerceIn(0.02f, 1f)
                strokingAlphaConstant = spec.opacity.coerceIn(0.02f, 1f)
            }

            for (index in targets) {
                val page = doc.pages.getOrNull(index) ?: continue
                val box = page.cropBox ?: page.mediaBox
                val drawWidth = box.width * spec.widthFraction.coerceIn(0.1f, 1f)
                val drawHeight = drawWidth * stamp.height / stamp.width

                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.saveGraphicsState()
                    cs.setGraphicsStateParameters(alpha)
                    // الدوران حوالين مركز الصفحة، وبعدين إزاحة عشان مركز
                    // الصورة يقع على مركز الصفحة.
                    val cx = box.lowerLeftX + box.width / 2f
                    val cy = box.lowerLeftY + box.height / 2f
                    cs.transform(
                        Matrix.getRotateInstance(Math.toRadians(spec.angle.toDouble()), cx, cy)
                    )
                    // بعد التحويل الأصل بقى مركز الصفحة، فرسم الصورة بنص
                    // مقاسها بالسالب بيحطّ مركزها على مركز الورقة.
                    cs.drawImage(stamp, -drawWidth / 2f, -drawHeight / 2f, drawWidth, drawHeight)
                    cs.restoreGraphicsState()
                }
            }
            doc.save(dest)
        }
    }

    /** بيرسم نص العلامة على بيتماب شفاف بمحرّك أندرويد (تشكيل عربي سليم). */
    private fun renderStamp(spec: Watermark): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = STAMP_TEXT_PX
            color = spec.colorArgb.toInt()
            isFakeBoldText = true
        }
        val width = paint.measureText(spec.text).toInt().coerceAtLeast(1)
        val metrics = paint.fontMetrics
        val height = (metrics.bottom - metrics.top).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(
            width + STAMP_PAD * 2,
            height + STAMP_PAD * 2,
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).drawText(
            spec.text,
            STAMP_PAD.toFloat(),
            STAMP_PAD - metrics.top,
            paint
        )
        return bitmap
    }

    // ══════════════════════════════════════════════════ طبقة نص مخفية

    /** كلمة من الـOCR بمكانها ببكسل الصورة اللي اتقريت. */
    data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * بيكتب نص الـOCR كطبقة **مخفية** فوق الصفحة.
     *
     * ده اللي بيحوّل الرسمة الممسوحة لمستند حقيقي: الصورة بتفضل زي ما هي
     * بالظبط (مافيش أي تغيير في شكل الصفحة)، وفوقها نص بوضع رسم
     * `NEITHER` — يعني مش بيتلوّن ولا بيترسم، لكن البحث والتحديد والنسخ
     * بيشوفوه. نفس الطريقة اللي Acrobat بيعملها في "Recognise Text".
     *
     * الخط بيتضمّن من أصول التطبيق لأن العربي مستحيل يتكتب بخطوط PDF
     * الأساسية (الأربعتاشر) — كلها Latin-1. والتضمين جزئي (subset) فمش
     * بيزوّد الملف غير بالحروف المستخدمة فعلاً.
     */
    suspend fun writeTextLayer(
        src: File,
        dest: File,
        fontStream: () -> java.io.InputStream,
        /** لكل صفحة: كلماتها + مقاس الصورة اللي اتقريت بالبكسل. */
        pages: Map<Int, OcrPage>
    ): Result<Int> = io {
        var written = 0
        PDDocument.load(src).use { doc ->
            val font = fontStream().use { PDType0Font.load(doc, it, true) }

            for ((index, page) in pages) {
                val target = doc.pages.getOrNull(index) ?: continue
                if (page.words.isEmpty() || page.imageWidth <= 0 || page.imageHeight <= 0) continue

                val box = target.cropBox ?: target.mediaBox
                val sx = box.width / page.imageWidth
                val sy = box.height / page.imageHeight

                PDPageContentStream(
                    doc, target, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    for (word in page.words) {
                        val heightPt = (word.bottom - word.top) * sy
                        if (heightPt <= 0.5f) continue
                        // حجم الخط من ارتفاع المربّع: التحديد بالإصبع
                        // بيقع على مكان الكلمة الحقيقي، مش على سطر وهمي.
                        val size = (heightPt * FONT_HEIGHT_RATIO).coerceIn(1f, 400f)
                        val x = box.lowerLeftX + word.left * sx
                        // أصل PDF أسفل-يسار، وأصل الصورة أعلى-يسار.
                        val y = box.upperRightY - word.bottom * sy
                        runCatching {
                            cs.setFont(font, size)
                            cs.setTextMatrix(Matrix.getTranslateInstance(x, y))
                            cs.showText(word.text)
                            written++
                        }
                    }
                    cs.endText()
                }
            }
            doc.save(dest)
        }
        written
    }

    /** صفحة واحدة من نتيجة الـOCR. */
    data class OcrPage(
        val words: List<OcrWord>,
        val imageWidth: Float,
        val imageHeight: Float
    )

    // ══════════════════════════════════════════════════ التعليقات الحقيقية

    /**
     * بيكتب التعليقات المتخزّنة كـ`/Annots` حقيقية.
     *
     * الفرق عن التصدير القديم مش تجميلي: النسخة المرسترة كانت بتحوّل كل
     * صفحة لصورة — الملف بيكبر، البحث بيموت، والتعليق نفسه بيبقى جزء من
     * الصورة مش حاجة تقدر تشيلها أو تردّ عليها. هنا كل علامة بتبقى كائن
     * PDF قياسي: تتفتح في Acrobat، تتحدّد، تتمسح، وليها كاتب وتاريخ.
     *
     * وبنولّد لكل واحدة **مظهر** (`/AP`) بـ`constructAppearances`. من غيره
     * التعليق بيبان في Acrobat (اللي بيولّد المظهر بنفسه) ومابيبانش في
     * نص العارضات التانية — وده أسوأ من إنه مايتكتبش أصلاً.
     */
    suspend fun writeAnnotations(
        src: File,
        dest: File,
        byPage: Map<Int, List<PdfAnnotationEntity>>,
        author: String = "Core Wall QA/QC",
        pointsOf: (PdfAnnotationEntity) -> List<Float>
    ): Result<Int> = io {
        var written = 0
        PDDocument.load(src).use { doc ->
            for ((pageIndex, items) in byPage) {
                val page = doc.pages.getOrNull(pageIndex) ?: continue
                val box = page.cropBox ?: page.mediaBox
                val existing = ArrayList(page.annotations)

                for (entity in items) {
                    val flat = pointsOf(entity)
                    if (flat.size < 4) continue
                    val points = toUserSpace(flat, box)
                    val annot = buildAnnotation(entity, points) ?: continue

                    annot.contents = entity.tool
                    if (annot is PDAnnotationMarkup) {
                        annot.setTitlePopup(author)
                        annot.creationDate = Calendar.getInstance()
                        annot.constantOpacity = entity.opacity.coerceIn(0.02f, 1f)
                    }
                    annot.isPrinted = true
                    annot.color = rgb(entity.color)
                    // `/P` بيربط التعليق بصفحته. من غيره بعض العارضات
                    // بتعرضه وبعضها بيتجاهله، والتعديل عليه بيبوظ.
                    annot.page = page

                    runCatching { (annot as? PDAnnotationMarkup)?.constructAppearances(doc) }
                    existing += annot
                    written++
                }
                page.annotations = existing
            }
            doc.save(dest)
        }
        written
    }

    /** منسّب (٠..١ من أعلى-يسار) → إحداثيات المستخدم في الصفحة. */
    private fun toUserSpace(flat: List<Float>, box: PDRectangle): FloatArray {
        val out = FloatArray(flat.size / 2 * 2)
        var i = 0
        while (i + 1 < flat.size) {
            out[i] = box.lowerLeftX + flat[i] * box.width
            out[i + 1] = box.upperRightY - flat[i + 1] * box.height
            i += 2
        }
        return out
    }

    private fun buildAnnotation(
        entity: PdfAnnotationEntity,
        points: FloatArray
    ): PDAnnotation? {
        val width = entity.strokeWidth.coerceAtLeast(0.25f)
        val border = PDBorderStyleDictionary().apply {
            style = PDBorderStyleDictionary.STYLE_SOLID
            this.width = width
        }

        return when (entity.tool) {
            PdfAnnotationEntity.TOOL_LINE, PdfAnnotationEntity.TOOL_ARROW -> {
                if (points.size < 4) return null
                val last = points.size - 2
                PDAnnotationLine().apply {
                    setLine(
                        floatArrayOf(points[0], points[1], points[last], points[last + 1])
                    )
                    if (entity.tool == PdfAnnotationEntity.TOOL_ARROW) {
                        endPointEndingStyle = PDAnnotationLine.LE_OPEN_ARROW
                    }
                    borderStyle = border
                    rectangle = boundsOf(points, width * 6f)
                }
            }

            PdfAnnotationEntity.TOOL_RECT -> square(points, border, width, cloudy = false)
            PdfAnnotationEntity.TOOL_CLOUD -> square(points, border, width, cloudy = true)

            /**
             * التظليل **مستطيل مملوء**، مش خط.
             *
             * كان بيقع في فرع `else` مع القلم والماركر فبيتصدّر `Ink` —
             * والتظليل نقطتين بس (ركن وركن)، يعني الناتج كان **خط قطري
             * واحد** جوّه مستطيل كبير. في التطبيق بيبان تظليل، وفي الملف
             * اللي بيتبعت بيبان شخبطة رفيعة.
             *
             * `interiorColor` هو الحشو؛ والحدّ بصفر عشان الشكل يطابق اللي
             * الشاشة بترسمه بالظبط — مساحة ملوّنة من غير إطار.
             */
            PdfAnnotationEntity.TOOL_HIGHLIGHT ->
                PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
                    interiorColor = rgb(entity.color)
                    borderStyle = PDBorderStyleDictionary().apply {
                        style = PDBorderStyleDictionary.STYLE_SOLID
                        this.width = 0f
                    }
                    rectangle = boundsOf(points, 0f)
                }

            PdfAnnotationEntity.TOOL_CIRCLE ->
                PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_CIRCLE).apply {
                    borderStyle = border
                    rectangle = boundsOf(points, width)
                }

            else -> {
                // القلم والماركر والتظليل كلهم مسار حر → Ink.
                if (points.size < 4) return null
                PDAnnotationMarkup().apply {
                    // `PDAnnotation` بيكشف `getSubtype()` من غير setter —
                    // النوع بيتكتب في القاموس مباشرة. `constructAppearances`
                    // بتقرا منه عشان تختار مولّد المظهر الصح.
                    cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
                    inkList = arrayOf(points)
                    borderStyle = border
                    rectangle = boundsOf(points, width)
                }
            }
        }
    }

    private fun square(
        points: FloatArray,
        border: PDBorderStyleDictionary,
        width: Float,
        cloudy: Boolean
    ) = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
        borderStyle = border
        if (cloudy) {
            borderEffect = PDBorderEffectDictionary().apply {
                style = PDBorderEffectDictionary.STYLE_CLOUDY
                intensity = CLOUD_INTENSITY
            }
        }
        // السحابة بتخرج برّه المستطيل، فبنوسّع الإطار عشان مايتقصّش.
        rectangle = boundsOf(points, if (cloudy) width + CLOUD_MARGIN_PT else width)
    }

    /** إطار يلمّ النقط، بهامش للسُمك — من غيره الخط بيتقصّ عند الحافة. */
    private fun boundsOf(points: FloatArray, pad: Float): PDRectangle {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < points.size) {
            minX = minOf(minX, points[i]); maxX = maxOf(maxX, points[i])
            minY = minOf(minY, points[i + 1]); maxY = maxOf(maxY, points[i + 1])
            i += 2
        }
        return PDRectangle(minX - pad, minY - pad, (maxX - minX) + pad * 2, (maxY - minY) + pad * 2)
    }

    private fun rgb(argb: Long): PDColor {
        val v = argb.toInt()
        return PDColor(
            floatArrayOf(
                ((v shr 16) and 0xFF) / 255f,
                ((v shr 8) and 0xFF) / 255f,
                (v and 0xFF) / 255f
            ),
            PDDeviceRGB.INSTANCE
        )
    }

    // ══════════════════════════════════════════════════ أدوات

    private fun normaliseRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

    private fun com.tom_roush.pdfbox.pdmodel.PDPageTree.getOrNull(index: Int): PDPage? =
        if (index in 0 until count) get(index) else null

    /**
     * كل العمليات على [Dispatchers.IO] وملفوفة في [Result].
     *
     * PDFBox بيرمي `IOException` على كل حاجة — ملف تالف، ذاكرة، مسار
     * مقفول. الاستثناء اللي بيطلع من دالة تعليق بيقتل التطبيق؛ [Result]
     * بيخلّي الواجهة تعرض رسالة وتكمّل.
     */
    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private const val STAMP_TEXT_PX = 96f
    private const val STAMP_PAD = 24
    private const val MIN_PDF_BYTES = 200L
    private const val CLOUD_INTENSITY = 2f
    private const val CLOUD_MARGIN_PT = 6f

    /**
     * نسبة حجم الخط لارتفاع المربّع.
     *
     * ارتفاع المربّع بيلمّ الحروف الطالعة والنازلة، وحجم الخط بيتقاس على
     * الجسم. ٠.٨ بيخلّي النص المخفي يقع على الكلمة بدل ما يزحلق تحتيها.
     */
    private const val FONT_HEIGHT_RATIO = 0.8f
}
