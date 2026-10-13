package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.collectObjects
import com.example.y2akio_mobile.util.findObject
import com.example.y2akio_mobile.util.firstLine
import com.example.y2akio_mobile.util.int
import com.example.y2akio_mobile.util.obj
import com.example.y2akio_mobile.util.objects
import com.example.y2akio_mobile.util.parseJsonObject
import com.example.y2akio_mobile.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URLEncoder

/** Shared Instagram helpers: web API access and media-item parsing (stories, posts, carousels). */
object InstagramApi {
    const val REFERER = "https://www.instagram.com/"
    private const val WEB_APP_ID = "936619743392459" // instagram.com's own web client id, required by the web API

    val jsonScriptRegex = Regex("""<script type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    suspend fun getApi(url: String, cookieHeader: String): String {
        val builder = Request.Builder().url(url)
            .header("Cookie", cookieHeader)
            .header("X-IG-App-ID", WEB_APP_ID)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", REFERER)
        Regex("""(?:^|; )csrftoken=([^;]+)""").find(cookieHeader)?.let { builder.header("X-CSRFToken", it.groupValues[1]) }

        return MediaGalleryService.execute(builder.build()).use { r ->
            if (r.code == 429) throw IllegalStateException("Instagram is rate-limiting requests right now. Wait a few minutes and try again.")
            if (!r.isSuccessful) throw MediaGalleryService.HttpStatusException(r.code, "Instagram API error ${r.code}")
            withContext(Dispatchers.IO) { r.body.string() }
        }
    }

    /**
     * One Instagram media item (story item, post, or carousel child) → gallery item, or null when it
     * carries no downloadable URL. `media_type` 2 = video (`video_versions`), otherwise photo.
     */
    fun parseMediaItem(it: JSONObject, index: Int): MediaGalleryItem? {
        val images = it.obj("image_versions2").arr("candidates").objects()
            .filter { c -> c.str("url").isNotEmpty() }
            .sortedByDescending { c -> c.int("width") * c.int("height") }

        if (it.int("media_type") == 2) {
            val videos = it.arr("video_versions").objects()
                .filter { v -> v.str("url").isNotEmpty() }
                .sortedByDescending { v -> v.int("width") * v.int("height") }
                .map { v -> v.str("url") }.distinct()
            if (videos.isEmpty()) return null
            return MediaGalleryItem(index, isVideo = true, urls = videos.toMutableList(), thumbnailUrls = images.map { c -> c.str("url") })
        }

        if (images.isEmpty()) return null
        return MediaGalleryItem(
            index,
            urls = images.map { c -> c.str("url") }.toMutableList(),
            width = images[0].int("width"),
            height = images[0].int("height"),
        )
    }

    /** The element's id — sent as a string in some payloads and a number in others. */
    fun idOf(e: JSONObject?): String {
        if (e == null) return ""
        for (key in listOf("id", "pk")) {
            when (val v = e.opt(key)) {
                is String -> return v
                is Number -> return e.get(key).toString()
            }
        }
        return ""
    }

    fun loginRedirected(finalUrl: String): Boolean {
        val path = parseUrl(finalUrl)?.encodedPath ?: return false
        return path.startsWith("/accounts/login", true) || path.contains("challenge", true)
    }
}

/**
 * Instagram stories (`instagram.com/stories/<username>/`) and highlights
 * (`instagram.com/stories/highlights/<id>/`). Login-only. The story page embeds the reel in its
 * Relay payload; otherwise it comes from `/api/v1/feed/reels_media/?reel_ids=…`.
 */
class InstagramStoryService {
    companion object {
        fun isStoryUrl(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            return MediaGalleryService.isHostOf(u.host, "instagram.com") &&
                u.encodedPath.startsWith("/stories/", true) && u.segments.size >= 2
        }
    }

    suspend fun fetchStory(url: String, cookiesText: String?): MediaGalleryPost {
        val segments = parseUrl(url)!!.segments
        val isHighlight = segments[1].equals("highlights", true)
        if (isHighlight && segments.size < 3) throw IllegalStateException("This Instagram highlight link is missing its id.")
        val username = if (isHighlight) "" else segments[1]
        var reelId = if (isHighlight) "highlight:${segments[2]}" else ""

        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "instagram.com") }
        if (!cookieHeader.contains("sessionid="))
            throw IllegalStateException("Instagram stories are only visible when logged in. Import your browser's cookies.txt (with a logged-in Instagram session) in Settings.")

        val pageUrl = if (isHighlight) "https://www.instagram.com/stories/highlights/${segments[2]}/"
        else "https://www.instagram.com/stories/$username/"
        val (html, finalUrl) = MediaGalleryService.getPage(pageUrl, cookieHeader)
        if (InstagramApi.loginRedirected(finalUrl))
            throw IllegalStateException("Instagram sent this request to the login page — the imported cookies have expired. Re-export cookies.txt from a logged-in browser and import it in Settings.")

        var post = findReel(InstagramApi.jsonScriptRegex.findAll(html).map { it.groupValues[1] }.toList(), username, reelId)

        if (post == null) {
            if (!isHighlight) reelId = lookupUserId(username, cookieHeader)
            val json = InstagramApi.getApi(
                "https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=${URLEncoder.encode(reelId, "UTF-8")}", cookieHeader,
            )
            post = findReel(listOf(json), username, reelId)
        }

        if (post == null || post.items.isEmpty())
            throw IllegalStateException("No active stories found. Stories disappear after 24 hours, and private accounts are only visible if you follow them.")
        return post
    }

    private suspend fun lookupUserId(username: String, cookieHeader: String): String {
        val json = InstagramApi.getApi(
            "https://www.instagram.com/api/v1/users/web_profile_info/?username=${URLEncoder.encode(username, "UTF-8")}", cookieHeader,
        )
        val id = parseJsonObject(json).obj("data").obj("user").str("id")
        if (id.isNotEmpty()) return id
        throw IllegalStateException("Instagram user @$username wasn't found.")
    }

    private fun findReel(blobs: List<String>, username: String, reelId: String): MediaGalleryPost? {
        for (json in blobs) {
            if (!json.contains("reels_media")) continue
            val root = parseJsonObject(json) ?: continue

            val reels = collectObjects(root, 40) { it.opt("reels_media") is JSONArray }
                .flatMap { it.arr("reels_media").objects() }

            for (reel in reels) {
                val user = reel.obj("user")
                val reelUser = user.str("username")
                val isHighlight = reelId.startsWith("highlight:")
                val matches = if (reelId.isNotEmpty())
                    InstagramApi.idOf(reel) == reelId || (!isHighlight && InstagramApi.idOf(user) == reelId)
                else reelUser.equals(username, true)
                if (!matches) continue

                val items = mutableListOf<MediaGalleryItem>()
                for (it in reel.arr("items").objects()) InstagramApi.parseMediaItem(it, items.size + 1)?.let(items::add)
                if (items.isEmpty()) continue

                val author = reelUser.ifEmpty { username }
                return MediaGalleryPost(
                    source = if (isHighlight) "Instagram Highlight" else "Instagram Story",
                    id = if (isHighlight) reelId.removePrefix("highlight:") else InstagramApi.idOf(user).ifEmpty { InstagramApi.idOf(reel) },
                    author = author.ifEmpty { "instagram" },
                    nickname = user.str("full_name"),
                    title = reel.str("title"),
                    items = items,
                    referer = InstagramApi.REFERER,
                )
            }
        }
        return null
    }
}

/**
 * Photos (and videos) of an Instagram post (`instagram.com/p/<shortcode>/`), carousels included.
 * The post page embeds the media in its Relay payload; otherwise it comes from
 * `/api/v1/media/<mediaId>/info/` (media id decoded from the shortcode).
 */
class InstagramPostService {
    companion object {
        private const val SHORTCODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** True for `instagram.com/p/<shortcode>`. Reels stay with yt-dlp. */
        fun isPostUrl(url: String) = shortcodeOf(url) != null

        private fun shortcodeOf(url: String): String? {
            val u = parseUrl(url) ?: return null
            if (!MediaGalleryService.isHostOf(u.host, "instagram.com")) return null
            val segs = u.segments
            val i = segs.indexOfFirst { it.equals("p", true) }
            return if (i >= 0 && i + 1 < segs.size) segs[i + 1] else null
        }

        /** Shortcodes are the media id in base64url — private posts' longer codes carry extra trailing chars. */
        fun mediaIdFromShortcode(shortcode: String): String {
            val code = if (shortcode.length > 11) shortcode.substring(0, 11) else shortcode
            var id = BigInteger.ZERO
            for (ch in code) {
                val v = SHORTCODE_ALPHABET.indexOf(ch)
                if (v < 0) break
                id = id.multiply(BigInteger.valueOf(64)).add(BigInteger.valueOf(v.toLong()))
            }
            return id.toString()
        }
    }

    suspend fun fetchPost(url: String, cookiesText: String?): MediaGalleryPost {
        val shortcode = shortcodeOf(url) ?: throw IllegalStateException("This doesn't look like an Instagram post link.")
        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "instagram.com") }

        val (html, finalUrl) = MediaGalleryService.getPage("https://www.instagram.com/p/$shortcode/", cookieHeader)
        if (InstagramApi.loginRedirected(finalUrl)) {
            throw IllegalStateException(
                if (cookieHeader.contains("sessionid="))
                    "Instagram sent this request to the login page — the imported cookies have expired. Re-export cookies.txt from a logged-in browser and import it in Settings."
                else "Instagram only shows this post to logged-in users. Import your browser's cookies.txt (with a logged-in Instagram session) in Settings.",
            )
        }

        var item = findPostItem(InstagramApi.jsonScriptRegex.findAll(html).map { it.groupValues[1] }.toList(), shortcode)

        if (item == null && cookieHeader.contains("sessionid=")) {
            val json = InstagramApi.getApi("https://www.instagram.com/api/v1/media/${mediaIdFromShortcode(shortcode)}/info/", cookieHeader)
            item = findPostItem(listOf(json), shortcode)
        }

        return item ?: throw IllegalStateException(
            "No media found in this post. It may be deleted, or private (only visible if you follow the account — import logged-in cookies in Settings).",
        )
    }

    private fun findPostItem(blobs: List<String>, shortcode: String): MediaGalleryPost? {
        for (json in blobs) {
            if (!json.contains(shortcode) || !json.contains("image_versions2")) continue
            val root = parseJsonObject(json) ?: continue
            val media = findObject(root, 60) { it.str("code") == shortcode && it.has("image_versions2") } ?: continue

            val items = mutableListOf<MediaGalleryItem>()
            val carousel = media.arr("carousel_media")
            if (carousel != null) {
                for (child in carousel.objects()) InstagramApi.parseMediaItem(child, items.size + 1)?.let(items::add)
            } else {
                InstagramApi.parseMediaItem(media, 1)?.let(items::add)
            }
            if (items.isEmpty()) continue

            val user = media.obj("user")
            return MediaGalleryPost(
                source = "Instagram Post",
                id = shortcode,
                author = user.str("username").ifEmpty { "instagram" },
                nickname = user.str("full_name"),
                title = firstLine(media.obj("caption").str("text")),
                items = items,
                referer = InstagramApi.REFERER,
            )
        }
        return null
    }
}
