package com.deungbul.anshimgil.ui.settings

import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.deungbul.anshimgil.data.Guardian
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.deungbul.anshimgil.ui.safety.canSendSms
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
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
    /** 내 정보 (m06). */
    onOpenMyInfo: () -> Unit = {},
    onVoiceChange: (Boolean) -> Unit,
    onVoiceChatChange: (Boolean) -> Unit = {},
    onAutoConditionsChange: (Boolean) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onExtraMapItemChange: (key: String, on: Boolean) -> Unit,
    onAddGuardian: (Guardian) -> Unit,
    onRemoveGuardian: (Guardian) -> Unit,
    onTurnSignsChange: (Boolean) -> Unit,
    onSosButtonChange: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier,
    /** 이동 기록: whether trips are kept, how many wait to go to the server, and wiping them. */
    tripConsent: Boolean = false,
    /** Trips the server keeps for this phone; null when unknown. */
    tripCount: Int? = null,
    onTripConsentChange: (Boolean) -> Unit = {},
    /** Returns false when the server copy could not be deleted. */
    onClearTrips: suspend () -> Boolean = { true },
    /** 쓰는 대로 맞추기 (Personal.kt). */
    adapt: Boolean = true,
    onAdaptChange: (Boolean) -> Unit = {},
    /** 캘린더 일정으로 길 알려 주기 (turning it on asks for the calendar permission). */
    calendarOn: Boolean = false,
    onCalendarChange: (Boolean) -> Unit = {},
    onResetAdapt: () -> Unit = {},
    /** How much AI 비서 has made the text bigger on top of the 글자 크기 below (1 = none). */
    textBoost: Float = 1f,
    onResetTextBoost: () -> Unit = {},
) {
    val context = LocalContext.current
    var addingGuardian by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
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
            Text("설정", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.weight(1f))
            // Every change is saved as it is made; this confirms it and goes back to the map.
            TextButton(
                onClick = {
                    Toast.makeText(context, "설정을 저장했어요", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("저장", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F6B42)) }
        }

        SectionTitle("내 설정")
        LinkRow("모드 바꾸기", "지금: ${mode.style().label}", onClick = onChangeMode)
        LinkRow("자주 가는 곳 관리", "집, 학교, 직장 등록과 변경", onClick = onManagePlaces)
        LinkRow("내 정보", "계단, 돌아가는 정도, 쉬는 간격, 기록 남기기", onClick = onOpenMyInfo)

        SectionTitle("귀가 지킴이")
        Text(
            "SOS가 울리거나 길안내 중 정한 길에서 벗어났을 때, 여기 등록한 보호자에게 지금 위치를 문자로 보내요.",
            fontSize = 14.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        settings.guardians.forEach { guardian ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(guardian.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(guardian.phone, fontSize = 14.sp, color = Muted)
                }
                IconButton(onClick = { onRemoveGuardian(guardian) }) {
                    Icon(Icons.Filled.Close, contentDescription = "${guardian.name} 삭제", tint = Muted)
                }
            }
        }
        // Turning SOS on asks to send texts, so the guardians get the text without another tap.
        // Without the permission SOS still works and opens the message app instead.
        val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSosButtonChange(true) }
        SwitchRow(
            "SOS 버튼",
            "지도 화면에 SOS 버튼을 보여 줘요. 누르고 5초가 지나면 보호자에게 지금 위치를 문자로 보내요",
            settings.sosButton,
        ) { on ->
            if (on && !canSendSms(context)) smsPermission.launch(Manifest.permission.SEND_SMS) else onSosButtonChange(on)
        }
        if (settings.guardians.size < Guardian.MAX) {
            LinkRow("보호자 추가", "이름과 전화번호 (최대 ${Guardian.MAX}명)") { addingGuardian = true }
        }

        SectionTitle("길찾기")
        SwitchRow("음성 안내", "걷는 동안 다음 방향을 읽어 줘요", settings.voiceGuide, onVoiceChange)
        SwitchRow("AI 비서와 말로 대화", "AI 비서를 열면 바로 듣고, 대답한 뒤 다시 들어요", settings.voiceChat, onVoiceChatChange)
        SwitchRow("상황 자동 판단", "밤, 비, 폭염 등을 알아서 경로에 반영해요", settings.autoConditions, onAutoConditionsChange)
        SwitchRow("꺾는 곳 표시", "길안내 지도에서 크게 꺾는 곳에 ↰ ↱ 표시를 보여 줘요", settings.turnSigns, onTurnSignsChange)

        SectionTitle("쓰는 대로 맞추기")
        SwitchRow(
            "쓰는 대로 맞추기",
            "자주 쓰는 버튼을 앞에, 자주 고르는 길을 먼저 보여 주고, 필요해 보이면 글씨를 크게 할지 물어봐요. 폰 안에서만 세요",
            adapt,
            onAdaptChange,
        )
        SwitchRow("캘린더 일정으로 길 알려 주기", "장소가 적힌 일정 전에 길을 먼저 알려 줘요. 일정은 폰 안에서만 봐요", calendarOn, onCalendarChange)
        if (textBoost > 1.001f) {
            // The AI part is separate from 글자 크기 (작게·보통·크게), which stays as chosen.
            LinkRow(
                "AI가 더 키운 글씨 · ${"%.2f".format(textBoost).trimEnd('0').trimEnd('.')}배",
                "아래 '글자 크기' 위에 더해져요. 누르면 AI가 키운 만큼을 되돌려요",
                onResetTextBoost,
            )
        }
        var confirmResetAdapt by remember { mutableStateOf(false) }
        LinkRow("맞춘 것 처음대로", "버튼 순서·고르는 길·AI 글씨·이동 패턴을 처음 상태로 돌려요") { confirmResetAdapt = true }
        if (confirmResetAdapt) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmResetAdapt = false },
                title = { Text("맞춘 것을 처음대로 돌릴까요?") },
                text = { Text("쓰면서 배운 버튼 순서, 자주 고르는 길, 머무는 시간, 이동 패턴을 지우고, AI가 키운 글씨와 캘린더 알림도 처음대로 돌려요.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmResetAdapt = false; onResetAdapt() }) { Text("처음대로") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmResetAdapt = false }) { Text("취소") } },
            )
        }

        SectionTitle("이동 기록")
        SwitchRow(
            "다닌 기록 남기기",
            "간 곳 이름, 요일·시간대(30분 단위), 걸었는지 버스였는지, 날씨, 한마디를 남겨요. 지나온 길·집 주소·정확한 시각은 남기지 않아요" +
                if (tripConsent && tripCount != null) " · 지금 ${tripCount}번 기억함" else "",
            tripConsent,
            onTripConsentChange,
        )
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var confirmClearTrips by remember { mutableStateOf(false) }
        LinkRow("이동 기록 모두 지우기", "서버에 남은 기록을 지워요") { confirmClearTrips = true }
        if (confirmClearTrips) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmClearTrips = false },
                title = { Text("이동 기록을 모두 지울까요?") },
                text = { Text("지우면 자주 가는 길 제안을 처음부터 다시 배워요.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmClearTrips = false
                        scope.launch {
                            val all = onClearTrips()
                            android.widget.Toast.makeText(
                                context,
                                if (all) "이동 기록을 지웠어요" else "서버에 연결하지 못해서 지우지 못했어요. 잠시 뒤 다시 해 주세요.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("지우기") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClearTrips = false }) { Text("취소") } },
            )
        }

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
            InfoText("개인정보", "자주 가는 곳과 보호자 연락처는 이 휴대폰에만 저장해요. 길을 맞게 찾으려고 고른 모드와 앱이 만든 무작위 번호만 서버에 두고, 이름·전화번호는 알 수 없어요. 다닌 기록은 동의했을 때만 남겨요. 걷는 동안의 위치는 길안내에만 쓰고 저장하지 않아요. 보호자 문자는 SOS나 ‘보호자에게 알리기’를 눌렀을 때만 나가요.")
            InfoText("데이터 출처", "공공데이터포털, 생활안전지도, 재난안전데이터 공유플랫폼, 기상청, 에어코리아, 카카오")
        }

        // Back to the first-run settings, after asking once.
        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).heightIn(min = 52.dp),
        ) { Text("기본 설정으로 되돌리기", fontSize = 16.sp, color = Ink) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("기본 설정으로 되돌릴까요?") },
            text = { Text("음성 안내, 상황 자동 판단, 글자 크기, 지도에 더 보기, 꺾는 곳 표시, SOS 버튼이 처음 상태로 돌아가요. 모드, 자주 가는 곳, 보호자 연락처는 그대로예요.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetToDefaults()
                    confirmReset = false
                    Toast.makeText(context, "기본 설정으로 되돌렸어요", Toast.LENGTH_SHORT).show()
                }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }

    if (addingGuardian) {
        GuardianDialog(
            onDismiss = { addingGuardian = false },
            onSave = { onAddGuardian(it); addingGuardian = false },
        )
    }
}

/** Name and phone number for a new guardian; saving is allowed once both look right. */
@Composable
private fun GuardianDialog(onDismiss: () -> Unit, onSave: (Guardian) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val cleanPhone = Guardian.cleanPhone(phone)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보호자 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 엄마)") }, singleLine = true)
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("전화번호") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotBlank() && cleanPhone == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { cleanPhone?.let { onSave(Guardian(name.trim(), it)) } },
                enabled = name.isNotBlank() && cleanPhone != null,
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
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
