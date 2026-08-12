package com.corewall.qaqc.takeoff

/**
 * تعليق — سحابة أو سهم أو نص. طبقة توضيح بصري بس.
 *
 * منفصلة عن [TakeoffItem] عن قصد، مش نوع تاني من [TakeoffTool]: التعليق
 * مالوش كمية ولا فئة ولا سعر، ومفيش أي داعي يظهر في الإجماليات أو
 * الـBOQ أو مراجع الصيغ — فصله في نموذج مستقل بيمنع أي مكان بيلف على
 * بنود الحصر إنه "ينسى" يستبعده، لأنه أصلاً مش موجود في نفس القايمة.
 */
enum class TakeoffAnnotationType { CLOUD, ARROW, TEXT }

data class TakeoffAnnotation(
    val id: String,
    val type: TakeoffAnnotationType,
    val page: Int,
    /** مضلّع مقفول (سحابة) أو نقطتين (سهم) أو نقطة واحدة (نص). */
    val verts: List<TakeoffPoint>,
    val colorArgb: Long,
    val text: String = "",
    val visible: Boolean = true
)
