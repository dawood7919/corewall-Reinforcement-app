package com.corewall.qaqc.ui.creative

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.creative.CreativeBlock
import com.corewall.qaqc.creative.CreativeBlockKind
import com.corewall.qaqc.creative.CreativeDocumentContent
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/** محرر مسودة من صفحة كاملة: يبقى المصدر قابلاً للتعديل قبل توليد PDF. */
@Composable
fun CreativeDocumentEditorScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val entity by vm.editingCreativeDocument.collectAsStateWithLifecycle()
    val c = LocalCwColors.current
    if (entity == null) return
    val initial = remember(entity!!.id, entity!!.updatedAt) { vm.creativeDocumentContent(entity!!) }
    var title by remember(entity!!.id, entity!!.updatedAt) { mutableStateOf(initial.title) }
    var blocks by remember(entity!!.id, entity!!.updatedAt) { mutableStateOf(initial.blocks) }
    val exportStatus by vm.creativeExportState.collectAsStateWithLifecycle()
    fun content() = CreativeDocumentContent(title = title, subtitle = initial.subtitle, blocks = blocks, accentArgb = initial.accentArgb)
    BackHandler { vm.closeCreativeDocument() }

    Column(modifier.fillMaxSize()) {
        Surface(color = c.surface, shadowElevation = 3f, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::closeCreativeDocument) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("رجوع") }
                    Spacer(Modifier.width(Space.sm))
                    Text("محرر التقرير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(onClick = { vm.saveCreativeDocument(content()) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(5.dp)); Text("حفظ") }
                    Button(onClick = { vm.saveCreativeDocument(content()); vm.exportCreativeDocumentPdf() }, colors = ButtonDefaults.buttonColors(containerColor = c.accent), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("تصدير PDF") }
                }
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(onClick = { vm.saveCreativeDocument(content()); vm.exportCreativeDocumentWord() }, modifier = Modifier.weight(1f)) { Text("Word") }
                    OutlinedButton(onClick = { vm.saveCreativeDocument(content()); vm.exportCreativeDocumentImage() }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(4.dp)); Text("صورة") }
                    OutlinedButton(onClick = vm::exportCreativeDocumentPackage, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.FolderZip, null); Spacer(Modifier.width(4.dp)); Text("حزمة") }
                }
                Spacer(Modifier.height(Space.xs))
                OutlinedButton(onClick = vm::shareLatestCreativeExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(4.dp)); Text("مشاركة آخر نسخة") }
                exportStatus?.let { Text(it, Modifier.padding(top = Space.xs), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxSize()) {
            item("title") {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان التقرير") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                EditableBlock(block = block, onChange = { updated -> blocks = blocks.toMutableList().also { it[index] = updated } })
            }
            item("preview-title") { Text("معاينة المحتوى", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary) }
            item("preview") { DocumentPreview(content()) }
        }
    }
}

@Composable
private fun EditableBlock(block: CreativeBlock, onChange: (CreativeBlock) -> Unit) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(blockLabel(block.kind), style = MaterialTheme.typography.labelLarge, color = c.accent)
            when (block.kind) {
                CreativeBlockKind.TABLE -> OutlinedTextField(
                    value = block.rows.joinToString("\n") { row -> row.cells.joinToString(" | ") },
                    onValueChange = { raw -> onChange(block.copy(rows = raw.lines().filter { it.isNotBlank() }.map { com.corewall.qaqc.creative.CreativeTableRow(it.split("|").map(String::trim)) })) },
                    label = { Text("صفوف الجدول — افصل الأعمدة بعلامة |") }, minLines = 3, modifier = Modifier.fillMaxWidth()
                )
                CreativeBlockKind.BULLETS -> OutlinedTextField(
                    value = block.items.joinToString("\n"),
                    onValueChange = { raw -> onChange(block.copy(items = raw.lines().filter { it.isNotBlank() })) },
                    label = { Text("كل سطر يمثل بنداً") }, minLines = 3, modifier = Modifier.fillMaxWidth()
                )
                else -> OutlinedTextField(value = block.text, onValueChange = { onChange(block.copy(text = it)) }, label = { Text("المحتوى") }, minLines = if (block.kind == CreativeBlockKind.HEADING) 1 else 3, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DocumentPreview(content: CreativeDocumentContent) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeLg, color = c.surface, tonalElevation = 2f, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(content.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text(content.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            content.blocks.forEach { block ->
                when (block.kind) {
                    CreativeBlockKind.HEADING -> Text(block.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.accent)
                    CreativeBlockKind.BULLETS -> block.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
                    CreativeBlockKind.TABLE -> block.rows.forEach { row -> Text(row.cells.joinToString("  |  "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                    else -> Text(block.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
        }
    }
}

private fun blockLabel(kind: String) = when (kind) {
    CreativeBlockKind.HEADING -> "عنوان قسم"
    CreativeBlockKind.BULLETS -> "قائمة"
    CreativeBlockKind.TABLE -> "جدول"
    else -> "نص"
}
