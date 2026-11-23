package za.co.mdmdoctor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remaining = deadlineMillis?.minus(nowMillis)
    val countdown = when {
        remaining == null -> "No deadline saved"
        remaining <= 0L -> "The saved reference deadline has passed"
        else -> {
            val seconds = remaining / 1000L
            val days = seconds / 86400L
            val hours = (seconds % 86400L) / 3600L
            val minutes = (seconds % 3600L) / 60L
            val secs = seconds % 60L
            "${days}d ${hours}h ${minutes}m ${secs}s remaining"
        }
    }

    fun openPayJoy() {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.payjoy")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                message = "PayJoy opened. Use its official payment or extension options to request a deadline change."
            } else {
                val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:com.payjoy"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(details)
                message = "The PayJoy app launch screen was not found. Android opened its app-information page if that package is installed."
            }
        } catch (error: Exception) {
            message = "Could not open PayJoy from Android: ${error.message ?: "app unavailable"}"
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("DEVICE RELOCK DEADLINE TRACKER", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enter the deadline currently shown or confirmed by your provider. MDM Doctor saves it as a reference and counts down locally. Android does not expose a public API for editing PayJoy or Knox Guard's protected enforcement timestamp, RPMB records, or TrustZone state.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Reference countdown", style = MaterialTheme.typography.titleSmall)
            Text(countdown, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Provider deadline date (YYYY-MM-DD)") },
                supportingText = { Text("Example: 2026-11-30") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = { Text("Provider deadline time (24-hour HH:mm)") },
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
                        message = "Reference deadline saved on this phone. The PayJoy/Knox Guard enforcement deadline has not been changed."
                    } catch (_: Exception) {
                        message = "Enter a valid date as YYYY-MM-DD and time as HH:mm."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save / update reference deadline") }
            OutlinedButton(
                onClick = {
                    prefs.edit().remove(RELOCK_DEADLINE_KEY).apply()
                    deadlineMillis = null
                    message = "Saved reference deadline cleared."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear reference deadline") }

            androidx.compose.material3.HorizontalDivider()
            Text("Change the actual provider deadline", style = MaterialTheme.typography.titleSmall)
            Text(
                "An actual enforcement deadline can only be changed by PayJoy or the authorised Knox Guard administrator. This button opens the provider app when Android can find it; complete any approved extension or payment process there.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { openPayJoy() }, modifier = Modifier.fillMaxWidth()) {
                Text("Open PayJoy to request a deadline change")
            }
            Text(
                "Device-side write access: NOT AVAILABLE through the public Android APIs used by this app. Editing the reference countdown does not unlock the phone or alter its security timer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
