package com.corewall.qaqc.v2.pdf

import com.corewall.qaqc.takeoff.TakeoffPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class V2TakeoffPersistenceTest {
    @Test
    fun volumeMeasurementMapsToExistingRoomShapeWithoutSchemaChange() {
        val record = V2MeasurementRecord(
            id = 10,
            page = 3,
            kind = V2MeasurementKind.VOLUME,
            points = listOf(V2DocumentPoint(0.1f, 0.2f), V2DocumentPoint(0.8f, 0.2f), V2DocumentPoint(0.8f, 0.9f)),
            calibration = V2PageCalibration(metresPerPoint = 0.001, thicknessMetres = 0.25)
        )
        var encoded = emptyList<TakeoffPoint>()

        val entity = record.toTakeoffEntity(
            drawingId = 44,
            name = "خرسانة قاعدة",
            colorArgb = 0xFFB487FF,
            encodePoints = { points -> encoded = points; "encoded" },
            createdAt = 500
        )

        assertEquals(44, entity.drawingId)
        assertEquals(3, entity.page)
        assertEquals("VOLUME", entity.tool)
        assertEquals(0.25, entity.thickness ?: 0.0, 0.0)
        assertEquals("encoded", entity.pointsJson)
        assertEquals(0.1, encoded.first().x, 0.00001)
        assertEquals(500, entity.createdAt)
    }
}
