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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            }
        }
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
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.lg, end = Space.lg,
                    top = Space.md, bottom = Space.bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(drawings, key = { it.id }) { drawing ->
                    val count = items.count { it.drawingId == drawing.id && it.parentId == null }
                    CwCard(onClick = {
                        vm.openTakeoffEditor(drawing.id, drawing.filePath, drawing.name)
                    }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            Icon(
                                Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = c.danger.fg
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    drawing.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textPrimary
                                )
                                Text(
                                    if (count == 0) "مفيش بنود لسه" else "$count بند",
                                    style = CwText.codeSmall,
                                    color = c.textTertiary
                                )
                            }
                            CwIconButton(
                                Icons.Filled.Delete, "احذف الرسمة",
                                { confirmDelete = drawing }, tint = c.danger.fg
                            )
                        }
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
