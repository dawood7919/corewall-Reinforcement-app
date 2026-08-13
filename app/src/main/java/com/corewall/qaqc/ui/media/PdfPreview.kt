package com.corewall.qaqc.ui.media

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** معاينة PDF عامة، منفصلة عن وحدة الملاحظات. */
@Composable
fun rememberPdfPreview(path: String, widthPx: Int = 300): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                PdfRenderer(ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    val page = renderer.openPage(0)
                    val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                    }
                }
            }.getOrNull()
        }
    }.value
