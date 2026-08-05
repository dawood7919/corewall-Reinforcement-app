package com.corewall.qaqc.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * توكنات التصميم — المصدر **الوحيد** لأي مقاس في التطبيق.
 *
 * القاعدة المفروضة: ممنوع أي `.dp` خام جوّه شاشة. لو محتاج مقاس مش موجود
 * هنا، القرار الصح إنك تستخدم أقرب خطوة — مش إنك تخترع رقم جديد.
 * الكلام ده مش شكليات: القياس القديم كان فيه ٦٣ قيمة `.dp` مختلفة، ٣٣ منهم
 * برّه شبكة الـ4/8، وده اللي كان بيخلّي الشاشات تحس إنها "مركّبة" مش "مصمّمة".
 */

/** شبكة المسافات — 4/8 rhythm. سبع خطوات، مفيش غيرهم. */
object Space {
    /** شعرة — للفواصل والحدود بس، مش للمسافات. */
    val hair = 1.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp

    /** الهامش الجانبي القياسي لكل شاشة. */
    val screen = lg

    /** المسافة بين كروت في نفس القايمة. */
    val stack = md

    /** مسافة أمان تحت آخر عنصر عشان شريط التنقّل ما يغطّيهوش. */
    val bottomInset = 96.dp
}

/** أنصاف الأقطار — أربع خطوات + كبسولة. */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp

    val shapeSm = RoundedCornerShape(sm)
    val shapeMd = RoundedCornerShape(md)
    val shapeLg = RoundedCornerShape(lg)
    val shapeXl = RoundedCornerShape(xl)
    val pill = RoundedCornerShape(percent = 50)

    /** أعلى الشيت بس — الأسفل ملزوق في حافة الشاشة. */
    val sheet = RoundedCornerShape(topStart = xl, topEnd = xl)
}

/** مقاسات الأيقونات — أربعة، مفيش غيرهم. */
object IconSize {
    /** جوّه شيب أو كشرط بجانب نص صغير. */
    val sm = 16.dp
    /** الافتراضي في الصفوف والأزرار. */
    val md = 20.dp
    /** التنقّل والأفعال الأساسية. */
    val lg = 24.dp
    /** الحالات الفاضية والأفاتار. */
    val xl = 32.dp
}

/** مقاسات ثابتة للمس والعناصر المتكررة. */
object Sizes {
    /**
     * أقل مساحة لمس مسموح بيها. Material بيقول 48، والـskill بيقول 44 —
     * الأصرم بيكسب.
     */
    val touch = 48.dp

    /** الأفاتار/الأيقونة داخل دايرة في صف. */
    val avatarSm = 32.dp
    val avatarMd = 40.dp
    val avatarLg = 56.dp

    /** ارتفاع الشريط العلوي المخصص. */
    val topBar = 56.dp

    /**
     * ارتفاع خانة جوّه مبدّل مقسّم. أقل من [touch] عن قصد: المبدّل نفسه
     * ارتفاعه كافي، والقاعدة بتتحقّق على الصف مش على كل خانة جوّاه.
     */
    val control = 40.dp

    /** سُمك مؤشّر التقدّم الخطي. */
    val bar = 8.dp

    /** عرض الشريط الجانبي الملوّن على الكروت المميّزة. */
    val accentEdge = 4.dp

    /** أقل عرض لخانة دور في شبكة المبدّل. */
    val levelCell = 72.dp

    /** أقصى ارتفاع لشبكة داخل شيت — عشان الشيت ما ياخدش الشاشة كلها. */
    val sheetGridMax = 400.dp

    /** أقصى عرض لعمود محتوى على تابلت — النص ما يمدّش أكتر من المريح للقراية. */
    val readableMax = 720.dp

    /** عرض عمود التنقّل الجانبي على الشاشات العريضة. */
    val rail = 88.dp
}

/** سُمك الحدود والفواصل. */
object Stroke {
    val hair = 1.dp
    val thick = 2.dp
}

/**
 * الحركة — كلها بين 150 و 300 مللي ثانية زي ما الـskill بيفرض.
 * القاعدة: الحركة بتشرح تغيّر حالة. لو مش بتشرح حاجة، متتحركش.
 * والخروج أسرع من الدخول دايماً.
 */
object Motion {
    const val fast = 150
    const val base = 200
    const val slow = 280

    /** دخول — يبدأ سريع ويهدى (بيحس بإنه "وصل"). */
    val enterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** خروج — يبدأ فوراً (مالوش لزمة يتعمّل عليه). */
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** قياسي للحالات والألوان. */
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(slow, easing = enterEasing)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(fast, easing = exitEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(base, easing = standardEasing)
}

/** الارتفاع — الكروت بتستخدم حدود مش ظل. الظل للطبقات الطايفة بس. */
object Elevation {
    val flat = 0.dp
    val raised = 3.dp
    val floating = 6.dp
    val overlay = 12.dp
}
