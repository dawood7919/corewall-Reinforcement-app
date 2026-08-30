package com.corewall.qaqc.shots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.data.db.WirEntity
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.pdf.PdfTool
import com.corewall.qaqc.ui.pdf.PdfToolbar
import com.corewall.qaqc.ui.pdf.ToolStyle
import com.corewall.qaqc.ui.theme.CoreWallTheme
import com.corewall.qaqc.ui.wir.WirCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * لقطات للشاشات — **مش مقارنة، عرض**.
 *
 * السبب اللي خلّاها موجودة: مفيش محاكي في البيئة اللي بأشتغل فيها، فالحاجات
 * اللي بتتشاف بالعين — شريط مقصوص، نص طالع بره كارت، تباين واطي — مكانش
 * فيه طريقة أكتشفها غير إن المستخدم يبعت سكرين شوت.
 *
 * الصور بتتكتب في `app/build/screenshots` والـCI بيرفعها على فرع، فتتشاف
 * من غير محاكي ومن غير جهاز.
 *
 * مافيش مقارنة بصورة مرجعية عن قصد: الهدف إني أبص، مش إن البناء يقع لما
 * بكسل يتغيّر. أي تغيير في التصميم كان هيبقى "اختبار فاشل" وده بيخلّي
 * الناس تحدّث الصور المرجعية من غير ما تبصّ — عكس الغرض بالظبط.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rEG-w411dp-h891dp-xhdpi")
class UiScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    /**
     * الكارت بيعرض "الملف مش موجود" لو مسار الطلب مش على القرص، فالصورة
     * كانت هتوري الحالة الاستثنائية بدل العادية.
     */
    private val presentFile = File(outDir, "sample-wir.pdf").apply { writeText("%PDF-1.4") }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        // الساعة بتتقدّم بالإيد.
        //
        // `waitForIdle` بيستنى الساعة تهدى، وتحت Robolectric مابتهداش —
        // كل لقطة كانت بتقع بـ`ComposeTimeoutException`. مع إيقاف التقدّم
        // التلقائي بنقدّم كام إطار بنفسنا لحد ما حركات الدخول تخلص،
        // وبعدين نصوّر. وده أدق كمان: اللقطة بتتاخد عند لحظة معروفة مش
        // عند "أول ما يهدى".
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CoreWallTheme(AppTheme.IOS_LIGHT) {
                val c = LocalCwColors.current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.background)
                        .padding(Space.md)
                ) { content() }
            }
        }
        // حركات الدخول في التطبيق أقصاها ٢٨٠ms — نصف ثانية بتغطّيها.
        compose.mainClock.advanceTimeBy(500L)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** شريط الأدوات وقت الرسم — الأدوات بتتمرّر أفقياً ولوحة الخصائص مفتوحة. */
    @Test
    fun pdfToolbarDrawing() = shoot("pdf-toolbar-drawing") {
        PdfToolbar(
            tool = PdfTool.CLOUD,
            style = ToolStyle(),
            onTool = {},
            onStyle = {},
            canUndo = true,
            canRedo = false,
            onUndo = {},
            onRedo = {},
            onClear = {}
        )
    }

    /** نفس الشريط ومعاه شريط التحديد الجديد. */
    @Test
    fun pdfToolbarSelection() = shoot("pdf-toolbar-selection") {
        PdfToolbar(
            tool = PdfTool.SELECT,
            style = ToolStyle(),
            onTool = {},
            onStyle = {},
            canUndo = true,
            canRedo = true,
            onUndo = {},
            onRedo = {},
            onClear = {},
            selectedCount = 3,
            onDeleteSelected = {},
            onRestyleSelected = {}
        )
    }

    /** كارت طلب فحص باسم قصير. */
    @Test
    fun wirCardShort() = shoot("wir-card-short") {
        WirCard(
            wir = sampleWir("WIR-CW-12", pages = 4, status = "SUBMITTED"),
            onOpen = {}, onStatus = {}, onRename = {}, onShare = {}, onDelete = {},
            sources = {}
        )
    }

    /**
     * نفس الكارت باسم طويل. ده الشكل اللي بيكسر التخطيط عادةً — الاسم
     * بيزقّ الشارة وقايمة الخيارات بره الكارت.
     */
    @Test
    fun wirCardLongName() = shoot("wir-card-long-name") {
        Column {
            WirCard(
                wir = sampleWir(
                    "طلب فحص حديد تسليح القواطيع والحوائط الخرسانية — الدور الأرضي المحور B",
                    pages = 17,
                    status = "REJECTED"
                ),
                onOpen = {}, onStatus = {}, onRename = {}, onShare = {}, onDelete = {},
                sources = {}
            )
        }
    }

    private fun sampleWir(name: String, pages: Int, status: String) = WirEntity(
        id = 1,
        name = name,
        level = "GF",
        filePath = presentFile.absolutePath,
        pageCount = pages,
        status = status,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )
}
