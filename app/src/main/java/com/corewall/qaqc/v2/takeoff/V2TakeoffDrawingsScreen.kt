package com.corewall.qaqc.v2.takeoff

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffDrawingEntity
import com.corewall.qaqc.v2.design.V2Colors
import com.corewall.qaqc.v2.design.V2Size
import com.corewall.qaqc.v2.design.V2Space
import kotlinx.coroutines.launch

/** رسمات القسم في V2؛ الاستيراد والتخزين يظلان عبر TakeoffStore نفسه. */
@Composable
internal fun V2TakeoffDrawingsScreen(vm: MainViewModel, projectId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawings by remember(projectId) { vm.takeoff.drawings(projectId) }.collectAsStateWithLifecycle(emptyList())
    val items by remember(projectId) { vm.takeoff.projectItems(projectId) }.collectAsStateWithLifecycle(emptyList())
    var failure by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TakeoffDrawingEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = displayName(context, uri)
        scope.launch { if (vm.takeoff.addDrawing(projectId, uri, name) == null) failure = true }
    }

    Box(modifier.fillMaxSize().background(V2Colors.Canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(V2Space.md),
            verticalArrangement = Arrangement.spacedBy(V2Space.sm)
        ) {
            item("intro") {
                Column {
                    Text("رسمات الحصر", color = V2Colors.Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(V2Space.xxs))
                    Text("ارفع PDF، عاير المقياس مرة، ثم ابدأ قياس الطول والمساحة والعدد والحجم.", color = V2Colors.InkMuted)
                }
            }
            if (drawings.isEmpty()) item("empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = V2Space.xl * 2),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.UploadFile, null, tint = V2Colors.Accent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(V2Space.sm))
                    Text("ارفع أول رسمة", color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
                    Text("تُحفظ الرسمة محلياً داخل القسم.", color = V2Colors.InkMuted)
                }
            }
            items(drawings, key = { it.id }) { drawing ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(V2Size.corner))
                        .background(V2Colors.Surface)
                        .clickable { vm.openTakeoffEditor(drawing.id, drawing.filePath, drawing.name) }
                        .padding(V2Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = V2Colors.Danger)
                    Spacer(Modifier.width(V2Space.sm))
                    Column(Modifier.padding(end = V2Space.sm)) {
                        Text(drawing.name, color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
                        val count = items.count { it.drawingId == drawing.id && it.parentId == null }
                        Text(if (count == 0) "لا توجد بنود بعد" else "$count بند حصر", color = V2Colors.InkMuted)
                    }
                    IconButton(onClick = { deleting = drawing }) { Icon(Icons.Filled.Delete, "حذف الرسمة", tint = V2Colors.Danger) }
                }
            }
        }
        FloatingActionButton(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            containerColor = V2Colors.Accent,
            contentColor = V2Colors.AccentInk,
            modifier = Modifier.align(Alignment.BottomEnd).padding(V2Space.lg)
        ) { Icon(Icons.Filled.Add, "رفع PDF") }
    }
    if (failure) AlertDialog(
        onDismissRequest = { failure = false }, containerColor = V2Colors.SurfaceRaised,
        titleContentColor = V2Colors.Ink, textContentColor = V2Colors.InkMuted,
        title = { Text("تعذر نسخ ملف الرسم") }, text = { Text("اختر ملف PDF آخر وحاول مرة أخرى.") },
        confirmButton = { Button(onClick = { failure = false }, colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Accent, contentColor = V2Colors.AccentInk)) { Text("حسناً") } }
    )
    deleting?.let { drawing -> AlertDialog(
        onDismissRequest = { deleting = null }, containerColor = V2Colors.SurfaceRaised,
        titleContentColor = V2Colors.Ink, textContentColor = V2Colors.InkMuted,
        title = { Text("حذف «${drawing.name}»؟") }, text = { Text("سيُحذف الملف وبنود الحصر المرتبطة به.") },
        confirmButton = { Button(onClick = { deleting = null; scope.launch { vm.takeoff.deleteDrawing(drawing) } }, colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Danger)) { Text("حذف") } },
        dismissButton = { Button(onClick = { deleting = null }, colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Surface)) { Text("إلغاء") } }
    ) }
}

private fun displayName(context: Context, uri: android.net.Uri): String = context.contentResolver.query(
    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) ?: "رسمة.pdf" else "رسمة.pdf"
} ?: uri.lastPathSegment?.substringAfterLast('/') ?: "رسمة.pdf"
