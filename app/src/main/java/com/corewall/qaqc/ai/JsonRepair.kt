package com.corewall.qaqc.ai

/**
 * استخراج كائن JSON من رد الموديل — مع إصلاح الردود المقطوعة.
 *
 * الموديل بيتقطع في النص لما يوصل لحد التوكنز (خصوصاً مع BBS فيه مئات الصفوف).
 * قبل كده كنا برمي الرد كله ونقول "رد غير مفهوم" — رغم إن 90% من البيانات وصلت.
 * دلوقتي بنقصّ عند آخر عنصر مكتمل وبنقفل الأقواس المفتوحة، فبنكسب اللي وصل.
 */
object JsonRepair {

    /**
     * نتيجة الاستخراج. [repaired] بيقول إن الرد كان مقطوع واتصلّح،
     * يعني البيانات جزئية — لازم المستخدم يعرف ده مش نداري عليه.
     */
    data class Extracted(val json: String, val repaired: Boolean)

    /** بيرجّع كائن JSON متوازن، أو null لو مفيش أي كائن في الرد. */
    fun extractObject(raw: String): Extracted? {
        val text = stripFences(raw)
        val start = text.indexOf('{')
        if (start < 0) return null
        balanced(text, start)?.let { return Extracted(it, repaired = false) }
        return repair(text, start)?.let { Extracted(it, repaired = true) }
    }

    /** بيشيل علامات markdown اللي بيلفّ بيها الموديل الرد أحياناً. */
    private fun stripFences(raw: String): String = raw.trim()
        .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        .removeSuffix("```").trim()

    /** لو الرد كامل بالفعل، بنرجّع الكائن الأول المتوازن. */
    private fun balanced(text: String, start: Int): String? {
        var depth = 0
        forEachStructural(text, start) { i, c ->
            when (c) {
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * إصلاح رد مقطوع: بنقصّ عند آخر نقطة نعرف إن اللي قبلها مكتمل
     * (قوس اتقفل، أو فاصلة — الفاصلة معناها إن القيمة اللي قبلها خلصت)،
     * وبعدين بنقفل الحاويات اللي لسه مفتوحة.
     */
    private fun repair(text: String, start: Int): String? {
        var cut = -1
        forEachStructural(text, start) { i, c ->
            when (c) {
                '}', ']' -> cut = i + 1
                ',' -> cut = i
            }
        }
        if (cut <= start) return null

        val body = text.substring(start, cut).trimEnd().trimEnd(',')
        val open = ArrayDeque<Char>()
        forEachStructural(body, 0) { _, c ->
            when (c) {
                '{', '[' -> open.addLast(c)
                '}', ']' -> open.removeLastOrNull()
            }
        }
        val closers = open.reversed().joinToString("") { if (it == '{') "}" else "]" }
        return (body + closers).takeIf { it.isNotBlank() }
    }

    /**
     * بيمشي على الحروف اللي بره النصوص بس — عشان قوس جوّه string
     * مايتحسبش كبنية. الـescape متعامل معاه.
     */
    private inline fun forEachStructural(text: String, from: Int, action: (Int, Char) -> Unit) {
        var inStr = false
        var esc = false
        for (i in from until text.length) {
            val c = text[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                inStr -> Unit
                else -> action(i, c)
            }
        }
    }
}
