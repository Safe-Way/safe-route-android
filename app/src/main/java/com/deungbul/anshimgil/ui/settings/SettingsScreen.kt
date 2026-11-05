package com.deungbul.anshimgil.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.AppSettings
import com.deungbul.anshimgil.data.TextSize
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.optionalMapItems
import com.deungbul.anshimgil.ui.mode.style

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF4A564E)
private val Green = Color(0xFF1F6B42)

/** Settings (issue #8). Everything here stays on this phone (NFR-07). */
@Composable
fun SettingsScreen(
    mode: UserMode,
    settings: AppSettings,
    onBack: () -> Unit,
    onChangeMode: () -> Unit,
    onManagePlaces: () -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    onAutoConditionsChange: (Boolean) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onExtraMapItemChange: (key: String, on: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "지도로 돌아가기", tint = Ink)
            }
            Text("설정", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }

        SectionTitle("내 설정")
        LinkRow("모드 바꾸기", "지금: ${mode.style().label}", onClick = onChangeMode)
        LinkRow("자주 가는 곳 관리", "집, 학교, 직장 등록과 변경", onClick = onManagePlaces)

        SectionTitle("길찾기")
        SwitchRow("음성 안내", "걷는 동안 다음 방향을 읽어 줘요", settings.voiceGuide, onVoiceChange)
        SwitchRow("상황 자동 판단", "밤, 비, 폭염 등을 알아서 경로에 반영해요", settings.autoConditions, onAutoConditionsChange)

        SectionTitle("지도에 더 보기")
        Text(
            "${mode.style().label} 모드에 기본으로 보이는 것 외에, 더 보고 싶은 정보를 골라요.",
            fontSize = 14.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        mode.optionalMapItems.forEach { item ->
            CheckRow(item.title, item.key in settings.extraMapItems) { onExtraMapItemChange(item.key, it) }
        }

        SectionTitle("글자 크기")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .selectableGroup(),
        ) {
            TextSize.entries.forEach { size ->
                val selected = size == settings.textSize
                Text(
                    size.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (selected) Color.White else Ink,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Ink else Color(0xFFEFF3EE))
                        .selectable(selected = selected, role = Role.RadioButton) { onTextSizeChange(size) }
                        .padding(vertical = 14.dp),
                )
            }
        }

        SectionTitle("정보")
        LinkRow("위치 권한", "휴대폰 설정에서 바꿀 수 있어요") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            InfoText("개인정보", "고른 모드와 자주 가는 곳은 이 휴대폰에만 저장해요. 걷는 동안의 위치는 길안내에만 쓰고 저장하지 않아요.")
            InfoText("데이터 출처", "공공데이터포털, 생활안전지도, 재난안전데이터 공유플랫폼, 기상청, 에어코리아, 카카오")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(subtitle, fontSize = 14.sp, color = Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
        }
        HorizontalDivider(color = Color(0xFFEDF1EC))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(subtitle, fontSize = 14.sp, color = Muted)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Green),
            )
        }
        HorizontalDivider(color = Color(0xFFEDF1EC))
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Green))
            Text(title, fontSize = 17.sp, color = Ink, modifier = Modifier.padding(start = 12.dp))
        }
        HorizontalDivider(color = Color(0xFFEDF1EC))
    }
}

@Composable
private fun InfoText(title: String, body: String) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(body, fontSize = 14.sp, color = Muted, lineHeight = 21.sp)
    }
}
