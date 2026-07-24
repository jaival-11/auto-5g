package me.jaival.5g.updater

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val isChecking: Boolean = false,
    val errorMessage: String? = null
)

interface UpdateManager {
    val updateState: StateFlow<UpdateInfo>
    suspend fun checkForUpdates(context: Context, force: Boolean = false)
}
