package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryItem
import com.example.y2akio_mobile.model.MediaGalleryPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Shared plumbing for gallery posts: one browser-like HTTP client, media downloads with mirror
 * fallback, cookie-header building from a Netscape cookies.txt, and file naming / zipping.
 */
object MediaGalleryService {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    // No cookie jar: each scraper sends its site's cookies as a hand-built Cookie header, so the
    // imported jar never leaks to CDN hosts or other sites.
    val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            val req = chain.request()
            val builder = req.newBuilder()
            if (req.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT)
            if (req.header("Accept-Language") == null) builder.header("Accept-Language", "en-US,en;q=0.9")
            chain.proceed(builder.build())
        }
        .build()

    class HttpStatusException(val code: Int, message: String) : IOException(message)

    /** Executes a request off the main thread; the response must be closed by the caller. */
    suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) { http.newCall(request).execute() }

    /**
     * Headers of a top-level browser navigation. Facebook answers share links with HTTP 400 when
     * the Sec-Fetch-* / Upgrade-Insecure-Requests set is incomplete.
     */
    private fun Request.Builder.navigationHeaders(): Request.Builder = this
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1")
        .header("Upgrade-Insecure-Requests", "1")

    private val noRedirectHttp: OkHttpClient by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    /**
     * Follows a share link's redirect chain hop by hop and returns the last http(s) URL. Done
     * manually (not via OkHttp's auto-follow) so the chain survives a final hop that fails or
     * points at an app scheme (`snssdk1233://`, `fb://`), and so a login wall's `next=` target
     * can be recovered.
     */
    suspend fun resolveRedirects(url: String, cookieHeader: String = "", maxHops: Int = 10): String {
        var current = url
        repeat(maxHops) {
            val builder = Request.Builder().url(current)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .navigationHeaders()
            if (cookieHeader.isNotEmpty()) builder.header("Cookie", cookieHeader)
            val next = try {
                withContext(Dispatchers.IO) {
                    noRedirectHttp.newCall(builder.build()).execute().use { r ->
                        if (r.isRedirect) r.header("Location")?.let { r.request.url.resolve(it)?.toString() } else null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return unwrapLogin(current)
            current = next
        }
        return unwrapLogin(current)
    }

    /** "…/login/?next=<target>" → target (anonymous Facebook / Instagram share links land there). */
    private fun unwrapLogin(url: String): String {
        val u = parseUrl(url) ?: return url
        if (!u.encodedPath.contains("login", ignoreCase = true)) return url
        val next = u.queryParameter("next") ?: return url
        return if (parseUrl(next) != null) next else url
    }

    /** GETs a page as a browser navigation would. Returns the body and the final (post-redirect) URL. */
    suspend fun getPage(url: String, cookieHeader: String): Pair<String, String> {
        val builder = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .navigationHeaders()
        if (cookieHeader.isNotEmpty()) builder.header("Cookie", cookieHeader)
        return execute(builder.build()).use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "HTTP ${response.code} for $url")
            val body = withContext(Dispatchers.IO) { response.body.string() }
            body to response.request.url.toString()
        }
    }

    suspend fun getString(url: String): String = execute(Request.Builder().url(url).build()).use { r ->
        if (!r.isSuccessful) throw HttpStatusException(r.code, "HTTP ${r.code} for $url")
        withContext(Dispatchers.IO) { r.body.string() }
    }

    /** Downloads the first URL in [urls] that answers into [target] (mirrors / lower qualities as fallbacks). */
    suspend fun downloadToFile(urls: List<String>, referer: String?, target: File, onBytes: (Long, Long) -> Unit = { _, _ -> }) {
        var last: Exception? = null
        for (url in urls) {
            try {
                val builder = Request.Builder().url(url)
                if (referer != null) builder.header("Referer", referer)
                execute(builder.build()).use { response ->
                    if (!response.isSuccessful) throw HttpStatusException(response.code, "HTTP ${response.code}")
                    val total = response.body.contentLength()
                    withContext(Dispatchers.IO) {
                        response.body.byteStream().use { input ->
                            target.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    onBytes(done, total)
                                }
                            }
                        }
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(last?.message ?: "No download URL.")
    }

    /**
     * Runs the item's lazy full-size lookup once (if it has one) and puts the result in front of
     * its preview URLs. A failed lookup just leaves the preview as the download.
     */
    suspend fun ensureFullUrls(item: MediaGalleryItem) {
        val resolve = item.resolveFullUrls ?: return
        item.resolveFullUrls = null
        try {
            val full = resolve()
            item.urls.addAll(0, full.filter { it !in item.urls })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // keep the preview URL
        }
    }

    /** "author_postId" — used for the per-post subfolder and the zip name. */
    fun baseNameFor(post: MediaGalleryPost): String = sanitizeFileName("${post.author}_${post.id}")

    fun fileNameFor(post: MediaGalleryPost, item: MediaGalleryItem, file: File): String =
        "${baseNameFor(post)}_%02d.${extensionFor(item, file)}".format(item.index)

    /** File extension sniffed from the payload (sites serve JPEG, sometimes WebP/PNG/HEIC). */
    fun extensionFor(item: MediaGalleryItem, file: File): String {
        if (item.isVideo) return "mp4"
        val b = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(b) } }.getOrDefault(0)
        if (n >= 12) {
            if (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) return "png"
            if (b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x46.toByte() &&
                b[8] == 0x57.toByte() && b[9] == 0x45.toByte() && b[10] == 0x42.toByte() && b[11] == 0x50.toByte()
            ) return "webp"
            if (b[4] == 0x66.toByte() && b[5] == 0x74.toByte() && b[6] == 0x79.toByte() && b[7] == 0x70.toByte()) return "heic"
        }
        return "jpg"
    }

    /** Packs the given files into one .zip (photos/videos are already compressed — entries are STORED-like level 0). */
    fun zip(files: List<Pair<String, File>>, target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.setLevel(0)
            for ((name, file) in files) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Builds a Cookie header from the entries of a Netscape cookies.txt whose domain satisfies
     * [domainFilter] (expired ones skipped).
     */
    fun buildCookieHeader(cookiesText: String?, domainFilter: (String) -> Boolean): String {
        if (cookiesText.isNullOrBlank()) return ""
        val now = System.currentTimeMillis() / 1000
        val pairs = LinkedHashMap<String, String>()
        for (raw in cookiesText.split('\n')) {
            var line = raw.trimEnd('\r')
            if (line.startsWith("#HttpOnly_")) line = line.removePrefix("#HttpOnly_")
            else if (line.startsWith("#") || line.isBlank()) continue

            val parts = line.split('\t')
            if (parts.size < 7) continue
            if (!domainFilter(parts[0].trimStart('.'))) continue
            val expiry = parts[4].toLongOrNull()
            if (expiry != null && expiry in 1 until now) continue

            val name = parts[5]
            val value = parts[6]
            // Header values must stay single-line ASCII; drop anything that would make the header invalid.
            if (name.isEmpty() || (name + value).any { it.code < 0x20 || it.code > 0x7E || it == ';' }) continue
            pairs[name] = value
        }
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** True when [host] is [domain] or one of its subdomains. */
    fun isHostOf(host: String, domain: String): Boolean =
        host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)

    fun sanitizeFileName(name: String): String {
        val invalid = "\\/:*?\"<>|\u0000"
        return name.map { if (it in invalid || it.code < 0x20) '_' else it }.joinToString("").trim().trimEnd('.')
            .take(150).ifEmpty { "media" }
    }
}

/** Lenient absolute http(s) URL parse (null when the text isn't one). */
fun parseUrl(url: String): HttpUrl? = url.trim().toHttpUrlOrNull()

/** Non-empty decoded path segments. */
val HttpUrl.segments: List<String> get() = pathSegments.filter { it.isNotEmpty() }
