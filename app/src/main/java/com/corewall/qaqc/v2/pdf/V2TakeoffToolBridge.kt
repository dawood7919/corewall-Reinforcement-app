package com.corewall.qaqc.v2.pdf

import com.corewall.qaqc.takeoff.TakeoffTool

/** نقطة الربط الوحيدة بين أزرار الحصر الحالية ومجموعة الأدوات الأساسية في V2. */
internal fun TakeoffTool.toV2WorkspaceTool(): V2WorkspaceTool? = when (this) {
    TakeoffTool.AREA -> V2WorkspaceTool.AREA
    TakeoffTool.LENGTH -> V2WorkspaceTool.LENGTH
    TakeoffTool.COUNT -> V2WorkspaceTool.COUNT
    TakeoffTool.VOLUME -> V2WorkspaceTool.VOLUME
    TakeoffTool.DEDUCT, TakeoffTool.COLUMN, TakeoffTool.DIMENSION -> null
}
