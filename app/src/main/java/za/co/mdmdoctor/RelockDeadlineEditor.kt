package za.co.mdmdoctor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RELOCK_EDITOR_PREFS = "relock_deadline_editor"
private const val RELOCK_DEADLINE_KEY = "observed_deadline_millis"

@Composable
fun RelockDeadlineEditorCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(RELOCK_EDITOR_PREFS, Context.MODE_PRIVATE) }
    val storedDeadline = remember { prefs.getLong(RELOCK_DEADLINE_KEY, 0L).takeIf { it > 0L } }
    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var dateText by remember {
        mutableStateOf(storedDeadline?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
        } ?: LocalDate.now().format(dateFormatter))
    }
    var timeText by remember {
        mutableStateOf(storedDeadline?.let {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
        } ?: "20:00")
    }
    var deadlineMillis by remember { mutableStateOf(storedDeadline) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(deadlineMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remaining = deadlineMillis?.minus(nowMillis)
    val countdown = when {
        remaining == null -> "No personal deadline has been saved"
        remaining <= 0L -> "The saved observation deadline has passed"
        else -> {
            val seconds = remaining / 1000L
            val days = seconds / 86400L
            val hours = (seconds % 86400L) / 3600L
            val minutes = (seconds % 3600L) / 60L
            val secs = seconds % 60L
            "${days}d ${hours}h ${minutes}m ${secs}s remaining"
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RELOCK DEADLINE EDITOR", style = MaterialTheme.typography.titleMedium)
            Text(
                "Set and edit a personal countdown deadline for tracking and reminders. This value is stored locally by MDM Doctor and is not a change to PayJoy, Knox Guard, the phone's enforced lock schedule, or a server-side payment deadline.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Countdown", style = MaterialTheme.typography.titleSmall)
            Text(countdown, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Date (YYYY-MM-DD)") },
                supportingText = { Text("Example: 2026-11-30") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = { Text("Time (24-hour HH:mm)") },
                supportingText = { Text("Example: 20:00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    try {
                        val date = LocalDate.parse(dateText.trim(), dateFormatter)
                        val time = LocalTime.parse(timeText.trim(), timeFormatter)
                        val millis = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        prefs.edit().putLong(RELOCK_DEADLINE_KEY, millis).apply()
                        deadlineMillis = millis
                        message = "Your personal countdown deadline has been saved."
                    } catch (_: Exception) {
                        message = "Enter a valid date as YYYY-MM-DD and time as HH:mm."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save / update my countdown") }
            OutlinedButton(
                onClick = {
                    prefs.edit().remove(RELOCK_DEADLINE_KEY).apply()
                    deadlineMillis = null
                    message = "Personal countdown cleared."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear personal countdown") }
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
