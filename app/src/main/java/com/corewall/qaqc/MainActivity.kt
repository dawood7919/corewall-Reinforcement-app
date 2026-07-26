package com.corewall.qaqc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corewall.qaqc.ui.ActiveLevelHeader
import com.corewall.qaqc.ui.dataroom.FilesScreen
import com.corewall.qaqc.ui.dataroom.TasksScreen
import com.corewall.qaqc.ui.home.AnalysisScreen
import com.corewall.qaqc.ui.home.HomeScreen
import com.corewall.qaqc.ui.home.UnifiedSheet
import com.corewall.qaqc.ui.notes.ImageViewerScreen
import com.corewall.qaqc.ui.notes.NoteEditorScreen
import com.corewall.qaqc.ui.pdf.PdfViewerScreen
import com.corewall.qaqc.ui.settings.SettingsScreen
import com.corewall.qaqc.ui.theme.CoreWallTheme

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

private val TABS = listOf(
    TabSpec("البرج", Icons.Filled.Apartment),
    TabSpec("التحليل", Icons.Filled.Insights),
    TabSpec("الملفات", Icons.Filled.Folder),
    TabSpec("المهام", Icons.Filled.Checklist),
    TabSpec("الإعدادات", Icons.Filled.Settings)
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val tabIndex by vm.tabIndex.collectAsStateWithLifecycle()
    val selectedElementId by vm.selectedElementId.collectAsStateWithLifecycle()
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    val canGoBack by vm.canGoBack.collectAsStateWithLifecycle()
    val openPdfPath by vm.openPdfPath.collectAsStateWithLifecycle()
    val editingNote by vm.editingNote.collectAsStateWithLifecycle()
    val viewingImage by vm.viewingImage.collectAsStateWithLifecycle()

    // زرار الرجوع بتاع الموبايل: يقفل الصورة/المحرّر/الـPDF ← يقفل الشيت ← يطفي
    // وضع التسمية ← يرجّع خطوة في التبويبات ← وأخيراً بس يطلع من التطبيق.
    BackHandler(
        enabled = viewingImage != null || editingNote != null || openPdfPath != null ||
            selectedElementId != null || namingMode || canGoBack
    ) {
        when {
            viewingImage != null -> vm.closeImage()
            editingNote != null -> vm.closeNoteEditor()
            openPdfPath != null -> vm.closePdf()
            selectedElementId != null -> vm.selectElement(null)
            namingMode -> vm.setNamingMode(false)
            else -> vm.popTab()
        }
    }

    Scaffold(
        topBar = { ActiveLevelHeader(vm) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
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
        val contentModifier = Modifier.padding(padding)
        when (tabIndex) {
            0 -> HomeScreen(vm, contentModifier)
            1 -> AnalysisScreen(vm, contentModifier)
            2 -> FilesScreen(vm, contentModifier)
            3 -> TasksScreen(vm, contentModifier)
            else -> SettingsScreen(vm, contentModifier)
        }
    }

    selectedElementId?.let { id ->
        val element = vm.planData.elements.firstOrNull { it.id == id }
        if (element != null) {
            UnifiedSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
        }
    }

    // عارض الـPDF الداخلي — بيغطي الشاشة كلها فوق أي حاجة
    openPdfPath?.let { path ->
        PdfViewerScreen(vm = vm, path = path, onClose = { vm.closePdf() })
    }

    // محرّر الملاحظات — كامل الشاشة فوق أي حاجة
    editingNote?.let { note ->
        NoteEditorScreen(vm = vm, note = note, onClose = { vm.closeNoteEditor() })
    }

    // عارض الصور بملء الشاشة — فوق المحرّر
    viewingImage?.let { path ->
        ImageViewerScreen(files = vm.files, path = path, onClose = { vm.closeImage() })
    }
}
