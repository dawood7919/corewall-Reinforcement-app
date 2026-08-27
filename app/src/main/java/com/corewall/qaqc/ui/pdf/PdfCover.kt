package com.corewall.qaqc.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * غلاف الرسمة — أول صفحة كصورة مصغّرة.
 *
 * ## ليه كاش عالمي مش لكل شاشة
 *
 * نفس الرسمة بتظهر في شاشة الأقسام وشاشة الرسمات وشاشة البيانات. فتح
 * الـPDF ورسم صفحة عملية غالية نسبياً، وتكرارها في كل شاشة بيخلّي
 * التنقّل بيهتّه. الكاش هنا بالمسار، فالرجوع لشاشة سبق فتحها بيرسم فوراً.
 *
 * ## ليه سقف على العدد
 *
 * كل مصغّرة بتفضل في الذاكرة. قسم فيه ٥٠ رسمة يعني ٥٠ صورة عايشة على
 * طول لو مفيش حد. السقف بيرمي الأقدم — المستخدم بيبص على اللي قدامه،
 * وإعادة رسم واحدة رجع لها أرخص من إن التطبيق يقع بنفاد ذاكرة.
 */
object PdfCoverCache {

    private const val MAX_ENTRIES = 40

    private val covers = LinkedHashMap<String, ImageBitmap>()
    private val lock = Mutex()

    /**
     * بيرجّع الغلاف من الكاش أو بيرسمه.
     *
     * `Mutex` واحد لكل الطلبات عن قصد: الشبكة بتفتح صفوف من العناصر مع
     * بعض، وفتح عشر ملفات PDF في نفس اللحظة بيقفل الجهاز. الترتيب بيخلّي
     * الأغلفة تظهر واحدة ورا التانية بدل ما الشاشة كلها تتجمّد.
     */
    suspend fun cover(
        context: android.content.Context,
        path: String,
        maxPx: Int = 320
    ): ImageBitmap? {
        covers[path]?.let { return it }
        return lock.withLock {
            // ممكن حد تاني يكون رسمها وإحنا مستنيين على القفل.
            covers[path]?.let { return@withLock it }
            val image = render(context, path, maxPx)
            if (image != null) {
                covers[path] = image
                while (covers.size > MAX_ENTRIES) {
                    val oldest = covers.keys.firstOrNull() ?: break
                    covers.remove(oldest)
                }
            }
            image
        }
    }

    private suspend fun render(
        context: android.content.Context,
        path: String,
        maxPx: Int
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null
            // الفتح غالي، فبنقفله فوراً بعد صفحة واحدة: الغلاف صورة
            // ساكنة، مالوش لازمة يمسك الملف مفتوح زي العارض.
            PdfDocumentSession.open(context, file).use { session ->
                session.measure(0)
                val size = session.sizeOrEstimate(0)
                val scale = maxPx / maxOf(size.width, size.height)
                val w = (size.width * scale).toInt().coerceAtLeast(1)
                val h = (size.height * scale).toInt().coerceAtLeast(1)
                session.renderTile(0, w, h, 0, 0, w, h)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

/**
 * غلاف رسمة جاهز للعرض. `null` = لسه بيتحمّل، أو الملف مش موجود.
 *
 * بيرجّع `null` بدل ما يرمي: الملف ممكن يكون اتمسح من برّه التطبيق،
 * وشبكة مصغّرات مش المكان اللي نوقّف فيه الشاشة على خطأ.
 */
@Composable
fun rememberPdfCover(path: String?, maxPx: Int = 320): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (!path.isNullOrBlank()) image = PdfCoverCache.cover(context, path, maxPx)
    }
    return image
}
