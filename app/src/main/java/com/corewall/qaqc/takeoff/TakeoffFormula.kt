package com.corewall.qaqc.takeoff

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * صيغة محسوبة — "شقة الأرضية = concrete.slabs.area1 * 0.2"، بترجع بأرقام
 * بنود تانية لايف بدل ما تتكتب يدوي وتنسى تتحدّث.
 *
 * ## النطاق — مقصود ومحدود
 *
 * الصيغة بترجع بس لبنود **نفس الرسمة** (ممكن صفحات مختلفة منها)، مش
 * بنود من رسمة تانية في نفس القسم. الفئات مشتركة بين رسمات القسم كله،
 * بس تقييم صيغة بيحتاج معايرة كل صفحة البند موجود فيها — ولو وسّعنا
 * النطاق لكل رسمات القسم كان لازم نفتح كل ملفات الـPDF بتاعتها مرة
 * واحدة، وده تمدّد معماري مش مستاهل في المرحلة دي.
 *
 * ## المراجع — بالـid مش بالاسم
 *
 * [refs] بتربط توكن في نص الصيغة (`concrete.slabs.area1`) بـ`id` البند
 * نفسه، مش بالاسم. لو المستخدم غيّر اسم البند بعدين، الصيغة تفضل شغالة —
 * لو كانت مربوطة بالاسم كانت هتتكسر بصمت.
 */
data class TakeoffFormula(
    val id: String,
    val name: String,
    val expr: String,
    val unit: String = "",
    /** خانات عشرية للتقريب. سالب = من غير تقريب. */
    val roundTo: Int = 2,
    val colorArgb: Long,
    /** توكن في [expr] → `TakeoffItem.id` اللي بيرجّع له. */
    val refs: Map<String, String> = emptyMap()
)

/** خطأ تحليل أو تقييم صيغة — رسالته عربية جاهزة تتعرض للمستخدم مباشرة. */
class FormulaException(message: String) : Exception(message)

/**
 * محرّك الصيغ — **كوتلن خالص**، بيتّست على الـJVM زي [TakeoffMath].
 *
 * محلّل نزولي تكراري (recursive-descent) حقيقي، **مش `eval()`**: أول
 * حاجة أي حد بيفكر فيها لمحرّك صيغ هي إنه ينادي مفسّر جافاسكريبت أو
 * يبني نص Kotlin ويكومبايله وقت التشغيل — والاتنين باب خلفي لتنفيذ كود.
 * هنا القواعد أربعة بس (`+ - * /`) ومجموعة دوال محدودة، فمفيش أي مسار
 * لتنفيذ حاجة المستخدم مايقصدهاش.
 */
object TakeoffFormulaEngine {

    data class Result(val value: Double?, val error: String?)

    /**
     * بتقيّم صيغة كاملة: بتجيب كمية كل مرجع من [items] بمعايرة صفحته هو
     * (من [pageGeometryFor])، وبعدين بتحلّل وتحسب [TakeoffFormula.expr].
     */
    fun evaluate(
        formula: TakeoffFormula,
        items: List<TakeoffItem>,
        pageGeometryFor: (Int) -> PageGeometry
    ): Result {
        val vars = HashMap<String, Double>()
        for ((token, itemId) in formula.refs) {
            val item = items.firstOrNull { it.id == itemId }
                ?: return Result(null, "#REF! $token اتمسح")
            vars[token] = TakeoffMath.netQuantity(item, items, pageGeometryFor(item.page))
        }
        return try {
            var value = Parser(tokenize(formula.expr), vars).parse()
            if (formula.roundTo >= 0) {
                val factor = 10.0.pow(formula.roundTo)
                value = round(value * factor) / factor
            }
            Result(value, null)
        } catch (e: FormulaException) {
            Result(null, e.message)
        }
    }

    // ═══════════════════════════════════════════════ التوكنة

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Ident(val name: String) : Token
        data object Plus : Token
        data object Minus : Token
        data object Star : Token
        data object Slash : Token
        data object LParen : Token
        data object RParen : Token
        data object Comma : Token
        data object End : Token
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        fun isIdentStart(c: Char) = c.isLetter() || c == '_'
        fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.'
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    val text = expr.substring(start, i)
                    tokens += Token.Number(text.toDoubleOrNull() ?: throw FormulaException("رقم غلط: $text"))
                }
                isIdentStart(c) -> {
                    val start = i
                    while (i < expr.length && isIdentPart(expr[i])) i++
                    tokens += Token.Ident(expr.substring(start, i))
                }
                c == '+' -> { tokens += Token.Plus; i++ }
                c == '-' -> { tokens += Token.Minus; i++ }
                c == '*' -> { tokens += Token.Star; i++ }
                c == '/' -> { tokens += Token.Slash; i++ }
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == ',' -> { tokens += Token.Comma; i++ }
                else -> throw FormulaException("رمز مش مفهوم: '$c'")
            }
        }
        tokens += Token.End
        return tokens
    }

    // ═══════════════════════════════════════════════ التحليل والحساب
    //
    // ترتيب النزول بيثبّت الأسبقية: expr (+ -) ← term (* /) ← unary (-) ← atom.

    private class Parser(private val tokens: List<Token>, private val vars: Map<String, Double>) {
        private var pos = 0
        private fun peek() = tokens[pos]
        private fun advance() = tokens[pos++]

        fun parse(): Double {
            val v = parseExpr()
            if (peek() != Token.End) throw FormulaException("رموز زيادة بعد نهاية الصيغة")
            return v
        }

        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    Token.Plus -> { advance(); v += parseTerm() }
                    Token.Minus -> { advance(); v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseUnary()
            while (true) {
                when (peek()) {
                    Token.Star -> { advance(); v *= parseUnary() }
                    Token.Slash -> {
                        advance()
                        val d = parseUnary()
                        if (d == 0.0) throw FormulaException("قسمة على صفر")
                        v /= d
                    }
                    else -> return v
                }
            }
        }

        private fun parseUnary(): Double =
            if (peek() == Token.Minus) { advance(); -parseUnary() } else parseAtom()

        private fun parseAtom(): Double {
            val t = advance()
            return when (t) {
                is Token.Number -> t.value
                is Token.Ident -> {
                    if (peek() == Token.LParen) {
                        advance()
                        val args = ArrayList<Double>()
                        if (peek() != Token.RParen) {
                            args += parseExpr()
                            while (peek() == Token.Comma) { advance(); args += parseExpr() }
                        }
                        if (peek() != Token.RParen) throw FormulaException("قوس ناقص بعد ${t.name}(")
                        advance()
                        callFunction(t.name, args)
                    } else {
                        vars[t.name] ?: throw FormulaException("مرجع غير معروف: ${t.name}")
                    }
                }
                Token.LParen -> {
                    val v = parseExpr()
                    if (peek() != Token.RParen) throw FormulaException("قوس ناقص")
                    advance()
                    v
                }
                else -> throw FormulaException("متوقّع رقم أو مرجع")
            }
        }

        private fun callFunction(name: String, args: List<Double>): Double = when (name.uppercase()) {
            "ROUND" -> {
                val n = if (args.size >= 2) args[1].toInt() else 0
                val factor = 10.0.pow(n)
                round(args.getOrElse(0) { 0.0 } * factor) / factor
            }
            "ABS" -> abs(args.getOrElse(0) { 0.0 })
            "MIN" -> args.minOrNull() ?: throw FormulaException("MIN محتاج قيمة على الأقل")
            "MAX" -> args.maxOrNull() ?: throw FormulaException("MAX محتاج قيمة على الأقل")
            "SQRT" -> sqrt(args.getOrElse(0) { 0.0 })
            "CEIL" -> ceil(args.getOrElse(0) { 0.0 })
            "FLOOR" -> floor(args.getOrElse(0) { 0.0 })
            else -> throw FormulaException("دالة غير معروفة: $name")
        }
    }
}

/**
 * اسم فئة/مجموعة/بند → توكن صالح في نص صيغة: عربي أو لاتيني أو رقم أو
 * شرطة تحت، وبس.
 */
fun takeoffSlug(s: String): String {
    val cleaned = s.trim().lowercase().map { c -> if (c.isLetterOrDigit() || c == '_') c else '_' }
        .joinToString("")
    val collapsed = Regex("_+").replace(cleaned, "_").trim('_')
    return collapsed.ifBlank { "x" }
}

/** "فئة.مجموعة.بند" — نفس مسار المراجع في نسخة الويب. */
fun takeoffVarPath(categoryName: String?, groupName: String?, itemName: String): String =
    listOfNotNull(
        categoryName?.let(::takeoffSlug)?.takeIf { it.isNotBlank() },
        groupName?.let(::takeoffSlug)?.takeIf { it.isNotBlank() },
        takeoffSlug(itemName)
    ).joinToString(".")

/**
 * توكن فريد في صيغة واحدة — لو نفس المسار مستخدم بالفعل لبند تاني، بيضيف
 * `_2`, `_3`… لحد ما يبقى فريد.
 */
fun takeoffUniqueToken(base: String, existing: Set<String>): String {
    if (base !in existing) return base
    var i = 2
    while ("${base}_$i" in existing) i++
    return "${base}_$i"
}
