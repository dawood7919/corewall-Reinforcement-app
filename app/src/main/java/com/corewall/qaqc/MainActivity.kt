package com.corewall.qaqc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corewall.qaqc.ui.attention.AttentionScreen
import com.corewall.qaqc.ui.counting.CountingPlanScreen
import com.corewall.qaqc.ui.counting.CountingReportScreen
import com.corewall.qaqc.ui.counting.CountingSheet
import com.corewall.qaqc.ui.plan.ElementSheet
import com.corewall.qaqc.ui.plan.PlanScreen
import com.corewall.qaqc.ui.settings.SettingsScreen
import com.corewall.qaqc.ui.theme.CoreWallTheme
import com.corewall.qaqc.ui.tools.ToolsScreen
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

/**
 * تبويبات كل أداة — إضافة أداة جديدة: قيمة في AppModule + سطر هنا +
 * شاشاتها في ModuleContent، والدروار والتبويبات بيتظبطوا لوحدهم.
 */
private fun tabsFor(module: AppModule): List<TabSpec> = when (module) {
    AppModule.REINFORCEMENT -> listOf(
        TabSpec("المسقط", Icons.Filled.Map),
        TabSpec("Attention", Icons.Filled.NotificationsActive),
        TabSpec("الأدوات", Icons.Filled.Build),
        TabSpec("الإعدادات", Icons.Filled.Settings)
    )
    AppModule.COUNTING -> listOf(
        TabSpec("البلان", Icons.Filled.Map),
        TabSpec("الريبورت", Icons.Filled.Summarize),
        TabSpec("الإعدادات", Icons.Filled.Settings)
    )
}

private fun moduleIcon(module: AppModule): ImageVector = when (module) {
    AppModule.REINFORCEMENT -> Icons.Filled.ViewInAr
    AppModule.COUNTING -> Icons.Filled.Calculate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val module by vm.module.collectAsStateWithLifecycle()
    val tabIndex by vm.tabIndex.collectAsStateWithLifecycle()
    val selectedElementId by vm.selectedElementId.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabs = tabsFor(module)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Core Wall QA/QC",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "اختار الأداة",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    AppModule.entries.forEach { m ->
                        NavigationDrawerItem(
                            label = { Text(m.title) },
                            icon = { Icon(moduleIcon(m), contentDescription = null) },
                            selected = m == module,
                            onClick = {
                                vm.setModule(m)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "الأدوات")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
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
            ModuleContent(vm, module, tabIndex, Modifier.padding(padding))
        }
    }

    selectedElementId?.let { id ->
        val element = vm.planData.elements.firstOrNull { it.id == id }
        if (element != null) {
            when (module) {
                AppModule.REINFORCEMENT ->
                    ElementSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
                AppModule.COUNTING ->
                    CountingSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
            }
        }
    }
}

@Composable
private fun ModuleContent(vm: MainViewModel, module: AppModule, tabIndex: Int, modifier: Modifier) {
    when (module) {
        AppModule.REINFORCEMENT -> when (tabIndex) {
            0 -> PlanScreen(vm, modifier)
            1 -> AttentionScreen(vm, modifier)
            2 -> ToolsScreen(vm, modifier)
            else -> SettingsScreen(vm, modifier)
        }
        AppModule.COUNTING -> when (tabIndex) {
            0 -> CountingPlanScreen(vm, modifier)
            1 -> CountingReportScreen(vm, modifier)
            else -> SettingsScreen(vm, modifier)
        }
    }
}
