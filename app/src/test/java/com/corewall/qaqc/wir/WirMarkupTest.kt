package com.corewall.qaqc.wir

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.corewall.qaqc.pdfengine.PdfOps
import com.corewall.qaqc.pdfengine.WirPdf
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * الصفحة اللي بتتبعت لطلب فحص لازم توصل **ومعاها تأشيرها**.
 *
 * دي أول حاجة المستخدم لاحظها، ومرة كمان بعد "إصلاح" كان بينسخ صفوف في
 * قاعدة البيانات بدل ما يكتب في الملف. الفرق مالوش أثر في التطبيق — بيبان
 * بس لما الملف يتبعت لحد تاني. فالاختبار بيفتح الملف الناتج ويسأله هو
 * نفسه، مش بيسأل القاعدة.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WirMarkupTest {

    private lateinit var dir: File
    private lateinit var source: File

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        PdfOps.ensureInit(context)
        dir = File(context.cacheDir, "wir-test").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        source = File(dir, "drawing.pdf")
        PDDocument().use { doc ->
            repeat(3) { doc.addPage(PDPage(PDRectangle.A4)) }
            doc.save(source)
        }
    }

    private fun mark(page: Int, tool: String) = PdfAnnotationEntity(
        filePath = source.absolutePath,
        page = page,
        tool = tool,
        color = 0xFFFF3B30,
        pointsJson = "[0.2,0.2,0.6,0.6]",
        createdAt = 0L,
        strokeWidth = 2.5f,
        opacity = 1f
    )

    private val points = listOf(0.2f, 0.2f, 0.6f, 0.6f)

    @Test
    fun `markup is written into the sent page, not left behind`() = runBlocking {
        val dest = File(dir, "WIR-1.pdf")
        val pages = WirPdf.appendPage(
            source = source, page = 1, dest = dest, workDir = dir,
            markup = listOf(mark(1, PdfAnnotationEntity.TOOL_RECT)),
            pointsOf = { points }
        ).getOrThrow()

        assertEquals(1, pages)
        PDDocument.load(dest).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(
                "الصفحة وصلت ورق نضيف — التأشير مش جوّه الملف",
                doc.getPage(0).annotations.isNotEmpty()
            )
        }
    }

    @Test
    fun `a page with no markup still travels`() = runBlocking {
        val dest = File(dir, "WIR-2.pdf")
        val pages = WirPdf.appendPage(
            source = source, page = 0, dest = dest, workDir = dir
        ).getOrThrow()
        assertEquals(1, pages)
        assertTrue(dest.length() > 400L)
    }

    /**
     * الإضافة في الآخر مش تفصيلة: التعليقات مربوطة برقم الصفحة، فأي إدراج
     * في النص بيزحلق كل تأشير اتعمل قبل كده لصفحة تانية.
     */
    @Test
    fun `a second page lands at the end and keeps the first one intact`() = runBlocking {
        val dest = File(dir, "WIR-3.pdf")
        WirPdf.appendPage(
            source = source, page = 0, dest = dest, workDir = dir,
            markup = listOf(mark(0, PdfAnnotationEntity.TOOL_RECT)), pointsOf = { points }
        ).getOrThrow()
        val pages = WirPdf.appendPage(
            source = source, page = 2, dest = dest, workDir = dir,
            markup = listOf(mark(2, PdfAnnotationEntity.TOOL_CLOUD)), pointsOf = { points }
        ).getOrThrow()

        assertEquals(2, pages)
        PDDocument.load(dest).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertTrue("الصفحة الأولى فقدت تأشيرها", doc.getPage(0).annotations.isNotEmpty())
            assertTrue("الصفحة الجديدة وصلت من غير تأشير", doc.getPage(1).annotations.isNotEmpty())
        }
    }

    @Test
    fun `page count reported matches the file`() = runBlocking {
        val dest = File(dir, "WIR-4.pdf")
        WirPdf.appendPage(source = source, page = 0, dest = dest, workDir = dir).getOrThrow()
        val reported = WirPdf.appendPage(source = source, page = 1, dest = dest, workDir = dir)
            .getOrThrow()
        assertEquals(WirPdf.pageCount(dest), reported)
    }
}
