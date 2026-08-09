package com.corewall.qaqc.ui.design

import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/**
 * لغة الحركة الموحّدة.
 *
 * القاعدة الحاكمة: **الحركة بتشرح تغيّر، مش بتزوّق شاشة**. كل حركة هنا
 * ليها وظيفة — تقول للمستخدم راح فين، ولا يرجع منين، ولا إن ضغطته وصلت.
 * اللي مالوش وظيفة من دول اتشال.
 *
 * والمدد قصيرة عن قصد. التطبيق ده بيتفتح ٥٠ مرة في اليوم على سقالة —
 * انتقال ٤٠٠ مللي "أنيق" أول مرة بيبقى ضريبة بعد عشر مرات.
 */

/**
 * هل المستخدم طالب تقليل الحركة؟
 *
 * أندرويد مافيهوش علم مباشر زي iOS، لكن إطفاء مقياس الحركة من خيارات
 * المطوّر أو إعدادات إمكانية الوصول بيصفّر `ANIMATOR_DURATION_SCALE` —
 * وده الإشارة اللي كل تطبيق محترم بيقراها.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun ProvideMotionPreferences(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/**
 * انتقالات الشاشات.
 *
 * الاتجاه بيتحدّد بعمق المكدّس مش بنوع الشاشة: الدخول لعمق أكبر بيدخل من
 * الجنب، والرجوع بيطلع من نفس الجنب بالعكس. ده اللي بيدّي إحساس بالمكان —
 * المستخدم بيفتكر إنه "جوّه" حاجة وإنه "طلع" منها.
 *
 * الإزاحة صغيرة (١٢٪ من العرض) عن قصد: الانزلاق الكامل بياخد وقت وبيبان
 * بطيء، والانزلاق الخفيف بيوصّل الاتجاه من غير انتظار.
 */
object ScreenMotion {

    /** انتقال بين وجهات على نفس المستوى (تبديل تبويب) — تلاشي بس. */
    fun lateral(reduced: Boolean): ContentTransform =
        if (reduced) {
            fadeIn(tween(Motion.fast)) togetherWith fadeOut(tween(Motion.fast))
        } else {
            fadeIn(tween(Motion.base, easing = Motion.enterEasing)) +
                scaleIn(tween(Motion.base, easing = Motion.enterEasing), initialScale = LATERAL_SCALE) togetherWith
                fadeOut(tween(Motion.fast, easing = Motion.exitEasing))
        }

    /** دخول لعمق أكبر (فتح تفاصيل). */
    fun forward(scope: AnimatedContentTransitionScope<*>, reduced: Boolean): ContentTransform =
        if (reduced) lateral(true)
        else with(scope) {
            (slideInHorizontally(tween(Motion.base, easing = Motion.enterEasing)) { it / SLIDE_DIVISOR } +
                fadeIn(tween(Motion.base, easing = Motion.enterEasing))) togetherWith
                (slideOutHorizontally(tween(Motion.base, easing = Motion.exitEasing)) { -it / EXIT_DIVISOR } +
                    fadeOut(tween(Motion.fast, easing = Motion.exitEasing)))
        }

    /** رجوع — نفس الحركة بالعكس بالظبط. */
    fun backward(scope: AnimatedContentTransitionScope<*>, reduced: Boolean): ContentTransform =
        if (reduced) lateral(true)
        else with(scope) {
            (slideInHorizontally(tween(Motion.base, easing = Motion.enterEasing)) { -it / SLIDE_DIVISOR } +
                fadeIn(tween(Motion.base, easing = Motion.enterEasing))) togetherWith
                (slideOutHorizontally(tween(Motion.base, easing = Motion.exitEasing)) { it / EXIT_DIVISOR } +
                    fadeOut(tween(Motion.fast, easing = Motion.exitEasing)))
        }

    /** مودال/حوار — يطلع من مركزه. */
    fun modalEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(Motion.fast))
        else fadeIn(tween(Motion.fast)) +
            scaleIn(tween(Motion.base, easing = Motion.enterEasing), initialScale = MODAL_SCALE)

    fun modalExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(Motion.fast))
        else fadeOut(tween(Motion.fast, easing = Motion.exitEasing)) +
            scaleOut(tween(Motion.fast, easing = Motion.exitEasing), targetScale = MODAL_SCALE)

    /** ١٢٪ من العرض تقريباً. */
    private const val SLIDE_DIVISOR = 8

    /** الشاشة الخارجة بتتحرّك أقل — بتبان "ورا" الجديدة مش بتتنطّ. */
    private const val EXIT_DIVISOR = 16

    private const val LATERAL_SCALE = 0.985f
    private const val MODAL_SCALE = 0.94f
}

// ══════════════════════════════════════════════════════════ ردّ الفعل باللمس

/**
 * انكماش خفيف تحت الإصبع.
 *
 * الغرض مش الشكل: بين لمسة الإصبع وأول تغيّر في الشاشة فيه فجوة، والفجوة
 * دي هي اللي بتخلّي الواجهة تحسّ "بطيئة" حتى لو الشغل نفسه سريع. الانكماش
 * بيملا الفجوة دي في نفس الإطار.
 *
 * الحركة على `scale` بس — خاصية رسم مش تخطيط، فمابتسببش أي قياس جديد.
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressed: Float = PRESSED_SCALE
): State<Float> {
    val reduced = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed && !reduced) pressed else 1f,
        animationSpec = pressSpring(),
        label = "pressScale"
    )
}

/**
 * نابض قصير وبدون ارتداد.
 *
 * الارتداد (`DampingRatioMediumBouncy`) بيبان لعوب في تطبيق قياسات —
 * والمهندس اللي بيضغط ٢٠٠ زرار في اليوم مش عايز كل واحد يهتزّ.
 */
fun <T> pressSpring(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

const val PRESSED_SCALE = 0.97f

/** بيطبّق مقياس الضغط كتحويل رسم — من غير أي إعادة تخطيط. */
fun Modifier.pressScale(scale: State<Float>): Modifier = this.scale(scale.value)

/** ارتفاع متحرّك للكروت — بيرتفع تحت الإصبع. */
@Composable
fun animatedElevation(
    interactionSource: InteractionSource,
    resting: Dp,
    pressedElevation: Dp
): State<Dp> {
    val reduced = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    return androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isPressed && !reduced) pressedElevation else resting,
        animationSpec = pressSpring(),
        label = "elevation"
    )
}
