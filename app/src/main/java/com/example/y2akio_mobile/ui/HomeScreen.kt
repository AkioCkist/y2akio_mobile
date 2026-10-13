@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.y2akio_mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.y2akio_mobile.R
import com.example.y2akio_mobile.download.Banner
import com.example.y2akio_mobile.download.JobState
import com.example.y2akio_mobile.download.JobStatus
import com.example.y2akio_mobile.model.DownloadHistoryItem
import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.model.MediaKind
import com.example.y2akio_mobile.service.MediaGalleryService
import com.example.y2akio_mobile.service.YtDlpRuntime
import com.example.y2akio_mobile.ui.theme.BrandGradient
import com.example.y2akio_mobile.ui.theme.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    jobs: List<JobState>,
    banner: Banner?,
    log: List<String>,
    history: List<DownloadHistoryItem>,
    runtimeState: YtDlpRuntime.State,
    onDismissBanner: () -> Unit,
    onCancelJob: (Long) -> Unit,
    onDismissJob: (Long) -> Unit,
    onClearFinishedJobs: () -> Unit,
    onClearLog: () -> Unit,
    onDeleteHistory: (DownloadHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<MediaGalleryItem?>(null) }
    var folderTarget by remember { mutableStateOf("video") }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.pickFolder(uri, folderTarget)
    }
    val pickFolder: (String) -> Unit = { target -> folderTarget = target; folderPicker.launch(null) }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Header() }
        item { UrlBar(state, vm, runtimeState) }

        banner?.let { b -> item(key = "banner-${b.id}") { BannerCard(b, onDismissBanner) } }

        if (state.card != null) {
            item(key = "card") { MediaCardView(state, vm::closeVideoCard) }
        }
        if (state.showOptions) {
            item(key = "options") { OptionsCard(state, vm, pickFolder) }
        }

        state.playlist?.let { playlist ->
            item(key = "playlist-head") {
                PlaylistHeader(playlist.title, playlist.uploader, playlist.entries.size, state.playlistSelected.size, vm)
            }
            items(playlist.entries, key = { "pl-" + it.url }) { entry ->
                PlaylistRow(entry.title, entry.durationDisplay, entry.thumbnailUrl, entry.url in state.playlistSelected,
                    onToggle = { vm.togglePlaylistEntry(entry) }, onOpen = { vm.openPlaylistEntry(entry) })
            }
        }

        state.gallery?.let { post ->
            item(key = "gallery-head") { GalleryHeader(post, state.gallerySelected.size, vm) }
            items(post.items.chunked(3), key = { row -> "g-" + row.first().index }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        GalleryTile(post, item, item.index in state.gallerySelected, Modifier.weight(1f),
                            onTap = { vm.toggleGalleryItem(item.index) }, onLongPress = { viewer = item })
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "gallery-foot") { GalleryFooter(post, state, vm, pickFolder) }
        }

        if (jobs.isNotEmpty()) {
            item(key = "jobs") { JobsCard(jobs, onCancelJob, onDismissJob, onClearFinishedJobs) }
        }
        item(key = "log") { LogCard(log, onClearLog) }
        item(key = "history-head") { HistoryHeader(history.isEmpty(), onClearHistory) }
        items(history, key = { "h-" + it.completedAt + it.fileUri }) { item -> HistoryRow(item, onDeleteHistory) }
    }

    viewer?.let { item -> GalleryViewer(state.gallery, item) { viewer = null } }
}

// ─── Header / URL bar ───────────────────────────────────────────────────────

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.main_logo), contentDescription = null, modifier = Modifier.height(96.dp).width(110.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(4.dp))
            Text("Y2", fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("AKIO", fontSize = 38.sp, fontWeight = FontWeight.Black, style = TextStyle(brush = BrandGradient))
        }
        Text(tr("Home.Subtitle"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UrlBar(state: HomeUiState, vm: HomeViewModel, runtimeState: YtDlpRuntime.State) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val analyze = { focus.clearFocus(); vm.analyze() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.url,
            onValueChange = vm::setUrl,
            placeholder = { Text(tr("Home.UrlPlaceholder"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { analyze() }),
            trailingIcon = {
                if (state.url.isNotEmpty()) {
                    IconButton(onClick = { vm.setUrl("") }) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Clear")) }
                } else {
                    IconButton(onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!text.isNullOrBlank()) vm.setUrl(text.trim())
                    }) { Icon(Icons.Filled.ContentPaste, contentDescription = tr("Home.Paste")) }
                }
            },
        )
        Button(
            onClick = analyze,
            enabled = !state.analyzing && state.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.analyzing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(tr("Home.Analyzing"))
            } else {
                Text(tr("Home.Analyze"), fontWeight = FontWeight.SemiBold)
            }
        }
        when (runtimeState) {
            YtDlpRuntime.State.Initializing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(tr("Home.Preparing"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is YtDlpRuntime.State.Failed -> Text(runtimeState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            YtDlpRuntime.State.Ready -> Unit
        }
    }
}

@Composable
private fun BannerCard(banner: Banner, onDismiss: () -> Unit) {
    val (bg, fg) = if (banner.success)
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    Card(colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(if (banner.success) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(banner.title, fontWeight = FontWeight.SemiBold)
                Text(banner.message, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Dismiss")) }
        }
    }
}

// ─── Single video card + options ────────────────────────────────────────────

@Composable
private fun MediaCardView(state: HomeUiState, onClose: () -> Unit) {
    val card = state.card ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (card.thumbnailUrl.isNotBlank()) {
                    AsyncImage(model = card.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (card.duration.isNotBlank()) {
                    Text(
                        card.duration, color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (card.uploader.isNotBlank()) Text(card.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.video == null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (state.playlist != null) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Close")) }
            }
        }
    }
}

@Composable
private fun OptionsCard(state: HomeUiState, vm: HomeViewModel, pickFolder: (String) -> Unit) {
    val o = state.options
    val video = state.video
    SectionCard {
        SectionTitle(tr("Home.DownloadOptions"))

        FieldLabel(tr("Home.FileFormat"))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = o.isVideo, onClick = { vm.updateOptions { it.copy(isVideo = true) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(tr("Home.VideoMp4"))
            }
            SegmentedButton(selected = !o.isVideo, onClick = { vm.updateOptions { it.copy(isVideo = false) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(tr("Home.AudioMusic"))
            }
        }

        val best = tr("Home.BestQuality")
        Dropdown(tr("Home.Quality"), state.qualityChoices.map { it to if (it == OptionsState.BEST) best else it }, o.quality, { q -> vm.updateOptions { it.copy(quality = q) } })

        if (o.isVideo) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dropdown(
                    tr("Home.VideoFormat"), listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"), o.container,
                    { c -> vm.updateOptions { it.copy(container = c) } }, Modifier.weight(1f), enabled = o.codec == "auto",
                )
                Dropdown(
                    tr("Home.VideoCodec"),
                    listOf(
                        "auto" to tr("Home.CodecAuto"), "h264" to tr("Home.CodecH264"), "hevc" to tr("Home.CodecHevc"),
                        "av1" to tr("Home.CodecAv1"), "vp9" to tr("Home.CodecVp9"), "mpeg4" to tr("Home.CodecMpeg4"),
                    ),
                    o.codec, { c -> vm.updateOptions { it.copy(codec = c) } }, Modifier.weight(1f),
                )
            }
            if (o.codec != "auto") Text(tr("Home.CodecHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        } else {
            Dropdown(
                tr("Home.AudioFormat"),
                listOf("mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis (OGG)", "wav" to "WAV", "alac" to "ALAC", "wma" to "WMA"),
                o.audioFormat, { f -> vm.updateOptions { it.copy(audioFormat = f) } },
            )
        }

        HorizontalDivider()
        FieldLabel(tr("Home.FileNamePrefix"))
        Column {
            CheckRow(tr("Home.PrefixDateTime"), o.prefixDateTime, { v -> vm.updateOptions { it.copy(prefixDateTime = v) } })
            CheckRow(tr("Home.PrefixChannel"), o.prefixChannel, { v -> vm.updateOptions { it.copy(prefixChannel = v) } })
            CheckRow(tr("Home.IncludeTitle"), o.includeTitle, { v -> vm.updateOptions { it.copy(includeTitle = v) } })
        }

        if (o.isVideo) {
            HorizontalDivider()
            CheckRow(tr("Home.DownloadSubtitles"), o.subtitlesEnabled, { v -> vm.updateOptions { it.copy(subtitlesEnabled = v) } })
            AnimatedVisibility(o.subtitlesEnabled) { SubtitleOptions(state, vm) }
        }

        HorizontalDivider()
        if (o.isVideo) FolderRow(tr("Home.VideoSaveLocation"), vm.folderLabel(o.videoFolder), tr("Home.ChooseFolder")) { pickFolder("video") }
        else FolderRow(tr("Home.AudioSaveLocation"), vm.folderLabel(o.audioFolder), tr("Home.ChooseFolder")) { pickFolder("audio") }

        HorizontalDivider()
        FieldLabel(tr("Home.ExtraOptions"))
        CheckRow(tr("Home.EmbedMetadata"), o.embedMetadata, { v -> vm.updateOptions { it.copy(embedMetadata = v) } })
        Dropdown(
            tr("Home.Thumbnail"),
            listOf("embed" to tr("Home.ThumbnailEmbed"), "save" to tr("Home.ThumbnailSave"), "none" to tr("Home.ThumbnailNone")),
            o.thumbnailMode, { m -> vm.updateOptions { it.copy(thumbnailMode = m) } },
        )

        if (video != null) {
            Button(
                onClick = vm::downloadNow,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Home.DownloadNow"), fontWeight = FontWeight.SemiBold)
            }
        } else if (state.playlist != null) {
            Text(tr("Home.PlaylistHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubtitleOptions(state: HomeUiState, vm: HomeViewModel) {
    val o = state.options
    val video = state.video
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel(tr("Home.SubtitleOptions"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(o.subtitleEmbed, { vm.updateOptions { it.copy(subtitleEmbed = true) } })
            Text(tr("Home.SubtitleEmbed"), style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(!o.subtitleEmbed, { vm.updateOptions { it.copy(subtitleEmbed = false) } })
            Text(tr("Home.SubtitleSeparate"), style = MaterialTheme.typography.bodyMedium)
        }

        FieldLabel(tr("Home.IncludeTracks"))
        CheckRow(tr("Home.ManualSubs"), o.subtitleManual, { v -> vm.updateOptions { it.copy(subtitleManual = v) } })
        if (o.subtitleManual) {
            val langs = buildList {
                add("all" to tr("Home.AllLanguages"))
                video?.manualSubtitleLanguages?.entries?.sortedBy { it.value }?.forEach { add(it.key to "${it.value} (${it.key})") }
                add("none" to tr("Home.NoLanguage"))
            }
            Dropdown(tr("Home.Languages"), langs, o.manualLanguage, { l -> vm.updateOptions { it.copy(manualLanguage = l) } })
        }
        CheckRow(tr("Home.AutoSubs"), o.subtitleAuto, { v -> vm.updateOptions { it.copy(subtitleAuto = v) } })
        if (o.subtitleAuto && video != null && video.autoSubtitleLanguages.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.updateOptions { it.copy(autoLanguages = video.autoSubtitleLanguages.keys) } }) { Text(tr("Home.SelectAll")) }
                TextButton(onClick = { vm.updateOptions { it.copy(autoLanguages = emptySet()) } }) { Text(tr("Home.DeselectAll")) }
            }
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                video.autoSubtitleLanguages.entries.sortedBy { it.value }.forEach { (code, name) ->
                    CheckRow("$name ($code)", code in o.autoLanguages, { v ->
                        vm.updateOptions { it.copy(autoLanguages = if (v) it.autoLanguages + code else it.autoLanguages - code) }
                    })
                }
            }
        }
        Dropdown(tr("Home.SubtitleFormat"), listOf("srt" to "SRT", "vtt" to "VTT"), o.subtitleFormat, { f -> vm.updateOptions { it.copy(subtitleFormat = f) } })
    }
}

// ─── Playlist ───────────────────────────────────────────────────────────────

@Composable
private fun PlaylistHeader(title: String, uploader: String, count: Int, selected: Int, vm: HomeViewModel) {
    SectionCard {
        Text(title.ifBlank { "Playlist" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            listOf(tr("Home.PlaylistVideos", count), uploader).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.selectAllPlaylist(true) }) { Text(tr("Home.SelectAll")) }
            OutlinedButton(onClick = { vm.selectAllPlaylist(false) }) { Text(tr("Home.DeselectAll")) }
        }
        Button(onClick = vm::downloadPlaylist, enabled = selected > 0, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (selected > 0) "${tr("Home.DownloadSelected")} ($selected)" else tr("Home.DownloadSelected"))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(title: String, duration: String, thumbnail: String, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (thumbnail.isNotBlank()) AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(duration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Gallery ────────────────────────────────────────────────────────────────

@Composable
private fun GalleryHeader(post: MediaGalleryPost, selected: Int, vm: HomeViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(post.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val photos = post.items.count { !it.isVideo }
                val videos = post.items.size - photos
                val meta = buildList {
                    add(post.source)
                    if (photos > 0) add(tr("Home.GalleryPhotoCount", photos))
                    if (videos > 0) add(tr("Home.GalleryVideoCount", videos))
                    add("@${post.author}")
                    if (post.nickname.isNotBlank() && post.nickname != post.displayTitle) add(post.nickname)
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::closeGallery) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Close")) }
        }
        Text(tr("Home.GalleryHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.selectAllGallery(true) }) { Text(tr("Home.SelectAll")) }
            OutlinedButton(onClick = { vm.selectAllGallery(false) }, enabled = selected > 0) { Text(tr("Home.DeselectAll")) }
        }
    }
}

private fun galleryRequest(context: Context, url: String?, referer: String?): ImageRequest {
    val headers = NetworkHeaders.Builder().set("User-Agent", MediaGalleryService.USER_AGENT)
    if (referer != null) headers.set("Referer", referer)
    return ImageRequest.Builder(context).data(url).httpHeaders(headers.build()).crossfade(true).build()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(post: MediaGalleryPost, item: MediaGalleryItem, selected: Boolean, modifier: Modifier, onTap: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = galleryRequest(context, item.tileUrl, post.referer),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            item.index.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        )
        if (item.isVideo) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.White, CircleShape),
            )
        }
        if (item.sizeDisplay.isNotEmpty()) {
            Text(
                item.sizeDisplay, color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GalleryFooter(post: MediaGalleryPost, state: HomeUiState, vm: HomeViewModel, pickFolder: (String) -> Unit) {
    SectionCard {
        FolderRow(tr("Home.GallerySaveLocation"), vm.folderLabel(state.galleryFolder), tr("Home.ChooseFolder")) { pickFolder("gallery") }
        Dropdown(
            tr("Home.GallerySaveAs"),
            listOf("folder" to tr("Home.GallerySaveFolder"), "zip" to tr("Home.GallerySaveZip")),
            if (state.galleryAsZip) "zip" else "folder",
            { vm.setGalleryZip(it == "zip") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val selected = state.gallerySelected.size
            FilledTonalButton(onClick = { vm.downloadGallery(selectedOnly = true) }, enabled = selected > 0, modifier = Modifier.weight(1f)) {
                Text(if (selected > 0) "${tr("Home.DownloadSelected")} ($selected)" else tr("Home.DownloadSelected"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { vm.downloadGallery(selectedOnly = false) }, modifier = Modifier.weight(1f)) {
                Text("${tr("Home.GalleryDownloadAll")} (${post.items.size})", maxLines = 1)
            }
        }
    }
}

/** Long-press viewer: zoomable photo, or a video handed to an external player. */
@Composable
private fun GalleryViewer(post: MediaGalleryPost?, item: MediaGalleryItem, onClose: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = galleryRequest(context, if (item.isVideo) item.tileUrl else item.urls.firstOrNull(), post?.referer),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX = if (scale == 1f) 0f else offsetX + pan.x
                            offsetY = if (scale == 1f) 0f else offsetY + pan.y
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
            Text(
                "${item.index} / ${post?.items?.size ?: 0}", color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = tr("Home.Close"), tint = Color.White)
            }
            if (item.isVideo && item.urls.isNotEmpty()) {
                Button(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.urls.first())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Home.OpenInBrowser"))
                }
            }
        }
    }
}

// ─── Jobs / log / history ───────────────────────────────────────────────────

@Composable
private fun JobsCard(jobs: List<JobState>, onCancel: (Long) -> Unit, onDismiss: (Long) -> Unit, onClearFinished: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Home.Downloads"), Modifier.weight(1f))
            if (jobs.any { !it.isActive }) TextButton(onClick = onClearFinished) { Text(tr("Home.Clear")) }
        }
        jobs.asReversed().forEach { job ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (job.status) {
                        JobStatus.Done -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                        JobStatus.Failed -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                        JobStatus.Canceled -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Icons.Filled.Download to MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(job.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (job.isActive) {
                        TextButton(onClick = { onCancel(job.id) }) { Text(tr("Home.Cancel")) }
                    } else {
                        IconButton(onClick = { onDismiss(job.id) }) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Dismiss"), modifier = Modifier.size(18.dp)) }
                    }
                }
                if (job.isActive) {
                    if (job.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    job.detail, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = if (job.status == JobStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LogCard(log: List<String>, onClear: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Home.DownloadLog"), Modifier.weight(1f))
            if (expanded) TextButton(onClick = onClear) { Text(tr("Home.Clear")) }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            val scroll = rememberScrollState()
            LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                    .verticalScroll(scroll)
                    .padding(10.dp),
            ) {
                Text(
                    log.takeLast(400).joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(empty: Boolean, onClear: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            SectionTitle(tr("Home.DownloadHistory"), Modifier.weight(1f))
            if (!empty) TextButton(onClick = onClear) { Text(tr("Home.ClearAll")) }
        }
        if (empty) Text(tr("Home.HistoryEmpty"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(item: DownloadHistoryItem, onDelete: (DownloadHistoryItem) -> Unit) {
    val context = LocalContext.current
    val copied = tr("Home.PathCopied")
    val noViewer = tr("Home.NoViewer")
    val shareTitle = tr("Home.Share")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (item.kind) {
                        MediaKind.Video -> Icons.Filled.Movie
                        MediaKind.Audio -> Icons.Filled.MusicNote
                        MediaKind.Image -> Icons.Filled.Image
                    },
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val time = SimpleDateFormat("HH:mm:ss d/M/yyyy", Locale.getDefault()).format(Date(item.completedAt))
                    Text(
                        "${item.kind} · ${item.qualityLabel} · ${item.folderLabel} · $time",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.fileUri), item.mimeType.ifBlank { "*/*" })
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(view)
                    } catch (_: Exception) {
                        Toast.makeText(context, noViewer, Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.PlayArrow, contentDescription = tr("Home.Play")) }
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("path", item.displayPath))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Filled.ContentCopy, contentDescription = tr("Home.CopyPath")) }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType(item.mimeType.ifBlank { "*/*" })
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.fileUri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Icon(Icons.Filled.Share, contentDescription = shareTitle) }
                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Filled.Delete, contentDescription = tr("Home.Delete")) }
            }
        }
    }
}
