package com.example.y2akio_mobile.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.y2akio_mobile.MainActivity
import com.example.y2akio_mobile.R
import com.example.y2akio_mobile.settings.AppLanguage
import com.example.y2akio_mobile.settings.Strings

object Notifications {
    const val CHANNEL_PROGRESS = "downloads_progress"
    const val CHANNEL_DONE = "downloads_done"
    const val FOREGROUND_ID = 1

    fun createChannels(context: Context, language: AppLanguage) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, Strings.get("Notif.ChannelProgress", language), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, Strings.get("Notif.ChannelDone", language), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ongoing summary shown while the foreground service runs. */
    fun progress(context: Context, title: String, text: String, percent: Float, cancelJobId: Long?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setProgress(100, percent.coerceIn(0f, 100f).toInt(), percent < 0)
        if (cancelJobId != null) {
            val cancel = PendingIntent.getForegroundService(
                context, cancelJobId.toInt(),
                Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL).putExtra(DownloadService.EXTRA_JOB_ID, cancelJobId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, Strings.get("Home.Cancel", currentLanguage(context)), cancel)
        }
        return builder.build()
    }

    /** Per-job completion notice; tapping it opens the saved file. */
    fun finished(context: Context, jobId: Long, title: String, text: String, fileUri: String?, mime: String?, success: Boolean) {
        if (!canPost(context)) return
        val contentIntent = if (fileUri != null) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(fileUri), mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, jobId.toInt(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            openAppIntent(context)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(if (success) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1000 + jobId.toInt(), n)
        } catch (_: SecurityException) {
            // permission revoked between the check and the call
        }
    }

    private fun currentLanguage(context: Context) =
        (context.applicationContext as com.example.y2akio_mobile.Y2AkioApp).settings.current.language
}
