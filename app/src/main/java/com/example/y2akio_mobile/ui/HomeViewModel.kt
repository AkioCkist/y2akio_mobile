package com.example.y2akio_mobile.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.y2akio_mobile.Y2AkioApp
import com.example.y2akio_mobile.download.Banner
import com.example.y2akio_mobile.model.AnalyzeResult
import com.example.y2akio_mobile.model.DownloadRequest
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.model.MediaKind
import com.example.y2akio_mobile.model.PlaylistEntry
import com.example.y2akio_mobile.model.PlaylistInfo
import com.example.y2akio_mobile.model.VideoInfo
import com.example.y2akio_mobile.service.YtDlpService
import com.example.y2akio_mobile.settings.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Header of the options card: painted instantly (oEmbed / playlist entry) before the full dump lands. */
data class MediaCard(val title: String, val uploader: String, val duration: String, val thumbnailUrl: String)

/** Every control of the "Download Options" panel. */
data class OptionsState(
    val isVideo: Boolean = true,
    /** "Best Quality", "1080p", "320 kbps"... */
    val quality: String = BEST,
    val container: String = "mp4",
    val codec: String = "auto",
    val audioFormat: String = "mp3",
    val prefixDateTime: Boolean = false,
    val prefixChannel: Boolean = false,
    val includeTitle: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val subtitleEmbed: Boolean = true,
    val subtitleManual: Boolean = true,
    val subtitleAuto: Boolean = false,
    /** "all", a language code, or "none". */
    val manualLanguage: String = "all",
    val autoLanguages: Set<String> = emptySet(),
    val subtitleFormat: String = "srt",
    val videoFolder: String = "",
    val audioFolder: String = "",
    val embedMetadata: Boolean = true,
    /** "embed" / "save" / "none". */
    val thumbnailMode: String = "embed",
) {
    companion object {
        const val BEST = "Best Quality"
    }
}

data class HomeUiState(
    val url: String = "",
    val analyzing: Boolean = false,
    val card: MediaCard? = null,
    val video: VideoInfo? = null,
    val playlist: PlaylistInfo? = null,
    val playlistSelected: Set<String> = emptySet(),
    val gallery: MediaGalleryPost? = null,
    val gallerySelected: Set<Int> = emptySet(),
    val galleryAsZip: Boolean = false,
    val galleryFolder: String = "",
    val options: OptionsState = OptionsState(),
) {
    /** Options panel is shown for a single video and (without header) for playlist batch settings. */
    val showOptions get() = card != null || playlist != null

    /** Quality dropdown entries for the selected media type (desktop: RefreshQualityOptions). */
    val qualityChoices: List<String>
        get() = if (options.isVideo) {
            val heights = video?.videoHeights?.takeIf { it.isNotEmpty() } ?: if (playlist != null) GENERIC_HEIGHTS else emptyList()
            listOf(OptionsState.BEST) + heights.map { "${it}p" }
        } else {
            listOf(OptionsState.BEST) + if (options.audioFormat in LOSSLESS) emptyList() else listOf(320, 256, 192, 128).map { "$it kbps" }
        }

    companion object {
        val GENERIC_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
        val LOSSLESS = setOf("flac", "wav", "alac")
    }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val y2 = app as Y2AkioApp
    private val _state = MutableStateFlow(
        HomeUiState(
            options = OptionsState(videoFolder = y2.settings.current.videoSaveFolder, audioFolder = y2.settings.current.audioSaveFolder),
            galleryFolder = y2.settings.current.videoSaveFolder,
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        // Save-folder changes made in Settings flow into the Home panel (desktop: SettingsChanged).
        viewModelScope.launch {
            var last = y2.settings.current
            y2.settings.state.collect { s ->
                if (s.videoSaveFolder != last.videoSaveFolder || s.audioSaveFolder != last.audioSaveFolder) {
                    _state.update {
                        it.copy(
                            options = it.options.copy(videoFolder = s.videoSaveFolder, audioFolder = s.audioSaveFolder),
                            galleryFolder = s.videoSaveFolder,
                        )
                    }
                }
                last = s
            }
        }
    }

    private fun t(key: String) = Strings.get(key, y2.settings.current.language)

    fun consumeToast() {
        _toast.value = null
    }

    fun setUrl(url: String) = _state.update { it.copy(url = url) }

    fun updateOptions(block: (OptionsState) -> OptionsState) = _state.update { s ->
        val next = s.copy(options = block(s.options))
        // Keep the quality valid when the media type / audio format changes the list.
        if (next.options.quality !in next.qualityChoices) next.copy(options = next.options.copy(quality = OptionsState.BEST)) else next
    }

    /** Takes persistable access to a folder picked on Home (applies to this session's downloads). */
    fun pickFolder(uri: Uri, target: String) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        when (target) {
            "video" -> updateOptions { it.copy(videoFolder = uri.toString()) }
            "audio" -> updateOptions { it.copy(audioFolder = uri.toString()) }
            "gallery" -> _state.update { it.copy(galleryFolder = uri.toString()) }
        }
    }

    fun folderLabel(folder: String) = y2.settings.folderLabel(folder)

    // ─── Analyze ─────────────────────────────────────────────────────────────

    fun analyze(urlOverride: String? = null) {
        val input = (urlOverride ?: _state.value.url).trim()
        if (input.isBlank() || _state.value.analyzing) return
        y2.downloads.showBanner(null)
        _state.update { it.copy(url = input, analyzing = true, card = null, video = null, playlist = null, gallery = null) }

        viewModelScope.launch {
            var cookies: java.io.File? = null
            try {
                y2.settings.awaitLoaded()
                val cookiesText = y2.settings.decryptCookies()
                // Share links (facebook.com/share/…, vt.tiktok.com/…) only reveal a photo post or a
                // story after their redirects, so resolve them before routing.
                val url = y2.gallery.resolve(input, cookiesText)
                if (url != input) y2.downloads.appendLog("Resolved: $input → $url")

                // Gallery posts (TikTok photos, X photos, Facebook / Instagram posts and stories) never
                // reach yt-dlp — they get the pick-and-save gallery instead.
                if (y2.gallery.mightBeGallery(url)) {
                    val post = y2.gallery.tryFetch(url, cookiesText)
                    if (post != null) {
                        _state.update { it.copy(gallery = post, gallerySelected = emptySet()) }
                        return@launch
                    }
                }

                cookies = y2.settings.writeCookiesFile("analyze")
                if (YtDlpService.looksLikeSingleVideoUrl(url)) {
                    // Fast single-video path: one full dump; paint the card from oEmbed meanwhile.
                    // coroutineScope rethrows a failed dump here instead of crashing the parent.
                    val cookiesPath = cookies?.absolutePath
                    coroutineScope {
                        val dump = async { y2.ytDlp.analyzeVideo(url, cookiesPath) }
                        launch {
                            val preview = y2.ytDlp.fetchYouTubePreview(url) ?: return@launch
                            _state.update { s -> if (s.video == null && s.analyzing) s.copy(card = MediaCard(preview.title, preview.author, "", preview.thumbnailUrl)) else s }
                        }
                        finishVideo(dump.await())
                    }
                } else {
                    when (val result = y2.ytDlp.analyze(url, cookies?.absolutePath)) {
                        is AnalyzeResult.Playlist -> _state.update {
                            it.copy(playlist = result.playlist, playlistSelected = result.playlist.entries.map { e -> e.url }.toSet())
                        }
                        is AnalyzeResult.Video -> finishVideo(result.video)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(card = null, video = null) }
                y2.downloads.showBanner(Banner(false, t("Home.ErrorTitle"), e.message ?: e.toString()))
            } finally {
                y2.settings.deleteCookiesFile(cookies)
                _state.update { it.copy(analyzing = false) }
            }
        }
    }

    private fun finishVideo(video: VideoInfo) = _state.update { s ->
        val next = s.copy(
            video = video,
            card = MediaCard(video.title, video.uploader, video.durationDisplay, video.thumbnailUrl),
            options = s.options.copy(
                quality = OptionsState.BEST,
                manualLanguage = "all",
                // Auto-generated languages start ticked, like the desktop checklist.
                autoLanguages = video.autoSubtitleLanguages.keys,
            ),
        )
        next
    }

    /** Playlist row tapped: show the entry card instantly, then load its own formats. */
    fun openPlaylistEntry(entry: PlaylistEntry) {
        if (_state.value.analyzing) return
        y2.downloads.showBanner(null)
        _state.update { it.copy(analyzing = true, video = null, card = MediaCard(entry.title, "", entry.durationDisplay, entry.thumbnailUrl)) }
        viewModelScope.launch {
            val cookies = y2.settings.writeCookiesFile("analyze")
            try {
                finishVideo(y2.ytDlp.analyzeVideo(entry.url, cookies?.absolutePath))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(card = null) }
                y2.downloads.showBanner(Banner(false, t("Home.ErrorTitle"), e.message ?: e.toString()))
            } finally {
                y2.settings.deleteCookiesFile(cookies)
                _state.update { it.copy(analyzing = false) }
            }
        }
    }

    fun closeVideoCard() = _state.update { it.copy(card = null, video = null) }

    fun togglePlaylistEntry(entry: PlaylistEntry) = _state.update {
        val sel = it.playlistSelected
        it.copy(playlistSelected = if (entry.url in sel) sel - entry.url else sel + entry.url)
    }

    fun selectAllPlaylist(select: Boolean) = _state.update {
        it.copy(playlistSelected = if (select) it.playlist?.entries?.map { e -> e.url }?.toSet() ?: emptySet() else emptySet())
    }

    // ─── Download ────────────────────────────────────────────────────────────

    private fun buildRequest(url: String, title: String): DownloadRequest {
        val s = _state.value
        val o = s.options
        val isVideo = o.isVideo
        val targetHeight = if (isVideo) o.quality.removeSuffix("p").toIntOrNull() else null
        val audioKbps = if (!isVideo) o.quality.filter(Char::isDigit).toIntOrNull() else null

        val subtitleManual = o.subtitlesEnabled && o.subtitleManual && o.manualLanguage != "none"
        val subtitleAuto = o.subtitlesEnabled && o.subtitleAuto && o.autoLanguages.isNotEmpty()
        val langs = run {
            if (o.subtitleManual && o.manualLanguage == "all") return@run "all"
            val codes = mutableListOf<String>()
            if (o.subtitleManual && o.manualLanguage != "none") codes += o.manualLanguage
            if (o.subtitleAuto) codes += o.autoLanguages
            if (codes.isEmpty()) "all" else codes.joinToString(",")
        }

        val qualityLabel = if (isVideo) o.quality else "${o.audioFormat.uppercase()} · ${o.quality}"
        return DownloadRequest(
            url = url,
            kind = if (isVideo) MediaKind.Video else MediaKind.Audio,
            saveFolder = if (isVideo) o.videoFolder else o.audioFolder,
            targetHeight = targetHeight,
            // A non-Auto codec forces a transcode → the target container is always MP4.
            containerFormat = if (o.codec == "auto") o.container else "mp4",
            videoCodec = o.codec,
            audioBitrateKbps = audioKbps,
            audioFormat = o.audioFormat,
            prefixDateTime = o.prefixDateTime,
            prefixChannel = o.prefixChannel,
            includeTitle = o.includeTitle,
            subtitlesEnabled = o.subtitlesEnabled,
            subtitleManual = subtitleManual,
            subtitleAuto = subtitleAuto,
            subtitleLangCode = langs,
            subtitleFormat = o.subtitleFormat,
            embedSubtitles = o.subtitleEmbed,
            embedMetadata = o.embedMetadata,
            thumbnailMode = o.thumbnailMode,
            qualityLabel = qualityLabel,
            title = title,
        )
    }

    fun downloadNow() {
        val video = _state.value.video ?: return
        y2.downloads.enqueueYtDlp(buildRequest(video.originalUrl, video.title))
        _toast.value = t("Home.Queued")
    }

    fun downloadPlaylist() {
        val s = _state.value
        val playlist = s.playlist ?: return
        val selected = playlist.entries.filter { it.url in s.playlistSelected }
        if (selected.isEmpty()) return
        selected.forEach { y2.downloads.enqueueYtDlp(buildRequest(it.url, it.title)) }
        _toast.value = t("Home.Queued") + " (${selected.size})"
    }

    // ─── Gallery ─────────────────────────────────────────────────────────────

    fun toggleGalleryItem(index: Int) = _state.update {
        val sel = it.gallerySelected
        it.copy(gallerySelected = if (index in sel) sel - index else sel + index)
    }

    fun selectAllGallery(select: Boolean) = _state.update {
        it.copy(gallerySelected = if (select) it.gallery?.items?.map { i -> i.index }?.toSet() ?: emptySet() else emptySet())
    }

    fun setGalleryZip(zip: Boolean) = _state.update { it.copy(galleryAsZip = zip) }

    fun downloadGallery(selectedOnly: Boolean) {
        val s = _state.value
        val post = s.gallery ?: return
        // Keep post order regardless of the order items were tapped in.
        val items = if (selectedOnly) post.items.filter { it.index in s.gallerySelected } else post.items
        if (items.isEmpty()) return
        y2.downloads.enqueueGallery(post, items.sortedBy { it.index }, s.galleryFolder, s.galleryAsZip)
        _toast.value = t("Home.Queued")
    }

    fun closeGallery() = _state.update { it.copy(gallery = null, gallerySelected = emptySet()) }
}
