package com.corewall.qaqc.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.domain.NotesLayout
import com.corewall.qaqc.domain.NotesView
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/**
 * شاشة الملاحظات.
 *
 * التخطيط شبكة متدرّجة (staggered) مش شبكة منتظمة: الملاحظات أطوالها
 * مختلفة، والشبكة المنتظمة كانت هتقصّ الطويلة أو تسيب فراغ تحت القصيرة.
 * المتدرّجة بتخلّي طول الكارت يقول قد إيه جوّه الملاحظة — معلومة مجانية
 * قبل ما تفتحها.
 *
 * وفيه وضع قايمة كمان: على تابلت أو مع ملاحظات نصّها طويل، الصف الكامل
 * بيقري أحسن من عمودين ضيقين.
 */
@Composable
fun NotesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val notes by vm.notesStore.visible.collectAsStateWithLifecycle()
    val labelsByNote by vm.notesStore.labelsByNote.collectAsStateWithLifecycle()
    val labels by vm.notesStore.noteLabels.collectAsStateWithLifecycle()
    val view by vm.notesStore.view.collectAsStateWithLifecycle()
    val layout by vm.notesStore.layout.collectAsStateWithLifecycle()
    val labelFilter by vm.notesStore.labelFilter.collectAsStateWithLifecycle()
    val counts by vm.notesStore.counts.collectAsStateWithLifecycle()
    val query by vm.notesStore.query.collectAsStateWithLifecycle()

    var searchOpen by remember { mutableStateOf(false) }
    var labelsOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<NoteEntity?>(null) }

    // في المهملات الضغطة العادية بتفتح الخيارات مش المحرّر: تعديل ملاحظة
    // محذوفة معناه إنها رجعت من غير ما المستخدم يقول كده.
    val open: (NoteEntity) -> Unit = { note ->
        if (view == NotesView.TRASH) optionsFor = note
        else vm.openNoteEditor(note.elementId, note)
    }

    // التثبيت بيقسّم القايمة لقسمين. في الأرشيف والمهملات مفيش تثبيت،
    // فالعناوين بتختفي بدل ما تفضل قاعدة فاضية.
    val pinned = remember(notes) { notes.filter { it.pinned } }
    val others = remember(notes) { notes.filterNot { it.pinned } }
    val sectioned = view == NotesView.ACTIVE && pinned.isNotEmpty()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            NotesTopBar(
                view = view,
                layout = layout,
                counts = counts,
                onSearch = { searchOpen = true },
                onToggleLayout = { vm.notesStore.toggleLayout() },
                onLabels = { labelsOpen = true },
                onView = { vm.notesStore.setView(it) },
                onEmptyTrash = { vm.notesStore.emptyTrash() }
            )

            if (labels.isNotEmpty() && view == NotesView.ACTIVE) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.lg, vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    CwChip(
                        label = "الكل",
                        selected = labelFilter == null,
                        onClick = { vm.notesStore.setLabelFilter(null) }
                    )
                    labels.forEach { label ->
                        CwChip(
                            label = label.name,
                            selected = labelFilter == label.id,
                            onClick = {
                                vm.notesStore.setLabelFilter(if (labelFilter == label.id) null else label.id)
                            }
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                NotesEmptyState(
                    view = view,
                    filtered = labelFilter != null || query.isNotBlank(),
                    onCreate = { vm.createNote() },
                    onClearFilter = {
                        vm.notesStore.setLabelFilter(null)
                        vm.notesStore.setQuery("")
                    }
                )
            } else if (layout == NotesLayout.GRID) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(GRID_MIN),
                    contentPadding = PaddingValues(
                        start = Space.lg, end = Space.lg,
                        top = Space.sm, bottom = Space.bottomInset
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalItemSpacing = Space.sm
                ) {
                    if (sectioned) {
                        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            SectionLabel("مثبّتة")
                        }
                        items(pinned, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                labels = labelsByNote[note.id].orEmpty(),
                                compact = true,
                                onClick = { open(note) },
                                onLongClick = { optionsFor = note }
                            )
                        }
                        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            SectionLabel("باقي الملاحظات")
                        }
                    }
                    items(if (sectioned) others else notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            labels = labelsByNote[note.id].orEmpty(),
                            compact = true,
                            onClick = { open(note) },
                            onLongClick = { optionsFor = note }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.lg, end = Space.lg,
                        top = Space.sm, bottom = Space.bottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    if (sectioned) {
                        item(key = "h-pinned") { SectionLabel("مثبّتة") }
                        items(pinned, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                labels = labelsByNote[note.id].orEmpty(),
                                compact = false,
                                onClick = { open(note) },
                                onLongClick = { optionsFor = note }
                            )
                        }
                        item(key = "h-others") { SectionLabel("باقي الملاحظات") }
                    }
                    items(if (sectioned) others else notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            labels = labelsByNote[note.id].orEmpty(),
                            compact = false,
                            onClick = { open(note) },
                            onLongClick = { optionsFor = note }
                        )
                    }
                }
            }
        }

        // زرار الإنشاء — في المهملات والأرشيف مالوش معنى.
        if (view == NotesView.ACTIVE) {
            QuickCreate(
                onText = { vm.createNote(NoteEntity.KIND_TEXT) },
                onChecklist = { vm.createNote(NoteEntity.KIND_CHECKLIST) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.md)
            )
        }
    }

    if (searchOpen) {
        NoteSearchScreen(vm = vm, onClose = { searchOpen = false })
    }

    if (labelsOpen) {
        NoteLabelsSheet(vm = vm, onDismiss = { labelsOpen = false })
    }

    optionsFor?.let { note ->
        NoteOptionsSheet(
            vm = vm,
            // الملاحظة بتتقرا من القايمة الحيّة عشان اللون والتثبيت يتحدّثوا
            // في الورقة نفسها وهي مفتوحة.
            note = notes.firstOrNull { it.id == note.id } ?: note,
            onDismiss = { optionsFor = null },
            onNoteGone = { optionsFor = null }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalCwColors.current
    Text(
        text,
        style = CwText.codeSmall,
        color = c.textTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Space.sm, bottom = Space.xxs)
    )
}

/**
 * مدخل تدوين سريع ثابت في منطقة الإبهام. الدخول على النص مباشر، وقائمة
 * التحقق لها زر مستقل كي لا يختفي عمل الإنشاء الشائع داخل قائمة عائمة.
 */
@Composable
private fun QuickCreate(
    onText: () -> Unit,
    onChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    androidx.compose.material3.Surface(
        onClick = onText,
        color = c.surface,
        contentColor = c.textPrimary,
        shape = Radius.pill,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "ملاحظة جديدة", tint = c.accent)
            Spacer(Modifier.width(Space.md))
            Text("دوّن ملاحظة…", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = onChecklist) {
                Icon(Icons.Filled.Checklist, contentDescription = "قائمة تحقق جديدة", tint = c.accent)
            }
        }
    }
}

@Composable
private fun NotesEmptyState(
    view: NotesView,
    filtered: Boolean,
    onCreate: () -> Unit,
    onClearFilter: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            filtered -> CwEmptyState(
                icon = Icons.Filled.Search,
                title = "مفيش نتايج",
                detail = "جرّب كلمة تانية أو شيل الفلتر.",
                action = { CwButton("شيل الفلتر", onClearFilter) }
            )
            view == NotesView.TRASH -> CwEmptyState(
                icon = Icons.Filled.DeleteForever,
                title = "المهملات فاضية",
                detail = "اللي بتحذفه بييجي هنا، وبيتشال لوحده بعد ٣٠ يوم."
            )
            view == NotesView.ARCHIVE -> CwEmptyState(
                icon = Icons.Filled.Label,
                title = "مفيش حاجة في الأرشيف",
                detail = "الأرشفة بتشيل الملاحظة من الشاشة الرئيسية من غير ما تحذفها."
            )
            else -> CwEmptyState(
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                title = "ابدأ أول ملاحظة",
                detail = "ملاحظة سريعة من الموقع، قايمة تشيك ليست، أو ملاحظة جودة على عنصر.",
                action = { CwButton("اكتب ملاحظة", onCreate) }
            )
        }
    }
}

private val GRID_MIN = 168.dp
