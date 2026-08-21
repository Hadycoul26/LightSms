package com.example.lightsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Service de premier plan. Il a deux roles :
 *
 *  1. Garder un processus vivant. C'est essentiel, pas cosmetique : le systeme
 *     eteint la torche des que le processus qui l'a allumee disparait. Un
 *     BroadcastReceiver reveille a froid ne survit pas a son onReceive, donc
 *     c'est ce service, et lui seul, qui doit piloter la lampe.
 *  2. Refleter l'etat reel de la torche via un TorchCallback, plutot que de
 *     supposer que notre derniere commande a tenu.
 */
class LightService : Service() {

    private val worker = Executors.newSingleThreadExecutor()

    private var cameraManager: CameraManager? = null

    /** Le systeme peut eteindre la torche sans nous prevenir : on l'apprend ici. */
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (Prefs.isTorchOn(this@LightService) != enabled) {
                Prefs.setTorchOn(this@LightService, enabled)
                updateNotification()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            Prefs.setTorchOn(this@LightService, false)
            updateNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        isRunning = true

        cameraManager = (getSystemService(Context.CAMERA_SERVICE) as? CameraManager)?.also {
            try {
                it.registerTorchCallback(torchCallback, null)
            } catch (e: Exception) {
                // Sans callback on perd le suivi d'etat, pas le pilotage.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatoire dans les 5 s suivant startForegroundService(), avant tout le reste.
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setEnabled(this, false)
                applyOnWorker(false, logResult = false)
                worker.execute { stopSelfSafely() }
                return START_NOT_STICKY
            }

            ACTION_APPLY -> {
                val on = intent.getStringExtra(EXTRA_COMMAND) == Command.ON.name
                // Un test manuel ne doit pas ecraser le resultat du dernier SMS.
                applyOnWorker(on, logResult = intent.getBooleanExtra(EXTRA_LOG, true))
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            // Rien a faire : on part de toute facon.
        }
        worker.shutdown()
        super.onDestroy()
    }

    // --- Pilotage --------------------------------------------------------

    /** Hors du thread principal : setTorch() peut attendre entre deux essais. */
    private fun applyOnWorker(on: Boolean, logResult: Boolean) {
        worker.execute {
            val result = TorchController.setTorch(this, on)

            if (logResult) {
                EventLog.updateLastResult(
                    this,
                    if (result.ok) "OK - " + result.detail else "ECHEC - " + result.detail
                )
                Prefs.setLastEvent(
                    this,
                    if (result.ok) result.detail.replaceFirstChar { it.uppercase() }
                    else "Echec : " + result.detail
                )
            }

            updateNotification()
        }
    }

    private fun stopSelfSafely() {
        try {
            stopSelf()
        } catch (e: Exception) {
            // Ignorable : le service part de toute maniere.
        }
    }

    // --- Notification ----------------------------------------------------

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

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            // Notifications refusees : le service continue de fonctionner.
        }
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

        val torchState = getString(
            if (Prefs.isTorchOn(this)) R.string.torch_on else R.string.torch_off
        )
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
        const val ACTION_APPLY = "com.example.lightsms.action.APPLY"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_LOG = "log"

        @Volatile
        var isRunning: Boolean = false
            internal set

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
         * Confie une commande au service, en le demarrant s'il etait mort.
         * C'est aussi ce qui repare l'ecoute quand le systeme a tue le processus.
         *
         * @return false si le systeme a refuse le demarrage ; l'appelant doit
         *   alors se rabattre sur un pilotage direct, forcement moins fiable.
         */
        fun applyCommand(context: Context, command: Command, log: Boolean = true): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LightService::class.java)
                    .setAction(ACTION_APPLY)
                    .putExtra(EXTRA_COMMAND, command.name)
                    .putExtra(EXTRA_LOG, log)
            )
            true
        } catch (e: Exception) {
            false
        }

        fun refresh(context: Context) {
            if (!isRunning) return
            context.startService(Intent(context, LightService::class.java))
        }
    }
}
