package com.corewall.qaqc.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * حزم لغات الـOCR — بتتحمّل عند الطلب مش مشحونة مع التطبيق.
 *
 * ليه بتتحمّل: حزمة الإنجليزي لوحدها ٢٢ ميجا والعربي ٦ ميجا. شحنهم في
 * الـAPK معناه إن كل مستخدم بيحمّل ٢٨ ميجا زيادة عشان ميزة أغلبهم مش
 * هيستخدمها — والتطبيق بيتوزّع كملف APK مباشر مش من متجر بيقسّم الحزم.
 *
 * والتحميل **بمبادرة المستخدم بس**: التطبيق مابيلمسش الشبكة من نفسه
 * أبداً. الشاشة بتقول الحجم قبل الضغط، والزرار هو اللي بيبدأ.
 */
object OcrPacks {

    /**
     * لغة متاحة.
     *
     * [bytes] تقريبي وبيتعرض قبل التحميل. الأرقام دي من ملفات الإصدار
     * القديم (٣.٠٤) لأن محرّك tess-two مبني على Tesseract ٣.٠٥،
     * والملفات الأحدث (LSTM) مابتتقريش منه أصلاً.
     */
    enum class Language(
        val code: String,
        val label: String,
        val bytes: Long
    ) {
        ENGLISH("eng", "إنجليزي", 21_876_550L),
        ARABIC("ara", "عربي", 6_315_068L);

        companion object {
            fun fromCode(code: String): Language? = entries.firstOrNull { it.code == code }
        }
    }

    /** المجلد اللي Tesseract بيدوّر فيه — لازم يبقى اسمه `tessdata` بالظبط. */
    fun dataDir(context: Context): File =
        File(context.filesDir, "tesseract").apply { mkdirs() }

    private fun tessdata(context: Context): File =
        File(dataDir(context), "tessdata").apply { mkdirs() }

    fun fileFor(context: Context, language: Language): File =
        File(tessdata(context), "${language.code}.traineddata")

    fun isInstalled(context: Context, language: Language): Boolean {
        val file = fileFor(context, language)
        // الحجم شرط مش رفاهية: تحميل اتقطع في النص بيسيب ملف موجود
        // وTesseract بيقع عليه بدل ما يقول إنه ناقص.
        return file.exists() && file.length() > MIN_VALID_BYTES
    }

    fun installed(context: Context): List<Language> =
        Language.entries.filter { isInstalled(context, it) }

    fun delete(context: Context, language: Language): Boolean =
        fileFor(context, language).delete()

    /**
     * بيحمّل حزمة لغة.
     *
     * بيكتب في ملف مؤقّت وبينقله بعد ما يخلص — نفس مبدأ الكتابة الآمنة
     * في باقي التطبيق. تحميل اتقطع مابيسيبش ملف نصّه مكتوب في مكان
     * المحرّك بيثق فيه.
     */
    suspend fun download(
        context: Context,
        language: Language,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = fileFor(context, language)
            val temp = File(target.parentFile, "${language.code}.part")

            val request = Request.Builder().url(urlFor(language)).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "الخادم رفض الطلب (${response.code})" }
                val body = response.body ?: error("رد فاضي من الخادم")
                val total = body.contentLength().takeIf { it > 0 } ?: language.bytes

                temp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
            }

            check(temp.length() > MIN_VALID_BYTES) { "الملف اللي نزل ناقص" }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "مقدرناش نحفظ الحزمة" }
            target
        }.onFailure {
            File(tessdata(context), "${language.code}.part").delete()
        }
    }

    private fun urlFor(language: Language): String = "$BASE_URL/${language.code}.traineddata"

    /**
     * فرع `3.04.00` مقصود.
     *
     * الملفات في `main` أو `tessdata_fast` مبنية لمحرّك ٤ (LSTM)،
     * وTesseract ٣.٠٥ بيرفضها بصمت ويرجّع نص فاضي — أسوأ من رسالة خطأ.
     */
    private const val BASE_URL =
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata/3.04.00"

    private const val BUFFER = 64 * 1024
    private const val MIN_VALID_BYTES = 100_000L

    private val client by lazy { OkHttpClient() }
}
