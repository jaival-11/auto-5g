package me.jaival.5g.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.jaival.5g.data.PermissionMode
import me.jaival.5g.data.SettingsRepository
import me.jaival.5g.system.ShizukuManager

@Composable
fun OnboardingScreen(
    repository: SettingsRepository,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(PermissionMode.SHIZUKU_ONETIME) }
    var isGranted by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val adbCommand = "adb shell pm grant me.jaival.5g android.permission.WRITE_SECURE_SETTINGS"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to Auto 5G",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose your setup method to allow Auto 5G to toggle cellular network modes in the background.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Method 1: One-Time Shizuku
            SetupMethodCard(
                title = "One-Time Shizuku Grant",
                description = "Uses Shizuku once to permanently grant WRITE_SECURE_SETTINGS permission to Auto 5G.",
                isSelected = selectedMode == PermissionMode.SHIZUKU_ONETIME,
                onSelect = { selectedMode = PermissionMode.SHIZUKU_ONETIME }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Method 2: Continuous Shizuku Mode
            SetupMethodCard(
                title = "Continuous Shizuku Mode",
                description = "Executes network mode switching commands through Shizuku IPC for every toggle.",
                isSelected = selectedMode == PermissionMode.SHIZUKU_CONTINUOUS,
                onSelect = { selectedMode = PermissionMode.SHIZUKU_CONTINUOUS }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Method 3: Manual ADB Command
            SetupMethodCard(
                title = "Manual ADB Command",
                description = "Run the ADB grant command from your PC or wireless debugging terminal.",
                isSelected = selectedMode == PermissionMode.MANUAL_ADB,
                onSelect = { selectedMode = PermissionMode.MANUAL_ADB }
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = selectedMode == PermissionMode.MANUAL_ADB,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Copy ADB Command:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = adbCommand,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ADB Command", adbCommand)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Command")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action Buttons with Spring Animation
            val buttonScale by animateFloatAsState(
                targetValue = if (isGranted) 1.05f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                if (selectedMode != PermissionMode.MANUAL_ADB) {
                    Button(
                        onClick = {
                            if (ShizukuManager.isShizukuAvailable()) {
                                if (selectedMode == PermissionMode.SHIZUKU_ONETIME) {
                                    val success = ShizukuManager.grantSecureSettingsViaShizuku(context)
                                    if (success) {
                                        isGranted = true
                                        statusMessage = "Permission granted successfully via Shizuku!"
                                    } else {
                                        statusMessage = "Requesting Shizuku permission..."
                                        ShizukuManager.requestShizukuPermission { requestCode, grantResult ->
                                            if (grantResult == 0) {
                                                ShizukuManager.grantSecureSettingsViaShizuku(context)
                                                isGranted = true
                                                statusMessage = "Permission granted!"
                                            }
                                        }
                                    }
                                } else {
                                    isGranted = ShizukuManager.hasShizukuPermission()
                                    if (isGranted) {
                                        statusMessage = "Shizuku Continuous Mode ready!"
                                    } else {
                                        ShizukuManager.requestShizukuPermission { _, grantResult ->
                                            if (grantResult == 0) {
                                                isGranted = true
                                                statusMessage = "Shizuku Continuous Mode ready!"
                                            }
                                        }
                                    }
                                }
                            } else {
                                statusMessage = "Shizuku service is not running."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(buttonScale)
                    ) {
                        Text("Grant via Shizuku")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            repository.setPermissionMode(selectedMode)
                            repository.setOnboardingCompleted(true)
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .scale(buttonScale)
                ) {
                    Text(if (selectedMode == PermissionMode.MANUAL_ADB) "Continue to App" else "Proceed")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupMethodCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
