package com.corewall.qaqc.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import java.io.File

/**
 * محرّر ملاحظة كامل الشاشة: عنوان + محتوى كبير قابل للتنسيق (Markdown)
 * بأدوات تنسيق (عنوان/نقاط/ترقيم/غامق/اقتباس/فاصل) + صور من المعرض أو الكاميرا.
 */
@Composable
fun NoteEditorScreen(vm: MainViewModel, note: NoteEntity, onClose: () -> Unit) {
    val context = LocalContext.current
    val markName = vm.markFor(note.elementId) ?: note.elementId

    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }
    var images by remember(note.id) { mutableStateOf(parseImagePaths(note.imagePathsJson)) }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val copied = vm.files.importNoteImages(uris, note.level, note.elementId)
            images = images + copied.map { it.absolutePath }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val f = pendingCameraFile
        if (success && f != null && f.exists()) images = images + f.absolutePath
        else f?.delete()
        pendingCameraFile = null
    }

    fun currentNote() = note.copy(
        title = title.trim(),
        body = body.text,
        imagePathsJson = encodeImagePaths(images)
    )

    fun applyFormat(prefix: String, wrap: Boolean = false) {
        val sel = body.selection
        val text = body.text
        if (wrap) {
            val start = sel.min
            val end = sel.max
            val selected = text.substring(start, end).ifEmpty { "نص" }
            val newText = text.substring(0, start) + prefix + selected + prefix + text.substring(end)
            body = TextFieldValue(newText, TextRange(start + prefix.length + selected.length + prefix.length))
        } else {
            // إدراج في بداية السطر الحالي
            val cursor = sel.min
            val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
            body = TextFieldValue(newText, TextRange(cursor + prefix.length))
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // شريط علوي
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.saveNote(currentNote()); onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = "حفظ وإغلاق")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("ملاحظة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$markName · دور ${note.level}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (note.id != 0L) {
                        IconButton(onClick = { vm.deleteNote(currentNote()); onClose() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Surface(
                        onClick = { vm.saveNote(currentNote()); onClose() },
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // العنوان
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (title.isEmpty()) Text(
                            "عنوان الملاحظة",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // الصور
                if (images.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEach { path ->
                            ImageThumb(path, onRemove = { images = images - path })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // المحتوى الكبير
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (body.text.isEmpty()) Text(
                                "اكتب ملاحظاتك هنا… تقدر تستخدم أدوات التنسيق تحت.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            inner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(14.dp)
                    )
                }
            }

            // شريط أدوات التنسيق + الصور (فوق الكيبورد وأزرار النظام)
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FmtBtn(Icons.Filled.Title, "عنوان") { applyFormat("## ") }
                    FmtBtn(Icons.AutoMirrored.Filled.FormatListBulleted, "نقاط") { applyFormat("- ") }
                    FmtBtn(Icons.Filled.FormatListNumbered, "ترقيم") { applyFormat("1. ") }
                    FmtBtn(Icons.Filled.FormatBold, "غامق") { applyFormat("**", wrap = true) }
                    FmtBtn(Icons.Filled.FormatQuote, "اقتباس") { applyFormat("> ") }
                    FmtBtn(Icons.Filled.HorizontalRule, "فاصل") { applyFormat("\n---\n") }
                    Spacer(Modifier.width(8.dp))
                    FmtBtn(Icons.Filled.PhotoLibrary, "من المعرض", tint = MaterialTheme.colorScheme.primary) {
                        galleryLauncher.launch(arrayOf("image/*"))
                    }
                    FmtBtn(Icons.Filled.PhotoCamera, "كاميرا", tint = MaterialTheme.colorScheme.primary) {
                        val f = vm.files.newImageFile(note.level, note.elementId)
                        pendingCameraFile = f
                        cameraLauncher.launch(vm.files.uriFor(f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FmtBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

@Composable
private fun ImageThumb(path: String, onRemove: () -> Unit) {
    Box(Modifier.size(96.dp)) {
        val bmp = rememberThumb(path)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxSize()
        ) {
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Surface(
            onClick = onRemove,
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "حذف الصورة", modifier = Modifier.padding(4.dp))
        }
    }
}
