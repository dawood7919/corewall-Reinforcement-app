package com.corewall.qaqc.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.LocalReducedMotion
import com.corewall.qaqc.ui.design.ScreenMotion
import com.corewall.qaqc.ui.design.rememberPressScale
import com.corewall.qaqc.ui.ai.AiChatScreen
import com.corewall.qaqc.ui.ai.AiReportScreen
import com.corewall.qaqc.ui.ai.AiSettingsScreen
import com.corewall.qaqc.ui.ai.PromptsScreen
import com.corewall.qaqc.ui.settings.ScheduleImportScreen
import com.corewall.qaqc.ui.ai.AiAnalysisScreen
import com.corewall.qaqc.ui.ai.KnowledgeScreen
import com.corewall.qaqc.ui.ai.ProjectKnowledgeScreen
import com.corewall.qaqc.ui.appscreens.AboutScreen
import com.corewall.qaqc.ui.appscreens.NotificationsScreen
import com.corewall.qaqc.ui.appscreens.SitePhotosScreen
import com.corewall.qaqc.ui.appscreens.SyncScreen
import com.corewall.qaqc.ui.attention.AttentionScreen
import com.corewall.qaqc.ui.cad.CadViewerScreen
import com.corewall.qaqc.ui.checks.ChecksScreen
import com.corewall.qaqc.ui.counting.CountingReportScreen
import com.corewall.qaqc.ui.dataroom.DataScreen
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke
import com.corewall.qaqc.ui.home.PlanScreen
import com.corewall.qaqc.ui.home.UnifiedSheet
import com.corewall.qaqc.ui.manpower.AttendanceFileDetailScreen
import com.corewall.qaqc.ui.manpower.ManpowerScreen
import com.corewall.qaqc.ui.nav.Dest
import com.corewall.qaqc.ui.media.ImageViewerScreen
import com.corewall.qaqc.ui.notes.NoteEditorScreen
import com.corewall.qaqc.ui.notes.NotesScreen
import com.corewall.qaqc.ui.takeoff.TakeoffDrawingsScreen
import com.corewall.qaqc.ui.takeoff.TakeoffEditorScreen
import com.corewall.qaqc.ui.pdf.PdfOrganizerScreen
import com.corewall.qaqc.ui.pdf.PdfViewerScreen
import com.corewall.qaqc.ui.pour.PourReadinessScreen
import com.corewall.qaqc.ui.settings.SettingsScreen
import com.corewall.qaqc.ui.today.TodayScreen
import com.corewall.qaqc.ui.tools.ToolsScreen
import com.corewall.qaqc.v2.takeoff.V2TakeoffProjectsScreen
import kotlinx.coroutines.launch

/**
 * هيكل التطبيق — **موجّه واحد** لكل الوجهات.
 *
 * قبل كده كان الـActivity فيه ٣ `when` منفصلين (عنوان، محتوى، رجوع) و٥
 * تدفّقات nullable للعارضات، وزرار رجوع بـ٩ فروع. النتيجة إن شاشة ممكن تكون
 * مرسومة في الموجّه ومفيش حاجة بتفتحها — وده اللي حصل فعلاً مع ٥ شاشات.
 *
 * دلوقتي: وجهة واحدة → عنوان واحد → محتوى واحد، والرجوع pop.
 */
@Composable
fun AppShell(vm: MainViewModel) {
    val c = LocalCwColors.current
    val nav by vm.navState.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val unread by vm.unreadNotifications.collectAsStateWithLifecycle()
    val selectedElementId by vm.selectedElementId.collectAsStateWithLifecycle()
    val editingNote by vm.editingNote.collectAsStateWithLifecycle()

    val reducedMotion = LocalReducedMotion.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showLevelSheet by remember { mutableStateOf(false) }

    val dest = nav.current
    val fullScreen = dest.fullScreen

    // طبقتين بس: الدرج (طبقة فوق كل حاجة)، وبعدها المكدّس.
    BackHandler(enabled = true) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else vm.back()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !fullScreen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.surface) {
                AppDrawer(vm = vm, onNavigate = { scope.launch { drawerState.close() } })
            }
        }
    ) {
        // على شاشة عريضة (تابلت أو موبايل مفرود) الشريط السفلي بيتحوّل لعمود
        // جانبي. السبب مش شكلي: التبويبات في الأسفل على تابلت بتبقى بعيدة عن
        // الإبهام وبتاكل ارتفاع، والمسقط هنا هو الشغل الأساسي والارتفاع أغلى
        // من العرض. مجموعة التبويبات نفسها ما بتتغيّرش — نفس الخمسة.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WideBreakpoint
            val showRail = wide && !fullScreen

            Row(Modifier.fillMaxSize()) {
                if (showRail) {
                    NavRail(current = nav.tab, onSelect = { vm.selectTab(it) })
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = c.background,
                    topBar = {
                        if (!fullScreen) {
                            FloorBar(
                                level = level,
                                levelIndex = vm.levels.indexOf(level),
                                levelCount = vm.levels.size,
                                destinationTitle = if (nav.canPop) titleFor(vm, dest) else null,
                                unread = unread,
                                onPickLevel = { showLevelSheet = true },
                                onMenu = { scope.launch { drawerState.open() } },
                                onBack = if (nav.canPop) ({ vm.back() }) else null,
                                onNotifications = { vm.go(Dest.Notifications) }
                            )
                        }
                    },
                    bottomBar = {
                        if (!fullScreen && !showRail) {
                            BottomNav(current = nav.tab, onSelect = { vm.selectTab(it) })
                        }
                    }
                ) { padding ->
                    val inner = Modifier.padding(padding)

                    /**
                     * حافظ حالة الوجهات.
                     *
                     * `AnimatedContent` بيهدّ الشاشة القديمة ويبني الجديدة من
                     * الصفر — يعني كل `remember` جوّه الشاشة بيضيع، وأول ما
                     * ترجع بالخلف كل حاجة بتتحسب تاني: موضع التمرير، الفلاتر
                     * المختارة، قايمة الملفات، المصغّرات. ده كان أكبر سبب
                     * لإحساس "الرجوع بيعيد تحميل كل حاجة".
                     *
                     * `SaveableStateHolder` بيحفظ حالة كل وجهة بمفتاحها
                     * ويرجّعها لما ترجع لها — نفس اللي navigation-compose
                     * بيعمله جوّه، من غير ما نستبدل نظام التنقّل كله.
                     *
                     * حدود الحل بصراحة: بيحفظ `rememberSaveable` بس، مش
                     * `remember` العادي. يعني موضع التمرير والفلاتر
                     * والحقول بترجع مكانها (ودي أغلب اللي المستخدم بيحسّه)،
                     * لكن الحالة التقيلة زي جلسة ملف PDF مفتوح بتتبني
                     * تاني. اللي عايز يعيش أطول من كده مكانه الـViewModel.
                     */
                    val stateHolder = rememberSaveableStateHolder()

                    /**
                     * اتجاه الانتقال من **عمق المكدّس**: أعمق = للأمام،
                     * أقل = رجوع، نفس العمق = تبديل تبويب.
                     */
                    val depth = nav.stack.size

                    AnimatedContent(
                        targetState = dest to depth,
                        transitionSpec = {
                            val from = initialState.second
                            val to = targetState.second
                            when {
                                to > from -> ScreenMotion.forward(this, reducedMotion)
                                to < from -> ScreenMotion.backward(this, reducedMotion)
                                else -> ScreenMotion.lateral(reducedMotion)
                            }
                        },
                        contentKey = { it.first.stateKey },
                        label = "destination"
                    ) { (d, _) ->
                        stateHolder.SaveableStateProvider(d.stateKey) {
                            Destination(vm = vm, dest = d, modifier = inner)
                        }
                    }
                }
            }
        }
    }

    // ورقة العنصر — مودال، مش وجهة. الاختيار بيتلغي بالسحب لتحت أو بالرجوع.
    if (nav.tab == Dest.Plan || nav.tab == Dest.Today) {
        selectedElementId?.let { id ->
            vm.planData.elements.firstOrNull { it.id == id }?.let { element ->
                UnifiedSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
            }
        }
    }

    if (showLevelSheet) {
        LevelSheet(
            levels = vm.levels,
            current = level,
            onPick = { vm.setLevel(it); showLevelSheet = false },
            onDismiss = { showLevelSheet = false }
        )
    }

    // المساعد الطايف — فوق كل شاشة إلا العارضات (عشان ما يغطّيش أدواتها).
    if (!fullScreen) {
        com.corewall.qaqc.ui.ai.AiCopilotOverlay(vm)
    }

    // محرّر الملاحظة بياخد بياناته من الـViewModel، ووجوده في المكدّس هو اللي
    // بيحدّد إنه ظاهر — فمفيش مصدرين للحقيقة.
    if (dest == Dest.NoteEditor) {
        editingNote?.let { note ->
            NoteEditorScreen(vm = vm, note = note, onClose = { vm.closeNoteEditor() })
        }
    }
}

/** العنوان المعروض للوجهة — مكان واحد بس. */
private fun titleFor(vm: MainViewModel, dest: Dest): String = when (dest) {
    Dest.Manpower -> "العمالة — ${vm.manpowerSection.value.label}"
    else -> dest.title
}

/**
 * الموجّه. كل وجهة ليها فرع واحد — ومفيش وجهة بتتعرّف من غير ما يبقى ليها
 * مدخل في [NavGraph.entryPoints].
 */
@Composable
private fun Destination(vm: MainViewModel, dest: Dest, modifier: Modifier) {
    when (dest) {
        // الجذور
        Dest.Today -> TodayScreen(vm, modifier)
        Dest.Plan -> PlanScreen(vm, modifier)
        Dest.Checks -> ChecksScreen(vm, modifier)
        Dest.Data -> DataScreen(vm, modifier)
        Dest.Assistant -> AiChatScreen(vm, modifier)

        // الفحص — الأربع شاشات دول كانوا مبنيين ومحدش يقدر يوصلهم
        Dest.PourReadiness -> PourReadinessScreen(vm, modifier)
        Dest.Gaps -> AttentionScreen(vm, modifier)
        Dest.CountingReport -> CountingReportScreen(vm, modifier)
        Dest.Tools -> ToolsScreen(vm, modifier)
        Dest.FloorAnalysis -> AiAnalysisScreen(vm, modifier)

        // الداتا والمشروع
        Dest.FloorNotes -> NotesScreen(vm, modifier)

        // حصر الكميات — قسم مستقل: أقسامه ورسماته مالهاش علاقة بالأدوار.
        Dest.Takeoff -> V2TakeoffProjectsScreen(vm, modifier)
        is Dest.TakeoffProject -> TakeoffDrawingsScreen(vm, dest.projectId, modifier)
        is Dest.TakeoffEditor -> TakeoffEditorScreen(
            vm = vm, drawingId = dest.drawingId, path = dest.path,
            onClose = { vm.back() }
        )
        Dest.SitePhotos -> SitePhotosScreen(vm, modifier)
        Dest.Manpower -> ManpowerScreen(vm, modifier)

        // المساعد
        Dest.FloorKnowledge -> KnowledgeScreen(vm, modifier)
        Dest.ProjectKnowledge -> ProjectKnowledgeScreen(vm, modifier)
        Dest.DocumentGen -> AiReportScreen(vm, modifier)
        Dest.AiSettings -> AiSettingsScreen(vm, modifier)
        Dest.Prompts -> PromptsScreen(vm, modifier)

        // النظام
        Dest.Notifications -> NotificationsScreen(vm, modifier)
        Dest.Settings -> SettingsScreen(vm, modifier)
        Dest.Sync -> SyncScreen(vm, modifier)
        Dest.ScheduleImport -> ScheduleImportScreen(vm, modifier)
        Dest.About -> AboutScreen(modifier)

        // ملء الشاشة
        is Dest.PdfViewer -> PdfViewerScreen(vm = vm, path = dest.path, onClose = { vm.closePdf() })
        is Dest.PdfOrganizer -> PdfOrganizerScreen(
            path = dest.path,
            onClose = { vm.back() },
            onOpenFile = { vm.openPdf(it) }
        )
        is Dest.CadViewer -> CadViewerScreen(path = dest.path, files = vm.files, onClose = { vm.closeCad() })
        is Dest.ImageViewer -> ImageViewerScreen(files = vm.files, path = dest.path, onClose = { vm.closeImage() })
        is Dest.AttendanceFile -> AttendanceFileDetailScreen(
            vm = vm, fileId = dest.id, onClose = { vm.closeAttendanceFile() }
        )

        // المحرّر بيترسم في [AppShell] فوق الهيكل كله.
        Dest.NoteEditor -> Box(modifier.fillMaxSize())
    }
}

// ══════════════════════════════════════════════════════════ شريط التنقّل

private data class TabSpec(val dest: Dest.Root, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec(Dest.Today, Icons.Filled.Today),
    TabSpec(Dest.Plan, Icons.Filled.Map),
    TabSpec(Dest.Checks, Icons.Filled.FactCheck),
    TabSpec(Dest.Data, Icons.Filled.Folder),
    TabSpec(Dest.Assistant, Icons.Filled.AutoAwesome)
)

/**
 * شريط سفلي **ثابت**: نفس الخمس تبويبات في كل مكان في التطبيق.
 *
 * قبل كده مجموعة التبويبات كانت بتتغيّر تحت إيد المستخدم لما القسم يتبدّل،
 * وآخر تبويب في القسمين كان بيفتح نفس الشاشة. دلوقتي التبويب معناه ثابت.
 */
@Composable
private fun BottomNav(current: Dest.Root, onSelect: (Dest.Root) -> Unit) {
    val c = LocalCwColors.current
    Surface(color = c.background) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Space.screen, vertical = Space.sm)) {
            Surface(color = c.surface, shape = Radius.shapeXl, shadowElevation = Elevation.raised) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Tabs.forEach { tab ->
                    NavTab(
                        spec = tab,
                        selected = tab.dest == current,
                        onClick = { onSelect(tab.dest) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }
        }
    }
}

/** الحد اللي بعده بنعتبر الشاشة عريضة — نفس حد Material للـmedium width. */
private val WideBreakpoint = 600.dp

/** عمود التنقّل الجانبي — نفس التبويبات الخمسة، بس رأسية. */
@Composable
private fun NavRail(current: Dest.Root, onSelect: (Dest.Root) -> Unit) {
    val c = LocalCwColors.current
    Surface(color = c.surface) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(Sizes.rail)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Tabs.forEach { tab ->
                NavTab(
                    spec = tab,
                    selected = tab.dest == current,
                    onClick = { onSelect(tab.dest) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    spec: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressScale(interaction)

    // اللون والحبّة بيتحرّكوا بدل ما ينطّوا. التبويب المختار بيتحوّل
    // قدّام عين المستخدم فبيربط الضغطة بالنتيجة.
    val tint by animateColorAsState(
        if (selected) c.onAccentContainer else c.textTertiary,
        Motion.standard(), label = "tabTint"
    )
    val pill by animateColorAsState(
        if (selected) c.accentContainer else Color.Transparent,
        Motion.standard(), label = "tabPill"
    )
    // الحبّة بتتوسّع شوية وهي بتتحدّد — إشارة اتجاه من غير حركة تخطيط.
    val pillScale by animateFloatAsState(
        if (selected) 1f else PILL_RESTING_SCALE,
        Motion.standard(), label = "tabPillScale"
    )

    Column(
        modifier
            .clip(Radius.shapeLg)
            .heightIn(min = Sizes.touch)
            .clickable(
                interactionSource = interaction,
                // مؤشّر المنصّة الافتراضي — نفس التموّج اللي في كل مكان
                // تاني في التطبيق، من غير ما نخترع واحد.
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick
            )
            .scale(press)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xxs)
    ) {
        Box(
            Modifier
                .scale(pillScale)
                .clip(Radius.shapeMd)
                .background(pill)
                .padding(horizontal = Space.lg, vertical = Space.xs)
        ) {
            Icon(
                spec.icon,
                contentDescription = null, // التسمية تحتها بتقول نفس المعنى
                tint = tint,
                modifier = Modifier.size(IconSize.lg)
            )
        }
        Text(
            spec.dest.title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** الحبّة غير المختارة أصغر شوية — الفرق بيتحسّ قبل ما يتقري. */
private const val PILL_RESTING_SCALE = 0.86f
