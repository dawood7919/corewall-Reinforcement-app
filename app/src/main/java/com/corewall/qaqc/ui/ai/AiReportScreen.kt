package com.corewall.qaqc.ui.ai

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.model.ReportKind
import com.corewall.qaqc.ui.notes.NoteContent
import com.corewall.qaqc.ui.theme.LocalSrtColors

/**
 * توليد المستندات الهندسية: تقرير يومي/أسبوعي/فحص/طلب مواد/تعليمات موقع —
 * كلها مبنية على بيانات الدور الحقيقية ومعرفة المستندات المرفوعة.
 */
@Composable
fun AiReportScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val context = LocalContext.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.reportBusy.collectAsStateWithLifecycle()
    val error by vm.reportError.collectAsStateWithLifecycle()
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("توليد مستند لدور $level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "الـAI بيكتب المستند من بيانات التطبيق والمستندات المحلّلة — الأرقام محسوبة مش مخترعة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!cfg.isConfigured) {
            Spacer(Modifier.height(14.dp))
            Text("ضيف مفتاح API من إعدادات المساعد الذكي الأول.",
                style = MaterialTheme.typography.bodySmall, color = srt.orange)
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKind.entries.forEach { kind ->
                Surface(
                    onClick = { if (!busy) vm.generateReport(kind) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = srt.blue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(kind.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(kind.prompt, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            }
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            Text("بيكتب المستند…", style = MaterialTheme.typography.bodySmall, color = srt.blue)
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = srt.red)
        }

        report?.let { r ->
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    onClick = {
                        vm.saveReportToFiles { f ->
                            Toast.makeText(
                                context,
                                if (f != null) "اتحفظ في ملفات الدور ✓" else "فشل الحفظ",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp), color = srt.blueTint
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = srt.blue, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("حفظ", style = MaterialTheme.typography.labelMedium, color = srt.blue)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = {
                        vm.saveReportToFiles { f -> f?.let { vm.files.share(it) } }
                    },
                    shape = RoundedCornerShape(10.dp), color = srt.green.copy(alpha = 0.14f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = srt.green, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("مشاركة", style = MaterialTheme.typography.labelMedium, color = srt.green)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    // نعرضه بالماركداون الموجود في التطبيق
                    NoteContent(markdown = r.markdown)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
