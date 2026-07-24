package me.jaival.auto5g

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.jaival.auto5g.data.SettingsRepository
import me.jaival.auto5g.ui.navigation.AppNavigation
import me.jaival.auto5g.ui.theme.Auto5GTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = SettingsRepository(applicationContext)

        setContent {
            Auto5GTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingCompletedState by repository.onboardingCompletedFlow.collectAsState(initial = null)

                    if (onboardingCompletedState == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AppNavigation(
                            repository = repository,
                            initialOnboardingCompleted = onboardingCompletedState == true
                        )
                    }
                }
            }
        }
    }
}
