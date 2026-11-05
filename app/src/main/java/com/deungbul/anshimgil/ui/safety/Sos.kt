package com.deungbul.anshimgil.ui.safety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.Guardian
import com.deungbul.anshimgil.data.GuardianAlert
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.guardianMessage
import kotlinx.coroutines.delay

private val Alarm = Color(0xFFB42318)

/** Seconds to cancel an SOS before the text to guardians opens. */
private const val SOS_COUNTDOWN_S = 5

/** The red SOS button shown on the map screens. */
@Composable
fun SosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = Alarm, contentColor = Color.White, shadowElevation = 6.dp, modifier = modifier.size(60.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("SOS", fontSize = 18.sp, fontWeight = FontWeight.Black) }
    }
}

/**
 * SOS (귀가 지킴이): an alarm sounds at once; after [SOS_COUNTDOWN_S] seconds every guardian gets a text
 * with the current location. Cancel stops it before then. With the SMS permission (asked when SOS is
 * switched on in settings) the text goes out by itself; without it the message app opens to send it.
 */
@Composable
fun SosOverlay(
    guardians: List<Guardian>,
    here: () -> LatLon?,
    onClose: () -> Unit,
    onAddGuardian: (() -> Unit)?,
) {
    val context = LocalContext.current
    var left by remember { mutableIntStateOf(SOS_COUNTDOWN_S) }
    var opened by remember { mutableStateOf(false) }
    // True when the text went out by itself (permission given); false when the message app was opened instead.
    var sent by remember { mutableStateOf(false) }
    AlarmSound()

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        if (guardians.isNotEmpty()) {
            sent = sendGuardianText(context, guardians, guardianMessage(GuardianAlert.SOS, here()))
            opened = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Alarm.copy(alpha = 0.94f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("SOS", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
            val message = when {
                guardians.isEmpty() -> "등록된 보호자가 없어요.\n경보음은 계속 울려요."
                !opened -> "${left}초 뒤 보호자 ${guardians.size}명에게\n지금 위치를 문자로 보내요"
                sent -> "보호자 ${guardians.size}명에게\n지금 위치를 문자로 보냈어요"
                else -> "문자 앱을 열었어요.\n보내기를 눌러 주세요."
            }
            Text(message, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp)

            if (opened) {
                WhiteButton(if (sent) "한 번 더 보내기" else "문자 다시 열기") {
                    sent = sendGuardianText(context, guardians, guardianMessage(GuardianAlert.SOS, here()))
                }
            }
            if (guardians.isEmpty() && onAddGuardian != null) {
                WhiteButton("보호자 등록하러 가기") { onClose(); onAddGuardian() }
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("112에 전화하기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            // Before the text opens this cancels the SOS; afterwards it just stops the alarm.
            WhiteButton(if (!opened && guardians.isNotEmpty()) "취소" else "경보 끄기", onClick = onClose)
        }
    }
}

@Composable
private fun WhiteButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Alarm),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
    ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Black) }
}

/** Plays the phone's alarm sound on repeat while shown. */
@Composable
private fun AlarmSound() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val player = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        onDispose { runCatching { player?.stop(); player?.release() } }
    }
}

/** Opens the message app with [body] addressed to every guardian; the walker presses send. */
fun openGuardianText(context: Context, guardians: List<Guardian>, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + guardians.joinToString(";") { it.phone }))
        .putExtra("sms_body", body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No message app (e.g. a tablet): nothing more to do here; the alarm and 112 button remain.
    }
}

/** Whether the app may send texts by itself (asked when the SOS button is switched on). */
fun canSendSms(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

/**
 * Texts every guardian at once when allowed, splitting long messages (Korean fits 70 characters a part).
 * Returns false, after opening the message app instead, when the app may not or could not send.
 */
fun sendGuardianText(context: Context, guardians: List<Guardian>, body: String): Boolean {
    if (canSendSms(context)) {
        val sms = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        }.getOrNull()
        if (sms != null) {
            val ok = runCatching {
                val parts = sms.divideMessage(body)
                guardians.forEach { sms.sendMultipartTextMessage(it.phone, null, parts, null, null) }
            }.isSuccess
            if (ok) return true
        }
    }
    openGuardianText(context, guardians, body)
    return false
}
