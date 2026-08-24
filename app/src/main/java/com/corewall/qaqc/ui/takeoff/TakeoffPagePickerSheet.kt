package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.pdf.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * اختيار صفحات الرسمة بعد رفعها.
 *
 * الرسمات بتيجي غالبًا كملف فيه اللوحة اللي محتاجها وورا كام صفحة
 * ماليهاش علاقة بالحصر — غلاف، جدول مراجعة، تفاصيل قياسية. لو فضلوا في
 * الملف، بيفضلوا في التنقّل وفي كل قايمة صفحات، وبتضيّع وقت في كل مرة.
 *
 * الصفحات بتتعرض بشكلها الحقيقي مش بأرقامها: رقم الصفحة مابيقولش لو دي
 * اللوحة اللي انت عايزها ولا لأ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffPagePickerSheet(
    path: String,
    onConfirm: (pages: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    var openFailed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, File(path)) }
        }
        opened.onSuccess { session = it }
        opened.onFailure { openFailed = true }
    }
    DisposableEffect(path) { onDispose { session?.close() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        val active = session
        // الافتراضي: الكل مختار. الشائع إن الملف كله مطلوب، والاستثناء
        // إنك تشيل صفحة — فالبداية من "الكل" بتخلّي الحالة الشائعة صفر لمسات.
        var selected by remember(active) {
            mutableStateOf((0 until (active?.pageCount ?: 0)).toSet())
        }

        Column(
            Modifier
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
        ) {
            Text(
                "اختر الصفحات",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(vertical = Space.sm)
            )
            Text(
                when {
                    openFailed -> "مقدرناش نفتح الملف"
                    active == null -> "بنفتح الملف…"
                    else -> "المختار: ${selected.size} من ${active.pageCount}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )
            Spacer(Modifier.height(Space.sm))

            if (active != null) {
                val thumbs = remember(active) { ThumbnailCache(active) }
                DisposableEffect(active) { onDispose { thumbs.clear() } }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(bottom = Space.md)
                ) {
                    items((0 until active.pageCount).toList(), key = { it }) { page ->
                        LaunchedEffect(page) { thumbs.request(page) }
                        val on = page in selected
                        Column(
                            Modifier
                                .fillMaxWidth()
                                // الضغط على الكارت كله، مش على الأيقونة بس —
                                // هدف ٤٤ بكسل مش أيقونة صغيرة في ركن.
                                .clickable {
                                    selected = if (on) selected - page else selected + page
                                }
                                .background(
                                    if (on) c.accent.copy(alpha = 0.12f) else c.surfaceAlt,
                                    Radius.shapeMd
                                )
                                .padding(Space.xs)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .background(c.surface, Radius.shapeSm),
                                contentAlignment = Alignment.Center
                            ) {
                                val image = thumbs.thumbs[page]
                                if (image != null) {
                                    Image(
                                        bitmap = image,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Icon(
                                    if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (on) "مختارة" else "مستبعدة",
                                    tint = if (on) c.accent else c.textTertiary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(Space.xxs)
                                )
                            }
                            Spacer(Modifier.height(Space.xxs))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "صفحة ${page + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (on) c.textPrimary else c.textTertiary
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.padding(vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    CwButton(
                        "أضف ${selected.size} صفحة",
                        { onConfirm(selected.sorted()) },
                        enabled = selected.isNotEmpty()
                    )
                    CwButton(
                        if (selected.size == active.pageCount) "شيل الكل" else "اختر الكل",
                        {
                            selected = if (selected.size == active.pageCount) emptySet()
                            else (0 until active.pageCount).toSet()
                        },
                        style = CwButtonStyle.Secondary
                    )
                    CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (openFailed) "مقدرناش نفتح الملف" else "…",
                        color = c.textTertiary
                    )
                }
            }
        }
    }
}
