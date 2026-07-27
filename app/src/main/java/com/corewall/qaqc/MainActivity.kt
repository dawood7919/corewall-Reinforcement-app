package com.corewall.qaqc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corewall.qaqc.ui.ActiveLevelHeader
import com.corewall.qaqc.ui.AppDrawer
import com.corewall.qaqc.ui.appscreens.AboutScreen
import com.corewall.qaqc.ui.appscreens.AppSettingsScreen
import com.corewall.qaqc.ui.appscreens.NotificationsScreen
import com.corewall.qaqc.ui.appscreens.SyncScreen
import com.corewall.qaqc.ui.dataroom.FilesScreen
import com.corewall.qaqc.ui.dataroom.TasksScreen
import com.corewall.qaqc.ui.home.AnalysisScreen
import com.corewall.qaqc.ui.home.HomeScreen
import com.corewall.qaqc.ui.home.UnifiedSheet
import com.corewall.qaqc.ui.manpower.AttendanceFileDetailScreen
import com.corewall.qaqc.ui.manpower.AttendanceScreen
import com.corewall.qaqc.ui.manpower.ManpowerReportsScreen
import com.corewall.qaqc.ui.manpower.ManpowerStatisticsScreen
import com.corewall.qaqc.ui.notes.ImageViewerScreen
import com.corewall.qaqc.ui.notes.NoteEditorScreen
import com.corewall.qaqc.ui.pdf.PdfViewerScreen
import com.corewall.qaqc.ui.settings.SettingsScreen
import com.corewall.qaqc.ui.theme.CoreWallTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            CoreWallTheme(settings.theme) {
                MainScreen(vm)
            }
        }
    }
}

private data class TabSpec(val label: String, val icon: ImageVector)

private fun tabsFor(section: Section): List<TabSpec> = when (section) {
    Section.COREWALL -> listOf(
        TabSpec("البرج", Icons.Filled.Apartment),
        TabSpec("التحليل", Icons.Filled.Insights),
        TabSpec("الملفات", Icons.Filled.Folder),
        TabSpec("المهام", Icons.Filled.Checklist),
        TabSpec("الإعدادات", Icons.Filled.Settings)
    )
    Section.MANPOWER -> listOf(
        TabSpec("الحضور", Icons.Filled.People),
        TabSpec("التقارير", Icons.Filled.Summarize),
        TabSpec("الإحصائيات", Icons.Filled.BarChart),
        TabSpec("الإعدادات", Icons.Filled.Settings)
    )
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val section by vm.section.collectAsStateWithLifecycle()
    val tabIndex by vm.tabIndex.collectAsStateWithLifecycle()
    val selectedElementId by vm.selectedElementId.collectAsStateWithLifecycle()
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    val canGoBack by vm.canGoBack.collectAsStateWithLifecycle()
    val openPdfPath by vm.openPdfPath.collectAsStateWithLifecycle()
    val editingNote by vm.editingNote.collectAsStateWithLifecycle()
    val viewingImage by vm.viewingImage.collectAsStateWithLifecycle()
    val openAttendanceFileId by vm.openAttendanceFileId.collectAsStateWithLifecycle()
    val appScreen by vm.appScreen.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabs = tabsFor(section)

    BackHandler(
        enabled = drawerState.isOpen || appScreen != null || openAttendanceFileId != null || viewingImage != null ||
            editingNote != null || openPdfPath != null || selectedElementId != null ||
            namingMode || canGoBack
    ) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            appScreen == com.corewall.qaqc.AppScreen.ABOUT -> vm.openAppScreen(com.corewall.qaqc.AppScreen.SETTINGS)
            appScreen != null -> vm.closeAppScreen()
            // ملاحظة: About بيرجع لـSettings؛ باقي الشاشات بترجع للرئيسية
            openAttendanceFileId != null -> vm.closeAttendanceFile()
            viewingImage != null -> vm.closeImage()
            editingNote != null -> vm.closeNoteEditor()
            openPdfPath != null -> vm.closePdf()
            selectedElementId != null -> vm.selectElement(null)
            namingMode -> vm.setNamingMode(false)
            else -> vm.popTab()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawer(
                    vm = vm,
                    onNavigate = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = { ActiveLevelHeader(vm, onMenu = { scope.launch { drawerState.open() } }) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { vm.setTabIndex(index) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            val m = Modifier.padding(padding)
            when (section) {
                Section.COREWALL -> when (tabIndex) {
                    0 -> HomeScreen(vm, m)
                    1 -> AnalysisScreen(vm, m)
                    2 -> FilesScreen(vm, m)
                    3 -> TasksScreen(vm, m)
                    else -> SettingsScreen(vm, m)
                }
                Section.MANPOWER -> when (tabIndex) {
                    0 -> AttendanceScreen(vm, m)
                    1 -> ManpowerReportsScreen(vm, m)
                    2 -> ManpowerStatisticsScreen(vm, m)
                    else -> SettingsScreen(vm, m)
                }
            }
        }
    }

    // ===== Overlays =====
    if (section == Section.COREWALL) {
        selectedElementId?.let { id ->
            vm.planData.elements.firstOrNull { it.id == id }?.let { element ->
                UnifiedSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
            }
        }
    }
    openPdfPath?.let { path -> PdfViewerScreen(vm = vm, path = path, onClose = { vm.closePdf() }) }
    editingNote?.let { note -> NoteEditorScreen(vm = vm, note = note, onClose = { vm.closeNoteEditor() }) }
    viewingImage?.let { path -> ImageViewerScreen(files = vm.files, path = path, onClose = { vm.closeImage() }) }
    openAttendanceFileId?.let { id ->
        AttendanceFileDetailScreen(vm = vm, fileId = id, onClose = { vm.closeAttendanceFile() })
    }

    // ===== شاشات القائمة الجانبية (S13–S16) بملء الشاشة =====
    appScreen?.let { screen ->
        val (title, back) = when (screen) {
            com.corewall.qaqc.AppScreen.NOTIFICATIONS -> "الإشعارات" to { vm.closeAppScreen() }
            com.corewall.qaqc.AppScreen.SETTINGS -> "الإعدادات" to { vm.closeAppScreen() }
            com.corewall.qaqc.AppScreen.SYNC -> "مزامنة البيانات" to { vm.closeAppScreen() }
            com.corewall.qaqc.AppScreen.ABOUT -> "عن التطبيق" to { vm.openAppScreen(com.corewall.qaqc.AppScreen.SETTINGS) }
        }
        AppScreenScaffold(title = title, onBack = back) { inner ->
            when (screen) {
                com.corewall.qaqc.AppScreen.NOTIFICATIONS -> NotificationsScreen(vm, inner)
                com.corewall.qaqc.AppScreen.SETTINGS -> AppSettingsScreen(vm, inner)
                com.corewall.qaqc.AppScreen.SYNC -> SyncScreen(vm, inner)
                com.corewall.qaqc.AppScreen.ABOUT -> AboutScreen(inner)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppScreenScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                androidx.compose.material3.CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding -> content(Modifier.padding(padding)) }
    }
}
