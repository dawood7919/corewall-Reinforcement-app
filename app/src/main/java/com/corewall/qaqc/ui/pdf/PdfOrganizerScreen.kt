package com.corewall.qaqc.ui.pdf

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfSessionHolder
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfOps
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * تنظيم الصفحات — ترتيب وتدوير وحذف وتكرار واستخراج.
 *
 * الشاشة بتشتغل على **خطة** مش على الملف: كل تعديل بيغيّر قايمة في
 * الذاكرة، والملف مابيتلمسش غير لما تضغط حفظ. ده اللي بيخلّي التراجع
 * ممكن (زرار "رجّع الأصل")، وبيخلّي المستخدم يجرّب ترتيب على رسمة تنفيذية
 * من غير ما يخاطر بيها.
 */
@Composable
fun PdfOrganizerScreen(
    path: String,
    onClose: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }

    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    val holder = remember(path) { PdfSessionHolder() }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var busy by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        PdfOps.ensureInit(context)
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, file) }
        }
        // الماسك بيتصرّف لو الشاشة اتقفلت والمستند لسه بيتفتح — من غيره
        // المستند الأصلي وخيط الرندر بيفضلوا عايشين للأبد.
        opened.onSuccess { if (holder.accept(it)) session = it }
        opened.onFailure { e ->
            error = (e as? PdfOpenException)?.userMessage ?: "مقدرناش نفتح الملف ده"
        }
    }

    DisposableEffect(path) { onDispose { holder.dispose() } }

    val active = session
    if (error != null || active == null) {
        OrganizerLoading(error, onClose)
        return
    }

    val thumbs = remember(active) { ThumbnailCache(active) }
    DisposableEffect(active) { onDispose { thumbs.clear() } }

    // الخطة: كل عنصر = (صفحة من الأصل، تدوير إضافي)
    val plan = remember(active) {
        mutableStateListOf<PdfOps.PagePlan>().apply {
            addAll((0 until active.pageCount).map { PdfOps.PagePlan(it) })
        }
    }
    val selected = remember(active) { mutableStateListOf<Int>() }

    fun resetPlan() {
        plan.clear()
        plan.addAll((0 until active.pageCount).map { PdfOps.PagePlan(it) })
        selected.clear()
    }

    fun rotate(delta: Int) {
        selected.forEach { i ->
            plan[i] = plan[i].copy(extraRotation = plan[i].extraRotation + delta)
        }
    }

    fun duplicate() {
        // من الآخر للأول عشان الفهارس اللي لسه ما اتعملتش مايزحلقوش.
        selected.sortedDescending().forEach { i -> plan.add(i + 1, plan[i]) }
        selected.clear()
    }

    fun remove() {
        if (selected.size >= plan.size) {
            Toast.makeText(context, "لازم تسيب صفحة واحدة على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        selected.sortedDescending().forEach { plan.removeAt(it) }
        selected.clear()
    }

    /** بينقل المختار خطوة. الترتيب بيتغيّر والاختيار بيمشي معاه. */
    fun move(step: Int) {
        val order = if (step < 0) selected.sorted() else selected.sortedDescending()
        val moved = ArrayList<Int>(selected.size)
        for (i in order) {
            val target = i + step
            if (target !in plan.indices) { moved += i; continue }
            val tmp = plan[target]
            plan[target] = plan[i]
            plan[i] = tmp
            moved += target
        }
        selected.clear()
        selected.addAll(moved)
    }

    /**
     * [openNew] بيفتح الناتج بعد ما يخلص.
     *
     * ده مش تزويق: بعد استخراج ٦ صفحات، السؤال التالي دايماً "طلعت
     * صح؟". من غير الفتح، المستخدم لازم يقفل ويدوّر على الملف بنفسه.
     */
    fun runOp(label: String, openNew: Boolean, block: suspend () -> Result<File>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = block()
            busy = false
            result
                .onSuccess { out ->
                    Toast.makeText(context, "$label ✓ — ${out.name}", Toast.LENGTH_LONG).show()
                    if (openNew) onOpenFile(out.absolutePath)
                }
                .onFailure { e ->
                    Toast.makeText(context, "فشل $label: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    Surface(Modifier.fillMaxSize(), color = c.surfaceAlt) {
        Column(Modifier.fillMaxSize()) {

            OrganizerTopBar(
                name = file.name,
                pageCount = plan.size,
                selectedCount = selected.size,
                dirty = isDirty(plan, active.pageCount),
                busy = busy,
                onClose = onClose,
                onReset = { resetPlan() },
                onSaveCopy = {
                    runOp("الحفظ كنسخة", openNew = true) {
                        val dest = uniqueSibling(file, "منظَّم")
                        PdfOps.applyPagePlan(file, dest, plan.toList()).map { dest }
                    }
                },
                onSaveOver = {
                    runOp("الحفظ", openNew = false) {
                        overwrite(file) { temp -> PdfOps.applyPagePlan(file, temp, plan.toList()) }
                    }
                }
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL_MIN),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                itemsIndexed(plan) { index, step ->
                    PageCell(
                        position = index + 1,
                        sourcePage = step.source,
                        rotation = step.extraRotation,
                        selected = index in selected,
                        cache = thumbs,
                        onClick = {
                            if (index in selected) selected.remove(index) else selected.add(index)
                        }
                    )
                }
            }

            if (selected.isNotEmpty()) {
                OrganizerActions(
                    count = selected.size,
                    onRotateLeft = { rotate(-90) },
                    onRotateRight = { rotate(90) },
                    onDuplicate = { duplicate() },
                    onDelete = { remove() },
                    onMoveBack = { move(-1) },
                    onMoveForward = { move(1) },
                    onExtract = {
                        val pages = selected.sorted().map { plan[it].source }
                        runOp("الاستخراج", openNew = true) {
                            val dest = uniqueSibling(file, "صفحات")
                            PdfOps.extract(file, dest, pages).map { dest }
                        }
                    }
                )
            }
        }
    }
}

/** الخطة اتغيّرت عن الأصل؟ الزرار مايبقاش شغّال من غير سبب. */
private fun isDirty(plan: List<PdfOps.PagePlan>, originalCount: Int): Boolean =
    plan.size != originalCount ||
        plan.withIndex().any { (i, step) -> step.source != i || step.extraRotation != 0 }

// ══════════════════════════════════════════════════════════════ الشرائط

@Composable
private fun OrganizerTopBar(
    name: String,
    pageCount: Int,
    selectedCount: Int,
    dirty: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onSaveCopy: () -> Unit,
    onSaveOver: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(color = c.surface, shadowElevation = Elevation.raised) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.sm)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (selectedCount > 0) "$pageCount صفحة · $selectedCount مختارة"
                    else "$pageCount صفحة",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    color = c.accent,
                    strokeWidth = CwStroke.thick,
                    modifier = Modifier.padding(horizontal = Space.sm).size(SPINNER)
                )
            }
            CwIconButton(
                Icons.Filled.Restore, "رجّع الترتيب الأصلي", onReset,
                enabled = dirty && !busy
            )
            CwIconButton(
                Icons.Filled.ContentCopy, "احفظ كنسخة جديدة", onSaveCopy,
                enabled = dirty && !busy
            )
            CwIconButton(
                Icons.Filled.Check, "احفظ فوق الملف", onSaveOver,
                tint = c.accent, enabled = dirty && !busy
            )
        }
    }
}

private val SPINNER = 20.dp

@Composable
private fun OrganizerActions(
    count: Int,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onExtract: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(color = c.surface, shadowElevation = Elevation.floating) {
        Row(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            CwIconButton(Icons.Filled.RotateLeft, "لفّ شمال", onRotateLeft)
            CwIconButton(Icons.Filled.RotateRight, "لفّ يمين", onRotateRight)
            CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "حرّك لورا", onMoveBack)
            CwIconButton(Icons.AutoMirrored.Filled.ArrowForward, "حرّك لقدّام", onMoveForward)
            Spacer(Modifier.weight(1f))
            CwIconButton(Icons.Filled.ContentCopy, "كرّر", onDuplicate)
            CwIconButton(Icons.Filled.FileUpload, "استخرج لملف جديد ($count)", onExtract)
            CwIconButton(
                Icons.Filled.DeleteOutline, "احذف", onDelete,
                tint = c.danger.fg
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════ الخلية

@Composable
private fun PageCell(
    position: Int,
    sourcePage: Int,
    rotation: Int,
    selected: Boolean,
    cache: ThumbnailCache,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    LaunchedEffect(sourcePage) { cache.request(sourcePage) }
    val image = cache.thumbs[sourcePage]

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = Radius.shapeSm,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                if (selected) CwStroke.thick else CwStroke.hair,
                if (selected) c.accent else c.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CELL_RATIO)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "صفحة ${sourcePage + 1}",
                        contentScale = ContentScale.Fit,
                        // التدوير معاينة بس — الملف مابيتغيّرش دلوقتي.
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.xxs)
                            .rotate(rotation.toFloat())
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(c.surfaceAlt))
                }
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(c.accent.copy(alpha = 0.14f))
                    )
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Space.xxs)
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xxs))
        Text(
            // الرقم الجديد أولاً — ده اللي المستخدم بيرتّبه. الأصلي جنبه
            // عشان يعرف الصفحة دي جاية منين بعد ما يقلب الترتيب.
            if (position - 1 == sourcePage) "$position"
            else "$position ← ${sourcePage + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.accent else c.textTertiary,
            maxLines = 1
        )
    }
}

private val CELL_MIN = 92.dp
private const val CELL_RATIO = 0.74f

@Composable
private fun OrganizerLoading(error: String?, onClose: () -> Unit) {
    val c = LocalCwColors.current
    Surface(Modifier.fillMaxSize(), color = c.surface) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (error == null) {
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(Space.md))
                Text("بيفتح الملف…", style = MaterialTheme.typography.bodyMedium, color = c.textTertiary)
            } else {
                Text(error, style = MaterialTheme.typography.titleSmall, color = c.danger.fg)
                Spacer(Modifier.height(Space.md))
                CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            }
        }
    }
}
