@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.y2akio_mobile.ui

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.y2akio_mobile.Y2AkioApp
import com.example.y2akio_mobile.download.Notifications
import com.example.y2akio_mobile.settings.AppLanguage
import com.example.y2akio_mobile.settings.AppTheme
import com.example.y2akio_mobile.settings.Strings
import com.example.y2akio_mobile.ui.theme.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: Y2AkioApp, contentPadding: PaddingValues) {
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
            val valid = text != null && text.lines().any { it.removePrefix("#HttpOnly_").split('\t').size >= 7 }
            if (!valid) {
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(tr("Settings.Title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard {
            SectionTitle(tr("Settings.Language"))
            Dropdown(
                tr("Settings.LanguageLabel"),
                listOf(AppLanguage.English.name to "English", AppLanguage.Vietnamese.name to "Tiếng Việt"),
                s.language.name, { settings.setLanguage(AppLanguage.valueOf(it)) },
            )
            Hint(tr("Settings.LanguageDesc"))
        }

        SectionCard {
            SectionTitle(tr("Settings.Appearance"))
            Dropdown(
                tr("Settings.ThemeLabel"),
                listOf(AppTheme.Light.name to tr("Settings.ThemeLight"), AppTheme.Dark.name to tr("Settings.ThemeDark"), AppTheme.System.name to tr("Settings.ThemeSystem")),
                s.theme.name, { settings.setTheme(AppTheme.valueOf(it)) },
            )
            Hint(tr("Settings.ThemeDesc"))
        }

        SectionCard {
            SectionTitle(tr("Settings.Download"))
            FolderRow(tr("Settings.VideoFolder"), settings.folderLabel(s.videoSaveFolder), tr("Home.ChooseFolder")) { videoPicker.launch(null) }
            if (s.videoSaveFolder.isNotBlank()) TextButton(onClick = { settings.setVideoSaveFolder(null) }) { Text(tr("Settings.ResetFolder")) }
            Hint(tr("Settings.VideoFolderDesc"))
            HorizontalDivider()
            FolderRow(tr("Settings.AudioFolder"), settings.folderLabel(s.audioSaveFolder), tr("Home.ChooseFolder")) { audioPicker.launch(null) }
            if (s.audioSaveFolder.isNotBlank()) TextButton(onClick = { settings.setAudioSaveFolder(null) }) { Text(tr("Settings.ResetFolder")) }
            Hint(tr("Settings.AudioFolderDesc"))
        }

        SectionCard {
            SectionTitle(tr("Settings.Share"))
            Hint(tr("Settings.ShareDesc"))
            FieldLabel(tr("Settings.ShareKind"))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = s.shareKind == "video", onClick = { settings.setShareKind("video") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(tr("Home.VideoMp4"))
                }
                SegmentedButton(selected = s.shareKind == "audio", onClick = { settings.setShareKind("audio") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(tr("Home.AudioMusic"))
                }
            }
            if (s.shareKind == "video") {
                Dropdown(
                    tr("Settings.ShareQuality"),
                    listOf("0" to tr("Home.BestQuality")) + listOf(2160, 1440, 1080, 720, 480, 360).map { "$it" to "${it}p" },
                    s.shareMaxHeight.toString(), { settings.setShareMaxHeight(it.toInt()) },
                )
                Dropdown(tr("Settings.ShareContainer"), listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"), s.shareContainer, settings::setShareContainer)
            } else {
                Dropdown(
                    tr("Settings.ShareAudioFormat"),
                    listOf("mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis (OGG)", "wav" to "WAV", "alac" to "ALAC", "wma" to "WMA"),
                    s.shareAudioFormat, settings::setShareAudioFormat,
                )
            }
            CheckRow(tr("Settings.ShareThumbnail"), s.shareEmbedThumbnail, settings::setShareEmbedThumbnail)
            Dropdown(
                tr("Settings.ShareGallery"),
                listOf("folder" to tr("Home.GallerySaveFolder"), "zip" to tr("Home.GallerySaveZip")),
                if (s.shareGalleryZip) "zip" else "folder", { settings.setShareGalleryZip(it == "zip") },
            )
        }

        SectionCard {
            SectionTitle(tr("Settings.Cookies"))
            Hint(tr("Settings.CookiesDesc"))
            if (s.hasCookies) {
                Text("${s.cookiesSourceName} · ${tr("Settings.CookiesImported")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cookiePicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream", "*/*")) }) { Text(tr("Settings.CookiesImport")) }
                if (s.hasCookies) OutlinedButton(onClick = settings::clearCookies) { Text(tr("Settings.CookiesDelete")) }
            }
        }

        SectionCard {
            SectionTitle(tr("Settings.Engine"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(tr("Settings.YtDlpVersion"))
                    Text(ytVersion, style = MaterialTheme.typography.bodyLarge)
                }
                Button(
                    enabled = !updating,
                    onClick = {
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
                    },
                ) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Settings.YtDlpUpdating"))
                    } else {
                        Text(tr("Settings.YtDlpUpdate"))
                    }
                }
            }
            Hint(tr("Settings.YtDlpUpdateDesc"))
        }

        SectionCard {
            SectionTitle(tr("Settings.Notifications"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Settings.NotificationsDesc"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                if (notificationsAllowed) Text(tr("Settings.Allowed"), color = MaterialTheme.colorScheme.primary)
                else Button(onClick = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Settings.Allow")) }
            }
        }

        SectionCard {
            SectionTitle(tr("Settings.About"))
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            Text("Y2Akio · ${tr("Settings.AppVersion")} $version", style = MaterialTheme.typography.bodyMedium)
            Text("MADE BY AKIO CKIST · yt-dlp · FFmpeg · youtubedl-android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Hint(tr("Settings.Legal"))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
