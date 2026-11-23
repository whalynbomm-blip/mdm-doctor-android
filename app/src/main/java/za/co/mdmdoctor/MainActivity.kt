package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.UserManager
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

private fun managementChecks(context: Context): List<Check> {
    val policyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    val ownDeviceOwner = try { policyManager.isDeviceOwnerApp(context.packageName) } catch (_: Exception) { false }
    val ownProfileOwner = try { policyManager.isProfileOwnerApp(context.packageName) } catch (_: Exception) { false }
    val managementSummary = when {
        ownDeviceOwner -> "MDM Doctor itself is registered as device owner"
        ownProfileOwner -> "MDM Doctor itself is registered as profile owner"
        else -> "Android does not expose a general list of other device-owner apps to an ordinary app"
    }

    val restrictionLabels = listOf(
        "no_factory_reset" to "Factory reset restricted",
        "no_add_user" to "Adding users restricted",
        "no_install_apps" to "App installation restricted",
        "no_uninstall_apps" to "App uninstallation restricted",
        "no_config_wifi" to "Wi-Fi configuration restricted",
        "no_usb_file_transfer" to "USB file transfer restricted",
        "no_safe_boot" to "Safe boot restricted",
        "no_debugging_features" to "Developer debugging restricted",
        "no_modify_accounts" to "Account modification restricted"
    )
    val activeRestrictions = restrictionLabels.mapNotNull { (key, label) ->
        try {
            if (userManager.hasUserRestriction(key)) label else null
        } catch (_: Exception) {
            null
        }
    }
    val restrictionResult = if (activeRestrictions.isEmpty()) {
        "No checked restrictions reported for this user"
    } else {
        activeRestrictions.joinToString("; ")
    }

    return listOf(
        Check(
            "Android management signals",
            managementSummary,
            "Uses Android's public device-management status APIs where available. This is not a Knox Guard or finance-lock status check."
        ),
        Check(
            "Observable user restrictions",
            restrictionResult,
            "Checks a limited set of restrictions visible to this app for the current Android user. Other policies, other users, OEM controls, and server-side rules may not be visible."
        )
    )
}

private fun payJoyAndNetworkChecks(context: Context): List<Check> {
    val payJoyPackage = "com.payjoy"
    val payJoyCheck = try {
        val info = context.packageManager.getPackageInfo(payJoyPackage, 0)
        val label = try { context.packageManager.getApplicationLabel(info.applicationInfo!!).toString() } catch (_: Exception) { payJoyPackage }
        Check("PayJoy app package visibility", "A package matching " + payJoyPackage + " is installed and visible to Android",
            "Visible app label: " + label + ". Package presence does not reveal account status, server commands, payment status, or whether a lock is active.")
    } catch (_: Exception) {
        Check("PayJoy app package visibility", "No installed app was visible under the exact package name " + payJoyPackage,
            "This is a limited package-name check. PayJoy software may use a different package name, be integrated by an OEM, or be hidden from ordinary app visibility. This result does not prove PayJoy is absent.")
    }
    val networkCheck = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            connected -> "Other network"
            else -> "No active network"
        }
        Check("Network connectivity", when {
            validated -> transport + " connected; Android reports internet validation"
            connected -> transport + " connected; internet validation not confirmed"
            else -> "No validated internet connection detected"
        }, "This checks the current network only. It cannot confirm that PayJoy servers were reached, that a device command was received, or that the financing account is current.")
    } catch (_: Exception) {
        Check("Network connectivity", "Could not read network status", "Android did not provide a readable network state to this app.")
    }
    return listOf(payJoyCheck, networkCheck)
}

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
        Check(
            "Samsung KG Client visibility",
            if (admins.any { it == "com.samsung.android.kgclient" }) "KG Client is listed as an active device administrator" else "KG Client was not listed among active device administrators",
            "Package visibility only. This does not confirm Knox Guard enrollment, a finance agreement, an active lock, or an enforced restriction."
        ),
        Check("Knox / finance lock", "Not conclusively detectable", "Samsung Knox Guard licensing state, finance-provider account status, server commands, and enforcement state are not exposed as a definitive status to an ordinary third-party app. Contact PayJoy or the finance partner for authoritative confirmation.")
    ) + payJoyAndNetworkChecks(context) + managementChecks(context)
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

data class ComponentFinding(
    val role: String,
    val component: String,
    val packageName: String,
    val appLabel: String,
    val explanation: String
)

private fun componentFindings(context: Context): List<ComponentFinding> {
    val policyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        policyManager.activeAdmins.orEmpty().map { it.flattenToString() }
    } catch (_: Exception) { emptyList() }
    val accessibility = settingList(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    val listeners = settingList(context, "enabled_notification_listeners")
    val entries = buildList {
        admins.forEach { add("Device administrator" to it) }
        accessibility.forEach { add("Accessibility service" to it) }
        listeners.forEach { add("Notification listener" to it) }
    }.distinct()
    return entries.map { (role, component) ->
        val packageName = component.substringBefore('/')
        val appLabel = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { packageName }
        val explanation = when (packageName) {
            "com.sec.android.app.launcher" ->
                "Recognised Samsung One UI Home launcher. It is a normal Samsung system component. Its appearance as a notification listener does not indicate finance management or a device lock."
            "com.samsung.android.smartmirroring" ->
                "Recognised Samsung Smart View component. It supports Samsung screen mirroring features. Its appearance as a notification listener does not indicate finance management or a device lock."
            "za.co.driverprofit.indassistant" ->
                "Recognised IND Assistant package. This is the accessibility service belonging to the IND Assistant app; its presence does not mean it is a finance-management component."
            "com.samsung.android.kgclient" ->
                "Recognised Samsung KG Client package. Its appearance in this Android access list indicates that this component is enabled for the role shown. This alone does not prove a finance lock is active, identify the organisation managing the phone, or show that a restriction is being enforced."
            context.packageName ->
                "This is MDM Doctor itself. Its presence here reflects the role shown; it does not mean the app can remove or change another administrator."
            else ->
                "No built-in identification is available for this exact package. An unfamiliar name is not automatically malicious or a finance-management app. Check the app's publisher and purpose with the device owner or your organisation before making changes."
        }
        ComponentFinding(role, component, packageName, appLabel, explanation)
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
    appendLine("VISIBLE COMPONENT REVIEW")
    val findings = componentFindings(context)
    if (findings.isEmpty()) {
        appendLine("No active administrator, enabled accessibility service, or notification listener was returned by the settings this app can read.")
    } else {
        findings.forEach { finding ->
            appendLine("${finding.role}: ${finding.appLabel} (${finding.packageName})")
            appendLine(finding.explanation)
        }
    }
    appendLine()
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

                val componentNotes = componentFindings(activity)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Detected component review", style = MaterialTheme.typography.titleSmall)
                        Text("These entries are grouped by the Android role in which they were found. Review them with the app information page; the app does not change their permissions.", style = MaterialTheme.typography.bodySmall)
                        if (componentNotes.isEmpty()) {
                            Text("No active administrators, enabled accessibility services, or notification listeners were returned by the settings this app can read.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            componentNotes.forEach { finding ->
                                HorizontalDivider()
                                Text(finding.appLabel, style = MaterialTheme.typography.titleSmall)
                                Text(finding.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Package: " + finding.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(finding.explanation, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${finding.packageName}") },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Open app information") }
                            }
                        }
                        Text("A recognised component or an unknown component is not, by itself, proof of a threat, finance lock, or active restriction.", style = MaterialTheme.typography.bodySmall)
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
