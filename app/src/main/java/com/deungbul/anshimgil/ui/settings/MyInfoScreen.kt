package com.deungbul.anshimgil.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.deungbul.anshimgil.data.server.ApiException
import com.deungbul.anshimgil.data.user.CONSENT_BODY
import com.deungbul.anshimgil.data.user.CONSENT_NO
import com.deungbul.anshimgil.data.user.CONSENT_NO_REPLY
import com.deungbul.anshimgil.data.user.CONSENT_TITLE
import com.deungbul.anshimgil.data.user.CONSENT_YES
import com.deungbul.anshimgil.data.user.DELETE_ALL_BODY
import com.deungbul.anshimgil.data.user.DELETE_ALL_DONE
import com.deungbul.anshimgil.data.user.DELETE_ALL_TITLE
import com.deungbul.anshimgil.data.user.DETOUR_CHOICES
import com.deungbul.anshimgil.data.user.MyInfo
import com.deungbul.anshimgil.data.user.MyInfoRepository
import com.deungbul.anshimgil.data.user.REST_CHOICES
import com.deungbul.anshimgil.data.user.STAIRS_CHOICES
import com.deungbul.anshimgil.data.user.TRANSIT_CHOICES
import com.deungbul.anshimgil.data.user.UserMode
import com.deungbul.anshimgil.data.user.nearestChoice
import com.deungbul.anshimgil.data.user.spokenName

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF5E6A62)

/**
 * 내 정보 (팀 할 일 m06): how the walker gets about, as the server keeps it for routes (stairs, going round,
 * resting, transit), and whether trips are kept, in the team's words (consent.md). Each tap is saved at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyInfoScreen(
    mode: UserMode,
    repository: MyInfoRepository,
    onBack: () -> Unit,
    onChangeMode: () -> Unit,
    /** Keeping trips said yes or no: kept on the phone and told to the server. */
    onConsent: suspend (Boolean) -> Unit,
    /** After everything was deleted on the server: the phone asks about keeping trips again. */
    onDeletedAll: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<MyInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        error = null
        info = try { repository.load() } catch (e: ApiException) { error = e.message; null }
    }
    fun save(field: String, value: Any) {
        scope.launch {
            try {
                info = repository.update(mode, JSONObject().put(field, value))
                note = "저장했어요."
            } catch (e: ApiException) {
                note = e.message
            }
        }
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Ink) }
            Text("내 정보", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("길을 고를 때 쓰는 내 정보예요. 누르면 바로 저장돼요.", fontSize = 16.sp, color = Muted)
            note?.let { Text(it, fontSize = 16.sp, color = Color(0xFF1F6B42), fontWeight = FontWeight.Bold) }
            error?.let { e ->
                Text(e, fontSize = 16.sp, color = Color(0xFFB42318))
                OutlinedButton(onClick = { reload++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 하기", fontSize = 16.sp) }
            }

            Section("지금 모드") {
                val i = info
                Text(mode.spokenName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (i?.tempMode != null) {
                    Text("잠깐 쓰는 모드: ${UserMode.fromKey(i.tempMode)?.spokenName ?: i.tempMode}${i.tempUntil?.let { " (${it}까지)" }.orEmpty()}", fontSize = 16.sp, color = Muted)
                }
                OutlinedButton(onClick = onChangeMode, modifier = Modifier.heightIn(min = 48.dp)) { Text("모드 바꾸기", fontSize = 16.sp) }
            }
            info?.let { i ->
                Section("계단은 어떠세요?") {
                    Choices(STAIRS_CHOICES, nearestChoice(i.stairsOk, STAIRS_CHOICES)) { save("stairsOk", it) }
                }
                Section("더 안전한 길이면 얼마나 돌아가도 될까요?") {
                    Choices(DETOUR_CHOICES, nearestChoice(i.maxDetour, DETOUR_CHOICES)) { save("maxDetour", it) }
                }
                Section("얼마나 걸으면 쉬어야 하세요?") {
                    Choices(REST_CHOICES, nearestChoice(i.restEveryM, REST_CHOICES)) { save("restEveryM", it) }
                }
                Section("걷기와 대중교통 중 무엇이 좋으세요?") {
                    Choices(TRANSIT_CHOICES, nearestChoice(i.preferTransit, TRANSIT_CHOICES)) { save("preferTransit", it) }
                }
                Section(CONSENT_TITLE) {
                    Text(CONSENT_BODY, fontSize = 16.sp, color = Ink, lineHeight = 23.sp)
                    if (i.consentTrips) Text("지금 남기고 있어요 · ${i.trips}번", fontSize = 15.sp, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = i.consentTrips, onClick = { scope.launch { onConsent(true); reload++ } },
                            label = { Text(CONSENT_YES, fontSize = 16.sp) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                        FilterChip(
                            selected = !i.consentTrips, onClick = { scope.launch { onConsent(false); note = CONSENT_NO_REPLY; reload++ } },
                            label = { Text(CONSENT_NO, fontSize = 16.sp) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Button(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("내 기록 지우기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(DELETE_ALL_TITLE) },
            text = { Text(DELETE_ALL_BODY, fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        note = try { repository.deleteAll(); onDeletedAll(); DELETE_ALL_DONE } catch (e: ApiException) { e.message }
                        reload++
                    }
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("그만두기") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF4F7F3), RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(choices: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onPick(value) },
                label = { Text(label, fontSize = 16.sp) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
