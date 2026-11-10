package com.deungbul.anshimgil.ui.assistant

import com.deungbul.anshimgil.data.StopPlace
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
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
    /** A trip with 들를 곳 on the way (AI 비서 planned them): the place, the stops in order, and whether by transit. */
    onRouteWithStops: (Place, List<StopPlace>, Boolean) -> Unit = { p, _, t -> if (t) onRouteByTransit(p) else onRoute(p) },
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
    // Showing something on the map waits for the walker's yes (11/10): what to open, until [지도에서 볼게요].
    var pendingGo by remember { mutableStateOf<(() -> Unit)?>(null) }
    var thinking by remember { mutableStateOf<String?>(null) }
    var urgent by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    // What the microphone heard last, shown with 다시 말하기 / 글자 고치기 (사용성 점검표 6번).
    var heard by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Replies are read aloud when 음성 안내 is on (or in a voice chat).
    val tts = rememberTts(android)
    val voiceChat = context.settings.voiceChat
    // Lines still being read aloud; the voice chat listens again once they are done.
    var speaking by remember { mutableIntStateOf(0) }
    var spoken by remember { mutableIntStateOf(0) }
    DisposableEffect(tts) {
        val main = Handler(Looper.getMainLooper())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id?.startsWith("assistant") == true) main.post { speaking = (speaking - 1).coerceAtLeast(0); spoken++ } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { onDone(id) }
        })
        onDispose { }
    }
    fun say(text: String) {
        lines += ChatLine(fromUser = false, text = text)
        if (context.settings.voiceGuide || voiceChat) {
            speaking++
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "assistant-${lines.size}")
        }
    }

    /** Asks before leaving the chat for the map; the answer is a button or said ("응", "보여 줘"). */
    fun offerGo(go: () -> Unit) {
        pendingGo = go
        say(GO_QUESTION)
        choices = listOf(GO_YES, GO_NO)
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
        offerGo { onRoute(place) }
    }

    fun send(text: String, byVoice: Boolean = false) {
        val said = text.trim()
        if (said.isEmpty() || thinking != null) return
        heard = if (byVoice) said else null
        pendingGo?.let { go ->
            when (goAnswer(said)) {
                true -> {
                    lines += ChatLine(fromUser = true, text = said)
                    pendingGo = null
                    choices = emptyList()
                    go()
                    return
                }
                false -> {
                    lines += ChatLine(fromUser = true, text = said)
                    pendingGo = null
                    choices = emptyList()
                    say("알겠어요. 더 필요한 게 있으면 말씀해 주세요.")
                    return
                }
                null -> pendingGo = null // something else was said: carry on with it
            }
        }
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
                if (answer.showOnMap) offerGo { onRoute(place) }
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
                is AssistantAction.GoPlace -> { chat.lastPlace = action.place; offerGo { when {
                    action.stops.isNotEmpty() -> onRouteWithStops(action.place, action.stops, action.transit)
                    action.transit -> onRouteByTransit(action.place)
                    else -> onRoute(action.place)
                } } }
                is AssistantAction.ShowBuses -> offerGo { onShowBuses() }
                is AssistantAction.OpenReport -> { delay(1200); onOpenReport(action) }
                is AssistantAction.Ask -> choices = action.choices
                is AssistantAction.Answer -> Unit
            }
            thinking = null
        }
    }

    // Listening happens inside the app (no Google dialog over the chat, 11/10): the microphone button shows it.
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    // How loud the voice is now (0..1), so the walker can see they are being heard.
    var level by remember { mutableFloatStateOf(0f) }
    // The voice chat goes on until nothing is heard twice in a row, or the walker types or taps.
    var chatOn by remember { mutableStateOf(voiceChat) }
    var silences by remember { mutableIntStateOf(0) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(android)) SpeechRecognizer.createSpeechRecognizer(android) else null
    }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it, byVoice = true) }
    }
    fun listenWithDialog() {
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
    fun listenInApp() {
        val r = recognizer ?: return listenWithDialog()
        tts.stop(); speaking = 0
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; partial = ""; level = 0f }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB - 1f) / 9f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onResults(b: Bundle?) {
                listening = false; partial = ""
                val said = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) onError(SpeechRecognizer.ERROR_NO_MATCH) else { silences = 0; send(said, byVoice = true) }
            }
            override fun onError(error: Int) {
                listening = false; partial = ""
                if (chatOn) {
                    silences++
                    if (silences >= 2) { chatOn = false; lines += ChatLine(false, "말씀이 없으셔서 듣기를 멈췄어요. 마이크를 누르면 다시 들어요.") }
                    else spoken++ // listen once more
                }
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Older walkers pause mid-sentence: wait longer for silence before the sentence counts as said.
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_DONE_MS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_DONE_MS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MIN_LISTEN_MS),
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listenInApp() else listenWithDialog()
    }
    fun listen() {
        if (listening) { recognizer?.stopListening(); return }
        val ok = androidx.core.content.ContextCompat.checkSelfPermission(android, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) listenInApp() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) { if (listenAtStart || voiceChat) listen() }
    // Voice chat: once the answer has been read out (and nothing else is going on), listen again.
    LaunchedEffect(spoken, thinking, speaking) {
        if (!chatOn || listening || thinking != null || speaking > 0 || urgent) return@LaunchedEffect
        if (lines.lastOrNull()?.fromUser != false) return@LaunchedEffect
        // A breath after the answer before the walker's turn (11/10: it came back too fast), then a short tone so it
        // is plain the microphone is on.
        delay(VOICE_CHAT_PAUSE_MS)
        if (!chatOn || listening || thinking != null || speaking > 0) return@LaunchedEffect
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 50).startTone(ToneGenerator.TONE_PROP_ACK, 120) }
        delay(250)
        listen()
    }

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
                    onFine = { urgent = false; say("다행이에요. 필요하면 언제든 말해 주세요.") },
                )
            }
        }
        if (choices.isNotEmpty()) {
            // Big enough for a thick finger: 48dp high, 8dp apart (사용성 점검표 14번).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                choices.forEach { c -> SuggestionChip(
                        onClick = { send(c) }, label = { Text(c, fontSize = 16.sp, color = Ink) },
                        colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp),
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
        // While listening: a big strip that moves with the voice and shows the words as they are heard (11/10).
        if (listening) ListeningStrip(level, partial, onStop = { recognizer?.stopListening() })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(300); if (chatOn) { chatOn = false; recognizer?.cancel(); listening = false } },
                placeholder = { Text(if (listening) partial.ifBlank { "듣고 있어요… 말씀해 주세요" } else "여기에 쓰거나 마이크를 눌러 말해요") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Ink),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                modifier = Modifier.weight(1f),
            )
            // The same simple rounded square as the microphone in walking guidance.
            // While listening it turns solid green, and a ring around it swells with the voice, so each word
            // shows on the microphone itself (11/10).
            val ring by androidx.compose.animation.core.animateFloatAsState(if (listening) level else 0f, label = "mic")
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 8.dp).size(64.dp)) {
            if (listening) Box(
                Modifier.size((52 + 12 * ring).dp)
                    .background(Green.copy(alpha = 0.18f + 0.3f * ring), RoundedCornerShape(18.dp)),
            )
            Surface(
                onClick = { if (!listening && voiceChat) { chatOn = true; silences = 0 }; listen() },
                shape = RoundedCornerShape(14.dp),
                color = if (listening) Green else Color.White,
                contentColor = if (listening) Color.White else Green,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (listening) Icons.Filled.Mic else Icons.Outlined.Mic, contentDescription = if (listening) "듣는 중, 누르면 멈춤" else "말로 하기", modifier = Modifier.size((26 + 6 * ring).dp))
                }
            }
            }
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && thinking == null) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기", tint = if (input.isNotBlank()) Green else Muted)
            }
        }
    }
    }
}

/** "듣고 있어요" with five bars that follow the voice's loudness, the words heard so far, and 다 말했어요. */
@Composable
private fun ListeningStrip(level: Float, heardSoFar: String, onStop: () -> Unit) {
    val shown by androidx.compose.animation.core.animateFloatAsState(level, label = "voice")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE7F3EC),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(32.dp)) {
                    listOf(0.5f, 0.8f, 1f, 0.8f, 0.5f).forEach { w ->
                        Box(
                            Modifier.width(6.dp).height((6 + 26 * shown * w).dp)
                                .background(Green, RoundedCornerShape(3.dp)),
                        )
                    }
                }
                Text(
                    if (shown > 0.4f) "잘 들려요" else "듣고 있어요… 말씀해 주세요",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onStop) { Text("다 말했어요", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            if (heardSoFar.isNotBlank()) Text(heardSoFar, fontSize = 20.sp, lineHeight = 27.sp, color = Ink)
        }
    }
}

/** Asked before the map opens (11/10), and its two answers. */
const val GO_QUESTION = "지도에서 보여 드릴까요?"
const val GO_YES = "지도에서 볼게요"
const val GO_NO = "괜찮아요"

/** The answer to [GO_QUESTION] in what was said: true to open the map, false not to, null for something else. */
internal fun goAnswer(said: String): Boolean? {
    val s = said.replace(" ", "")
    if (s == GO_YES.replace(" ", "")) return true
    if (s == GO_NO.replace(" ", "")) return false
    if (listOf("아니", "괜찮", "됐어", "싫어", "나중에", "말고").any { s.contains(it) }) return false
    if (s in setOf("어", "어어", "응응")) return true
    if (s.length <= 12 && listOf("네", "응", "예", "좋아", "그래", "보여", "볼게", "보자", "가자", "열어", "부탁", "알겠").any { s.contains(it) }) return true
    return null
}

/** The chip that takes back AI 비서's last change. */
const val UNDO_CHOICE = "되돌리기"

/** Voice chat: the pause after an answer is read before listening again. */
private const val VOICE_CHAT_PAUSE_MS = 1_500L

/** Silence this long ends what is said (the phone's default is about 1 s, too short mid-sentence). */
private const val SILENCE_DONE_MS = 2_000L

/** Listen at least this long, so a slow start is not cut off. */
private const val MIN_LISTEN_MS = 4_000L

/** How long AI 비서 may think before it says it is still looking. */
private const val SLOW_MS = 3_000L

/** How far back a live bubble's line counts as already said. */
private const val RECENT_LINES = 8

/** 급한 말에: the team server's three answers, 112 / 보호자 / 괜찮아요 (RuleBrain.emergency), big enough to hit in a hurry. */
@Composable
private fun EmergencyCard(onCall: () -> Unit, onGuardian: () -> Unit, onFine: () -> Unit) {
    val red = Color(0xFFB42318)
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCall, colors = ButtonDefaults.buttonColors(containerColor = red), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("112에 전화", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onGuardian, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("보호자에게 알리기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
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
