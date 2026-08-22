package com.corewall.qaqc.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberImagePreview(path: String, targetPx: Int = 1200): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path, targetPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > targetPx) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }.value
