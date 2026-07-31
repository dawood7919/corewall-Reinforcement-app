package com.corewall.qaqc.ui.appscreens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.SitePhotoEntity
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.notes.rememberThumb
import com.corewall.qaqc.ui.theme.LocalSrtColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val photoDateFmt = SimpleDateFormat("dd/MM/yyyy  ·  hh:mm a", Locale.ENGLISH)

/**
 * معرض صور الموقع (Site Photos) — معزول لكل دور.
 * التقاط صورة أو اختيار من المعرض → كتابة تعليق يظهر تحت الصورة بخط كبير وواضح + التاريخ والوقت.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePhotosScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val photos by vm.sitePhotos.collectAsStateWithLifecycle()
    val srt = LocalSrtColors.current

    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var pendingCommentPath by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var editingPhoto by remember { mutableStateOf<SitePhotoEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<SitePhotoEntity?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamera
        if (ok && f != null && f.exists()) {
            pendingCommentPath = f.absolutePath
            commentDraft = ""
        } else {
            f?.delete()
        }
        pendingCamera = null
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = vm.files.importUris(listOf(uri), vm.files.sitePhotosDir(level))
            val file = copied.firstOrNull()
            if (file != null) {
                pendingCommentPath = file.absolutePath
                commentDraft = ""
            }
        }
    }

    fun launchCamera() {
        val f = vm.files.newSitePhotoFile(level)
        pendingCamera = f
        camera.launch(vm.files.uriFor(f))
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { gallery.launch("image/*") },
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    text = { Text("من المعرض") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                ExtendedFloatingActionButton(
                    onClick = { launchCamera() },
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = { Text("التقط صورة") }
                )
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AddAPhoto,
                title = "لا توجد صور في دور $level",
                subtitle = "التقط صورة للموقع أو اختر من المعرض، ثم اكتب تعليقاً يظهر تحتها مع التاريخ والوقت.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                SitePhotoCard(
                    photo = photo,
                    onOpen = { vm.openImage(photo.filePath) },
                    onEdit = {
                        editingPhoto = photo
                        commentDraft = photo.comment
                    },
                    onDelete = { deleteTarget = photo }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // حوار التعليق بعد التقاط/اختيار صورة جديدة
    pendingCommentPath?.let { path ->
        CommentDialog(
            title = "تعليق على الصورة",
            initial = commentDraft,
            confirmLabel = "حفظ",
            onDismiss = {
                // لو ألغى المستخدم نمسح الملف المؤقت
                File(path).delete()
                pendingCommentPath = null
                commentDraft = ""
            },
            onConfirm = { text ->
                vm.addSitePhoto(path, text)
                pendingCommentPath = null
                commentDraft = ""
            }
        )
    }

    // تعديل تعليق صورة موجودة
    editingPhoto?.let { photo ->
        CommentDialog(
            title = "تعديل التعليق",
            initial = commentDraft,
            confirmLabel = "تحديث",
            onDismiss = { editingPhoto = null; commentDraft = "" },
            onConfirm = { text ->
                vm.updateSitePhotoComment(photo, text)
                editingPhoto = null
                commentDraft = ""
            }
        )
    }

    // تأكيد الحذف
    deleteTarget?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الصورة؟") },
            text = { Text("سيتم حذف الصورة والتعليق نهائياً من دور $level.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSitePhoto(photo)
                    deleteTarget = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SitePhotoCard(
    photo: SitePhotoEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val srt = LocalSrtColors.current
    val bmp = rememberThumb(photo.filePath, targetPx = 900)
    val dateText = remember(photo.timestamp) { photoDateFmt.format(Date(photo.timestamp)) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // الصورة
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bmp != null) {
                    Surface(onClick = onOpen, modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = srt.text3, modifier = Modifier.size(48.dp))
                    }
                }
                // أزرار تعديل/حذف
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل", modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Surface(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                }
            }

            // التعليق + التاريخ
            Column(Modifier.padding(16.dp)) {
                if (photo.comment.isNotBlank()) {
                    Text(
                        photo.comment,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            lineHeight = 30.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text(
                        "بدون تعليق",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = srt.text3,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CommentDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "اكتب تعليقاً واضحاً يظهر تحت الصورة بخط كبير.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("مثال: تسليح حائط T1-W05 قبل الصب") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
