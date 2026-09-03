package com.corewall.qaqc

import com.corewall.qaqc.data.AttendanceSheetImport
import com.corewall.qaqc.data.SheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تحويل شيت المقاول لصفوف.
 *
 * كل حالة هنا شكل شيت حقيقي بيتسلّم في الموقع: ترويسة شركة فوق الجدول،
 * عمود مسلسل قبل الاسم، عناوين بالعربي أو الإنجليزي، وسطر إجمالي تحت.
 */
class AttendanceSheetImportTest {

    private fun sheet(vararg rows: List<String>) = SheetReader.Sheet(rows.toList())

    @Test
    fun `an arabic header is found and the columns mapped`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("م", "الاسم", "الكود", "التخصص"),
                listOf("1", "أحمد محمود", "1021", "نجار"),
                listOf("2", "سيد علي", "1022", "حداد")
            ),
            fileId = 7
        )
        assertEquals(2, rows.size)
        assertEquals("أحمد محمود", rows[0].name)
        assertEquals("1021", rows[0].code)
        assertEquals("نجار", rows[0].trade)
        assertEquals(7, rows[0].fileId)
        assertEquals(1, rows[1].ordinal)
    }

    /** ترويسة الشركة والمشروع فوق الجدول — شكل كل شيت مقاول تقريباً. */
    @Test
    fun `rows above the header are ignored`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("شركة المقاولات المتحدة"),
                listOf("مشروع برج بكارات — كشف حضور"),
                listOf(""),
                listOf("Employee Name", "ID", "Trade"),
                listOf("Ahmed", "1021", "Carpenter")
            ),
            fileId = 1
        )
        assertEquals(1, rows.size)
        assertEquals("Ahmed", rows[0].name)
        assertEquals("1021", rows[0].code)
    }

    /**
     * ده السبب اللي خلّى المطابقة على الكلمة مش على جزء منها: `contains("no")`
     * بيطابق "Notes"، فعمود الملاحظات كان هياخد مكان عمود الكود.
     */
    @Test
    fun `a notes column is not mistaken for the code column`() {
        val header = AttendanceSheetImport.findHeader(
            sheet(listOf("Name", "Notes", "Trade"))
        )
        assertEquals(0, header!!.name)
        assertNull(header.code)
        assertEquals(2, header.trade)
    }

    @Test
    fun `a total line at the bottom is dropped`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("الاسم", "الكود"),
                listOf("أحمد", "1"),
                listOf("الإجمالي", "1")
            ),
            fileId = 1
        )
        assertEquals(1, rows.size)
        assertEquals("أحمد", rows[0].name)
    }

    @Test
    fun `blank name rows are skipped`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("الاسم"),
                listOf("أحمد"),
                listOf(""),
                listOf("سيد")
            ),
            fileId = 1
        )
        assertEquals(listOf("أحمد", "سيد"), rows.map { it.name })
    }

    /**
     * من غير الرفض ده، شيت من غير ترويسة بيستورد عمود المسلسل كأسماء —
     * مية صف اسمهم "1" و"2"، والاستيراد بيقول إنه نجح.
     */
    @Test
    fun `a sheet with no recognisable header imports nothing`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("1", "أحمد", "1021"),
                listOf("2", "سيد", "1022")
            ),
            fileId = 1
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a missing code column leaves the field empty rather than shifting`() {
        val rows = AttendanceSheetImport.rowsFrom(
            sheet(
                listOf("الاسم", "التخصص"),
                listOf("أحمد", "نجار")
            ),
            fileId = 1
        )
        assertEquals("", rows[0].code)
        assertEquals("نجار", rows[0].trade)
    }
}
