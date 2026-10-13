package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import com.example.y2akio_mobile.util.arr
import com.example.y2akio_mobile.util.firstLine
import com.example.y2akio_mobile.util.int
import com.example.y2akio_mobile.util.obj
import com.example.y2akio_mobile.util.objects
import com.example.y2akio_mobile.util.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.floor

/**
 * Photos of an X (Twitter) post (`x.com/<user>/status/<id>`). yt-dlp's twitter extractor only
 * handles video, so photo posts are fetched here: first the public embed API
 * (`cdn.syndication.twimg.com/tweet-result`), then — for posts it won't serve — X's own web
 * GraphQL query `TweetResultByRestId` with the imported cookies (`auth_token` + `ct0`).
 * Video-only posts return null so the caller hands them to yt-dlp (full format choice).
 */
class XPostService {
    companion object {
        const val REFERER = "https://x.com/"

        // Public bearer token of X's own web client — the same one every browser session sends.
        private const val WEB_BEARER =
            "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

        private val statusRegex = Regex("""/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)

        // Last known TweetResultByRestId operation — refreshed from X's bundles when it goes stale.
        @Volatile private var queryId = "vEO-5QkqZwFZlJZoNTs6fA"
        @Volatile private var providedVariables = listOf(
            "__relay_internal__pv__xclientsfeaturesappviewerisloggedinrelayprovider",
            "__relay_internal__pv__xclientsfeaturestranslationnoteautotranslationenabledrelayprovider",
        )

        fun isPostUrl(url: String): Boolean {
            val u = parseUrl(url) ?: return false
            return (MediaGalleryService.isHostOf(u.host, "x.com") || MediaGalleryService.isHostOf(u.host, "twitter.com")) &&
                statusRegex.containsMatchIn(u.encodedPath)
        }

        /** The embed API's token: (id / 1e15 · π) in base 36, zeros and the dot removed. */
        fun syndicationToken(tweetId: String): String {
            val x = tweetId.toDouble() / 1e15 * PI
            val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
            var intPart = floor(x).toLong()
            var frac = x - intPart
            val sb = StringBuilder()
            do {
                sb.insert(0, digits[(intPart % 36).toInt()]); intPart /= 36
            } while (intPart > 0)
            sb.append('.')
            var i = 0
            while (i < 12 && frac > 0) {
                frac *= 36
                val d = floor(frac).toInt()
                sb.append(digits[d])
                frac -= d
                i++
            }
            return sb.toString().replace(Regex("(0+|\\.)"), "")
        }
    }

    private data class TweetMedia(val type: String, val url: String, val width: Int, val height: Int, val videoUrls: List<String>)
    private data class Tweet(val screenName: String, val name: String, val text: String, val media: List<TweetMedia>)

    /** The post's photos (plus any videos mixed in), or null for a video-only post (left to yt-dlp). */
    suspend fun fetchPost(url: String, cookiesText: String?): MediaGalleryPost? {
        val u = parseUrl(url) ?: throw IllegalStateException("Invalid X link.")
        val tweetId = statusRegex.find(u.encodedPath)!!.groupValues[1]

        trySyndication(tweetId)?.let { return toPost(it, tweetId) }

        val cookieHeader = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "x.com") }
        if (!cookieHeader.contains("auth_token=") || !cookieHeader.contains("ct0=")) {
            throw IllegalStateException(
                "X didn't serve this post publicly (protected account, age-restricted, or deleted). If you can see it while logged in, import your browser's cookies.txt (with a logged-in X session) in Settings.",
            )
        }
        val tweet = fetchGraphQl(tweetId, cookieHeader)
            ?: throw IllegalStateException("X returned no data for this post. It may be deleted, or from a protected account your imported cookies' account doesn't follow.")
        return toPost(tweet, tweetId)
    }

    private fun toPost(tweet: Tweet, tweetId: String): MediaGalleryPost? {
        val items = mutableListOf<MediaGalleryItem>()
        for (m in tweet.media) {
            if (m.type == "photo") {
                items.add(MediaGalleryItem(index = items.size + 1, urls = originalPhotoUrls(m.url).toMutableList(), width = m.width, height = m.height))
            } else if (m.videoUrls.isNotEmpty()) {
                items.add(MediaGalleryItem(index = items.size + 1, isVideo = true, urls = m.videoUrls.toMutableList(), thumbnailUrls = listOf(m.url)))
            }
        }
        if (items.none { !it.isVideo }) return null // video-only → yt-dlp

        return MediaGalleryPost(
            source = "X Post",
            id = tweetId,
            author = tweet.screenName.ifBlank { "x" },
            nickname = tweet.name,
            title = firstLine(tweet.text),
            items = items,
            referer = REFERER,
        )
    }

    /** "https://pbs.twimg.com/media/ABC.jpg" → original-quality URL first, the plain URL as fallback. */
    private fun originalPhotoUrls(url: String): List<String> {
        val m = Regex("""^(https://pbs\.twimg\.com/media/[^.?]+)\.(\w+)""").find(url) ?: return listOf(url)
        return listOf("${m.groupValues[1]}?format=${m.groupValues[2]}&name=orig", url)
    }

    private suspend fun trySyndication(tweetId: String): Tweet? {
        try {
            val url = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en&token=${syndicationToken(tweetId)}"
            val body = MediaGalleryService.execute(Request.Builder().url(url).build()).use { response ->
                if (!response.isSuccessful) return null
                withContext(Dispatchers.IO) { response.body.string() }
            }
            val root = JSONObject(body)
            val type = root.str("__typename")
            if (type == "TweetTombstone" || type == "TweetUnavailable") return null
            val details = root.arr("mediaDetails") ?: return null
            val user = root.obj("user")
            return Tweet(user.str("screen_name"), user.str("name"), stripTrailingLink(root.str("text")), details.objects().map(::parseMediaEntity))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null // fall through to the logged-in API
        }
    }

    private suspend fun fetchGraphQl(tweetId: String, cookieHeader: String): Tweet? {
        var (status, json) = queryTweet(tweetId, cookieHeader)
        // 404 / validation error = the persisted query id rotated: re-discover it and retry once.
        if (status == 400 || status == 404 || status == 422) {
            if (discoverQuery()) queryTweet(tweetId, cookieHeader).let { status = it.first; json = it.second }
        }
        if (status == 401 || status == 403)
            throw IllegalStateException("X rejected the imported cookies (logged out or expired). Re-export cookies.txt from a logged-in browser and import it in Settings.")
        if (status == 429) throw IllegalStateException("X is rate-limiting requests right now. Wait a few minutes and try again.")
        if (status != 200) return null

        var result = JSONObject(json).obj("data").obj("tweet_result_by_rest_id").obj("result") ?: return null
        // "TweetWithVisibilityResults" wraps the tweet for limited-visibility posts.
        result.obj("tweet")?.let { result = it }
        val type = result.str("__typename")
        if (type == "TweetTombstone" || type == "TweetUnavailable") return null

        val media = mutableListOf<TweetMedia>()
        for (key in listOf("media_entities2", "media_entities")) {
            val arr = result.arr(key) ?: continue
            media.addAll(arr.objects().map(::parseMediaEntity))
            break
        }
        if (media.isEmpty()) {
            media.addAll(result.obj("legacy").obj("extended_entities").arr("media").objects().map(::parseMediaEntity))
        }

        val user = result.obj("core").obj("user_results").obj("result")
        val names = user.obj("core") ?: user.obj("legacy")
        val text = result.obj("details")?.str("full_text") ?: result.obj("legacy").str("full_text")
        return Tweet(names.str("screen_name"), names.str("name"), stripTrailingLink(text), media)
    }

    private suspend fun queryTweet(tweetId: String, cookieHeader: String): Pair<Int, String> {
        val variables = JSONObject().put("restId", tweetId).put("withFocalTweet", true)
        for (pv in providedVariables) variables.put(pv, pv.contains("loggedin", ignoreCase = true))

        val url = "https://x.com/i/api/graphql/$queryId/TweetResultByRestId?variables=" +
            URLEncoder.encode(variables.toString(), "UTF-8").replace("+", "%20")
        val builder = Request.Builder().url(url)
            .header("Authorization", "Bearer $WEB_BEARER")
            .header("Cookie", cookieHeader)
            .header("x-twitter-auth-type", "OAuth2Session")
            .header("x-twitter-active-user", "yes")
            .header("Referer", REFERER)
        Regex("""(?:^|; )ct0=([^;]+)""").find(cookieHeader)?.let { builder.header("x-csrf-token", it.groupValues[1]) }

        return MediaGalleryService.execute(builder.build()).use { r -> r.code to withContext(Dispatchers.IO) { r.body.string() } }
    }

    /** Finds the current `TweetResultByRestId` persisted query id by walking X's web bundles (bounded BFS). */
    private suspend fun discoverQuery(): Boolean {
        try {
            val (home, _) = MediaGalleryService.getPage("https://x.com/", "")
            val likely = ArrayDeque<String>()
            val other = ArrayDeque(Regex("""https://abs\.twimg\.com/[^"'\s]+\.js""").findAll(home).map { it.value }.toList())
            val seen = HashSet<String>()

            while ((likely.isNotEmpty() || other.isNotEmpty()) && seen.size < 200) {
                val scriptUrl = likely.removeFirstOrNull() ?: other.removeFirst()
                if (!seen.add(scriptUrl)) continue

                val js = try {
                    MediaGalleryService.getString(scriptUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }

                val op = Regex("""params:\{id:`([^`]+)`,metadata:\{\},name:`TweetResultByRestId`[^}]*?providedVariables:\{([^}]*)\}""").find(js)
                if (op != null) {
                    queryId = op.groupValues[1]
                    providedVariables = Regex("""__relay_internal__pv__\w+""").findAll(op.groupValues[2]).map { it.value }.distinct().toList()
                    return true
                }

                val dir = scriptUrl.substring(0, scriptUrl.lastIndexOf('/') + 1)
                for (imp in Regex("""["'`](\./[\w./-]+\.js|https://abs\.twimg\.com/[^"'`\s]+\.js)["'`]""").findAll(js)) {
                    val v = imp.groupValues[1]
                    val next = if (v.startsWith("./")) dir + v.substring(2) else v
                    if (next in seen) continue
                    val name = next.substringAfterLast('/')
                    if (Regex("tweet|status|article|conversation", RegexOption.IGNORE_CASE).containsMatchIn(name)) likely.add(next) else other.add(next)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // keep the old id
        }
        return false
    }

    private fun parseMediaEntity(m: JSONObject): TweetMedia {
        val info = m.obj("original_info")
        val videos = m.obj("video_info").arr("variants").objects()
            .filter { it.str("content_type") == "video/mp4" && it.str("url").isNotEmpty() }
            .sortedByDescending { it.int("bitrate") }
            .map { it.str("url") }
        return TweetMedia(m.str("type"), m.str("media_url_https"), info.int("width"), info.int("height"), videos)
    }

    /** Drops the trailing t.co link X appends for attached media. */
    private fun stripTrailingLink(text: String) = text.replace(Regex("""\s*https://t\.co/\w+\s*$"""), "").trim()
}
