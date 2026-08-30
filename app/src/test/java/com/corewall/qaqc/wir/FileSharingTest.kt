package com.corewall.qaqc.wir

import com.corewall.qaqc.data.FilesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * إعداد `FileProvider` — الجذور اللي المشاركة مسموح لها تطلّع منها روابط.
 *
 * الاختبار ده موجود لأن الحالة دي **وصلت للمستخدم**: نسخة الطلب المعلّقة
 * بتتكتب في الكاش، والكاش مكانش مدرج، فزرار المشاركة كان بيتداس ومفيش
 * حاجة بتحصل. الفشل كان صامت مرتين — مرة في الإعداد ومرة في `runCatching`
 * اللي بيبلع الاستثناء.
 *
 * ## ليه بيقرا الـXML بدل ما يجرّب المشاركة فعلاً
 *
 * جرّبت الأول أشغّل `FileProvider.getUriForFile` تحت Robolectric على كل
 * مجلد. النتيجة كانت إن **الخمسة كلهم فشلوا** بنفس الرسالة — بما فيهم
 * مجلدات التطبيق بيشارك منها كل يوم على الجهاز. السبب إن Robolectric
 * مابيوصّلش `<meta-data>` بتاعة الـprovider، فـ`FileProvider` بيلاقي
 * إعداد فاضي ويرفض كل حاجة.
 *
 * اختبار بيفشل على كود سليم أسوأ من مفيش اختبار: بيتشال أو بيتجاهل.
 * فالاختبار بيتأكد من **الإعداد نفسه** — وده بالظبط اللي كان ناقص.
 */
class FileSharingTest {

    /** مسار نسبي لمجلد الموديول — دليل الشغل بتاع الاختبارات هو `app/`. */
    private val paths = File("src/main/res/xml/file_paths.xml")

    private val declared: List<String> by lazy {
        Regex("<\\s*([a-z-]+-path)\\b").findAll(paths.readText()).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `the provider config file is where the manifest points`() {
        assertTrue("مفيش ${paths.path} — المانيفست بيشاور عليه", paths.exists())
    }

    /**
     * كل نوع هنا مربوط بمكان التطبيق بيكتب فيه ملف بيتبعت:
     * - `external-files-path`: ملفات الأدوار وطلبات الفحص.
     * - `cache-path`: النسخ المؤقّتة — النسخة المعلّقة من طلب فحص.
     * - `files-path` و `external-cache-path`: مسارات تانية بيكتب فيها.
     */
    @Test
    fun `every root the app writes shareable files to is declared`() {
        val required = listOf(
            "external-files-path" to "ملفات الأدوار وطلبات الفحص",
            "files-path" to "التخزين الداخلي",
            "cache-path" to "النسخة المعلّقة اللي بتتبعت",
            "external-cache-path" to "الكاش الخارجي"
        )
        val missing = required.filterNot { (type, _) -> type in declared }
        assertTrue(
            "الجذور دي مش مدرجة في file_paths.xml — المشاركة منها بتفشل بصمت:\n" +
                missing.joinToString("\n") { "  <${it.first}>  ← ${it.second}" },
            missing.isEmpty()
        )
    }

    @Test
    fun `an arabic request name survives as a file name`() {
        assertEquals("قواطيع الدور الأول", FilesManager.safeFileName("قواطيع الدور الأول"))
        assertEquals("WIR_CW_12", FilesManager.safeFileName("WIR/CW:12"))
        assertEquals("WIR", FilesManager.safeFileName("   "))
        assertEquals(80, FilesManager.safeFileName("ط".repeat(200)).length)
    }
}
