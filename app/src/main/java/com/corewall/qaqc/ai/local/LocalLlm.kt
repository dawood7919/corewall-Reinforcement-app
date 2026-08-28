package com.corewall.qaqc.ai.local

import android.content.Context
import com.corewall.qaqc.diag.CrashReporter
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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

    /**
     * سقف التوكن — **دخل وخرج مع بعض**، مش الرد لوحده.
     *
     * ١٠٢٤ كانت ضيقة: السياق والسؤال بياخدوا منها، والباقي للرد. ٢٠٤٨
     * بتدّي مساحة معقولة للاتنين. الرقم ده بيحدّد حجم الـkv-cache في
     * الذاكرة كمان، فرفعه أكتر بيزوّد استهلاك الرام على طول — مش بس
     * وقت الردود الطويلة.
     */
    private const val MAX_TOKENS = 2048

    /**
     * أعلى topK مسموح — **لازم يكون واحد** في المحرّك والجلسة.
     *
     * ثابت واحد بدل رقمين عشان الغلطة اللي حصلت (الجلسة بتطلب أكتر من
     * اللي المحرّك اتبنى عليه) تبقى مستحيلة تتكرر.
     */
    private const val TOP_K = 40

    /**
     * بعض الملفات مبنية على سقف kv-cache ثابت، ومكتوب في اسمها:
     * `..._q8_ekv1280.task` يعني ١٢٨٠ توكن مش أكتر.
     *
     * طلب أكبر من اللي الملف اتبنى عليه بيفشل التحميل — والرسالة اللي
     * بتطلع مابتقولش إن ده السبب. القراية من الاسم شكلها هش، وهي كده
     * فعلاً، بس البديل إن المستخدم ينزّل جيجا ونص ويلاقيها مش شغّالة
     * من غير ما حد يقوله ليه.
     */
    private val EKV = Regex("""ekv(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * سقف التوكن الفعلي للملف ده — دخل وخرج مع بعض.
     *
     * مكشوف عشان اللي بيبني البرومبت يقدر يفصّله على المقاس: موديل
     * سقفه ١٢٨٠ لو بعتّله سياق بألف توكن، مابيبقاش قدامه مكان يرد.
     */
    fun tokenBudget(modelPath: String): Int {
        val declared = EKV.find(File(modelPath).name)?.groupValues?.get(1)?.toIntOrNull()
        return if (declared != null) minOf(MAX_TOKENS, declared) else MAX_TOKENS
    }

    private var engine: LlmInference? = null
    private var loadedPath: String? = null
    private var loadedBackend: String? = null

    /**
     * نداء واحد في المرة.
     *
     * الموديل المحلي بيشغّل المعالج بالكامل. نداءين مع بعض مابيخلّوش
     * الرد أسرع — بيخلّوا الاتنين أبطأ والجهاز يسخن.
     */
    private val lock = Mutex()

    /**
     * الملف اللي قتل العملية آخر مرة.
     *
     * ## ليه ده لازم يتسجّل
     *
     * وقوع الكود الأصلي مايتمسكش — يعني موديل مش متوافق بيقفل التطبيق
     * **مع كل رسالة، للأبد**. المستخدم بيلاقي تطبيق بيموت وهو مش عارف
     * ليه ولا قادر يوصل لشاشة الإعدادات يغيّر الملف.
     *
     * العلامة بتتكتب قبل التحميل وبتتشال بعده. لو لقيناها لسه مكتوبة
     * لنفس الملف، يبقى الملف ده وقّعنا قبل كده — فبنرفض بدل ما نجرّب
     * تاني ونموت تاني. رسالة واضحة أحسن من دورة قفل.
     */
    private const val PREFS = "local_llm"
    private const val KEY_LOADING = "loadingPath"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        prompt: String,
        backend: String = "DEFAULT"
    ): String = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!isReady(modelPath)) {
                throw LocalModelError("ملف الموديل مش موجود. اختاره تاني من الإعدادات.")
            }
            val active = ensureEngine(context, modelPath, backend)
            CrashReporter.enterNative(context, "الموديل المحلي — توليد رد")
            val answer = runCatching {
                // جلسة بإعدادات صريحة بدل النداء المباشر.
                //
                // الافتراضي بيولّد بعشوائية عالية، وده على موديل صغير
                // بيبان كـ«غباء»: بيسرح، بيخترع، وبيرد على سؤال تاني.
                // حرارة واطية وtopK محدود بيخلّوه يلزم اللي قدامه —
                // مابيخلّوهوش أذكى، بيمنعوه يتشتّت.
                LlmInferenceSession.createFromOptions(
                    active,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(0.2f)
                        .setTopK(TOP_K)
                        .setTopP(0.9f)
                        .build()
                ).use { session ->
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                }
            }.recoverCatching {
                // الجلسة بإعداداتها ممكن يرفضها بناء معيّن. النداء
                // البسيط أضعف (عشوائية أعلى) بس بيرد — أحسن من إن
                // السؤال يضيع.
                ensureEngine(context, modelPath, backend).generateResponse(prompt)
            }.also {
                CrashReporter.leaveNative(context)
            }.getOrElse { e ->
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

    private fun ensureEngine(
        context: Context,
        modelPath: String,
        backend: String
    ): LlmInference {
        engine?.takeIf { loadedPath == modelPath && loadedBackend == backend }?.let { return it }
        release()

        // وقعنا على نفس الملف قبل كده؟ مانجربش تاني.
        val store = prefs(context)
        if (store.getString(KEY_LOADING, null) == modelPath) {
            store.edit().remove(KEY_LOADING).apply()
            throw LocalModelError(
                "الملف ده قفل التطبيق آخر مرة اتحمّل فيها، فوقفناه. " +
                    "غالباً صيغته مش متوافقة مع نسخة المكتبة. " +
                    "اختار ملف تاني من الإعدادات، أو شيل الموديل المحلي وارجع للسحابي."
            )
        }
        // `commit` مش `apply`: الكتابة لازم توصل القرص قبل نداء ممكن
        // يقتل العملية.
        @Suppress("ApplySharedPref")
        store.edit().putString(KEY_LOADING, modelPath).commit()
        CrashReporter.enterNative(context, "الموديل المحلي — تحميل الملف")
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(tokenBudget(modelPath))
                // **ده كان سبب القفل.**
                //
                // المحرّك بيحجز بفرات أخذ العيّنات على أساس `maxTopK` وقت
                // إنشائه. الجلسة اللي بتطلب topK أكبر من اللي المحرّك
                // اتبنى عليه بتكتب برّه البفر — والنتيجة موت العملية في
                // الكود الأصلي، من غير استثناء جافا ومن غير أي تقرير.
                //
                // كنت مضيف الجلسة بـtopK=40 من غير ما أرفع السقف هنا.
                .setMaxTopK(TOP_K)
                .setPreferredBackend(
                    when (backend) {
                        "CPU" -> LlmInference.Backend.CPU
                        "GPU" -> LlmInference.Backend.GPU
                        else -> LlmInference.Backend.DEFAULT
                    }
                )
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also {
                engine = it
                loadedPath = modelPath
                loadedBackend = backend
                CrashReporter.leaveNative(context)
                // اتحمّل بالسلامة — العلامة تتشال عشان التحميل الجاي
                // مايتمنعش بالغلط.
                store.edit().remove(KEY_LOADING).apply()
            }
        }.getOrElse { e ->
            release()
            store.edit().remove(KEY_LOADING).apply()
            // الرسالة بتفرّق بين السببين لأن التصرّف مختلف: الفشل على
            // الـGPU غالباً ذاكرته مش كفاية والحل تجرّب المعالج، والفشل
            // العام غالباً الملف نفسه.
            val hint = if (backend == "GPU")
                " جرّب تبدّل للمعالج (CPU) — ذاكرة كارت الشاشة أقل، والموديل الكبير مابيدخلهاش."
            else " اتأكد إنه ملف .task أو .litertlm سليم، وإن الجهاز فيه ذاكرة كافية. " +
                "الملفات الكبيرة (٤ جيجا وفوق) محتاجة تفضّي رام — اقفل تطبيقات تانية وجرّب."
            throw LocalModelError(
                "مقدرناش نفتح الموديل.$hint (${e.message ?: e::class.java.simpleName})"
            )
        }
    }

    /** بيفضّي الذاكرة. بيتنده لما المستخدم يغيّر الموديل أو يشيله. */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
        loadedBackend = null
    }
}

/** فشل في الموديل المحلي — رسالته جاهزة تتعرض للمستخدم. */
class LocalModelError(message: String) : Exception(message)
