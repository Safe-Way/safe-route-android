package com.deungbul.anshimgil.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.REPORT_KEEP_HOURS
import com.deungbul.anshimgil.data.ReportSpot
import com.deungbul.anshimgil.data.reportSpotTitle

/** A tapped 신고 쌓인 곳: what, how many, how recent, and what people wrote. */
@Composable
internal fun ReportSpotCard(spot: ReportSpot, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reportSpotTitle(spot), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412),
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color(0xFF17211B)) }
            }
            spot.memos.take(3).forEach {
                Text("· $it", fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF3F4A43), modifier = Modifier.padding(end = 12.dp, top = 2.dp))
            }
            val photos = spot.reports.sumOf { it.photoCount }
            Text(
                (if (photos > 0) "사진 ${photos}장 · " else "") + "여러 사람이 알려 준 곳이에요. ${REPORT_KEEP_HOURS}시간이 지나면 사라져요.",
                fontSize = 14.sp, color = Color(0xFF5E6A62), modifier = Modifier.padding(end = 12.dp, top = 6.dp),
            )
        }
    }
}
