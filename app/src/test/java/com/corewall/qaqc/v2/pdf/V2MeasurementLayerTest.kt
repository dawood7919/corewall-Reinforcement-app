package com.corewall.qaqc.v2.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2MeasurementLayerTest {
    @Test
    fun incompleteFinishKeepsTheDraftInsteadOfDiscardingIt() {
        val layer = V2MeasurementLayer()
        layer.selectTool(V2WorkspaceTool.LENGTH)
        layer.onStylusDown(V2DocumentPoint(0.20f, 0.25f), page = 0, pressure = 0.5f, isEraser = false)

        val result = layer.finishMeasurement()

        assertTrue(result is V2MeasurementFinishResult.Incomplete)
        assertEquals(1, layer.draft?.points?.size)
        assertTrue(layer.measurements.isEmpty())
    }

    @Test
    fun finishCommitsValidLengthAndClearsOnlyTheDraft() {
        val layer = V2MeasurementLayer()
        layer.selectTool(V2WorkspaceTool.LENGTH)
        layer.onStylusDown(V2DocumentPoint(0.10f, 0.10f), page = 4, pressure = 0.5f, isEraser = false)
        layer.onStylusDown(V2DocumentPoint(0.60f, 0.10f), page = 4, pressure = 0.5f, isEraser = false)

        val result = layer.finishMeasurement()

        assertTrue(result is V2MeasurementFinishResult.Saved)
        assertEquals(1, layer.measurements.size)
        assertEquals(4, layer.measurements.single().page)
        assertEquals(V2MeasurementKind.LENGTH, layer.measurements.single().kind)
        assertEquals(null, layer.draft)
    }

    @Test
    fun countCommitsImmediatelyWithoutFinish() {
        val layer = V2MeasurementLayer()
        layer.selectTool(V2WorkspaceTool.COUNT)

        layer.onStylusDown(V2DocumentPoint(0.42f, 0.35f), page = 1, pressure = 0.5f, isEraser = false)

        assertEquals(1, layer.measurements.size)
        assertEquals(V2MeasurementKind.COUNT, layer.measurements.single().kind)
        assertTrue(layer.draft == null)
    }

    @Test
    fun inkPreservesStyleAndSupportsUndoWithoutMeasurementState() {
        val layer = V2MeasurementLayer()
        layer.selectTool(V2WorkspaceTool.INK)
        layer.setInkStyle(V2InkStyle(colorArgb = 0xFFD32F2FL, baseWidthPx = 8f))

        layer.onStylusDown(V2DocumentPoint(0.10f, 0.10f), page = 2, pressure = 0.25f, isEraser = false)
        layer.onStylusMove(V2DocumentPoint(0.18f, 0.16f), page = 2, pressure = 0.90f)
        val saved = layer.onStylusUp(V2DocumentPoint(0.30f, 0.20f), page = 2, pressure = 0.60f)

        assertEquals(0xFFD32F2FL, saved?.colorArgb)
        assertEquals(2, saved?.page)
        assertTrue((saved?.widthPx ?: 0f) in 1.2f..16f)
        assertEquals(saved, layer.undoLastInk())
        assertTrue(layer.inkStrokes.isEmpty())
    }

    @Test
    fun geometricQuantityUsesDocumentPointsAndNeverNeedsViewportZoom() {
        val record = V2MeasurementRecord(
            id = 1,
            page = 0,
            kind = V2MeasurementKind.AREA,
            points = listOf(
                V2DocumentPoint(0f, 0f),
                V2DocumentPoint(0.5f, 0f),
                V2DocumentPoint(0.5f, 0.5f),
                V2DocumentPoint(0f, 0.5f)
            ),
            calibration = V2PageCalibration(metresPerPoint = 0.01)
        )

        val quantity = V2MeasurementMath.quantity(record, pageWidthPt = 1_000f, pageHeightPt = 2_000f)

        assertEquals(50.0, quantity ?: 0.0, 0.0001)
    }

    @Test
    fun volumeNeedsThicknessWhileAreaDoesNot() {
        val base = listOf(
            V2DocumentPoint(0f, 0f),
            V2DocumentPoint(1f, 0f),
            V2DocumentPoint(1f, 1f),
            V2DocumentPoint(0f, 1f)
        )
        val withoutThickness = V2MeasurementRecord(
            id = 1,
            page = 0,
            kind = V2MeasurementKind.VOLUME,
            points = base,
            calibration = V2PageCalibration(metresPerPoint = 0.01)
        )
        val withThickness = withoutThickness.copy(
            calibration = V2PageCalibration(metresPerPoint = 0.01, thicknessMetres = 0.20)
        )

        assertEquals(null, V2MeasurementMath.quantity(withoutThickness, 1_000f, 1_000f))
        assertEquals(20.0, V2MeasurementMath.quantity(withThickness, 1_000f, 1_000f) ?: 0.0, 0.0001)
        assertFalse(withThickness.calibration.hasThickness.not())
    }
}
