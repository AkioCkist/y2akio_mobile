package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.collectObjects
import com.example.y2akio_mobile.util.findProperty
import com.example.y2akio_mobile.util.firstLine
import com.example.y2akio_mobile.util.int
import com.example.y2akio_mobile.util.keyList
import com.example.y2akio_mobile.util.obj
import com.example.y2akio_mobile.util.objects
import com.example.y2akio_mobile.util.parseJsonObject
import com.example.y2akio_mobile.util.str
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Shared Facebook Relay-payload helpers (stories, posts, photo pages). */
object FacebookParse {
    val jsonScriptRegex = Regex("""<script type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    fun blobs(html: String): List<String> = jsonScriptRegex.findAll(html).map { it.groupValues[1] }.toList()

    /** Turns one attachment's media into a gallery item, or null when it carries no downloadable URL. */
    fun parseMedia(media: JSONObject, index: Int): MediaGalleryItem? {
        when (media.str("__typename")) {
            "Video" -> {
                val urls = mutableListOf<String>()
                // Progressive mp4s (audio muxed in), HD first.
                media.obj("videoDeliveryResponseFragment").obj("videoDeliveryResponseResult").arr("progressive_urls").objects()
                    .map { it.str("progressive_url") to (it.obj("metadata").str("quality") == "HD") }
                    .filter { it.first.isNotEmpty() }
                    .sortedByDescending { it.second }
                    .forEach { urls.add(it.first) }
                for (key in listOf("browser_native_hd_url", "playable_url_quality_hd", "browser_native_sd_url", "playable_url")) {
                    media.str(key).takeIf { it.isNotEmpty() }?.let(urls::add)
                }
                val distinct = urls.distinct()
                if (distinct.isEmpty()) return null
                return MediaGalleryItem(
                    index, isVideo = true, urls = distinct.toMutableList(),
                    thumbnailUrls = imageUris(media, "preferred_thumbnail.image", "previewImage", "image", "blurred_image"),
                )
            }
            "Photo" -> {
                val urls = imageUris(media, "viewer_image", "photo_image", "image", "previewImage")
                if (urls.isEmpty()) return null
                // Size of the largest rendition present (viewer_image is the full-size one).
                val sized = listOf("viewer_image", "photo_image", "image").mapNotNull { media.obj(it) }.firstOrNull { it.int("width") > 0 }
                return MediaGalleryItem(index, urls = urls.toMutableList(), width = sized.int("width"), height = sized.int("height"))
            }
        }
        return null
    }

    /** The `.uri` of each named (dot-path) image object that has one, in the given order. */
    fun imageUris(media: JSONObject, vararg paths: String): List<String> {
        val uris = mutableListOf<String>()
        for (path in paths) {
            var el: JSONObject? = media
            for (part in path.split('.')) el = el.obj(part)
            val uri = el.str("uri")
            if (uri.isNotEmpty() && uri !in uris) uris.add(uri)
        }
        return uris
    }

    /** "https://www.facebook.com/pvtd264" → "pvtd264"; profile.php?id=… → the id. */
    fun slugFromProfileUrl(url: String): String {
        val u = parseUrl(url) ?: return ""
        u.queryParameter("id")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }?.let { return it }
        val segs = u.segments
        return if (segs.isNotEmpty() && segs[0] != "profile.php") segs[0] else ""
    }

    fun loginRedirected(finalUrl: String): Boolean {
        val path = parseUrl(finalUrl)?.encodedPath ?: return false
        return path.contains("login", true) || path.contains("checkpoint", true)
    }

    /** www page URL for any facebook host (the mobile/basic hosts render a different page). */
    fun wwwUrl(url: String): String {
        val u = parseUrl(url)!!
        val query = u.encodedQuery?.let { "?$it" } ?: ""
        return "https://www.facebook.com${u.encodedPath}$query"
    }
}

/**
 * Facebook stories (`facebook.com/stories/<bucketId>/<storyId>/`). yt-dlp doesn't support story
 * links and stories are login-only, so the story page is fetched with the imported cookies and its
 * Relay payload parsed directly.
 */
class FacebookStoryService {
    companion object {
        fun isStoryUrl(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            return MediaGalleryService.isHostOf(u.host, "facebook.com") && u.encodedPath.startsWith("/stories/", true)
        }
    }

    suspend fun fetchStory(url: String, cookiesText: String?): MediaGalleryPost {
        val segments = parseUrl(url)!!.segments
        val bucketId = segments.getOrElse(1) { "" }
        val storyId = segments.getOrElse(2) { "" }

        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "facebook.com") }
        if (!cookieHeader.contains("c_user="))
            throw IllegalStateException("Facebook stories are only visible when logged in. Import your browser's cookies.txt (with a logged-in Facebook session) in Settings.")

        val (html, finalUrl) = MediaGalleryService.getPage(FacebookParse.wwwUrl(url), cookieHeader)
        if (FacebookParse.loginRedirected(finalUrl))
            throw IllegalStateException("Facebook sent this request to the login page — the imported cookies have expired. Re-export cookies.txt from a logged-in browser and import it in Settings.")

        val post = parseBucket(html, bucketId, storyId)
        if (post == null || post.items.isEmpty())
            throw IllegalStateException("No story media found. The story may have expired (stories last 24h), been deleted, or not be shared with your account.")
        return post
    }

    /**
     * Collects every populated story node of the target bucket across all JSON blobs on the page
     * (the same bucket shows up several times, often as id-only stubs).
     */
    private fun parseBucket(html: String, bucketId: String, storyId: String): MediaGalleryPost? {
        val items = mutableListOf<MediaGalleryItem>()
        val seenMedia = HashSet<String>()
        var ownerName = ""
        var ownerSlug = ""

        for (json in FacebookParse.blobs(html)) {
            if (!json.contains("unified_stories")) continue
            val root = parseJsonObject(json) ?: continue

            val buckets = collectObjects(root, 40) { it.has("unified_stories_with_notes") || it.has("unified_stories") }
            for (bucket in buckets) {
                val nodes = storyNodes(bucket)
                val isTarget = bucket.str("id") == bucketId || (storyId.isNotEmpty() && nodes.any { it.str("id") == storyId })
                if (!isTarget) continue

                if (ownerName.isEmpty()) {
                    bucket.obj("story_bucket_owner")?.let { owner ->
                        ownerName = owner.str("name")
                        ownerSlug = FacebookParse.slugFromProfileUrl(owner.str("url")).ifEmpty { owner.str("id") }
                    }
                }

                for (node in nodes) {
                    for (att in node.arr("attachments").objects()) {
                        val media = att.obj("media") ?: continue
                        val mediaId = media.str("id")
                        if (mediaId.isNotEmpty() && mediaId in seenMedia) continue
                        val item = FacebookParse.parseMedia(media, items.size + 1) ?: continue // id-only stub
                        if (mediaId.isNotEmpty()) seenMedia.add(mediaId)
                        items.add(item)
                    }
                }
            }
        }

        if (items.isEmpty()) return null
        return MediaGalleryPost(
            source = "Facebook Story",
            id = bucketId,
            author = ownerSlug.ifBlank { "facebook" },
            title = ownerName,
            items = items,
        )
    }

    private fun storyNodes(bucket: JSONObject): List<JSONObject> =
        listOf("unified_stories_with_notes", "unified_stories").flatMap { key ->
            bucket.obj(key).arr("edges").objects().mapNotNull { it.obj("node") }
        }
}

/**
 * Photos of a Facebook post (share/p links, permalinks, /posts/, group posts, single /photo pages).
 * Albums only embed their first few photos at full size, so the full list comes from the album
 * page and each missing photo's full-size image is looked up lazily from its own photo page.
 */
class FacebookPostService {
    companion object {
        /** True for Facebook post / photo links. Video and reel links are left to yt-dlp. */
        fun isPostUrl(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            if (!MediaGalleryService.isHostOf(u.host, "facebook.com")) return false
            val path = u.encodedPath.lowercase()
            return path.startsWith("/share/p/") ||
                path.startsWith("/permalink.php") ||
                path.startsWith("/story.php") ||
                path.contains("/posts/") ||
                (path.startsWith("/groups/") && path.contains("/permalink/")) ||
                path.startsWith("/photo") ||
                path.contains("/photos/")
        }
    }

    private class Entry(val mediaId: String, val item: MediaGalleryItem)

    private class ParsedPost {
        var id = ""
        var author = ""
        var name = ""
        var message = ""
        var mediasetToken = ""
        var totalCount = 0
        var items = mutableListOf<Entry>()

        fun toGalleryPost() = MediaGalleryPost(
            source = "Facebook Post",
            id = id,
            author = author.ifBlank { "facebook" },
            nickname = name,
            title = firstLine(message).ifEmpty { name },
            items = items.mapIndexed { i, e -> e.item.copyWithIndex(i + 1) },
        )
    }

    /** The post's photos, or null when it has none (video posts etc. are left to yt-dlp). */
    suspend fun fetchPost(url: String, cookiesText: String?): MediaGalleryPost? {
        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "facebook.com") }

        val (html, finalUrl) = MediaGalleryService.getPage(FacebookParse.wwwUrl(url), cookieHeader)
        if (FacebookParse.loginRedirected(finalUrl)) {
            throw IllegalStateException(
                if (cookieHeader.isEmpty())
                    "Facebook only shows this post to logged-in users. Import your browser's cookies.txt (with a logged-in Facebook session) in Settings."
                else "Facebook sent this request to the login page — the imported cookies have expired. Re-export cookies.txt from a logged-in browser and import it in Settings.",
            )
        }

        val blobs = FacebookParse.blobs(html)
        val finalPath = parseUrl(finalUrl)?.encodedPath?.lowercase() ?: ""
        if (finalPath.startsWith("/photo") || finalPath.contains("/photos/")) {
            parsePhotoPage(blobs, finalUrl)?.let { return it }
        }

        val post = parsePost(blobs) ?: return null
        if (post.mediasetToken.isNotEmpty() && post.totalCount > post.items.size) addAlbumRest(post, post.mediasetToken, cookieHeader)
        return post.toGalleryPost()
    }

    /** The requested post is the page's first story — later blobs are the news feed / sidebar. */
    private fun parsePost(blobs: List<String>): ParsedPost? {
        for (json in blobs) {
            if (!json.contains("\"attachments\"")) continue
            val root = parseJsonObject(json) ?: continue
            val stories = collectObjects(root, 60) { it.opt("attachments") is JSONArray && it.opt("actors") is JSONArray }
            if (stories.isEmpty()) continue
            for (story in stories) parseStory(story)?.let { return it }
            return null
        }
        return null
    }

    private fun parseStory(story: JSONObject): ParsedPost? {
        val post = ParsedPost()
        for (att in story.arr("attachments").objects()) {
            val attachment = att.obj("styles").obj("attachment") ?: continue

            val album = findAlbumSubattachments(attachment)
            if (album != null) {
                post.mediasetToken = attachment.str("mediaset_token")
                post.totalCount = album.first
                post.items.addAll(parseSubattachmentNodes(album.second, post.items.size))
            } else {
                val media = attachment.obj("media") ?: continue
                FacebookParse.parseMedia(media, post.items.size + 1)?.let { post.items.add(Entry(media.str("id"), it)) }
            }
        }

        // A lone video attachment is a video post — yt-dlp handles those with full quality options.
        if (post.mediasetToken.isEmpty() && post.items.none { !it.item.isVideo }) return null

        post.id = story.str("post_id")
        val captionHolder = if (!story.has("message")) story.obj("comet_sections").obj("content").obj("story") ?: story else story
        post.message = captionHolder.obj("message").str("text")

        story.arr("actors").objects().firstOrNull()?.let { actor ->
            post.name = actor.str("name")
            post.author = FacebookParse.slugFromProfileUrl(actor.str("url")).ifEmpty { actor.str("id") }
        }
        if (post.id.isEmpty() && post.mediasetToken.startsWith("pcb.")) post.id = post.mediasetToken.removePrefix("pcb.")
        return post
    }

    /** An album attachment's total photo count and the subattachments holding its embedded photos. */
    private fun findAlbumSubattachments(attachment: JSONObject): Pair<Int, JSONObject>? {
        attachment.obj("all_subattachments")?.let { return it.int("count") to it }
        val frame = attachment.obj("frame_sublayout_subattachments") ?: return null

        var best = frame
        var bestCount = parseSubattachmentNodes(frame, 0).size
        for (key in attachment.keyList()) {
            if (!key.endsWith("_subattachments")) continue
            val value = attachment.obj(key) ?: continue
            val parsed = parseSubattachmentNodes(value, 0).size
            if (parsed > bestCount) {
                best = value
                bestCount = parsed
            }
        }
        return frame.int("count") to best
    }

    private fun parseSubattachmentNodes(subs: JSONObject, startCount: Int): List<Entry> {
        val items = mutableListOf<Entry>()
        for (node in subs.arr("nodes").objects()) {
            val media = node.obj("media") ?: continue
            FacebookParse.parseMedia(media, startCount + items.size + 1)?.let { items.add(Entry(media.str("id"), it)) }
        }
        return items
    }

    /** Loads the album page for the full photo list (in album order); missing photos resolve lazily. */
    private suspend fun addAlbumRest(post: ParsedPost, token: String, cookieHeader: String) {
        val html = try {
            MediaGalleryService.getPage("https://www.facebook.com/media/set/?set=${URLEncoder.encode(token, "UTF-8")}&type=3", cookieHeader).first
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return // keep what the post page had
        }

        val known = post.items.associateBy { it.mediaId }
        val ordered = mutableListOf<Entry>()
        val seen = HashSet<String>()

        for (json in FacebookParse.blobs(html)) {
            if (!json.contains("\"album\"")) continue
            val root = parseJsonObject(json) ?: continue
            val nodes = collectObjects(root, 60) { it.obj("album").obj("media").arr("edges") != null }
                .flatMap { it.obj("album").obj("media").arr("edges").objects().mapNotNull { e -> e.obj("node") } }

            for (node in nodes) {
                val id = node.str("id")
                if (id.isEmpty() || !seen.add(id)) continue
                known[id]?.let { ordered.add(it); continue }
                if (node.str("__typename") != "Photo") continue // album videos: open them as normal links
                val preview = FacebookParse.imageUris(node, "image")
                if (preview.isEmpty()) continue
                ordered.add(
                    Entry(
                        id,
                        MediaGalleryItem(
                            index = 0,
                            urls = preview.toMutableList(),
                            thumbnailUrls = preview,
                            resolveFullUrls = { resolvePhoto(id, token, cookieHeader) },
                        ),
                    ),
                )
            }
        }

        for (e in post.items) if (e.mediaId !in seen) ordered.add(e)
        if (ordered.size >= post.items.size) post.items = ordered
    }

    /** Full-size image URL of one photo, read from its photo page. */
    private suspend fun resolvePhoto(photoId: String, setToken: String, cookieHeader: String): List<String> {
        val (html, _) = MediaGalleryService.getPage(
            "https://www.facebook.com/photo/?fbid=$photoId&set=${URLEncoder.encode(setToken, "UTF-8")}", cookieHeader,
        )
        val media = findCurrMedia(FacebookParse.blobs(html), photoId) ?: return emptyList()
        return FacebookParse.imageUris(media, "image", "viewer_image", "photo_image")
    }

    /** A /photo page opened directly: one item, the page's current photo. */
    private fun parsePhotoPage(blobs: List<String>, finalUrl: String): MediaGalleryPost? {
        val photoId = parseUrl(finalUrl)?.queryParameter("fbid") ?: ""
        val media = findCurrMedia(blobs, photoId) ?: return null
        val urls = FacebookParse.imageUris(media, "image", "viewer_image", "photo_image")
        if (urls.isEmpty()) return null

        val owner = media.obj("owner")
        val size = media.obj("image")
        return MediaGalleryPost(
            source = "Facebook Photo",
            id = media.str("id"),
            author = owner.str("id").ifEmpty { "facebook" },
            nickname = owner.str("name"),
            items = listOf(MediaGalleryItem(1, urls = urls.toMutableList(), width = size.int("width"), height = size.int("height"))),
        )
    }

    private fun findCurrMedia(blobs: List<String>, photoId: String): JSONObject? {
        for (json in blobs) {
            if (!json.contains("\"currMedia\"")) continue
            val root = parseJsonObject(json) ?: continue
            val media = findProperty(root, "currMedia", 60) ?: continue
            if (photoId.isEmpty() || media.str("id") == photoId) return media
        }
        return null
    }
}
