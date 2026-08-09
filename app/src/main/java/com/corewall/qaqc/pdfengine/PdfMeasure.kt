package com.corewall.qaqc.pdfengine

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * القياس على الرسمة — الحساب والوحدات والصياغة.
 *
 * القاعدة اللي كل حاجة هنا مبنية عليها: **الملف بيقيس بالنقط، والمهندس
 * بيقيس بالمتر**. الجسر بينهم رقم واحد — [Scale.unitsPerPoint] — وكل باقي
 * الملف مجرد ضرب وقسمة عليه. لو الرقم ده غلط، كل قياس في المستند غلط،
 * علشان كده المعايرة بتتخزّن مع الملف مش في الذاكرة.
 */

/** نوع القياس. */
enum class MeasureKind(val id: String, val label: String) {
    DISTANCE("DISTANCE", "مسافة"),
    AREA("AREA", "مساحة"),
    COUNT("COUNT", "عدّ");

    companion object {
        fun fromId(id: String): MeasureKind = entries.firstOrNull { it.id == id } ?: DISTANCE
    }
}

/** وحدة الطول اللي المستخدم بيعاير بيها. */
enum class MeasureUnit(val id: String, val label: String, val perMetre: Double) {
    MM("mm", "مم", 1000.0),
    CM("cm", "سم", 100.0),
    M("m", "م", 1.0);

    companion object {
        fun fromId(id: String): MeasureUnit = entries.firstOrNull { it.id == id } ?: MM
    }
}

/**
 * معايرة صفحة: كام وحدة حقيقية في نقطة PDF واحدة.
 *
 * فيه طريقتين للوصول للرقم ده، والاتنين مدعومين عن قصد:
 *
 * 1. **مقياس قياسي** (١:٥٠، ١:١٠٠…). دقيق تماماً **لو** الملف اتصدّر
 *    بمقاسه الحقيقي من الأوتوكاد. النقطة = ١/٧٢ بوصة = ٠.٣٥٢٨ مم على
 *    الورق، فالمقياس ١:١٠٠ معناه ٣٥.٢٨ مم في الطبيعة لكل نقطة.
 * 2. **معايرة بخط معلوم**: المستخدم بيرسم على بُعد مكتوب في الرسمة
 *    وبيكتب قيمته. دي اللي بتنقذ الملفات اللي اتطبعت على مقاس مختلف أو
 *    اتعمللها scale وقت التصدير — وده بيحصل أكتر ما حد يتخيّل.
 */
data class Scale(
    val unitsPerPoint: Double,
    val unit: MeasureUnit,
    val note: String = ""
) {
    val isValid: Boolean get() = unitsPerPoint.isFinite() && unitsPerPoint > 0.0

    /** طول بالنقط → القيمة الحقيقية بوحدة المعايرة. */
    fun length(points: Double): Double = points * unitsPerPoint

    /** مساحة بالنقط المربّعة → مساحة حقيقية بالوحدة المربّعة. */
    fun area(pointsSquared: Double): Double = pointsSquared * unitsPerPoint * unitsPerPoint

    /**
     * صياغة مسافة.
     *
     * بنطلّع لـ"م" لوحدنا لما الرقم يعدّي المتر ومعايرتنا بالمم. مهندس
     * بيقرا "٤٢٥٠ مم" بيحوّلها في دماغه لـ٤.٢٥ متر — فالتحويل ده شغل
     * التطبيق مش شغله.
     */
    fun formatLength(points: Double): String {
        val value = length(points)
        return when {
            unit == MeasureUnit.MM && abs(value) >= 1000.0 -> "${trim(value / 1000.0)} م"
            unit == MeasureUnit.CM && abs(value) >= 100.0 -> "${trim(value / 100.0)} م"
            else -> "${trim(value)} ${unit.label}"
        }
    }

    /**
     * صياغة مساحة — **بالمتر المربّع دايماً**.
     *
     * ده مش تفضيل جمالي: الحصر والكميات في المشروع كلها بالمتر المربّع.
     * "٨٥٠٠٠٠٠ مم²" رقم صحيح ومالوش أي استخدام.
     */
    fun formatArea(pointsSquared: Double): String {
        val inUnit = area(pointsSquared)
        val squareMetres = inUnit / (unit.perMetre * unit.perMetre)
        return if (abs(squareMetres) < 0.01) "${trim(squareMetres * 10_000.0)} سم²"
        else "${trim(squareMetres)} م²"
    }

    private fun trim(value: Double): String {
        val abs = abs(value)
        val decimals = when {
            abs >= 100.0 -> 0
            abs >= 10.0 -> 1
            abs >= 1.0 -> 2
            else -> 3
        }
        val factor = TEN_POW[decimals]
        val rounded = (value * factor).roundToInt() / factor
        return if (decimals == 0) rounded.toInt().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }

    companion object {
        private val TEN_POW = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)

        /** مم على الورق لكل نقطة PDF: بوصة ÷ ٧٢. */
        const val MM_PER_POINT = 25.4 / 72.0

        /** المقاييس المعمارية الشائعة — الطريق السريع للمعايرة. */
        val COMMON_RATIOS = listOf(10, 20, 25, 50, 100, 200, 500, 1000)

        /** من مقياس ١:[ratio] — النتيجة بالمليمتر. */
        fun fromRatio(ratio: Int): Scale = Scale(
            unitsPerPoint = MM_PER_POINT * ratio,
            unit = MeasureUnit.MM,
            note = "١:$ratio"
        )

        /**
         * من خط معلوم: [realLength] بوحدة [unit] بيقابل [points] نقطة.
         * بيرجّع null لو الخط قصير أوي — معايرة على ٣ نقط معناها خطأ
         * ±٣٠٪ في كل قياس بعدها.
         */
        fun fromReference(points: Double, realLength: Double, unit: MeasureUnit): Scale? {
            if (points < MIN_REFERENCE_POINTS || realLength <= 0.0) return null
            return Scale(realLength / points, unit, "معايرة يدوية")
        }

        /** أقل طول مقبول لخط المعايرة، بالنقط (حوالي ١ سم على الورق). */
        const val MIN_REFERENCE_POINTS = 28.0
    }
}

// ══════════════════════════════════════════════════════════════ الحساب

/** طول مسار متعدد الأضلاع، بالنقط. */
fun polylineLength(points: List<Offset>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        total += hypot(
            (points[i].x - points[i - 1].x).toDouble(),
            (points[i].y - points[i - 1].y).toDouble()
        )
    }
    return total
}

/**
 * مساحة مضلّع بصيغة الحذاء (shoelace)، بالنقط المربّعة.
 *
 * القيمة المطلقة مقصودة: الصيغة بترجّع سالب لو المستخدم رسم عكس عقارب
 * الساعة، والمساحة السالبة مالهاش معنى هنا.
 */
fun polygonArea(points: List<Offset>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        sum += a.x.toDouble() * b.y.toDouble() - b.x.toDouble() * a.y.toDouble()
    }
    return abs(sum) / 2.0
}

/** محيط مضلّع مقفول — بيتعرض جنب المساحة. */
fun polygonPerimeter(points: List<Offset>): Double {
    if (points.size < 3) return polylineLength(points)
    return polylineLength(points + points.first())
}
