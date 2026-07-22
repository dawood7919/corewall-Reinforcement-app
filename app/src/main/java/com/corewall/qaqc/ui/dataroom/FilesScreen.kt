package com.corewall.qaqc.ui.dataroom

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.LevelSelector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH)

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * قسم "الملفات": مدير ملفات للدور المختار — مجلدات، رفع ملفات مباشر،
 * فتح (PDF جوّه التطبيق) / مشاركة / حذف. كل دور له مساحته الخاصة.
 */
@Composable
fun FilesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    // مسار التنقل الحالي جوّه مجلد الدور
    var subPath by remember(level) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    val currentDir = remember(level, subPath, refresh) {
        val base = vm.files.levelDir(level)
        if (subPath.isEmpty()) base else File(base, subPath)
    }
    val entries = remember(currentDir, refresh) { vm.files.list(currentDir) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val copied = vm.files.importUris(uris, currentDir)
            refresh++
            Toast.makeText(context, "اترفع ${copied.size} ملف ✓", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LevelSelector(
                levels = vm.levels,
                current = level,
                onPick = vm::setLevel,
                onStep = vm::stepLevel
            )
        }

        // شريط المسار + أزرار الإجراءات
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subPath.isNotEmpty()) {
                IconButton(onClick = {
                    subPath = subPath.substringBeforeLast('/', "")
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            }
            Text(
                "دور $level" + if (subPath.isEmpty()) "" else " / $subPath",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { newFolderDialog = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "مجلد جديد")
            }
            IconButton(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.UploadFile, contentDescription = "رفع ملفات")
            }
        }

        if (entries.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "المجلد فاضي — ارفع شوب دروينج، BBS، دليفري نوت،\nملفات أوتوكاد، صور… أي حاجة تخص الدور ده.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("رفع ملفات")
                    }
                    OutlinedButton(onClick = { newFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("مجلد جديد")
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            items(entries) { file ->
                FileRow(
                    vm = vm,
                    file = file,
                    onOpenFolder = {
                        subPath = if (subPath.isEmpty()) file.name else "$subPath/${file.name}"
                    },
                    onDelete = { deleteTarget = file }
                )
            }
        }
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("مجلد جديد") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المجلد") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.files.createFolder(currentDir, name)
                        refresh++
                        newFolderDialog = false
                    }
                ) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("إلغاء") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف ${target.name}؟") },
            text = {
                Text(
                    if (target.isDirectory) "المجلد وكل اللي جوّاه هيتحذف نهائي."
                    else "الملف هيتحذف نهائي."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.files.delete(target)
                    refresh++
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun FileRow(
    vm: MainViewModel,
    file: File,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Surface(
            onClick = {
                if (file.isDirectory) onOpenFolder()
                else if (file.extension.equals("pdf", ignoreCase = true)) vm.openPdf(file.absolutePath)
                else if (!vm.files.openExternally(file)) {
                    Toast.makeText(context, "مفيش تطبيق يقدر يفتح الملف ده", Toast.LENGTH_SHORT).show()
                }
            },
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        file.isDirectory -> Icons.Filled.Folder
                        file.extension.lowercase() == "pdf" -> Icons.Filled.PictureAsPdf
                        file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic") ->
                            Icons.Filled.Image
                        else -> Icons.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (file.isDirectory) "${file.listFiles()?.size ?: 0} عنصر"
                        else "${sizeText(file.length())} — ${dateFormat.format(Date(file.lastModified()))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!file.isDirectory) {
                    IconButton(onClick = { vm.files.share(file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "مشاركة")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
