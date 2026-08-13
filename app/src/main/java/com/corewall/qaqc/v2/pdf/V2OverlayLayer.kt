package com.corewall.qaqc.v2.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale
import kotlin.math.max

/** طبقة Canvas مستقلة فوق PDF: لا تطلب رندر مستند ولا تنشئ Bitmap. */
internal class V2OverlayLayer(private val state: V2MeasurementLayer) {
    private val path = Path()
    private val measurementStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val measurementFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF6CFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F5F7.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val labelRect = RectF()

    fun draw(canvas: Canvas, page: Int, pageWidthPt: Float, pageHeightPt: Float, zoom: Float) {
        state.measurements.forEach { record ->
            if (record.page == page) drawMeasurement(canvas, record, pageWidthPt, pageHeightPt, zoom, preview = false)
        }
        state.draft?.takeIf { it.page == page }?.let { draft ->
            drawDraft(canvas, draft, pageWidthPt, pageHeightPt, zoom)
        }
        state.inkStrokes.forEach { stroke ->
            if (stroke.page == page) drawInk(canvas, stroke.points, stroke.widthPx, pageWidthPt, pageHeightPt, zoom)
        }
        state.liveInk?.takeIf { it.page == page }?.let { stroke ->
            drawInk(canvas, stroke.points, stroke.widthPx, pageWidthPt, pageHeightPt, zoom)
        }
    }

    private fun drawMeasurement(
        canvas: Canvas,
        record: V2MeasurementRecord,
        pageWidthPt: Float,
        pageHeightPt: Float,
        zoom: Float,
        preview: Boolean
    ) {
        val color = colorFor(record.kind)
        drawShape(canvas, record.kind, record.points, pageWidthPt, pageHeightPt, zoom, color, preview)
        if (!preview) {
            val value = V2MeasurementMath.quantity(record, pageWidthPt, pageHeightPt)
            drawLabel(canvas, record.kind, record.points, value, pageWidthPt, pageHeightPt, zoom)
        }
    }

    private fun drawDraft(
        canvas: Canvas,
        draft: V2MeasurementLayer.V2MeasurementDraft,
        pageWidthPt: Float,
        pageHeightPt: Float,
        zoom: Float
    ) {
        drawShape(canvas, draft.kind, draft.points, pageWidthPt, pageHeightPt, zoom, colorFor(draft.kind), preview = true)
    }

    private fun drawShape(
        canvas: Canvas,
        kind: V2MeasurementKind,
        points: List<V2DocumentPoint>,
        pageWidthPt: Float,
        pageHeightPt: Float,
        zoom: Float,
        color: Int,
        preview: Boolean
    ) {
        if (points.isEmpty()) return
        val lineWidth = (2.6f / zoom.coerceAtLeast(0.1f)).coerceIn(0.5f, 18f)
        measurementStroke.color = color
        measurementStroke.strokeWidth = lineWidth
        measurementStroke.alpha = if (preview) 170 else 255
        measurementFill.color = color
        measurementFill.alpha = if (preview) 35 else 52

        if (kind == V2MeasurementKind.COUNT) {
            points.forEach { point -> drawCountMarker(canvas, point, pageWidthPt, pageHeightPt, zoom, color) }
            return
        }

        trace(points, pageWidthPt, pageHeightPt, close = kind.closesPath && points.size >= kind.minimumPoints)
        if (kind.closesPath && points.size >= 3) canvas.drawPath(path, measurementFill)
        canvas.drawPath(path, measurementStroke)
        points.forEach { point ->
            canvas.drawCircle(point.x * pageWidthPt, point.y * pageHeightPt, 3.6f / zoom.coerceAtLeast(0.1f), measurementStroke)
        }
    }

    private fun drawCountMarker(
        canvas: Canvas, point: V2DocumentPoint, pageWidthPt: Float, pageHeightPt: Float, zoom: Float, color: Int
    ) {
        val x = point.x * pageWidthPt
        val y = point.y * pageHeightPt
        val half = 8f / zoom.coerceAtLeast(0.1f)
        measurementStroke.color = color
        measurementStroke.alpha = 255
        measurementStroke.strokeWidth = 2.4f / zoom.coerceAtLeast(0.1f)
        canvas.drawLine(x - half, y, x + half, y, measurementStroke)
        canvas.drawLine(x, y - half, x, y + half, measurementStroke)
    }

    private fun drawInk(
        canvas: Canvas,
        points: List<V2DocumentPoint>,
        widthPx: Float,
        pageWidthPt: Float,
        pageHeightPt: Float,
        zoom: Float
    ) {
        if (points.size < 2) return
        inkPaint.strokeWidth = widthPx / zoom.coerceAtLeast(0.1f)
        trace(points, pageWidthPt, pageHeightPt, close = false)
        canvas.drawPath(path, inkPaint)
    }

    private fun drawLabel(
        canvas: Canvas,
        kind: V2MeasurementKind,
        points: List<V2DocumentPoint>,
        value: Double?,
        pageWidthPt: Float,
        pageHeightPt: Float,
        zoom: Float
    ) {
        if (points.isEmpty()) return
        val x = points.sumOf { it.x.toDouble() }.toFloat() / points.size * pageWidthPt
        val y = points.sumOf { it.y.toDouble() }.toFloat() / points.size * pageHeightPt
        val text = value?.let { String.format(Locale.US, "%.2f %s", it, kind.unit) } ?: "عاير المقياس"
        labelText.textSize = 12f / zoom.coerceAtLeast(0.1f)
        val horizontalPadding = 7f / zoom.coerceAtLeast(0.1f)
        val verticalPadding = 4f / zoom.coerceAtLeast(0.1f)
        val width = max(labelText.measureText(text) + horizontalPadding * 2, 36f / zoom.coerceAtLeast(0.1f))
        val height = labelText.textSize + verticalPadding * 2
        labelRect.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
        labelPaint.color = 0xE8171B20.toInt()
        canvas.drawRoundRect(labelRect, 5f / zoom.coerceAtLeast(0.1f), 5f / zoom.coerceAtLeast(0.1f), labelPaint)
        val baseline = y - (labelText.ascent() + labelText.descent()) / 2
        canvas.drawText(text, x, baseline, labelText)
    }

    private fun trace(points: List<V2DocumentPoint>, pageWidthPt: Float, pageHeightPt: Float, close: Boolean) {
        path.reset()
        val first = points.first()
        path.moveTo(first.x * pageWidthPt, first.y * pageHeightPt)
        points.drop(1).forEach { point -> path.lineTo(point.x * pageWidthPt, point.y * pageHeightPt) }
        if (close) path.close()
    }

    private fun colorFor(kind: V2MeasurementKind): Int = when (kind) {
        V2MeasurementKind.AREA -> 0xFF37D89B.toInt()
        V2MeasurementKind.LENGTH -> 0xFF4FC3F7.toInt()
        V2MeasurementKind.COUNT -> 0xFFFFC857.toInt()
        V2MeasurementKind.VOLUME -> 0xFFB487FF.toInt()
    }
}
