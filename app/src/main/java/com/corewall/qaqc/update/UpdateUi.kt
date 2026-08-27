package com.corewall.qaqc.update

import java.io.File

/**
 * حالة التحديث.
 *
 * عايشة في [com.corewall.qaqc.MainViewModel] مش في الشاشة، وده الفرق بين
 * تحميل بيكمّل وتحميل بيموت: `rememberCoroutineScope` بيتلغي أول ما
 * الشاشة تخرج من التكوين، يعني كان يكفي تخرج من "عن التطبيق" عشان
 * التحميل يتوقف في نصّه — من غير أي رسالة تقول إنه وقف.
 */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val update: AvailableUpdate) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class NeedsPermission(val file: File) : UpdateUi
    data object Installing : UpdateUi
    data class Failed(val reason: String = "") : UpdateUi
}
