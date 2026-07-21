package com.corewall.qaqc.domain

import kotlin.math.PI

sealed interface CalloutResult {
    val totalDescription: String

    /** كولاوت تباعد: T25-200 => قطر 25مم كل 200مم => مساحة/متر. */
    data class Spaced(
        val diaMm: Int,
        val spacingMm: Int,
        val barAreaMm2: Double,
        val barsPerMeter: Double,
        val areaPerMeterMm2: Double
    ) : CalloutResult {
        override val totalDescription: String
            get() = "%.0f mm²/m (%.1f سيخ/م × %.1f mm²)".format(areaPerMeterMm2, barsPerMeter, barAreaMm2)
    }

    /** كولاوت عدد: 6T32 => 6 أسياخ قطر 32مم => مساحة إجمالية. */
    data class Counted(
        val count: Int,
        val diaMm: Int,
        val barAreaMm2: Double,
        val totalAreaMm2: Double
    ) : CalloutResult {
        override val totalDescription: String
            get() = "%.0f mm² (%d × %.1f mm²)".format(totalAreaMm2, count, barAreaMm2)
    }
}

object SteelCalculator {

    private val spacedRegex = Regex("""^T\s*(\d{1,2})\s*[-@]\s*(\d{2,4})$""", RegexOption.IGNORE_CASE)
    private val countedRegex = Regex("""^(\d{1,2})\s*T\s*(\d{1,2})$""", RegexOption.IGNORE_CASE)

    fun barArea(diaMm: Int): Double = PI * diaMm * diaMm / 4.0

    /**
     * بيفهم "T25-200" (قطر-تباعد) و"6T32" (عدد×قطر).
     * القيم المركبة زي "T10-200,T10-200" بتتحسب في [parseList] بجمع كل جزء.
     */
    fun parse(raw: String): CalloutResult? {
        val s = raw.trim()
        spacedRegex.matchEntire(s)?.let { m ->
            val dia = m.groupValues[1].toInt()
            val spacing = m.groupValues[2].toInt()
            if (spacing == 0) return null
            val area = barArea(dia)
            val perMeter = 1000.0 / spacing
            return CalloutResult.Spaced(dia, spacing, area, perMeter, area * perMeter)
        }
        countedRegex.matchEntire(s)?.let { m ->
            val count = m.groupValues[1].toInt()
            val dia = m.groupValues[2].toInt()
            val area = barArea(dia)
            return CalloutResult.Counted(count, dia, area, count * area)
        }
        return null
    }

    /** "T10-200,T10-200" => جزأين متجمّعين. بيرجع null لو أي جزء مش مفهوم. */
    fun parseList(raw: String): List<CalloutResult>? {
        val parts = raw.split(',', '+').map { it.trim() }.filter { it.isNotEmpty() && it != "-" }
        if (parts.isEmpty()) return null
        val results = parts.map { parse(it) ?: return null }
        return results
    }
}
