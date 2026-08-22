package com.corewall.qaqc.v2.pdf

import com.corewall.qaqc.data.db.TakeoffItemEntity
import com.corewall.qaqc.takeoff.TakeoffPoint

/**
 * جسر ترحيل مؤقت بين مساحة عمل V2 ومخزن الحصر الحالي. يحافظ على إحداثيات
 * الصفحة المنسّبة وصيغة JSON المستخدمة بالفعل، لذلك لا يحتاج Room إلى هجرة.
 */
internal fun V2MeasurementRecord.toTakeoffEntity(
    drawingId: Long,
    name: String,
    colorArgb: Long,
    encodePoints: (List<TakeoffPoint>) -> String,
    createdAt: Long = System.currentTimeMillis()
): TakeoffItemEntity {
    val points = points.map { TakeoffPoint(it.x.toDouble(), it.y.toDouble()) }
    return TakeoffItemEntity(
        drawingId = drawingId,
        page = page,
        tool = kind.toTakeoffToolName(),
        name = name,
        colorArgb = colorArgb,
        pointsJson = encodePoints(points),
        thickness = calibration.thicknessMetres.takeIf { kind == V2MeasurementKind.VOLUME },
        createdAt = createdAt
    )
}

private fun V2MeasurementKind.toTakeoffToolName(): String = when (this) {
    V2MeasurementKind.AREA -> "AREA"
    V2MeasurementKind.LENGTH -> "LENGTH"
    V2MeasurementKind.COUNT -> "COUNT"
    V2MeasurementKind.VOLUME -> "VOLUME"
}
