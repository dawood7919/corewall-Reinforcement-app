package com.corewall.qaqc.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** بيانات ملف (اسم/حجم/امتداد) للكارت. */
data class FileMeta(
    val name: String,
    val sizeText: String,
    val ext: String,
    val pdfPages: Int? = null
)

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** صورة مصغّرة من مسار، منخفضة الدقة (خارج الـmain thread). */
@Composable
fun rememberThumb(path: String, targetPx: Int = 600): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sample > targetPx) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }.value

/** أبعاد صورة كنص "1920×1080". */
@Composable
fun rememberImageDim(path: String): String? =
    produceState<String?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, b)
                if (b.outWidth > 0) "${b.outWidth}×${b.outHeight}" else null
            }.getOrNull()
        }
    }.value

@Composable
fun rememberFileMeta(path: String): FileMeta {
    val f = File(path)
    return produceState(initialValue = FileMeta(f.name, "", f.extension.lowercase()), path) {
        value = withContext(Dispatchers.IO) {
            val pages = if (f.extension.equals("pdf", true)) {
                runCatching {
                    PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
                }.getOrNull()
            } else null
            FileMeta(f.name, sizeText(f.length()), f.extension.lowercase(), pages)
        }
    }.value
}

/** أول صفحة من PDF كصورة مصغّرة للكارت. */
@Composable
fun rememberPdfThumb(path: String, widthPx: Int = 300): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                    val page = r.openPage(0)
                    val h = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
            }.getOrNull()
        }
    }.value
