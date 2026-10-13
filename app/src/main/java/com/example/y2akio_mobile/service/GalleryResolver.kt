package com.example.y2akio_mobile.service

import com.example.y2akio_mobile.model.MediaGalleryPost

/**
 * Routes a link to the matching gallery scraper (TikTok photo posts, X photo posts, Facebook /
 * Instagram posts and stories). Returns null for anything else — and for video posts the
 * scrapers leave to yt-dlp — so the normal yt-dlp flow handles it.
 */
class GalleryResolver(browserFetcher: WebPageFetcher? = null) {
    private val tikTok = TikTokPhotoService(
        browserFetcher?.let { fetcher ->
            { url: String, scriptIds: List<String>, accept: (String) -> Boolean, cookiesText: String? ->
                // Only the imported tiktok.com cookies go into the WebView's jar.
                val cookies = MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, "tiktok.com") }
                    .split("; ").filter { it.isNotBlank() }
                fetcher.fetchScriptText(url, scriptIds, "https://www.tiktok.com", cookies, accept = accept)
            }
        },
    )
    private val facebookStories = FacebookStoryService()
    private val instagramStories = InstagramStoryService()
    private val facebookPosts = FacebookPostService()
    private val instagramPosts = InstagramPostService()
    private val xPosts = XPostService()

    /**
     * Share / short links whose real target (photo post, story, reel...) is only known after
     * their redirects: facebook.com/share/…, fb.me, vt./vm.tiktok.com, tiktok.com/t/…,
     * instagram.com/share/…, t.co.
     */
    fun isShortLink(url: String): Boolean {
        val u = parseUrl(url) ?: return false
        val host = u.host
        val path = u.encodedPath.lowercase()
        return TikTokPhotoService.isShortLink(url) ||
            (MediaGalleryService.isHostOf(host, "facebook.com") && path.startsWith("/share/")) ||
            MediaGalleryService.isHostOf(host, "fb.me") ||
            (MediaGalleryService.isHostOf(host, "instagram.com") && path.startsWith("/share/")) ||
            host.equals("t.co", ignoreCase = true)
    }

    /**
     * The link's real target, or the link itself when it isn't a short link / can't be resolved.
     * Facebook share links only reveal stories and private posts to a logged-in session, so the
     * imported facebook.com / instagram.com cookies go along (and only those).
     */
    suspend fun resolve(url: String, cookiesText: String?): String {
        if (!isShortLink(url)) return url
        val host = parseUrl(url)?.host ?: return url
        val cookieDomain = when {
            MediaGalleryService.isHostOf(host, "facebook.com") || MediaGalleryService.isHostOf(host, "fb.me") -> "facebook.com"
            MediaGalleryService.isHostOf(host, "instagram.com") -> "instagram.com"
            else -> null
        }
        val cookieHeader = cookieDomain?.let { d -> MediaGalleryService.buildCookieHeader(cookiesText) { MediaGalleryService.isHostOf(it, d) } } ?: ""
        return MediaGalleryService.resolveRedirects(url, cookieHeader)
    }

    /** Cheap URL-only check: could this (already resolved) link be a gallery post? */
    fun mightBeGallery(url: String): Boolean =
        FacebookStoryService.isStoryUrl(url) || FacebookPostService.isPostUrl(url) ||
            InstagramStoryService.isStoryUrl(url) || InstagramPostService.isPostUrl(url) ||
            XPostService.isPostUrl(url) || TikTokPhotoService.isPhotoUrl(url)

    /** [url] must already be resolved (see [resolve]). */
    suspend fun tryFetch(url: String, cookiesText: String?): MediaGalleryPost? = when {
        FacebookStoryService.isStoryUrl(url) -> facebookStories.fetchStory(url, cookiesText)
        FacebookPostService.isPostUrl(url) -> facebookPosts.fetchPost(url, cookiesText)
        InstagramStoryService.isStoryUrl(url) -> instagramStories.fetchStory(url, cookiesText)
        InstagramPostService.isPostUrl(url) -> instagramPosts.fetchPost(url, cookiesText)
        XPostService.isPostUrl(url) -> xPosts.fetchPost(url, cookiesText)
        TikTokPhotoService.isPhotoUrl(url) -> tikTok.fetchPost(url, cookiesText)
        else -> null
    }
}
