package com.corewall.qaqc.ui.manpower

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.ManpowerSection

/**
 * العمالة — الحضور والتقارير والإحصائيات.
 *
 * قبل كده دي كانت "قسم" تاني بيبدّل مجموعة التبويبات في الشريط السفلي كلها
 * تحت إيد المستخدم، وآخر تبويب فيه كان بيفتح **نفس** شاشة إعدادات القسم
 * التاني. دلوقتي هي وجهة عادية بتبويب داخلي — الشريط السفلي معناه ما بيتغيّرش.
 */
@Composable
fun ManpowerScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val section by vm.manpowerSection.collectAsStateWithLifecycle()
    val sections = ManpowerSection.entries

    Column(modifier.fillMaxSize()) {
        CwSegmented(
            options = sections,
            selectedIndex = sections.indexOf(section),
            label = { it.label },
            onSelect = { vm.setManpowerSection(sections[it]) },
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm)
        )
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.exit()) },
            label = "manpowerSection"
        ) { s ->
            when (s) {
                ManpowerSection.ATTENDANCE -> AttendanceScreen(vm, Modifier.fillMaxSize())
                ManpowerSection.REPORTS -> ManpowerReportsScreen(vm, Modifier.fillMaxSize())
                ManpowerSection.STATISTICS -> ManpowerStatisticsScreen(vm, Modifier.fillMaxSize())
            }
        }
    }
}
