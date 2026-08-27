package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiError
import com.corewall.qaqc.ai.JsonRepair
import com.corewall.qaqc.ai.model.AnswerBlock
import com.corewall.qaqc.ai.model.ChatAnswer
import com.corewall.qaqc.ai.remote.providerFor
import kotlinx.serialization.json.Json

/**
 * حلقة الوكيل: **يفكّر ← ينفّذ أدوات ← يشوف النتيجة ← يجاوب.**
 *
 * ليه حلقة أصلاً؟ لأن سؤال زي "هل تسليح الدور الجاي هيتغيّر؟" محتاج
 * بيانات مش موجودة في اللقطة العامة. الموديل بيطلب الأداة، بننفّذها بكود
 * حتمي، وبنرجّعله الحقيقة — فالإجابة مبنية على حساب مش على تخمين.
 *
 * ### الحدود
 * - أقصى [MAX_ROUNDS] جولات، فالسؤال مايدخلش في لفّة لا نهائية.
 * - أدوات القراءة والتنقّل بتتنفّذ فوراً.
 * - أدوات التعديل والحذف **مابتتنفّذش هنا**. بترجع كإجراءات مقترحة،
 *   والمستخدم بيوافق بضغطة. ده مقصود: دي سجلّات جودة لمشروع حقيقي،
 *   وهلوسة واحدة تكفي تمسح شغل شهر.
 */
class AgentEngine(private val executor: AgentExecutor) {

    private val json = Json {
        ignoreUnknownKeys = true; isLenient = true
        coerceInputValues = true; encodeDefaults = true; explicitNulls = false
    }

    companion object {
        private const val MAX_ROUNDS = 4
        private const val MAX_ACTIONS_PER_ROUND = 4

        /** بالترتيب: الإجابة أهم من الفكرة لو الاتنين موجودين. */
        private val PREVIEW_KEYS = listOf("\"headline\"", "\"thought\"", "\"body\"")

        private val PERMISSION_PHRASES = Regex(
            "موافقت|موافقة منك|تحب أ|تحب ا|هل أ|أعملها|اعملها|أنفّذ|انفذ|" +
                "تأكيد|لو موافق|تحب تأكد|بإذنك|استأذن"
        )
    }

    /** نتيجة تشغيل الوكيل. */
    data class Run(
        val answer: ChatAnswer,
        val executed: List<ActionLogEntry>,
        val pending: List<PendingAction>
    )

    suspend fun ask(
        config: AiConfig,
        question: String,
        appState: String,
        knowledge: String,
        history: String,
        memory: String,
        onProgress: (String) -> Unit
    ): Run {
        if (!config.isConfigured) throw AiError.NoKey

        val executed = mutableListOf<ActionLogEntry>()
        val pending = mutableListOf<PendingAction>()
        val transcript = StringBuilder()
        var pendingSeq = System.currentTimeMillis()
        var retriedForAction = false
        // بصمة كل أداة اتشغّلت بنفس الوسائط. الموديل بيعيد نفس النداء
        // أحياناً رغم التعليمات، والنتيجة بتتخزّن مرتين في الترانسكريبت
        // وبتتبعت في كل جولة بعد كده — تمن مضاعف لصفر معلومة جديدة.
        val alreadyRun = HashSet<String>()

        repeat(MAX_ROUNDS) { round ->
            val user = buildUserMessage(question, appState, knowledge, history, memory, transcript.toString(), round)
            // بثّ: النصّ بيتسلّم وهو بيوصل. الرد النهائي هو هو، اللي
            // بيتغيّر إن المستخدم بيشوف حاجة بتحصل بدل شاشة ساكتة.
            val raw = providerFor(config.provider)
                .completeStreaming(config, systemPrompt(), user) { partial ->
                    livePreview(partial)?.let(onProgress)
                }
            val step = parseStep(raw)

            if (step.thought.isNotBlank()) onProgress(step.thought)

            val actions = step.actions.take(MAX_ACTIONS_PER_ROUND)

            // مفيش أدوات = الوكيل خلص
            if (actions.isEmpty()) {
                val answer = step.answer ?: fallbackAnswer(raw)

                // شبكة أمان لأكتر غلطة بتضيّع طلب المستخدم: الوكيل يكتب
                // "مستني موافقتك" **من غير** ما يبعت الأداة. ساعتها مفيش
                // كارت موافقة يظهر، والمستخدم بيبص على رسالة بتستأذن في
                // حاجة مالهاش زرار — وده بالظبط اللي كان بيحصل.
                //
                // الرد مرة واحدة بس، وبس لما يبقى فيه استئذان فعلاً: تمنها
                // رحلة واحدة في حالة الفشل، ومفيش تمن في الحالة العادية.
                if (pending.isEmpty() && !retriedForAction && asksPermission(answer)) {
                    retriedForAction = true
                    transcript.appendLine(
                        "[النظام] إنت استأذنت المستخدم من غير ما تبعت الأداة في `actions`، " +
                            "فمظهرش عنده أي حاجة يوافق عليها. ابعت الأداة دلوقتي في " +
                            "`actions` — التطبيق هو اللي هيعرض الموافقة."
                    )
                    transcript.appendLine()
                    return@repeat
                }

                return Run(answer, executed, pending)
            }

            for (action in actions) {
                val tool = AgentTools.find(action.tool)
                if (tool == null) {
                    transcript.appendLine("[${action.tool}] ✗ أداة مش موجودة")
                    continue
                }
                if (AgentTools.autoRuns(tool.name)) {
                    val signature = action.describe()
                    if (!alreadyRun.add(signature)) {
                        transcript.appendLine(
                            "[${tool.name}] ↺ اتشغّلت قبل كده بنفس الوسائط — النتيجة فوق."
                        )
                        transcript.appendLine()
                        continue
                    }
                    // المستخدم بيستنى دقيقة قدام شاشة مكتوب عليها "بيشتغل…"
                    // من غير ما يعرف بيشتغل في إيه. اسم الأداة بيدّي إشارة
                    // إن فيه تقدّم فعلي بدل ما الانتظار يتقري كتعليق.
                    onProgress(AgentTools.progressLabel(tool.name))
                    val outcome = executor.run(action)
                    executed += ActionLogEntry(
                        at = System.currentTimeMillis(),
                        tool = tool.name,
                        detail = action.describe(),
                        ok = outcome.ok,
                        auto = true
                    )
                    transcript.appendLine("[${tool.name}] ${if (outcome.ok) "✓" else "✗"}")
                    transcript.appendLine(outcome.observation)
                    transcript.appendLine()
                } else {
                    // إجراء بيغيّر بيانات — بيتعرض للموافقة، ومابيتنفّذش دلوقتي
                    pending += PendingAction(
                        id = pendingSeq++,
                        action = action,
                        tool = tool,
                        label = action.reason.ifBlank { tool.summary }
                    )
                    transcript.appendLine(
                        "[${tool.name}] ⏸ اتعرض على المستخدم للموافقة — لسه ماتنفّذش. " +
                            "متفترضش إنه اتعمل."
                    )
                    transcript.appendLine()
                }
            }

            // فيه إجراء مستني موافقة؟ خلاص — الكرة عند المستخدم.
            //
            // الجولات اللي بعدها مالهاش أي فايدة: الوكيل مش هيقدر ينفّذ
            // الإجراء ولا يشوف نتيجته، وكل جولة رحلة كاملة للسيرفر.
            // ده كان بيكلّف لحد تلات رحلات زيادة على كل طلب تعديل.
            if (pending.isNotEmpty()) {
                val answer = step.answer ?: finalAnswer(
                    config, question, appState, knowledge, history, memory, transcript.toString(), onProgress
                )
                return Run(answer, executed, pending)
            }

            // آخر جولة: لو الموديل بعت إجابة معاها، ناخدها
            if (round == MAX_ROUNDS - 1 && step.answer != null) {
                return Run(step.answer, executed, pending)
            }
        }

        // خلصت الجولات من غير إجابة — نطلب واحدة أخيرة من غير أدوات
        return Run(
            finalAnswer(config, question, appState, knowledge, history, memory, transcript.toString(), onProgress),
            executed, pending
        )
    }

    /** طلب أخير من غير أدوات — للحالة اللي الوكيل مابعتش فيها إجابة. */
    private suspend fun finalAnswer(
        config: AiConfig,
        question: String,
        appState: String,
        knowledge: String,
        history: String,
        memory: String,
        transcript: String,
        onProgress: (String) -> Unit
    ): ChatAnswer {
        val raw = providerFor(config.provider).completeStreaming(
            config, systemPrompt(),
            // `round = 0` هنا مش خطأ: ده الطلب اللي الإجابة بتطلع منه،
            // فبياخد السياق كامل. `force` هو اللي بيحدّد التعليمات.
            buildUserMessage(question, appState, knowledge, history, memory, transcript, 0, force = true)
        ) { partial -> livePreview(partial)?.let(onProgress) }
        return parseStep(raw).answer ?: fallbackAnswer(raw)
    }

    // ------------------------------------------------------------ البرومبت

    private fun systemPrompt(): String = buildString {
        appendLine(AppSchema.CORE)
        appendLine()
        appendLine("# إنت وكيل جوّه التطبيق")
        appendLine()
        appendLine("مش بترد على أسئلة بس — عندك أدوات بتشغّلها فعلاً جوّه التطبيق.")
        appendLine("كل جولة إنت بتختار: تشغّل أدوات، ولا تدّي الإجابة النهائية.")
        appendLine()
        appendLine(AgentTools.catalogue())
        appendLine()
        appendLine("## قواعد استخدام الأدوات")
        appendLine("1. لو الإجابة محتاجة رقم مش موجود في حالة التطبيق المعروضة، **شغّل أداة**. متخمّنش.")
        appendLine("2. لسؤال \"هل التسليح هيتغيّر في الدور الجاي\" استخدم `next_floor_changes`.")
        appendLine("3. لأي حساب حديد استخدم `steel_quantity` — متحسبش بنفسك.")
        appendLine("4. أدوات التعديل والحذف: **ابعت الأداة في `actions` عادي.** التطبيق هو اللي")
        appendLine("   بيوقّفها ويعرض كارت موافقة للمستخدم — إنت مش بتنفّذها، وإنت مش بتستأذن.")
        appendLine("   **ممنوع** تكتب \"مستني موافقتك\" أو \"تحب أعملها؟\" في نص الإجابة من غير ما")
        appendLine("   تبعت الأداة معاها: لو بعت الكلام لوحده، المستخدم **مش هيلاقي حاجة يوافق عليها**")
        appendLine("   والطلب بتاعه بيضيع. غلط ← {\"answer\":{\"headline\":\"مستني موافقتك\"},\"actions\":[]}")
        appendLine("   صح ← {\"actions\":[{\"tool\":\"add_task\",\"args\":{…}}],\"answer\":{\"headline\":\"جهّزت المهمة\"}}")
        appendLine("   وبرضه **متقولش إنك عملتها** — قول إنها اتجهّزت.")
        appendLine("5. متشغّلش أداة كتبت نتيجتها قبل كده في نفس المحادثة.")
        appendLine("7. المستخدم شاور على حاجة اتقالت قبل كده وإنت مش شايفها فوق؟ شغّل")
        appendLine("   `search_chat` — المحادثة كلها متسجّلة. **ممنوع** ترد بـ\"مش فاكر\".")
        appendLine("8. طلع قرار أو رقم هيفرق في المحادثات الجاية؟ احفظه بـ`remember`.")
        appendLine("6. لو المستخدم طلب حاجة تتعمل (امسح/ضيف/غيّر)، اقترح الأداة المناسبة فوراً —")
        appendLine("   متسألش أسئلة توضيحية زيادة عن اللزوم.")
        appendLine()
        appendLine("## صيغة الرد (JSON صالح بس، من غير markdown)")
        appendLine(
            """
{
  "thought": "سطر قصير عن اللي بتعمله دلوقتي",
  "actions": [ {"tool":"اسم_الأداة","args":{"مفتاح":"قيمة"},"reason":"ليه"} ],
  "answer": null
}
            """.trim()
        )
        appendLine()
        appendLine("ولما تخلص، ابعت `actions: []` مع الإجابة:")
        appendLine(
            """
{
  "thought": "",
  "actions": [],
  "answer": {
    "headline": "الإجابة في جملة",
    "blocks": [ … ],
    "followUps": ["سؤال متابعة"],
    "sources": ["مصدر"]
  }
}
            """.trim()
        )
        appendLine()
        appendLine("## أنواع بلوكات الإجابة")
        appendLine(AnswerBlockSpec.SPEC)
    }

    private fun buildUserMessage(
        question: String,
        appState: String,
        knowledge: String,
        history: String,
        memory: String,
        transcript: String,
        round: Int,
        force: Boolean = false
    ): String = buildString {
        // لقطة التطبيق ٦ آلاف حرف، وكانت بتتبعت **كاملة في كل جولة** —
        // يعني أربع مرات في السؤال الواحد، والحالة مابتتغيّرش بينهم أصلاً.
        // الجولة الأولى بتاخدها كلها؛ اللي بعدها بتاخد السطور اللي بتحدّد
        // «إنت فين» بس، لأن باقي اللي محتاجه بقى في نتايج الأدوات تحت.
        appendLine(if (round == 0) appState else appState.lineSequence().take(6).joinToString("\n"))
        appendLine()
        // الذاكرة بتتبعت في كل جولة عن قصد — سقفها ١٢٠٠ حرف، وهي اللي
        // بتخلّي الوكيل فاكر بدل ما يسأل نفس السؤال تاني.
        if (memory.isNotBlank()) {
            appendLine("## اللي إنت فاكره عن الدور ده")
            appendLine(memory)
            appendLine()
        }

        // نفس السبب: دول محسوبين من السؤال ومابيتغيّروش بين الجولات.
        if (round == 0 && knowledge.isNotBlank()) {
            appendLine("## معرفة المستندات المرتبطة بالسؤال")
            appendLine(knowledge)
            appendLine()
        }
        if (round == 0 && history.isNotBlank()) {
            appendLine("## آخر الرسائل")
            appendLine(history)
            appendLine()
        }
        if (transcript.isNotBlank()) {
            appendLine("## نتايج الأدوات اللي شغّلتها (حقائق محسوبة — استخدمها زي ما هي)")
            appendLine(transcript)
            appendLine()
        }
        appendLine("## سؤال المستخدم")
        appendLine(question)
        appendLine()
        when {
            force -> appendLine("خلاص، مفيش أدوات تانية. ابعت الإجابة النهائية دلوقتي (`actions: []`).")
            round == 0 -> appendLine("لو محتاج بيانات مش موجودة فوق، شغّل الأدوات المناسبة. غير كده جاوب على طول.")
            else -> appendLine("لو البيانات كفاية، جاوب دلوقتي (`actions: []`). لو ناقص حاجة محدّدة، شغّل أداة واحدة كمان.")
        }
    }

    // ------------------------------------------------------------ الفكّ

    private fun parseStep(raw: String): AgentStep {
        val obj = JsonRepair.extractObject(raw) ?: return AgentStep(answer = fallbackAnswer(raw))
        return runCatching { json.decodeFromString(AgentStep.serializer(), obj.json) }
            .getOrElse { AgentStep(answer = fallbackAnswer(raw)) }
    }

    /**
     * أول كلام مفيد من JSON لسه بيتكتب.
     *
     * الرد بيوصل حرف حرف، وفي نصّه بيبقى JSON ناقص مايتفكّش. بس الحقول
     * اللي إحنا عايزينها بتيجي في الأول، وقراية نصّ بين علامتي تنصيص
     * مابتحتاجش المستند يكون كامل — فبنقراها بالإيد بدل ما نستنى القوس
     * الأخير. مش محلّل JSON، وماينفعش يبقى واحد: نصّ نص مش JSON صالح.
     *
     * بيرجّع `null` لحد ما يبقى فيه حاجة تتعرض — عرض حروف مقطوعة أسوأ
     * من عرض الرسالة القديمة ثانية زيادة.
     */
    private fun livePreview(buffer: String): String? {
        for (key in PREVIEW_KEYS) {
            val at = buffer.indexOf(key)
            if (at < 0) continue
            val colon = buffer.indexOf(':', at + key.length)
            if (colon < 0) continue
            val quote = buffer.indexOf('"', colon + 1)
            if (quote < 0) continue
            val sb = StringBuilder()
            var i = quote + 1
            while (i < buffer.length) {
                val ch = buffer[i]
                when {
                    ch == '\\' && i + 1 < buffer.length -> {
                        // الهروب بيتفك بالإيد: `\n` جوّه العرض بيبان كسطر
                        // جديد، و`\"` مايقفلش النصّ بالغلط.
                        when (val next = buffer[i + 1]) {
                            'n' -> sb.append(' ')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            else -> sb.append(next)
                        }
                        i += 2
                    }
                    ch == '"' -> return sb.toString().trim().ifBlank { null }
                    else -> { sb.append(ch); i++ }
                }
            }
            // النصّ لسه بيتكتب — بنعرضه ناقص، ده المقصود من البثّ.
            return sb.toString().trim().takeIf { it.length >= 4 }
        }
        return null
    }

    /**
     * هل الرد بيستأذن؟
     *
     * الصيغ دي هي اللي الموديل بيكتبها لما يفهم إن التعديل محتاج موافقة
     * فيسأل بالكلام بدل ما يبعت الأداة. مش فحص كامل للغة — ومش محتاج
     * يكون: أسوأ نتيجة للخطأ إن الوكيل يتسأل مرة زيادة.
     */
    private fun asksPermission(answer: ChatAnswer): Boolean {
        val text = buildString {
            append(answer.headline)
            answer.blocks.forEach { append(' ').append(it.title).append(' ').append(it.body) }
        }
        return PERMISSION_PHRASES.containsMatchIn(text)
    }

    /** لو الموديل خرج عن الصيغة، بنعرض نصّه بدل ما نضيّع الرد. */
    private fun fallbackAnswer(raw: String): ChatAnswer =
        ChatAnswer(blocks = listOf(AnswerBlock(type = "TEXT", body = raw.trim().take(2_000))))
}

/** مواصفة بلوكات الإجابة — متعرّفة مرة واحدة ومتشاركة بين البرومبتات. */
internal object AnswerBlockSpec {
    val SPEC = """
اختار النوع حسب وظيفة البيانات، مش حسب الشكل:
- رقم أو رقمين مهمين → {"type":"METRICS","title":"","metrics":[{"label":"","value":"1,240","hint":"","delta":"+12","direction":"UP|DOWN|FLAT","upIsGood":true}]}
- مقارنة مقادير → {"type":"BAR","title":"","unit":"kg","points":[{"label":"T1-W4A","value":320}]}
- جزء من كل → {"type":"SPLIT","title":"","points":[{"label":"معتمد","value":18},{"label":"معلّق","value":5}]}
- نسبة مقابل حد → {"type":"METER","title":"","percent":72,"body":""}
- تغيّر عبر الزمن → {"type":"TREND","title":"","unit":"","points":[{"label":"الأحد","value":42}]}
- تفاصيل كتير أو أكتر من ٧ أصناف → {"type":"TABLE","title":"","columns":["",""],"rows":[["",""]]}
- نقط أو خطوات → {"type":"LIST"|"STEPS","title":"","items":[""]}
- خطر أو نقص → {"type":"ALERT","title":"","body":"","severity":"WARNING|SERIOUS|CRITICAL"}
- شرح → {"type":"TEXT","title":"","body":""}
- عرض ملفات للمستخدم → {"type":"FILES","title":"","files":[{"path":"المسار الكامل","caption":"ليه بتعرضه"}]}
- عرض صور → {"type":"IMAGES","title":"","files":[{"path":"مسار الصورة","caption":""}]}

قواعد: رقم واحد مايتعملوش رسمة. حد أقصى 5 بلوكات. البلوك اللي مالوش
بيانات حقيقية متبعتوش. في FILES و IMAGES استخدم **المسارات الكاملة**
زي ما رجعت من الأدوات بالظبط — متخترعش مسار.
حُطّ أكواد العناصر المعنية في "marks": ["T1-W4A"].
    """.trim()
}
