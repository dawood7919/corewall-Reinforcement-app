package com.corewall.qaqc.ai.local

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
 * الموديل اللي بيشتغل على تليفون أصغر بمراحل من اللي بيشتغل على سيرفر،
 * ومعنى كده حاجتين:
 *
 * 1. **مفيش أدوات.** حلقة الوكيل بتطلب من الموديل يطلّع JSON فيه نداءات
 *    أدوات ويكرّرها أربع جولات. موديل بحجم اللي بيتحمّل على تليفون
 *    مابيمسكش الصيغة دي، والنتيجة أدوات بتضيع بصمت — وده أسوأ من إنها
 *    تكون مقفولة بصراحة.
 * 2. **مفيش سياق ضخم.** برومبت الوكيل لوحده ٦٣٠٠ توكن، ومعالجته على
 *    الجهاز بتاخد وقت طويل قبل أول حرف. فالمسار المحلي بياخد سياق مختصر.
 *
 * فالمحلي **مش بديل** للسحابي — هو اللي بيشتغل لما مايكونش فيه شبكة.
 * الاتنين موجودين، والمستخدم بيبدّل.
 *
 * ## المحرّك بيتحمّل مرة
 *
 * `initialize()` ممكن تاخد لحد عشر ثواني، فالمحرّك بيتخزّن ويتعاد
 * استخدامه. بيتقفل ويترجع يتحمّل بس لو المستخدم غيّر الملف.
 */
object LocalLlm {

    private var engine: Engine? = null
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
     * بيولّد رد، وبينده [onDelta] بالنص التراكمي وهو بيتكوّن.
     *
     * أي فشل بيطلع كـ[LocalModelError] برسالة مفهومة: الملف الغلط أو
     * الجهاز اللي مش قادر يشيل الموديل حاجات المستخدم يقدر يتصرّف فيها،
     * فمايستاهلش يشوف رسالة استثناء خام.
     */
    suspend fun generate(
        modelPath: String,
        prompt: String,
        onDelta: (String) -> Unit = {}
    ): String = lock.withLock {
        withContext(Dispatchers.Default) {
            if (!isReady(modelPath)) {
                throw LocalModelError("ملف الموديل مش موجود. اختاره تاني من الإعدادات.")
            }
            val active = ensureEngine(modelPath)
            val out = StringBuilder()
            var lastNotified = 0
            try {
                active.createConversation().use { conversation ->
                    conversation.sendMessageAsync(prompt).collect { piece ->
                        out.append(piece.toString())
                        // نفس سبب الخنق في البثّ السحابي: التحديث مع كل
                        // رمز بينسخ نص بيكبر، والتكلفة تربيعية.
                        if (out.length - lastNotified >= 48) {
                            lastNotified = out.length
                            onDelta(out.toString())
                        }
                    }
                }
            } catch (e: Throwable) {
                // المحرّك ممكن يكون بقى في حالة مش سليمة — بنرميه عشان
                // النداء الجاي يبدأ من نضيف بدل ما يفضل يفشل.
                release()
                throw LocalModelError(
                    "الموديل المحلي وقع: ${e.message ?: e::class.java.simpleName}"
                )
            }
            if (out.isBlank()) throw LocalModelError("الموديل المحلي ماردّش بحاجة.")
            out.toString().also(onDelta)
        }
    }

    private fun ensureEngine(modelPath: String): Engine {
        engine?.takeIf { loadedPath == modelPath }?.let { return it }
        release()
        return runCatching {
            Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU())).also {
                it.initialize()
                engine = it
                loadedPath = modelPath
            }
        }.getOrElse { e ->
            release()
            throw LocalModelError(
                "مقدرناش نفتح الموديل. اتأكد إنه ملف .litertlm سليم ومناسب للجهاز. " +
                    "(${e.message ?: e::class.java.simpleName})"
            )
        }
    }

    /** بيفضّي الذاكرة. بيتنده لما المستخدم يغيّر الموديل أو يقفل الميزة. */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }
}

/** فشل في الموديل المحلي — رسالته جاهزة تتعرض للمستخدم. */
class LocalModelError(message: String) : Exception(message)
