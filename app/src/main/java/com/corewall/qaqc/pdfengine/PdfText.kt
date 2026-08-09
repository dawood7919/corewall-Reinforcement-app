package com.corewall.qaqc.pdfengine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * طبقة النص — البحث والتحديد.
 *
 * الفرق بين عارض PDF حقيقي وعارض صور: **طبقة النص**. من غيرها الملف صورة
 * بتتحرك، ومعاها بيبقى مستند تقدر تدوّر فيه وتنسخ منه. الطبقة دي هي كمان
 * اللي بتخلّي الملف يوصل للذكاء الاصطناعي كنص مش كصورة — يعني تحليل أدق
 * وتكلفة أقل.
 */

/**
 * مستطيل نص على صفحة، **بنقط الصفحة وبأصل أعلى-يسار**.
 *
 * الأصل ده مقصود ومختلف عن اللي PDFium بيرجّعه (أسفل-يسار). القلب بيحصل
 * مرة واحدة عند حدود المكتبة في [PdfDocumentSession]، عشان باقي التطبيق
 * مايفضلش يفتكر إن فيه مساحتين رأسيتين.
 */
data class TextQuad(
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** نتيجة بحث واحدة. */
data class SearchHit(
    val page: Int,
    val charIndex: Int,
    val charCount: Int,
    /** سطر حوالين النتيجة — بيتعرض في قائمة النتائج. */
    val snippet: String,
    val quads: List<TextQuad>
)

/** حصيلة البحث في صفحة: النتائج + هل الصفحة فيها نص أصلاً. */
data class PageSearch(val hits: List<SearchHit>, val charCount: Int) {
    companion object {
        val EMPTY = PageSearch(emptyList(), 0)
    }
}

/** المستطيل اللي بيلمّ نتيجة كاملة — للتنقّل ليها. */
fun SearchHit.bounds(): Rect? {
    if (quads.isEmpty()) return null
    return Rect(
        quads.minOf { it.left },
        quads.minOf { it.top },
        quads.maxOf { it.right },
        quads.maxOf { it.bottom }
    )
}

// ══════════════════════════════════════════════════════════════ البحث

/**
 * حالة البحث.
 *
 * **البحث بيبدأ من الصفحة اللي انت فيها** وبيلفّ حوالين المستند. ده مش
 * تفصيلة: في مستند ٢٠٠ صفحة، البحث من الأول معناه إن أول نتيجة بتوديك
 * لصفحة ١ وانت واقف في صفحة ١٤٠. الترتيب بالقرب بيخلّي "التالي" يعني
 * "أقرب واحدة ليّا".
 *
 * والمسح **تدريجي**: كل صفحة نتايجها بتظهر أول ما تخلص، والمستخدم يقدر
 * ينط لأول نتيجة والباقي لسه بيتحمّل. البحث المتزامن في ملف كبير معناه
 * شاشة واقفة ثواني قبل أي رد فعل.
 */
@Stable
class PdfSearchState(private val session: PdfDocumentSession) {

    var query by mutableStateOf("")
        private set

    var matchCase by mutableStateOf(false)
        private set

    var wholeWord by mutableStateOf(false)
        private set

    val hits = mutableStateListOf<SearchHit>()

    /** فهرس النتيجة الحالية في [hits]، أو −١ لو مفيش. */
    var active by mutableIntStateOf(-1)
        private set

    var running by mutableStateOf(false)
        private set

    /** كام صفحة اتمسحت — بيغذّي شريط التقدّم. */
    var scanned by mutableIntStateOf(0)
        private set

    /**
     * المستند كله مالوش طبقة نص (صور ممسوحة).
     *
     * بنقول ده صراحةً بدل ما نسيب المستخدم يفتكر إن بحثه غلط. رسمة ممسوحة
     * بالسكانر مفيهاش حروف خالص، ومفيش بحث ممكن يشتغل عليها من غير OCR.
     */
    var noTextLayer by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    val activeHit: SearchHit? get() = hits.getOrNull(active)

    fun setQuery(next: String, fromPage: Int) {
        if (next == query) return
        query = next
        restart(fromPage)
    }

    fun setMatchCase(next: Boolean, fromPage: Int) {
        if (next == matchCase) return
        matchCase = next
        restart(fromPage)
    }

    fun setWholeWord(next: Boolean, fromPage: Int) {
        if (next == wholeWord) return
        wholeWord = next
        restart(fromPage)
    }

    /** النتيجة التالية. بتلفّ لأول القائمة لما تخلص. */
    fun next(): SearchHit? {
        if (hits.isEmpty()) return null
        active = (active + 1).mod(hits.size)
        return hits[active]
    }

    fun prev(): SearchHit? {
        if (hits.isEmpty()) return null
        active = (active - 1).mod(hits.size)
        return hits[active]
    }

    fun selectAt(index: Int): SearchHit? {
        if (index !in hits.indices) return null
        active = index
        return hits[index]
    }

    fun clear() {
        job?.cancel()
        job = null
        query = ""
        hits.clear()
        active = -1
        running = false
        scanned = 0
        noTextLayer = false
    }

    fun dispose() {
        job?.cancel()
        scope.cancel()
    }

    private fun restart(fromPage: Int) {
        job?.cancel()
        hits.clear()
        active = -1
        scanned = 0
        noTextLayer = false

        val q = query.trim()
        if (q.length < MIN_QUERY) {
            running = false
            return
        }

        running = true
        job = scope.launch {
            // تأخير صغير: كل حرف بيتكتب مش لازم يولّع مسح للمستند كله.
            delay(DEBOUNCE_MS)
            var sawText = false
            val order = scanOrder(fromPage, session.pageCount)
            for (page in order) {
                ensureActive()
                val result = session.searchPage(page, q, matchCase, wholeWord)
                if (result.charCount > 0) sawText = true
                if (result.hits.isNotEmpty()) {
                    hits += result.hits
                    // أول نتيجة بتتحدّد لوحدها: المستخدم كتب عشان يروح،
                    // مش عشان يبصّ على عدّاد.
                    if (active < 0) active = 0
                }
                scanned++
                if (hits.size >= MAX_TOTAL_HITS) break
            }
            noTextLayer = !sawText
            running = false
        }
    }

    companion object {
        /** حرف واحد في مستند كبير بيرجّع كل حاجة — ومش بيفيد حد. */
        private const val MIN_QUERY = 2
        private const val DEBOUNCE_MS = 220L
        private const val MAX_TOTAL_HITS = 2000

        /** ترتيب المسح: من الصفحة الحالية للأمام، وبعدين اللفّ للأول. */
        internal fun scanOrder(from: Int, count: Int): List<Int> {
            if (count <= 0) return emptyList()
            val start = from.coerceIn(0, count - 1)
            return List(count) { (start + it) % count }
        }
    }
}

// ══════════════════════════════════════════════════════════════ التحديد

/**
 * حالة تحديد النص.
 *
 * التحديد **جوّه صفحة واحدة** بس. التحديد العابر للصفحات في عارض بيتمرّر
 * بحرية في الاتجاهين معناه إن أي سحبة بتبقى غامضة (بتحدّد ولا بتمرّر؟)،
 * والمقابل — نسخ فقرة مقسومة على صفحتين — نادر في رسمة تنفيذية.
 */
@Stable
class PdfSelectionState(private val session: PdfDocumentSession) {

    var page by mutableIntStateOf(-1)
        private set

    var start by mutableIntStateOf(-1)
        private set

    var end by mutableIntStateOf(-1)
        private set

    var text by mutableStateOf("")
        private set

    val quads = mutableStateListOf<TextQuad>()

    val isActive: Boolean get() = page >= 0 && start >= 0 && end >= start

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** ضغطة مطوّلة: بتحدّد الكلمة اللي تحت الإصبع. بترجّع false لو مفيش نص. */
    fun selectWordAt(page: Int, xPt: Float, yPtFromTop: Float, onResult: (Boolean) -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            val index = session.charIndexAt(page, xPt, yPtFromTop)
            if (index < 0) {
                clear()
                onResult(false)
                return@launch
            }
            val word = session.wordAt(page, index) ?: (index..index)
            applyRange(page, word.first, word.last)
            onResult(quads.isNotEmpty())
        }
    }

    /**
     * سحب مقبض. [movingStart] بيقول أي طرف بيتحرّك؛ الطرف التاني ثابت.
     * لو المستخدم عدّى الطرف التاني، بنقلب المدى بدل ما نمنعه.
     */
    fun dragHandle(xPt: Float, yPtFromTop: Float, movingStart: Boolean) {
        val current = page
        if (current < 0) return
        job?.cancel()
        job = scope.launch {
            val index = session.charIndexAt(current, xPt, yPtFromTop, DRAG_TOLERANCE_PT)
            if (index < 0) return@launch
            val anchor = if (movingStart) end else start
            applyRange(current, minOf(anchor, index), maxOf(anchor, index))
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        page = -1
        start = -1
        end = -1
        text = ""
        quads.clear()
    }

    fun dispose() {
        job?.cancel()
        scope.cancel()
    }

    private suspend fun applyRange(onPage: Int, from: Int, to: Int) {
        val count = (to - from + 1).coerceAtLeast(1)
        val rects = session.quadsFor(onPage, from, count)
        val content = session.textRange(onPage, from, count)
        page = onPage
        start = from
        end = to
        text = content
        quads.clear()
        quads += rects
    }

    private companion object {
        /**
         * تسامح أوسع وقت السحب من وقت الضغط.
         *
         * وقت السحب الإصبع بيغطّي النص، والمستخدم بيسحب بالتقريب مش بالبكسل.
         * تسامح ضيّق هنا معناه إن المقبض بيقف كل ما يعدّي فراغ بين كلمتين.
         */
        const val DRAG_TOLERANCE_PT = 14f
    }
}
