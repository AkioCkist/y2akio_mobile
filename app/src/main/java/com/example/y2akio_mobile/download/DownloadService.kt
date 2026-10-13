package com.example.y2akio_mobile.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.example.y2akio_mobile.Y2AkioApp
import com.example.y2akio_mobile.settings.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and CPU) alive while downloads run, so they continue
 * after the app is closed or when they were started from the share sheet. The work itself lives in
 * [DownloadManager]; this service mirrors its state into the ongoing notification and stops itself
 * once nothing is queued or running.
 */
@OptIn(FlowPreview::class)
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as Y2AkioApp
        if (intent?.action == ACTION_CANCEL) {
            app.downloads.cancel(intent.getLongExtra(EXTRA_JOB_ID, -1))
        }

        val lang = app.settings.current.language
        startForeground(
            Notifications.FOREGROUND_ID,
            Notifications.progress(this, Strings.get("Job.BackgroundStarted", lang), Strings.get("Job.Starting", lang), -1f, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "y2akio:downloads")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (!observing) {
            observing = true
            scope.launch {
                app.downloads.jobs.combine(app.settings.state) { jobs, settings -> jobs to settings.language }
                    .sample(500)
                    .collect { (jobs, language) ->
                        val active = jobs.filter { it.isActive }
                        if (active.isEmpty()) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            return@collect
                        }
                        val n = if (active.size == 1) {
                            val job = active[0]
                            Notifications.progress(this@DownloadService, job.title, job.detail, job.progress, job.id)
                        } else {
                            val avg = active.map { it.progress.coerceAtLeast(0f) }.average().toFloat()
                            Notifications.progress(
                                this@DownloadService,
                                Strings.get("Notif.Running", language).replace("{0}", active.size.toString()),
                                active.joinToString(" · ") { it.title.take(40) },
                                avg,
                                null,
                            )
                        }
                        getSystemService(android.app.NotificationManager::class.java).notify(Notifications.FOREGROUND_ID, n)
                    }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 dataSync time limit reached: stop cleanly (running jobs are canceled). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as Y2AkioApp).downloads.cancelAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.example.y2akio_mobile.CANCEL"
        const val EXTRA_JOB_ID = "jobId"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}
