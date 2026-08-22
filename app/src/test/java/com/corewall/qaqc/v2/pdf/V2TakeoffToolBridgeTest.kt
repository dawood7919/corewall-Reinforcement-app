package com.corewall.qaqc.v2.pdf

import com.corewall.qaqc.takeoff.TakeoffTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2TakeoffToolBridgeTest {
    @Test
    fun coreDockToolsMapToV2WorkspaceTools() {
        assertEquals(V2WorkspaceTool.AREA, TakeoffTool.AREA.toV2WorkspaceTool())
        assertEquals(V2WorkspaceTool.LENGTH, TakeoffTool.LENGTH.toV2WorkspaceTool())
        assertEquals(V2WorkspaceTool.COUNT, TakeoffTool.COUNT.toV2WorkspaceTool())
        assertEquals(V2WorkspaceTool.VOLUME, TakeoffTool.VOLUME.toV2WorkspaceTool())
    }

    @Test
    fun advancedLegacyToolsRemainOnTheLegacyPathUntilTheirV2PortsExist() {
        assertNull(TakeoffTool.DEDUCT.toV2WorkspaceTool())
        assertNull(TakeoffTool.COLUMN.toV2WorkspaceTool())
        assertNull(TakeoffTool.DIMENSION.toV2WorkspaceTool())
    }
}
