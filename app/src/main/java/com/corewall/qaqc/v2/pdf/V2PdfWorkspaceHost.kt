package com.corewall.qaqc.v2.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.corewall.qaqc.pdfengine.PdfDocumentSession

/** بوابة Compose الوحيدة إلى سطح PDF V2؛ التحديث لا ينقل حالة الإيماءة إلى Compose. */
@Composable
internal fun V2PdfWorkspaceHost(
    session: PdfDocumentSession,
    page: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PdfWorkspaceView(context) },
        update = { view -> view.bind(session, page) },
        onRelease = { view -> view.release() }
    )
}
