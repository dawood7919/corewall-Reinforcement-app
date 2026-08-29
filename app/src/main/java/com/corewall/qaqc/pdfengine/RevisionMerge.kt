package com.corewall.qaqc.pdfengine

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * دمج إصدارات الشوب دروينج — بناء الست النهائي بآخر ريفيجن لكل لوحة.
 *
 * ## المشكلة
 *
 * الإرسالية بتتبعت على دفعات: `rev 00` فيه الست كامل، و`rev 01` فيه
 * **اللوحات اللي اتغيّرت بس**، و`rev 02` كمان. اللي محتاجه الموقع ملف
 * واحد فيه كل لوحة بآخر إصدار صدر منها.
 *
 * ## القاعدة اللي كل الباقي مبني عليها
 *
 * **المطابقة برقم اللوحة، مش بترتيب الصفحة.** ملف الريفيجن صفحاته أقل
 * وترتيبها مختلف، فالدمج بالفهرس بيطلّع لوحة غلط **من غير أي رسالة خطأ**
 * — وده أسوأ نوع خطأ في الرسومات: ملف يبان سليم وفيه لوحة قديمة.
 *
 * ## ليه الدمج بيحصل على مرحلتين
 *
 * نسخ صفحة من مستند لمستند تاني بالإيد بيسيب مراجع بتشاور على كائنات في
 * المستند الأصلي، والنتيجة ملف بيفتح فاضي أو بيقع. فبنعمل الآتي:
 * `PDFMergerUtility` بيلزق كل الملفات في مستند واحد (استنساخ عميق صحيح)،
 * وبعدين [PdfOps.applyPagePlan] بيختار الصفحات من **جوّه** المستند ده —
 * وإعادة الترتيب جوّه مستند واحد عملية آمنة.
 *
 * الملف المؤقّت والقراءة بيشتغلوا على ملفات مؤقّتة مش على الرام
 * ([MemoryUsageSetting.setupTempFileOnly]): ست رسومات A0 ممكن يبقى مئات
 * الميجات، والدمج في الرام كان هيقفل التطبيق على أول إرسالية حقيقية.
 */
object RevisionMerge {

    /** النمط الافتراضي — أرقام لوحات باكارات (`…-SHD-ST-RFT-20003`). */
    const val DEFAULT_PATTERN = "RFT-(\\d{5})"

    /** أعرض من كده = فرخ رسمة، مش A4. */
    private const val LARGE_SHEET_PT = 1000f

    /** من أنهي ملف ناخد صفحات الإرسالية (الغلاف / خطاب الإرسال). */
    enum class Covers { LATEST, FIRST, ALL, NONE }

    /** ترتيب اللوحات في الملف النهائي. */
    enum class Order { NUMBER, FILE }

    /** نتيجة قراءة ملف واحد. */
    data class FileScan(
        val file: File,
        val pageCount: Int,
        /** صفحات مش لوحات — إرسالية، خطاب، ورقة مراجعة. */
        val covers: List<Int>,
        /** رقم اللوحة ← فهرس صفحتها في الملف ده. */
        val drawings: Map<String, Int>,
        val warnings: List<String>
    ) {
        val name: String get() = file.name
    }

    /** الخطة قبل الكتابة — دي اللي المستخدم بيراجعها. */
    data class Plan(
        val scans: List<FileScan>,
        /** أرقام اللوحات بترتيب الإخراج. */
        val numbers: List<String>,
        /** رقم اللوحة ← فهرس الملف اللي هناخد منه. */
        val chosen: Map<String, Int>,
        val warnings: List<String>
    ) {
        fun countFrom(fileIndex: Int): Int = numbers.count { chosen[it] == fileIndex }
    }

    /**
     * ترتيب تلقائي بالرقم اللي في آخر اسم الملف (`…-00.pdf`, `…-01.pdf`).
     *
     * بيتطبّق **بس** لو كل الملفات ليها الرقم ده. غير كده بنسيب ترتيب
     * المستخدم زي ما هو — تخمين نص الترتيب أسوأ من عدم التخمين.
     */
    fun autoOrder(files: List<File>): List<File> {
        val rx = Regex("-(\\d{1,3})\\.pdf$", RegexOption.IGNORE_CASE)
        val keyed = files.map { it to rx.find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        if (keyed.any { it.second == null }) return files
        return keyed.sortedBy { it.second!! }.map { it.first }
    }

    /** اسم الملف النهائي المقترح من أحدث ملف. */
    fun suggestedName(newest: File): String {
        val base = newest.nameWithoutExtension.replace(Regex("-\\d{1,3}$"), "")
        return "$base-FINAL.pdf"
    }

    fun validatePattern(pattern: String): String? =
        runCatching { Regex(pattern) }.exceptionOrNull()?.let { "النمط مش صحيح: ${it.message}" }

    /**
     * قراية كل الملفات وتصنيف صفحاتها.
     *
     * التصنيف:
     * - الصفحة فيها رقم واحد متميّز ← **لوحة**.
     * - فيها أكتر من رقم وهي فرخ كبير ← لوحة، بالرقم الأكتر تكراراً
     *   (رقم اللوحة بيتكرر في البلوك وفي الجدول، وأرقام المراجع بتتذكر مرة).
     * - غير كده ← **صفحة إرسالية** (الإرسالية بتعدّد أرقام كتير على A4).
     *
     * النص بيتشال منه المسافات قبل المطابقة، لأن الرقم بيتقطّع بين
     * أجزاء نص في الـPDF فبيفشل المطابقة وهو موجود بالعين.
     */
    suspend fun scan(
        files: List<File>,
        pattern: String,
        onProgress: suspend (Float) -> Unit
    ): Result<List<FileScan>> = withContext(Dispatchers.IO) {
        runCatching {
            require(files.isNotEmpty()) { "مفيش ملفات" }
            val rx = Regex(pattern)
            val spaces = Regex("\\s+")
            val out = ArrayList<FileScan>(files.size)

            files.forEachIndexed { fileIndex, file ->
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val stripper = PDFTextStripper().apply { sortByPosition = false }
                    val covers = ArrayList<Int>()
                    val drawings = LinkedHashMap<String, Int>()
                    val warnings = ArrayList<String>()
                    val blanks = ArrayList<Int>()

                    for (page in 0 until doc.numberOfPages) {
                        stripper.startPage = page + 1
                        stripper.endPage = page + 1
                        val flat = runCatching { stripper.getText(doc) }
                            .getOrDefault("")
                            .replace(spaces, "")
                        val hits = rx.findAll(flat)
                            .map { m -> m.groupValues.getOrNull(1)?.ifBlank { null } ?: m.value }
                            .toList()
                        val distinct = hits.distinct()

                        val box = doc.getPage(page).mediaBox
                        val turned = (doc.getPage(page).rotation % 180) != 0
                        val width = if (turned) box.height else box.width
                        val wide = width >= LARGE_SHEET_PT

                        val number = when {
                            distinct.size == 1 -> distinct.first()
                            distinct.size > 1 && wide ->
                                hits.groupingBy { it }.eachCount()
                                    .maxByOrNull { it.value }?.key
                            else -> null
                        }

                        if (number == null) {
                            if (wide && distinct.isEmpty()) blanks += page + 1
                            covers += page
                        } else {
                            val previous = drawings[number]
                            if (previous != null) {
                                warnings += "${file.name}: اللوحة $number مكررة " +
                                    "(صفحة ${previous + 1} و ${page + 1}) — أخدنا الأخيرة."
                            }
                            drawings[number] = page
                        }
                        onProgress((fileIndex + (page + 1f) / doc.numberOfPages) / files.size)
                    }

                    if (blanks.isNotEmpty()) {
                        warnings += "${file.name}: صفحات كبيرة من غير رقم لوحة " +
                            "(${blanks.joinToString("، ") { "ص $it" }}) — اتحطت مع صفحات " +
                            "الإرسالية. لو دي رسومات ممسوحة ضوئياً راجعها بنفسك."
                    }
                    if (drawings.isEmpty()) {
                        warnings += "${file.name}: مفيش أي رقم لوحة اتلقى فيه."
                    }

                    out += FileScan(file, doc.numberOfPages, covers, drawings, warnings)
                }
            }
            out
        }
    }

    /**
     * بناء الخطة: **أحدث ملف فيه اللوحة هو اللي بيكسب**.
     *
     * الترتيب في [scans] هو مصدر الحقيقة للأقدم/الأحدث — آخر عنصر = الأحدث.
     */
    fun plan(scans: List<FileScan>, order: Order): Plan {
        val chosen = LinkedHashMap<String, Int>()
        scans.forEachIndexed { index, scan ->
            scan.drawings.keys.forEach { number -> chosen[number] = index }
        }
        val numbers = when (order) {
            Order.NUMBER -> chosen.keys.sorted()
            Order.FILE -> {
                val base = scans.firstOrNull()?.drawings.orEmpty()
                val inBase = base.entries.sortedBy { it.value }.map { it.key }
                inBase + (chosen.keys - base.keys).sorted()
            }
        }
        return Plan(scans, numbers, chosen, scans.flatMap { it.warnings })
    }

    /**
     * كتابة الملف النهائي.
     *
     * [chosen] بيتبعت لوحده مش من [plan] عشان تعديلات المستخدم في الجدول
     * تتطبّق من غير ما نعيد قراية الملفات.
     */
    suspend fun build(
        plan: Plan,
        chosen: Map<String, Int>,
        covers: Covers,
        dest: File,
        workDir: File,
        onProgress: suspend (Float) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var temp: File? = null
        runCatching {
            val scans = plan.scans
            require(scans.isNotEmpty()) { "مفيش ملفات" }

            onProgress(0.05f)
            val combined = if (scans.size == 1) scans.first().file else {
                require(workDir.exists() || workDir.mkdirs()) { "مقدرناش نجهّز مجلد مؤقّت" }
                val file = File(workDir, "revision-merge-${System.currentTimeMillis()}.pdf")
                temp = file
                PDFMergerUtility().apply {
                    destinationFileName = file.absolutePath
                    scans.forEach { addSource(it.file) }
                }.mergeDocuments(MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir))
                file
            }
            onProgress(0.6f)

            // فهرس أول صفحة لكل ملف جوّه المستند الملزوق.
            val offsets = IntArray(scans.size)
            var running = 0
            scans.forEachIndexed { index, scan ->
                offsets[index] = running
                running += scan.pageCount
            }

            val pages = ArrayList<Int>()
            val coverFiles = when (covers) {
                Covers.LATEST -> listOf(scans.lastIndex)
                Covers.FIRST -> listOf(0)
                Covers.ALL -> scans.indices.toList()
                Covers.NONE -> emptyList()
            }
            coverFiles.forEach { index ->
                scans[index].covers.forEach { page -> pages += offsets[index] + page }
            }
            plan.numbers.forEach { number ->
                val index = chosen[number] ?: return@forEach
                val page = scans.getOrNull(index)?.drawings?.get(number) ?: return@forEach
                pages += offsets[index] + page
            }
            check(pages.isNotEmpty()) { "الخطة مافيهاش ولا صفحة — راجع النمط أو خلي صفحات الإرسالية داخلة" }

            onProgress(0.7f)
            PdfOps.applyPagePlan(
                src = combined,
                dest = dest,
                plan = pages.map { PdfOps.PagePlan(it) },
                memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
            ).getOrThrow()
            onProgress(1f)
            pages.size
        }.also { temp?.delete() }
    }
}
