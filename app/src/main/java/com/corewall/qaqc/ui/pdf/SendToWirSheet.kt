package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.WirEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/**
 * إرسال الصفحة الحالية لطلب فحص.
 *
 * الاسم هو المفتاح: تكتب اسم جديد يتعمل طلب، أو تدوس على طلب موجود
 * فالصفحة تتضاف في آخره. القايمة موجودة عشان الاسم مايتكتبش غلط —
 * "WIR-CW-12" و"WIR CW 12" طلبين مختلفين لو الكتابة هي الطريق الوحيد.
 */
@Composable
fun SendToWirSheet(
    page: Int,
    existing: List<WirEntity>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            Column(Modifier.padding(bottom = Space.md)) {
                Text(
                    "أرسل الصفحة لـWIR",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "صفحة ${page + 1} هتتضاف في آخر ملف الطلب",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            }

            CwField(
                value = name,
                onValueChange = { name = it },
                label = "اسم الطلب",
                placeholder = "مثلاً: WIR-CW-12 — حوائط المحور B"
            )

            if (existing.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                Text(
                    "أو ضيفها لطلب موجود",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(existing, key = { it.id }) { wir ->
                        CwListItem(
                            title = wir.name,
                            subtitle = "${wir.pageCount} صفحة",
                            leading = { CwLeadingIcon(Icons.Filled.FactCheck) },
                            onClick = { onSend(wir.name) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
            CwButton(
                "أرسل",
                { onSend(name) },
                enabled = name.isNotBlank(),
                fillWidth = true
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "الرسمة الأصلية مابتتغيّرش — بتتنسخ منها صفحة واحدة.",
                style = CwText.codeSmall,
                color = c.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
