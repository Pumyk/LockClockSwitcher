package com.example.lockclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Shizuku-style pairing helper: a persistent notification with a direct-reply
 * action, so you can type the pairing code while staying on the Wireless
 * debugging screen — no split screen needed. The pairing port is auto-detected
 * via mDNS while the "Pair device with pairing code" dialog is open.
 */
class PairingService : Service() {

    private var port: Int? = null
    private var finder: PairingPortFinder? = null
    private lateinit var nm: NotificationManager
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pairing helper", NotificationManager.IMPORTANCE_HIGH)
        )
        startForeground(NOTIF_ID, buildNotification())
        finder = PairingPortFinder(this) { p ->
            main.post {
                port = p
                nm.notify(NOTIF_ID, buildNotification())
            }
        }
        finder?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()

            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString()
                    ?.filter(Char::isDigit)?.take(6).orEmpty()
                if (code.length != 6) {
                    showResult("The pairing code must be exactly 6 digits — try replying again.")
                    nm.notify(NOTIF_ID, buildNotification())
                    return START_NOT_STICKY
                }
                nm.notify(NOTIF_ID, buildNotification("Pairing…"))
                val p = port
                worker.execute {
                    val result = AdbSetup.pairAndGrant(applicationContext, p, code)
                    main.post {
                        if (result.isSuccess) {
                            showResult("Paired! WRITE_SECURE_SETTINGS granted — you can turn Wireless debugging off now.")
                            stopSelf()
                        } else {
                            showResult(result.exceptionOrNull()?.message ?: "Pairing failed. Reply with a fresh code.")
                            nm.notify(NOTIF_ID, buildNotification())
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        worker.shutdown()
        finder?.stop()
        nm.cancel(NOTIF_ID)
        super.onDestroy()
    }

    private fun buildNotification(working: String? = null): Notification {
        val text = working ?: when (val p = port) {
            null -> "Open \"Pair device with pairing code\", then reply here with the 6-digit code."
            else -> "Pairing port detected: $p. Reply here with the 6-digit code."
        }
        val reply = PendingIntent.getService(
            this, 1,
            Intent(this, PairingService::class.java).setAction(ACTION_CODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val replyAction = Notification.Action.Builder(0, "Enter pairing code", reply)
            .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("6-digit pairing code").build())
            .build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Lock Clock Switcher")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(replyAction)
            .addAction(Notification.Action.Builder(0, "Stop", stop).build())
            .build()
    }

    private fun showResult(msg: String) {
        nm.notify(
            RESULT_ID,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Lock Clock Switcher")
                .setContentText(msg)
                .setStyle(Notification.BigTextStyle().bigText(msg))
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        private const val CHANNEL = "pairing"
        private const val NOTIF_ID = 1
        private const val RESULT_ID = 2
        private const val KEY_CODE = "code"
        private const val ACTION_CODE = "com.example.lockclock.action.PAIR_CODE"
        private const val ACTION_STOP = "com.example.lockclock.action.STOP"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, PairingService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PairingService::class.java))
        }
    }
}
