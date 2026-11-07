package ru.r3xed.qsolog.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.r3xed.qsolog.MainActivity
import ru.r3xed.qsolog.R
import ru.r3xed.qsolog.tr

/** DX cluster notifications: spots worth telling about, and the service that keeps the app on in the background. */
object DxNotify {
    private const val CHANNEL_SPOTS = "dx_spots"
    private const val CHANNEL_SERVICE = "dx_service"
    const val SERVICE_ID = 7301

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SPOTS, tr("DX-кластер: споты"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, tr("DX-кластер в фоне"), NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open", "dx"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun show(ctx: Context, text: String, id: Int) {
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_SPOTS)
            .setSmallIcon(R.drawable.ic_stat_dx)
            .setContentTitle(tr("DX-кластер"))
            .setContentText(text)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // Notifications not allowed (Android 13+): nothing to show.
        }
    }

    fun serviceNotification(ctx: Context): Notification {
        channels(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_dx)
            .setContentTitle(tr("DX-кластер на связи"))
            .setContentText(tr("Уведомления о спотах приходят, пока QSO-LOG в фоне"))
            .setContentIntent(open(ctx))
            .setOngoing(true)
            .build()
    }

    fun startService(ctx: Context) {
        val i = Intent(ctx, DxService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: Exception) {
            // Not allowed to start from the background right now: it starts with the next spot seen in front.
        }
    }

    fun stopService(ctx: Context) {
        ctx.stopService(Intent(ctx, DxService::class.java))
    }
}

/** Holds the app's process while DX cluster notifications in the background are on; the connection is the app's. */
class DxService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = DxNotify.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 29) startForeground(DxNotify.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(DxNotify.SERVICE_ID, n)
        return START_NOT_STICKY
    }
}
