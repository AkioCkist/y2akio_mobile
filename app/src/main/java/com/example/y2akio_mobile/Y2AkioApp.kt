package com.example.y2akio_mobile

import android.app.Application
import com.example.y2akio_mobile.download.DownloadManager
import com.example.y2akio_mobile.download.Notifications
import com.example.y2akio_mobile.service.GalleryResolver
import com.example.y2akio_mobile.service.YtDlpRuntime
import com.example.y2akio_mobile.service.YtDlpService
import com.example.y2akio_mobile.settings.AppSettings
import com.example.y2akio_mobile.storage.HistoryStore
import com.example.y2akio_mobile.storage.OutputStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        gallery = GalleryResolver()
        storage = OutputStorage(this)
        downloads = DownloadManager(this, settings, history, storage, ytDlp, gallery)

        Notifications.createChannels(this, settings.current.language)

        appScope.launch(Dispatchers.IO) {
            // Leftovers of a process that was killed mid-download: plaintext cookies and work folders.
            settings.cleanupStaleCookieFiles()
            File(cacheDir, "jobs").deleteRecursively()
            // Unpack Python / yt-dlp / FFmpeg early so the first analyze doesn't wait for it.
            runCatching { runtime.ensureReady() }
        }
    }
}
