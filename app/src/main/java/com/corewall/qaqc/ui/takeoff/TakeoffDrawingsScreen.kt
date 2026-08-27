package com.corewall.qaqc.ui.takeoff

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.pdf.rememberPdfCover
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffDrawingEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.launch

/**
 * رسمات قسم حصر — ارفع PDF وافتحه.
 */
@Composable
fun TakeoffDrawingsScreen(
    vm: MainViewModel,
    projectId: Long,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val drawings by remember(projectId) { vm.takeoff.drawings(projectId) }
        .collectAsStateWithLifecycle(emptyList())
    val items by remember(projectId) { vm.takeoff.projectItems(projectId) }
        .collectAsStateWithLifecycle(emptyList())

    var failed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TakeoffDrawingEntity?>(null) }

    /** رسمة اترفعت ومستنية اختيار صفحاتها. */
    var pendingPages by remember { mutableStateOf<TakeoffDrawingEntity?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // الاسم بيتقرا من الـURI الأول لأن الصلاحية عليه مؤقتة —
            // بعد النسخ الـURI ممكن يبقى مش صالح.
            val name = queryDisplayName(context, uri)
            scope.launch {
                val id = vm.takeoff.addDrawing(projectId, uri, name)
                if (id == null) failed = true
                // الاختيار بيحصل **بعد** النسخ عن قصد: منتقي الملفات بيدّي
                // صلاحية مؤقتة على الـURI، وفتح الملف لعرض مصغّرات وهو لسه
                // برّه التطبيق ممكن يفشل من غير سبب واضح للمستخدم.
                else vm.takeoff.drawingById(id)?.let { pendingPages = it }
            }
        }
    }

    pendingPages?.let { drawing ->
        TakeoffPagePickerSheet(
            path = drawing.filePath,
            onConfirm = { pages ->
                pendingPages = null
                scope.launch { vm.takeoff.keepPages(drawing.id, pages) }
            },
            // الإلغاء بيسيب الملف كامل — الرفع نفسه نجح، والاختيار تحسين
            // مش شرط لإتمامه.
            onDismiss = { pendingPages = null }
        )
    }

    Box(modifier.fillMaxSize()) {
        if (drawings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CwEmptyState(
                    icon = Icons.Filled.UploadFile,
                    title = "ارفع أول رسمة",
                    detail = "أي ملف PDF. بعد ما ترفعه تعاير المقياس مرة، " +
                        "وبعدها كل قياس على الصفحة دي بيطلع بالمتر.",
                    action = { CwButton("ارفع PDF", { picker.launch(arrayOf("application/pdf")) }) }
                )
            }
        } else {
            // الرسمة بتتعرّف من شكلها. أيقونة PDF حمرا واحدة على كل صف
            // معناها إن كل الرسمات شكلها واحد، والاسم لوحده ("A-101")
            // مابيقولش لحد إيه اللي جوّه غير لو فاتحه من شوية.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = Space.lg, end = Space.lg,
                    top = Space.md, bottom = Space.bottomInset
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(drawings, key = { it.id }) { drawing ->
                    val count = items.count { it.drawingId == drawing.id && it.parentId == null }
                    val cover = rememberPdfCover(drawing.filePath)
                    CwCard(
                        onClick = {
                            vm.openTakeoffEditor(drawing.id, drawing.filePath, drawing.name)
                        },
                        contentPadding = PaddingValues(Space.sm)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.15f)
                                .background(c.surfaceAlt, Radius.shapeMd),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cover != null) {
                                Image(
                                    bitmap = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(Space.xxs)
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = c.danger.fg
                                )
                            }
                            CwIconButton(
                                Icons.Filled.Delete, "احذف الرسمة",
                                { confirmDelete = drawing }, tint = c.danger.fg,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            drawing.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (count == 0) "مفيش بنود لسه" else "$count بند",
                            style = CwText.codeSmall,
                            color = c.textTertiary
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                containerColor = c.accent,
                contentColor = c.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Text("  ارفع PDF")
            }
        }
    }

    if (failed) {
        TakeoffConfirmSheet(
            title = "مقدرناش ننسخ الملف",
            detail = "جرّب تاني، أو اختار الملف من مكان تاني على الجهاز.",
            confirmLabel = "تمام",
            onConfirm = { failed = false },
            onDismiss = { failed = false }
        )
    }

    confirmDelete?.let { drawing ->
        TakeoffConfirmSheet(
            title = "حذف «${drawing.name}»؟",
            detail = "هيتشال الملف وكل بنود الحصر اللي عليه. مافيش تراجع.",
            confirmLabel = "احذف",
            onConfirm = {
                confirmDelete = null
                scope.launch { vm.takeoff.deleteDrawing(drawing) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    context.contentResolver.query(
        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index) ?: "رسمة.pdf"
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "رسمة.pdf"
}

/** تأكيد بسيط — الأفعال اللي مالهاش تراجع بتعدّي من هنا. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffConfirmSheet(
    title: String,
    detail: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwButton(confirmLabel, onConfirm, style = CwButtonStyle.Danger)
                CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}
