package com.corewall.qaqc.v2.pdf

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * حالة المنظر عالية التردد لـV2. لا تستخدم Compose State؛ يقرأها Surface
 * الأصلي ويرسمها مباشرة في كل إطار لمس.
 */
internal class WorkspaceViewport {
    var zoom = 1f
        private set
    var panX = 0f
        private set
    var panY = 0f
        private set
    var width = 0
        private set
    var height = 0
        private set
    var pageWidthPt = 0f
        private set
    var pageHeightPt = 0f
        private set

    fun updateViewport(widthPx: Int, heightPx: Int) {
        width = widthPx.coerceAtLeast(0)
        height = heightPx.coerceAtLeast(0)
        clamp()
    }

    fun setPageSize(widthPt: Float, heightPt: Float, fit: Boolean = false) {
        pageWidthPt = widthPt.coerceAtLeast(1f)
        pageHeightPt = heightPt.coerceAtLeast(1f)
        if (fit) fitWidth() else clamp()
    }

    fun fitWidth() {
        if (width == 0 || pageWidthPt <= 0f) return
        zoom = (width / pageWidthPt).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = (width - pageWidthPt * zoom) / 2f
        panY = 0f
        clamp()
    }

    fun zoomAbout(factor: Float, focusX: Float, focusY: Float) {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = next / zoom
        if (applied == 1f) return
        panX = focusX - (focusX - panX) * applied
        panY = focusY - (focusY - panY) * applied
        zoom = next
        clamp()
    }

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
        clamp()
    }

    fun visiblePageRect(): RectF {
        if (zoom <= 0f) return RectF()
        return RectF(
            max(0f, -panX / zoom),
            max(0f, -panY / zoom),
            min(pageWidthPt, (width - panX) / zoom),
            min(pageHeightPt, (height - panY) / zoom)
        )
    }

    /** يحوّل موضع اللمس مرة واحدة إلى إحداثيات وثيقة ثابتة ٠..١. */
    fun screenToDocument(x: Float, y: Float): V2DocumentPoint? {
        if (zoom <= 0f || pageWidthPt <= 0f || pageHeightPt <= 0f) return null
        val pageX = (x - panX) / zoom
        val pageY = (y - panY) / zoom
        if (pageX !in 0f..pageWidthPt || pageY !in 0f..pageHeightPt) return null
        return V2DocumentPoint(pageX / pageWidthPt, pageY / pageHeightPt)
    }

    private fun clamp() {
        if (width == 0 || height == 0 || pageWidthPt <= 0f || pageHeightPt <= 0f) return
        val contentW = pageWidthPt * zoom
        val contentH = pageHeightPt * zoom
        panX = if (contentW <= width) (width - contentW) / 2f else panX.coerceIn(width - contentW, 0f)
        panY = if (contentH <= height) (height - contentH) / 2f else panY.coerceIn(height - contentH, 0f)
    }

    private companion object {
        const val MIN_ZOOM = 0.12f
        /**
         * ٦٤× — نفس سقف العارض الكلاسيكي.
         *
         * كان ١٢×، وده كان أقل من نص سقف الرسم في المسار التاني، فالتقريب
         * كان بيقف فجأة وانت بترسم تفصيلة. السلّم بيرندر بوضوح كامل لحد
         * ٦٤ بكسل لكل نقطة، فالرقم ده هو آخر تقريب لسه حاد.
         */
        const val MAX_ZOOM = 64f
    }
}
