package me.jaival.5g.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.jaival.5g.data.SettingsRepository
import me.jaival.5g.ui.about.AboutScreen
import me.jaival.5g.ui.home.HomeScreen
import me.jaival.5g.ui.onboarding.OnboardingScreen

sealed class Screen {
    object Onboarding : Screen()
    object Home : Screen()
    object About : Screen()
}

@Composable
fun AppNavigation(
    repository: SettingsRepository,
    initialOnboardingCompleted: Boolean
) {
    var currentScreen by remember {
        mutableStateOf<Screen>(if (initialOnboardingCompleted) Screen.Home else Screen.Onboarding)
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppScreenNavigation"
    ) { screen ->
        when (screen) {
            is Screen.Onboarding -> {
                OnboardingScreen(
                    repository = repository,
                    onComplete = {
                        currentScreen = Screen.Home
                    }
                )
            }
            is Screen.Home -> {
                HomeScreen(
                    repository = repository,
                    onNavigateToAbout = {
                        currentScreen = Screen.About
                    }
                )
            }
            is Screen.About -> {
                AboutScreen(
                    onBack = {
                        currentScreen = Screen.Home
                    }
                )
            }
        }
    }
}
