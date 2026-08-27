package com.corewall.qaqc.ai.agent

/**
 * مستوى خطر الأداة — ده اللي بيقرّر تتنفّذ لوحدها ولا تستنى موافقة.
 *
 * القراءة والتنقّل بيتنفّذوا فوراً: أسوأ حالاتهم إنك تشوف شاشة مكنتش
 * عايزها. الكتابة والحذف بيستنّوا ضغطة واحدة، لأن دي سجلّات جودة
 * لمشروع حقيقي — طلب فحص اتحطّ غلط أو ملف اتمسح غلط مش بيترجعوا،
 * وهلوسة واحدة من الموديل تكفي.
 */
enum class ToolRisk {
    READ,
    NAVIGATE,

    /**
     * كتابة في دفتر الوكيل نفسه، مش في بيانات المستخدم.
     *
     * ليه مستوى لوحده مش `WRITE`: الموافقة موجودة عشان سجلّات الجودة —
     * مهمة اتحطّت غلط أو فحص اتغيّر غلط مالهمش رجعة. ملاحظة في الذاكرة
     * مالهاش أي أثر على المشروع، ولو استنّت موافقة الوكيل مش هيقدر يفتكر
     * حاجة غير بضغطة من المستخدم — وده يلغي فايدة الذاكرة أصلاً.
     */
    MEMORY,

    /**
     * بتعمل حاجة حقيقية برّه (نداء مدفوع، ملف جديد) — بس مابتغيّرش أي
     * بيانات للمشروع.
     *
     * بتتنفّذ من غير موافقة لأن المستخدم هو اللي طلبها بنص كلامه
     * ("اعملي صورة") — كارت موافقة على حاجة لسه طالبها بثانية بيبقى
     * سؤال متكرر، مش حماية. والموافقة موجودة أصلاً عشان سجلّات الجودة،
     * وملف صورة جديد مالوش أي أثر عليها.
     */
    GENERATE,

    WRITE,
    DESTRUCTIVE
}

/**
 * أداة يقدر الوكيل ينفّذها جوّه التطبيق.
 *
 * [args] بتتحوّل لنص في البرومبت، فالوصف لازم يكون دقيق —
 * الموديل بيختار الأداة من الوصف ده بس.
 */
data class AgentTool(
    val name: String,
    val risk: ToolRisk,
    val summary: String,
    val args: List<ToolArg> = emptyList()
)

data class ToolArg(
    val name: String,
    val type: String,
    val required: Boolean,
    val about: String
)

private fun arg(name: String, type: String, about: String, required: Boolean = false) =
    ToolArg(name, type, required, about)

/**
 * سجلّ الأدوات. أي أداة جديدة بتتضاف هنا وبتتنفّذ في [AgentExecutor] —
 * الاتنين لازم يفضلوا متطابقين.
 */
object AgentTools {

    val ALL: List<AgentTool> = listOf(

        // ------------------------------------------------ قراءة: الحالة الهندسية
        AgentTool(
            "get_floor_summary", ToolRisk.READ,
            "ملخّص دور كامل: عدد العناصر، حالات الفحص، الفجوات، الفروق عن الدور السابق واللاحق.",
            listOf(arg("level", "string", "كود الدور — لو فاضي بيستخدم الدور الشغّال"))
        ),
        AgentTool(
            "get_element", ToolRisk.READ,
            "تفاصيل عنصر واحد في دور: المدى الشغّال، قيم التسليح، حالة الفحص، الكومنتات.",
            listOf(
                arg("mark", "string", "الكود المرجعي زي T1-W4A", required = true),
                arg("level", "string", "الدور — الافتراضي هو الشغّال")
            )
        ),
        AgentTool(
            "list_elements", ToolRisk.READ,
            "كل عناصر الدور بأكوادها وفئتها وحالة فحصها.",
            listOf(
                arg("level", "string", "الدور"),
                arg("status", "string", "فلتر بحالة الفحص: NONE|WIR_SUBMITTED|APPROVED|CAST|REJECTED")
            )
        ),
        AgentTool(
            "compare_floors", ToolRisk.READ,
            "مقارنة التسليح بين دورين وإظهار كل عنصر اتغيّر وإيه اللي اتغيّر بالظبط.",
            listOf(
                arg("from", "string", "الدور الأول", required = true),
                arg("to", "string", "الدور التاني", required = true),
                arg("mark", "string", "لو عايز عنصر واحد بس")
            )
        ),
        AgentTool(
            "next_floor_changes", ToolRisk.READ,
            "إيه اللي هيتغيّر في التسليح لما تطلع الدور اللي بعد ده مباشرة — الأداة الأساسية لسؤال " +
                "\"هل فيه تعديل في تسليح الدور الجاي؟\".",
            listOf(arg("level", "string", "الدور اللي بتقف عليه"))
        ),
        AgentTool(
            "get_plan_geometry", ToolRisk.READ,
            "هندسة المسقط: كل عنصر بإحداثياته ومقاسه وفئته والاسم المتسمّى بيه — لتحليل الرسمة الرئيسية.",
            listOf(arg("level", "string", "الدور — بيأثر على حالات الفحص المرفقة"))
        ),
        AgentTool(
            "steel_quantity", ToolRisk.READ,
            "حساب حتمي لمساحة الحديد من كولاوت زي T25-200 أو 6T32. استخدمها بدل ما تحسب بنفسك.",
            listOf(arg("callout", "string", "نص الكولاوت", required = true))
        ),
        AgentTool(
            "pour_readiness", ToolRisk.READ,
            "هل الدور جاهز للصبّ؟ بيرجّع الحكم مع الموانع والتحذيرات والعناصر المعنية. " +
                "استخدمها لأي سؤال عن الجاهزية أو \"ينفع نصبّ؟\" — متجمّعش الإجابة بنفسك من أدوات تانية.",
            listOf(arg("level", "string", "الدور"))
        ),
        AgentTool(
            "get_bar_counts", ToolRisk.READ,
            "عدّ الأسياخ المسجّل من الموقع في دور، ومقارنته بالرسمة.",
            listOf(arg("level", "string", "الدور"))
        ),

        // ------------------------------------------------ قراءة: الملفات والمعرفة
        AgentTool(
            "list_files", ToolRisk.READ,
            "محتويات مجلد ملفات الدور: الملفات والمجلدات بأحجامها وتواريخها.",
            listOf(
                arg("level", "string", "الدور"),
                arg("path", "string", "مسار فرعي جوّه مجلد الدور — فاضي = الجذر")
            )
        ),
        AgentTool(
            "read_file", ToolRisk.READ,
            "قراءة محتوى ملف نصي أو جدول (txt/csv/xlsx/docx). للـPDF والصور استخدم list_documents.",
            listOf(arg("path", "string", "المسار الكامل للملف", required = true))
        ),
        AgentTool(
            "list_documents", ToolRisk.READ,
            "المستندات المتحلّلة بالـAI وحالتها والملخّص بتاعها.",
            listOf(arg("level", "string", "الدور"))
        ),
        AgentTool(
            "get_document_facts", ToolRisk.READ,
            "الحقائق المستخرجة من مستند محلّل (بار ماركات، أقطار، كميات…).",
            listOf(arg("documentId", "number", "رقم المستند", required = true))
        ),
        AgentTool(
            "list_photos", ToolRisk.READ,
            "صور الموقع المسجّلة في دور مع تعليقاتها.",
            listOf(arg("level", "string", "الدور"))
        ),
        AgentTool(
            "search", ToolRisk.READ,
            "بحث شامل في الملاحظات والمهام والكومنتات والمستندات والحقائق وأسماء الملفات.",
            listOf(arg("query", "string", "كلمة أو كود للبحث", required = true))
        ),
        AgentTool(
            "list_tasks", ToolRisk.READ,
            "مهام الدور وحالتها.",
            listOf(arg("level", "string", "الدور"))
        ),
        AgentTool(
            "list_notes", ToolRisk.READ,
            "ملاحظات الدور (عناوينها وبداية محتواها).",
            listOf(arg("level", "string", "الدور"))
        ),
        // ------------------------------------------------ قراءة: الذاكرة
        AgentTool(
            "search_chat", ToolRisk.READ,
            "بحث في **كل** المحادثة السابقة مع المستخدم في الدور ده — مش آخر رسايل بس. " +
                "استخدمها أول ما المستخدم يشاور على حاجة اتقالت قبل كده (\"زي ما اتفقنا\"، " +
                "\"الرقم اللي قلتهولك\"، \"المهمة اللي عملناها\") بدل ما تقول إنك مش فاكر.",
            listOf(arg("query", "string", "كلمة أو كود للبحث في المحادثة", required = true))
        ),
        AgentTool(
            "remember", ToolRisk.MEMORY,
            "حفظ حقيقة صغيرة تفضل معاك في كل المحادثات الجاية في الدور ده: قرار اتاخد، " +
                "تفضيل للمستخدم، رقم مرجعي. المفتاح الواحد بيتكتب فوق القديم. " +
                "احفظ اللي هيفرق بعدين بس — مش كل تفصيلة.",
            listOf(
                arg("key", "string", "اسم قصير للمعلومة، زي \"مقاس فتحة البوم\"", required = true),
                arg("value", "string", "المعلومة نفسها في سطر", required = true)
            )
        ),

        AgentTool(
            "get_attendance", ToolRisk.READ,
            "بيانات العمالة: الحضور اليومي وإجماليات الفترة.",
            listOf(
                arg("level", "string", "الدور"),
                arg("days", "number", "آخر كام يوم — الافتراضي 14")
            )
        ),

        // ------------------------------------------------ توليد صور
        AgentTool(
            "make_image", ToolRisk.GENERATE,
            "توليد صورة من وصف إنت بتكتبه، وحفظها في ملفات الدور. " +
                "استخدمها لما المستخدم يطلب صورة أو رسمة أو مخطط توضيحي. " +
                "**إنت اللي بتكتب الوصف** — اكتبه بالإنجليزي، مفصّل، وحطّ فيه الأرقام " +
                "والأسماء الحقيقية اللي طلعت من الأدوات، مش وصف عام. " +
                "الملف بيرجع بمساره الكامل: حطّه في بلوك IMAGES في إجابتك عشان يتعرض.",
            listOf(
                arg(
                    "prompt", "string",
                    "وصف الصورة بالإنجليزي — تفصيلي، وفيه البيانات الحقيقية",
                    required = true
                ),
                arg("caption", "string", "تعليق قصير بالعربي يتعرض تحت الصورة"),
                arg("level", "string", "الدور اللي الصورة تتحفظ في ملفاته")
            )
        ),

        // ------------------------------------------------ تنقّل (بيتنفّذ فوراً)
        AgentTool(
            "open_screen", ToolRisk.NAVIGATE,
            "فتح شاشة للمستخدم. القيم: HOME|PLAN|SCHEDULE|ATTENTION|COUNTING|FILES|TASKS|NOTES|" +
                "PHOTOS|POUR|MANPOWER|KNOWLEDGE|REPORTS|DASHBOARD|SETTINGS",
            listOf(arg("screen", "string", "اسم الشاشة", required = true))
        ),
        AgentTool(
            "set_level", ToolRisk.NAVIGATE,
            "تغيير الدور الشغّال. استخدمها لو المستخدم طلب ينتقل لدور — مش عشان تقرا بيانات دور تاني.",
            listOf(arg("level", "string", "كود الدور", required = true))
        ),
        AgentTool(
            "open_file", ToolRisk.NAVIGATE,
            "فتح ملف للمستخدم (PDF في العارض، صورة في العارض، غير كده بتطبيق خارجي).",
            listOf(arg("path", "string", "المسار الكامل", required = true))
        ),

        // ------------------------------------------------ كتابة (محتاجة موافقة)
        AgentTool(
            "add_task", ToolRisk.WRITE,
            "إضافة مهمة جديدة. لدور واحد، أو لكل الأدوار مرة واحدة بـ`allLevels: true` — " +
                "استخدم `allLevels` لأي طلب من نوع \"في كل دور\" بدل ما تبعت الأداة مرة لكل دور " +
                "(الأدوار ممكن تكون ٤٨، وده مش هينفع).",
            listOf(
                arg("title", "string", "نص المهمة", required = true),
                arg("level", "string", "الدور — بيتجاهَل لو allLevels = true"),
                arg("allLevels", "boolean", "true = المهمة تتضاف في كل أدوار المشروع")
            )
        ),
        AgentTool(
            "complete_task", ToolRisk.WRITE,
            "تعليم مهمة قائمة كمكتملة. استخدم رقم المهمة الذي رجع من list_tasks.",
            listOf(arg("id", "number", "رقم المهمة", required = true))
        ),
        AgentTool(
            "add_note", ToolRisk.WRITE,
            "إنشاء ملاحظة جديدة ضمن ذاكرة الدور.",
            listOf(
                arg("title", "string", "عنوان الملاحظة", required = true),
                arg("body", "string", "نص الملاحظة", required = true),
                arg("level", "string", "الدور")
            )
        ),
        AgentTool(
            "create_document", ToolRisk.WRITE,
            "إنشاء مسودة تقرير داخل استوديو الإنشاء. القوالب المتاحة: QUALITY أو TAKEOFF أو DAILY أو MEETING أو LETTER.",
            listOf(
                arg("title", "string", "عنوان التقرير", required = true),
                arg("template", "string", "نوع القالب: QUALITY|TAKEOFF|DAILY|MEETING|LETTER", required = true),
                arg("level", "string", "الدور")
            )
        ),
        AgentTool(
            "export_document_pdf", ToolRisk.WRITE,
            "تصدير مسودة تقرير موجودة إلى PDF. استخدم رقم المستند الذي رجع من create_document.",
            listOf(arg("documentId", "number", "رقم مسودة التقرير", required = true))
        ),
        AgentTool(
            "add_comment", ToolRisk.WRITE,
            "إضافة كومنت على عنصر في الدور.",
            listOf(
                arg("mark", "string", "كود العنصر", required = true),
                arg("text", "string", "نص الكومنت", required = true)
            )
        ),
        AgentTool(
            "set_inspection", ToolRisk.WRITE,
            "تغيير حالة فحص عنصر. دي سجلّ جودة رسمي — اقترحها بس لما المستخدم يطلب صراحة.",
            listOf(
                arg("mark", "string", "كود العنصر", required = true),
                arg("status", "string", "NONE|WIR_SUBMITTED|APPROVED|CAST|REJECTED", required = true)
            )
        ),
        AgentTool(
            "create_folder", ToolRisk.WRITE,
            "إنشاء مجلد جوّه ملفات الدور.",
            listOf(
                arg("name", "string", "اسم المجلد", required = true),
                arg("path", "string", "المسار الأب — فاضي = الجذر")
            )
        ),
        AgentTool(
            "rename_file", ToolRisk.WRITE,
            "إعادة تسمية ملف أو مجلد.",
            listOf(
                arg("path", "string", "المسار الحالي", required = true),
                arg("newName", "string", "الاسم الجديد", required = true)
            )
        ),
        AgentTool(
            "delete_file", ToolRisk.DESTRUCTIVE,
            "حذف ملف أو مجلد نهائياً. مفيش تراجع.",
            listOf(arg("path", "string", "المسار الكامل", required = true))
        ),
        AgentTool(
            "delete_task", ToolRisk.DESTRUCTIVE,
            "حذف مهمة.",
            listOf(arg("id", "number", "رقم المهمة", required = true))
        )
    )

    private val byName = ALL.associateBy { it.name }

    fun find(name: String): AgentTool? = byName[name.trim()]

    /** هل الأداة بتتنفّذ من غير ما نستأذن؟ */
    fun autoRuns(name: String): Boolean =
        find(name)?.risk?.let {
            it == ToolRisk.READ || it == ToolRisk.NAVIGATE ||
                it == ToolRisk.MEMORY || it == ToolRisk.GENERATE
        } ?: false

    /**
     * جملة بالعربي عن الأداة اللي شغّالة دلوقتي.
     *
     * الشاشة كانت بتقول "بيشتغل…" لدقيقة كاملة، وانتظار من غير أي إشارة
     * تقدّم بيتقري كتعليق مش كشغل. الاسم التقني مش هيفيد المستخدم، فده
     * وصف بلغته.
     */
    fun progressLabel(name: String): String = when (name) {
        "get_floor_summary" -> "بيراجع ملخّص الدور…"
        "get_element" -> "بيقرا بيانات العنصر…"
        "list_elements" -> "بيعدّ عناصر الدور…"
        "compare_floors", "next_floor_changes" -> "بيقارن التسليح بين الأدوار…"
        "get_plan_geometry" -> "بيقرا المسقط…"
        "steel_quantity" -> "بيحسب مساحة الحديد…"
        "pour_readiness" -> "بيراجع جاهزية الصبّ…"
        "get_bar_counts" -> "بيراجع عدّ الأسياخ…"
        "list_files", "read_file" -> "بيقرا الملفات…"
        "list_documents", "get_document_facts" -> "بيراجع المستندات المحلّلة…"
        "list_photos" -> "بيراجع صور الموقع…"
        "search" -> "بيدوّر في بيانات الدور…"
        "search_chat" -> "بيراجع اللي اتقال قبل كده…"
        "remember" -> "بيسجّل المعلومة في الذاكرة…"
        "make_image" -> "بيرسم الصورة…"
        "list_tasks", "list_notes" -> "بيراجع المهام والملاحظات…"
        "get_attendance" -> "بيراجع بيانات العمالة…"
        "open_screen", "set_level", "open_file" -> "بيفتحلك الشاشة…"
        else -> "بينفّذ $name…"
    }

    /** وصف الأدوات زي ما بيتبعت للموديل. */
    fun catalogue(): String = buildString {
        ToolRisk.entries.forEach { risk ->
            val group = ALL.filter { it.risk == risk }
            if (group.isEmpty()) return@forEach
            appendLine(
                when (risk) {
                    ToolRisk.READ -> "### أدوات قراءة (بتتنفّذ فوراً)"
                    ToolRisk.NAVIGATE -> "### أدوات تنقّل (بتتنفّذ فوراً)"
                    ToolRisk.MEMORY -> "### ذاكرة (بتتنفّذ فوراً)"
                    ToolRisk.GENERATE -> "### توليد (بتتنفّذ فوراً)"
                    ToolRisk.WRITE -> "### أدوات تعديل (محتاجة موافقة المستخدم)"
                    ToolRisk.DESTRUCTIVE -> "### أدوات حذف (محتاجة موافقة المستخدم — مفيش تراجع)"
                }
            )
            group.forEach { t ->
                append("- `${t.name}`")
                if (t.args.isNotEmpty()) {
                    append("(")
                    append(t.args.joinToString(", ") { a ->
                        "${a.name}: ${a.type}${if (a.required) "" else "?"}"
                    })
                    append(")")
                }
                appendLine(" — ${t.summary}")
                t.args.filter { it.about.isNotBlank() }.forEach { a ->
                    appendLine("    · ${a.name}: ${a.about}")
                }
            }
            appendLine()
        }
    }.trim()
}
