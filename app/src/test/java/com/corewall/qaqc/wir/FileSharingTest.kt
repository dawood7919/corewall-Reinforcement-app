package com.corewall.qaqc.wir

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.corewall.qaqc.data.FilesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * المشاركة بتعدّي من `FileProvider`، وده بيرمي على أي ملف مش تحت جذر
 * مدرج في `res/xml/file_paths.xml`.
 *
 * الاختبار ده موجود لأن الحالة دي **وصلت للمستخدم**: نسخة الطلب المعلّقة
 * بتتكتب في الكاش، والكاش مكانش مدرج، فزرار المشاركة كان بيتداس ومفيش
 * حاجة بتحصل. الفشل كان صامت مرتين — مرة في الإعداد ومرة في `runCatching`
 * اللي بيبلع الاستثناء.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSharingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = FilesManager(context)

    private fun seed(file: File): File {
        file.parentFile?.mkdirs()
        file.writeText("pdf")
        return file
    }

    /**
     * بيجمع كل الجذور المكسورة قبل ما يفشل، مش بيقف على أول واحد: جولة
     * واحدة بتقول كل اللي ناقص بدل ما كل إصلاح يكشف اللي بعده.
     */
    @Test
    fun `every directory the app shares from resolves through the provider`() {
        val roots = buildMap<String, File> {
            put("مجلد ملفات الدور", File(files.levelDir("GF"), "x.pdf"))
            put("مجلد طلبات الفحص", File(files.wirDir("GF"), "WIR-1.pdf"))
            put("الكاش الداخلي", File(context.cacheDir, "WIR-1-معلّق.pdf"))
            put("الملفات الداخلية", File(context.filesDir, "x.pdf"))
            // الكاش الخارجي مش مضمون على كل جهاز — بنختبره لو موجود بس.
            context.externalCacheDir?.let { put("الكاش الخارجي", File(it, "x.pdf")) }
        }
        val broken = roots.mapNotNull { (label, file) ->
            runCatching { files.uriFor(seed(file)) }
                .exceptionOrNull()
                ?.let { "$label → ${file.absolutePath}\n    ${it.message}" }
        }
        assertTrue(
            "FileProvider مارضيش يطلّع رابط من الجذور دي — المشاركة منها بتفشل بصمت:\n" +
                broken.joinToString("\n"),
            broken.isEmpty()
        )
    }

    @Test
    fun `sharing reports a reason instead of failing silently`() {
        // ملف بره كل الجذور — لازم يرجّع سبب، مش يبتلع الغلط.
        val outside = seed(File(System.getProperty("java.io.tmpdir"), "corewall-outside.pdf"))
        assertNotNull("المشاركة من مسار ممنوع لازم ترجّع سبب", files.shareChecked(outside))
    }

    @Test
    fun `an arabic request name survives as a file name`() {
        assertEquals("قواطيع الدور الأول", FilesManager.safeFileName("قواطيع الدور الأول"))
        assertEquals("WIR_CW_12", FilesManager.safeFileName("WIR/CW:12"))
        assertEquals("WIR", FilesManager.safeFileName("   "))
    }
}
