package za.co.mdmdoctor

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import java.text.DateFormat
import java.util.Date

private data class RelockInspection(
    val activeAdmins: List<String>,
    val payJoyVisible: Boolean,
    val kgClientVisible: Boolean,
    val network: String,
    val checkedAt: String
)

private fun readRelockIndicators(context: Context): RelockInspection {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admins = try {
        dpm.activeAdmins.orEmpty().map { it.packageName }.distinct().sorted()
    } catch (_: Exception) { emptyList() }

    fun packageVisible(name: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(name, 0)
        true
    } catch (_: Exception) { false }

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    val network = when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> "Other network"
        else -> "No active internet network detected"
    }
    return RelockInspection(
        activeAdmins = admins,
        payJoyVisible = packageVisible("com.payjoy"),
        kgClientVisible = packageVisible("com.samsung.android.kgclient"),
        network = network,
        checkedAt = DateFormat.getDateTimeInstance().format(Date())
    )
}

@Composable
fun RelockTimerInspectorCard(context: Context) {
    var result by remember { mutableStateOf(readRelockIndicators(context)) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RELOCK TIMER INVESTIGATION", style = MaterialTheme.typography.titleMedium)
            Text(
                "This module checks device-side indicators that an ordinary Android app is allowed to observe. It does not change or disable any lock policy.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Text("PayJoy package com.payjoy visible: ${if (result.payJoyVisible) "Yes" else "No"}")
            Text("Samsung KG Client package visible: ${if (result.kgClientVisible) "Yes" else "No"}")
            Text("Active Android device administrators:")
            if (result.activeAdmins.isEmpty()) {
                Text("No active administrator components returned by Android's public API.")
            } else {
                result.activeAdmins.forEach { Text("• $it") }
            }
            Text("Network at inspection: ${result.network}")
            Text("Last inspection: ${result.checkedAt}")
            Text("Applied relock timestamp: NOT ACCESSIBLE")
            Text(
                "Knox Guard's appliedRelockTimestamp and PayJoy account/server state are not exposed to an ordinary third-party app. This app also cannot read RPMB, TrustZone secure memory, or Knox Guard private storage. A missing package/admin indicator does not prove that no lock policy exists.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Authorised change / removal test", style = MaterialTheme.typography.titleSmall)
            Text(
                "A timestamp change or unlock must be issued by PayJoy or the authorised Knox Guard administrator after verifying the financing account. MDM Doctor cannot write the protected timer or send an administrator unlock command.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Test procedure: (1) ask the provider to confirm the currently applied relock timestamp; (2) request an authorised extension or unlock; (3) record the provider's confirmation and time; (4) rerun this inspection and note whether the device's visible behaviour changed. The inspection is observational and cannot independently verify the server-side timestamp.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { result = readRelockIndicators(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh relock indicators") }
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DEVICE_ADMIN_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Review Android device administrators") }
        }
    }
}
