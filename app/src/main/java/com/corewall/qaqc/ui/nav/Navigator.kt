package com.corewall.qaqc.ui.nav

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حالة التنقّل — **مكدّس واحد** فوق التبويب الشغّال.
 *
 * النظام القديم كان ٦ آليات تنقّل شغالة مع بعض (قسمين × شرائط تبويبات
 * مختلفة، ٣ عدسات، enum من ١٣ شاشة، ٥ عارضات بـflows منفصلة، ومساعد طايف)،
 * وزرار الرجوع كان cascade من ٩ فروع مكتوب بالإيد. دلوقتي الرجوع = pop.
 */
@Immutable
data class NavState(
    val tab: Dest.Root,
    val stack: List<Dest>
) {
    /** الوجهة المعروضة دلوقتي. */
    val current: Dest get() = stack.lastOrNull() ?: tab

    /** هل فيه حاجة نرجع منها جوّه التبويب الحالي؟ */
    val canPop: Boolean get() = stack.isNotEmpty()

    /** الوجهة الحالية بتغطّي شرائط التنقّل؟ */
    val isFullScreen: Boolean get() = current.fullScreen

    /**
     * مسار الوجهة من الجذر — الشاشة بتستخدمه تقول للمستخدم هو فين.
     * القاعدة بتقول breadcrumbs من العمق ٣ فما فوق.
     */
    val breadcrumb: List<Dest> get() = buildList {
        add(tab)
        addAll(stack)
    }
}

/**
 * الملّاح. المصدر الوحيد لسؤال "أنا في أنهي شاشة؟".
 *
 * التنقّل بيتنده من الـViewModel مش من داخل الـcomposable — ده اللي القاعدة
 * بتسمّيه navigation-as-events، وبيخلّي الشاشة نفسها مالهاش رأي في المكدّس.
 */
class Navigator(start: Dest.Root = Dest.Today) {

    private val _state = MutableStateFlow(NavState(start, emptyList()))
    val state: StateFlow<NavState> = _state.asStateFlow()

    /** تاريخ التبويبات — عشان الرجوع من تبويب يرجّعك للي قبله. */
    private val tabHistory = ArrayDeque<Dest.Root>()

    private val _canGoBack = MutableStateFlow(false)

    /** هل زرار الرجوع هيعمل حاجة جوّه التطبيق؟ */
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private fun sync() {
        _canGoBack.value = _state.value.canPop || tabHistory.isNotEmpty()
    }

    val current: Dest get() = _state.value.current

    /**
     * اختيار تبويب. لو التبويب هو نفسه الحالي وفيه مكدّس فوقه، بيرجّعك
     * لجذر التبويب — سلوك متوقّع في كل تطبيقات أندرويد.
     */
    fun selectTab(root: Dest.Root) {
        val s = _state.value
        if (s.tab == root) {
            if (s.stack.isNotEmpty()) _state.value = s.copy(stack = emptyList())
            sync()
            return
        }
        tabHistory.addLast(s.tab)
        if (tabHistory.size > 16) tabHistory.removeFirst()
        _state.value = NavState(root, emptyList())
        sync()
    }

    /** فتح وجهة فوق التبويب الحالي. */
    fun push(dest: Dest) {
        if (dest is Dest.Root) {
            selectTab(dest)
            return
        }
        val s = _state.value
        // نفس الوجهة مرتين ورا بعض = ضغطة مكرّرة، مش عمق جديد.
        if (s.stack.lastOrNull() == dest) return
        _state.value = s.copy(stack = s.stack + dest)
        sync()
    }

    /** استبدال أعلى المكدّس — لما وجهة تودّي لوجهة بديلة ومش عايزينها في الرجوع. */
    fun replace(dest: Dest) {
        val s = _state.value
        _state.value = if (s.stack.isEmpty()) s.copy(stack = listOf(dest))
        else s.copy(stack = s.stack.dropLast(1) + dest)
        sync()
    }

    /**
     * الرجوع. قاعدة واحدة بترتيب واحد:
     * ١) لو فيه حاجة فوق التبويب — اطلعها.
     * ٢) لو لأ، ارجع للتبويب اللي قبله.
     * ٣) لو لأ، سيب النظام يخرج من التطبيق.
     *
     * بترجّع الوجهة اللي اتقفلت عشان الـViewModel ينضّف بياناتها.
     */
    fun pop(): PopResult {
        val s = _state.value
        if (s.stack.isNotEmpty()) {
            val top = s.stack.last()
            _state.value = s.copy(stack = s.stack.dropLast(1))
            sync()
            return PopResult.Popped(top)
        }
        val prev = tabHistory.removeLastOrNull() ?: return PopResult.Exhausted
        _state.value = NavState(prev, emptyList())
        sync()
        return PopResult.SwitchedTab(prev)
    }

    /** قفل وجهة بعينها لو هي اللي فوق — للأزرار اللي بتقفل نفسها. */
    fun dismiss(dest: Dest) {
        if (_state.value.stack.lastOrNull() == dest) pop()
    }

    /** رجوع لجذر التبويب من غير ما نغيّر التبويب. */
    fun popToRoot() {
        _state.value = _state.value.copy(stack = emptyList())
        sync()
    }

    sealed interface PopResult {
        /** اتقفلت وجهة — [dest] هي اللي اتشالت. */
        data class Popped(val dest: Dest) : PopResult

        /** رجعنا لتبويب قديم. */
        data class SwitchedTab(val tab: Dest.Root) : PopResult

        /** مفيش حاجة نرجعها — النظام يخرج. */
        data object Exhausted : PopResult
    }
}
