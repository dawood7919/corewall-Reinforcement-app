package com.corewall.qaqc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corewall.qaqc.ui.design.ProvideMotionPreferences
import com.corewall.qaqc.ui.shell.AppShell
import com.corewall.qaqc.ui.theme.CoreWallTheme

/**
 * نقطة الدخول — بس.
 *
 * الـActivity القديم كان ٢٦٤ سطر فيه ٣ `when` منفصلين للتنقّل (عنوان،
 * محتوى، رجوع) وشريط تبويبات بيتغيّر حسب القسم. كل ده اتنقل لـ[AppShell]
 * ونظام الوجهات، فالـActivity رجع لدوره الطبيعي: يركّب الثيم ويشغّل الهيكل.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            CoreWallTheme(settings.theme) {
                // إعداد "تقليل الحركة" بيتقري مرة واحدة هنا وبيوصل لكل
                // مكوّن — الحركة اختيارية، والوظيفة مش متوقّفة عليها أبداً.
                ProvideMotionPreferences {
                    AppShell(vm)
                }
            }
        }
    }
}
