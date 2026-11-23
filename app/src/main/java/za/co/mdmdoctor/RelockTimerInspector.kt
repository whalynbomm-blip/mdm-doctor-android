package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class PhoneRelockInspection(
    val activeAdmins: List<String>,
    val device: String
)

private fun inspectPhoneRelock(context: Context): PhoneRelockInspection {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        dpm.activeAdmins.orEmpty().map { it.packageName }.distinct().sorted()
    } catch (_: Exception) {
        emptyList()
    }
    return PhoneRelockInspection(
        activeAdmins = admins,
        device = "${Build.MANUFACTURER} ${Build.MODEL}"
    )
}

@Composable
fun RelockTimerInspectorCard(context: Context) {
    var result by remember { mutableStateOf(inspectPhoneRelock(context)) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PHONE RELOCK DEADLINE", style = MaterialTheme.typography.titleMedium)
            Text(
                "Investigation of the phone's own enforced relock deadline — not a countdown created or stored by MDM Doctor.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Device: ${result.device}")
            Text("Active Android device administrators:")
            if (result.activeAdmins.isEmpty()) {
                Text("Android's public API did not return active administrator components.")
            } else {
                result.activeAdmins.forEach { Text("• $it") }
            }
            Text("Phone-enforced relock deadline: NOT EXPOSED TO THIS APP")
            Text(
                "Android does not provide ordinary apps with a public interface to read or change a protected device-enforced relock timestamp. Changing the displayed date or time does not establish that the underlying deadline has changed. This inspector does not simulate a deadline or claim to edit the phone's timer.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "A genuine deadline change requires an authorised control interface from the organisation or service that manages the device. If such an interface is exposed for this phone, it can be investigated and integrated; otherwise the protected deadline cannot be edited from this app.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { result = inspectPhoneRelock(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh phone relock inspection")
            }
        }
    }
}
