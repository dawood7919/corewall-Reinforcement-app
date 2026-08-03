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
        onProgress: (String) -> Unit
    ): Run {
        if (!config.isConfigured) throw AiError.NoKey

        val executed = mutableListOf<ActionLogEntry>()
        val pending = mutableListOf<PendingAction>()
        val transcript = StringBuilder()
        var pendingSeq = System.currentTimeMillis()

        repeat(MAX_ROUNDS) { round ->
            val user = buildUserMessage(question, appState, knowledge, history, transcript.toString(), round)
            val raw = providerFor(config.provider).complete(config, systemPrompt(), user)
            val step = parseStep(raw)

            if (step.thought.isNotBlank()) onProgress(step.thought)

            val actions = step.actions.take(MAX_ACTIONS_PER_ROUND)

            // مفيش أدوات = الوكيل خلص
            if (actions.isEmpty()) {
                return Run(step.answer ?: fallbackAnswer(raw), executed, pending)
            }

            for (action in actions) {
                val tool = AgentTools.find(action.tool)
                if (tool == null) {
                    transcript.appendLine("[${action.tool}] ✗ أداة مش موجودة")
                    continue
                }
                if (AgentTools.autoRuns(tool.name)) {
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

            // آخر جولة: لو الموديل بعت إجابة معاها، ناخدها
            if (round == MAX_ROUNDS - 1 && step.answer != null) {
                return Run(step.answer, executed, pending)
            }
        }

        // خلصت الجولات من غير إجابة — نطلب واحدة أخيرة من غير أدوات
        val finalRaw = providerFor(config.provider).complete(
            config, systemPrompt(),
            buildUserMessage(question, appState, knowledge, history, transcript.toString(), MAX_ROUNDS, force = true)
        )
        val finalStep = parseStep(finalRaw)
        return Run(finalStep.answer ?: fallbackAnswer(finalRaw), executed, pending)
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
        appendLine("4. أدوات التعديل والحذف **مابتتنفّذش لحد ما المستخدم يوافق**. لو اقترحت واحدة،")
        appendLine("   قول في إجابتك إنها مستنية موافقته — **متقولش إنك عملتها**.")
        appendLine("5. متشغّلش أداة كتبت نتيجتها قبل كده في نفس المحادثة.")
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
        transcript: String,
        round: Int,
        force: Boolean = false
    ): String = buildString {
        appendLine(appState)
        appendLine()
        if (knowledge.isNotBlank()) {
            appendLine("## معرفة المستندات المرتبطة بالسؤال")
            appendLine(knowledge)
            appendLine()
        }
        if (history.isNotBlank()) {
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
