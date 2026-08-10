package com.corewall.qaqc.ui.notes

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteLabelEntity
import com.corewall.qaqc.domain.NotesLayout
import com.corewall.qaqc.domain.NotesView
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space

/**
 * شريط الملاحظات العلوي.
 *
 * القسم (ملاحظات / أرشيف / مهملات) في قايمة مش تبويبات: تلاتة تبويبات
 * فوق تاخد صف كامل عشان قسمين منهم المستخدم بيدخلهم مرة في الشهر.
 */
@Composable
fun NotesTopBar(
    view: NotesView,
    layout: NotesLayout,
    counts: Map<NotesView, Int>,
    onSearch: () -> Unit,
    onToggleLayout: () -> Unit,
    onLabels: () -> Unit,
    onView: (NotesView) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val c = LocalCwColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.sm)
        ) {
            Text(
                view.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            val count = counts[view] ?: 0
            Text(
                if (count == 0) "مفيش حاجة" else "$count ملاحظة",
                style = CwText.codeSmall,
                color = c.textTertiary
            )
        }

        CwIconButton(Icons.Filled.Search, "دوّر في الملاحظات", onSearch)
        CwIconButton(
            if (layout == NotesLayout.GRID) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
            if (layout == NotesLayout.GRID) "عرض كقايمة" else "عرض كشبكة",
            onToggleLayout
        )
        Box {
            CwIconButton(Icons.Filled.MoreVert, "خيارات", { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("الملاحظات") },
                    leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
                    onClick = { menuOpen = false; onView(NotesView.ACTIVE) }
                )
                DropdownMenuItem(
                    text = { Text("الأرشيف (${counts[NotesView.ARCHIVE] ?: 0})") },
                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                    onClick = { menuOpen = false; onView(NotesView.ARCHIVE) }
                )
                DropdownMenuItem(
                    text = { Text("المهملات (${counts[NotesView.TRASH] ?: 0})") },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                    onClick = { menuOpen = false; onView(NotesView.TRASH) }
                )
                DropdownMenuItem(
                    text = { Text("إدارة التصنيفات") },
                    leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                    onClick = { menuOpen = false; onLabels() }
                )
                if (view == NotesView.TRASH && (counts[NotesView.TRASH] ?: 0) > 0) {
                    DropdownMenuItem(
                        text = { Text("فضّي المهملات") },
                        leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                        onClick = { menuOpen = false; onEmptyTrash() }
                    )
                }
            }
        }
    }
}

/**
 * بحث ملء الشاشة.
 *
 * بيغطّي الشاشة كلها عن قصد: البحث في الملاحظات مهمة قائمة بذاتها، مش
 * فلتر على قايمة. والنتايج بتتحدّث مع كل حرف لأن الفلترة بتحصل في
 * الخلفية أصلاً (شوف `visibleNotes` في الـViewModel).
 */
@Composable
fun NoteSearchScreen(vm: MainViewModel, onClose: () -> Unit) {
    val c = LocalCwColors.current
    val notes by vm.visibleNotes.collectAsStateWithLifecycle()
    val labelsByNote by vm.labelsByNote.collectAsStateWithLifecycle()
    val query by vm.notesQuery.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // البحث بيتفضّى مع الخروج: قايمة الملاحظات ورا الشاشة دي بتستخدم
    // نفس الفلتر، وسيبانه شغّال معناه إن المستخدم يقفل البحث ويلاقي
    // ملاحظاته ناقصة من غير ما يعرف ليه.
    androidx.activity.compose.BackHandler(enabled = true) {
        vm.setNotesQuery("")
        onClose()
    }

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.surface, shadowElevation = Elevation.raised) {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CwIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack, "رجوع",
                        { vm.setNotesQuery(""); onClose() }
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = Sizes.control)
                            .padding(horizontal = Space.sm),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "دوّر في الملاحظات…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textTertiary
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { vm.setNotesQuery(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                                .copy(color = c.textPrimary),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                        )
                    }
                    if (query.isNotEmpty()) {
                        CwIconButton(Icons.Filled.Close, "امسح", { vm.setNotesQuery("") })
                    }
                }
            }

            if (query.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "اكتب كلمة تدوّر بيها في العناوين والنصوص والبنود والتصنيفات.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textTertiary,
                        modifier = Modifier.padding(Space.xl)
                    )
                }
            } else if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "مفيش ملاحظة فيها «$query»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textTertiary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            labels = labelsByNote[note.id].orEmpty(),
                            compact = false,
                            onClick = {
                                vm.setNotesQuery("")
                                onClose()
                                vm.openNoteEditor(note.elementId, note)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** إدارة التصنيفات — إنشاء وإعادة تسمية وحذف. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteLabelsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labels by vm.noteLabels.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteLabelEntity?>(null) }
    var editText by remember { mutableStateOf("") }

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
                .padding(bottom = Space.lg)
        ) {
            Text(
                "التصنيفات",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary
            )
            Spacer(Modifier.height(Space.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CwField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = "تصنيف جديد",
                    placeholder = "جودة · RFI · عاجل",
                    modifier = Modifier.weight(1f)
                )
                CwIconButton(
                    Icons.Filled.Add, "أضف",
                    { vm.createNoteLabel(newLabel); newLabel = "" },
                    tint = c.accent,
                    enabled = newLabel.isNotBlank()
                )
            }

            Spacer(Modifier.height(Space.md))

            if (labels.isEmpty()) {
                Text(
                    "التصنيفات بتخلّيك تلمّ ملاحظات الموقع بعيد عن ملاحظات المكتب.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            } else {
                LazyColumn(Modifier.heightIn(max = LABELS_MAX)) {
                    items(labels, key = { it.id }) { label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            Icon(
                                Icons.Filled.Label,
                                contentDescription = null,
                                tint = c.textTertiary
                            )
                            if (editing?.id == label.id) {
                                CwField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    label = "الاسم",
                                    modifier = Modifier.weight(1f)
                                )
                                CwIconButton(
                                    Icons.Filled.Add, "احفظ الاسم",
                                    {
                                        vm.renameNoteLabel(label, editText)
                                        editing = null
                                    },
                                    tint = c.accent
                                )
                            } else {
                                Text(
                                    label.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            editing = label
                                            editText = label.name
                                        }
                                )
                                CwIconButton(
                                    Icons.Filled.Delete, "احذف التصنيف",
                                    { vm.deleteNoteLabel(label) },
                                    tint = c.danger.fg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LABELS_MAX = 320.dp
