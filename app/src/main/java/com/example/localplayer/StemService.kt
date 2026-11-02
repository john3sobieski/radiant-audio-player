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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Создание стемов и загрузка моделей идут в сервисе переднего плана (тип dataSync): работа занимает минуты,
 * и без сервиса система остановила бы её, как только приложение уйдёт в фон. Саму работу выполняет [Stems.execute].
 * Пока идёт работа, держится частичная блокировка процессора (с потолком по времени), иначе при погашенном экране
 * процессор засыпает и разбор растягивается в разы.
 */
class StemService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Stems.cancel()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        val name = AppLocale.localized(this).getString(R.string.stems_notif_channel)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))

        ServiceCompat.startForeground(this, NOTIF_ONGOING, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        acquireWakeLock()
        val started = Stems.execute(
            applicationContext,
            onProgress = { nm.notify(NOTIF_ONGOING, ongoing()) },
            onEnd = { finish(nm) },
        )
        if (!started && !Stems.busy) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiant:stems").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_MAX_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(): Notification {
        val c = AppLocale.localized(this)
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, StemService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val models = Stems.phase == Stems.Phase.MODELS
        val percent = (Stems.progress * 100).toInt().coerceIn(0, 100)
        val title = if (models) c.getString(R.string.stems_models_title) else c.getString(R.string.stems_notif_title)
        val text = Stems.track?.title?.let { "$it\n" }.orEmpty() + Stems.statusText(this)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$title · $percent%")
            .setContentText(Stems.statusText(this))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(0, c.getString(R.string.cancel), cancel)
            .build()
    }

    /** Работа закончилась (успешно, с ошибкой или отменой). Если приложение закрыто, итог сообщаем уведомлением. */
    private fun finish(nm: NotificationManager) {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val n = Stems.notice
        if (n != null && !Stems.appVisible) {
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
        private const val CHANNEL = "stems"
        private const val NOTIF_ONGOING = 4301
        private const val NOTIF_DONE = 4302
        private const val ACTION_CANCEL = "com.example.localplayer.STEMS_CANCEL"

        /** Потолок на случай сбоя: даже если работа зависла, процессор не остаётся занятым навсегда. */
        private const val WAKE_LOCK_MAX_MS = 3 * 60 * 60 * 1000L

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, StemService::class.java))
        }
    }
}
