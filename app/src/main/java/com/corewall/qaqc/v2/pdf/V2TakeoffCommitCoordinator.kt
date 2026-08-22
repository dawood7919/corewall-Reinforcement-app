package com.corewall.qaqc.v2.pdf

import com.corewall.qaqc.takeoff.TakeoffStore

/**
 * منسق حفظ منخفض التردد: يُستدعى فقط عند ضغط «إنهاء»، وليس لكل نقطة قلم.
 * يبقي مساحة العمل V2 مستقلة عن Room، ويحافظ على صيغة بنود الحصر الحالية.
 */
internal class V2TakeoffCommitCoordinator(
    private val drawingId: Long,
    private val store: TakeoffStore
) {
    suspend fun persist(
        finish: V2MeasurementFinishResult,
        name: String,
        colorArgb: Long,
        categoryId: Long? = null
    ): V2TakeoffCommitResult = when (finish) {
        V2MeasurementFinishResult.NoDraft -> V2TakeoffCommitResult.NothingToSave
        is V2MeasurementFinishResult.Incomplete -> V2TakeoffCommitResult.Incomplete(finish.message)
        is V2MeasurementFinishResult.Saved -> {
            val entity = finish.record.toTakeoffEntity(
                drawingId = drawingId,
                name = name,
                colorArgb = colorArgb,
                encodePoints = store::encodeRing
            ).copy(categoryId = categoryId)
            V2TakeoffCommitResult.Persisted(store.saveItem(entity))
        }
    }
}

internal sealed interface V2TakeoffCommitResult {
    data object NothingToSave : V2TakeoffCommitResult
    data class Incomplete(val message: String) : V2TakeoffCommitResult
    data class Persisted(val itemId: Long) : V2TakeoffCommitResult
}
