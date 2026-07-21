package com.corewall.qaqc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corewall.qaqc.ui.attention.AttentionScreen
import com.corewall.qaqc.ui.plan.ElementSheet
import com.corewall.qaqc.ui.plan.PlanScreen
import com.corewall.qaqc.ui.settings.SettingsScreen
import com.corewall.qaqc.ui.theme.CoreWallTheme
import com.corewall.qaqc.ui.tools.ToolsScreen

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

@Composable
fun MainScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val selectedElementId by vm.selectedElementId.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AppTab.PLAN,
                    onClick = { vm.setTab(AppTab.PLAN) },
                    icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                    label = { Text("المسقط") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.ATTENTION,
                    onClick = { vm.setTab(AppTab.ATTENTION) },
                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                    label = { Text("Attention") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.TOOLS,
                    onClick = { vm.setTab(AppTab.TOOLS) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("الأدوات") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS,
                    onClick = { vm.setTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            AppTab.PLAN -> PlanScreen(vm, contentModifier)
            AppTab.ATTENTION -> AttentionScreen(vm, contentModifier)
            AppTab.TOOLS -> ToolsScreen(vm, contentModifier)
            AppTab.SETTINGS -> SettingsScreen(vm, contentModifier)
        }
    }

    selectedElementId?.let { id ->
        val element = vm.planData.elements.firstOrNull { it.id == id }
        if (element != null) {
            ElementSheet(vm = vm, element = element, onDismiss = { vm.selectElement(null) })
        }
    }
}
