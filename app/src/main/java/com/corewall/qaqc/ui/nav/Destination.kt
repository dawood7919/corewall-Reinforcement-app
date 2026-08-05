package com.corewall.qaqc.ui.nav

import androidx.compose.runtime.Immutable

/**
 * كل وجهة في التطبيق — متعرّفة **مرّة واحدة**، بعنوانها وسلوكها.
 *
 * ليه ده مهم: النظام القديم كان enum + `when` مكتوب بالإيد في الـActivity،
 * فإضافة شاشة كانت بتتطلب تعديل في ٣ أماكن ومفيش حاجة بتتأكد إن الشاشة ليها
 * مدخل أصلاً. النتيجة كانت **٥ شاشات من ٣١ مالهاش أي طريق يوصلها** — منهم
 * كاشف الفجوات وتقرير عدّ الحديد، وهما من صميم المنتج.
 *
 * دلوقتي الوجهة بتتعرّف هنا، وشجرة التنقّل في [NavGraph] بتضمن إن كل وجهة
 * ليها مدخل — والاختبار في [NavGraph.orphans] بيفشل لو حصل العكس.
 */
@Immutable
sealed interface Dest {

    /** العنوان اللي بيظهر في الشريط العلوي. */
    val title: String

    /**
     * شاشة ملء الشاشة بتخفي شرائط التنقّل والمساعد الطايف (عارض/محرّر).
     * الباقي بيفضل جوّه هيكل التطبيق.
     */
    val fullScreen: Boolean get() = false

    /** جذر — تبويب في الشريط السفلي. الجذور دايماً ٥ وثابتة. */
    @Immutable
    sealed interface Root : Dest

    // ───────────────────────────── الجذور الخمسة
    // الترتيب بيتبع تسلسل القرار: إيه حالة الدور؟ → فين المشكلة؟ → أقدر أصبّ؟

    /** حالة الدور النهاردة — الشاشة اللي بتفتح عليها. */
    data object Today : Root {
        override val title = "اليوم"
    }

    /** سطح الشغل — المسقط التفاعلي بعدساته. */
    data object Plan : Root {
        override val title = "المسقط"
    }

    /** فين المشكلة — الفجوات، الفروق، العدّ، جاهزية الصبّ. */
    data object Checks : Root {
        override val title = "الفحص"
    }

    /** ملفات · مهام · ملاحظات · صور — كلها معزولة بالدور. */
    data object Data : Root {
        override val title = "الداتا"
    }

    /** المساعد الهندسي. */
    data object Assistant : Root {
        override val title = "المساعد"
    }

    // ───────────────────────────── وجهات الفحص

    data object PourReadiness : Dest {
        override val title = "جاهزية الصبّ"
    }

    /** كاشف الفجوات — كان مبنيّ ومحدش يقدر يوصله. */
    data object Gaps : Dest {
        override val title = "الفجوات في الجدول"
    }

    /** تقرير عدّ الحديد الرأسي — كان مبنيّ ومحدش يقدر يوصله. */
    data object CountingReport : Dest {
        override val title = "تقرير العدّ"
    }

    /** أدوات التحليل — كانت مبنيّة ومحدش يقدر يوصلها. */
    data object Tools : Dest {
        override val title = "أدوات التحليل"
    }

    /** تحليل الدور بالذكاء الاصطناعي — كان مرسوم بس من غير أي مدخل. */
    data object FloorAnalysis : Dest {
        override val title = "تحليل الدور"
    }

    // ───────────────────────────── وجهات الداتا والمشروع

    data object FloorNotes : Dest {
        override val title = "ملاحظات الدور"
    }

    data object SitePhotos : Dest {
        override val title = "صور الموقع"
    }

    data object Manpower : Dest {
        override val title = "العمالة"
    }

    // ───────────────────────────── وجهات المساعد

    data object FloorKnowledge : Dest {
        override val title = "ذاكرة الدور"
    }

    data object ProjectKnowledge : Dest {
        override val title = "معرفة المشروع"
    }

    data object DocumentGen : Dest {
        override val title = "توليد المستندات"
    }

    data object AiSettings : Dest {
        override val title = "إعدادات المساعد"
    }

    // ───────────────────────────── النظام

    data object Notifications : Dest {
        override val title = "الإشعارات"
    }

    /** إعدادات **واحدة**. قبل كده كان فيه شاشتين مختلفتين بنفس الاسم. */
    data object Settings : Dest {
        override val title = "الإعدادات"
    }

    /**
     * حالة البيانات. اسمها كان "المزامنة" وهي مالهاش سيرفر — الاسم نفسه كان
     * بيوعد بحاجة مش موجودة.
     */
    data object Sync : Dest {
        override val title = "حالة البيانات"
    }

    data object About : Dest {
        override val title = "عن التطبيق"
    }

    // ───────────────────────────── عارضات ملء الشاشة

    @Immutable
    data class PdfViewer(val path: String) : Dest {
        override val title = "عارض PDF"
        override val fullScreen = true
    }

    @Immutable
    data class CadViewer(val path: String) : Dest {
        override val title = "عارض المخطط"
        override val fullScreen = true
    }

    @Immutable
    data class ImageViewer(val path: String) : Dest {
        override val title = "صورة"
        override val fullScreen = true
    }

    data object NoteEditor : Dest {
        override val title = "تحرير ملاحظة"
        override val fullScreen = true
    }

    @Immutable
    data class AttendanceFile(val id: Long) : Dest {
        override val title = "كشف الحضور"
        override val fullScreen = true
    }
}

/** أقسام شاشة الداتا — تبويب داخلي، مش تبويب في شريط التنقّل. */
enum class DataSection(val label: String) {
    FILES("الملفات"),
    TASKS("المهام"),
    NOTES("الملاحظات"),
    PHOTOS("الصور")
}

/** أقسام شاشة العمالة — تبويب داخلي كمان. */
enum class ManpowerSection(val label: String) {
    ATTENDANCE("الحضور"),
    REPORTS("التقارير"),
    STATISTICS("الإحصائيات")
}

/**
 * شجرة التنقّل — بتربط كل وجهة بمدخلها.
 *
 * مش توثيق: [orphans] بتتحسب فعلياً، والاختبار بيفشل لو وجهة اتضافت من غير
 * ما حد يقدر يوصلها. ده بالظبط النوع من الأخطاء اللي خلّى ٥ شاشات ميتة.
 */
object NavGraph {

    val roots: List<Dest.Root> = listOf(
        Dest.Today, Dest.Plan, Dest.Checks, Dest.Data, Dest.Assistant
    )

    /** كل الوجهات اللي مش جذور — ولازم كل واحدة تظهر في [entryPoints]. */
    val pushable: List<Dest> = listOf(
        Dest.PourReadiness, Dest.Gaps, Dest.CountingReport, Dest.Tools, Dest.FloorAnalysis,
        Dest.FloorNotes, Dest.SitePhotos, Dest.Manpower,
        Dest.FloorKnowledge, Dest.ProjectKnowledge, Dest.DocumentGen, Dest.AiSettings,
        Dest.Notifications, Dest.Settings, Dest.Sync, Dest.About,
        Dest.NoteEditor
    )

    /**
     * مين بيفتح مين. المفتاح الوجهة، والقيمة الأماكن اللي فيها مدخل ليها.
     * الوجهات اللي بتاخد بيانات (عارض PDF/صورة/ملف حضور) بتتفتح من محتوى
     * ديناميكي فمش مدرجة هنا.
     */
    val entryPoints: Map<Dest, List<Dest>> = mapOf(
        Dest.PourReadiness to listOf(Dest.Today, Dest.Checks),
        Dest.Gaps to listOf(Dest.Checks),
        Dest.CountingReport to listOf(Dest.Checks),
        Dest.Tools to listOf(Dest.Checks),
        Dest.FloorAnalysis to listOf(Dest.Checks, Dest.Assistant),
        Dest.FloorNotes to listOf(Dest.Today, Dest.Data),
        Dest.SitePhotos to listOf(Dest.Data),
        Dest.Manpower to listOf(Dest.Today),
        Dest.FloorKnowledge to listOf(Dest.Assistant),
        Dest.ProjectKnowledge to listOf(Dest.Assistant),
        Dest.DocumentGen to listOf(Dest.Assistant),
        Dest.AiSettings to listOf(Dest.Assistant, Dest.Settings),
        Dest.Notifications to listOf(Dest.Today),
        Dest.Settings to listOf(Dest.Today),
        Dest.Sync to listOf(Dest.Settings),
        Dest.About to listOf(Dest.Settings),
        Dest.NoteEditor to listOf(Dest.Data)
    )

    /** أي وجهة مالهاش مدخل. المفروض تفضل فاضية للأبد. */
    val orphans: List<Dest> get() = pushable.filter { entryPoints[it].isNullOrEmpty() }
}
