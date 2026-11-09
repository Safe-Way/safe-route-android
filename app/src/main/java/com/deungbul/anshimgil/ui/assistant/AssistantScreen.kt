package com.deungbul.anshimgil.ui.assistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.AppAssistant
import com.deungbul.anshimgil.data.AssistantAction
import com.deungbul.anshimgil.data.AssistantContext
import com.deungbul.anshimgil.data.pickLandmark
import com.deungbul.anshimgil.data.placeDetailAnswer
import com.deungbul.anshimgil.data.withDirection
import com.deungbul.anshimgil.data.ChatLine
import com.deungbul.anshimgil.data.NEARBY_KINDS
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.location.phoneOrigin
import androidx.compose.foundation.layout.Box
import com.deungbul.anshimgil.map.addMyLocation
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.location.lastKnownLocation
import com.deungbul.anshimgil.ui.guide.rememberTts
import com.deungbul.anshimgil.ui.mode.Bubble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF5E6A62)
private val Green = Color(0xFF1F6B42)

private const val HELLO = "무엇을 도와드릴까요? 편하게 말해 주세요.\n예: \"근처 화장실로 안내해 줘\", \"글씨가 안 보여\""
private val STARTERS = listOf("근처 화장실로 안내해 줘", "글씨가 잘 안 보여", "가까운 버스 보여 줘", "가까운 편의점")

/**
 * 말로 하기: the map screen's assistant. Settings change right here ([onChangeSettings]); places are
 * found here and opened as a route ([onRoute]); buses and reports go back to the map ([onShowBuses],
 * [onOpenReport]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    assistant: AppAssistant,
    context: AssistantContext,
    placeRepository: PlaceRepository,
    onChangeSettings: (AssistantAction.ChangeSettings) -> Unit,
    onRoute: (Place) -> Unit,
    /** Like [onRoute], opening on the 대중교통 tab. */
    onRouteByTransit: (Place) -> Unit = onRoute,
    onShowBuses: () -> Unit,
    onOpenReport: (AssistantAction.OpenReport) -> Unit,
    /** Go to the map and open SOS (it counts down before texting, so it can still be cancelled). */
    onAlertGuardian: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The line AI 비서 opens with, when it came from its bubble on the map; otherwise the greeting. */
    opening: String? = null,
    /** Opened from the map's microphone button: start listening at once. */
    listenAtStart: Boolean = false,
    /** The conversation, kept by the caller while the app runs so it is still there on coming back. */
    chat: AssistantChat = remember { AssistantChat() },
    /** Takes back the last change AI 비서 made; false when there is none (사용성 점검표 21번). */
    onUndo: () -> Boolean = { false },
) {
    val android = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = chat.lines
    // A new chat starts with the greeting (or the bubble's line); coming back, a bubble's line is added on.
    remember {
        if (!chat.greeted) {
            chat.greeted = true
            lines += ChatLine(fromUser = false, text = opening ?: HELLO)
            chat.choices = STARTERS
        } else if (opening != null) {
            lines += ChatLine(fromUser = false, text = opening)
            chat.choices = emptyList()
        }
        true
    }
    var choices by chat::choices
    var thinking by remember { mutableStateOf<String?>(null) }
    var urgent by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    // What the microphone heard last, shown with 다시 말하기 / 글자 고치기 (사용성 점검표 6번).
    var heard by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Replies are read aloud when 음성 안내 is on.
    val tts = rememberTts(android)
    fun say(text: String) {
        lines += ChatLine(fromUser = false, text = text)
        if (context.settings.voiceGuide) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "assistant")
    }

    suspend fun findAndGo(find: suspend (lat: Double, lon: Double) -> Place?, missing: String) {
        thinking = "찾는 중…"
        val from = phoneOrigin(android).point
        val place = try {
            find(from.lat, from.lon)
        } catch (e: ApiException) {
            say(e.message ?: "지금은 찾을 수 없어요. 잠시 뒤에 다시 해 주세요.")
            return
        }
        if (place == null) {
            say(missing)
            return
        }
        chat.lastPlace = place
        say("${withDirection(place.name)} 길을 찾았어요.")
        delay(600)
        onRoute(place)
    }

    fun send(text: String, byVoice: Boolean = false) {
        val said = text.trim()
        if (said.isEmpty() || thinking != null) return
        heard = if (byVoice) said else null
        if (said == UNDO_CHOICE) {
            lines += ChatLine(fromUser = true, text = said)
            choices = emptyList()
            say(if (onUndo()) "되돌렸어요." else "되돌릴 게 없어요.")
            return
        }
        lines += ChatLine(fromUser = true, text = said)
        input = ""
        choices = emptyList()
        urgent = false
        // A question about the place just found ("그곳 몇 층이야") is answered from what the app knows of it.
        chat.lastPlace?.let { place ->
            placeDetailAnswer(said, place)?.let { answer ->
                say(answer.text)
                if (answer.showOnMap) scope.launch { delay(1500); onRoute(place) }
                return
            }
        }
        thinking = "생각하는 중…"
        // Taking long (the server, say): after 3 s it says so, aloud or with a tone (사용성 점검표 20번).
        val slow = scope.launch {
            delay(SLOW_MS)
            thinking = "잠시만요, 찾고 있어요…"
            if (context.settings.voiceGuide) tts.speak("잠시만요, 찾고 있어요.", TextToSpeech.QUEUE_ADD, null, "wait")
            else runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
        scope.launch {
            // The greeting is the app's own, so only what the user said and the replies go out.
            val action = assistant.reply(lines.drop(1), context)
            slow.cancel()
            say(action.say)
            when (action) {
                is AssistantAction.ChangeSettings -> {
                    onChangeSettings(action)
                    say("바꿨어요. 잘못 바뀌었으면 [되돌리기]를 눌러 주세요.")
                    choices = listOf(UNDO_CHOICE)
                }
                is AssistantAction.Emergency -> urgent = true
                is AssistantAction.GoNearby -> findAndGo(
                    { lat, lon -> placeRepository.nearby(action.kind, context.mode, lat, lon).places.firstOrNull() },
                    "1km 안에서 ${NEARBY_KINDS[action.kind]}을(를) 찾지 못했어요.",
                )
                is AssistantAction.GoTo -> findAndGo(
                    { lat, lon -> pickLandmark(placeRepository.search(action.query, lat, lon)) },
                    "'${action.query}'을(를) 찾지 못했어요. 이름을 조금 다르게 말해 주세요.",
                )
                is AssistantAction.GoPlace -> { chat.lastPlace = action.place; delay(1200); if (action.transit) onRouteByTransit(action.place) else onRoute(action.place) }
                is AssistantAction.ShowBuses -> { delay(500); onShowBuses() }
                is AssistantAction.OpenReport -> { delay(1200); onOpenReport(action) }
                is AssistantAction.Ask -> choices = action.choices
                is AssistantAction.Answer -> Unit
            }
            thinking = null
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it, byVoice = true) }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            say("이 휴대폰은 음성 입력을 쓸 수 없어요. 글로 써 주세요.")
        }
    }

    LaunchedEffect(Unit) { if (listenAtStart) listen() }

    LaunchedEffect(lines.size, thinking) { listState.animateScrollToItem(maxOf(0, lines.size)) }

    // The map around the walker shows faintly behind the chat, so they keep their bearings.
    val here = remember { lastKnownLocation(android) }
    Box(modifier.fillMaxSize().background(Color(0xFFF7F8F5))) {
    if (here != null) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
            center = com.kakao.vectormap.LatLng.from(here.lat, here.lon),
            zoomLevel = 16,
            onMapReady = { map -> map.addMyLocation(android, here) },
        )
        // A white veil over the map keeps the words easy to read.
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F8F5).copy(alpha = 0.72f)))
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "지도로 돌아가기", tint = Ink) }
            Text("AI 비서", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            items(lines) { line -> Bubble(line) }
            thinking?.let { item { Text(it, fontSize = 15.sp, color = Muted) } }
            if (urgent) item {
                EmergencyCard(
                    onCall = {
                        // Opens the dialer with 112 filled in; the person presses call.
                        android.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                    },
                    onGuardian = onAlertGuardian,
                    onPolice = { urgent = false; send("가까운 파출소로 안내해 줘") },
                    onFine = { urgent = false; say("다행이에요. 필요하면 언제든 말해 주세요.") },
                )
            }
        }
        if (choices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                choices.forEach { c -> SuggestionChip(
                        onClick = { send(c) }, label = { Text(c, fontSize = 16.sp, color = Ink) },
                        colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) }
            }
        }
        heard?.let { h ->
            // Heard wrong? Say it again, or fix the words and send them.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Text("잘못 들었나요?", fontSize = 16.sp, color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
                OutlinedButton(onClick = { heard = null; listen() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 말하기", fontSize = 16.sp) }
                OutlinedButton(onClick = { input = h; heard = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("글자 고치기", fontSize = 16.sp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(300) },
                placeholder = { Text("여기에 쓰거나 마이크를 눌러 말해요") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Ink),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                modifier = Modifier.weight(1f),
            )
            // The same simple rounded square as the microphone in walking guidance.
            Surface(
                onClick = ::listen,
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                contentColor = Green,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
                modifier = Modifier.padding(start = 8.dp).size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = "말로 하기", modifier = Modifier.size(26.dp))
                }
            }
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && thinking == null) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기", tint = if (input.isNotBlank()) Green else Muted)
            }
        }
    }
    }
}

/** The chip that takes back AI 비서's last change. */
const val UNDO_CHOICE = "되돌리기"

/** How long AI 비서 may think before it says it is still looking. */
private const val SLOW_MS = 3_000L

/** How far back a live bubble's line counts as already said. */
private const val RECENT_LINES = 8

/** 급한 말에: 112 / 보호자 / 파출소 / 괜찮아요, big enough to hit in a hurry. */
@Composable
private fun EmergencyCard(onCall: () -> Unit, onGuardian: () -> Unit, onPolice: () -> Unit, onFine: () -> Unit) {
    val red = Color(0xFFB42318)
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCall, colors = ButtonDefaults.buttonColors(containerColor = red), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("112에 전화", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onGuardian, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("보호자에게 알리기 (SOS)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onPolice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("가까운 파출소로 가기", fontSize = 16.sp) }
            OutlinedButton(onClick = onFine, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("괜찮아요", fontSize = 16.sp) }
        }
    }
}


/**
 * AI 비서's conversation while the app runs: kept when leaving the screen and coming back, gone when the
 * app is closed (it is only in memory, never saved).
 */
class AssistantChat {
    val lines = mutableStateListOf<ChatLine>()
    var choices by mutableStateOf<List<String>>(emptyList())
    /** The place found last, so "그곳 몇 층이야" is about it. */
    var lastPlace by mutableStateOf<Place?>(null)
    /** The greeting has been said (live lines may come in before AI 비서 is first opened). */
    var greeted by mutableStateOf(false)

    /**
     * A line from AI 비서's live bubbles (map, guidance) or the walker's tap on one, kept here so the chat shows
     * the whole conversation (11/8). The same words twice in a row are kept once.
     */
    fun note(fromUser: Boolean, text: String) {
        if (text.isBlank()) return
        val last = lines.lastOrNull()
        if (last != null && last.fromUser == fromUser && last.text == text) return
        // A bubble's question shown again on coming back to the map is not added again (e.g. the calendar offer).
        if (!fromUser && lines.takeLast(RECENT_LINES).any { !it.fromUser && it.text == text }) return
        lines += ChatLine(fromUser = fromUser, text = text)
    }
}
