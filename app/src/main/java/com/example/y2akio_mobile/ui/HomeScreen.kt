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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
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
import com.example.y2akio_mobile.Y2AkioApp
import com.example.y2akio_mobile.download.DownloadManager
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
import com.example.y2akio_mobile.ui.theme.OneUi
import com.example.y2akio_mobile.ui.theme.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: HomeViewModel, app: Y2AkioApp, bottomPadding: Dp) {
    // Each flow is collected here (not at the Activity root) so a progress tick only recomposes
    // the Home list, and the log is collected by the log card itself — only while it is open.
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val jobs by app.downloads.jobs.collectAsStateWithLifecycle()
    val banner by app.downloads.banner.collectAsStateWithLifecycle()
    val history by app.history.items.collectAsStateWithLifecycle()
    val runtimeState by app.runtime.state.collectAsStateWithLifecycle()
    val downloads = app.downloads
    val onDismissBanner = remember(downloads) { { downloads.showBanner(null) } }
    val onCancelJob: (Long) -> Unit = remember(downloads) { { id -> downloads.cancel(id) } }
    val onDismissJob: (Long) -> Unit = remember(downloads) { { id -> downloads.dismiss(id) } }
    val onClearFinishedJobs = remember(downloads) { { downloads.clearFinished() } }
    val onDeleteHistory: (DownloadHistoryItem) -> Unit = remember(app) { { item -> app.history.remove(item) } }
    val onClearHistory = remember(app) { { app.history.clear() } }
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

    OneUiScreen(
        title = "Y2Akio",
        bottomPadding = bottomPadding,
        expandedHeader = { HomeHeader() },
    ) {
        item(key = "url") { UrlCard(state, vm, runtimeState) }

        banner?.let { b -> item(key = "banner-${b.id}") { BannerCard(b, onDismissBanner) } }

        if (state.card != null) item(key = "card") { MediaCardView(state, vm::closeVideoCard) }
        if (state.showOptions) optionsSection(state, vm, pickFolder)

        state.playlist?.let { playlist ->
            item(key = "playlist-sub") { SubHeader(playlist.title.ifBlank { "Playlist" }) }
            item(key = "playlist-head") {
                PlaylistHeader(playlist.uploader, playlist.entries.size, state.playlistSelected.size, vm)
            }
            item(key = "playlist-gap") { Spacer(Modifier.height(OneUi.CardGap)) }
            itemsIndexed(playlist.entries, key = { _, e -> "pl-" + e.url }) { i, entry ->
                Column(Modifier.groupItem(i, playlist.entries.size)) {
                    PlaylistRow(entry.title, entry.durationDisplay, entry.thumbnailUrl, entry.url in state.playlistSelected,
                        onToggle = { vm.togglePlaylistEntry(entry) }, onOpen = { vm.openPlaylistEntry(entry) })
                    if (i < playlist.entries.size - 1) RowDivider(startInset = 140.dp)
                }
            }
        }

        state.gallery?.let { post ->
            item(key = "gallery-sub") { SubHeader(post.source) }
            item(key = "gallery-head") { GalleryHeader(post, state.gallerySelected.size, vm) }
            item(key = "gallery-gap") { Spacer(Modifier.height(OneUi.CardGap)) }
            val rows = post.items.chunked(3)
            itemsIndexed(rows, key = { _, row -> "g-" + row.first().index }) { i, row ->
                Row(
                    Modifier
                        .groupItem(i, rows.size)
                        .padding(start = 10.dp, end = 10.dp, top = if (i == 0) 10.dp else 3.dp, bottom = if (i == rows.size - 1) 10.dp else 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
            item(key = "jobs-sub") {
                SectionHeaderRow(tr("Home.Downloads")) {
                    if (jobs.any { !it.isActive }) AccentTextButton(tr("Home.Clear"), onClearFinishedJobs)
                }
            }
            val ordered = jobs.asReversed()
            itemsIndexed(ordered, key = { _, j -> "job-${j.id}" }) { i, job ->
                Column(Modifier.groupItem(i, ordered.size)) {
                    JobRow(job, onCancelJob, onDismissJob)
                    if (i < ordered.size - 1) RowDivider(startInset = 72.dp)
                }
            }
        }

        item(key = "log") { LogCard(downloads) }

        item(key = "history-sub") {
            SectionHeaderRow(tr("Home.DownloadHistory")) {
                if (history.isNotEmpty()) AccentTextButton(tr("Home.ClearAll"), onClearHistory)
            }
        }
        if (history.isEmpty()) {
            item(key = "history-empty") {
                RoundedGroup(contentPadding = PaddingValues(vertical = 28.dp, horizontal = OneUi.CardPadding)) {
                    Text(
                        tr("Home.HistoryEmpty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        itemsIndexed(history, key = { _, h -> "h-" + h.completedAt + h.fileUri }) { i, item ->
            Column(Modifier.groupItem(i, history.size)) {
                HistoryRow(item, onDeleteHistory)
                if (i < history.size - 1) RowDivider(startInset = 72.dp)
            }
        }
    }

    viewer?.let { item -> GalleryViewer(state.gallery, item) { viewer = null } }
}

/** Sub-header with a trailing accent action ("Clear All"). */
@Composable
private fun SectionHeaderRow(title: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = OneUi.CardPadding, end = 6.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = OneUi.SubHeaderStyle, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) { action() }
    }
}

// ─── Header / URL card ──────────────────────────────────────────────────────

@Composable
private fun HomeHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.main_logo), contentDescription = null,
            modifier = Modifier.height(92.dp).width(104.dp), contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(6.dp))
        Text("Y2", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text("AKIO", fontSize = 40.sp, fontWeight = FontWeight.Black, style = TextStyle(brush = BrandGradient))
    }
    Spacer(Modifier.height(6.dp))
    Text(tr("Home.Subtitle"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(2.dp))
    Text(
        tr("App.TitleBar"), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun UrlCard(state: HomeUiState, vm: HomeViewModel, runtimeState: YtDlpRuntime.State) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val analyze = { focus.clearFocus(); vm.analyze() }
    RoundedGroup(contentPadding = PaddingValues(16.dp)) {
        TextField(
            value = state.url,
            onValueChange = vm::setUrl,
            placeholder = { Text(tr("Home.UrlPlaceholder"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (state.url.isNotEmpty()) {
                    IconButton(onClick = { vm.setUrl("") }) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Clear")) }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { analyze() }),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                tr("Home.Paste"),
                onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) vm.setUrl(text.trim())
                },
                icon = Icons.Filled.ContentPaste,
                tonal = true,
            )
            PillButton(
                if (state.analyzing) tr("Home.Analyzing") else tr("Home.Analyze"),
                onClick = analyze,
                enabled = !state.analyzing && state.url.isNotBlank(),
                modifier = Modifier.weight(1f),
                leading = if (state.analyzing) {
                    { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else null,
            )
        }
        when (runtimeState) {
            YtDlpRuntime.State.Initializing -> Row(Modifier.padding(top = 12.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                Text(tr("Home.Preparing"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is YtDlpRuntime.State.Failed -> Text(
                runtimeState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp, start = 6.dp),
            )
            YtDlpRuntime.State.Ready -> Unit
        }
    }
}

@Composable
private fun BannerCard(banner: Banner, onDismiss: () -> Unit) {
    val accent = if (banner.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(Modifier.padding(top = OneUi.CardGap)) {
        RoundedGroup(contentPadding = PaddingValues(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (banner.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = accent, modifier = Modifier.padding(top = 1.dp).size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(banner.title, style = MaterialTheme.typography.titleSmall, color = accent)
                    Text(banner.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Home.Dismiss"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── Media card + options ───────────────────────────────────────────────────

@Composable
private fun MediaCardView(state: HomeUiState, onClose: () -> Unit) {
    val card = state.card ?: return
    Column(Modifier.padding(top = OneUi.CardGap)) {
        RoundedGroup(contentPadding = PaddingValues(12.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (card.thumbnailUrl.isNotBlank()) {
                    AsyncImage(model = card.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (card.duration.isNotBlank()) {
                    Text(
                        card.duration, color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                if (state.playlist != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Close"), tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (card.uploader.isNotBlank()) Text(card.uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.video == null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), strokeCap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionsSection(state: HomeUiState, vm: HomeViewModel, pickFolder: (String) -> Unit) {
    val o = state.options
    item(key = "opt-format") {
        Column {
            SubHeader(tr("Home.DownloadOptions"))
            RoundedGroup(contentPadding = PaddingValues(top = 16.dp, bottom = 6.dp)) {
                SegmentedPill(
                    listOf(tr("Home.VideoMp4"), tr("Home.AudioMusic")),
                    if (o.isVideo) 0 else 1,
                    { i -> vm.updateOptions { it.copy(isVideo = i == 0) } },
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
                )
                val best = tr("Home.BestQuality")
                ChoiceRow(tr("Home.Quality"), state.qualityChoices.map { it to if (it == OptionsState.BEST) best else it }, o.quality, { q -> vm.updateOptions { it.copy(quality = q) } })
                RowDivider()
                if (o.isVideo) {
                    ChoiceRow(
                        tr("Home.VideoCodec"),
                        listOf(
                            "auto" to tr("Home.CodecAuto"), "h264" to tr("Home.CodecH264"), "hevc" to tr("Home.CodecHevc"),
                            "av1" to tr("Home.CodecAv1"), "vp9" to tr("Home.CodecVp9"), "mpeg4" to tr("Home.CodecMpeg4"),
                        ),
                        o.codec, { c -> vm.updateOptions { it.copy(codec = c) } },
                        hint = if (o.codec != "auto") tr("Home.CodecHint") else null,
                    )
                    RowDivider()
                    ChoiceRow(
                        tr("Home.VideoFormat"), listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"),
                        if (o.codec == "auto") o.container else "mp4", { c -> vm.updateOptions { it.copy(container = c) } }, enabled = o.codec == "auto",
                    )
                } else {
                    ChoiceRow(
                        tr("Home.AudioFormat"),
                        listOf("mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis (OGG)", "wav" to "WAV", "alac" to "ALAC", "wma" to "WMA"),
                        o.audioFormat, { f -> vm.updateOptions { it.copy(audioFormat = f) } },
                    )
                }
            }
        }
    }

    item(key = "opt-name") {
        Column {
            SubHeader(tr("Home.FileNamePrefix"))
            RoundedGroup {
                SwitchRow(tr("Home.PrefixDateTime"), o.prefixDateTime, { v -> vm.updateOptions { it.copy(prefixDateTime = v) } })
                RowDivider()
                SwitchRow(tr("Home.PrefixChannel"), o.prefixChannel, { v -> vm.updateOptions { it.copy(prefixChannel = v) } })
                RowDivider()
                SwitchRow(tr("Home.IncludeTitle"), o.includeTitle, { v -> vm.updateOptions { it.copy(includeTitle = v) } })
            }
        }
    }

    if (o.isVideo) {
        item(key = "opt-subs") {
            Column {
                SubHeader(tr("Home.SubtitleOptions"))
                RoundedGroup { SubtitleOptions(state, vm) }
            }
        }
    }

    item(key = "opt-save") {
        Column {
            SubHeader(tr("Home.ExtraOptions"))
            RoundedGroup {
                val folder = if (o.isVideo) o.videoFolder else o.audioFolder
                PrefRow(
                    if (o.isVideo) tr("Home.VideoSaveLocation") else tr("Home.AudioSaveLocation"),
                    summary = vm.folderLabel(folder),
                    summaryAccent = true,
                    icon = Icons.Filled.Folder,
                    showChevron = true,
                    onClick = { pickFolder(if (o.isVideo) "video" else "audio") },
                )
                RowDivider(startInset = 72.dp)
                SwitchRow(tr("Home.EmbedMetadata"), o.embedMetadata, { v -> vm.updateOptions { it.copy(embedMetadata = v) } })
                RowDivider()
                ChoiceRow(
                    tr("Home.Thumbnail"),
                    listOf("embed" to tr("Home.ThumbnailEmbed"), "save" to tr("Home.ThumbnailSave"), "none" to tr("Home.ThumbnailNone")),
                    o.thumbnailMode, { m -> vm.updateOptions { it.copy(thumbnailMode = m) } },
                )
            }
        }
    }

    item(key = "opt-action") {
        Box(Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp)) {
            if (state.video != null) {
                PillButton(tr("Home.DownloadNow"), onClick = vm::downloadNow, icon = Icons.Filled.Download, modifier = Modifier.fillMaxWidth().height(54.dp))
            } else if (state.playlist != null) {
                Text(
                    tr("Home.PlaylistHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SubtitleOptions(state: HomeUiState, vm: HomeViewModel) {
    val o = state.options
    val video = state.video
    var languagesDialog by remember { mutableStateOf(false) }

    SwitchRow(tr("Home.DownloadSubtitles"), o.subtitlesEnabled, { v -> vm.updateOptions { it.copy(subtitlesEnabled = v) } })
    AnimatedVisibility(o.subtitlesEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            RowDivider()
            RadioRow(tr("Home.SubtitleEmbed"), o.subtitleEmbed) { vm.updateOptions { it.copy(subtitleEmbed = true) } }
            RadioRow(tr("Home.SubtitleSeparate"), !o.subtitleEmbed) { vm.updateOptions { it.copy(subtitleEmbed = false) } }
            RowDivider()
            SwitchRow(tr("Home.ManualSubs"), o.subtitleManual, { v -> vm.updateOptions { it.copy(subtitleManual = v) } })
            if (o.subtitleManual) {
                val langs = buildList {
                    add("all" to tr("Home.AllLanguages"))
                    video?.manualSubtitleLanguages?.entries?.sortedBy { it.value }?.forEach { add(it.key to "${it.value} (${it.key})") }
                    add("none" to tr("Home.NoLanguage"))
                }
                ChoiceRow(tr("Home.Languages"), langs, o.manualLanguage, { l -> vm.updateOptions { it.copy(manualLanguage = l) } })
            }
            RowDivider()
            SwitchRow(tr("Home.AutoSubs"), o.subtitleAuto, { v -> vm.updateOptions { it.copy(subtitleAuto = v) } })
            if (o.subtitleAuto && video != null && video.autoSubtitleLanguages.isNotEmpty()) {
                val count = o.autoLanguages.size
                PrefRow(
                    tr("Home.Languages"),
                    summary = "$count / ${video.autoSubtitleLanguages.size}",
                    summaryAccent = true,
                    showChevron = true,
                    onClick = { languagesDialog = true },
                )
            }
            RowDivider()
            ChoiceRow(tr("Home.SubtitleFormat"), listOf("srt" to "SRT", "vtt" to "VTT"), o.subtitleFormat, { f -> vm.updateOptions { it.copy(subtitleFormat = f) } })
        }
    }

    if (languagesDialog && video != null) {
        AlertDialog(
            onDismissRequest = { languagesDialog = false },
            shape = RoundedCornerShape(OneUi.CardRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text(tr("Home.AutoSubs"), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Row {
                        AccentTextButton(tr("Home.SelectAll"), { vm.updateOptions { it.copy(autoLanguages = video.autoSubtitleLanguages.keys) } })
                        AccentTextButton(tr("Home.DeselectAll"), { vm.updateOptions { it.copy(autoLanguages = emptySet()) } })
                    }
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        video.autoSubtitleLanguages.entries.sortedBy { it.value }.forEach { (code, name) ->
                            val checked = code in o.autoLanguages
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.updateOptions { it.copy(autoLanguages = if (checked) it.autoLanguages - code else it.autoLanguages + code) } }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(8.dp))
                                Text("$name ($code)", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { AccentTextButton(tr("Settings.Ok"), { languagesDialog = false }) },
        )
    }
}

// ─── Playlist ───────────────────────────────────────────────────────────────

@Composable
private fun PlaylistHeader(uploader: String, count: Int, selected: Int, vm: HomeViewModel) {
    RoundedGroup(contentPadding = PaddingValues(start = OneUi.CardPadding, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Text(
            listOf(tr("Home.PlaylistVideos", count), uploader).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 4.dp)) {
            AccentTextButton(tr("Home.SelectAll"), { vm.selectAllPlaylist(true) })
            AccentTextButton(tr("Home.DeselectAll"), { vm.selectAllPlaylist(false) }, enabled = selected > 0)
        }
        PillButton(
            if (selected > 0) "${tr("Home.DownloadSelected")} ($selected)" else tr("Home.DownloadSelected"),
            onClick = vm::downloadPlaylist, enabled = selected > 0, icon = Icons.Filled.Download,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(title: String, duration: String, thumbnail: String, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onToggle)
            .padding(start = 8.dp, end = OneUi.CardPadding, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Box(Modifier.width(84.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (thumbnail.isNotBlank()) AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(duration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Gallery ────────────────────────────────────────────────────────────────

@Composable
private fun GalleryHeader(post: MediaGalleryPost, selected: Int, vm: HomeViewModel) {
    RoundedGroup(contentPadding = PaddingValues(start = OneUi.CardPadding, end = 8.dp, top = 18.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(post.displayTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                val photos = post.items.count { !it.isVideo }
                val videos = post.items.size - photos
                val meta = buildList {
                    if (photos > 0) add(tr("Home.GalleryPhotoCount", photos))
                    if (videos > 0) add(tr("Home.GalleryVideoCount", videos))
                    add("@${post.author}")
                    if (post.nickname.isNotBlank() && post.nickname != post.displayTitle) add(post.nickname)
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::closeGallery) { Icon(Icons.Filled.Close, contentDescription = tr("Home.Close"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            tr("Home.GalleryHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, end = 14.dp),
        )
        Row(Modifier.padding(top = 2.dp)) {
            AccentTextButton(tr("Home.SelectAll"), { vm.selectAllGallery(true) })
            AccentTextButton(tr("Home.DeselectAll"), { vm.selectAllGallery(false) }, enabled = selected > 0)
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = galleryRequest(context, item.tileUrl, post.referer),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = if (selected) 0.92f else 1f
                scaleX = s; scaleY = s
            }.clip(shape),
        )
        if (item.isVideo) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
        }
        // One UI selection mark: round checkbox in the corner.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
            else Text(item.index.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GalleryFooter(post: MediaGalleryPost, state: HomeUiState, vm: HomeViewModel, pickFolder: (String) -> Unit) {
    Column(Modifier.padding(top = OneUi.CardGap)) {
        RoundedGroup {
            PrefRow(
                tr("Home.GallerySaveLocation"), summary = vm.folderLabel(state.galleryFolder), summaryAccent = true,
                icon = Icons.Filled.Folder, showChevron = true, onClick = { pickFolder("gallery") },
            )
            RowDivider(startInset = 72.dp)
            ChoiceRow(
                tr("Home.GallerySaveAs"),
                listOf("folder" to tr("Home.GallerySaveFolder"), "zip" to tr("Home.GallerySaveZip")),
                if (state.galleryAsZip) "zip" else "folder",
                { vm.setGalleryZip(it == "zip") },
            )
        }
        Row(Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val selected = state.gallerySelected.size
            PillButton(
                if (selected > 0) "${tr("Home.DownloadSelected")} ($selected)" else tr("Home.DownloadSelected"),
                onClick = { vm.downloadGallery(selectedOnly = true) }, enabled = selected > 0, tonal = true, modifier = Modifier.weight(1f),
            )
            PillButton(
                "${tr("Home.GalleryDownloadAll")} (${post.items.size})",
                onClick = { vm.downloadGallery(selectedOnly = false) }, modifier = Modifier.weight(1f),
            )
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
                "${item.index} / ${post?.items?.size ?: 0}", color = Color.White, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Filled.Close, contentDescription = tr("Home.Close"), tint = Color.White)
            }
            if (item.isVideo && item.urls.isNotEmpty()) {
                PillButton(
                    tr("Home.OpenInBrowser"),
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.urls.first())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp),
                )
            }
        }
    }
}

// ─── Jobs / log / history ───────────────────────────────────────────────────

/** Round tinted icon badge used at the start of list rows. */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun JobRow(job: JobState, onCancel: (Long) -> Unit, onDismiss: (Long) -> Unit) {
    val (icon, tint) = when (job.status) {
        JobStatus.Done -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        JobStatus.Failed -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        JobStatus.Canceled -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.Download to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (job.isActive) {
                if (job.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), strokeCap = StrokeCap.Round)
                else LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth().clip(CircleShape), strokeCap = StrokeCap.Round, drawStopIndicator = {})
            }
            Text(
                job.detail, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (job.status == JobStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (job.isActive) {
            AccentTextButton(tr("Home.Cancel"), { onCancel(job.id) })
        } else {
            IconButton(onClick = { onDismiss(job.id) }) {
                Icon(Icons.Filled.Close, contentDescription = tr("Home.Dismiss"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LogCard(downloads: DownloadManager) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 14.dp)) {
        RoundedGroup(contentPadding = PaddingValues(0.dp)) {
            PrefRow(tr("Home.DownloadLog"), onClick = { expanded = !expanded }) {
                if (expanded) AccentTextButton(tr("Home.Clear"), downloads::clearLog)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val log by downloads.log.collectAsStateWithLifecycle()
                val text = remember(log) { log.takeLast(400).joinToString("\n") }
                val scroll = rememberScrollState()
                LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }
                Box(
                    Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .verticalScroll(scroll)
                        .padding(14.dp),
                ) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: DownloadHistoryItem, onDelete: (DownloadHistoryItem) -> Unit) {
    val context = LocalContext.current
    val copied = tr("Home.PathCopied")
    val noViewer = tr("Home.NoViewer")
    val shareTitle = tr("Home.Share")
    var menu by remember { mutableStateOf(false) }

    val open = {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.fileUri), item.mimeType.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (_: Exception) {
            Toast.makeText(context, noViewer, Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            when (item.kind) {
                MediaKind.Video -> Icons.Filled.Movie
                MediaKind.Audio -> Icons.Filled.MusicNote
                MediaKind.Image -> Icons.Filled.Image
            },
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val time = SimpleDateFormat("HH:mm · d/M/yyyy", Locale.getDefault()).format(Date(item.completedAt))
            Text(
                "${item.qualityLabel} · $time",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(item.folderLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(
                expanded = menu, onDismissRequest = { menu = false },
                shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp,
            ) {
                MenuEntry(tr("Home.Play"), Icons.Filled.PlayArrow) { menu = false; open() }
                MenuEntry(tr("Home.CopyPath"), Icons.Filled.ContentCopy) {
                    menu = false
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("path", item.displayPath))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }
                MenuEntry(shareTitle, Icons.Filled.Share) {
                    menu = false
                    val send = Intent(Intent.ACTION_SEND).setType(item.mimeType.ifBlank { "*/*" })
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.fileUri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                MenuEntry(tr("Home.Delete"), Icons.Filled.Delete, destructive = true) { menu = false; onDelete(item) }
            }
        }
    }
}

@Composable
private fun MenuEntry(text: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = color) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
    )
}
