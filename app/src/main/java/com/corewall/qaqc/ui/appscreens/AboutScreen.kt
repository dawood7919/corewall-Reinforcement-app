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
import com.corewall.qaqc.BuildConfig
import kotlinx.coroutines.launch
import com.corewall.qaqc.update.AvailableUpdate
import com.corewall.qaqc.update.AppUpdater
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
                // الرقم جاي من البناء نفسه مش مكتوب بالإيد.
                //
                // كان مكتوب نصّاً ("الإصدار 8.1 (Build 33)") وفضل مكانه
                // ٩ إصدارات — يعني الشاشة كانت بتقول رقم غلط للمستخدم،
                // وهو أسوأ من ما تقولش حاجة. دلوقتي مستحيل يقدم.
                Text(
                    "الإصدار ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = CwText.code,
                    color = c.textTertiary
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "بناء ${BuildConfig.BUILD_COMMIT}",
                    style = CwText.codeSmall,
                    color = c.textTertiary
                )
            }
        }

        item(key = "update") { UpdateCard() }

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

/**
 * تحديث التطبيق.
 *
 * الفحص بيحصل لما تفتح الشاشة مش عند تشغيل التطبيق: التحديث مش عاجل،
 * وسؤال الشبكة مع كل فتحة بيدفع بطارية وبيانات مقابل حاجة المستخدم
 * بيدوّر عليها لما يحتاجها.
 */
@Composable
private fun UpdateCard() {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Checking) }

    LaunchedEffect(Unit) {
        state = AppUpdater.check()?.let { UpdateUi.Available(it) } ?: UpdateUi.UpToDate
    }

    CwCard {
        when (val s = state) {
            UpdateUi.Checking -> Text("بنشوف فيه تحديث…", style = CwText.codeSmall, color = c.textTertiary)

            UpdateUi.UpToDate -> Text(
                "التطبيق محدَّث لآخر إصدار",
                style = CwText.codeSmall,
                color = c.textTertiary
            )

            is UpdateUi.Available -> {
                Text(
                    "فيه إصدار جديد: ${s.update.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "هيتنزّل هنا، وبعدين النظام هيسألك توافق على التثبيت.",
                    style = CwText.codeSmall,
                    color = c.textTertiary
                )
                Spacer(Modifier.height(Space.sm))
                CwButton("نزّل وثبّت", {
                    state = UpdateUi.Downloading(0f)
                    scope.launch {
                        val file = AppUpdater.download(context, s.update) { p ->
                            state = UpdateUi.Downloading(p)
                        }
                        state = when {
                            file == null -> UpdateUi.Failed
                            !AppUpdater.canInstall(context) -> UpdateUi.NeedsPermission(file)
                            AppUpdater.install(context, file) -> UpdateUi.Installing
                            else -> UpdateUi.Failed
                        }
                    }
                })
            }

            is UpdateUi.Downloading -> {
                Text(
                    "بيتنزّل… ${(s.progress * 100).toInt()}٪",
                    style = CwText.codeSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.xs))
                CwProgressBar(fraction = s.progress)
            }

            is UpdateUi.NeedsPermission -> {
                // مش خطأ: أندرويد بيمنع تثبيت الحزم من تطبيق غير مسموح له،
                // والمستخدم بيسمح مرة واحدة بس.
                Text(
                    "محتاج إذن \"تثبيت تطبيقات غير معروفة\" مرة واحدة",
                    style = CwText.codeSmall,
                    color = c.warning.fg
                )
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CwButton("افتح الإعداد", { AppUpdater.openInstallPermission(context) })
                    CwButton(
                        "ثبّت",
                        { if (!AppUpdater.install(context, s.file)) state = UpdateUi.Failed },
                        style = CwButtonStyle.Secondary
                    )
                }
            }

            UpdateUi.Installing -> Text(
                "شاشة التثبيت اتفتحت",
                style = CwText.codeSmall,
                color = c.textTertiary
            )

            UpdateUi.Failed -> {
                Text("مقدرناش نكمّل التحديث", style = CwText.codeSmall, color = c.danger.fg)
                Spacer(Modifier.height(Space.sm))
                CwButton("جرّب تاني", {
                    state = UpdateUi.Checking
                    scope.launch {
                        state = AppUpdater.check()?.let { UpdateUi.Available(it) } ?: UpdateUi.UpToDate
                    }
                })
            }
        }
    }
}

private sealed interface UpdateUi {
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val update: AvailableUpdate) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class NeedsPermission(val file: java.io.File) : UpdateUi
    data object Installing : UpdateUi
    data object Failed : UpdateUi
}
