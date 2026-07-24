package me.jaival.auto5g.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.jaival.auto5g.BuildConfig
import me.jaival.auto5g.data.SettingsRepository
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class UpdateManagerImpl : UpdateManager {

    private val _updateState = MutableStateFlow(UpdateInfo())
    override val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    private val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L

    override suspend fun checkForUpdates(context: Context, force: Boolean) {
        if (!BuildConfig.ENABLE_UPDATER) return

        val repository = SettingsRepository(context.applicationContext)
        val updaterEnabled = repository.updaterEnabledFlow.first()
        if (!updaterEnabled && !force) return

        val lastChecked = repository.lastCheckedTimestampFlow.first()
        val currentTime = System.currentTimeMillis()

        if (!force && (currentTime - lastChecked < FOUR_HOURS_MS)) {
            return
        }

        _updateState.value = _updateState.value.copy(isChecking = true, errorMessage = null)

        withContext(Dispatchers.IO) {
            try {
                val includePrereleases = repository.includePrereleasesFlow.first()
                val url = URL("https://api.github.com/repos/jaival-11/auto-5g/releases")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "Auto5GApp")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val releases = JSONArray(jsonStr)
                    var foundRelease: UpdateInfo? = null

                    for (i in 0 until releases.length()) {
                        val release = releases.getJSONObject(i)
                        val isPrerelease = release.optBoolean("prerelease", false)
                        val isDraft = release.optBoolean("draft", false)

                        if (isDraft) continue
                        if (isPrerelease && !includePrereleases) continue

                        val tagName = release.optString("tag_name", "").removePrefix("v")
                        val body = release.optString("body", "")
                        val htmlUrl = release.optString("html_url", "")

                        if (isVersionHigher(tagName, BuildConfig.VERSION_NAME)) {
                            foundRelease = UpdateInfo(
                                hasUpdate = true,
                                latestVersion = tagName,
                                downloadUrl = htmlUrl,
                                releaseNotes = body,
                                isChecking = false,
                                errorMessage = null
                            )
                            break
                        }
                    }

                    repository.setLastCheckedTimestamp(currentTime)

                    if (foundRelease != null) {
                        _updateState.value = foundRelease
                    } else {
                        _updateState.value = UpdateInfo(
                            hasUpdate = false,
                            latestVersion = BuildConfig.VERSION_NAME,
                            isChecking = false,
                            errorMessage = null
                        )
                    }
                } else {
                    _updateState.value = _updateState.value.copy(
                        isChecking = false,
                        errorMessage = "HTTP Error: ${connection.responseCode}"
                    )
                }
            } catch (e: Exception) {
                _updateState.value = _updateState.value.copy(
                    isChecking = false,
                    errorMessage = e.localizedMessage ?: "Failed to check for updates"
                )
            }
        }
    }

    private fun isVersionHigher(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
