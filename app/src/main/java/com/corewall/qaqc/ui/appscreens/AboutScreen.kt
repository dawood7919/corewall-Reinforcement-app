package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.SrtGroupedList
import com.corewall.qaqc.ui.theme.SrtRow

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)).background(srt.blue),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Apartment, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(64.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Core Wall QA/QC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("الإصدار 5.3 (Build 21)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        SrtGroupedList {
            val chevron: @Composable () -> Unit = {
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = srt.text3, modifier = Modifier.size(14.dp))
            }
            SrtRow("سياسة الخصوصية", trailing = chevron, onClick = {})
            SrtRow("شروط الاستخدام", trailing = chevron, onClick = {})
            SrtRow("الترخيص والمصادر", trailing = chevron, onClick = {})
            SrtRow("التواصل مع الدعم الفني", trailing = chevron, onClick = {})
            SrtRow("تقييم التطبيق", trailing = chevron, showDivider = false, onClick = {})
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "BHR Tower 1 — Baccarat Hotel & Residences\nArabian Construction Co. © 2026",
            style = MaterialTheme.typography.bodySmall,
            color = srt.text3,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}
