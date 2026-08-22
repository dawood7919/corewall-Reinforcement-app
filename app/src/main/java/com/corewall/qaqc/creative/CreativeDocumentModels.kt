package com.corewall.qaqc.creative

import kotlinx.serialization.Serializable

object CreativeTemplate {
    const val QUALITY = "QUALITY"
    const val TAKEOFF = "TAKEOFF"
    const val DAILY = "DAILY"
    const val MEETING = "MEETING"
    const val LETTER = "LETTER"

    val all = listOf(QUALITY, TAKEOFF, DAILY, MEETING, LETTER)

    fun label(key: String) = when (key) {
        QUALITY -> "تقرير فحص الجودة"
        TAKEOFF -> "تقرير حصر الكميات"
        DAILY -> "التقرير اليومي"
        MEETING -> "محضر اجتماع"
        LETTER -> "خطاب رسمي"
        else -> "مستند Core Wall"
    }
}

object CreativeBlockKind {
    const val HEADING = "HEADING"
    const val PARAGRAPH = "PARAGRAPH"
    const val BULLETS = "BULLETS"
    const val TABLE = "TABLE"
    const val IMAGE = "IMAGE"
    const val CALLOUT = "CALLOUT"
}

@Serializable
data class CreativeTableRow(val cells: List<String>)

/** كتلة بسيطة ومحايدة عن الصيغة نفسها؛ يمكن رندرها إلى PDF أو صورة أو محرر داخلي. */
@Serializable
data class CreativeBlock(
    val id: String,
    val kind: String,
    val text: String = "",
    val items: List<String> = emptyList(),
    val rows: List<CreativeTableRow> = emptyList(),
    val imagePath: String = "",
    val caption: String = ""
)

@Serializable
data class CreativeDocumentContent(
    val title: String,
    val subtitle: String = "",
    val blocks: List<CreativeBlock>,
    val accentArgb: Long = 0xFF1677FF
)

@Serializable
data class CreativeSourceRef(
    val kind: String,
    val id: String,
    val label: String
)
