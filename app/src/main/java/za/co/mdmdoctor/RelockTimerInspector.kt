package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private data class PhoneRelockInspection(
    val device: String,
    val androidVersion: String,
    val build: String,
    val currentDateTime: String,
    val timezone: String,
    val automaticTime: String,
    val automaticTimezone: String,
    val bootCount: String,
    val elapsedSinceBoot: String,
    val maxTimeToLock: String,
    val activeAdmins: List<String>,
    val deviceOwner: String,
    val profileOwner: String,
    val actualDeadline: String
) {
    fun asReport(): String = buildString {
        appendLine("PHONE RELOCK DEADLINE INVESTIGATION")
        appendLine("Generated: $currentDateTime")
        appendLine("Device: $device")
        appendLine("Android: $androidVersion")
        appendLine("Build: $build")
        appendLine("Timezone: $timezone")
        appendLine("Automatic date/time setting: $automaticTime")
        appendLine("Automatic timezone setting: $automaticTimezone")
        appendLine("Android boot count: $bootCount")
        appendLine("Elapsed time since boot: $elapsedSinceBoot")
        appendLine("Android maximum time-to-lock policy: $maxTimeToLock")
        appendLine("MDM Doctor is device owner: $deviceOwner")
        appendLine("MDM Doctor is profile owner: $profileOwner")
        appendLine("Active device administrators:")
        if (activeAdmins.isEmpty()) appendLine("None returned by Android public API")
        else activeAdmins.forEach { appendLine("- $it") }
        appendLine("Actual phone-enforced 8 PM relock deadline: $actualDeadline")
        appendLine()
        appendLine("Interpretation: these are public Android observations. Maximum time-to-lock is an inactivity policy, not the same as a fixed 8 PM deadline. This report cannot identify a protected OEM/firmware/server deadline or authorise changing it.")
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000L
    val days = seconds / 86400L
    val hours = (seconds % 86400L) / 3600L
    val minutes = (seconds % 3600L) / 60L
    return "${days}d ${hours}h ${minutes}m"
}

private fun inspectPhoneRelock(context: Context): PhoneRelockInspection {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        dpm.activeAdmins.orEmpty().map { it.packageName }.distinct().sorted()
    } catch (_: Exception) {
        emptyList()
    }
    val autoTime = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    } catch (_: Exception) { false }
    val autoTimezone = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1
    } catch (_: Exception) { false }
    val bootCount = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
    } catch (_: Exception) { "Not available" }
    val maxLock = try {
        val millis = dpm.getMaximumTimeToLock(null)
        if (millis <= 0L) "No maximum time-to-lock policy reported"
        else "${millis} ms (${formatDuration(millis)})"
    } catch (_: Exception) { "Not readable by this app" }

    return PhoneRelockInspection(
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        build = Build.DISPLAY,
        currentDateTime = DateFormat.getDateTimeInstance().format(Date()),
        timezone = TimeZone.getDefault().id,
        automaticTime = if (autoTime) "Enabled" else "Disabled / not reported",
        automaticTimezone = if (autoTimezone) "Enabled" else "Disabled / not reported",
        bootCount = bootCount,
        elapsedSinceBoot = formatDuration(SystemClock.elapsedRealtime()),
        maxTimeToLock = maxLock,
        activeAdmins = admins,
        deviceOwner = try { dpm.isDeviceOwnerApp(context.packageName).toString() } catch (_: Exception) { "Not available" },
        profileOwner = try { dpm.isProfileOwnerApp(context.packageName).toString() } catch (_: Exception) { "Not available" },
        actualDeadline = "NOT EXPOSED through the public Android APIs available to this app"
    )
}

@Composable
fun RelockTimerInspectorCard(context: Context) {
    var result by remember { mutableStateOf(inspectPhoneRelock(context)) }
    var reportMessage by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PHONE RELOCK DEADLINE INVESTIGATION", style = MaterialTheme.typography.titleMedium)
            Text(
                "Collects phone clock, timezone, boot uptime, Android lock-policy and administrator indicators to help determine what kind of deadline is involved.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Device: ${result.device}")
            Text("Android: ${result.androidVersion}")
            Text("Build: ${result.build}")
            Text("Phone date/time: ${result.currentDateTime}")
            Text("Timezone: ${result.timezone}")
            Text("Automatic date/time: ${result.automaticTime}")
            Text("Automatic timezone: ${result.automaticTimezone}")
            Text("Boot count: ${result.bootCount}")
            Text("Elapsed time since boot: ${result.elapsedSinceBoot}")
            Text("Android maximum time-to-lock policy: ${result.maxTimeToLock}")
            Text("MDM Doctor device owner: ${result.deviceOwner}")
            Text("MDM Doctor profile owner: ${result.profileOwner}")
            Text("Active Android device administrators:")
            if (result.activeAdmins.isEmpty()) {
                Text("Android's public API did not return active administrator components.")
            } else {
                result.activeAdmins.forEach { Text("• $it") }
            }
            Text("Actual phone-enforced relock deadline: ${result.actualDeadline}")
            Text(
                "Important: Android's maximum time-to-lock value controls inactivity locking; it does not reveal or edit a fixed 8 PM deadline. Comparing wall-clock time with elapsed time since boot can help describe behaviour, but cannot prove where a protected deadline is stored.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "This investigation cannot directly change the actual deadline. It will report any supported Android policy it can read and clearly identify what remains inaccessible.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    result = inspectPhoneRelock(context)
                    reportMessage = "Investigation refreshed."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Run / refresh phone deadline investigation") }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Phone relock investigation", result.asReport()))
                    reportMessage = "Investigation report copied. Paste it into this chat."
                    Toast.makeText(context, "Investigation report copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copy investigation report") }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Phone relock deadline investigation")
                        putExtra(Intent.EXTRA_TEXT, result.asReport())
                    }
                    context.startActivity(Intent.createChooser(send, "Share phone investigation"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share investigation report") }
            if (reportMessage.isNotBlank()) {
                Text(reportMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
