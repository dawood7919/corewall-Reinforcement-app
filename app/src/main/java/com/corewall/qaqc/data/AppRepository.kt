package com.corewall.qaqc.data

import android.content.Context
import com.corewall.qaqc.data.db.AppDatabase
import com.corewall.qaqc.data.db.CommentEntity
import com.corewall.qaqc.data.db.ElementNameEntity
import com.corewall.qaqc.data.db.InspectionEntity
import com.corewall.qaqc.data.db.RangeEditEntity
import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * بيانات الجدول المرجعية بتتقري read-only من الأصول (assets)،
 * وكل تعديلات المستخدم (أسماء/حالات/كومنتات/تعديلات قيم) في Room —
 * بتتحفظ تلقائي وتفضل موجودة بعد قفل التطبيق.
 */
class AppRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val db = AppDatabase.get(context)

    val planData: PlanData = context.assets.open("plan-elements.json")
        .bufferedReader().use { it.readText() }
        .let { json.decodeFromString<PlanData>(it) }

    /** الجدول الأصلي زي ما جه من المكتب — من غير أي تعديلات. */
    val baseSchedule: ScheduleData = context.assets.open("schedule-data.json")
        .bufferedReader().use { it.readText() }
        .let { json.decodeFromString<ScheduleData>(it) }

    val names: Flow<Map<String, String>> =
        db.elementNameDao().observeAll().map { list -> list.associate { it.elementId to it.mark } }

    val inspections: Flow<Map<Pair<String, String>, String>> =
        db.inspectionDao().observeAll().map { list ->
            list.associate { (it.elementId to it.level) to it.status }
        }

    val comments: Flow<List<CommentEntity>> = db.commentDao().observeAll()

    val rangeEdits: Flow<List<RangeEditEntity>> = db.rangeEditDao().observeAll()

    suspend fun setName(elementId: String, mark: String) {
        if (mark.isBlank()) db.elementNameDao().delete(elementId)
        else db.elementNameDao().upsert(ElementNameEntity(elementId, mark.trim()))
    }

    suspend fun setInspection(elementId: String, level: String, status: String) =
        db.inspectionDao().upsert(InspectionEntity(elementId, level, status))

    suspend fun addComment(elementId: String, level: String, text: String) =
        db.commentDao().upsert(
            CommentEntity(elementId = elementId, level = level, text = text, timestamp = System.currentTimeMillis())
        )

    suspend fun deleteComment(id: Long) = db.commentDao().delete(id)

    suspend fun saveRangeEdit(mark: String, rowIndex: Int, patch: Map<String, String>) {
        if (patch.isEmpty()) db.rangeEditDao().delete(mark, rowIndex)
        else db.rangeEditDao().upsert(RangeEditEntity(mark, rowIndex, json.encodeToString(patch)))
    }

    suspend fun clearRangeEdit(mark: String, rowIndex: Int) = db.rangeEditDao().delete(mark, rowIndex)

    // ---------- تطبيق التعديلات فوق الجدول المرجعي ----------

    fun parsePatch(patchJson: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(patchJson) }.getOrDefault(emptyMap())

    fun applyEdits(edits: List<RangeEditEntity>): ScheduleData {
        if (edits.isEmpty()) return baseSchedule
        val byKey = edits.associateBy { it.mark to it.rowIndex }
        val walls = baseSchedule.walls.mapValues { (mark, rows) ->
            rows.mapIndexed { i, row ->
                byKey[mark to i]?.let { applyWallPatch(row, parsePatch(it.patchJson)) } ?: row
            }
        }
        val beams = baseSchedule.beams.mapValues { (mark, rows) ->
            rows.mapIndexed { i, row ->
                byKey[mark to i]?.let { applyBeamPatch(row, parsePatch(it.patchJson)) } ?: row
            }
        }
        return ScheduleData(baseSchedule.levels, walls, beams)
    }

    private fun applyWallPatch(row: WallRange, patch: Map<String, String>): WallRange {
        if (patch.isEmpty()) return row
        return row.copy(
            w = patch["w"]?.toIntOrNull() ?: row.w,
            v = patch["v"] ?: row.v,
            h = patch["h"] ?: row.h,
            t = patch["t"] ?: row.t,
            edited = true
        )
    }

    private fun applyBeamPatch(row: BeamRange, patch: Map<String, String>): BeamRange {
        if (patch.isEmpty()) return row
        fun layers(prefix: String, base: List<String>): List<String> =
            List(maxOf(base.size, 3)) { i -> patch["$prefix$i"] ?: base.getOrElse(i) { "-" } }
        return row.copy(
            w = patch["w"]?.toIntOrNull() ?: row.w,
            d = patch["d"]?.toIntOrNull() ?: row.d,
            bottom = layers("B", row.bottom),
            top = layers("T", row.top),
            side = patch["side"] ?: row.side,
            links = patch["links"] ?: row.links,
            edited = true
        )
    }

    // ---------- نسخة احتياطية ----------

    @Serializable
    data class Backup(
        val version: Int = 1,
        val exportedAt: Long,
        val names: List<ElementNameEntity>,
        val inspections: List<InspectionEntity>,
        val comments: List<CommentEntity>,
        val rangeEdits: List<RangeEditEntity>
    )

    suspend fun exportBackupJson(): String = json.encodeToString(
        Backup(
            exportedAt = System.currentTimeMillis(),
            names = db.elementNameDao().getAll(),
            inspections = db.inspectionDao().getAll(),
            comments = db.commentDao().getAll(),
            rangeEdits = db.rangeEditDao().getAll()
        )
    )

    /** بيرجع رسالة نجاح/فشل مختصرة. */
    suspend fun importBackupJson(content: String): Result<String> = runCatching {
        val backup = json.decodeFromString<Backup>(content)
        db.elementNameDao().upsertAll(backup.names)
        db.inspectionDao().upsertAll(backup.inspections)
        db.commentDao().upsertAll(backup.comments)
        db.rangeEditDao().upsertAll(backup.rangeEdits)
        "تم استيراد ${backup.names.size} اسم و${backup.inspections.size} حالة فحص و${backup.comments.size} كومنت"
    }
}
