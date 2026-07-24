package com.corewall.qaqc.ui.dataroom

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.model.PlanElement
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH)

internal fun attachmentIconFor(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> Icons.Filled.PictureAsPdf
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> Icons.Filled.Image
        else -> Icons.Filled.InsertDriveFile
    }
}

/**
 * محتوى عدسة الداتا جوّه الـSheet الموحّد: كومنتات ومرفقات العنصر
 * في الدور الحالي **بس** (كل دور معزول) — فتح/مشاركة/حذف وإضافة.
 */
@Composable
fun DataSheetContent(vm: MainViewModel, element: PlanElement) {
    val context = LocalContext.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    val items = attachments.filter { it.elementId == element.id && it.level == level }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addDataFiles(element.id, uris)
            Toast.makeText(context, "جاري نسخ ${uris.size} ملف…", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "${names[element.id] ?: element.id} — دور $level",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                "مفيش كومنتات أو مرفقات للعنصر ده في الدور ده لسه.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        items.forEach { item ->
            AttachmentRow(vm, item)
            HorizontalDivider()
        }

        Spacer(Modifier.height(12.dp))
        var newComment by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newComment,
                onValueChange = { newComment = it },
                label = { Text("اكتب كومنت…") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { vm.addDataComment(element.id, newComment); newComment = "" },
                enabled = newComment.isNotBlank()
            ) {
                Icon(Icons.Filled.Send, contentDescription = "إضافة")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("أرفق صور / ملفات")
        }
    }
}

@Composable
private fun AttachmentRow(vm: MainViewModel, item: ElementAttachmentEntity) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.type == ElementAttachmentEntity.TYPE_COMMENT) Icons.AutoMirrored.Filled.Comment
            else attachmentIconFor(item.text),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                timeFormat.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.type == ElementAttachmentEntity.TYPE_FILE && item.filePath != null) {
            val file = File(item.filePath)
            IconButton(onClick = {
                if (file.extension.equals("pdf", ignoreCase = true)) vm.openPdf(file.absolutePath)
                else if (!vm.files.openExternally(file)) {
                    Toast.makeText(context, "مفيش تطبيق يقدر يفتح الملف ده", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(attachmentIconFor(item.text), contentDescription = "فتح")
            }
            IconButton(onClick = { vm.files.share(file) }) {
                Icon(Icons.Filled.Share, contentDescription = "مشاركة")
            }
        }
        IconButton(onClick = { vm.deleteAttachment(item) }) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "حذف",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
