package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.findObject
import com.example.y2akio_mobile.util.int
import com.example.y2akio_mobile.util.obj
import com.example.y2akio_mobile.util.objects
import com.example.y2akio_mobile.util.parseJsonObject
import com.example.y2akio_mobile.util.str
import com.example.y2akio_mobile.util.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder

/**
 * TikTok photo posts ("photo mode" / slideshows, `tiktok.com/@user/photo/<id>`). yt-dlp only
 * surfaces the background audio of these posts, never the images, so they are scraped directly:
 * the post page embeds its item JSON in `__UNIVERSAL_DATA_FOR_REHYDRATION__`, whose
 * `imagePost.images[].imageURL.urlList` holds signed CDN links to the full-size images.
 */
class TikTokPhotoService(
    /**
     * Real-browser page loader for when TikTok's WAF / rate limiter turns OkHttp away:
     * (url, script ids, accept-predicate, cookies.txt text) → the first accepted `<script id=…>`
     * text, from an off-screen WebView (see [WebPageFetcher]). Null = OkHttp only.
     */
    private val browserFallback: (suspend (String, List<String>, (String) -> Boolean, String?) -> String?)? = null,
) {
    companion object {
        const val REFERER = "https://www.tiktok.com/"

        private val rehydrationRegex = Regex(
            """<script[^>]+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL,
        )
        private val sigiRegex = Regex("""<script[^>]+id="SIGI_STATE"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        private val frontityRegex = Regex(
            """<script[^>]+id="__FRONTITY_CONNECT_STATE__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL,
        )
        private val postIdRegex = Regex("""/(?:photo|video)/(\d+)""")

        /** True for a direct `tiktok.com/.../photo/<id>` link. */
        fun isPhotoUrl(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            return MediaGalleryService.isHostOf(u.host, "tiktok.com") && u.encodedPath.contains("/photo/", ignoreCase = true)
        }

        /** True for share short links (vt./vm.tiktok.com, tiktok.com/t/...) whose target is unknown until resolved. */
        fun isShortLink(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            return u.host.equals("vt.tiktok.com", true) || u.host.equals("vm.tiktok.com", true) ||
                (MediaGalleryService.isHostOf(u.host, "tiktok.com") && u.encodedPath.startsWith("/t/", ignoreCase = true))
        }
    }

    /**
     * Loads the post's metadata + image list. The `/photo/` page itself ships without the item
     * data, but the same ID under `/video/` renders the full item, so that form is tried first.
     */
    suspend fun fetchPost(url: String, cookiesText: String?): MediaGalleryPost {
        val postId = postIdRegex.find(url)?.groupValues?.get(1)
            ?: throw IllegalStateException("This doesn't look like a TikTok photo post link.")
        val user = extractUser(url)
        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "tiktok.com") }

        val candidates = mutableListOf("https://www.tiktok.com/@$user/video/$postId")
        if (url !in candidates) candidates.add(url)

        val loggedIn = cookieHeader.contains("sessionid=")
        var lastError = if (loggedIn)
            "TikTok returned no data for this post. It may be deleted, region-locked, or private / friends-only and not visible to the account in your imported cookies."
        else
            "TikTok returned no data for this post. If it's private or friends-only, import your browser's cookies.txt (with a logged-in TikTok session that can see it) in Settings."

        // The embed player ships the full image list and isn't behind the WAF that guards the
        // regular post pages (they answer non-browser clients with a JS challenge).
        tryEmbed(postId, user)?.let { return it }
        browserFallback?.invoke(embedUrl(postId), listOf("__FRONTITY_CONNECT_STATE__"), { it.contains("imagePostInfo") || it.contains("\"code\"") }, null)
            ?.let { parseEmbed(it, postId, user) }
            ?.let { return it }

        for (candidate in candidates) {
            try {
                var (html, _) = MediaGalleryService.getPage(candidate, cookieHeader)
                if (isBotChallenge(html)) {
                    val script = browserFallback?.invoke(
                        candidate,
                        listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE"),
                        // Wait for the item itself (or TikTok's status for it), not the page shell.
                        { it.contains("\"imagePost\"") || it.contains("webapp.video-detail") },
                        cookiesText,
                    )
                    if (script == null) {
                        lastError = "TikTok answered with its bot-check page instead of the post. Try again in a moment."
                        continue
                    }
                    // Re-wrap so the normal page parser reads it.
                    html = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">$script</script>"
                }
                val item = findItemStruct(html) ?: continue
                val post = parsePost(item, postId, user)
                if (post.items.isNotEmpty()) return post
                lastError = "This TikTok post has no photos (it's a regular video — paste it as a normal link)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: TikTokStatusException) {
                lastError = e.describe(loggedIn)
            } catch (e: Exception) {
                lastError = e.message ?: lastError
            }
        }
        throw IllegalStateException(lastError)
    }

    /**
     * `tiktok.com/embed/v2/<id>`: `__FRONTITY_CONNECT_STATE__` → `videoData` with `itemInfos`,
     * `authorInfos` and `imagePostInfo.displayImages[]` (full-size, signed CDN links). Public posts
     * only — anything else returns null and falls through to the post page.
     */
    private suspend fun tryEmbed(postId: String, userFromUrl: String): MediaGalleryPost? {
        // TikTok's rate limiter answers bursts with 429 / 503 (and sometimes a 4xx that still
        // carries the full page), with a window of tens of seconds: back off progressively.
        for (attempt in 0 until 5) {
            try {
                val (code, html) = MediaGalleryService.execute(
                    okhttp3.Request.Builder().url(embedUrl(postId)).header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8").build(),
                ).use { r -> r.code to withContext(Dispatchers.IO) { r.body.string() } }
                if (html.contains("__FRONTITY_CONNECT_STATE__")) {
                    val script = frontityRegex.find(html)?.groupValues?.get(1) ?: return null
                    return parseEmbed(script, postId, userFromUrl)
                }
                if (code != 429 && code !in 500..599) return null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // network hiccup: retry
            }
            if (attempt < 4) delay(2000L * (attempt + 1))
        }
        return null
    }

    private fun embedUrl(postId: String) = "https://www.tiktok.com/embed/v2/$postId"

    /** Embed page data (`__FRONTITY_CONNECT_STATE__` JSON) → post, or null when it has no photos. */
    private fun parseEmbed(script: String, postId: String, userFromUrl: String): MediaGalleryPost? {
        val root = parseJsonObject(script) ?: return null
        val data = findObject(root, 10) { it.opt("imagePostInfo") is JSONObject } ?: return null

        val images = mutableListOf<MediaGalleryItem>()
        for (img in data.obj("imagePostInfo").arr("displayImages").objects()) {
            val urls = img.arr("urlList").strings()
                .sortedBy { if (it.contains(".jpeg", true) || it.contains(".jpg", true)) 0 else 1 }
            if (urls.isEmpty()) continue
            images.add(MediaGalleryItem(images.size + 1, urls = urls.toMutableList(), width = img.int("width"), height = img.int("height")))
        }
        if (images.isEmpty()) return null

        val author = data.obj("authorInfos")
        return MediaGalleryPost(
            source = "TikTok",
            id = data.obj("itemInfos").str("id").ifEmpty { postId },
            author = author.str("uniqueId").ifBlank { userFromUrl.ifBlank { "tiktok" } },
            nickname = author.str("nickName"),
            title = data.obj("itemInfos").str("text").trim(),
            items = images,
            referer = REFERER,
        )
    }

    /** TikTok's WAF challenge: a stub page with none of the page-data scripts. */
    private fun isBotChallenge(html: String) = !rehydrationRegex.containsMatchIn(html) && !sigiRegex.containsMatchIn(html)

    /** Pulls the item object (the first one carrying an `imagePost`) out of either page-data format. */
    private fun findItemStruct(html: String): JSONObject? {
        for (regex in listOf(rehydrationRegex, sigiRegex)) {
            val match = regex.find(html) ?: continue
            val root = parseJsonObject(match.groupValues[1]) ?: continue
            findObject(root, 12) { it.opt("imagePost") is JSONObject }?.let { return it }

            val detail = root.obj("__DEFAULT_SCOPE__").obj("webapp.video-detail")
            val code = detail.int("statusCode")
            if (detail != null && code != 0) throw TikTokStatusException(code, detail.str("statusMsg"))
        }
        return null
    }

    private fun parsePost(item: JSONObject, postId: String, userFromUrl: String): MediaGalleryPost {
        var author = userFromUrl
        var nickname = ""
        when (val a = item.opt("author")) {
            is JSONObject -> {
                author = a.str("uniqueId").ifEmpty { author }
                nickname = a.str("nickname")
            }
            is String -> author = a // SIGI_STATE stores the handle directly
        }

        val imagePost = item.obj("imagePost")
        val title = imagePost.str("title")
        val desc = item.str("desc")

        val images = mutableListOf<MediaGalleryItem>()
        for (img in imagePost.arr("images").objects()) {
            // JPEG mirrors first: they decode everywhere (HEIC needs a codec on some devices).
            val urls = img.obj("imageURL").arr("urlList").strings()
                .sortedBy { if (it.contains(".jpeg", true) || it.contains(".jpg", true)) 0 else 1 }
            if (urls.isEmpty()) continue
            images.add(
                MediaGalleryItem(
                    index = images.size + 1,
                    urls = urls.toMutableList(),
                    width = img.int("imageWidth"),
                    height = img.int("imageHeight"),
                ),
            )
        }

        return MediaGalleryPost(
            source = "TikTok",
            id = item.str("id").ifEmpty { postId },
            author = author.ifBlank { "tiktok" },
            nickname = nickname,
            title = title.ifBlank { desc },
            items = images,
            referer = REFERER,
        )
    }

    private fun extractUser(url: String): String {
        val m = Regex("""/@([^/?#]+)""").find(url) ?: return "tiktok"
        return URLDecoder.decode(m.groupValues[1], "UTF-8")
    }
}

/** TikTok's own non-zero `statusCode` for an item (private, deleted, ...), turned into a readable message. */
class TikTokStatusException(val code: Int, private val statusMessage: String?) :
    Exception("TikTok refused the request (status $code${if (statusMessage.isNullOrBlank()) "" else ": $statusMessage"}).") {

    fun describe(loggedIn: Boolean): String = when {
        (code == 10216 || code == 10222) && loggedIn ->
            "This TikTok post is private or friends-only, and the account in your imported cookies isn't allowed to see it."
        code == 10216 || code == 10222 ->
            "This TikTok post is private or friends-only. Import your browser's cookies.txt (with a logged-in TikTok session that can see it) in Settings."
        code == 10204 -> "This TikTok post doesn't exist anymore (deleted, or the link is wrong)."
        else -> message ?: "TikTok error $code"
    }
}
