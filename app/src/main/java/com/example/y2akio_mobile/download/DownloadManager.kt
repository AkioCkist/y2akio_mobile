package com.example.y2akio_mobile.download

import android.content.Context
import com.example.y2akio_mobile.model.DownloadHistoryItem
import com.example.y2akio_mobile.model.DownloadRequest
import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.model.MediaKind
import com.example.y2akio_mobile.service.GalleryResolver
import com.example.y2akio_mobile.service.MediaGalleryService
import com.example.y2akio_mobile.service.YtDlpService
import com.example.y2akio_mobile.settings.AppSettings
import com.example.y2akio_mobile.settings.SettingsData
import com.example.y2akio_mobile.settings.Strings
import com.example.y2akio_mobile.storage.HistoryStore
import com.example.y2akio_mobile.storage.OutputStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class JobStatus { Queued, Running, Done, Failed, Canceled }

data class JobState(
    val id: Long,
    val title: String,
    val detail: String,
    /** 0..100, or -1 for indeterminate. */
    val progress: Float,
    val status: JobStatus,
    val resultUri: String? = null,
    val resultMime: String? = null,
) {
    val isActive get() = status == JobStatus.Queued || status == JobStatus.Running
}

/** Success / error banner shown on Home (the desktop app's InfoBars). */
data class Banner(val success: Boolean, val title: String, val message: String, val id: Long = System.nanoTime())

/**
 * Runs every download — in-app requests, gallery saves and share-sheet jobs — in an app-wide
 * scope that outlives the UI. [DownloadService] keeps the process in the foreground while any job
 * is active. Each job works in its own cache folder; results are exported via [OutputStorage] and
 * recorded in [HistoryStore].
 */
class DownloadManager(
    private val context: Context,
    private val settings: AppSettings,
    private val history: HistoryStore,
    private val storage: OutputStorage,
    private val ytDlp: YtDlpService,
    private val gallery: GalleryResolver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong(System.currentTimeMillis() % 100_000)
    private val running = ConcurrentHashMap<Long, Job>()
    // Two concurrent yt-dlp processes keep a phone responsive; more just compete for bandwidth.
    private val slots = Semaphore(2)

    private val _jobs = MutableStateFlow<List<JobState>>(emptyList())
    val jobs: StateFlow<List<JobState>> = _jobs.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    // ─── Log (throttled: yt-dlp prints a progress line many times per second) ───

    private val logBuffer = ArrayDeque<String>()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    @Volatile private var logDirty = false

    init {
        scope.launch {
            while (true) {
                delay(250)
                if (logDirty) {
                    logDirty = false
                    _log.value = synchronized(logBuffer) { logBuffer.toList() }
                }
            }
        }
    }

    fun appendLog(line: String) {
        synchronized(logBuffer) {
            logBuffer.addLast(line)
            while (logBuffer.size > 1500) logBuffer.removeFirst()
        }
        logDirty = true
    }

    fun clearLog() {
        synchronized(logBuffer) { logBuffer.clear() }
        _log.value = emptyList()
    }

    fun showBanner(banner: Banner?) {
        _banner.value = banner
    }

    private fun t(key: String) = Strings.get(key, settings.current.language)

    // ─── Public API ──────────────────────────────────────────────────────────

    /** In-app "Download Now" / playlist entries. */
    fun enqueueYtDlp(request: DownloadRequest): Long = launchJob(request.title.ifBlank { request.url }) { id ->
        runYtDlp(id, request)
    }

    /** In-app gallery "Download All / Selected". */
    fun enqueueGallery(post: MediaGalleryPost, items: List<MediaGalleryItem>, folder: String, asZip: Boolean): Long =
        launchJob(post.displayTitle) { id -> runGallery(id, post, items, folder, asZip) }

    /** Share-sheet job: gallery post when the link is one, else yt-dlp with the saved share defaults. */
    fun enqueueShared(sharedUrl: String): Long = launchJob(sharedUrl) { id ->
        settings.awaitLoaded()
        val s = settings.current
        val cookiesText = settings.decryptCookies()
        update(id) { it.copy(detail = t("Home.Analyzing")) }
        val url = gallery.resolve(sharedUrl, cookiesText)
        if (url != sharedUrl) appendLog("Resolved: $sharedUrl → $url")
        if (gallery.mightBeGallery(url)) {
            val post = gallery.tryFetch(url, cookiesText)
            if (post != null) {
                update(id) { it.copy(title = post.displayTitle) }
                runGallery(id, post, post.items, s.videoSaveFolder, s.shareGalleryZip)
                return@launchJob
            }
        }
        runYtDlp(id, shareRequest(url, s))
    }

    fun cancel(id: Long) {
        running[id]?.cancel()
    }

    fun cancelAll() = running.values.forEach { it.cancel() }

    fun dismiss(id: Long) = _jobs.update { list -> list.filterNot { it.id == id && !it.isActive } }

    fun clearFinished() = _jobs.update { list -> list.filter { it.isActive } }

    // ─── Job plumbing ────────────────────────────────────────────────────────

    private fun launchJob(title: String, body: suspend (Long) -> Unit): Long {
        val id = ids.incrementAndGet()
        _jobs.update { it + JobState(id, title, t("Job.Queued"), -1f, JobStatus.Queued) }
        DownloadService.start(context)

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                slots.withPermit {
                    update(id) { it.copy(status = JobStatus.Running, detail = t("Job.Starting")) }
                    body(id)
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(status = JobStatus.Canceled, detail = t("Job.Canceled"), progress = 0f) }
                appendLog("Canceled.")
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                val title = _jobs.value.firstOrNull { it.id == id }?.title ?: title
                update(id) { it.copy(status = JobStatus.Failed, detail = msg) }
                appendLog("ERROR: $msg")
                _banner.value = Banner(false, t("Job.Failed"), msg)
                Notifications.finished(context, id, "${t("Job.Failed")}: $title", msg, null, null, success = false)
            } finally {
                running.remove(id)
                File(context.cacheDir, "jobs/$id").deleteRecursively()
            }
        }
        running[id] = job
        job.start()
        return id
    }

    private fun update(id: Long, block: (JobState) -> JobState) =
        _jobs.update { list -> list.map { if (it.id == id) block(it) else it } }

    private fun workDir(id: Long) = File(context.cacheDir, "jobs/$id").apply { deleteRecursively(); mkdirs() }

    private fun finishJob(id: Long, title: String, savedTo: String, uri: String?, mime: String?, bannerMessage: String) {
        update(id) { it.copy(status = JobStatus.Done, progress = 100f, detail = t("Job.Done").replace("{0}", savedTo), resultUri = uri, resultMime = mime) }
        _banner.value = Banner(true, t("Home.SuccessTitle"), bannerMessage)
        Notifications.finished(context, id, title, t("Job.Done").replace("{0}", savedTo), uri, mime, success = true)
    }

    // ─── yt-dlp ──────────────────────────────────────────────────────────────

    private fun shareRequest(url: String, s: SettingsData): DownloadRequest {
        val audio = s.shareKind == "audio"
        val quality = when {
            audio -> "${s.shareAudioFormat.uppercase()} · Best"
            s.shareMaxHeight > 0 -> "${s.shareMaxHeight}p"
            else -> "Best Quality"
        }
        return DownloadRequest(
            url = url,
            kind = if (audio) MediaKind.Audio else MediaKind.Video,
            saveFolder = if (audio) s.audioSaveFolder else s.videoSaveFolder,
            targetHeight = s.shareMaxHeight.takeIf { it > 0 },
            containerFormat = s.shareContainer,
            audioFormat = s.shareAudioFormat,
            thumbnailMode = if (s.shareEmbedThumbnail) "embed" else "none",
            qualityLabel = quality,
        )
    }

    private suspend fun runYtDlp(id: Long, request: DownloadRequest) {
        val work = workDir(id)
        val cookies = settings.writeCookiesFile(id.toString())
        appendLog("=== ${request.title.ifBlank { request.url }} ===")
        var lastUpdate = 0L
        try {
            val files = ytDlp.download(
                request, work, cookies?.absolutePath,
                onProgress = { pct, line ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 300) {
                        lastUpdate = now
                        update(id) { it.copy(progress = pct, detail = line.removePrefix("[download]").trim()) }
                    }
                },
                onLog = { line ->
                    appendLog(line)
                    // Pick the real title up from the destination line when it wasn't analyzed first.
                    if (request.title.isBlank()) {
                        Regex("""Destination:\s*(.+)$""").find(line)?.let { m ->
                            val name = File(m.groupValues[1].trim()).nameWithoutExtension.substringBeforeLast(".f")
                            update(id) { it.copy(title = name) }
                        }
                    }
                },
            )
            if (files.isEmpty()) throw IllegalStateException("yt-dlp finished but produced no file.")

            update(id) { it.copy(progress = -1f, detail = t("Job.Saving")) }
            val main = files.first()
            val title = request.title.ifBlank { main.nameWithoutExtension }
            var mainSaved: OutputStorage.Saved? = null
            for (file in files) {
                val saved = storage.save(file, request.saveFolder)
                appendLog("Saved: ${saved.displayPath}")
                if (file == main) mainSaved = saved
            }
            val saved = mainSaved!!
            history.add(
                DownloadHistoryItem(
                    title = title,
                    kind = request.kind,
                    qualityLabel = request.qualityLabel,
                    fileUri = saved.uri.toString(),
                    mimeType = saved.mimeType,
                    displayPath = saved.displayPath,
                    folderLabel = storage.folderLabel(request.saveFolder),
                    completedAt = System.currentTimeMillis(),
                ),
            )
            update(id) { it.copy(title = title) }
            finishJob(id, title, saved.displayPath, saved.uri.toString(), saved.mimeType, "$title → ${saved.displayPath}")
        } finally {
            settings.deleteCookiesFile(cookies)
        }
    }

    // ─── Gallery ─────────────────────────────────────────────────────────────

    /**
     * Saves the given items as loose files in a per-post subfolder (`author_postId/`) or as one
     * `author_postId.zip`.
     */
    private suspend fun runGallery(id: Long, post: MediaGalleryPost, items: List<MediaGalleryItem>, folder: String, asZip: Boolean) {
        if (items.isEmpty()) throw IllegalStateException("Nothing to download.")
        val work = workDir(id)
        appendLog("${post.source} ${post.id} by @${post.author}: saving ${items.size} item(s)${if (asZip) " as ZIP" else ""}")

        val downloaded = mutableListOf<Pair<String, File>>()
        items.forEachIndexed { i, item ->
            update(id) {
                it.copy(
                    detail = t("Home.GalleryFetching").replace("{0}", "${i + 1}").replace("{1}", "${items.size}"),
                    progress = i * 90f / items.size,
                )
            }
            MediaGalleryService.ensureFullUrls(item)
            val tmp = File(work, "item_$i")
            MediaGalleryService.downloadToFile(item.urls, post.referer, tmp)
            downloaded += MediaGalleryService.fileNameFor(post, item, tmp) to tmp
        }

        update(id) { it.copy(detail = t("Home.GalleryWriting"), progress = 92f) }
        val baseName = MediaGalleryService.baseNameFor(post)
        val first: OutputStorage.Saved
        val savedTarget: String
        if (asZip) {
            val zip = File(work, "$baseName.zip")
            MediaGalleryService.zip(downloaded, zip)
            first = storage.save(zip, folder)
            savedTarget = first.displayPath
        } else {
            var firstSaved: OutputStorage.Saved? = null
            for ((name, file) in downloaded) {
                val saved = storage.save(file, folder, baseName, name)
                if (firstSaved == null) firstSaved = saved
            }
            first = firstSaved!!
            savedTarget = storage.folderLabel(folder, baseName)
        }
        appendLog("Saved: $savedTarget")

        val title = post.title.trim().ifBlank { "@${post.author} · ${post.id}" }
        history.add(
            DownloadHistoryItem(
                title = title,
                kind = if (items.all { it.isVideo }) MediaKind.Video else MediaKind.Image,
                qualityLabel = "${post.source} · ${if (asZip) "ZIP · " else ""}${items.size} file(s)",
                fileUri = first.uri.toString(),
                mimeType = first.mimeType,
                displayPath = first.displayPath,
                folderLabel = if (asZip) storage.folderLabel(folder) else savedTarget,
                completedAt = System.currentTimeMillis(),
            ),
        )
        finishJob(
            id, title, savedTarget, first.uri.toString(), first.mimeType,
            t("Home.GallerySaved").replace("{0}", "${items.size}").replace("{1}", savedTarget),
        )
    }
}
