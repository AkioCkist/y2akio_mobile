package com.example.y2akio_mobile.model

import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.keyList
import com.example.y2akio_mobile.util.num
import com.example.y2akio_mobile.util.objects
import com.example.y2akio_mobile.util.str
import org.json.JSONObject

enum class MediaKind { Video, Audio, Image }

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A single entry from yt-dlp's "formats" array (only the fields the app uses). */
data class VideoFormat(
    val formatId: String,
    val extension: String,
    val height: Int?,
    val audioBitrateKbps: Double?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
) {
    companion object {
        fun fromJson(el: JSONObject): VideoFormat {
            val vcodec = el.str("vcodec")
            val acodec = el.str("acodec")
            return VideoFormat(
                formatId = el.str("format_id"),
                extension = el.str("ext"),
                height = el.num("height")?.toInt(),
                audioBitrateKbps = el.num("abr"),
                hasVideo = vcodec.isNotEmpty() && vcodec != "none",
                hasAudio = acodec.isNotEmpty() && acodec != "none",
            )
        }
    }
}

/** Parsed result of `yt-dlp --dump-single-json <url>`. */
data class VideoInfo(
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Double,
    val originalUrl: String,
    val formats: List<VideoFormat>,
    /** Author-uploaded subtitles: language code -> display name. */
    val manualSubtitleLanguages: Map<String, String>,
    /** Auto-generated captions: language code -> display name. */
    val autoSubtitleLanguages: Map<String, String>,
) {
    val durationDisplay: String get() = if (durationSeconds > 0) formatDuration(durationSeconds) else ""

    /** Distinct video heights, highest first — the "Quality" choices. */
    val videoHeights: List<Int>
        get() = formats.filter { it.hasVideo && it.height != null }.mapNotNull { it.height }.distinct().sortedDescending()

    companion object {
        fun fromJson(json: String, originalUrl: String): VideoInfo {
            val root = JSONObject(json)
            val formats = root.arr("formats").objects().map(VideoFormat::fromJson)
            return VideoInfo(
                title = root.str("title"),
                uploader = root.str("uploader").ifEmpty { root.str("channel") },
                thumbnailUrl = root.str("thumbnail"),
                durationSeconds = root.num("duration") ?: 0.0,
                originalUrl = originalUrl,
                formats = formats,
                manualSubtitleLanguages = parseSubtitleLanguages(root, "subtitles"),
                autoSubtitleLanguages = parseSubtitleLanguages(root, "automatic_captions"),
            )
        }

        /** yt-dlp's subtitle maps are keyed by language code; each track list usually carries a friendly "name". */
        private fun parseSubtitleLanguages(root: JSONObject, property: String): Map<String, String> {
            val section = root.optJSONObject(property) ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            for (code in section.keyList()) {
                val first = section.optJSONArray(code)?.optJSONObject(0)
                result[code] = first.str("name").ifEmpty { code }
            }
            return result
        }
    }
}

/** One track in a playlist, parsed from flat-playlist mode (no formats yet). */
data class PlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSeconds: Double,
) {
    val durationDisplay: String get() = if (durationSeconds <= 0) "—" else formatDuration(durationSeconds)

    companion object {
        fun fromFlatJson(el: JSONObject, playlistUrl: String): PlaylistEntry {
            val id = el.str("id")
            var url = el.str("url").ifBlank { el.str("webpage_url") }
            if (url.isBlank() && id.isNotBlank()) {
                // Best-effort reconstruction: YouTube always accepts the watch URL form.
                url = if (playlistUrl.contains("youtube.com") || playlistUrl.contains("youtu.be"))
                    "https://www.youtube.com/watch?v=$id" else "$playlistUrl?v=$id"
            }
            var thumb = el.str("thumbnail")
            if (thumb.isBlank()) thumb = el.arr("thumbnails").objects().lastOrNull { it.str("url").isNotEmpty() }.str("url")
            // YouTube flat entries carry no thumbnail at all; the id gives a stable one.
            if (thumb.isBlank() && id.isNotBlank() && url.contains("youtu")) thumb = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            val title = el.str("title")
            return PlaylistEntry(
                id = id,
                title = title.ifBlank { "(untitled $id)" },
                url = url,
                thumbnailUrl = thumb,
                durationSeconds = el.num("duration") ?: 0.0,
            )
        }
    }
}

/** A parsed playlist, produced from a `--flat-playlist --dump-single-json` run. */
data class PlaylistInfo(
    val title: String,
    val uploader: String,
    val originalUrl: String,
    val entries: List<PlaylistEntry>,
) {
    companion object {
        fun fromFlatJson(root: JSONObject, originalUrl: String): PlaylistInfo {
            val entries = root.arr("entries").objects()
                .map { PlaylistEntry.fromFlatJson(it, originalUrl) }
                .filter { it.url.isNotBlank() }
            return PlaylistInfo(
                title = root.str("title"),
                uploader = root.str("uploader").ifEmpty { root.str("channel") },
                originalUrl = originalUrl,
                entries = entries,
            )
        }
    }
}

sealed interface AnalyzeResult {
    data class Video(val video: VideoInfo) : AnalyzeResult
    data class Playlist(val playlist: PlaylistInfo) : AnalyzeResult
}

/** Instant title/author/thumbnail from YouTube's oEmbed endpoint. */
data class YouTubePreview(val title: String, val author: String, val thumbnailUrl: String)

/**
 * A post whose media is picked from a gallery instead of handed to yt-dlp: TikTok photo posts,
 * X photo posts, Facebook / Instagram posts and stories. Items keep their on-site order.
 */
data class MediaGalleryPost(
    val source: String,
    val id: String,
    val author: String,
    val nickname: String = "",
    val title: String = "",
    val items: List<MediaGalleryItem>,
    val referer: String? = null,
) {
    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title.trim()
            nickname.isNotBlank() -> nickname
            else -> "@$author"
        }
}

/** One photo or video of a gallery post. */
class MediaGalleryItem(
    val index: Int,
    val isVideo: Boolean = false,
    /** Full-size media (CDN mirrors / quality fallbacks), best first. */
    val urls: MutableList<String>,
    /** Tile image for videos (and photos whose urls are resolved lazily). */
    val thumbnailUrls: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    /** Optional lazy lookup of the full-size URLs (Facebook album photos beyond the first few). */
    var resolveFullUrls: (suspend () -> List<String>)? = null,
) {
    val tileUrl: String? get() = thumbnailUrls.firstOrNull() ?: urls.firstOrNull()
    val sizeDisplay: String get() = if (width > 0 && height > 0) "$width×$height" else ""

    fun copyWithIndex(newIndex: Int) = MediaGalleryItem(newIndex, isVideo, urls, thumbnailUrls, width, height, resolveFullUrls)
}

/** Options collected from the "Download Options" panel (or the share defaults). */
data class DownloadRequest(
    val url: String,
    val kind: MediaKind,
    /** SAF tree URI of the target folder, or "" for the default Download/Y2Akio folder. */
    val saveFolder: String,
    val targetHeight: Int? = null,
    val containerFormat: String = "mp4",
    val videoCodec: String = "auto",
    val audioBitrateKbps: Int? = null,
    val audioFormat: String = "mp3",
    val prefixDateTime: Boolean = false,
    val prefixChannel: Boolean = false,
    val includeTitle: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val subtitleManual: Boolean = true,
    val subtitleAuto: Boolean = false,
    val subtitleLangCode: String = "all",
    val subtitleFormat: String = "srt",
    val embedSubtitles: Boolean = true,
    val embedMetadata: Boolean = true,
    /** "embed", "save" or "none". */
    val thumbnailMode: String = "embed",
    val qualityLabel: String = "",
    /** Known title (from analysis); empty = derived from the output file name. */
    val title: String = "",
)

/** One row in the "Download History" list. */
data class DownloadHistoryItem(
    val title: String,
    val kind: MediaKind,
    val qualityLabel: String,
    /** content:// URI of the saved file. */
    val fileUri: String,
    val mimeType: String,
    /** Human-readable location (e.g. "Download/Y2Akio/name.mp4"). */
    val displayPath: String,
    val folderLabel: String,
    val completedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("kind", kind.name)
        .put("quality", qualityLabel)
        .put("uri", fileUri)
        .put("mime", mimeType)
        .put("path", displayPath)
        .put("folder", folderLabel)
        .put("at", completedAt)

    companion object {
        fun fromJson(o: JSONObject) = DownloadHistoryItem(
            title = o.str("title"),
            kind = runCatching { MediaKind.valueOf(o.str("kind")) }.getOrDefault(MediaKind.Video),
            qualityLabel = o.str("quality"),
            fileUri = o.str("uri"),
            mimeType = o.str("mime"),
            displayPath = o.str("path"),
            folderLabel = o.str("folder"),
            completedAt = o.optLong("at"),
        )
    }
}
