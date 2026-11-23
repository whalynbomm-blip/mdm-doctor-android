package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private fun settingList(context: Context, key: String): List<String> =
    Settings.Secure.getString(context.contentResolver, key).orEmpty()
        .split(':').filter { it.isNotBlank() }

private fun runChecks(context: Context): List<Check> {
    val accessibility = settingList(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    val notificationListeners = settingList(context, Settings.Secure.ENABLED_NOTIFICATION_LISTENERS)
    val admins = try {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        manager.activeAdmins.orEmpty().map { it.packageName }
    } catch (_: Exception) { emptyList() }

    return listOf(
        Check("Device", "${Build.MANUFACTURER} ${Build.MODEL}", "Manufacturer and model reported by Android."),
        Check("Android version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Build: ${Build.DISPLAY}"),
        Check("Security patch", Build.VERSION.SECURITY_PATCH, "Patch level reported by Android; this does not confirm that every update is installed."),
        Check("Active device-admin apps", "${admins.distinct().size} reported", if (admins.isEmpty()) "Android did not return any active device-admin components to this app." else admins.distinct().joinToString("\n")),
        Check("Enabled accessibility services", "${accessibility.size} reported", if (accessibility.isEmpty()) "No enabled services were listed in Android's secure settings." else accessibility.joinToString("\n")),
        Check("Notification-listener access", "${notificationListeners.size} reported", if (notificationListeners.isEmpty()) "No enabled notification listeners were listed." else notificationListeners.joinToString("\n")),
        Check("MDM Doctor owner status", "Not a device owner", "This checks only whether MDM Doctor itself is registered as a device or profile owner. It cannot identify every other management or finance policy."),
        Check("Knox / finance lock", "Not conclusively detectable", "Samsung Knox, finance-provider controls, and server-side lock policies may not be fully visible to an ordinary app. Contact the provider or device seller for authoritative release status.")
    )
}

private fun reportText(context: Context, checks: List<Check>): String = buildString {
    appendLine("MDM DOCTOR - DEVICE DIAGNOSTIC REPORT")
    appendLine("Generated: ${DateFormat.getDateTimeInstance().format(Date())}")
    appendLine()
    checks.forEach {
        appendLine(it.name)
        appendLine(it.result)
        appendLine(it.detail)
        appendLine()
    }
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

                Button(onClick = { checks = runChecks(activity) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Run diagnostics again")
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
                OutlinedButton(onClick = { openSettings(Settings.ACTION_DEVICE_ADMIN_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
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
                Text("MDM Doctor is an information tool, not an unlock or removal tool.", style = MaterialTheme.typography.labelSmall)
                Text("Generated: ${DateFormat.getDateTimeInstance().format(Date())}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
