package com.corewall.qaqc.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.FindFlags
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
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
     * صفحات PDFium المفتوحة — كاش صغير بترتيب الاستخدام.
     *
     * `doc.openPage(n)` مش استدعاء رخيص: PDFium بيفكّ محتوى الصفحة ويبني
     * شجرة الكائنات بتاعتها. الكود كان بينده `openPage(…).use { }` **لكل
     * مربّع على حدة**، يعني صفحة رسمة فيها اتناشر مربّع مرئي كانت بتتفتح
     * وتتقفل اتناشر مرة لكل مستوى تكبير — وده أغلى بكتير من الرسم نفسه.
     *
     * الكاش هنا **مش محتاج أقفال**: كل الدوال اللي بتلمسه `withContext
     * (dispatcher)`، والـdispatcher خيط واحد. ده نفس السبب اللي خلّى
     * المستند كله على خيط واحد من الأصل.
     *
     * السقف تلات صفحات: الصفحة اللي انت عليها واللي فوقها واللي تحتها —
     * ودول اللي التمرير المستمر بيلمسهم فعلاً.
     */
    private val openPages = LinkedHashMap<Int, PdfPage>(4, 0.75f, true)

    /**
     * بيرجّع صفحة مفتوحة (من الكاش أو بيفتحها). **ممنوع تقفلها** — الكاش
     * هو اللي بيقفل، وقفلها من برّه بيسيب مؤشّر ميت جوّه الخريطة.
     *
     * لازم يتنده من على [dispatcher] بس.
     */
    private fun pageHandle(index: Int): PdfPage? {
        if (closed || index !in 0 until pageCount) return null
        openPages[index]?.let { return it }
        val opened = runCatching { doc.openPage(index) }.getOrNull() ?: return null
        openPages[index] = opened
        if (openPages.size > MAX_OPEN_PAGES) {
            val oldest = openPages.entries.iterator()
            if (oldest.hasNext()) {
                val entry = oldest.next()
                oldest.remove()
                runCatching { entry.value.close() }
            }
        }
        return opened
    }

    /** بيقفل كل الصفحات المفتوحة. لازم يتنده من على [dispatcher]. */
    private fun closeOpenPages() {
        openPages.values.forEach { runCatching { it.close() } }
        openPages.clear()
    }

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
        val handle = pageHandle(page) ?: return@withContext false
        val measured = runCatching {
            SizePt(handle.getPageWidthPoint().toFloat(), handle.getPageHeightPoint().toFloat())
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
            val p = pageHandle(page) ?: return@runCatching false
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
            true
        }.getOrDefault(false)

        if (ok) bitmap else { bitmap.recycle(); null }
    }

    /** نص صفحة كامل — للبحث وللتحليل. */
    suspend fun pageText(page: Int): String = withContext(dispatcher) {
        if (closed || page !in 0 until pageCount) return@withContext ""
        onTextPage(page) { t, _ ->
            val n = t.textPageCountChars()
            if (n <= 0) "" else t.readText(0, n)
        } ?: ""
    }

    // ══════════════════════════════════════════════════════ طبقة النص

    /**
     * بيفتح صفحة نصّها ويدّي معاها **ارتفاع الصفحة بالنقط**.
     *
     * الارتفاع مش زيادة: إحداثيات PDFium النصّية أصلها **أسفل يسار** والمحور
     * الرأسي بيزيد لفوق، بينما المحرّك كله شغّال بأصل **أعلى يسار**. من غير
     * القلب ده، كل مستطيل بحث بيتحطّ مقلوب رأسياً على الصفحة.
     */
    private fun <T> onTextPage(page: Int, block: (PdfTextPage, Float) -> T): T? {
        if (closed || page !in 0 until pageCount) return null
        return runCatching {
            val p = pageHandle(page) ?: return null
            val height = p.getPageHeightPoint().toFloat()
            // صفحة النص نفسها بتفضل قصيرة العمر: البحث والتحديد بيحصلوا
            // على دفعات، ومسكها مفتوحة بيشيل ذاكرة من غير مكسب.
            p.openTextPage().use { t -> block(t, height) }
        }.getOrNull()
    }

    /**
     * عدد الحروف في صفحة. **صفر معناه إن الصفحة صورة** — رسمة ممسوحة ضوئياً
     * مش متولّدة من CAD. البحث والتحديد فيها مش ناقصين، هما مستحيلين من غير
     * OCR (جايّ في مرحلة القياس والـOCR).
     */
    suspend fun charCount(page: Int): Int = withContext(dispatcher) {
        onTextPage(page) { t, _ -> t.textPageCountChars() }?.coerceAtLeast(0) ?: 0
    }

    /**
     * بيدوّر على [query] في صفحة واحدة.
     *
     * البحث بيتعمل بمحرّك PDFium نفسه مش بمقارنة نصوص في Kotlin، وده مقصود:
     * PDFium بيعرف يوصّل الكلمة المقطوعة بين سطرين، وبيعرف الحروف اللي في
     * الملف بترميز غريب، وبيدّينا **مستطيلات** الكلمة على الصفحة — وده اللي
     * بيخلّي التظليل يقع في مكانه بالظبط.
     */
    suspend fun searchPage(
        page: Int,
        query: String,
        matchCase: Boolean = false,
        wholeWord: Boolean = false,
        maxHits: Int = MAX_HITS_PER_PAGE
    ): PageSearch = withContext(dispatcher) {
        if (query.isBlank()) return@withContext PageSearch.EMPTY
        onTextPage(page) { t, pageHeight ->
            val total = t.textPageCountChars()
            if (total <= 0) return@onTextPage PageSearch.EMPTY

            val flags = buildSet {
                if (matchCase) add(FindFlags.MatchCase)
                if (wholeWord) add(FindFlags.MatchWholeWord)
            }
            val finder = t.findStart(query, flags, 0)
                ?: return@onTextPage PageSearch(emptyList(), total)

            val out = ArrayList<SearchHit>()
            finder.use { f ->
                while (out.size < maxHits && f.findNext()) {
                    val index = f.getSchResultIndex()
                    val count = f.getSchCount()
                    if (index < 0 || count <= 0) break
                    out += SearchHit(
                        page = page,
                        charIndex = index,
                        charCount = count,
                        snippet = t.snippet(total, index, count),
                        quads = t.quads(page, pageHeight, index, count)
                    )
                }
            }
            PageSearch(out, total)
        } ?: PageSearch.EMPTY
    }

    /** مستطيلات مدى حروف — للتحديد ولتظليل نتيجة البحث. */
    suspend fun quadsFor(page: Int, start: Int, count: Int): List<TextQuad> =
        withContext(dispatcher) {
            if (count <= 0) return@withContext emptyList()
            onTextPage(page) { t, h -> t.quads(page, h, start, count) } ?: emptyList()
        }

    /** نص مدى حروف — ده اللي بيتنسخ للحافظة. */
    suspend fun textRange(page: Int, start: Int, count: Int): String = withContext(dispatcher) {
        if (count <= 0) return@withContext ""
        onTextPage(page) { t, _ ->
            val total = t.textPageCountChars()
            if (total <= 0) return@onTextPage ""
            val from = start.coerceIn(0, total - 1)
            val len = count.coerceAtMost(total - from)
            t.readText(from, len)
        } ?: ""
    }

    /**
     * الحرف اللي تحت نقطة معيّنة. [yPtFromTop] بأصل أعلى-يسار زي باقي المحرّك.
     * بيرجّع −١ لو مفيش نص قريب.
     */
    suspend fun charIndexAt(
        page: Int,
        xPt: Float,
        yPtFromTop: Float,
        tolerancePt: Float = HIT_TOLERANCE_PT
    ): Int = withContext(dispatcher) {
        onTextPage(page) { t, height ->
            t.textPageGetCharIndexAtPos(
                xPt.toDouble(),
                (height - yPtFromTop).toDouble(),
                tolerancePt.toDouble(),
                tolerancePt.toDouble()
            )
        } ?: -1
    }

    /**
     * الكلمة اللي فيها الحرف ده.
     *
     * بنجيب **نافذة** حوالين الحرف بنداء واحد وبنوسّع في Kotlin، مش بنسأل
     * PDFium عن كل حرف لوحده. الفرق مش تحسين نظري: كل نداء أصلي بياخد قفل
     * عام، ومئة نداء وسط ضغطة مطوّلة بتحسّ كأن التطبيق واقف.
     */
    suspend fun wordAt(page: Int, index: Int): IntRange? = withContext(dispatcher) {
        onTextPage(page) { t, _ ->
            val total = t.textPageCountChars()
            if (total <= 0 || index !in 0 until total) return@onTextPage null
            val from = (index - WORD_WINDOW).coerceAtLeast(0)
            val to = (index + WORD_WINDOW).coerceAtMost(total)
            val text = t.readText(from, to - from)
            val local = index - from
            if (local !in text.indices) return@onTextPage index..index
            if (!text[local].isWordChar()) return@onTextPage index..index
            var s = local
            while (s > 0 && text[s - 1].isWordChar()) s--
            var e = local
            while (e < text.lastIndex && text[e + 1].isWordChar()) e++
            (from + s)..(from + e)
        }
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

    /**
     * قراءة نص بمدى.
     *
     * بنستخدم `textPageGetTextLegacy` مش `textPageGetText` عن قصد: التانية
     * بتحجز مصفوفة بطول الحروف بالظبط، و`FPDFText_GetText` الأصلية بتكتب
     * حرف زيادة (الـNUL الخاتم) — يعني كتابة برّه المصفوفة. الأولى بتحجز
     * `length + 1` وبتقصّ الـNUL بعد القراية، وده السلوك الصح.
     */
    private fun PdfTextPage.readText(start: Int, length: Int): String {
        if (length <= 0) return ""
        return runCatching { textPageGetTextLegacy(start, length) }.getOrNull().orEmpty()
    }

    /** سطر معاينة حوالين النتيجة — عشان المستخدم يعرف النتيجة دي إيه قبل ما يروحلها. */
    private fun PdfTextPage.snippet(total: Int, index: Int, count: Int): String {
        val from = (index - SNIPPET_LEAD).coerceAtLeast(0)
        val to = (index + count + SNIPPET_TRAIL).coerceAtMost(total)
        return readText(from, to - from)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(WHITESPACE, " ")
            .trim()
    }

    /**
     * مستطيلات مدى حروف، مقلوبة لأصل أعلى-يسار.
     *
     * ترتيب النداءات مش اختياري: `FPDFText_CountRects` هي اللي بتحضّر المدى
     * جوّه PDFium، و`FPDFText_GetRect` بترجّع من آخر مدى اتحضّر. لو ناديت
     * الرسم من غير العدّ الأول بترجع مستطيلات من بحث قديم.
     */
    private fun PdfTextPage.quads(
        page: Int,
        pageHeight: Float,
        start: Int,
        count: Int
    ): List<TextQuad> {
        val n = runCatching { textPageCountRects(start, count) }.getOrDefault(0)
        if (n <= 0) return emptyList()
        val out = ArrayList<TextQuad>(n)
        for (i in 0 until n) {
            val r = runCatching { textPageGetRect(i) }.getOrNull() ?: continue
            val left = minOf(r.left, r.right)
            val right = maxOf(r.left, r.right)
            // القلب: y من أسفل ← y من أعلى. الأعلى في PDF هو الأكبر رقماً.
            val top = pageHeight - maxOf(r.top, r.bottom)
            val bottom = pageHeight - minOf(r.top, r.bottom)
            if (right - left <= 0f || bottom - top <= 0f) continue
            out += TextQuad(page, left, top, right, bottom)
        }
        return out
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
                // الصفحات الأول: قفل المستند وفيه صفحات مفتوحة تسريب
                // للذاكرة الأصلية (والمكتبة مش بتتشكى).
                closeOpenPages()
                runCatching { doc.close() }
                runCatching { pfd.close() }
            }.get()
        }
        runCatching { scope.cancel() }
        runCatching { executor.shutdown() }
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** أقصى عدد صفحات PDFium مفتوحة في وقت واحد لكل مستند. */
        private const val MAX_OPEN_PAGES = 3

        /**
         * سقف نتائج الصفحة الواحدة.
         *
         * جدول تسليح فيه "T10" مية مرة في صفحة واحدة نتيجته إن الشاشة بتبقى
         * صفرا بالكامل — يعني ولا نتيجة مفيدة، وتلات آلاف مستطيل بيترسموا كل
         * إطار. السقف بيحمي الاتنين.
         */
        private const val MAX_HITS_PER_PAGE = 300

        /** نصف قطر البحث عن حرف تحت الإصبع، بنقط الصفحة. */
        private const val HIT_TOLERANCE_PT = 6f

        /** نص النافذة اللي بنجيبها حوالين الحرف عشان نوسّع للكلمة. */
        private const val WORD_WINDOW = 48

        private const val SNIPPET_LEAD = 32
        private const val SNIPPET_TRAIL = 48

        private val WHITESPACE = Regex("\\s+")

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

/**
 * حرف بيتعدّ جزء من "كلمة" وقت التحديد بضغطة مطوّلة.
 *
 * الشرطة والشرطة السفلية داخلة عن قصد: أكواد المشروع نفسها (`T1-FGN-B1`،
 * `2LT10-200`) هي أكتر حاجة المهندس بيحدّدها، ولو الشرطة قطعت الكلمة
 * كل تحديد هيرجّع نتفة من الكود مش الكود كله.
 */
private fun Char.isWordChar(): Boolean =
    isLetterOrDigit() || this == '_' || this == '-' || this == '/'

/** عنصر في فهرس المستند. */
data class OutlineEntry(val title: String, val page: Int, val depth: Int)

/** فشل فتح ملف، برسالة صالحة للعرض. */
class PdfOpenException(
    val userMessage: String,
    val needsPassword: Boolean = false
) : Exception(userMessage)
