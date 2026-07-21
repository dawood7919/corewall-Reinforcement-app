package com.corewall.qaqc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** فئة العنصر على المسقط. */
enum class ElementCategory {
    WALL, COUPLING_BEAM, INTERNAL_BEAM, OTHER;

    companion object {
        fun fromJson(value: String): ElementCategory = when (value) {
            "wall" -> WALL
            "coupling_beam" -> COUPLING_BEAM
            "internal_beam" -> INTERNAL_BEAM
            // TODO: عناصر الفئة "other" (3 عناصر) نوعها غير مؤكد حتى الآن —
            // معروضة مؤقتاً بلون البيمات الداخلية لحين تحديد نوعها الفعلي.
            else -> OTHER
        }
    }
}

@Serializable
data class PlanElement(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val category: String
) {
    val cat: ElementCategory get() = ElementCategory.fromJson(category)
}

@Serializable
data class PlanData(
    val viewBox: String,
    val elements: List<PlanElement>
) {
    /** minX, minY, width, height */
    val viewBoxRect: DoubleArray by lazy {
        viewBox.trim().split(Regex("[ ,]+")).map { it.toDouble() }.toDoubleArray()
    }
}

/**
 * مدى تسليح حائط. المدى غير شامل للنهاية: from <= level < to.
 * قيم to الخاصة ("TOP ROOF", "FM LMR") تعني الاستمرار حتى أعلى المبنى.
 */
@Serializable
data class WallRange(
    val from: String,
    val to: String? = null,
    val w: Int,
    val v: String,
    val h: String,
    val t: String = "-",
    val rev: Boolean = false,
    val note: String? = null,
    val edited: Boolean = false
)

/**
 * مدى تسليح كمرة. المدى شامل للنهاية: from <= level <= to.
 * لو to غايبة فالمدى دور واحد فقط (from نفسه).
 */
@Serializable
data class BeamRange(
    val from: String,
    val to: String? = null,
    val w: Int,
    val d: Int,
    @SerialName("B") val bottom: List<String>,
    @SerialName("T") val top: List<String>,
    val side: String = "-",
    val links: String = "-",
    val rev: Boolean = false,
    val note: String? = null,
    val edited: Boolean = false
)

@Serializable
data class ScheduleData(
    val levels: List<String>,
    val walls: Map<String, List<WallRange>>,
    val beams: Map<String, List<BeamRange>>
) {
    val allMarks: List<String> by lazy { walls.keys.toList() + beams.keys.toList() }

    fun isWallMark(mark: String) = walls.containsKey(mark)
    fun isBeamMark(mark: String) = beams.containsKey(mark)
}

/** حالة الفحص لعنصر في دور معيّن. */
enum class InspectionStatus(val label: String) {
    NONE("بدون"),
    WIR_SUBMITTED("WIR مقدَّم"),
    APPROVED("مقبول"),
    CAST("تم الصب"),
    REJECTED("مرفوض");

    companion object {
        fun from(name: String?): InspectionStatus =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
