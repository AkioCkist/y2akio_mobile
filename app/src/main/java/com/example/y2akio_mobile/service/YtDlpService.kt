package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.AnalyzeResult
import com.example.y2akio_mobile.model.DownloadRequest
import com.example.y2akio_mobile.model.MediaKind
import com.example.y2akio_mobile.model.PlaylistInfo
import com.example.y2akio_mobile.model.VideoInfo
import com.example.y2akio_mobile.model.YouTubePreview
import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.parseJsonObject
import com.example.y2akio_mobile.util.str
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * yt-dlp front end: URL analysis (playlist probe / full dump), download command construction,
 * progress reporting and output collection. Runs the embedded yt-dlp of youtubedl-android, which
 * already wires in its bundled FFmpeg (`--ffmpeg-location`) and QuickJS (`--js-runtimes`).
 */
class YtDlpService(private val ensureReady: suspend () -> Unit) {

    class ProcessResult(val out: String, val err: String, val exitCode: Int)

    // ─── Analysis ────────────────────────────────────────────────────────────

    /**
     * Analyzes a pasted link. Definite single-video URLs go straight to the full dump; anything
     * else runs a fast `--flat-playlist` probe first and becomes a playlist when it has >1 entries.
     */
    suspend fun analyze(url: String, cookiesPath: String?): AnalyzeResult {
        if (looksLikeSingleVideoUrl(url)) return AnalyzeResult.Video(analyzeVideo(url, cookiesPath))

        val flatJson = runDumpJson(url, flat = true, cookiesPath = cookiesPath)
        val root = parseJsonObject(flatJson)
        val entries = root.arr("entries")
        if (root != null && entries != null && entries.length() > 1) {
            return AnalyzeResult.Playlist(PlaylistInfo.fromFlatJson(root, url))
        }
        return AnalyzeResult.Video(analyzeVideo(url, cookiesPath))
    }

    /** Full (non-flat) `--dump-single-json` for one specific video, formats included. */
    suspend fun analyzeVideo(url: String, cookiesPath: String?): VideoInfo =
        VideoInfo.fromJson(runDumpJson(url, flat = false, cookiesPath = cookiesPath), url)

    /** Cheap YouTube-only title/author/thumbnail via oEmbed (~0.2s). Never throws. */
    suspend fun fetchYouTubePreview(url: String): YouTubePreview? {
        if (!isYouTubeUrl(url)) return null
        return try {
            val endpoint = "https://www.youtube.com/oembed?url=${URLEncoder.encode(url, "UTF-8")}&format=json"
            val body = MediaGalleryService.execute(Request.Builder().url(endpoint).build()).use { r ->
                if (!r.isSuccessful) return null
                withContext(Dispatchers.IO) { r.body.string() }
            }
            val root = parseJsonObject(body) ?: return null
            val title = root.str("title")
            if (title.isBlank()) null else YouTubePreview(title, root.str("author_name"), root.str("thumbnail_url"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun runDumpJson(url: String, flat: Boolean, cookiesPath: String?): String {
        val target = normalizeUrl(url)
        var lastError = ""
        for (attempt in attemptArgs(target, cookiesPath)) {
            val args = mutableListOf("--dump-single-json", "--no-warnings")
            if (flat) args += listOf("--flat-playlist", "--playlist-items", "1-500") else args += "--no-playlist"
            args += attempt
            val result = run(target, args, redirectErrorStream = false)
            lastError = result.err
            if (result.exitCode == 0 && result.out.isNotBlank()) return result.out
            if (!isRetryable(target, lastError)) break
        }
        throw IllegalStateException(cleanError(lastError).ifBlank { "yt-dlp failed to analyze this link." })
    }

    // ─── Download ────────────────────────────────────────────────────────────

    /**
     * Downloads according to [req] into the empty work folder [workDir]. Returns the media files
     * produced (sidecars removed when they were embedded). [onProgress] gets percent + raw line.
     */
    suspend fun download(
        req: DownloadRequest,
        workDir: File,
        cookiesPath: String?,
        onProgress: (Float, String) -> Unit,
        onLog: (String) -> Unit,
    ): List<File> {
        workDir.mkdirs()
        val url = normalizeUrl(req.url)
        val outputTemplate = File(workDir, buildOutputTemplate(req)).absolutePath

        val recent = ArrayDeque<String>()
        var lastError = ""
        var lastExit = 1
        for (attempt in attemptArgs(url, cookiesPath)) {
            val args = buildDownloadArgs(req, outputTemplate, attempt)
            val result = run(url, args, redirectErrorStream = true) { progress, line ->
                onLog(line)
                synchronized(recent) {
                    recent.addLast(line)
                    if (recent.size > 40) recent.removeFirst()
                }
                if (progress >= 0) onProgress(progress, line.trim())
            }
            lastExit = result.exitCode
            // stderr is merged into the log stream, so the error text comes from the last lines seen.
            lastError = synchronized(recent) {
                recent.filter { it.contains("ERROR", ignoreCase = false) }.joinToString("\n").ifBlank { recent.takeLast(5).joinToString("\n") }
            }.ifBlank { result.err }
            if (lastExit == 0) break
            if (!isRetryable(url, lastError)) break
            onLog("Retrying with the next client...")
        }
        if (lastExit != 0) throw IllegalStateException(cleanError(lastError).ifBlank { "Download failed." })

        return collectOutputs(req, workDir)
    }

    private fun buildDownloadArgs(req: DownloadRequest, outputTemplate: String, attempt: List<String>): List<String> {
        val args = mutableListOf("--newline", "--no-playlist", "--no-mtime", "--no-part")
        args += attempt
        args += listOf("-o", outputTemplate)

        if (req.kind == MediaKind.Audio) {
            args += listOf("-x", "--audio-format", req.audioFormat)
            args += listOf("--audio-quality", req.audioBitrateKbps?.let { "${it}k" } ?: "0")
        } else {
            val heightFilter = req.targetHeight?.let { "[height<=$it]" } ?: ""
            args += listOf("-f", "bestvideo$heightFilter+bestaudio/best$heightFilter")
            if (req.videoCodec != "auto") {
                // Force transcode: merge to mkv first (--recode-video mp4 skips when already mp4),
                // then convert to the chosen codec. Saved file is always that codec in MP4.
                args += listOf("--merge-output-format", "mkv", "--recode-video", "mp4")
                args += listOf("--postprocessor-args", "recode:${codecTranscodeArgs(req.videoCodec)} -c:a aac -c:s mov_text")
            } else {
                args += listOf("--merge-output-format", req.containerFormat)
            }
        }

        if (req.embedMetadata) args += "--add-metadata"
        when (req.thumbnailMode) {
            "embed" -> args += "--embed-thumbnail"
            "save" -> args += "--write-thumbnail"
        }

        if (req.kind == MediaKind.Video && req.subtitlesEnabled && (req.subtitleManual || req.subtitleAuto)) {
            if (req.subtitleManual) args += "--write-subs"
            if (req.subtitleAuto) args += "--write-auto-subs"
            args += listOf("--sub-langs", req.subtitleLangCode, "--convert-subs", req.subtitleFormat)
            if (req.embedSubtitles) args += "--embed-subs"
        }
        return args
    }

    /**
     * Everything yt-dlp left in the work folder, minus partial fragments and the sidecars the user
     * asked to embed (subtitles / thumbnail images). Main media first (largest file).
     */
    private fun collectOutputs(req: DownloadRequest, workDir: File): List<File> {
        val subtitleExts = setOf("srt", "vtt", "ass", "ssa", "sub")
        val thumbExts = setOf("webp", "jpg", "jpeg", "png")
        val files = workDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val keep = files.filter { f ->
            val ext = f.extension.lowercase()
            val drop = ext == "part" || ext == "ytdl" || f.name.endsWith(".temp") ||
                (req.kind == MediaKind.Video && req.embedSubtitles && ext in subtitleExts) ||
                (req.thumbnailMode == "embed" && ext in thumbExts)
            if (drop) f.delete()
            !drop
        }
        return keep.sortedByDescending { it.length() }
    }

    private fun buildOutputTemplate(req: DownloadRequest): String {
        val parts = mutableListOf<String>()
        if (req.prefixDateTime) parts += SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        if (req.prefixChannel) parts += "%(uploader).80B"
        // Facebook / Instagram titles are often generic, so two videos would map to the same file
        // name — the id keeps names unique. Titles are byte-capped: Android caps names at 255 bytes.
        val title = if (isFacebookUrl(req.url) || isInstagramUrl(req.url)) "%(title).120B [%(id)s]" else "%(title).150B"
        if (req.includeTitle || parts.isEmpty()) parts += title
        return parts.joinToString(" - ") + ".%(ext)s"
    }

    // ─── Process ─────────────────────────────────────────────────────────────

    /**
     * Runs yt-dlp with [args] + [url]. Cancelling the coroutine kills the process. A non-zero exit
     * is returned (not thrown) so callers can apply their retry policy.
     */
    private suspend fun run(
        url: String,
        args: List<String>,
        redirectErrorStream: Boolean,
        onLine: ((Float, String) -> Unit)? = null,
    ): ProcessResult {
        ensureReady()
        val processId = UUID.randomUUID().toString()
        val request = YoutubeDLRequest(url)
        // YoutubeDLRequest stores options as a map; keep repeated flags by passing raw commands.
        request.addCommands(args)

        return coroutineScope {
            val job = async(Dispatchers.IO) {
                try {
                    val r = YoutubeDL.getInstance().execute(request, processId, redirectErrorStream) { progress, _, line ->
                        onLine?.invoke(progress, line)
                    }
                    ProcessResult(r.out, r.err, r.exitCode)
                } catch (e: YoutubeDL.CanceledException) {
                    throw CancellationException("Canceled")
                } catch (e: YoutubeDLException) {
                    ProcessResult("", e.message ?: "", 1)
                }
            }
            try {
                job.await()
            } catch (e: CancellationException) {
                YoutubeDL.getInstance().destroyProcessById(processId)
                throw e
            }
        }
    }

    companion object {
        /** ffmpeg encoder + quality flags for a forced codec transcode. */
        fun codecTranscodeArgs(codec: String) = when (codec) {
            "h264" -> "-c:v libx264 -preset medium -crf 18"
            "hevc" -> "-c:v libx265 -preset medium -crf 23"
            "av1" -> "-c:v libsvtav1 -preset 6 -crf 30"
            "vp9" -> "-c:v libvpx-vp9 -crf 31 -b:v 0 -row-mt 1"
            "mpeg4" -> "-c:v mpeg4 -q:v 3"
            else -> ""
        }

        /**
         * The yt-dlp variants to try in order: YouTube web client then the Android player client
         * (bot checks), Facebook anonymous then with cookies, everything else one run.
         */
        fun attemptArgs(url: String, cookiesPath: String?): List<List<String>> {
            if (isYouTubeUrl(url)) return listOf(emptyList(), listOf("--extractor-args", "youtube:player_client=android"))
            val cookies = cookiesArg(cookiesPath, url)
            if (isFacebookUrl(url) && cookies.isNotEmpty()) return listOf(emptyList(), cookies)
            return listOf(cookies)
        }

        /** YouTube trips its bot check on a whole-browser cookie jar; only send cookies elsewhere. */
        private fun cookiesArg(cookiesPath: String?, url: String): List<String> =
            if (cookiesPath.isNullOrBlank() || isYouTubeUrl(url)) emptyList() else listOf("--cookies", cookiesPath)

        fun isRetryable(url: String, error: String) = !isYouTubeUrl(url) || isYoutubeClientRetryable(error)

        private fun isYoutubeClientRetryable(error: String?): Boolean {
            if (error.isNullOrBlank()) return false
            return listOf("This video is not available", "Video unavailable", "needs to be reloaded", "Requested format is not available", "Sign in to confirm")
                .any { error.contains(it, ignoreCase = true) }
        }

        fun isYouTubeUrl(url: String): Boolean {
            val host = parseUrl(url)?.host ?: return false
            return host.endsWith("youtube.com", true) || host.endsWith("youtube-nocookie.com", true) || host.equals("youtu.be", true)
        }

        fun isFacebookUrl(url: String): Boolean {
            val host = parseUrl(url)?.host ?: return false
            return host.endsWith("facebook.com", true) || host.endsWith("fb.watch", true)
        }

        fun isInstagramUrl(url: String) = parseUrl(url)?.host?.endsWith("instagram.com", true) == true

        /** True only when the URL definitely points at ONE YouTube video (skips the playlist probe). */
        fun looksLikeSingleVideoUrl(url: String): Boolean {
            val u = parseUrl(normalizeUrl(url)) ?: return false
            if (!isYouTubeUrl(url)) return false
            if (u.queryParameterNames.any { it.equals("list", true) }) return false

            val segs = u.segments
            if (u.host.equals("youtu.be", true)) return segs.size == 1 && segs[0].isNotBlank()
            if (segs.size == 1 && segs[0].equals("watch", true)) return !u.queryParameter("v").isNullOrBlank()
            return segs.size == 2 && segs[0] in setOf("shorts", "embed", "live", "v") && segs[1].isNotBlank()
        }

        /** Facebook `/stories/<id>/<token>/` → `watch/?v=<id>` (yt-dlp only knows the latter). */
        fun normalizeUrl(url: String): String {
            val u = parseUrl(url) ?: return url.trim()
            if (!u.host.endsWith("facebook.com", true)) return url.trim()
            if (!u.encodedPath.startsWith("/stories/", true)) return url.trim()
            val segs = u.segments
            if (segs.size < 2 || segs[1].toLongOrNull() == null) return url.trim()
            return "${u.scheme}://${u.host}/watch/?v=${segs[1]}"
        }

        /** Keeps yt-dlp's "ERROR: ..." lines (the useful part) and drops Python tracebacks. */
        fun cleanError(error: String): String {
            val lines = error.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val errors = lines.filter { it.startsWith("ERROR") }
            return (errors.ifEmpty { lines.takeLast(3) }).joinToString("\n").removePrefix("ERROR: ")
        }
    }
}
