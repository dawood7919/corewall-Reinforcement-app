package com.corewall.qaqc.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.util.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/**
 * مستند مفتوح — كل تعامل مع PDFium بيعدّي من هنا.
 *
 * **قاعدة الخيط الواحد.** PDFium مش آمن للاستدعاء المتوازي على نفس المستند،
 * والمكتبة نفسها بتعمل `synchronized` جوّاها. لو سبنا الاستدعاءات تتنافس،
 * أحسن حالة إنها تتسلسل ورا قفل عام، وأوحش حالة انهيار في كود أصلي —
 * وده بيقفل التطبيق من غير أي استثناء تقدر تمسكه.
 *
 * فكل مستند بياخد **منفّذ بخيط واحد**، وكل دالة هنا `suspend` وبتتنفّذ عليه.
 * ده مش بطيء: الرندر أصلاً بيتعمل مربّع ورا مربّع، والتوازي الحقيقي بيحصل
 * **بين المستندات** مش جوّه المستند الواحد.
 *
 * المستند بيتقفل مع الشاشة. أي استدعاء بعد القفل بيرجّع قيمة فاضية بدل ما
 * يرمي — الشاشة ممكن تكون لسه بترسم إطار أخير وهي بتتقفل.
 */
class PdfDocumentSession private constructor(
    val file: File,
    private val core: PdfiumCore,
    private val doc: PdfDocument,
    private val pfd: ParcelFileDescriptor,
    private val executor: java.util.concurrent.ExecutorService,
    val dispatcher: CoroutineDispatcher,
    val pageCount: Int
) : Closeable {

    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var closed = false

    /**
     * مقاسات الصفحات — بتتقاس **عند الطلب** مش كلها عند الفتح.
     *
     * مستند ٢٠٠٠ صفحة لو قِسنا كل صفحاته وقت الفتح هيستنى ثواني قبل ما يبان
     * أي حاجة. بنقيس أول صفحة بس، ونستخدمها كتقدير للباقي لحد ما توصلها،
     * وساعتها بتتقاس وبتتثبّت. الرصّ بيتحدّث لوحده.
     */
    private val sizes = arrayOfNulls<SizePt>(pageCount)

    /**
     * بيزيد كل ما مقاس صفحة يتعرف.
     *
     * `StateFlow` مش عدّاد عادي عن قصد: الواجهة لازم **تعرف** إن مقاس وصل
     * عشان تعيد رصّ الصفحات وتطلب مربّعاتها. عدّاد صامت معناه صفحة بتفضل
     * فاضية لحد ما المستخدم يحرّك إيده صدفة.
     */
    private val _measuredCount = MutableStateFlow(0)
    val measuredCount: StateFlow<Int> = _measuredCount

    /** التقدير الأولي — أول صفحة، وهي غالباً ممثّلة لباقي المستند. */
    @Volatile
    var estimate: SizePt = SizePt.A4
        private set

    fun knownSize(page: Int): SizePt? = sizes.getOrNull(page)

    fun sizeOrEstimate(page: Int): SizePt = sizes.getOrNull(page) ?: estimate

    /** كل المقاسات المعروفة، والباقي بالتقدير — ده اللي الرصّ بيتبني عليه. */
    fun allSizes(): List<SizePt> = List(pageCount) { sizeOrEstimate(it) }

    /** بيقيس صفحة لو لسه ما اتقاستش. بيرجّع true لو المقاس اتغيّر فعلاً. */
    suspend fun measure(page: Int): Boolean = withContext(dispatcher) {
        if (closed || page !in 0 until pageCount) return@withContext false
        if (sizes[page] != null) return@withContext false
        val measured = runCatching {
            doc.openPage(page).use { p ->
                SizePt(p.getPageWidthPoint().toFloat(), p.getPageHeightPoint().toFloat())
            }
        }.getOrNull() ?: return@withContext false
        if (measured.width <= 0f || measured.height <= 0f) return@withContext false
        sizes[page] = measured
        if (page == 0) estimate = measured
        _measuredCount.value = _measuredCount.value + 1
        true
    }

    /**
     * بيرسم مربّع واحد.
     *
     * الحيلة هنا في [PdfPage.renderPageBitmap]: بنقوله ارسم الصفحة **كاملة**
     * بمقاس [gridWidth]×[gridHeight] بكسل، بس ابدأ من إحداثي سالب. PDFium
     * بيقصّ اللي برّه الصورة لوحده، فاللي بيتكتب في الـbitmap هو المربّع
     * المطلوب بس — مرسوم بدقّته الأصلية مش مكبّر من صورة أصغر.
     *
     * ده الفرق كله بين "حاد عند ٦٤×" و"ضبابي عند ٣×".
     */
    suspend fun renderTile(
        page: Int,
        gridWidth: Int,
        gridHeight: Int,
        originX: Int,
        originY: Int,
        tileWidth: Int,
        tileHeight: Int
    ): Bitmap? = withContext(dispatcher) {
        if (closed || page !in 0 until pageCount) return@withContext null
        if (tileWidth <= 0 || tileHeight <= 0) return@withContext null

        // ARGB_8888 عن قصد: RGB_565 بيوفّر نص الذاكرة بس دعمه في الكود
        // الأصلي بيختلف من نسخة للتانية، والرسمة اللي بتطلع ألوانها غلط
        // أسوأ من رسمة بتاخد ميجا زيادة.
        val bitmap = runCatching {
            Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return@withContext null

        val ok = runCatching {
            doc.openPage(page).use { p ->
                p.renderPageBitmap(
                    bitmap = bitmap,
                    startX = -originX,
                    startY = -originY,
                    drawSizeX = gridWidth,
                    drawSizeY = gridHeight,
                    renderAnnot = true,
                    textMask = false,
                    canvasColor = WHITE,
                    pageBackgroundColor = WHITE
                )
            }
            true
        }.getOrDefault(false)

        if (ok) bitmap else { bitmap.recycle(); null }
    }

    /** نص صفحة كامل — للبحث وللتحليل. */
    suspend fun pageText(page: Int): String = withContext(dispatcher) {
        if (closed || page !in 0 until pageCount) return@withContext ""
        runCatching {
            doc.openPage(page).use { p ->
                p.openTextPage().use { t ->
                    val n = t.textPageCountChars()
                    if (n <= 0) "" else t.textPageGetText(0, n).orEmpty()
                }
            }
        }.getOrDefault("")
    }

    /** فهرس المستند (outline) — مسطّح بمستوى العمق للعرض. */
    suspend fun outline(): List<OutlineEntry> = withContext(dispatcher) {
        if (closed) return@withContext emptyList()
        runCatching {
            val out = ArrayList<OutlineEntry>()
            fun walk(items: List<PdfDocument.Bookmark>, depth: Int) {
                items.forEach { b ->
                    out += OutlineEntry(b.title.orEmpty(), b.pageIdx.toInt(), depth)
                    if (depth < 6) walk(b.children, depth + 1)
                }
            }
            walk(doc.getTableOfContents(), 0)
            out
        }.getOrDefault(emptyList())
    }

    /** بيانات المستند — بتتعرض في لوحة المعلومات. */
    suspend fun metadata(): Map<String, String> = withContext(dispatcher) {
        if (closed) return@withContext emptyMap()
        runCatching {
            val m = doc.getDocumentMeta()
            buildMap {
                m.title?.takeIf { it.isNotBlank() }?.let { put("العنوان", it) }
                m.author?.takeIf { it.isNotBlank() }?.let { put("المؤلف", it) }
                m.subject?.takeIf { it.isNotBlank() }?.let { put("الموضوع", it) }
                m.creator?.takeIf { it.isNotBlank() }?.let { put("البرنامج المُنشئ", it) }
                m.producer?.takeIf { it.isNotBlank() }?.let { put("المُنتِج", it) }
                m.creationDate?.takeIf { it.isNotBlank() }?.let { put("تاريخ الإنشاء", it) }
            }
        }.getOrDefault(emptyMap())
    }

    /** بيدّي وصول مباشر للمستند لعمليات متخصّصة — دايماً على الخيط الصح. */
    suspend fun <T> withDocument(block: (PdfDocument) -> T): T? = withContext(dispatcher) {
        if (closed) null else runCatching { block(doc) }.getOrNull()
    }

    override fun close() {
        if (closed) return
        closed = true
        // القفل نفسه لازم يحصل على خيط PDFium، وبعدين نطفّي الخيط.
        runCatching {
            executor.submit {
                runCatching { doc.close() }
                runCatching { pfd.close() }
            }.get()
        }
        runCatching { scope.cancel() }
        runCatching { executor.shutdown() }
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()

        /**
         * بيفتح ملف. بيرمي [PdfOpenException] برسالة مفهومة بدل استثناء خام —
         * "الملف محمي بكلمة سر" حاجة والمستخدم لازم يعرفها، و"الملف تالف"
         * حاجة تانية خالص.
         */
        fun open(context: Context, file: File, password: String? = null): PdfDocumentSession {
            if (!file.exists()) throw PdfOpenException("الملف مش موجود")
            if (file.length() == 0L) throw PdfOpenException("الملف فاضي")

            val core = PdfiumCore(context.applicationContext, Config())
            val pfd = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrElse { throw PdfOpenException("مقدرناش نفتح الملف من القرص") }

            val doc = runCatching {
                if (password.isNullOrEmpty()) core.newDocument(pfd) else core.newDocument(pfd, password)
            }.getOrElse { e ->
                runCatching { pfd.close() }
                val msg = e.message.orEmpty()
                throw when {
                    e is io.legere.pdfiumandroid.PdfPasswordException ||
                        msg.contains("password", true) ->
                        PdfOpenException("الملف محمي بكلمة سر", needsPassword = true)
                    else -> PdfOpenException("الملف مش PDF سليم أو تالف")
                }
            }

            // دالة مش خاصية: `getPageCount()` معرّفة بـ`fun` في المكتبة،
            // فصيغة الخاصية `doc.pageCount` مابتترجمش.
            val count = runCatching { doc.getPageCount() }.getOrDefault(0)
            if (count <= 0) {
                runCatching { doc.close() }; runCatching { pfd.close() }
                throw PdfOpenException("الملف مافيهوش صفحات")
            }

            val exec = Executors.newSingleThreadExecutor { r ->
                Thread(r, "pdfium-${file.name.take(24)}").apply {
                    priority = Thread.NORM_PRIORITY - 1  // الواجهة أهم من الرندر
                    isDaemon = true
                }
            }

            return PdfDocumentSession(
                file = file, core = core, doc = doc, pfd = pfd,
                executor = exec, dispatcher = exec.asCoroutineDispatcher(),
                pageCount = count
            ).also { session ->
                // أول صفحة بتتقاس فوراً وبالتزامن: الرصّ محتاج تقدير قبل
                // ما يرسم أول إطار، وقياس صفحة واحدة أرخص من إطار فاضي.
                runCatching {
                    exec.submit {
                        doc.openPage(0).use { p ->
                            val s = SizePt(
                                p.getPageWidthPoint().toFloat(),
                                p.getPageHeightPoint().toFloat()
                            )
                            if (s.width > 0 && s.height > 0) {
                                session.sizes[0] = s
                                session.estimate = s
                                session._measuredCount.value = 1
                            }
                        }
                    }.get()
                }
            }
        }
    }
}

/** عنصر في فهرس المستند. */
data class OutlineEntry(val title: String, val page: Int, val depth: Int)

/** فشل فتح ملف، برسالة صالحة للعرض. */
class PdfOpenException(
    val userMessage: String,
    val needsPassword: Boolean = false
) : Exception(userMessage)
