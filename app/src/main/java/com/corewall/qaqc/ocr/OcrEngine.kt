package com.corewall.qaqc.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * التعرّف الضوئي على النص.
 *
 * ده اللي بيفرق بين "رسمة ممسوحة بالسكانر" و"مستند". من غيره الملف
 * الممسوح صورة: مافيهوش بحث ولا تحديد ولا نص يروح للذكاء الاصطناعي.
 *
 * المحرّك Tesseract عن طريق tess-two. اختياره كان على أساس إنه الوحيد
 * المتاح على Maven Central اللي بيدعم **العربي** ويشتغل بالكامل على
 * الجهاز — من غير رفع أي صفحة لأي خادم. البديل الأخف (ML Kit) مابيعرفش
 * عربي خالص، والبديل الأحدث (Tesseract 4) مش منشور غير على JitPack.
 */
object OcrEngine {

    /** كلمة متعرّف عليها بمكانها **ببكسل الصورة** اللي اتعملها OCR. */
    data class Word(
        val text: String,
        val box: Rect,
        val confidence: Float
    )

    data class Outcome(
        val text: String,
        val words: List<Word>,
        /** متوسط الثقة ٠..١٠٠ — تحت ٦٠ يعني النتيجة محتاجة مراجعة. */
        val confidence: Int
    )

    /**
     * بيشغّل التعرّف على [bitmap].
     *
     * `withContext(Dispatchers.Default)` مش `IO`: ده شغل معالج مكثّف مش
     * انتظار قرص. صفحة A3 عند ٣٠٠ نقطة/بوصة بتاخد ثواني، والمكتبة
     * الأصلية بتقفل الخيط طول المدة دي.
     */
    suspend fun recognise(
        context: Context,
        bitmap: Bitmap,
        languages: List<OcrPacks.Language>
    ): Result<Outcome> = withContext(Dispatchers.Default) {
        runCatching {
            require(languages.isNotEmpty()) { "اختار لغة واحدة على الأقل" }
            val missing = languages.filterNot { OcrPacks.isInstalled(context, it) }
            require(missing.isEmpty()) {
                "حزم ناقصة: ${missing.joinToString("، ") { it.label }}"
            }

            val api = TessBaseAPI()
            try {
                val ok = api.init(
                    OcrPacks.dataDir(context).absolutePath,
                    languages.joinToString("+") { it.code }
                )
                check(ok) { "المحرّك مقدرش يقرا الحزم" }

                // الرسومات التنفيذية مش فقرات — سطور وأرقام متفرّقة على
                // اللوحة. الوضع ده بيخلّي Tesseract يفكّك الصفحة بدل ما
                // يحاول يقراها كعمود نص واحد.
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
                api.setImage(bitmap)

                val text = api.getUTF8Text().orEmpty()
                val words = collectWords(api)
                val confidence = api.meanConfidence().coerceIn(0, 100)
                Outcome(text.trim(), words, confidence)
            } finally {
                runCatching { api.end() }
            }
        }
    }

    private fun collectWords(api: TessBaseAPI): List<Word> {
        val iterator = api.getResultIterator() ?: return emptyList()
        val out = ArrayList<Word>()
        return try {
            iterator.begin()
            do {
                val word = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                if (!word.isNullOrBlank()) {
                    val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (box != null && box.width() > 0 && box.height() > 0) {
                        out += Word(
                            text = word.trim(),
                            box = box,
                            confidence = iterator.confidence(
                                TessBaseAPI.PageIteratorLevel.RIL_WORD
                            )
                        )
                    }
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            out
        } catch (e: Exception) {
            // مربّعات الكلمات إضافة على النص، مش بديل عنه. لو التكرار
            // وقع، بنرجّع اللي جمعناه بدل ما نضيّع النتيجة كلها.
            out
        } finally {
            runCatching { iterator.delete() }
        }
    }
}
