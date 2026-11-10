package com.deungbul.anshimgil.ui.report

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.Origin
import com.deungbul.anshimgil.data.REPORT_MAX_MEMO
import com.deungbul.anshimgil.data.REPORT_MAX_PHOTOS
import com.deungbul.anshimgil.data.ReportRepository
import com.deungbul.anshimgil.data.ReportType
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.photoForUpload
import com.deungbul.anshimgil.location.phoneOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF5E6A62)
private val Green = Color(0xFF1F6B42)

/**
 * 불편 신고 (FR-49): what is wrong, an optional line about it, up to three photos, and where the
 * walker is now. Sent to the server; the position goes only with the report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    mode: UserMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Filled in by AI 비서 from what the walker said; they still check it and press 보내기. */
    initialType: ReportType? = null,
    initialMemo: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initialType) }
    var memo by remember { mutableStateOf(initialMemo) }
    val photos = remember { mutableStateListOf<ByteArray>() }
    var where by remember { mutableStateOf<Origin?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { where = phoneOrigin(context) }

    fun addPhoto(uri: Uri) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { photoForUpload(context, uri) }
            if (bytes != null && photos.size < REPORT_MAX_PHOTOS) photos += bytes
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(REPORT_MAX_PHOTOS)) { uris ->
        uris.take(REPORT_MAX_PHOTOS - photos.size).forEach(::addPhoto)
    }
    // The camera writes into the app's cache through a content URI; it is read back and shrunk.
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let(::addPhoto)
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "report_photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        cameraUri = uri
        camera.launch(uri)
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "지도로 돌아가기", tint = Ink) }
            Text("불편 신고", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text("걷기 불편한 곳을 알려 주세요. 모아서 길찾기와 지도를 고치는 데 써요.", fontSize = 15.sp, color = Muted)

            Text("무엇이 불편한가요?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label, fontSize = 16.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Text("사진 (${photos.size}/$REPORT_MAX_PHOTOS)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEachIndexed { i, bytes ->
                    val image = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEFF3EE))) {
                        image?.let { Image(it, contentDescription = "사진 ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Surface(
                            onClick = { photos.removeAt(i) },
                            shape = CircleShape, color = Color(0xCC17211B), contentColor = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = "사진 ${i + 1} 빼기", modifier = Modifier.size(18.dp)) } }
                    }
                }
            }
            if (photos.size < REPORT_MAX_PHOTOS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::takePhoto, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("사진 찍기", fontSize = 16.sp) }
                    OutlinedButton(
                        onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("앨범에서 고르기", fontSize = 16.sp) }
                }
            }

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it.take(REPORT_MAX_MEMO) },
                label = { Text("한 줄 설명 (선택)") },
                supportingText = { Text("${memo.length}/$REPORT_MAX_MEMO") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                when {
                    where == null -> "지금 위치를 찾는 중…"
                    where?.fromGps == true -> "지금 위치에서 신고해요"
                    else -> "지금 위치를 찾지 못했어요. 위치를 켜고 다시 열어 주세요."
                },
                fontSize = 14.sp, color = if (where?.fromGps == false) Color(0xFFB42318) else Muted,
            )
            error?.let { Text(it, fontSize = 15.sp, color = Color(0xFFB42318)) }
        }

        val canSend = type != null && where?.fromGps == true && !sending
        Button(
            onClick = {
                val t = type ?: return@Button
                val at = where?.takeIf { it.fromGps }?.point ?: return@Button
                sending = true
                error = null
                scope.launch {
                    try {
                        ReportRepository.send(t, at, null, mode, memo, photos.toList())
                        // The words of the team's consent text (consent.md 3번): one report already shows on the map.
                        Toast.makeText(context, "신고를 보냈어요. 고마워요! 신고한 곳은 지도에 '신고된 곳'으로 보여요.", Toast.LENGTH_LONG).show()
                        File(context.cacheDir, "report_photos").deleteRecursively()
                        onBack()
                    } catch (e: ApiException) {
                        error = e.message
                    }
                    sending = false
                }
            },
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
        ) {
            Text(if (sending) "보내는 중…" else if (type == null) "무엇이 불편한지 골라 주세요" else "신고 보내기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
