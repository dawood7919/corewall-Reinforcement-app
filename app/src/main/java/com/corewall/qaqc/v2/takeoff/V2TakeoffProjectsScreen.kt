package com.corewall.qaqc.v2.takeoff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffProjectEntity
import com.corewall.qaqc.v2.design.V2Colors
import com.corewall.qaqc.v2.design.V2Size
import com.corewall.qaqc.v2.design.V2Space
import kotlinx.coroutines.launch

/** قائمة V2 الخفيفة لأقسام الحصر؛ تستخدم Room الحالي ولا تنشئ نموذج بيانات موازياً. */
@Composable
internal fun V2TakeoffProjectsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val projects by vm.takeoff.projects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TakeoffProjectEntity?>(null) }

    Box(modifier.fillMaxSize().background(V2Colors.Canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(V2Space.sm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = V2Space.md, end = V2Space.md, top = V2Space.md, bottom = V2Space.xl * 3
            )
        ) {
            item("intro") {
                Column {
                    Text("مساحات الحصر", color = V2Colors.Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(V2Space.xxs))
                    Text(
                        "اختر قسماً، أضف الرسمات، ثم قِس بسرعة بالقلم بينما يبقى الإصبع للتنقل.",
                        color = V2Colors.InkMuted
                    )
                }
            }
            if (adding) item("add") {
                Column(
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(V2Size.corner))
                        .background(V2Colors.SurfaceRaised).padding(V2Space.md),
                    verticalArrangement = Arrangement.spacedBy(V2Space.sm)
                ) {
                    Text("قسم حصر جديد", color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("اسم القسم") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = V2Colors.Ink, unfocusedTextColor = V2Colors.Ink,
                            focusedBorderColor = V2Colors.Accent, unfocusedBorderColor = V2Colors.Outline,
                            focusedLabelColor = V2Colors.Accent, unfocusedLabelColor = V2Colors.InkMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(V2Space.xs)) {
                        Button(
                            enabled = name.isNotBlank(),
                            onClick = {
                                val value = name.trim(); name = ""; adding = false
                                scope.launch { vm.takeoff.createProject(value) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Accent, contentColor = V2Colors.AccentInk)
                        ) { Text("إنشاء") }
                        Button(
                            onClick = { name = ""; adding = false },
                            colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Surface, contentColor = V2Colors.Ink)
                        ) { Text("إلغاء") }
                    }
                }
            }
            if (projects.isEmpty() && !adding) item("empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = V2Space.xl * 2),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Straighten, null, tint = V2Colors.Accent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(V2Space.sm))
                    Text("ابدأ أول قسم حصر", color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
                    Text("يحتوي القسم على الرسمات وبنود القياس الخاصة به.", color = V2Colors.InkMuted)
                }
            }
            items(projects, key = { it.id }) { project ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(V2Size.corner))
                        .background(V2Colors.Surface).clickable { vm.openTakeoffProject(project.id, project.name) }
                        .padding(V2Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Straighten, null, tint = V2Colors.Accent)
                    Spacer(Modifier.width(V2Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(project.name, color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
                        if (project.note.isNotBlank()) Text(project.note, color = V2Colors.InkMuted)
                    }
                    IconButton(onClick = { deleting = project }) {
                        Icon(Icons.Filled.Delete, "حذف القسم", tint = V2Colors.Danger)
                    }
                }
            }
        }
        if (!adding) FloatingActionButton(
            onClick = { adding = true },
            containerColor = V2Colors.Accent,
            contentColor = V2Colors.AccentInk,
            modifier = Modifier.align(Alignment.BottomEnd).padding(V2Space.lg)
        ) { Icon(Icons.Filled.Add, "قسم جديد") }
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = V2Colors.SurfaceRaised,
            titleContentColor = V2Colors.Ink,
            textContentColor = V2Colors.InkMuted,
            title = { Text("حذف «${project.name}»؟") },
            text = { Text("سيُحذف القسم وكل رسماته وبنود الحصر التابعة له.") },
            confirmButton = { Button(onClick = { deleting = null; scope.launch { vm.takeoff.deleteProject(project.id) } }, colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Danger)) { Text("حذف") } },
            dismissButton = { Button(onClick = { deleting = null }, colors = ButtonDefaults.buttonColors(containerColor = V2Colors.Surface)) { Text("إلغاء") } }
        )
    }
}
