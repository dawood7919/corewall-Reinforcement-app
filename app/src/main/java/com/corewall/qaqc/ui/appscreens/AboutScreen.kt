package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwKeyValue
import com.corewall.qaqc.ui.design.CwKeyValueList
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space

/**
 * عن التطبيق.
 *
 * الشاشة القديمة كانت خمس صفوف بسهم — سياسة الخصوصية، شروط الاستخدام،
 * الترخيص، الدعم الفني، تقييم التطبيق — وكلهم `onClick = {}` فاضي. الصف
 * شكله بيوعد بصفحة والدوس عليه ما بيعملش حاجة، وده أسوأ من إنه ما يكونش
 * موجود. اتشالوا، وفضل اللي فيه معلومة حقيقية.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val c = LocalCwColors.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.xl, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "logo") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(Sizes.avatarLg)
                        .clip(Radius.shapeXl)
                        .background(c.accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Apartment,
                        contentDescription = null,
                        tint = c.onAccentContainer,
                        modifier = Modifier.size(IconSize.xl)
                    )
                }
                Spacer(Modifier.height(Space.lg))
                Text(
                    "Core Wall QA/QC",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "الإصدار 8.1 (Build 33)",
                    style = CwText.code,
                    color = c.textTertiary
                )
            }
        }

        item(key = "project-header") { CwSectionHeader("المشروع") }
        item(key = "project") {
            CwCard {
                CwKeyValueList(
                    listOf(
                        CwKeyValue("المشروع", "BHR Tower 1"),
                        CwKeyValue("المبنى", "Baccarat Hotel & Residences"),
                        CwKeyValue("المقاول", "Arabian Construction Co."),
                        CwKeyValue("الأدوار", "48 دور — من B02 لحد ROOF")
                    )
                )
            }
        }

        item(key = "tech-header") { CwSectionHeader("التقنية") }
        item(key = "tech") {
            CwCard {
                CwKeyValueList(
                    listOf(
                        CwKeyValue("المنصّة", "Android — Kotlin + Jetpack Compose"),
                        CwKeyValue("التخزين", "محلي على الجهاز (Room)"),
                        CwKeyValue("المزامنة", "مفيش — التصدير هو النسخة الاحتياطية"),
                        CwKeyValue("المساعد", "بيشتغل بمفتاحك انت، ومن غيره مفيش أي اتصال")
                    )
                )
            }
        }

        item(key = "principle") {
            CwCard {
                Text(
                    "المبدأ",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "التطبيق بيحسب. الذكاء الاصطناعي بيفسّر.\n\n" +
                        "كل رقم بتشوفه — الفجوات، جاهزية الصبّ، فروق التسليح، أوزان " +
                        "الحديد — متحسّب بكود حتمي جوّه التطبيق. الذكاء الاصطناعي " +
                        "بيقرا الأرقام دي ويشرحها، بس عمره ما بيحسبها ولا بياخد قرار.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
        }

        item(key = "footer") {
            Text(
                "Arabian Construction Co. © 2026",
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
