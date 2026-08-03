package com.corewall.qaqc.ai.model

import kotlinx.serialization.Serializable

/**
 * لقطة منظّمة لبيانات الدور الشغّال — دي اللي بتتبعت للـ AI.
 *
 * مهم: كل الأرقام والحقائق هنا **محسوبة** بمنطق التطبيق نفسه
 * (ScheduleLogic / AttentionDiff / SteelCalculator) — الـ AI بيفسّر بس،
 * مش بيحسب. كده الأرقام مستحيل تتهلوس.
 */
@Serializable
data class FloorContext(
    val project: String,
    val level: String,
    val levelIndex: Int,
    val totalLevels: Int,
    val previousLevel: String?,
    val nextLevel: String?,
    val elements: ElementsSummary,
    val reinforcement: List<ElementReinforcement>,
    val dataGaps: List<DataGap>,
    val changes: ChangesSummary,
    val barCountChecks: List<BarCountCheck>,
    val inspection: InspectionSummary,
    val quantities: QuantitySummary,
    val documentation: DocumentationSummary,
    val manpower: ManpowerSummary
)

@Serializable
data class ElementsSummary(
    val total: Int,
    val named: Int,
    val unnamed: Int,
    val walls: Int,
    val couplingBeams: Int,
    val internalBeams: Int
)

/** تسليح عنصر واحد في الدور الحالي (الصف الشغّال بس). */
@Serializable
data class ElementReinforcement(
    val mark: String,
    val type: String,               // WALL | BEAM
    val rangeFrom: String,
    val rangeTo: String?,
    val widthMm: Int,
    val depthMm: Int? = null,
    val vertical: String? = null,   // حوائط
    val horizontal: String? = null,
    val ties: String? = null,
    val bottom: String? = null,     // كمرات
    val top: String? = null,
    val side: String? = null,
    val links: String? = null,
    val revised: Boolean = false,
    val userEdited: Boolean = false,
    val note: String? = null,
    val inspectionStatus: String
)

/** فجوة بيانات حقيقية: الدور جوّه مدى العنصر لكن مفيش صف يغطيه. */
@Serializable
data class DataGap(
    val mark: String,
    val type: String,
    val missingLevels: List<String>
)

@Serializable
data class ChangesSummary(
    val changedFromPrevious: List<ElementChange>,
    val changingInNext: List<ElementChange>
)

@Serializable
data class ElementChange(
    val mark: String,
    val type: String,
    val fields: List<FieldDelta>
)

@Serializable
data class FieldDelta(val field: String, val before: String, val after: String)

/** مقارنة عدّ الأسياخ: الموقع مقابل الرسمة. */
@Serializable
data class BarCountCheck(
    val mark: String,
    val siteTotals: Map<String, Int>,     // القطر -> العدد
    val drawingTotals: Map<String, Int>,
    val matches: Boolean,
    val missingOnSite: Map<String, Int> = emptyMap()
)

@Serializable
data class InspectionSummary(
    val approved: Int,
    val cast: Int,
    val wirSubmitted: Int,
    val rejected: Int,
    val notInspected: Int,
    val completionPercent: Int,
    val rejectedMarks: List<String> = emptyList()
)

/** كميات الحديد المحسوبة من الكولاوتات (مش تقديرات AI). */
@Serializable
data class QuantitySummary(
    val wallVerticalAreaPerMeterMm2: Double,
    val wallHorizontalAreaPerMeterMm2: Double,
    val unparsedCallouts: List<String>,
    val heaviestElements: List<String>
)

@Serializable
data class DocumentationSummary(
    val notes: Int,
    val notesWithImages: Int,
    val attachments: Int,
    val openTasks: Int,
    val doneTasks: Int,
    val overdueTasks: Int,
    val recentNoteTitles: List<String>
)

@Serializable
data class ManpowerSummary(
    val companies: Int,
    val workersToday: Int,
    val foremenToday: Int,
    val engineersToday: Int,
    val averageWorkersPerDay: Int,
    val daysRecorded: Int
)
