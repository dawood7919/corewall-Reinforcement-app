package com.corewall.qaqc.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.CwCountPill
import com.corewall.qaqc.ui.design.CwDivider
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.Dest

/**
 * الدرج — الوجهات **الثانوية** بس.
 *
 * الدرج القديم كان بيخلط أربع حاجات مختلفة تحت عنوانين: تبديل مساحة شغل،
 * تبديل عدسة، وشاشة ورقية — وكلهم شكلهم واحد. وكان فيه صف اسمه "التقارير"
 * بيبدّل القسم كله من غير ما يقول، وصفين بيروحوا لنفس شاشة الإشعارات.
 *
 * دلوقتي: التنقّل الأساسي كله في الشريط السفلي. الدرج فيه اللي مش بيتفتح كل
 * يوم، مجمّع بمعنى حقيقي.
 */
@Composable
fun AppDrawer(vm: MainViewModel, onNavigate: () -> Unit) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val unread by vm.unreadNotifications.collectAsStateWithLifecycle()

    fun go(dest: Dest) {
        vm.go(dest)
        onNavigate()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.surface),
        contentPadding = PaddingValues(bottom = Space.xl)
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(Space.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(Sizes.avatarMd)
                            .clip(Radius.shapeMd)
                            .background(c.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Apartment,
                            contentDescription = null,
                            tint = c.onAccentContainer,
                            modifier = Modifier.size(IconSize.lg)
                        )
                    }
                    Spacer(Modifier.size(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "BHR Tower 1",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary
                        )
                        Text(
                            "Baccarat Hotel & Residences",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                Text(
                    "الدور الشغّال $level",
                    style = CwText.sectionLabel,
                    color = c.textTertiary
                )
            }
            CwDivider(inset = false)
        }

        item(key = "project-header") { CwSectionHeader("المشروع") }
        item(key = "manpower") {
            DrawerRow(Icons.Filled.Groups, "العمالة", "الحضور · التقارير · الإحصائيات") {
                vm.goToManpower(); onNavigate()
            }
        }
        item(key = "photos") {
            DrawerRow(Icons.Filled.PhotoCamera, "صور الموقع", "معرض صور الدور") {
                go(Dest.SitePhotos)
            }
        }
        item(key = "notifications") {
            DrawerRow(
                Icons.Filled.Notifications, "الإشعارات", null,
                badge = unread
            ) { go(Dest.Notifications) }
        }

        item(key = "knowledge-header") { CwSectionHeader("المعرفة") }
        item(key = "floor-knowledge") {
            DrawerRow(
                Icons.Filled.Storage, "ذاكرة الدور",
                "ملفات الدور $level بس — معزولة عن باقي الأدوار"
            ) { go(Dest.FloorKnowledge) }
        }
        item(key = "project-knowledge") {
            DrawerRow(
                Icons.Filled.Hub, "معرفة المشروع",
                "مكتبة مشتركة — متاحة من كل الأدوار",
                tone = CwTone.Pending
            ) { go(Dest.ProjectKnowledge) }
        }
        item(key = "documents") {
            DrawerRow(
                Icons.Filled.Description, "توليد المستندات",
                "تقرير يومي · طلب فحص · طلب مواد"
            ) { go(Dest.DocumentGen) }
        }
        item(key = "ai-settings") {
            DrawerRow(Icons.Filled.AutoAwesome, "إعدادات المساعد", "المزوّد والموديل والمفتاح") {
                go(Dest.AiSettings)
            }
        }

        item(key = "system-header") { CwSectionHeader("النظام") }
        item(key = "settings") {
            DrawerRow(Icons.Filled.Settings, "الإعدادات", null) { go(Dest.Settings) }
        }
        item(key = "sync") {
            DrawerRow(Icons.Filled.Storage, "حالة البيانات", null) { go(Dest.Sync) }
        }
        item(key = "about") {
            DrawerRow(Icons.Filled.Info, "عن التطبيق", null) { go(Dest.About) }
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tone: CwTone = CwTone.Info,
    badge: Int = 0,
    onClick: () -> Unit
) {
    CwListItem(
        title = title,
        subtitle = subtitle,
        leading = { CwLeadingIcon(icon, tone = tone) },
        trailing = if (badge > 0) ({ CwCountPill(badge, tone = CwTone.Danger) }) else null,
        onClick = onClick
    )
}
