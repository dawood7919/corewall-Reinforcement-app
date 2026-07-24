package com.corewall.qaqc.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.ui.counting.CountingSheetContent
import com.corewall.qaqc.ui.dataroom.DataSheetContent
import com.corewall.qaqc.ui.plan.ReinforcementSheetContent

/**
 * Sheet واعي بالعدسة: نفس العنصر — بدّل العدسة من جوّه من غير ما تقفل
 * (تسليح / عدّ / داتا) والمحتوى بيتبدّل في مكانه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSheet(vm: MainViewModel, element: PlanElement, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lens by vm.lens.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lens.entries.forEach { l ->
                    FilterChip(
                        selected = lens == l,
                        onClick = { vm.setLens(l) },
                        label = { Text(l.label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            when (lens) {
                Lens.REINF -> ReinforcementSheetContent(vm, element)
                Lens.COUNT -> CountingSheetContent(vm, element)
                Lens.DATA -> DataSheetContent(vm, element)
            }
        }
    }
}
