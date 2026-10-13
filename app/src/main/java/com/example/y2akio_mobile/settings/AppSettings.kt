package com.example.y2akio_mobile.settings

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class AppLanguage { English, Vietnamese }
enum class AppTheme { System, Light, Dark }

data class SettingsData(
    val language: AppLanguage = AppLanguage.Vietnamese,
    val theme: AppTheme = AppTheme.System,
    /** SAF tree URI, or "" = Download/Y2Akio. */
    val videoSaveFolder: String = "",
    val audioSaveFolder: String = "",
    val hasCookies: Boolean = false,
    val cookiesSourceName: String = "",
    // ── Background (share sheet) download defaults ──
    val shareKind: String = "video",          // "video" / "audio"
    val shareMaxHeight: Int = 0,              // 0 = best
    val shareContainer: String = "mp4",
    val shareAudioFormat: String = "mp3",
    val shareGalleryZip: Boolean = false,
    val shareEmbedThumbnail: Boolean = true,
)

/**
 * Persists user preferences (language, theme, save folders, encrypted cookies, share defaults) in
 * SharedPreferences and publishes them as a StateFlow so every screen and the background service
 * react to changes immediately.
 */
class AppSettings(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsData> = _state.asStateFlow()
    val current: SettingsData get() = _state.value

    private fun load() = SettingsData(
        language = enumOr(prefs.getString("language", null), AppLanguage.Vietnamese),
        theme = enumOr(prefs.getString("theme", null), AppTheme.System),
        videoSaveFolder = prefs.getString("videoFolder", "") ?: "",
        audioSaveFolder = prefs.getString("audioFolder", "") ?: "",
        hasCookies = !prefs.getString("cookiesEncrypted", "").isNullOrBlank(),
        cookiesSourceName = prefs.getString("cookiesSource", "") ?: "",
        shareKind = prefs.getString("shareKind", "video") ?: "video",
        shareMaxHeight = prefs.getInt("shareMaxHeight", 0),
        shareContainer = prefs.getString("shareContainer", "mp4") ?: "mp4",
        shareAudioFormat = prefs.getString("shareAudioFormat", "mp3") ?: "mp3",
        shareGalleryZip = prefs.getBoolean("shareGalleryZip", false),
        shareEmbedThumbnail = prefs.getBoolean("shareEmbedThumbnail", true),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _state.value = load()
    }

    fun setLanguage(language: AppLanguage) = update { putString("language", language.name) }

    fun setTheme(theme: AppTheme) {
        update { putString("theme", theme.name) }
        applyNightMode()
    }

    /**
     * Tells the system the app's own light/dark choice, so the window background and the launch
     * splash screen follow it from the very first frame (not just the system setting).
     */
    fun applyNightMode() {
        val mode = when (current.theme) {
            AppTheme.System -> UiModeManager.MODE_NIGHT_AUTO
            AppTheme.Light -> UiModeManager.MODE_NIGHT_NO
            AppTheme.Dark -> UiModeManager.MODE_NIGHT_YES
        }
        runCatching { context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode) }
    }

    fun setVideoSaveFolder(uri: Uri?) = setFolder("videoFolder", uri)
    fun setAudioSaveFolder(uri: Uri?) = setFolder("audioFolder", uri)

    private fun setFolder(key: String, uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        update { putString(key, uri?.toString() ?: "") }
    }

    fun setShareKind(kind: String) = update { putString("shareKind", kind) }
    fun setShareMaxHeight(height: Int) = update { putInt("shareMaxHeight", height) }
    fun setShareContainer(container: String) = update { putString("shareContainer", container) }
    fun setShareAudioFormat(format: String) = update { putString("shareAudioFormat", format) }
    fun setShareGalleryZip(zip: Boolean) = update { putBoolean("shareGalleryZip", zip) }
    fun setShareEmbedThumbnail(embed: Boolean) = update { putBoolean("shareEmbedThumbnail", embed) }

    // Resolving a tree URI's display name is a ContentResolver query: do it once per folder, not
    // on every recomposition.
    private val labelCache = ConcurrentHashMap<String, String>()

    /** Human label for a save folder setting ("Download/Y2Akio" or the picked folder's name). */
    fun folderLabel(folder: String): String {
        if (folder.isBlank()) return DEFAULT_FOLDER_LABEL
        return labelCache.getOrPut(folder) {
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folder))?.name }.getOrNull()
                ?: Uri.parse(folder).lastPathSegment?.substringAfterLast(':')
                ?: folder
        }
    }

    // ─── Cookies ──────────────────────────────────────────────────────────────

    /** Encrypts the imported cookies.txt (Android Keystore) and persists the blob. Plaintext is never stored. */
    fun setCookies(plaintext: String, sourceName: String) {
        if (plaintext.isBlank()) return clearCookies()
        val blob = CookiesProtector.protect(plaintext)
        update {
            putString("cookiesEncrypted", blob)
            putString("cookiesSource", sourceName)
        }
    }

    fun clearCookies() = update {
        remove("cookiesEncrypted")
        remove("cookiesSource")
    }

    /** Decrypted cookies.txt content, or null when none is imported / decryption fails. */
    fun decryptCookies(): String? {
        val blob = prefs.getString("cookiesEncrypted", "") ?: ""
        return if (blob.isBlank()) null else CookiesProtector.unprotect(blob)
    }

    /**
     * Writes the decrypted cookie jar to a private file yt-dlp can read for one run. The caller
     * deletes it (see [deleteCookiesFile]) once the analyze/download finishes. Null = no cookies.
     */
    fun writeCookiesFile(tag: String): File? {
        val plain = decryptCookies() ?: return null
        val dir = File(context.noBackupFilesDir, "cookies").apply { mkdirs() }
        return File(dir, "cookies-$tag.txt").apply { writeText(plain) }
    }

    fun deleteCookiesFile(file: File?) {
        runCatching { file?.delete() }
    }

    /** Removes any plaintext cookie jars a killed process may have left behind. */
    fun cleanupStaleCookieFiles() {
        File(context.noBackupFilesDir, "cookies").listFiles()?.forEach { runCatching { it.delete() } }
    }

    companion object {
        const val DEFAULT_FOLDER_LABEL = "Download/Y2Akio"
    }
}
