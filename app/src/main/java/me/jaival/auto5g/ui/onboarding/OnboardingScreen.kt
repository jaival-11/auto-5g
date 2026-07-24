package me.jaival.auto5g.ui.onboarding

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import me.jaival.auto5g.data.PermissionMode
import me.jaival.auto5g.data.SettingsRepository
import me.jaival.auto5g.system.ShizukuManager

@Composable
fun OnboardingScreen(
    repository: SettingsRepository,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isGranted by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
                text = "Auto 5G uses Shizuku to toggle cellular network modes seamlessly in the background using hidden Android Telephony APIs.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                Button(
                    onClick = {
                        if (ShizukuManager.isShizukuAvailable()) {
                            isGranted = ShizukuManager.hasShizukuPermission()
                            if (isGranted) {
                                statusMessage = "Shizuku permission granted!"
                            } else {
                                ShizukuManager.requestShizukuPermission { _, grantResult ->
                                    if (grantResult == 0) {
                                        isGranted = true
                                        statusMessage = "Shizuku permission granted!"
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
                    Text("Grant Shizuku Permission")
                }
                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            repository.setPermissionMode(PermissionMode.SHIZUKU_CONTINUOUS)
                            repository.setOnboardingCompleted(true)
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .scale(buttonScale)
                ) {
                    Text("Proceed")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
