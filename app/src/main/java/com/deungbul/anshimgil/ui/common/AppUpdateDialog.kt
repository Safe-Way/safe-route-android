package com.deungbul.anshimgil.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.deungbul.anshimgil.data.server.AppUpdate

/**
 * 업데이트 안내 창. Required: no [나중에], and back or a tap outside does not close it, so the old build cannot be used;
 * it stays after [업데이트] too, for when the walker comes back without installing. Optional: [업데이트] [나중에].
 */
@Composable
fun AppUpdateDialog(update: AppUpdate, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!update.required) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !update.required,
            dismissOnClickOutside = !update.required,
        ),
        title = {
            Text(
                if (update.required) "업데이트해야 쓸 수 있어요" else "새 버전이 나왔어요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(update.message, fontSize = 18.sp, lineHeight = 26.sp) },
        confirmButton = {
            Button(onClick = onUpdate, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("업데이트", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (update.required) null else {
            {
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("나중에", fontSize = 18.sp)
                }
            }
        },
    )
}
