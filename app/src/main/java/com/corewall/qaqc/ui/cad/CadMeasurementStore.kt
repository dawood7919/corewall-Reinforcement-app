package com.corewall.qaqc.ui.cad

import com.corewall.qaqc.data.db.CadDrawingSettingsEntity
import com.corewall.qaqc.data.db.CadMeasurementDao
import com.corewall.qaqc.data.db.CadMeasurementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** يحوّل قياسات الواجهة إلى كيانات دائمة بلا أي اعتماد على transform الشاشة. */
class CadMeasurementStore(private val dao: CadMeasurementDao) {
    private val json = Json { ignoreUnknownKeys = true }

    fun measurements(filePath: String): Flow<List<CadMeasurement>> =
        dao.observeMeasurements(filePath).map { list -> list.mapNotNull(::decode) }

    suspend fun save(filePath: String, value: CadMeasurement) {
        dao.upsertMeasurement(
            CadMeasurementEntity(
                id = value.id,
                filePath = filePath,
                kind = typeOf(value),
                pointsJson = json.encodeToString(StoredMeasurement(pointsOf(value))),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: Long) = dao.deleteMeasurement(id)
    suspend fun clear(filePath: String) = dao.deleteAllMeasurements(filePath)
    suspend fun settings(filePath: String) = dao.settings(filePath)

    suspend fun saveSettings(filePath: String, unitsPerMeter: Double, unit: MeasureUnit) =
        dao.upsertSettings(CadDrawingSettingsEntity(filePath, unitsPerMeter, unit.name, System.currentTimeMillis()))

    private fun typeOf(value: CadMeasurement) = when (value) {
        is CadMeasurement.Distance -> "DISTANCE"
        is CadMeasurement.Continuous -> "CONTINUOUS"
        is CadMeasurement.AreaPoly -> "AREA"
        is CadMeasurement.Angle -> "ANGLE"
        is CadMeasurement.Radius -> "RADIUS"
    }

    private fun pointsOf(value: CadMeasurement): List<CadPoint> = when (value) {
        is CadMeasurement.Distance -> listOf(value.a, value.b)
        is CadMeasurement.Continuous -> value.points
        is CadMeasurement.AreaPoly -> value.points
        is CadMeasurement.Angle -> listOf(value.armA, value.vertex, value.armB)
        is CadMeasurement.Radius -> listOf(value.center, value.edge)
    }

    private fun decode(entity: CadMeasurementEntity): CadMeasurement? = runCatching {
        val pts = json.decodeFromString<StoredMeasurement>(entity.pointsJson).points
        when (entity.kind) {
            "DISTANCE" -> pts.takeIf { it.size >= 2 }?.let { CadMeasurement.Distance(entity.id, it[0], it[1]) }
            "CONTINUOUS" -> pts.takeIf { it.size >= 2 }?.let { CadMeasurement.Continuous(entity.id, it) }
            "AREA" -> pts.takeIf { it.size >= 3 }?.let { CadMeasurement.AreaPoly(entity.id, it) }
            "ANGLE" -> pts.takeIf { it.size >= 3 }?.let { CadMeasurement.Angle(entity.id, it[1], it[0], it[2]) }
            "RADIUS" -> pts.takeIf { it.size >= 2 }?.let { CadMeasurement.Radius(entity.id, it[0], it[1]) }
            else -> null
        }
    }.getOrNull()

    @Serializable private data class StoredMeasurement(val points: List<CadPoint>)
}
