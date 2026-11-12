package com.deungbul.anshimgil.ui.mode

import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
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
import com.deungbul.anshimgil.data.ChatLine
import com.deungbul.anshimgil.data.SetupAssistant
import com.deungbul.anshimgil.data.SetupPick
import com.deungbul.anshimgil.data.SetupReply
import com.deungbul.anshimgil.data.summaryLines
import kotlinx.coroutines.launch

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF5E6A62)
private val Green = Color(0xFF1F6B42)

private const val FIRST_QUESTION = "누가 어떻게 걸으시는지 편하게 말해 주세요.\n예: \"우리 엄마가 70대인데 다리가 좀 불편해요\""

/**
 * 말로 맞추기: a short chat that ends with "이렇게 맞췄어요" and the settings, which the user accepts
 * ([onApply]) or goes back to choose themselves ([onBack]). Typed or spoken (the phone's speech input).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupChatScreen(
    assistant: SetupAssistant,
    onApply: (SetupPick) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf(ChatLine(fromUser = false, text = FIRST_QUESTION)) }
    var choices by remember { mutableStateOf(emptyList<String>()) }
    var picked by remember { mutableStateOf<SetupPick?>(null) }
    // The last settings picked, kept while the user asks for more so a follow-up builds on them.
    var lastPick by remember { mutableStateOf<SetupPick?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun send(text: String) {
        val said = text.trim()
        if (said.isEmpty() || thinking) return
        lines += ChatLine(fromUser = true, text = said)
        input = ""
        choices = emptyList()
        val before = picked ?: lastPick
        picked = null
        thinking = true
        scope.launch {
            // The opening question is the app's own, so only what the user said and the replies go out.
            when (val reply = assistant.reply(lines.drop(1), before)) {
                is SetupReply.Ask -> {
                    lines += ChatLine(fromUser = false, text = reply.question)
                    choices = reply.choices
                }
                is SetupReply.Pick -> {
                    lines += ChatLine(fromUser = false, text = reply.reason)
                    picked = reply.pick
                    lastPick = reply.pick
                }
            }
            thinking = false
        }
    }

    // The phone's own speech input; whatever it hears is sent like a typed answer.
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::send)
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            lines += ChatLine(fromUser = false, text = "이 휴대폰은 음성 입력을 쓸 수 없어요. 글로 써 주세요.")
        }
    }

    LaunchedEffect(lines.size, picked) { listState.animateScrollToItem(maxOf(0, lines.size)) }

    Column(modifier.fillMaxSize().background(Color(0xFFF7F8F5)).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "직접 고르기로 돌아가기", tint = Ink) }
            Text("말로 맞추기", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            items(lines) { line -> Bubble(line) }
            if (thinking) item { Text("생각하는 중…", fontSize = 15.sp, color = Muted) }
            picked?.let { pick -> item { PickCard(pick, onApply = { onApply(pick) }, onBack = onBack) } }
        }

        if (choices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                choices.forEach { c -> SuggestionChip(onClick = { send(c) }, label = { Text(c, fontSize = 16.sp) }, modifier = Modifier.heightIn(min = 48.dp)) }
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
            IconButton(onClick = ::listen) { Icon(Icons.Filled.Mic, contentDescription = "말로 답하기", tint = Green) }
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !thinking) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기", tint = if (input.isNotBlank()) Green else Muted)
            }
        }
    }
}

@Composable
internal fun Bubble(line: ChatLine) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (line.fromUser) Green else Color.White,
            contentColor = if (line.fromUser) Color.White else Ink,
            shadowElevation = if (line.fromUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            // Press and hold to select and copy, in both the walker's lines and the replies (11/12).
            SelectionContainer {
                Text(line.text, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

/** "이렇게 맞췄어요": the picked settings with 좋아요 / 직접 고를래요. */
@Composable
private fun PickCard(pick: SetupPick, onApply: () -> Unit, onBack: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("이렇게 맞췄어요", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink)
            pick.summaryLines().forEach { Text("· $it", fontSize = 16.sp, color = Ink) }
            Text("나중에 설정에서 언제든 바꿀 수 있어요.", fontSize = 13.sp, color = Muted)
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("좋아요, 지도 보기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("직접 고를래요", fontSize = 16.sp) }
        }
    }
}
