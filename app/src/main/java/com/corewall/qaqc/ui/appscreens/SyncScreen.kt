package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.Dest

/**
 * حالة البيانات.
 *
 * الشاشة دي كانت اسمها "المزامنة" وكانت بتقول **"كل البيانات محدثة"**
 * وتحتها "آخر مزامنة: اليوم 09:35 صباحًا" — التوقيت ده كان مكتوب ثابت في
 * الكود، وزرار "مزامنة الآن" كان بيقلب boolean محلي ومش بيعمل أي حاجة،
 * ومفيش سيرفر أصلاً في التطبيق.
 *
 * في أداة جودة ده مش مجرد زحمة — ده خطر: مهندس يفتكر إن فحوصاته متخزّنة
 * على سيرفر وهي موجودة على تليفونه بس، وأول ما التليفون يضيع تضيع معاه.
 *
 * فالشاشة بقت بتقول الحقيقة: البيانات محلية، وده عدد اللي متسجّل فعلاً،
 * والطريقة الوحيدة لتأمينها هي التصدير.
 */
@Composable
fun SyncScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()
    val photos by vm.sitePhotos.collectAsStateWithLifecycle()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "truth") {
            CwBanner(
                title = "البيانات كلها محلية على الجهاز",
                detail = "مفيش سيرفر ولا مزامنة تلقائية. لو التليفون ضاع أو التطبيق " +
                    "اتمسح، البيانات بتضيع معاه. التصدير هو النسخة الاحتياطية الوحيدة.",
                tone = CwTone.Warning
            )
        }

        item(key = "backup") {
            CwCard {
                Text("خُد نسخة", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "التصدير بيطلّع ملف JSON فيه كل الأسماء والحالات والملاحظات " +
                        "وتعديلات الجدول — ينفع للنقل لتليفون تاني أو للأرشيف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
                Spacer(Modifier.height(Space.md))
                CwButton(
                    "روح لتصدير البيانات",
                    { vm.go(Dest.Settings) },
                    icon = Icons.Filled.Settings,
                    fillWidth = true
                )
            }
        }

        item(key = "counts-header") { CwSectionHeader("اللي متسجّل دلوقتي") }

        item(key = "counts") {
            CwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                DataRow(
                    Icons.Filled.Assignment,
                    "عناصر المسقط",
                    "${vm.planData.elements.size} عنصر · ثابتة مع المشروع"
                )
                DataRow(
                    Icons.Filled.Straighten,
                    "فحوصات مسجّلة",
                    "${inspections.size} فحص عبر كل الأدوار"
                )
                DataRow(
                    Icons.Filled.Straighten,
                    "أعداد الحديد",
                    "${barCounts.size} سطر · ${barCounts.count { it.level == level }} في دور $level"
                )
                DataRow(
                    Icons.Filled.EditNote,
                    "ملاحظات",
                    "${notes.size} ملاحظة · ${notes.count { it.level == level }} في دور $level"
                )
                DataRow(
                    Icons.Filled.PhotoCamera,
                    "صور الموقع",
                    "${photos.size} صورة · ${photos.count { it.level == level }} في دور $level"
                )
                DataRow(
                    Icons.Filled.Groups,
                    "سجلات الحضور",
                    "${daily.size} سجل يومي"
                )
            }
        }
    }
}

@Composable
private fun DataRow(icon: ImageVector, title: String, detail: String) {
    CwListItem(
        title = title,
        subtitle = detail,
        leading = { CwLeadingIcon(icon, tone = CwTone.Neutral) }
    )
}
