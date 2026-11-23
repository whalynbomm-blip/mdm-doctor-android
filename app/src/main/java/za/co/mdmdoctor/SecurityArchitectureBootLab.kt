package za.co.mdmdoctor

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

private data class BootModeLesson(
    val name: String,
    val purpose: String,
    val limitation: String
)

private val bootModeLessons = listOf(
    BootModeLesson(
        "Samsung Exynos USB Boot (EUB)",
        "A low-level Samsung service/boot communication path used on supported hardware for authorised manufacturing or repair workflows.",
        "Availability depends on the exact chipset and board design. Detecting or entering a mode does not grant permission to decrypt, erase, or alter protected data."
    ),
    BootModeLesson(
        "MediaTek BootROM (BROM)",
        "A BootROM-level USB protocol used by compatible MediaTek devices during early boot and authorised servicing.",
        "Secure boot, authentication, device configuration and signed download agents can restrict operations. A visible BROM connection is not proof that protected storage is writable."
    ),
    BootModeLesson(
        "Qualcomm Emergency Download (EDL)",
        "An emergency recovery interface used on supported Qualcomm platforms, commonly with authenticated service programmers.",
        "Modern devices commonly enforce programmer authentication and secure-boot policy. EDL presence alone does not authorise access to protected partitions."
    )
)

@Composable
fun SecurityArchitectureBootLabCard() {
    var expanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(0) }
    val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.ifBlank { "Not exposed by Android" } else "Not exposed by this Android API"
    val hardware = Build.HARDWARE.ifBlank { "Not reported" }
    val board = Build.BOARD.ifBlank { "Not reported" }
    val bootloader = Build.BOOTLOADER.ifBlank { "Not reported" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("SECURITY ARCHITECTURE & BOOT LAB", style = MaterialTheme.typography.titleMedium)
            Text(
                "Educational, read-only module. The boot-mode lessons below are simulations; this app does not force emergency modes, communicate with factory loaders, or write device storage.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Hardware identity", style = MaterialTheme.typography.titleSmall)
            Text("Manufacturer / model: ${Build.MANUFACTURER} ${Build.MODEL}")
            Text("SoC model: $soc")
            Text("Android hardware: $hardware")
            Text("Board: $board")
            Text("Bootloader string: $bootloader")
            Text("Build fingerprint: ${Build.FINGERPRINT}", style = MaterialTheme.typography.bodySmall)
            Text(
                "These values are informational Android build properties. They may be generic, omitted, or vendor-defined; they do not prove the exact silicon revision, boot state, secure-boot state, or lock status.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (expanded) "Hide security architecture lessons" else "Explore security architecture") }

            if (expanded) {
                Text("How the security layers fit together", style = MaterialTheme.typography.titleSmall)
                Text("1. Android framework and apps — ordinary apps and system services run here. App visibility or a lock-screen interface does not reveal every policy held below Android.")
                Text("2. Bootloader and verified boot — validate boot components and enforce device boot policy. A factory reset does not normally remove an organisation's server-side enrolment.")
                Text("3. TrustZone / secure world — isolates security-sensitive services and operations from the normal Android world. Third-party apps cannot inspect private secure-world memory.")
                Text("4. RPMB — an authenticated storage region supported by compatible eMMC/UFS hardware. Access is controlled by hardware-backed authentication; ordinary apps cannot clear its protected records.")
                Text("5. Knox Guard / finance provider services — device-side components can participate in enforcement, while enrolment, account status and policy decisions may also depend on provider infrastructure. Hiding a client interface does not establish that a server-side restriction has been removed.")
                Text("Important distinction: not every lock flag is necessarily stored in RPMB, and the precise PayJoy implementation on a particular handset is not publicly verifiable from this app. Treat claims about a specific partition or timer as unconfirmed unless documented by the manufacturer or provider.")

                Text("Emergency boot mode learning simulator", style = MaterialTheme.typography.titleSmall)
                bootModeLessons.forEachIndexed { index, lesson ->
                    OutlinedButton(
                        onClick = { selectedMode = index },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selectedMode == index) "Selected: ${lesson.name}" else lesson.name) }
                }
                val lesson = bootModeLessons[selectedMode]
                Text(lesson.name, style = MaterialTheme.typography.titleSmall)
                Text(lesson.purpose)
                Text(lesson.limitation, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Simulation state: lesson selected — no USB mode has been entered and no device command has been sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text("What a legitimate service workflow can establish", style = MaterialTheme.typography.titleSmall)
                Text("• Identify the model, board and reported firmware build.")
                Text("• Observe whether a supported USB interface is enumerated by an authorised service workstation.")
                Text("• Confirm the required signed service package and authorisation with the manufacturer or repair provider.")
                Text("• Preserve logs and diagnostic reports before authorised repair.")
                Text("This version intentionally does not short motherboard test points, exploit ADB/MTP, load leaked programmers, bypass authentication, disable Knox Guard, block management servers, or read/write protected partitions.")
            }
        }
    }
}
