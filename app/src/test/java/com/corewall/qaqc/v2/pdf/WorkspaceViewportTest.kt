package com.corewall.qaqc.v2.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceViewportTest {
    @Test
    fun zoomKeepsDocumentCoordinateUnderGestureFocus() {
        val viewport = WorkspaceViewport()
        viewport.updateViewport(1_000, 1_500)
        viewport.setPageSize(1_000f, 2_000f, fit = true)
        viewport.panBy(-120f, -220f)

        val focusX = 430f
        val focusY = 680f
        val beforeX = (focusX - viewport.panX) / viewport.zoom
        val beforeY = (focusY - viewport.panY) / viewport.zoom

        viewport.zoomAbout(2f, focusX, focusY)

        assertEquals(beforeX, (focusX - viewport.panX) / viewport.zoom, 0.001f)
        assertEquals(beforeY, (focusY - viewport.panY) / viewport.zoom, 0.001f)
    }

    @Test
    fun screenPointConvertsToStableNormalizedDocumentPoint() {
        val viewport = WorkspaceViewport()
        viewport.updateViewport(1_000, 1_500)
        viewport.setPageSize(1_000f, 2_000f, fit = true)
        viewport.panBy(-120f, -220f)
        val screenX = 420f
        val screenY = 680f

        val before = viewport.screenToDocument(screenX, screenY)
        viewport.zoomAbout(2f, screenX, screenY)
        val after = viewport.screenToDocument(screenX, screenY)

        assertEquals(before?.x ?: 0f, after?.x ?: 0f, 0.0001f)
        assertEquals(before?.y ?: 0f, after?.y ?: 0f, 0.0001f)
    }
}
