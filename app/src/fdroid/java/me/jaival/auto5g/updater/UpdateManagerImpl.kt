package me.jaival.auto5g.updater

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpdateManagerImpl : UpdateManager {

    private val _updateState = MutableStateFlow(UpdateInfo(hasUpdate = false, isChecking = false))
    override val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    override suspend fun checkForUpdates(context: Context, force: Boolean) {
        // No-op for F-Droid flavor (strictly no internet permission or update checks)
    }
}
