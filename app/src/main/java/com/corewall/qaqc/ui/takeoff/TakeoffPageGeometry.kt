package com.corewall.qaqc.ui.takeoff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffScaleEntity
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.SizePt
import com.corewall.qaqc.takeoff.PageGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * هندسة صفحات رسمة، للشاشات اللي مش فاتحة الرسمة نفسها.
 *
 * ## ليه لازم نفتح الـPDF
 *
 * النقط متخزّنة **منسّبة ٠..١** لصفحتها. عشان تطلع منها متر حقيقي لازم
 * تضربها في مقاس الصفحة **بالنقط** وبعدين في `metresPerPoint`. يعني
 * المقاس داخل في الحساب مباشرة — والمساحة بتتأثر بيه **تربيعيًا**.
 *
 * الشاشات دي كانت بتفترض A4 عشان ما تفتحش PDFium، والنتيجة إن رسمة على
 * ورق أكبر كانت بتدّي كميات أصغر بمعامل ثابت (A1 مقابل A4 = ٨ أضعاف في
 * المساحة)، فنفس البند بيطلع رقمين مختلفين في شاشتين. الافتراض ده كان
 * غلط من أصله؛ المقاس بيتقرا من الملف دلوقتي.
 *
 * الفتح هنا أرخص بكتير من العارض: بنقيس الصفحات اللي فيها بنود بس،
 * ومابنرندرش ولا مربّع واحد.
 *
 * لحد ما المقاس يوصل، المقاس بيرجع صفر — و[PageGeometry.calibrated]
 * بترجع `false` فالكمية بتبقى صفر. ده مقصود: رقم غلط بيتصدّق، والصفر
 * بيبان إنه لسه بيحمّل.
 */
@Composable
internal fun rememberDrawingPageGeometry(
    vm: MainViewModel,
    drawingId: Long,
    scaleRows: List<TakeoffScaleEntity>,
    pages: Set<Int>
): (Int) -> PageGeometry {
    val context = LocalContext.current
    var sizes by remember(drawingId) { mutableStateOf<Map<Int, SizePt>>(emptyMap()) }

    // المفتاح هو الصفحات المطلوبة نفسها: بند جديد على صفحة لسه مااتقاستش
    // بيعيد تشغيل القياس، وغير كده مابنفتحش الملف تاني.
    LaunchedEffect(drawingId, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        val path = vm.takeoff.drawingById(drawingId)?.filePath ?: return@LaunchedEffect
        val measured = withContext(Dispatchers.IO) {
            runCatching {
                PdfDocumentSession.open(context, File(path)).use { session ->
                    buildMap {
                        for (page in pages) {
                            if (page !in 0 until session.pageCount) continue
                            session.measure(page)
                            session.knownSize(page)?.let { put(page, it) }
                        }
                    }
                }
            }.getOrNull()
        }
        if (!measured.isNullOrEmpty()) sizes = measured
    }

    return remember(sizes, scaleRows) {
        { page: Int ->
            val size = sizes[page]
            val mpp = scaleRows.firstOrNull { it.page == page }?.metresPerPoint ?: 0.0
            PageGeometry(
                widthPt = size?.width?.toDouble() ?: 0.0,
                heightPt = size?.height?.toDouble() ?: 0.0,
                metresPerPoint = mpp
            )
        }
    }
}
