package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

data class Check(val name: String, val result: String, val detail: String)

private fun settingList(context: Context, key: String): List<String> = try {
    Settings.Secure.getString(context.contentResolver, key).orEmpty()
        .split(':').filter { it.isNotBlank() }.distinct()
} catch (_: Exception) {
    emptyList()
}

private fun readableComponent(context: Context, component: String): String {
    val packageName = component.substringBefore('/')
    val label = try {
        val app = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(app).toString()
    } catch (_: Exception) {
        packageName
    }
    return if (label == packageName) component else "$label ($component)"
}

private fun readableComponents(context: Context, components: List<String>): List<String> =
    components.distinct().map { readableComponent(context, it) }

private fun runChecks(context: Context): List<Check> {
    val accessibility = settingList(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    val notificationListeners = settingList(context, "enabled_notification_listeners")
    val policyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        policyManager.activeAdmins.orEmpty().map { it.packageName }
    } catch (_: Exception) { emptyList() }
    val ownManagementRole = when {
        policyManager.isDeviceOwnerApp(context.packageName) -> "MDM Doctor is registered as device owner"
        policyManager.isProfileOwnerApp(context.packageName) -> "MDM Doctor is registered as profile owner"
        else -> "MDM Doctor is not device/profile owner"
    }

    return listOf(
        Check("Device", "${Build.MANUFACTURER} ${Build.MODEL}", "Manufacturer and model reported by Android."),
        Check("Android version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Build: ${Build.DISPLAY}"),
        Check("Security patch", Build.VERSION.SECURITY_PATCH, "Patch level reported by Android; this does not confirm that every update is installed."),
        Check("Active device-admin apps", "${admins.distinct().size} reported", if (admins.isEmpty()) "Android did not return any active device-admin components to this app." else readableComponents(context, admins).joinToString("\n")),
        Check("Enabled accessibility services", "${accessibility.size} reported", if (accessibility.isEmpty()) "No enabled services were listed in Android's secure settings." else readableComponents(context, accessibility).joinToString("\n")),
        Check("Notification-listener access", "${notificationListeners.size} reported", if (notificationListeners.isEmpty()) "No enabled notification listeners were listed." else readableComponents(context, notificationListeners).joinToString("\n")),
        Check("MDM Doctor management role", ownManagementRole, "This checks only MDM Doctor's own registration status. It does not identify every other management agent or finance policy."),
        Check("Knox / finance lock", "Not conclusively detectable", "Samsung Knox, finance-provider controls, and server-side lock policies may not be fully visible to an ordinary app. Contact the provider or device seller for authoritative release status.")
    )
}

private const val HISTORY_PREFS = "diagnostic_history"
private const val HISTORY_LIMIT = 10

private fun loadHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
    val count = prefs.getInt("count", 0).coerceIn(0, HISTORY_LIMIT)
    return (0 until count).mapNotNull { prefs.getString("entry_$it", null) }
}

private fun saveHistory(context: Context, report: String): List<String> {
    val entries = (listOf(report) + loadHistory(context)).take(HISTORY_LIMIT)
    val editor = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit()
        .putInt("count", entries.size)
    entries.forEachIndexed { index, entry -> editor.putString("entry_$index", entry) }
    editor.apply()
    return entries
}

private fun clearHistory(context: Context) {
    context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

private fun assessmentText(checks: List<Check>): String {
    val adminCount = checks.firstOrNull { it.name == "Active device-admin apps" }
        ?.result?.substringBefore(' ')?.toIntOrNull() ?: 0
    val accessibilityCount = checks.firstOrNull { it.name == "Enabled accessibility services" }
        ?.result?.substringBefore(' ')?.toIntOrNull() ?: 0
    val listenerCount = checks.firstOrNull { it.name == "Notification-listener access" }
        ?.result?.substringBefore(' ')?.toIntOrNull() ?: 0
    return if (adminCount + accessibilityCount + listenerCount > 0) {
        "Some administrator or special-access services are enabled. Review each listed app in Android Settings and confirm that you recognise it. These counts are not a threat score and do not prove that an app has locked or controls the device."
    } else {
        "Android did not report active administrators or enabled special-access services in the settings this app can read. This does not rule out other management, Knox, finance-provider, or server-side controls."
    }
}

private fun reportText(context: Context, checks: List<Check>): String = buildString {
    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }
    appendLine("MDM DOCTOR - DEVICE DIAGNOSTIC REPORT")
    appendLine("App version: $appVersion")
    appendLine("Generated: ${DateFormat.getDateTimeInstance().format(Date())}")
    appendLine()
    checks.forEach {
        appendLine(it.name)
        appendLine(it.result)
        appendLine(it.detail)
        appendLine()
    }
    appendLine("Privacy: no report is uploaded automatically. Sharing happens only after the user chooses Share and selects a destination.")
    appendLine("This report is informational. It does not prove that a device is free of management or finance restrictions.")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MdmDoctorScreen(this) }
    }
}

@Composable
private fun MdmDoctorScreen(activity: MainActivity) {
    var checks by remember { mutableStateOf(runChecks(activity)) }
    var lastRunAt by remember { mutableStateOf(Date()) }
    var history by remember { mutableStateOf(loadHistory(activity)) }
    var showHistory by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    val exportReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            exportStatus = "Export cancelled."
        } else {
            try {
                val report = reportText(activity, checks)
                val output = activity.contentResolver.openOutputStream(uri)
                if (output == null) {
                    exportStatus = "Could not open the selected destination."
                } else {
                    output.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                    exportStatus = "Report exported successfully."
                    Toast.makeText(activity, "Diagnostic report saved", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                exportStatus = "Export failed: " + (error.message ?: "unknown error")
            }
        }
    }
    var packageName by remember { mutableStateOf("com.payjoy") }

    fun openSettings(action: String, data: String? = null) {
        try {
            val intent = Intent(action)
            if (data != null) intent.data = android.net.Uri.parse(data)
            activity.startActivity(intent)
        } catch (_: Exception) {
            // Some Android builds do not expose every settings page.
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("MDM DOCTOR", style = MaterialTheme.typography.headlineMedium)
                Text("Android device diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Read-only inspection. No device-management settings are changed.", style = MaterialTheme.typography.bodyMedium)

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Understanding your results", style = MaterialTheme.typography.titleSmall)
                        Text("A listed administrator, accessibility service, or notification listener is an indicator to review in Android Settings. It does not by itself prove that an app controls or has locked your device.", style = MaterialTheme.typography.bodySmall)
                        Text("An empty list is not proof that the device has no management, Knox, finance, or server-side restrictions.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Diagnostic assessment", style = MaterialTheme.typography.titleSmall)
                        Text(assessmentText(checks), style = MaterialTheme.typography.bodySmall)
                        Text("This explains visible settings only; it is not a security rating or finance-lock detector.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(onClick = {
                    checks = runChecks(activity)
                    lastRunAt = Date()
                    history = saveHistory(activity, reportText(activity, checks))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Run diagnostics again")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MDM Doctor diagnostic report", reportText(activity, checks)))
                        Toast.makeText(activity, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Copy diagnostic report") }
                OutlinedButton(
                    onClick = { exportReport.launch("mdm-doctor-report.txt") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export report as text file") }
                if (exportStatus.isNotBlank()) {
                    Text(exportStatus, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "MDM Doctor diagnostic report")
                            putExtra(Intent.EXTRA_TEXT, reportText(activity, checks))
                        }
                        activity.startActivity(Intent.createChooser(send, "Share diagnostic report"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share diagnostic report") }

                OutlinedButton(
                    onClick = { showHistory = !showHistory; history = loadHistory(activity) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (showHistory) "Hide diagnostic history" else "View diagnostic history (" + history.size + ")") }

                if (showHistory) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Saved on this device", style = MaterialTheme.typography.titleSmall)
                            Text("The latest 10 reports are kept locally on this device. They are not uploaded.", style = MaterialTheme.typography.bodySmall)
                            if (history.isEmpty()) {
                                Text("No saved reports yet. Run diagnostics to save the first report.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                history.forEachIndexed { index, entry ->
                                    Text("Report " + (index + 1), style = MaterialTheme.typography.labelLarge)
                                    Text(entry, style = MaterialTheme.typography.bodySmall)
                                    if (index < history.lastIndex) HorizontalDivider()
                                }
                                TextButton(onClick = {
                                    clearHistory(activity)
                                    history = emptyList()
                                }) { Text("Clear saved history") }
                            }
                        }
                    }
                }

                checks.forEach { item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(item.result, style = MaterialTheme.typography.titleMedium)
                            Text(item.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider()
                Text("Open Android inspection screens", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { openSettings("android.settings.DEVICE_ADMIN_SETTINGS") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Device admin settings")
                }
                OutlinedButton(onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Accessibility settings")
                }
                OutlinedButton(onClick = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Usage access settings")
                }
                OutlinedButton(onClick = { openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${activity.packageName}") }, modifier = Modifier.fillMaxWidth()) {
                    Text("MDM Doctor overlay permission page")
                }

                HorizontalDivider()
                Text("Open an app's Android information page", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        val value = packageName.trim()
                        if (value.isNotEmpty()) openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$value")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open app info") }
                Text("If the package name is unknown, find the app under Android Settings > Apps. Opening its information page does not change its permissions or management status.", style = MaterialTheme.typography.bodySmall)
                Text("Privacy: MDM Doctor does not upload diagnostic reports. You choose if and where to share them.", style = MaterialTheme.typography.bodySmall)
                Text("MDM Doctor is an information tool, not an unlock or removal tool.", style = MaterialTheme.typography.labelSmall)
                Text("Last checked: ${DateFormat.getDateTimeInstance().format(lastRunAt)}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
