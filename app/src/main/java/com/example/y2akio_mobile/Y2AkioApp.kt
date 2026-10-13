package com.example.y2akio_mobile

import android.app.Application
import com.example.y2akio_mobile.download.DownloadManager
import com.example.y2akio_mobile.download.Notifications
import com.example.y2akio_mobile.service.GalleryResolver
import com.example.y2akio_mobile.service.WebPageFetcher
import com.example.y2akio_mobile.service.YtDlpRuntime
import com.example.y2akio_mobile.service.YtDlpService
import com.example.y2akio_mobile.settings.AppSettings
import com.example.y2akio_mobile.storage.HistoryStore
import com.example.y2akio_mobile.storage.OutputStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Holds the app-wide singletons (settings, history, yt-dlp runtime, download queue). */
class Y2AkioApp : Application() {
    lateinit var settings: AppSettings
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var runtime: YtDlpRuntime
        private set
    lateinit var ytDlp: YtDlpService
        private set
    lateinit var gallery: GalleryResolver
        private set
    lateinit var storage: OutputStorage
        private set
    lateinit var downloads: DownloadManager
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        history = HistoryStore(this)
        runtime = YtDlpRuntime(this)
        ytDlp = YtDlpService(runtime::ensureReady)
        gallery = GalleryResolver(WebPageFetcher(this))
        storage = OutputStorage(this, settings)
        downloads = DownloadManager(this, settings, history, storage, ytDlp, gallery)

        appScope.launch(Dispatchers.IO) {
            // Leftovers of a process that was killed mid-download: plaintext cookies and work folders.
            settings.cleanupStaleCookieFiles()
            File(cacheDir, "jobs").deleteRecursively()

            // Preferences are disk-backed. Apply them after the first frame can be prepared instead
            // of blocking Application.onCreate; re-creating channels also updates localized names.
            settings.awaitLoaded()
            Notifications.createChannels(this@Y2AkioApp, settings.current.language)
            withContext(Dispatchers.Main.immediate) {
                settings.applyNightMode()
            }

            // The launcher splash waits for this one-time engine setup, so start it immediately and
            // reveal Home only when Analyze can be used without a second "Preparing" phase.
            runCatching { runtime.ensureReady() }
        }
    }
}
