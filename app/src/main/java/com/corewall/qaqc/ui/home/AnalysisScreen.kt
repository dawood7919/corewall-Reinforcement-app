package com.corewall.qaqc.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.attention.AttentionScreen
import com.corewall.qaqc.ui.counting.CountingReportScreen
import com.corewall.qaqc.ui.tools.ToolsScreen

/** تبويب التحليل: Attention + الأدوات + ريبورت العدّ في مكان واحد. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var section by remember { mutableIntStateOf(0) }
    val labels = listOf("Attention", "الأدوات", "ريبورت العدّ")

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = section == i,
                    onClick = { section = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (section) {
                0 -> AttentionScreen(vm, Modifier.fillMaxSize())
                1 -> ToolsScreen(vm, Modifier.fillMaxSize())
                else -> CountingReportScreen(vm, Modifier.fillMaxSize())
            }
        }
    }
}
