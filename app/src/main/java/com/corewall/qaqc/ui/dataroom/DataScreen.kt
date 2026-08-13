package com.corewall.qaqc.ui.dataroom

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.appscreens.SitePhotosScreen
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.DataSection
import com.corewall.qaqc.ui.notes.NotesScreen

/**
 * الداتا — ملفات · مهام · ملاحظات · صور، كلها معزولة بالدور الشغّال.
 *
 * الأربعة دول كانوا متفرّقين: اتنين تبويبات في الشريط السفلي واتنين شاشات
 * جوّه الدرج. مالهمش فرق مفاهيمي — كلهم "حاجات الدور ده" — فبقوا مكان واحد
 * بتبويب داخلي. كده الشريط السفلي فضل ٥ تبويبات ثابتة، والوصول بقى أقصر.
 */
@Composable
fun DataScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val section by vm.dataSection.collectAsStateWithLifecycle()
    val sections = DataSection.entries
    val c = LocalCwColors.current

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Space.screen, vertical = Space.md)) {
            Text("مساحة المشروع", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xxs))
            Text("الملفات والمهام والملاحظات والصور في مكان واحد.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        CwSegmented(
            options = sections,
            selectedIndex = sections.indexOf(section),
            label = { it.label },
            onSelect = { vm.setDataSection(sections[it]) },
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xs)
        )
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.exit()) },
            label = "dataSection"
        ) { s ->
            when (s) {
                DataSection.FILES -> FilesScreen(vm, Modifier.fillMaxSize())
                DataSection.TASKS -> TasksScreen(vm, Modifier.fillMaxSize())
                DataSection.NOTES -> NotesScreen(vm, Modifier.fillMaxSize())
                DataSection.PHOTOS -> SitePhotosScreen(vm, Modifier.fillMaxSize())
            }
        }
    }
}
