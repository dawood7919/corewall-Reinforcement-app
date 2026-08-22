package com.corewall.qaqc.v2.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.SizePt
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * سطح V2 للرسم. لا يقرأ Compose State ولا ينشئ Bitmap في onDraw أو onTouch.
 * كل ما يحدث أثناء الإيماءة هو تعديل Matrix خفيف وطلب منطقة مرئية من المجدول.
 */
internal class PdfWorkspaceView(context: Context) : View(context) {
    private val viewport = WorkspaceViewport()
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val measurements = V2MeasurementLayer()
    private val overlay = V2OverlayLayer(measurements)
    private val gesture = GestureDetector(context, GestureListener())
    private val scaler = ScaleGestureDetector(context, ScaleListener())
    private val stylus = V2StylusInput(
        enabled = { measurements.capturesStylus },
        onDown = { x, y, pressure, eraser -> dispatchStylusDown(x, y, pressure, eraser) },
        onMove = { x, y, pressure -> dispatchStylusMove(x, y, pressure) },
        onUp = { x, y, pressure -> dispatchStylusUp(x, y, pressure) },
        onCancel = { measurements.cancelStylusStroke(); invalidate() }
    )

    private var session: PdfDocumentSession? = null
    private var scheduler: V2TileScheduler? = null
    private var pageIndex = 0
    private var pageSize = SizePt.A4
    private var persistedItems: List<V2PersistedTakeoffItem> = emptyList()
    private var persistedInk: List<V2PersistedInkStroke> = emptyList()
    private var onInkCommitted: ((V2InkStroke) -> Unit)? = null
    private var onPersistedInkErased: ((Long) -> Unit)? = null

    /** يستدعى مرة واحدة بعد فتح جلسة، ولا يملك إغلاق الجلسة نفسها. */
    fun bind(document: PdfDocumentSession, page: Int) {
        val requestedPage = page.coerceIn(0, document.pageCount - 1)
        if (session === document && pageIndex == requestedPage && scheduler != null) return
        scheduler?.clear()
        session = document
        pageIndex = requestedPage
        pageSize = document.sizeOrEstimate(pageIndex)
        viewport.setPageSize(pageSize.width, pageSize.height, fit = true)
        scheduler = V2TileScheduler(
            session = document,
            memoryBudgetBytes = memoryBudget(context),
            onTileChanged = ::invalidate
        )
        schedule()

        // المقاس الحقيقي قد يصل بعد المعاينة، ولا نعيد فتح المستند أو ننشئ
        // شجرة واجهة. فقط نضبط الصفحة ونطلب البلاطات للمنظر الحالي.
        document.scope.launch {
            document.measure(pageIndex)
            post {
                if (session !== document) return@post
                pageSize = document.sizeOrEstimate(pageIndex)
                viewport.setPageSize(pageSize.width, pageSize.height)
                schedule()
                invalidate()
            }
        }
    }

    fun release() {
        scheduler?.clear()
        scheduler = null
        session = null
    }

    fun selectWorkspaceTool(tool: V2WorkspaceTool) {
        measurements.selectTool(tool)
        invalidate()
    }

    fun setMeasurementCalibration(calibration: V2PageCalibration) {
        measurements.setCalibration(calibration)
        invalidate()
    }

    fun setCountCommitImmediately(value: Boolean) {
        measurements.setCommitCountsImmediately(value)
    }

    fun setMeasurementColor(colorArgb: Long) {
        overlay.setActiveMeasurementColor(colorArgb)
        invalidate()
    }

    fun setPersistedItems(items: List<V2PersistedTakeoffItem>) {
        persistedItems = items
        invalidate()
    }

    fun setPersistedInk(strokes: List<V2PersistedInkStroke>) {
        persistedInk = strokes
        invalidate()
    }

    fun setOnInkCommitted(listener: ((V2InkStroke) -> Unit)?) {
        onInkCommitted = listener
    }

    fun setOnPersistedInkErased(listener: ((Long) -> Unit)?) {
        onPersistedInkErased = listener
    }

    fun setInkStyle(style: V2InkStyle) {
        measurements.setInkStyle(style)
    }

    fun acknowledgeMeasurementPersisted(id: Long) {
        measurements.discardCompletedMeasurement(id)
        invalidate()
    }

    fun acknowledgeInkPersisted(id: Long) {
        measurements.discardCompletedInk(id)
        invalidate()
    }

    fun undoLastInk(): V2InkUndoResult? {
        measurements.undoLastInk()?.let { return V2InkUndoResult.Local(it) }
        return persistedInk.lastOrNull()?.let { V2InkUndoResult.Persisted(it.annotationId) }
    }

    fun zoomBy(factor: Float) {
        viewport.zoomAbout(factor, viewport.width / 2f, viewport.height / 2f)
        schedule()
        invalidate()
    }

    fun fitPage() {
        viewport.fitWidth()
        schedule()
        invalidate()
    }

    fun finishMeasurement(): V2MeasurementFinishResult {
        val result = measurements.finishMeasurement()
        invalidate()
        return result
    }

    fun undoMeasurementPoint(): Boolean {
        val undone = measurements.undoLastDraftPoint()
        if (undone) invalidate()
        return undone
    }

    fun cancelMeasurement() {
        measurements.cancelDraft()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewport.updateViewport(w, h)
        schedule()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BACKGROUND)
        val current = scheduler ?: return
        canvas.save()
        canvas.translate(viewport.panX, viewport.panY)
        canvas.scale(viewport.zoom, viewport.zoom)
        canvas.drawRect(0f, 0f, pageSize.width, pageSize.height, pagePaint)

        val sharpLevel = V2ZoomLadder.levelFor(viewport.zoom)
        drawLayer(canvas, current, (sharpLevel - 1).coerceAtLeast(0))
        drawLayer(canvas, current, sharpLevel)
        overlay.drawPersisted(canvas, persistedItems, pageIndex, pageSize.width, pageSize.height, viewport.zoom)
        overlay.drawPersistedInk(canvas, persistedInk, pageIndex, pageSize.width, pageSize.height, viewport.zoom)
        overlay.draw(canvas, pageIndex, pageSize.width, pageSize.height, viewport.zoom)
        canvas.drawRect(0f, 0f, pageSize.width, pageSize.height, borderPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stylus.handle(event)) return true
        scaler.onTouchEvent(event)
        gesture.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            schedule()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun schedule() {
        scheduler?.requestVisible(viewport, pageIndex, pageSize)
    }

    private fun dispatchStylusDown(x: Float, y: Float, pressure: Float, eraser: Boolean) {
        viewport.screenToDocument(x, y)?.let { point ->
            if (eraser && erasePersistedInk(point, pageIndex) != null) {
                invalidate()
                return@let
            }
            measurements.onStylusDown(point, pageIndex, pressure, eraser)
            invalidate()
        }
    }

    private fun dispatchStylusMove(x: Float, y: Float, pressure: Float) {
        viewport.screenToDocument(x, y)?.let { point ->
            measurements.onStylusMove(point, pageIndex, pressure)
            invalidate()
        }
    }

    private fun dispatchStylusUp(x: Float, y: Float, pressure: Float) {
        measurements.onStylusUp(viewport.screenToDocument(x, y), pageIndex, pressure)
            ?.let { onInkCommitted?.invoke(it) }
        invalidate()
    }

    private fun erasePersistedInk(point: V2DocumentPoint, page: Int): Long? {
        val candidate = persistedInk
            .asSequence()
            .filter { it.visible && it.page == page }
            .map { stroke -> stroke to (stroke.points.minOfOrNull { distance(it, point) } ?: Double.MAX_VALUE) }
            .minByOrNull { it.second }
            ?: return null
        if (candidate.second > PERSISTED_INK_ERASE_RADIUS) return null
        onPersistedInkErased?.invoke(candidate.first.annotationId)
        return candidate.first.annotationId
    }

    private fun distance(a: V2DocumentPoint, b: V2DocumentPoint): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.hypot(dx, dy)
    }

    private fun drawLayer(canvas: Canvas, tiles: V2TileScheduler, level: Int) {
        val scale = V2ZoomLadder.scaleFor(level)
        val step = TILE_PX / scale
        val visible = viewport.visiblePageRect()
        val maxCol = ((ceil(pageSize.width * scale).toInt() - 1) / TILE_PX.toInt()).coerceAtLeast(0)
        val maxRow = ((ceil(pageSize.height * scale).toInt() - 1) / TILE_PX.toInt()).coerceAtLeast(0)
        val fromCol = floor(visible.left / step).toInt().coerceIn(0, maxCol)
        val toCol = floor(visible.right / step).toInt().coerceIn(0, maxCol)
        val fromRow = floor(visible.top / step).toInt().coerceIn(0, maxRow)
        val toRow = floor(visible.bottom / step).toInt().coerceIn(0, maxRow)
        for (row in fromRow..toRow) for (column in fromCol..toCol) {
            val bitmap = tiles.bitmap(V2TileKey(pageIndex, level, column, row)) ?: continue
            val left = column * step
            val top = row * step
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left, top, min(left + step, pageSize.width), min(top + step, pageSize.height)),
                null
            )
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoomAbout(detector.scaleFactor, detector.focusX, detector.focusY)
            schedule()
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onScroll(
            down: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            if (!scaler.isInProgress) {
                viewport.panBy(-distanceX, -distanceY)
                schedule()
                invalidate()
            }
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            viewport.zoomAbout(1.75f, event.x, event.y)
            schedule()
            invalidate()
            return true
        }
    }

    private fun memoryBudget(context: Context): Long {
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val quarterHeap = (memory?.memoryClass ?: 128).toLong() * 1024 * 1024 / 4
        return quarterHeap.coerceIn(32L * 1024 * 1024, 160L * 1024 * 1024)
    }

    private companion object {
        const val TILE_PX = 384f
        const val BACKGROUND = 0xFF101317.toInt()
        const val PERSISTED_INK_ERASE_RADIUS = 0.020
    }
}
