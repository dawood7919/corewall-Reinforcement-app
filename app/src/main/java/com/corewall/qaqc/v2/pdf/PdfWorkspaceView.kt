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
    private val gesture = GestureDetector(context, GestureListener())
    private val scaler = ScaleGestureDetector(context, ScaleListener())

    private var session: PdfDocumentSession? = null
    private var scheduler: V2TileScheduler? = null
    private var pageIndex = 0
    private var pageSize = SizePt.A4

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

        val sharpLevel = levelFor(viewport.zoom)
        drawLayer(canvas, current, (sharpLevel - 1).coerceAtLeast(0))
        drawLayer(canvas, current, sharpLevel)
        canvas.drawRect(0f, 0f, pageSize.width, pageSize.height, borderPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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

    private fun drawLayer(canvas: Canvas, tiles: V2TileScheduler, level: Int) {
        val scale = scaleFor(level)
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

    private fun levelFor(zoom: Float): Int = when {
        zoom < 0.75f -> 0
        zoom < 1.5f -> 1
        zoom < 3f -> 2
        else -> 3
    }

    private fun scaleFor(level: Int): Float = when (level.coerceIn(0, 3)) {
        0 -> 0.5f
        1 -> 1f
        2 -> 2f
        else -> 4f
    }

    private fun memoryBudget(context: Context): Long {
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val quarterHeap = (memory?.memoryClass ?: 128).toLong() * 1024 * 1024 / 4
        return quarterHeap.coerceIn(32L * 1024 * 1024, 160L * 1024 * 1024)
    }

    private companion object {
        const val TILE_PX = 384f
        const val BACKGROUND = 0xFF101317.toInt()
    }
}
