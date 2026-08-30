package com.corewall.qaqc

import com.corewall.qaqc.pdfengine.RevisionMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * قواعد بناء الست النهائي.
 *
 * القاعدة الوحيدة اللي كل الباقي مبني عليها: **أحدث ملف فيه اللوحة هو
 * اللي بيكسب**. غلطة هنا بتطلّع ملف يبان سليم وفيه لوحة قديمة — أسوأ
 * نوع خطأ في الرسومات، لأن محدش بيلاحظه.
 */
class RevisionMergeLogicTest {

    private fun scan(name: String, vararg drawings: Pair<String, Int>) =
        RevisionMerge.FileScan(
            file = File(name),
            pageCount = drawings.size + 1,
            covers = listOf(0),
            drawings = drawings.toMap(),
            warnings = emptyList()
        )

    @Test
    fun `files order by the trailing number when every file has one`() {
        val ordered = RevisionMerge.autoOrder(
            listOf(File("pkg-02.pdf"), File("pkg-00.pdf"), File("pkg-01.pdf"))
        )
        assertEquals(listOf("pkg-00.pdf", "pkg-01.pdf", "pkg-02.pdf"), ordered.map { it.name })
    }

    /** تخمين نص الترتيب أسوأ من عدم التخمين. */
    @Test
    fun `order is left alone when one file has no number`() {
        val input = listOf(File("pkg-02.pdf"), File("revised.pdf"), File("pkg-00.pdf"))
        assertEquals(input.map { it.name }, RevisionMerge.autoOrder(input).map { it.name })
    }

    @Test
    fun `the newest file holding a drawing wins`() {
        val plan = RevisionMerge.plan(
            listOf(
                scan("rev00.pdf", "20001" to 1, "20002" to 2, "20003" to 3),
                scan("rev01.pdf", "20002" to 1),
                scan("rev02.pdf", "20003" to 1)
            ),
            RevisionMerge.Order.NUMBER
        )
        assertEquals(listOf("20001", "20002", "20003"), plan.numbers)
        assertEquals(0, plan.chosen["20001"])
        assertEquals(1, plan.chosen["20002"])
        assertEquals(2, plan.chosen["20003"])
    }

    @Test
    fun `a drawing that only appears in a later file is still included`() {
        val plan = RevisionMerge.plan(
            listOf(scan("rev00.pdf", "20001" to 1), scan("rev01.pdf", "20009" to 1)),
            RevisionMerge.Order.NUMBER
        )
        assertEquals(listOf("20001", "20009"), plan.numbers)
    }

    @Test
    fun `file order keeps the oldest file's sequence and appends the rest`() {
        val plan = RevisionMerge.plan(
            listOf(
                scan("rev00.pdf", "20003" to 1, "20001" to 2),
                scan("rev01.pdf", "20002" to 1)
            ),
            RevisionMerge.Order.FILE
        )
        assertEquals(listOf("20003", "20001", "20002"), plan.numbers)
    }

    @Test
    fun `counts per file add up to the sheet count`() {
        val plan = RevisionMerge.plan(
            listOf(
                scan("rev00.pdf", "1" to 1, "2" to 2, "3" to 3),
                scan("rev01.pdf", "2" to 1)
            ),
            RevisionMerge.Order.NUMBER
        )
        assertEquals(2, plan.countFrom(0))
        assertEquals(1, plan.countFrom(1))
        assertEquals(plan.numbers.size, plan.countFrom(0) + plan.countFrom(1))
    }

    @Test
    fun `a bad pattern is reported, a good one is not`() {
        assertNull(RevisionMerge.validatePattern(RevisionMerge.DEFAULT_PATTERN))
        assertNotNull(RevisionMerge.validatePattern("RFT-(\\d{5}"))
    }

    @Test
    fun `the suggested name drops the revision suffix`() {
        assertEquals("BHR-SHD-ST-00345-FINAL.pdf", RevisionMerge.suggestedName(File("BHR-SHD-ST-00345-02.pdf")))
        assertEquals("package-FINAL.pdf", RevisionMerge.suggestedName(File("package.pdf")))
    }
}
