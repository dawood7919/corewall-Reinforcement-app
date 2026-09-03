package com.corewall.qaqc.data

import com.corewall.qaqc.data.db.AttendanceRosterEntity

/**
 * تحويل شيت المقاول لصفوف حضور.
 *
 * ## المشكلة الحقيقية مش القراية
 *
 * [SheetReader] بيرجّع صفوف وأعمدة نص. الجزء اللي بيغلط هو **أنهي عمود
 * فيه الأسماء**: كل مقاول بيسلّم شيت بشكل مختلف — عنوان بالعربي أو
 * بالإنجليزي، صف عنوان واحد أو تلاتة فوقه ترويسة الشركة، وعمود مسلسل
 * قبل الاسم.
 *
 * لو خدنا العمود الأول دايماً، أغلب الشيتات هتستورد **أرقام مسلسلة
 * كأسماء** — والاستيراد هيقول "نجح".
 *
 * ## الطريقة
 *
 * بندوّر على صف الترويسة في أول عشر صفوف: الصف اللي فيه كلمة من كلمات
 * الاسم المعروفة. اللي تحته بيانات، واللي فوقه بيتتجاهل (ترويسة شركة،
 * عنوان مشروع، سطر فاضي).
 *
 * ولو مالقيناش ترويسة، بنرجّع **قايمة فاضية** بدل ما نخمّن. الاستيراد
 * اللي بيفشل بصوت عالي أحسن من شيت فيه مية صف اسمهم "1".
 */
object AttendanceSheetImport {

    private val NAME_WORDS = listOf("اسم", "الاسم", "name", "employee", "worker", "العامل", "العمال")
    private val CODE_WORDS = listOf("كود", "الكود", "code", "رقم", "id", "no", "no.", "number")
    private val TRADE_WORDS = listOf("تخصص", "التخصص", "مهنة", "المهنة", "trade", "job", "title", "designation")

    /** أبعد من كده مش ترويسة — ده جدول بيانات. */
    private const val HEADER_SEARCH_ROWS = 10

    fun rowsFrom(sheet: SheetReader.Sheet, fileId: Long): List<AttendanceRosterEntity> {
        val header = findHeader(sheet) ?: return emptyList()
        val nameColumn = header.name
        val now = System.currentTimeMillis()

        return sheet.rows
            .drop(header.row + 1)
            .mapIndexedNotNull { offset, row ->
                val name = row.getOrNull(nameColumn).orEmpty().trim()
                // صف من غير اسم = سطر فاصل أو إجمالي في آخر الشيت.
                if (name.isBlank() || looksLikeTotal(name)) return@mapIndexedNotNull null
                AttendanceRosterEntity(
                    fileId = fileId,
                    name = name,
                    code = header.code?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty(),
                    trade = header.trade?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty(),
                    ordinal = offset,
                    createdAt = now
                )
            }
    }

    data class Header(val row: Int, val name: Int, val code: Int?, val trade: Int?)

    fun findHeader(sheet: SheetReader.Sheet): Header? {
        for (index in 0 until minOf(HEADER_SEARCH_ROWS, sheet.rows.size)) {
            val row = sheet.rows[index]
            val name = row.indexOfFirstMatching(NAME_WORDS)
            if (name < 0) continue
            return Header(
                row = index,
                name = name,
                code = row.indexOfFirstMatching(CODE_WORDS).takeIf { it >= 0 && it != name },
                trade = row.indexOfFirstMatching(TRADE_WORDS).takeIf { it >= 0 && it != name }
            )
        }
        return null
    }

    /**
     * المطابقة على **الكلمة** مش على جزء منها.
     *
     * `contains("no")` بيطابق "Notes" و"Nominal"، فعمود الملاحظات كان
     * هيتاخد كعمود الكود. التقسيم على غير الحروف بيمنع ده من غير ما يمنع
     * عناوين زي "اسم العامل" أو "Employee Name".
     */
    private fun List<String>.indexOfFirstMatching(words: List<String>): Int =
        indexOfFirst { cell ->
            val tokens = cell.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
            tokens.any { token -> words.any { it.equals(token, ignoreCase = true) } }
        }

    private fun looksLikeTotal(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "total" || lower == "sum" || name.startsWith("الإجمالي") ||
            name.startsWith("اجمالي") || name.startsWith("المجموع")
    }
}
