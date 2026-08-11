package com.corewall.qaqc.stylus

import android.os.Build
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter

/**
 * تصنيف أداة اللمس.
 *
 * أندرويد بيقول لكل مؤشّر (pointer) إيه اللي عامله: صباع، قلم، أستيكة القلم،
 * ماوس. الكلام ده جاي من الـdigitizer نفسه مش تخمين، وهو أساس الوضع كله.
 */
enum class PointerKind {
    FINGER,

    /** قلم على الشاشة — S Pen أو أي قلم متوافق. */
    STYLUS,

    /** الطرف العكسي للقلم (أستيكة) — بعض الأقلام بتبلّغ عنه لوحده. */
    ERASER,

    /** ماوس أو حاجة مش معروفة. بنعاملها معاملة الصباع. */
    OTHER;

    val isPen: Boolean get() = this == STYLUS || this == ERASER
}

/**
 * بيحوّل `TOOL_TYPE_*` لتصنيف.
 *
 * دالة على الرقم الخام مش على `MotionEvent` عشان تتّست من غير أندرويد —
 * `MotionEvent` مايتعملش من اختبار وحدة عادي.
 */
fun pointerKindOf(toolType: Int): PointerKind = when (toolType) {
    MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
    MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER
    MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
    else -> PointerKind.OTHER
}

/** بيقرا نوع الأداة لمؤشّر معيّن جوّه الحدث. */
fun MotionEvent.pointerKindAt(index: Int): PointerKind = pointerKindOf(getToolType(index))

/**
 * `FLAG_CANCELED` (أندرويد ١٣+) بيقول إن الحدث ده كان **غلط** ولازم يتلغي —
 * ودي إشارة رفض الكف الرسمية من النظام. على النسخ الأقدم مافيش غير
 * `ACTION_CANCEL` للحدث كله.
 */
fun MotionEvent.isCanceledCompat(): Boolean =
    actionMasked == MotionEvent.ACTION_CANCEL ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (flags and MotionEvent.FLAG_CANCELED) != 0)

/**
 * عيّنة من القلم.
 *
 * [pressure] من ٠ لـ١ زي ما الجهاز بيبلّغ. الأجهزة اللي مابتقيسش ضغط
 * بترجّع ١ ثابت — فالكود اللي بيستخدمه لازم يشتغل صح في الحالتين.
 */
data class StylusSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    /** ميل القلم بالتقدير الدائري. ٠ = عمودي على الشاشة. */
    val tilt: Float,
    val kind: PointerKind
)

/**
 * موجّه لمس القلم.
 *
 * ده قلب الوضع كله، ومكتوب كـclass عادي (مش Composable) عشان يتّست لوحده
 * من غير شاشة.
 *
 * ### ليه على مستوى الحدث مش على مستوى الحبر
 *
 * أسهل حاجة كانت إننا نرسم بأي لمسة وبعدين نمسح اللي طلع من صباع. ده غلط
 * لسببين: الخط بيبان ويختفي (وده بيبان كعطل)، والكف اللي مستريح على
 * الشاشة بيفضل يولّد أحداث. القرار هنا بيتاخد **قبل** ما أي نقطة توصل
 * لمحرّك الرسم.
 *
 * ### الفرز
 *
 * • **قلم نازل** → بيمسك الخط، والموجّه بيبلع الحدث كله (`true`).
 *   وده بالظبط رفض الكف: طول ما القلم على الشاشة، أي صباع أو كف بينزل
 *   بيتبلع ومابيوصلش لا للرسم ولا للتنقّل.
 * • **مفيش قلم** → الموجّه بيرجّع `false`، فالحدث بيكمّل لطبقة الإيماءات
 *   العادية: تمرير، تكبير، نقر. الصباع بيفضل بيتنقّل زي ما هو.
 *
 * ### الإلغاء
 *
 * لو النظام قال إن اللمسة دي كانت غلط (`ACTION_CANCEL` أو `FLAG_CANCELED`
 * على أندرويد ١٣+)، الخط اللي كان بيتبني بيترمي بالكامل عن طريق
 * [onCancel] — مابيتحفظش ومابيدخلش تاريخ التراجع. ده الفرق بين "الكف عمل
 * خربشة اتشالت" و"الكف مارسمش أصلاً".
 */
class StylusInkController(
    /** هل الوضع شغّال دلوقتي؟ (الوضع مفعّل + فيه أداة رسم مختارة) */
    private val enabled: () -> Boolean,
    private val onStart: (StylusSample) -> Unit,
    private val onMove: (StylusSample) -> Unit,
    private val onEnd: () -> Unit,
    private val onCancel: () -> Unit
) {
    /** معرّف المؤشّر اللي ماسك الخط الحالي. −١ = مفيش خط شغّال. */
    private var activeId = NO_POINTER

    val isDrawing: Boolean get() = activeId != NO_POINTER

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!enabled()) {
            // الوضع اتقفل أو الأداة اتغيّرت وسط خط — نرميه بدل ما نسيبه معلّق.
            if (isDrawing) finish(canceled = true)
            return false
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val kind = event.pointerKindAt(index)
                if (kind.isPen && !isDrawing) {
                    activeId = event.getPointerId(index)
                    onStart(event.sampleAt(index, kind))
                    true
                } else {
                    // صباع أو كف نزل والقلم شغّال → يتبلع.
                    isDrawing
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return false
                val index = event.findPointerIndex(activeId)
                if (index >= 0) {
                    val kind = event.pointerKindAt(index)
                    // النقط التاريخية = العيّنات اللي الجهاز جمّعها بين
                    // إطارين. استخدامها بيدّي خط أنعم وأقرب لطرف القلم من
                    // غير ما نضيف أي تأخير — دي عيّنات حصلت فعلاً، مش تنعيم.
                    for (h in 0 until event.historySize) {
                        onMove(event.historicalSampleAt(index, h, kind))
                    }
                    onMove(event.sampleAt(index, kind))
                }
                true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activeId) {
                    finish(canceled = event.isCanceledCompat())
                    true
                } else {
                    isDrawing
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDrawing) return false
                finish(canceled = event.isCanceledCompat())
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isDrawing) return false
                finish(canceled = true)
                true
            }

            else -> isDrawing
        }
    }

    private fun finish(canceled: Boolean) {
        activeId = NO_POINTER
        if (canceled) onCancel() else onEnd()
    }

    private companion object {
        const val NO_POINTER = -1
    }
}

private fun MotionEvent.sampleAt(index: Int, kind: PointerKind) = StylusSample(
    x = getX(index),
    y = getY(index),
    pressure = getPressure(index),
    tilt = getAxisValue(MotionEvent.AXIS_TILT, index),
    kind = kind
)

private fun MotionEvent.historicalSampleAt(index: Int, pos: Int, kind: PointerKind) = StylusSample(
    x = getHistoricalX(index, pos),
    y = getHistoricalY(index, pos),
    pressure = getHistoricalPressure(index, pos),
    tilt = getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, pos),
    kind = kind
)

/**
 * بيركّب [StylusInkController] على مُعدِّل.
 *
 * لازم يتحطّ **قبل** مُعدِّلات الإيماءات في السلسلة عشان يشوف الحدث الأول
 * ويقرّر: يبلعه (قلم) ولا يسيبه يعدّي (صباع).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.stylusInk(controller: StylusInkController): Modifier =
    this.pointerInteropFilter { event -> controller.onMotionEvent(event) }

/**
 * سُمك الخط من ضغط القلم.
 *
 * الضغط بيتحوّل لمعامل بين [MIN_FACTOR] و[MAX_FACTOR] حوالين السُمك
 * الأساسي للأداة. المدى ضيّق عن قصد: خط بيتغيّر سُمكه تلات أضعاف بيبقى
 * زي الفرشاة مش زي قلم هندسي، والرسمة دي مستند مش لوحة.
 *
 * الأجهزة اللي مابتقيسش ضغط بترجّع ١ ثابت، فالمعامل بيطلع ١ والسُمك
 * بيفضل زي ما هو بالظبط — يعني الميزة اختيارية من غير أي فرع في الكود.
 */
fun pressureWidthFactor(pressure: Float): Float {
    if (pressure <= 0f) return 1f
    val p = pressure.coerceIn(0f, 1f)
    return (MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * p).coerceIn(MIN_FACTOR, MAX_FACTOR)
}

private const val MIN_FACTOR = 0.65f
private const val MAX_FACTOR = 1.45f

/**
 * متوسّط ضغط الخط.
 *
 * نموذج التعليق بيخزّن **سُمك واحد للخط كله** مش لكل نقطة، فالخيار الصادق
 * هو إن الضغط يحدّد سُمك الخط ككل: تضغط أكتر → خط أتخن. تغيير السُمك جوّه
 * الخط الواحد كان هيحتاج تغيير في المخطط وترحيل، وده مش مبرّر لمكسب
 * بصري بسيط.
 *
 * كائن عادي مش حالة Compose عن قصد: بيتحدّث مع كل عيّنة (عشرات في
 * الثانية)، ولو كان حالة كان هيعيد التركيب مع كل نقطة.
 */
class PressureAverage {
    private var sum = 0f
    private var count = 0

    fun add(pressure: Float) {
        if (pressure > 0f) { sum += pressure; count++ }
    }

    /** ١ لو مافيش عيّنات — يعني الجهاز مابيقيسش ضغط، والسُمك بيفضل زي ما هو. */
    val value: Float get() = if (count == 0) 1f else sum / count

    fun reset() { sum = 0f; count = 0 }
}
