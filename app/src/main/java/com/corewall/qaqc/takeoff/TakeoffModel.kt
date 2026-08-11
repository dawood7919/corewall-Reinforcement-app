package com.corewall.qaqc.takeoff

/**
 * نموذج الحصر — **كوتلن خالص، من غير أي أندرويد ولا Compose**.
 *
 * الملف ده والملف اللي جنبه ([TakeoffMath]) مايستوردوش حاجة من أندرويد
 * عن قصد، عشان كل الحسابات تتّست على الـJVM من غير محاكي. ده مطلب صريح
 * في المواصفة (§15) وهو نفس السبب اللي خلّى محرّك التسليح عندنا متثبّت
 * باختبارات: الحساب اللي بيتّست مرة بيفضل صح.
 *
 * ═══════════════════════════════════════════════════════════════
 * ## الإحداثيات — أخطر قرار في النظام كله
 *
 * المواصفة بتحذّر (§2) من تخزين إحداثيات **الشاشة**: أول ما المستخدم
 * يكبّر أو يمرّر، المساحة المحسوبة بتتغيّر. وده باج حقيقي حصل في نسخة
 * الويب.
 *
 * الحل عندهم كان "page-space" = بكسل الصفحة بعد الرسترة عند معامل ثابت
 * (`RS = 2`). إحنا بنخزّن **أنسب من كده**: إحداثيات **منسّبة ٠..١** جوّه
 * الصفحة — نفس اللي تعليقات الـPDF في CoreWall متخزّنة بيه أصلاً.
 *
 * الفرق مش تجميلي:
 *
 * • page-space مربوط بمعامل الرسترة. لو غيّرت `RS` يوم، كل القياسات
 *   القديمة تبقى غلط **بصمت** — والمواصفة نفسها بتقول "reuse the exact
 *   same constant or the presets will be silently wrong".
 * • النسبي ٠..١ مستقل عن التكبير **وعن دقة الرندر كمان**. مافيش ثابت
 *   يقدر يخرب البيانات القديمة.
 *
 * التحويل للواقع بيعدّي من [PageGeometry]: مقاس الصفحة بالنقط (من الملف
 * نفسه) × المعايرة. مافيش رسترة في المعادلة أصلاً.
 */

/**
 * نقطة منسّبة جوّه صفحة: ٠ = الحافة الشمال/فوق، ١ = اليمين/تحت.
 *
 * `Double` مش `Float`: الفرق بيتراكم في الجمع التلسكوبي بتاع الشوليس على
 * مضلّع بمية نقطة، ومساحة الحصر رقم بيتحاسب عليه فلوس.
 */
data class TakeoffPoint(val x: Double, val y: Double)

/**
 * الجسر بين الصفحة والواقع.
 *
 * [metresPerPoint] هو نفسه مفهوم `unitsPerPixel` في المواصفة، بس بالنقطة
 * بدل البكسل — والنقطة وحدة جوّه ملف الـPDF نفسه (١/٧٢ بوصة)، مش ناتج
 * رسترة. `Scale.unitsPerPoint` الموجود في المحرّك بيتحوّل لهنا بضربة
 * واحدة عند حدود الواجهة.
 */
data class PageGeometry(
    val widthPt: Double,
    val heightPt: Double,
    val metresPerPoint: Double
) {
    val calibrated: Boolean
        get() = metresPerPoint.isFinite() && metresPerPoint > 0.0 &&
            widthPt > 0.0 && heightPt > 0.0
}

/**
 * وضع العرض — تكبير وإزاحة.
 *
 * موجود هنا عشان دالتين بس: [TakeoffMath.pageToScreen] و
 * [TakeoffMath.screenToPage]. **دول الوحيدين المسموح لهم يعرفوا حاجة عن
 * التكبير.** أي حساب كمية بيلمس `ViewTransform` يبقى غلط بحكم التعريف —
 * ده الشرط اللي بيمنع باج المواصفة رقم ١ من إنه يرجع.
 */
data class ViewTransform(
    val zoom: Double,
    val offsetX: Double,
    val offsetY: Double
)

enum class TakeoffTool {
    /** مضلّع مقفول — الكمية بالمتر المربّع. */
    AREA,

    /** خط مكسّر — الكمية بالمتر الطولي. */
    LENGTH,

    /** علامات — الكمية عدد. */
    COUNT,

    /**
     * خصم. **مش كمية سالبة عايمة** — دايماً مربوط بمساحة أب واحدة
     * عن طريق [TakeoffItem.parentId]، وبيتخصم من كميّتها هي بس.
     */
    DEDUCT,

    /** مضلّع × سمك مُدخَل يدويًا — الكمية بالمتر المكعّب. */
    VOLUME,

    /** علامات × أبعاد عمود واحد مُدخَلة يدويًا — الكمية بالمتر المكعّب. */
    COLUMN;

    /** الأدوات اللي بتتجمّع كمضلّعات إضافية بدل خطوط. */
    val isAreaLike: Boolean get() = this == AREA || this == DEDUCT || this == VOLUME
}

/**
 * بند حصر واحد.
 *
 * [extraRings] و[extraSegments] هما "الرسم المستمر" من §6 في المواصفة:
 * المستخدم بيرسم أوضة على شكل L كأربع أشكال ورا بعض، والأربعة بيتجمّعوا
 * في **بند واحد** بكمية واحدة بدل أربع بنود. ده مش تحسين شكلي — ده الفرق
 * بين أداة بيستحملها حاصر كمّيات وأداة بتغيّظه.
 */
data class TakeoffItem(
    val id: String,
    val drawingPath: String,
    val page: Int,
    val tool: TakeoffTool,
    val name: String,
    /** معرّف [TakeoffCategory.id] — `null` يعني "بلا تصنيف". */
    val categoryId: String?,
    val colorArgb: Long,
    val visible: Boolean = true,

    /** الشكل الأساسي. مضلّع (AREA/DEDUCT)، خط (LENGTH)، أو علامات (COUNT). */
    val verts: List<TakeoffPoint> = emptyList(),

    /** مضلّعات إضافية بتتجمع مع الأساسي — AREA بس. */
    val extraRings: List<List<TakeoffPoint>> = emptyList(),

    /** خطوط إضافية **مش لازم تكون متصلة** — LENGTH بس (وزرة مقطوعة بباب). */
    val extraSegments: List<List<TakeoffPoint>> = emptyList(),

    /** الأب اللي الخصم ده بيتقطع منه. لـ[TakeoffTool.DEDUCT] بس. */
    val parentId: String? = null,

    /** مجموعة فرعية جوّه الفئة — اختيارية. */
    val groupId: String? = null,

    /** وسم مكان حر: "الدور الأرضي — بلوك A". */
    val zone: String = "",

    /** نسبة التنفيذ في الموقع ٠..١٠٠. */
    val progressPercent: Double? = null,

    /** سعر الوحدة لو مختلف عن افتراضي الفئة. `null` = استخدم فئة الأب. */
    val rateOverride: Double? = null,

    /** السمك بالمتر — [TakeoffTool.VOLUME] بس. */
    val thickness: Double? = null,

    /** أبعاد عمود واحد بالمتر — [TakeoffTool.COLUMN] بس. */
    val colLength: Double? = null,
    val colWidth: Double? = null,
    val colHeight: Double? = null
)

/**
 * فئة حصر — مستوى القسم كله، بتتشارك بين كل رسماته.
 *
 * [rate]/[wastePct] بيتحسبوا live على أي بند تابع، مش بيتخزّنوا في
 * البند نفسه — تغيير سعر الفئة بيغيّر كل التكلفة المعروضة فورًا.
 */
data class TakeoffCategory(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val rate: Double = 0.0,
    val wastePct: Double = 0.0
)

/** مجموعة جوّه فئة. */
data class TakeoffGroup(
    val id: String,
    val categoryId: String,
    val name: String
)
