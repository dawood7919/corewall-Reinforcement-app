package com.corewall.qaqc.v2.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.corewall.qaqc.pdfengine.PdfDocumentSession

/** بوابة Compose الوحيدة إلى سطح PDF V2؛ التحديث لا ينقل حالة الإيماءة إلى Compose. */
@Composable
internal fun V2PdfWorkspaceHost(
    session: PdfDocumentSession,
    page: Int,
    modifier: Modifier = Modifier,
    controller: V2WorkspaceController = remember { V2WorkspaceController() },
    persistedItems: List<V2PersistedTakeoffItem> = emptyList(),
    persistedInk: List<V2PersistedInkStroke> = emptyList(),
    activeMeasurementColorArgb: Long = 0xFF37D89B,
    commitCountsImmediately: Boolean = true,
    inkStyle: V2InkStyle = V2InkStyle(),
    onInkCommitted: ((V2InkStroke) -> Unit)? = null,
    onPersistedInkErased: ((Long) -> Unit)? = null
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PdfWorkspaceView(context).also {
                controller.attach(it)
                it.setPersistedItems(persistedItems)
                it.setPersistedInk(persistedInk)
                it.setMeasurementColor(activeMeasurementColorArgb)
                it.setCountCommitImmediately(commitCountsImmediately)
                it.setInkStyle(inkStyle)
                it.setOnInkCommitted(onInkCommitted)
                it.setOnPersistedInkErased(onPersistedInkErased)
            }
        },
        update = { view ->
            controller.attach(view)
            view.bind(session, page)
            view.setPersistedItems(persistedItems)
            view.setPersistedInk(persistedInk)
            view.setMeasurementColor(activeMeasurementColorArgb)
            view.setCountCommitImmediately(commitCountsImmediately)
            view.setInkStyle(inkStyle)
            view.setOnInkCommitted(onInkCommitted)
            view.setOnPersistedInkErased(onPersistedInkErased)
        },
        onRelease = { view ->
            controller.detach(view)
            view.release()
        }
    )
}
