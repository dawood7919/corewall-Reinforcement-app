package com.corewall.qaqc.ai.local

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * موديل شغّال على الجهاز.
 *
 * ## ليه أصلاً
 *
 * الشغل في موقع، والشبكة في المواقع بتقطع. مساعد بيحتاج إنترنت بيبقى
 * غير موجود في نص الحالات اللي المستخدم محتاجه فيها فعلاً.
 *
 * ## الحدود — دي مش تفاصيل، دي شكل الميزة
 *
 * الموديل اللي بيشتغل على تليفون أصغر بمراحل من اللي بيشتغل على سيرفر:
 *
 * 1. **مفيش أدوات.** حلقة الوكيل بتطلب من الموديل يطلّع JSON فيه نداءات
 *    أدوات ويكرّرها أربع جولات. موديل بحجم اللي بيتحمّل على تليفون
 *    مابيمسكش الصيغة دي، والنتيجة أدوات بتضيع بصمت — وده أسوأ من إنها
 *    تكون مقفولة بصراحة.
 * 2. **مفيش سياق ضخم.** برومبت الوكيل لوحده ٦٣٠٠ توكن، ومعالجته على
 *    الجهاز بتاخد وقت طويل قبل أول حرف. فالمسار المحلي بياخد سياق مختصر.
 *
 * فالمحلي **مش بديل** للسحابي — هو اللي بيشتغل لما مايكونش فيه شبكة.
 *
 * ## المحرّك بيتحمّل مرة
 *
 * `createFromOptions` بتقرا ملف بحجم جيجابايت وبتجهّزه في الذاكرة، فبتاخد
 * ثواني. المحرّك بيتخزّن ويتعاد استخدامه، وبيتقفل بس لو المستخدم غيّر
 * الملف — عشان نسختين في الذاكرة مع بعض بتوقّع التطبيق.
 *
 * ## المكتبة
 *
 * MediaPipe واجهتها جافا، وده مقصود: `litertlm-android` (الطريق اللي
 * جوجل بتوصّي بيه) متبني على Kotlin 2.3 والمشروع على 2.0.21، فالكومبايلر
 * بيرفض ميتاداتاه. الملف ده هو **كل** اللي بيعرف المكتبة، فالنقل لما
 * الـtoolchain يترقّى بيبقى تغيير هنا بس.
 */
object LocalLlm {

    /** طول الرد بالتوكن. أكبر من كده بياكل ذاكرة ووقت من غير فايدة هنا. */
    private const val MAX_TOKENS = 1024

    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    /**
     * نداء واحد في المرة.
     *
     * الموديل المحلي بيشغّل المعالج بالكامل. نداءين مع بعض مابيخلّوش
     * الرد أسرع — بيخلّوا الاتنين أبطأ والجهاز يسخن.
     */
    private val lock = Mutex()

    /** الملف موجود فعلاً؟ */
    fun isReady(path: String): Boolean =
        path.isNotBlank() && runCatching { File(path).let { it.isFile && it.length() > 0 } }
            .getOrDefault(false)

    /**
     * بيولّد رد.
     *
     * أي فشل بيطلع كـ[LocalModelError] برسالة مفهومة: الملف الغلط أو
     * الجهاز اللي مش قادر يشيل الموديل حاجات المستخدم يقدر يتصرّف فيها،
     * فمايستاهلش يشوف رسالة استثناء خام.
     */
    suspend fun generate(
        context: Context,
        modelPath: String,
        prompt: String
    ): String = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!isReady(modelPath)) {
                throw LocalModelError("ملف الموديل مش موجود. اختاره تاني من الإعدادات.")
            }
            val active = ensureEngine(context, modelPath)
            val answer = runCatching { active.generateResponse(prompt) }.getOrElse { e ->
                // المحرّك ممكن يكون بقى في حالة مش سليمة — بنرميه عشان
                // النداء الجاي يبدأ من نضيف بدل ما يفضل يفشل.
                release()
                throw LocalModelError(
                    "الموديل المحلي وقع: ${e.message ?: e::class.java.simpleName}"
                )
            }
            if (answer.isNullOrBlank()) throw LocalModelError("الموديل المحلي ماردّش بحاجة.")
            answer
        }
    }

    private fun ensureEngine(context: Context, modelPath: String): LlmInference {
        engine?.takeIf { loadedPath == modelPath }?.let { return it }
        release()
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also {
                engine = it
                loadedPath = modelPath
            }
        }.getOrElse { e ->
            release()
            throw LocalModelError(
                "مقدرناش نفتح الموديل. اتأكد إنه ملف .task مناسب لـMediaPipe " +
                    "وإن الجهاز فيه ذاكرة كافية. (${e.message ?: e::class.java.simpleName})"
            )
        }
    }

    /** بيفضّي الذاكرة. بيتنده لما المستخدم يغيّر الموديل أو يشيله. */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }
}

/** فشل في الموديل المحلي — رسالته جاهزة تتعرض للمستخدم. */
class LocalModelError(message: String) : Exception(message)
