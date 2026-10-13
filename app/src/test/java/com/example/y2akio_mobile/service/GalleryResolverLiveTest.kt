package com.example.y2akio_mobile.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hits the real sites with share links that used to fall through to yt-dlp ("Unsupported URL").
 * Network-dependent: run with env Y2AKIO_LIVE=1.
 */
class GalleryResolverLiveTest {
    private val resolver = GalleryResolver()

    private fun live() = assumeTrue(System.getenv("Y2AKIO_LIVE") == "1")

    @Test
    fun tiktokShortLinkResolvesToPhotoPost() = runBlocking {
        live()
        val url = resolver.resolve("https://vt.tiktok.com/ZSbof69h6/", null)
        assertTrue(url, TikTokPhotoService.isPhotoUrl(url))
        val post = resolver.tryFetch(url, null)!!
        println("TikTok: ${post.items.size} item(s) by @${post.author}: ${post.title}")
        assertTrue(post.items.isNotEmpty())
    }

    @Test
    fun facebookShareLinkResolvesToPhotoPost() = runBlocking {
        live()
        val url = resolver.resolve("https://www.facebook.com/share/19uAqpXyab/", null)
        assertTrue(url, FacebookPostService.isPostUrl(url))
        val post = resolver.tryFetch(url, null)!!
        println("Facebook post: ${post.items.size} item(s) by @${post.author}: ${post.title}")
        assertTrue(post.items.isNotEmpty())
    }

    @Test
    fun facebookShareLinkResolvesToStory() = runBlocking {
        live()
        val url = resolver.resolve("https://www.facebook.com/share/1E8LuRzYCw/", null)
        assertTrue(url, FacebookStoryService.isStoryUrl(url))
    }

    @Test
    fun nonShortLinkIsUntouched() = runBlocking {
        assertEquals("https://www.youtube.com/watch?v=x", resolver.resolve("https://www.youtube.com/watch?v=x", null))
    }
}
