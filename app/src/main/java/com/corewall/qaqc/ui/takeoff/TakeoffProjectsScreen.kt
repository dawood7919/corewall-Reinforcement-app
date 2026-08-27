package com.corewall.qaqc.ui.takeoff

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.TakeoffDrawingEntity
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.pdf.rememberPdfCover
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffProjectEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.launch

/**
 * أقسام الحصر.
 *
 * القسم هو وحدة الشغل: "عمارة الشيخ زايد"، "تشطيبات الدور الأرضي". جوّه
 * كل قسم رسمات، وعلى كل رسمة بنود حصر. مافيش أي ارتباط بأدوار المشروع
 * ولا بملفاته — الحصر بيتعمل على رسمات جاية من برّه، وإجباره على شجرة
 * المشروع كان هيخلّي المستخدم يخترع دور لكل حاجة بيحصرها.
 */
@Composable
fun TakeoffProjectsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val scope = rememberCoroutineScope()
    val projects by vm.takeoff.projects.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<TakeoffProjectEntity?>(null) }

    Box(modifier.fillMaxSize()) {
        if (projects.isEmpty() && !creating) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CwEmptyState(
                    icon = Icons.Filled.Straighten,
                    title = "ابدأ أول قسم حصر",
                    detail = "القسم بيجمّع رسمات مشروع واحد. ترفع الرسمة، تعاير " +
                        "المقياس، وتبدأ تحصر مساحات وأطوال وأعداد.",
                    action = { CwButton("قسم جديد", { creating = true }) }
                )
            }
        } else {
            // شبكة مش ليستة: القسم بيتعرّف من رسمته قبل اسمه — الأسماء
            // بتتشابه ("برج ١"، "برج ٢")، والغلاف بيفرّق من نظرة.
            // `Adaptive` مش عدد ثابت: عمودين على الموبايل وأكتر على
            // الشاشة الكبيرة أو الوضع الأفقي، من غير حسابات مقاسات.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = Space.lg, end = Space.lg,
                    top = Space.md, bottom = Space.bottomInset
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item(key = "overview", span = { GridItemSpan(maxLineSpan) }) {
                    CwCard(style = com.corewall.qaqc.ui.design.CwCardStyle.Accent, accent = c.accent) {
                        Text("حصر الكميات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Space.xs))
                        Text("أنشئ مساحة عمل مستقلة لكل مشروع، ثم أضف الرسمات وابدأ قياس المساحة والطول والعدد والحجم.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
                if (creating) {
                    item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
                        CwCard {
                            Column {
                                CwField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = "اسم القسم",
                                    placeholder = "عمارة الشيخ زايد"
                                )
                                Spacer(Modifier.height(Space.md))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    CwButton("إنشاء", {
                                        val name = newName
                                        newName = ""; creating = false
                                        scope.launch { vm.takeoff.createProject(name) }
                                    })
                                    CwButton("إلغاء", {
                                        newName = ""; creating = false
                                    }, style = CwButtonStyle.Ghost)
                                }
                            }
                        }
                    }
                }

                items(projects, key = { it.id }) { project ->
                    ProjectTile(
                        project = project,
                        loadDrawings = { vm.takeoff.drawingsOnce(project.id) },
                        onOpen = { vm.openTakeoffProject(project.id, project.name) },
                        onDelete = { confirmDelete = project }
                    )
                }
            }
        }

        if (!creating) {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = c.accent,
                contentColor = c.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "قسم جديد")
            }
        }
    }

    confirmDelete?.let { project ->
        // الحذف بيشيل الرسمات وبنودها وملفاتها من القرص — مالوش تراجع،
        // فبيتأكّد الأول.
        TakeoffConfirmSheet(
            title = "حذف «${project.name}»؟",
            detail = "هيتشال القسم وكل رسماته وبنوده وملفاته. مافيش تراجع.",
            confirmLabel = "احذف",
            onConfirm = {
                confirmDelete = null
                scope.launch { vm.takeoff.deleteProject(project.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/**
 * خانة قسم في الشبكة: غلاف أول رسمة، الاسم، وعدد الرسمات.
 *
 * الرسمات بتتقري هنا مش في الشاشة الأم عن قصد: الشبكة كسولة، فالقسم
 * اللي لسه ماوصلتش له بالتمرير مابيفتحش ملف ولا بيرسم صفحة.
 */
@Composable
private fun ProjectTile(
    project: TakeoffProjectEntity,
    loadDrawings: suspend () -> List<TakeoffDrawingEntity>,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    var drawings by remember(project.id) { mutableStateOf<List<TakeoffDrawingEntity>>(emptyList()) }
    LaunchedEffect(project.id) { drawings = runCatching { loadDrawings() }.getOrDefault(emptyList()) }

    val cover = rememberPdfCover(drawings.firstOrNull()?.filePath)

    CwCard(onClick = onOpen, contentPadding = PaddingValues(Space.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                // نسبة الورقة تقريباً — الغلاف بيفضل بنفس الشكل قبل ما
                // الصورة توصل، فالشبكة مابتنطّش وهي بتتحمّل.
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
                    if (drawings.isEmpty()) Icons.Filled.Straighten else Icons.Filled.Description,
                    contentDescription = null,
                    tint = c.textTertiary
                )
            }
            CwIconButton(
                Icons.Filled.Delete, "احذف القسم", onDelete,
                tint = c.danger.fg,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        Spacer(Modifier.height(Space.xs))
        Text(
            project.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            when (drawings.size) {
                0 -> "مفيش رسمات"
                1 -> "رسمة واحدة"
                2 -> "رسمتين"
                else -> "${drawings.size} رسمة"
            },
            style = CwText.codeSmall,
            color = c.textTertiary
        )
    }
}
