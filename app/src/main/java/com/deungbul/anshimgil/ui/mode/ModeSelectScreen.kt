package com.deungbul.anshimgil.ui.mode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.UserMode

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF3B463F)
private val Ground = Color(0xFFF7FAF6)

/** How each mode looks on the picker. Matches design artboard ①. */
data class ModeStyle(
    val label: String,
    val description: String,
    val shows: String,
    val icon: ImageVector,
    val tile: Color,
    val accent: Color,
)

fun UserMode.style(): ModeStyle = when (this) {
    UserMode.CHILD -> ModeStyle(
        "어린이", "학교·학원 가는 길", "어린이 보호구역, 아동안전지킴이집, CCTV·경찰서·비상벨·편의점·지하철역",
        Icons.Filled.Backpack, Color(0xFFFFF1C9), Color(0xFF8A5A00),
    )
    UserMode.SENIOR -> ModeStyle(
        "어르신", "계단 없이 쉬엄쉬엄", "무더위·한파쉼터, 공중화장실, 피할 계단, CCTV·경찰서·비상벨·편의점·지하철역",
        Icons.Filled.Chair, Color(0xFFE3F2E6), Color(0xFF1F6B42),
    )
    UserMode.DISABLED -> ModeStyle(
        "장애인", "휠체어로 편한 길", "장애인 화장실, 턱 낮춘 횡단보도, 육교(엘리베이터 여부), 피할 계단, CCTV·경찰서·비상벨·편의점·지하철역",
        Icons.Filled.Elevator, Color(0xFFE1EBFB), Color(0xFF1D4ED8),
    )
    UserMode.GENERAL -> ModeStyle(
        "일반인", "밤길도 밝은 길", "CCTV·경찰서·비상벨·편의점·지하철역",
        Icons.Filled.Nightlight, Color(0xFFECE7F6), Color(0xFF5B3FA8),
    )
}

/**
 * First-launch mode picker (issue #2). Also reused when changing the mode later.
 * Large text and touch targets so older users can pick without help.
 */
@Composable
fun ModeSelectScreen(
    initial: UserMode?,
    onStart: (UserMode) -> Unit,
    modifier: Modifier = Modifier,
    onTalk: (() -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("세잎지도", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F6B42))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("누가 걸어가시나요?", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink, lineHeight = 38.sp)
            Text(
                "그림을 한 번 눌러 주세요.\n나중에 언제든 바꿀 수 있어요.",
                fontSize = 17.sp, color = Muted, lineHeight = 26.sp,
            )
        }

        // 말로 맞추기: say who walks and how, and the settings are picked from that.
        onTalk?.let { talk ->
            Surface(
                onClick = talk,
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFE8F1EA),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("말로 맞추기", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF1F6B42))
                    Text("\"우리 엄마가 70대인데 다리가 좀 불편해요\"처럼 말하면 알아서 맞춰 드려요", fontSize = 15.sp, color = Muted, lineHeight = 22.sp)
                }
            }
        }

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserMode.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { mode ->
                        ModeTile(
                            mode = mode,
                            selected = mode == selected,
                            onClick = { selected = mode },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        selected?.let { mode ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFDCE3DC)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("지도에 보여요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(mode.style().shows, fontSize = 15.sp, color = Ink)
                }
            }
        }

        Button(
            onClick = { selected?.let(onStart) },
            enabled = selected != null,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        ) {
            Text(
                selected?.let { "${it.style().label} 길 보기" } ?: "모드를 골라 주세요",
                fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
            if (selected != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        Text(
            "고른 모드는 이 휴대폰에만 저장돼요",
            fontSize = 13.sp, color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ModeTile(
    mode: UserMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = mode.style()
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .heightIn(min = 190.dp)
            .clip(shape)
            .background(style.tile)
            .border(4.dp, if (selected) Ink else Color.Transparent, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, style.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(48.dp))
            }
            Text(style.label, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(style.description, fontSize = 15.sp, color = Color(0xFF2B352F))
        }
        if (selected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("선택", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ModeSelectScreenPreview() {
    ModeSelectScreen(initial = UserMode.SENIOR, onStart = {})
}


/**
 * The mode as a tiny icon: under the status bar on the left of the map, and on the instruction card's
 * corner during walking guidance (11/6). It does not depend on the phone showing notification icons.
 */
@Composable
fun ModeBadge(mode: UserMode, modifier: Modifier = Modifier) {
    val style = mode.style()
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = modifier.size(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(style.icon, contentDescription = "${style.label} 모드", tint = style.accent, modifier = Modifier.size(12.dp))
        }
    }
}
