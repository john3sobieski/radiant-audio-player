package com.example.localplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Скачивание из облака идёт в сервисе переднего плана (тип dataSync): так загрузка не обрывается,
 * когда приложение уходит в фон. Само скачивание выполняет [CloudSync.download].
 */
class CloudSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            CloudSync.cancel()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        val name = AppLocale.localized(this).getString(R.string.cloud_notif_channel)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))

        ServiceCompat.startForeground(this, NOTIF_ONGOING, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val started = CloudSync.download(
            applicationContext,
            onProgress = { nm.notify(NOTIF_ONGOING, ongoing()) },
            onEnd = { finish(nm) },
        )
        if (!started && !CloudSync.busy) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(): Notification {
        val c = AppLocale.localized(this)
        val total = CloudSync.filesTotal
        val done = CloudSync.filesDone
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, CloudSyncService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(c.getString(R.string.cloud_notif_title, CloudSync.provider?.title ?: ""))
            .setContentText(c.getString(R.string.cloud_progress, done, total))
            .setProgress(total.coerceAtLeast(1), done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(0, c.getString(R.string.cancel), cancel)
            .build()
    }

    /** Загрузка закончилась (успешно, с ошибкой или отменой). Если приложение закрыто, сообщаем итог уведомлением. */
    private fun finish(nm: NotificationManager) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val n = CloudSync.notice
        if (n != null && !CloudSync.appVisible &&
            (n.kind == CloudNotice.Kind.DONE || n.kind == CloudNotice.Kind.CANCELLED || n.kind == CloudNotice.Kind.ERROR)
        ) {
            val done = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(n.title(this))
                .setContentText(n.message(this))
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.message(this)))
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
            nm.notify(NOTIF_DONE, done)
        }
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "cloud_sync"
        private const val NOTIF_ONGOING = 4201
        private const val NOTIF_DONE = 4202
        private const val ACTION_CANCEL = "com.example.localplayer.CLOUD_CANCEL"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CloudSyncService::class.java))
        }
    }
}
