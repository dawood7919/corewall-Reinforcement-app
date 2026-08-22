package com.corewall.qaqc.diag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * بيعرض تقرير آخر قفلة — لو فيه واحد محفوظ.
 *
 * بيتحط فوق الهيكل كله عشان يظهر مهما كانت الشاشة اللي التطبيق فتح
 * عليها. النص قابل للتحديد وفيه زرار نسخ: الهدف إن المستخدم يقدر يبعت
 * السبب من غير ما يحتاج كمبيوتر ولا `adb`.
 */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashReporter.pending(context)) }
    val text = report ?: return

    AlertDialog(
        onDismissRequest = { },
        title = { Text("التطبيق قفل المرة اللي فاتت") },
        text = {
            Column {
                Text(
                    "ده سبب القفلة. ابعتها للمطوّر (صوّر الشاشة أو انسخها) " +
                        "عشان تتصلّح — مش هتظهر تاني بعد ما تقفلها.",
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectionContainer {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
            }) { Text("نسخ") }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashReporter.clear(context)
                report = null
            }) { Text("تمام، اقفلها") }
        }
    )
}
