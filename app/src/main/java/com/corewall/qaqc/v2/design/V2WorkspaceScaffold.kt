package com.corewall.qaqc.v2.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/** إطار V2 خفيف؛ لا يحمل حركة أو شفافية أو طبقات فوق مساحة العمل الحية. */
@Composable
fun V2WorkspaceScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V2Colors.Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(V2Size.topBar)
                .background(V2Colors.Surface)
                .padding(horizontal = V2Space.md, vertical = V2Space.xs)
        ) {
            Text(text = title, color = V2Colors.Ink, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = V2Colors.InkMuted)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
