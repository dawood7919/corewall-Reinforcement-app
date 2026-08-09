package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfSelectionState
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.TextQuad
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlin.math.roundToInt

/**
 * طبقة تحديد النص: التظليل + المقبضين + شريط الأوامر.
 *
 * المقبضين **مكوّنات حقيقية** مش رسم على الكانفاس، وده مقصود: لو كانوا
 * رسم، كان لازم أفرّق جوّه معالج الإيماءات بين "سحب مقبض" و"تمرير
 * الصفحة" — وده بالظبط النوع من الغموض اللي بيخلّي التحديد يفلت وانت
 * بتحاول تظبّطه. كمكوّنات، كل واحد بياخد لمسته لوحده والباقي بيمرّر عادي.
 */
@Composable
fun SelectionHandles(
    state: PdfViewerState,
    selection: PdfSelectionState,
    onCopy: () -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!selection.isActive || selection.quads.isEmpty()) return
    val c = LocalCwColors.current
    val density = LocalDensity.current

    val first = selection.quads.first()
    val last = selection.quads.last()
    val startPoint = state.pagePointScreen(selection.page, first.left, first.bottom) ?: return
    val endPoint = state.pagePointScreen(selection.page, last.right, last.bottom) ?: return

    // ── المقبضان
    Handle(startPoint) { screen ->
        val doc = state.screenToPagePoint(selection.page, screen)
        if (doc != null) selection.dragHandle(doc.x, doc.y, movingStart = true)
    }
    Handle(endPoint) { screen ->
        val doc = state.screenToPagePoint(selection.page, screen)
        if (doc != null) selection.dragHandle(doc.x, doc.y, movingStart = false)
    }

    // ── شريط الأوامر فوق التحديد
    val top = selection.quads.minOf { it.top }
    val centreX = selection.quads.let { qs -> (qs.minOf { it.left } + qs.maxOf { it.right }) / 2f }
    val anchor = state.pagePointScreen(selection.page, centreX, top) ?: return
    // عرض الشريط بيتقاس مش بيتخمّن: أي تخمين بيخلّيه مش متمركز على
    // التحديد، وده بيبان فوراً على تحديد كلمة قصيرة.
    var barWidth by remember { mutableIntStateOf(0) }
    val lift = with(density) { BAR_LIFT.toPx() }

    Box(
        Modifier
            .offset {
                IntOffset(
                    x = (anchor.x - barWidth / 2f).roundToInt(),
                    y = (anchor.y - lift).roundToInt()
                )
            }
            .onSizeChanged { barWidth = it.width }
    ) {
        Surface(
            shape = Radius.pill,
            color = c.surface,
            shadowElevation = Elevation.overlay,
            border = androidx.compose.foundation.BorderStroke(
                com.corewall.qaqc.ui.design.Stroke.hair, c.outline
            )
        ) {
            Row(
                Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                CwIconButton(Icons.Filled.ContentCopy, "انسخ", onCopy)
                CwIconButton(
                    Icons.Filled.Search, "دوّر على المحدَّد",
                    { onSearch(selection.text) },
                    enabled = selection.text.isNotBlank()
                )
                CwIconButton(Icons.Filled.Close, "إلغاء التحديد", onDismiss)
            }
        }
    }
}

/**
 * مقبض واحد. [onDragTo] بياخد **الموضع المطلق** على الشاشة مش الفرق.
 *
 * السبب: كل حركة بتغيّر التحديد، والتحديد بيحرّك المقبض. لو بعتنا الفرق،
 * الحركة كانت هتتحسب مرتين — مرة في المقبض اللي اتحرّك ومرة في الفرق
 * المتراكم — والمقبض كان هيهرب من تحت الصباع. علشان كده بنمسك نقطة
 * البداية وقت بداية السحب وبنضيف عليها المجموع.
 */
@Composable
private fun Handle(at: Offset, onDragTo: (Offset) -> Unit) {
    val c = LocalCwColors.current
    val density = LocalDensity.current
    val radiusPx = with(density) { HANDLE_SIZE.toPx() / 2f }
    val latestAt by rememberUpdatedState(at)
    val latestDrag by rememberUpdatedState(onDragTo)

    Box(
        Modifier
            .offset {
                IntOffset(
                    (at.x - radiusPx).roundToInt(),
                    // المقبض بيقعد **تحت** خط الأساس، زي كل عارض نص —
                    // فوقه كان هيغطّي الحرف اللي انت بتظبّط عليه.
                    (at.y - radiusPx / 2f).roundToInt()
                )
            }
            .size(HANDLE_SIZE)
            .pointerInput(Unit) {
                var origin = Offset.Zero
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { origin = latestAt; total = Offset.Zero },
                    onDrag = { change, delta ->
                        total += delta
                        latestDrag(origin + total)
                        change.consume()
                    }
                )
            }
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = c.accent,
            modifier = Modifier
                .size(HANDLE_DOT)
                .offset(x = (HANDLE_SIZE - HANDLE_DOT) / 2, y = (HANDLE_SIZE - HANDLE_DOT) / 2)
        ) {}
    }
}

/**
 * تظليل التحديد — بيترسم مع باقي الطبقات على نفس الكانفاس.
 * لون الاختيار شفاف عشان النص تحته يفضل مقروء وانت بتظبّط الحواف.
 */
fun DrawScope.drawSelection(
    state: PdfViewerState,
    quads: List<TextQuad>,
    color: Color
) {
    quads.forEach { q ->
        val rect = state.quadScreenRect(q) ?: return@forEach
        drawRect(
            color = color.copy(alpha = 0.30f),
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height)
        )
    }
}

/**
 * تظليل نتائج البحث.
 *
 * النتيجة الحالية بتاخد لون تاني وإطار. من غير التفرقة دي، "التالي" في
 * صفحة فيها ١٢ نتيجة مابيوريكش انت رحت لأنهي واحدة فيهم.
 */
fun DrawScope.drawSearchHighlights(
    state: PdfViewerState,
    quads: List<TextQuad>,
    activeQuads: List<TextQuad>,
    base: Color,
    active: Color
) {
    quads.forEach { q ->
        val rect = state.quadScreenRect(q) ?: return@forEach
        drawRect(
            color = base.copy(alpha = 0.34f),
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height)
        )
    }
    activeQuads.forEach { q ->
        val rect = state.quadScreenRect(q) ?: return@forEach
        drawRect(
            color = active.copy(alpha = 0.42f),
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height)
        )
        drawRect(
            color = active,
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = ACTIVE_RING_PX)
        )
    }
}

private const val ACTIVE_RING_PX = 2f

/** مستطيل نص → مستطيل على الشاشة. */
fun PdfViewerState.quadScreenRect(q: TextQuad): Rect? {
    val doc = pageRectToDoc(q.page, q.left, q.top, q.right, q.bottom) ?: return null
    return Rect(
        docToScreenX(doc.left),
        docToScreenY(doc.top),
        docToScreenX(doc.right),
        docToScreenY(doc.bottom)
    )
}

/** نقطة بنقط الصفحة → نقطة على الشاشة. */
fun PdfViewerState.pagePointScreen(page: Int, xPt: Float, yPt: Float): Offset? {
    val slot = layout.slotAt(page) ?: return null
    return Offset(docToScreenX(slot.left + xPt), docToScreenY(slot.top + yPt))
}

/** العكس: نقطة على الشاشة → نقط الصفحة (أصل أعلى-يسار للصفحة). */
fun PdfViewerState.screenToPagePoint(page: Int, screen: Offset): Offset? {
    val slot = layout.slotAt(page) ?: return null
    val doc = screenToDoc(screen)
    return Offset(doc.x - slot.left, doc.y - slot.top)
}

private val HANDLE_SIZE = 40.dp
private val HANDLE_DOT = 14.dp
private val BAR_LIFT = 56.dp
