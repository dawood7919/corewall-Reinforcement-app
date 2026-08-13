package com.corewall.qaqc.v2.pdf

/**
 * جسر أوامر منخفض التردد بين شريط Compose وسطح PDF الأصلي. لا يكشف حركة
 * المنظر أو عينات القلم إلى Compose، ولا يحتفظ بحالة عرض قابلة لإعادة التركيب.
 */
internal class V2WorkspaceController {
    private var workspace: PdfWorkspaceView? = null

    internal fun attach(view: PdfWorkspaceView) {
        workspace = view
    }

    internal fun detach(view: PdfWorkspaceView) {
        if (workspace === view) workspace = null
    }

    fun selectTool(tool: V2WorkspaceTool) {
        workspace?.selectWorkspaceTool(tool)
    }

    fun setCalibration(calibration: V2PageCalibration) {
        workspace?.setMeasurementCalibration(calibration)
    }

    fun setCountCommitImmediately(value: Boolean) {
        workspace?.setCountCommitImmediately(value)
    }

    fun setMeasurementColor(colorArgb: Long) {
        workspace?.setMeasurementColor(colorArgb)
    }

    fun acknowledgeMeasurementPersisted(id: Long) {
        workspace?.acknowledgeMeasurementPersisted(id)
    }

    fun zoomBy(factor: Float) {
        workspace?.zoomBy(factor)
    }

    fun fitPage() {
        workspace?.fitPage()
    }

    fun finishMeasurement(): V2MeasurementFinishResult =
        workspace?.finishMeasurement() ?: V2MeasurementFinishResult.NoDraft

    fun undoMeasurementPoint(): Boolean = workspace?.undoMeasurementPoint() ?: false

    fun cancelMeasurement() {
        workspace?.cancelMeasurement()
    }
}
