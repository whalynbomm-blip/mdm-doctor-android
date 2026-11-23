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

private data class ProcessorDeadlineInspection(
    val device: String,
    val soc: String,
    val hardware: String,
    val board: String,
    val bootloader: String,
    val buildFingerprint: String,
    val supportedAbis: String,
    val androidVersion: String,
    val securityPatch: String,
    val build: String,
    val generatedAt: String,
    val wallClock: String,
    val unixTimeMillis: String,
    val timezone: String,
    val automaticTime: String,
    val automaticTimezone: String,
    val bootCount: String,
    val elapsedSinceBoot: String,
    val uptimeSinceBoot: String,
    val elapsedRealtimeNanos: String,
    val maxTimeToLock: String,
    val activeAdmins: List<String>,
    val deviceOwner: String,
    val profileOwner: String,
    val secureClock: String,
    val secureEnvironment: String,
    val deadlineInterface: String,
    val actualDeadline: String
) {
    fun asReport(): String = buildString {
        appendLine("MDM DOCTOR — PROCESSOR DEADLINE INVESTIGATION")
        appendLine("Generated: $generatedAt")
        appendLine()
        appendLine("1. DEVICE AND PROCESSOR IDENTITY")
        appendLine("Device: $device")
        appendLine("Reported SoC model: $soc")
        appendLine("Android hardware property: $hardware")
        appendLine("Board property: $board")
        appendLine("Bootloader property: $bootloader")
        appendLine("Supported ABIs: $supportedAbis")
        appendLine("Build fingerprint: $buildFingerprint")
        appendLine("Android: $androidVersion")
        appendLine("Security patch: $securityPatch")
        appendLine("Build: $build")
        appendLine()
        appendLine("2. CLOCK AND UPTIME OBSERVATIONS")
        appendLine("Phone wall clock: $wallClock")
        appendLine("Unix time (milliseconds): $unixTimeMillis")
        appendLine("Timezone: $timezone")
        appendLine("Automatic date/time: $automaticTime")
        appendLine("Automatic timezone: $automaticTimezone")
        appendLine("Android boot count: $bootCount")
        appendLine("Elapsed real time since boot (includes sleep): $elapsedSinceBoot")
        appendLine("Uptime since boot (excludes deep sleep): $uptimeSinceBoot")
        appendLine("Elapsed realtime nanoseconds: $elapsedRealtimeNanos")
        appendLine()
        appendLine("3. ANDROID SECURITY AND DEVICE-POLICY VISIBILITY")
        appendLine("Android maximum time-to-lock policy: $maxTimeToLock")
        appendLine("MDM Doctor is device owner: $deviceOwner")
        appendLine("MDM Doctor is profile owner: $profileOwner")
        appendLine("Active Android device administrators:")
        if (activeAdmins.isEmpty()) appendLine("None returned by the public Android API")
        else activeAdmins.forEach { appendLine("- $it") }
        appendLine()
        appendLine("4. SECURE-CLOCK AND PROCESSOR SECURITY CHECK")
        appendLine("Secure clock: $secureClock")
        appendLine("TrustZone / secure environment: $secureEnvironment")
        appendLine("Android exposes ordinary wall-clock and monotonic uptime values. Those values are not a direct reading of a TrustZone-protected clock or a hardware-backed deadline counter.")
        appendLine()
        appendLine("5. AUTHORISED DEADLINE-MANAGEMENT INTERFACE")
        appendLine(deadlineInterface)
        appendLine("Actual phone-enforced 8 PM deadline: $actualDeadline")
        appendLine()
        appendLine("6. RESULT AND LIMITATIONS")
        appendLine("This investigation uses public Android APIs and build properties only. Build properties do not authenticate the exact silicon revision or prove secure-boot state. Android's maximum time-to-lock is an inactivity policy, not a fixed clock-time deadline.")
        appendLine("No local countdown value is represented as the phone's protected deadline. An editor can only be added if the device manufacturer or authorised management provider exposes an interface that both reports and permits changing that actual deadline.")
        appendLine("No protected processor memory, TrustZone memory, RPMB records, or firmware partitions were read or modified.")
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000L
    val days = seconds / 86400L
    val hours = (seconds % 86400L) / 3600L
    val minutes = (seconds % 3600L) / 60L
    return "${days}d ${hours}h ${minutes}m"
}

private fun inspectProcessorDeadline(context: Context): ProcessorDeadlineInspection {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        dpm.activeAdmins.orEmpty().map { it.packageName }.distinct().sorted()
    } catch (_: Exception) { emptyList() }
    val autoTime = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    } catch (_: Exception) { false }
    val autoTimezone = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1
    } catch (_: Exception) { false }
    val bootCount = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
    } catch (_: Exception) { "Not available to this app" }
    val maxLock = try {
        val millis = dpm.getMaximumTimeToLock(null)
        if (millis <= 0L) "No maximum time-to-lock policy reported"
        else "${millis} ms (${formatDuration(millis)})"
    } catch (_: Exception) { "Not readable by this app" }
    val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.ifBlank { "Not exposed by Android" } else "Not exposed by this Android API level"
    val securityPatch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH.ifBlank { "Not reported" } else "Not available"
    val now = Date()
    return ProcessorDeadlineInspection(
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        soc = soc,
        hardware = Build.HARDWARE.ifBlank { "Not reported" },
        board = Build.BOARD.ifBlank { "Not reported" },
        bootloader = Build.BOOTLOADER.ifBlank { "Not reported" },
        buildFingerprint = Build.FINGERPRINT.ifBlank { "Not reported" },
        supportedAbis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "Not reported" },
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        securityPatch = securityPatch,
        build = Build.DISPLAY.ifBlank { "Not reported" },
        generatedAt = DateFormat.getDateTimeInstance().format(now),
        wallClock = DateFormat.getDateTimeInstance().format(now),
        unixTimeMillis = System.currentTimeMillis().toString(),
        timezone = TimeZone.getDefault().id,
        automaticTime = if (autoTime) "Enabled" else "Disabled / not reported",
        automaticTimezone = if (autoTimezone) "Enabled" else "Disabled / not reported",
        bootCount = bootCount,
        elapsedSinceBoot = formatDuration(SystemClock.elapsedRealtime()),
        uptimeSinceBoot = formatDuration(SystemClock.uptimeMillis()),
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos().toString(),
        maxTimeToLock = maxLock,
        activeAdmins = admins,
        deviceOwner = try { dpm.isDeviceOwnerApp(context.packageName).toString() } catch (_: Exception) { "Not available" },
        profileOwner = try { dpm.isProfileOwnerApp(context.packageName).toString() } catch (_: Exception) { "Not available" },
        secureClock = "No public Android API available to this ordinary app for reading a TrustZone/RPMB-protected secure clock or the reported 8 PM enforcement counter.",
        secureEnvironment = "The SoC model and Android hardware properties are reported where available. Android does not expose private TrustZone memory, secure-world state, or RPMB contents to this app.",
        deadlineInterface = "No general public Android API was found by this app that exposes an OEM/firmware 8 PM deadline together with permission to change it. The readable DevicePolicyManager maximum-time-to-lock setting is an inactivity policy and is not an authorised fixed-deadline editor.",
        actualDeadline = "NOT EXPOSED through the public Android APIs available to this app"
    )
}

private fun saveProcessorReport(context: Context, report: String) {
    val prefs = context.getSharedPreferences("processor_deadline_investigation", Context.MODE_PRIVATE)
    val previous = prefs.getString("report_history", "").orEmpty()
    val entries = (listOf(report) + previous.split("\n\n--- SAVED REPORT ---\n\n").filter { it.isNotBlank() })
        .distinct().take(10)
    prefs.edit()
        .putString("latest_report", report)
        .putString("report_history", entries.joinToString("\n\n--- SAVED REPORT ---\n\n"))
        .apply()
}

@Composable
fun RelockTimerInspectorCard(context: Context) {
    var result by remember { mutableStateOf(inspectProcessorDeadline(context)) }
    var reportMessage by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PROCESSOR DEADLINE INVESTIGATION", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only investigation of processor identity, Android-visible security information, secure-clock visibility, uptime, and authorised deadline-management interfaces.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Device: ${result.device}")
            Text("Reported processor / SoC: ${result.soc}")
            Text("Hardware: ${result.hardware}")
            Text("Board: ${result.board}")
            Text("Bootloader property: ${result.bootloader}")
            Text("Android: ${result.androidVersion}")
            Text("Security patch: ${result.securityPatch}")
            Text("Phone date/time: ${result.wallClock}")
            Text("Timezone: ${result.timezone}")
            Text("Automatic date/time: ${result.automaticTime}")
            Text("Boot count: ${result.bootCount}")
            Text("Elapsed time since boot: ${result.elapsedSinceBoot}")
            Text("Uptime since boot: ${result.uptimeSinceBoot}")
            Text("Android maximum time-to-lock: ${result.maxTimeToLock}")
            Text("MDM Doctor device owner: ${result.deviceOwner}")
            Text("MDM Doctor profile owner: ${result.profileOwner}")
            Text("Active Android administrators:")
            if (result.activeAdmins.isEmpty()) Text("None returned by Android's public API")
            else result.activeAdmins.forEach { Text("• $it") }
            Text("Secure-clock access: ${result.secureClock}", style = MaterialTheme.typography.bodySmall)
            Text("Processor security visibility: ${result.secureEnvironment}", style = MaterialTheme.typography.bodySmall)
            Text("Authorised deadline interface: ${result.deadlineInterface}", style = MaterialTheme.typography.bodySmall)
            Text("Actual phone-enforced 8 PM deadline: ${result.actualDeadline}")
            Text(
                "A local countdown cannot change a protected phone deadline. An editor will only be added if an authorised interface exposes the real deadline and explicitly permits changes.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    result = inspectProcessorDeadline(context)
                    reportMessage = "Investigation refreshed."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Run / refresh processor investigation") }
            OutlinedButton(
                onClick = {
                    saveProcessorReport(context, result.asReport())
                    reportMessage = "Detailed report saved on this phone (latest report and up to 10 saved reports)."
                    Toast.makeText(context, "Processor investigation saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save investigation report on phone") }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Processor deadline investigation", result.asReport()))
                    reportMessage = "Investigation report copied. Paste it into this chat."
                    Toast.makeText(context, "Investigation report copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copy investigation report") }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MDM Doctor processor deadline investigation")
                        putExtra(Intent.EXTRA_TEXT, result.asReport())
                    }
                    context.startActivity(Intent.createChooser(send, "Share processor investigation"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share investigation report") }
            if (reportMessage.isNotBlank()) Text(reportMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
