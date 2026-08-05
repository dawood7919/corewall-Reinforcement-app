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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
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
 * معرض صور الموقع — مجلدات + صور مباشرة.
 * التعليق مكتوب **فوق الصورة** (overlay) بخط كبير واضح + التاريخ والوقت.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePhotosScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val allPhotos by vm.sitePhotos.collectAsStateWithLifecycle()
    val srt = LocalSrtColors.current

    // المسار النسبي الحالي داخل صور الدور ("" = الجذر)
    var currentFolder by remember(level) { mutableStateOf("") }
    var folderListTick by remember { mutableStateOf(0) }

    val folders = remember(level, currentFolder, folderListTick) {
        vm.files.listSitePhotoFolders(level, currentFolder)
    }
    val photosHere = remember(allPhotos, currentFolder) {
        allPhotos.filter { it.folder == currentFolder }.sortedByDescending { it.timestamp }
    }

    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var pendingCommentPath by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var editingPhoto by remember { mutableStateOf<SitePhotoEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<SitePhotoEntity?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

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
            val copied = vm.files.importUris(listOf(uri), vm.files.sitePhotosDir(level, currentFolder))
            vm.registerFiles(copied)
            val file = copied.firstOrNull()
            if (file != null) {
                pendingCommentPath = file.absolutePath
                commentDraft = ""
            }
        }
    }

    fun launchCamera() {
        val f = vm.files.newSitePhotoFile(level, currentFolder)
        pendingCamera = f
        camera.launch(vm.files.uriFor(f))
    }

    fun goUp() {
        if (currentFolder.isBlank()) return
        currentFolder = currentFolder.substringBeforeLast('/', missingDelimiterValue = "")
    }

    fun openFolder(name: String) {
        currentFolder = if (currentFolder.isBlank()) name else "$currentFolder/$name"
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.md)) {
                FloatingActionButton(
                    onClick = { showNewFolder = true; newFolderName = "" },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "مجلد جديد") }
                FloatingActionButton(
                    onClick = { gallery.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) { Icon(Icons.Filled.PhotoLibrary, contentDescription = "من المعرض") }
                FloatingActionButton(
                    onClick = { launchCamera() }
                ) { Icon(Icons.Filled.PhotoCamera, contentDescription = "التقط صورة") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // شريط المسار
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = Space.sm, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFolder.isNotBlank()) {
                        IconButton(onClick = { goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "دور $level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (currentFolder.isBlank()) "كل الصور" else currentFolder,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${folders.size} مجلد · ${photosHere.size} صورة",
                        style = MaterialTheme.typography.labelSmall,
                        color = srt.text3
                    )
                }
            }

            if (folders.isEmpty() && photosHere.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.AddAPhoto,
                    title = if (currentFolder.isBlank()) "لا توجد صور في دور $level" else "المجلد فارغ",
                    subtitle = "أنشئ مجلداً، التقط صورة، أو اختر من المعرض — التعليق يظهر مكتوباً فوق الصورة.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    items(folders, key = { "dir-${it.absolutePath}" }) { dir ->
                        FolderCard(
                            name = dir.name,
                            onOpen = { openFolder(dir.name) },
                            onDelete = {
                                vm.files.delete(dir)
                                folderListTick++
                            }
                        )
                    }
                    items(photosHere, key = { it.id }) { photo ->
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
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }

    pendingCommentPath?.let { path ->
        CommentDialog(
            title = "اكتب على الصورة",
            initial = commentDraft,
            confirmLabel = "حفظ",
            hint = "النص يظهر مكتوباً فوق الصورة بخط كبير.",
            onDismiss = {
                File(path).delete()
                pendingCommentPath = null
                commentDraft = ""
            },
            onConfirm = { text ->
                vm.addSitePhoto(path, text, currentFolder)
                pendingCommentPath = null
                commentDraft = ""
            }
        )
    }

    editingPhoto?.let { photo ->
        CommentDialog(
            title = "تعديل النص على الصورة",
            initial = commentDraft,
            confirmLabel = "تحديث",
            hint = "النص يظهر مكتوباً فوق الصورة.",
            onDismiss = { editingPhoto = null; commentDraft = "" },
            onConfirm = { text ->
                vm.updateSitePhotoComment(photo, text)
                editingPhoto = null
                commentDraft = ""
            }
        )
    }

    deleteTarget?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الصورة؟") },
            text = { Text("سيتم حذف الصورة والتعليق نهائياً.") },
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

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("مجلد جديد", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("مثال: فحص قبل الصب") },
                    shape = Radius.shapeLg
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        vm.files.createSitePhotoFolder(level, currentFolder, newFolderName.trim())
                        folderListTick++
                    }
                    showNewFolder = false
                    newFolderName = ""
                }) { Text("إنشاء", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun FolderCard(name: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        onClick = onOpen,
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(Radius.shapeLg)
                    .background(srt.blue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = srt.blue, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(Space.md))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onDelete) {
                Text("حذف", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
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
    val bmp = rememberThumb(photo.filePath, targetPx = 700)
    val dateText = remember(photo.timestamp) { photoDateFmt.format(Date(photo.timestamp)) }

    Surface(
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(Radius.shapeLg)
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
                    Icon(Icons.Filled.Image, contentDescription = null, tint = srt.text3, modifier = Modifier.size(40.dp))
                }
            }

            // تدرج سفلي + النص مكتوب على الصورة
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(horizontal = Space.md, vertical = Space.md)
            ) {
                Column {
                    if (photo.comment.isNotBlank()) {
                        Text(
                            photo.comment,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(Space.xs))
                    }
                    Text(
                        dateText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // أزرار تعديل/حذف — كانت مساحة لمسها 32dp فوق صورة، يعني أقل من
            // الحد الأدنى وفوق خلفية متغيّرة. بقت 48dp بخلفية داكنة ثابتة.
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                PhotoAction(Icons.Filled.Edit, "عدّل تعليق الصورة", Color.White, onEdit)
                PhotoAction(Icons.Filled.Delete, "امسح الصورة", Color(0xFFFFB3AC), onDelete)
            }
        }
    }
}

@Composable
private fun CommentDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    hint: String,
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
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("مثال: تسليح حائط T1-W05 قبل الصب") },
                    minLines = 3,
                    maxLines = 6,
                    shape = Radius.shapeLg
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

/**
 * زرار فوق صورة. الخلفية الداكنة الثابتة مقصودة: الصورة تحته ممكن تكون أي
 * لون، فالتباين لازم ييجي من الزرار نفسه مش من اللي وراه.
 */
@Composable
private fun PhotoAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = Radius.shapeSm,
        color = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier.size(Sizes.touch)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(IconSize.md))
        }
    }
}
