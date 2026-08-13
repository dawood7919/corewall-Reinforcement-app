package com.corewall.qaqc.v2.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** لغة V2: تباين عالٍ للرسومات، أسطح هادئة، وموضع لمس واضح بلا مؤثرات ثقيلة. */
object V2Colors {
    val Canvas = Color(0xFF101317)
    val Surface = Color(0xFF181D22)
    val SurfaceRaised = Color(0xFF212830)
    val SurfacePressed = Color(0xFF2A333D)
    val Ink = Color(0xFFF4F7FA)
    val InkMuted = Color(0xFFAAB5C1)
    val Outline = Color(0xFF34414D)
    val Accent = Color(0xFF50D3A2)
    val AccentInk = Color(0xFF06261B)
    val Warning = Color(0xFFFFC14D)
    val Danger = Color(0xFFFF7777)
}

object V2Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object V2Size {
    val touch = 48.dp
    val rail = 68.dp
    val topBar = 58.dp
    val corner = 18.dp
}
