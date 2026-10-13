package com.example.y2akio_mobile.ui

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.y2akio_mobile.Y2AkioApp
import com.example.y2akio_mobile.download.Notifications
import com.example.y2akio_mobile.settings.AppLanguage
import com.example.y2akio_mobile.settings.AppTheme
import com.example.y2akio_mobile.settings.Strings
import com.example.y2akio_mobile.ui.theme.OneUi
import com.example.y2akio_mobile.ui.theme.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: Y2AkioApp, bottomPadding: Dp) {
    val settings = app.settings
    val s by settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(settings::setVideoSaveFolder) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(settings::setAudioSaveFolder) }
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            // A Netscape cookies.txt has tab-separated 7-field lines.
            if (text == null || text.lines().none { it.removePrefix("#HttpOnly_").split('\t').size >= 7 }) {
                Toast.makeText(context, Strings.get("Settings.CookiesImportError", s.language), Toast.LENGTH_LONG).show()
            } else {
                val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "cookies.txt"
                withContext(Dispatchers.Default) { settings.setCookies(text, name) }
            }
        }
    }

    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    var ytVersion by remember { mutableStateOf("…") }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ytVersion = runCatching { app.runtime.version() }.getOrDefault("?") }

    val updateYtDlp: () -> Unit = {
        if (!updating) {
            updating = true
            scope.launch {
                val message = try {
                    val updated = app.runtime.update()
                    ytVersion = app.runtime.version()
                    if (updated) Strings.get("Settings.YtDlpUpdated", s.language).replace("{0}", ytVersion)
                    else Strings.get("Settings.YtDlpUpToDate", s.language)
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
                updating = false
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    OneUiScreen(title = tr("Settings.Title"), bottomPadding = bottomPadding) {
        item(key = "general") {
            RoundedGroup {
                ChoiceRow(
                    tr("Settings.LanguageLabel"),
                    listOf(AppLanguage.English.name to "English", AppLanguage.Vietnamese.name to "Tiếng Việt"),
                    s.language.name, { settings.setLanguage(AppLanguage.valueOf(it)) },
                )
                RowDivider()
                ChoiceRow(
                    tr("Settings.ThemeLabel"),
                    listOf(AppTheme.Light.name to tr("Settings.ThemeLight"), AppTheme.Dark.name to tr("Settings.ThemeDark"), AppTheme.System.name to tr("Settings.ThemeSystem")),
                    s.theme.name, { settings.setTheme(AppTheme.valueOf(it)) },
                )
            }
        }

        item(key = "download") {
            Column {
                SubHeader(tr("Settings.Download"))
                RoundedGroup {
                    PrefRow(
                        tr("Settings.VideoFolder"), summary = settings.folderLabel(s.videoSaveFolder), summaryAccent = true,
                        icon = Icons.Filled.Folder, showChevron = s.videoSaveFolder.isBlank(), onClick = { videoPicker.launch(null) },
                    ) {
                        if (s.videoSaveFolder.isNotBlank()) AccentTextButton(tr("Settings.ResetFolder"), { settings.setVideoSaveFolder(null) })
                    }
                    RowDivider(startInset = 72.dp)
                    PrefRow(
                        tr("Settings.AudioFolder"), summary = settings.folderLabel(s.audioSaveFolder), summaryAccent = true,
                        icon = Icons.Filled.LibraryMusic, showChevron = s.audioSaveFolder.isBlank(), onClick = { audioPicker.launch(null) },
                    ) {
                        if (s.audioSaveFolder.isNotBlank()) AccentTextButton(tr("Settings.ResetFolder"), { settings.setAudioSaveFolder(null) })
                    }
                }
            }
        }

        item(key = "share") {
            Column {
                SubHeader(tr("Settings.Share"))
                RoundedGroup(contentPadding = PaddingValues(top = 16.dp, bottom = 6.dp)) {
                    Text(
                        tr("Settings.ShareDesc"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.CardPadding).padding(bottom = 14.dp),
                    )
                    SegmentedPill(
                        listOf(tr("Home.VideoMp4"), tr("Home.AudioMusic")),
                        if (s.shareKind == "video") 0 else 1,
                        { settings.setShareKind(if (it == 0) "video" else "audio") },
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
                    )
                    if (s.shareKind == "video") {
                        ChoiceRow(
                            tr("Settings.ShareQuality"),
                            listOf("0" to tr("Home.BestQuality")) + listOf(2160, 1440, 1080, 720, 480, 360).map { "$it" to "${it}p" },
                            s.shareMaxHeight.toString(), { settings.setShareMaxHeight(it.toInt()) },
                        )
                        RowDivider()
                        ChoiceRow(tr("Settings.ShareContainer"), listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"), s.shareContainer, settings::setShareContainer)
                    } else {
                        ChoiceRow(
                            tr("Settings.ShareAudioFormat"),
                            listOf("mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis (OGG)", "wav" to "WAV", "alac" to "ALAC", "wma" to "WMA"),
                            s.shareAudioFormat, settings::setShareAudioFormat,
                        )
                    }
                    RowDivider()
                    SwitchRow(tr("Settings.ShareThumbnail"), s.shareEmbedThumbnail, settings::setShareEmbedThumbnail)
                    RowDivider()
                    ChoiceRow(
                        tr("Settings.ShareGallery"),
                        listOf("folder" to tr("Home.GallerySaveFolder"), "zip" to tr("Home.GallerySaveZip")),
                        if (s.shareGalleryZip) "zip" else "folder", { settings.setShareGalleryZip(it == "zip") },
                    )
                }
            }
        }

        item(key = "account") {
            Column {
                SubHeader(tr("Settings.Cookies"))
                RoundedGroup {
                    PrefRow(
                        if (s.hasCookies) s.cookiesSourceName.ifBlank { "cookies.txt" } else tr("Settings.CookiesImport"),
                        summary = if (s.hasCookies) tr("Settings.CookiesImported") else tr("Settings.CookiesDesc"),
                        summaryAccent = s.hasCookies,
                        icon = Icons.Filled.Cookie,
                        showChevron = !s.hasCookies,
                        onClick = { cookiePicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream", "*/*")) },
                    ) {
                        if (s.hasCookies) AccentTextButton(tr("Settings.CookiesDelete"), settings::clearCookies)
                    }
                }
            }
        }

        item(key = "system") {
            Column {
                SubHeader(tr("Settings.Engine"))
                RoundedGroup {
                    PrefRow(
                        tr("Settings.YtDlpVersion"),
                        summary = if (updating) tr("Settings.YtDlpUpdating") else ytVersion,
                        summaryAccent = true,
                        icon = Icons.Filled.SystemUpdate,
                        onClick = updateYtDlp,
                    ) {
                        if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else AccentTextButton(tr("Settings.YtDlpUpdate"), updateYtDlp)
                    }
                    Text(
                        tr("Settings.YtDlpUpdateDesc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 72.dp, end = OneUi.CardPadding, bottom = 12.dp),
                    )
                    RowDivider(startInset = 72.dp)
                    PrefRow(
                        tr("Settings.Notifications"),
                        summary = if (notificationsAllowed) tr("Settings.Allowed") else tr("Settings.NotificationsDesc"),
                        summaryAccent = notificationsAllowed,
                        icon = Icons.Filled.Notifications,
                        onClick = if (notificationsAllowed) null else ({ notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }),
                    ) {
                        if (!notificationsAllowed) AccentTextButton(tr("Settings.Allow"), { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    }
                }
            }
        }

        item(key = "about") {
            Column {
                SubHeader(tr("Settings.About"))
                RoundedGroup {
                    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                    PrefRow("Y2Akio", summary = "${tr("Settings.AppVersion")} $version · MADE BY AKIO CKIST", icon = Icons.Filled.Info)
                    RowDivider(startInset = 72.dp)
                    Column(Modifier.padding(start = 72.dp, end = OneUi.CardPadding, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("yt-dlp · FFmpeg · youtubedl-android", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Settings.Legal"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
