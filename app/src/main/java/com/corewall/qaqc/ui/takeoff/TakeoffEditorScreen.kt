package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffItemEntity
import com.corewall.qaqc.pdfengine.PageLayout
import com.corewall.qaqc.pdfengine.PdfCanvas
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfSessionHolder
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.TileEngine
import com.corewall.qaqc.pdfengine.pageHit
import com.corewall.qaqc.stylus.PointerKind
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * شاشة الحصر — الرسمة وأدواتها.
 *
 * ## اللي اتعمل إعادة استخدام له بدل ما يتبني من جديد
 *
 * المحرّك (PDFium بمربّعات)، حالة العرض، الإيماءات، وفرز القلم — كلهم
 * من [PdfCanvas] الموجود. مواصفة البورت كانت بتقترح محرّك `PdfRenderer`
 * منفصل، وده كان هيبقى **محرّك PDF تاني في نفس التطبيق** — الفخ رقم ٧
 * في المواصفة نفسها.
 *
 * ## الجسر بين الشاشة والحساب
 *
 * `state.pageHit(screen)` بيرجّع `(page, nx, ny)` **منسّبة ٠..١** —
 * وده بالظبط نظام إحداثيات الحصر. فمفيش تحويل وسيط ولا فرصة لخلط
 * المساحات: اللمسة بتتحوّل مرة واحدة عند الحدود وخلاص.
 */
@Composable
fun TakeoffEditorScreen(
    vm: MainViewModel,
    drawingId: Long,
    path: String,
    onClose: () -> Unit
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val file = remember(path) { File(path) }

    // ── فتح الرسمة
    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    val holder = remember(path) { PdfSessionHolder() }
    var openError by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, file) }
        }
        opened.onSuccess { if (holder.accept(it)) session = it }
        opened.onFailure { e ->
            openError = (e as? PdfOpenException)?.userMessage ?: "مقدرناش نفتح الرسمة"
        }
    }
    DisposableEffect(path) { onDispose { holder.dispose() } }

    val active = session
    if (openError != null || active == null) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (openError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(openError!!, color = c.textSecondary)
                        CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onClose)
                    }
                } else CircularProgressIndicator(color = c.accent)
            }
        }
        return
    }

    val engine = remember(active) { TileEngine(active, TileEngine.budgetFor(context)) }
    DisposableEffect(active) { onDispose { engine.clear() } }

    val state = remember(active) { PdfViewerState(active.pageCount) }
    val measured by active.measuredCount.collectAsStateWithLifecycle()

    LaunchedEffect(measured, state.mode) {
        state.updateLayout(PageLayout.build(active.allSizes(), state.mode))
    }
    LaunchedEffect(state.currentPage) {
        val from = (state.currentPage - 2).coerceAtLeast(0)
        val to = (state.currentPage + 4).coerceAtMost(active.pageCount - 1)
        for (p in from..to) active.measure(p)
    }

    // ── البيانات المخزّنة
    val rows by remember(drawingId) { vm.takeoff.items(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val scaleRows by remember(drawingId) { vm.takeoff.scales(drawingId) }
        .collectAsStateWithLifecycle(emptyList())

    val items = remember(rows) { rows.map { vm.takeoff.toModel(it) } }
    val pageItems = remember(items, state.currentPage) {
        items.filter { it.page == state.currentPage }
    }

    /** هندسة الصفحة الحالية — مقاسها بالنقط + معايرتها. */
    val pageGeometry = remember(measured, state.currentPage, scaleRows) {
        val size = active.knownSize(state.currentPage) ?: active.estimate
        val mpp = scaleRows.firstOrNull { it.page == state.currentPage }?.metresPerPoint ?: 0.0
        PageGeometry(size.width.toDouble(), size.height.toDouble(), mpp)
    }

    // ── حالة الأدوات
    var tool by remember { mutableStateOf(TakeoffTool.AREA) }
    var pointerMode by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var colourIndex by remember { mutableIntStateOf(0) }
    var calibrating by remember { mutableStateOf(false) }
    var totalsOpen by remember { mutableStateOf(false) }
    var deductFor by remember { mutableStateOf<TakeoffItem?>(null) }

    /**
     * المسوّدة **بإحداثيات الصفحة المنسّبة**، مش بإحداثيات الشاشة.
     *
     * ده مقصود: التمرير والتكبير شغّالين وأنت في نص شكل، فلو خزّنّا نقطة
     * الشاشة وأجّلنا التحويل للحظة الحفظ، أي تمرير بين أول لمسة والحفظ
     * كان هيزحلق الشكل كله. التحويل بيحصل **مرة واحدة عند اللمس** —
     * وبعدها النقطة متعلّقة في الورق، مش في الشاشة.
     */
    val draft = remember(path) { mutableStateListOf<TakeoffPoint>() }
    val draftPage = remember { mutableIntStateOf(-1) }
    /** نقطتا المعايرة — بتتجمعوا بنفس آلية المسوّدة. */
    val calibPoints = remember(path) { mutableStateListOf<TakeoffPoint>() }

    fun endSession() {
        // "الخروج الشامل" من الفخ رقم ٥: مسوّدة، تحديد، وضع، خصم — كلهم.
        draft.clear()
        draftPage.intValue = -1
        calibPoints.clear()
        calibrating = false
        deductFor = null
        pointerMode = true
        selectedId = null
    }

    fun commit() {
        val page = draftPage.intValue
        if (page < 0 || draft.isEmpty()) { draft.clear(); draftPage.intValue = -1; return }

        val points = draft.toList()
        val enough = when (tool) {
            TakeoffTool.COUNT -> points.isNotEmpty()
            TakeoffTool.LENGTH -> points.size >= 2
            else -> points.size >= 3
        }
        if (!enough) { draft.clear(); draftPage.intValue = -1; return }

        val activeTool = if (deductFor != null) TakeoffTool.DEDUCT else tool
        val parent = deductFor
        val colour = TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size]
        scope.launch {
            vm.takeoff.saveItem(
                TakeoffItemEntity(
                    drawingId = drawingId,
                    page = page,
                    tool = activeTool.name,
                    name = defaultName(activeTool, rows.size + 1),
                    colorArgb = if (parent != null) 0xFF9E9E9EL else colour,
                    pointsJson = vm.takeoff.encodeRing(points),
                    parentId = parent?.id?.toLongOrNull(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        if (parent == null) colourIndex++
        draft.clear()
        draftPage.intValue = -1
        deductFor = null
    }

    fun addPoint(screen: Offset) {
        val hit = state.pageHit(screen) ?: return
        if (draftPage.intValue < 0) draftPage.intValue = hit.page
        // لمسة على صفحة تانية وأنت في نص شكل بتتجاهل — البند بيخص صفحة
        // واحدة، وشكل بيعدّي بين صفحتين مالوش معنى في الحصر.
        if (hit.page != draftPage.intValue) return
        draft += TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
    }

    val penHasJob = settings.stylusOnly && !pointerMode
    val activeColour = Color(
        (TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size] or 0xFF000000L).toInt()
    )
    val calibrationColour = c.accent

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize()) {
            PdfCanvas(
                state = state,
                engine = engine,
                session = active,
                // الرسم بيحصل بالنقر مش بالسحب، فطبقة الإيماءات بتفضل
                // شغّالة دايماً: تمرّر وتكبّر وأنت في نص شكل.
                drawingActive = false,
                gestureAccept = remember(penHasJob) {
                    { kind: PointerKind -> !(penHasJob && kind.isPen) }
                },
                onTap = { point, kind ->
                    val penOnly = settings.stylusOnly && !pointerMode
                    when {
                        // في وضع القلم، الصباع بيتنقّل بس — مايرسمش ولا يعاير.
                        penOnly && !kind.isPen -> Unit
                        calibrating -> {
                            state.pageHit(point)?.let { hit ->
                                if (calibPoints.size < 2) {
                                    calibPoints += TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                }
                            }
                        }
                        pointerMode -> {
                            val hit = state.pageHit(point)
                            selectedId = hit?.let { h ->
                                val p = TakeoffPoint(h.nx.toDouble(), h.ny.toDouble())
                                // من الآخر للأول: الشكل اللي اترسم بعدين هو
                                // اللي فوق بصرياً، فلازم يكسب اللمسة.
                                pageItems.lastOrNull { candidate ->
                                    candidate.visible && TakeoffMath.hitTest(
                                        candidate, p, pageGeometry, tapRadiusPt = 14.0
                                    )
                                }?.id
                            }
                        }
                        else -> addPoint(point)
                    }
                },
                onLongPress = { _, _ -> if (!pointerMode) commit() },
                overlay = { s ->
                    drawTakeoffItems(
                        state = s,
                        items = pageItems,
                        page = s.currentPage,
                        deductionsOf = { parent ->
                            pageItems.filter {
                                it.tool == TakeoffTool.DEDUCT && it.parentId == parent.id
                            }
                        },
                        selectedId = selectedId
                    )
                    // نقطتا المعايرة لازم تبانوا وهو بيحطهم — من غير ده
                    // المستخدم مش عارف إذا كانت لمسته اتسجّلت ولا لأ.
                    if (calibrating && calibPoints.isNotEmpty()) {
                        drawTakeoffDraft(
                            state = s,
                            page = s.currentPage,
                            points = calibPoints.toList(),
                            tool = TakeoffTool.LENGTH,
                            colour = calibrationColour
                        )
                    }
                    if (draft.isNotEmpty() && draftPage.intValue >= 0) {
                        drawTakeoffDraft(
                            state = s,
                            page = draftPage.intValue,
                            points = draft.toList(),
                            tool = if (deductFor != null) TakeoffTool.DEDUCT else tool,
                            colour = activeColour
                        )
                    }
                }
            )

            TakeoffTopBar(
                title = if (calibrating) "عاير المقياس" else null,
                calibrated = pageGeometry.calibrated,
                page = state.currentPage + 1,
                pageCount = active.pageCount,
                onBack = onClose,
                onTotals = { totalsOpen = true },
                onCalibrate = { endSession(); calibrating = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            TakeoffToolBar(
                tool = tool,
                pointerMode = pointerMode,
                deducting = deductFor != null,
                hasDraft = draft.isNotEmpty(),
                canDeduct = selectedId != null &&
                    pageItems.firstOrNull { it.id == selectedId }?.tool == TakeoffTool.AREA,
                onPointer = { endSession() },
                onPick = { picked ->
                    endSession(); pointerMode = false; tool = picked
                },
                onDeduct = {
                    val parent = pageItems.firstOrNull { it.id == selectedId }
                    if (parent != null) {
                        draft.clear(); draftPage.intValue = -1
                        deductFor = parent; pointerMode = false; tool = TakeoffTool.AREA
                    }
                },
                onDone = { commit() },
                onDeleteSelected = {
                    selectedId?.toLongOrNull()?.let { id ->
                        scope.launch { vm.takeoff.deleteItem(id) }
                    }
                    selectedId = null
                },
                selectedId = selectedId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    if (calibrating) {
        TakeoffCalibrateSheet(
            points = calibPoints.toList(),
            pageGeometry = pageGeometry,
            onApply = { metresPerPoint, note, allPages ->
                scope.launch {
                    if (allPages) {
                        vm.takeoff.copyScaleToAllPages(
                            drawingId,
                            com.corewall.qaqc.data.db.TakeoffScaleEntity(
                                drawingId, state.currentPage, metresPerPoint, note
                            ),
                            active.pageCount
                        )
                    } else {
                        vm.takeoff.setScale(drawingId, state.currentPage, metresPerPoint, note)
                    }
                }
                endSession()
            },
            onDismiss = { endSession() }
        )
    }

    if (totalsOpen) {
        TakeoffTotalsSheet(
            items = items,
            pageGeometry = pageGeometry,
            onDismiss = { totalsOpen = false }
        )
    }
}

private fun defaultName(tool: TakeoffTool, index: Int): String = when (tool) {
    TakeoffTool.AREA -> "مساحة $index"
    TakeoffTool.LENGTH -> "طول $index"
    TakeoffTool.COUNT -> "عدّ $index"
    TakeoffTool.DEDUCT -> "خصم $index"
}

@Composable
private fun TakeoffTopBar(
    title: String?,
    calibrated: Boolean,
    page: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onTotals: () -> Unit,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(modifier.fillMaxWidth(), color = c.surface, shadowElevation = Elevation.raised) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    title ?: "صفحة $page من $pageCount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
                Text(
                    if (calibrated) "المقياس معاير" else "الصفحة مش معايرة — عاير الأول",
                    style = CwText.codeSmall,
                    color = if (calibrated) c.success.fg else c.danger.fg
                )
            }
            CwIconButton(Icons.Filled.Straighten, "معايرة المقياس", onCalibrate)
            CwIconButton(Icons.Filled.Functions, "الإجماليات", onTotals)
        }
    }
}

/** شريط الأدوات السفلي. */
@Composable
private fun TakeoffToolBar(
    tool: TakeoffTool,
    pointerMode: Boolean,
    deducting: Boolean,
    hasDraft: Boolean,
    canDeduct: Boolean,
    selectedId: String?,
    onPointer: () -> Unit,
    onPick: (TakeoffTool) -> Unit,
    onDeduct: () -> Unit,
    onDone: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(
        modifier.padding(Space.md),
        color = c.surface,
        shape = Radius.pill,
        shadowElevation = Elevation.floating
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            CwIconButton(
                Icons.Filled.PanTool, "تنقّل وتحديد", onPointer,
                active = pointerMode && !deducting
            )
            CwIconButton(
                Icons.Filled.Square, "مساحة", { onPick(TakeoffTool.AREA) },
                active = !pointerMode && !deducting && tool == TakeoffTool.AREA
            )
            CwIconButton(
                Icons.Filled.Timeline, "طول", { onPick(TakeoffTool.LENGTH) },
                active = !pointerMode && tool == TakeoffTool.LENGTH
            )
            CwIconButton(
                Icons.Filled.PinDrop, "عدّ", { onPick(TakeoffTool.COUNT) },
                active = !pointerMode && tool == TakeoffTool.COUNT
            )
            CwIconButton(
                Icons.Filled.ContentCut, "خصم من المحدّد", onDeduct,
                active = deducting, enabled = canDeduct || deducting
            )
            if (hasDraft) {
                CwIconButton(Icons.Filled.Check, "إنهاء الشكل", onDone, tint = c.success.fg)
            }
            if (selectedId != null) {
                CwIconButton(
                    Icons.Filled.Delete, "احذف المحدّد", onDeleteSelected, tint = c.danger.fg
                )
            }
        }
    }
}

