package za.co.mdmdoctor

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MdmDoctorScreen(this) }
    }
}

data class Check(val name: String, val result: String, val detail: String)

private fun runChecks(context: Context): List<Check> {
    val accessibility = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    } catch (_: Exception) { "" }
    val accessibilityCount = accessibility.split(':').count { it.isNotBlank() }
    return listOf(
        Check("Device model", "${Build.MANUFACTURER} ${Build.MODEL}", "Reported by Android."),
        Check("Android version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Reported by Android."),
        Check("Enabled accessibility services", "${accessibilityCount} service(s)", if (accessibility.isBlank()) "Android reports no enabled accessibility services." else accessibility.replace(":", "\\n")),
        Check("Device owner / Knox status", "Not conclusively detectable", "A normal third-party app cannot reliably inspect every Samsung Knox, finance-provider, or server-side policy. Ask the device owner or finance provider for release status.")
    )
}

@Composable
private fun MdmDoctorScreen(activity: MainActivity) {
    var checks by remember { mutableStateOf(runChecks(activity)) }
    var packageName by remember { mutableStateOf("com.payjoy") }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("MDM DOCTOR", style = MaterialTheme.typography.headlineMedium)
                Text("Android device diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Read-only checks. This app does not unlock devices or change management settings.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { checks = runChecks(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Run diagnostics") }
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
                Text("Open an app's Android settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package name") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${packageName.trim()}")
                    }
                    try { activity.startActivity(intent) } catch (_: Exception) { }
                }, modifier = Modifier.fillMaxWidth()) { Text("Open app info") }
                Text("The package name is a starting suggestion. If Android cannot find it, locate the app through Settings > Apps.", style = MaterialTheme.typography.bodySmall)
                Text("Report generated: ${DateFormat.getDateTimeInstance().format(Date())}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
