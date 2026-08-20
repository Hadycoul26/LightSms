package com.example.lightsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Service de premier plan. Il ne fait rien en boucle : il existe juste pour
 * que le systeme (et les surcouches type Xiaomi/Huawei) ne tuent pas l'app,
 * et pour montrer l'etat courant a l'utilisateur.
 */
class LightService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            TorchController.setTorch(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LightService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val torchState = if (Prefs.isTorchOn(this)) {
            getString(R.string.torch_on)
        } else {
            getString(R.string.torch_off)
        }
        val detail = Prefs.lastEvent(this) ?: getString(R.string.no_event_yet)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flash)
            .setContentTitle(getString(R.string.notif_title, torchState))
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "light_sms_listener"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.example.lightsms.action.STOP"
        const val ACTION_REFRESH = "com.example.lightsms.action.REFRESH"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LightService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LightService::class.java))
        }

        /**
         * Met a jour la notification. On ne demarre pas le service ici :
         * Android 12+ interdit de lancer un service de premier plan depuis
         * l'arriere-plan, et le recepteur SMS fonctionne de toute facon sans lui.
         */
        fun refresh(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, LightService::class.java).setAction(ACTION_REFRESH)
            )
        }
    }
}
