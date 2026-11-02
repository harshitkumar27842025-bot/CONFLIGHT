package com.example.busylight

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class BusyLightService : Service() {
    private var exec: ScheduledExecutorService? = null
    private var api: HttpApi? = null
    private lateinit var light: UsbLight
    private lateinit var tracker: ForegroundTracker
    private lateinit var audio: AudioManager

    private var current: String? = null     // color actually sent to the light
    private var pendingSince = 0L           // when the wanted color first differed from `current` (0 = no change pending)
    private var lastSend = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("busylight", "Busy Light", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "busylight")
            .setContentTitle("Busy Light running")
            .setContentText("Port ${Config.API_PORT}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, 1 shl 30) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else startForeground(1, n)

        DelaySettings.load(this)
        if (exec == null) {
            light = UsbLight(this)
            tracker = ForegroundTracker(this)
            audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            api = HttpApi(Config.API_PORT).also { it.start() }
            exec = Executors.newSingleThreadScheduledExecutor().also {
                it.scheduleWithFixedDelay({ poll() }, 0, Config.POLL_MS, TimeUnit.MILLISECONDS)
            }
        }
        return START_STICKY
    }

    private fun poll() {
        try {
            val now = SystemClock.elapsedRealtime()
            BusyState.usbPresent = light.present
            BusyState.usageAccess = hasUsageAccess(this)
            val fg = if (BusyState.usageAccess) tracker.update() else null
            BusyState.foregroundApp = fg
            BusyState.foregroundClass = if (fg != null) tracker.currentClass else null

            // RED when ANY app uses the microphone or plays audio, an Office app is on screen, or the TV is being cast to.
            val micInUse = audio.activeRecordingConfigurations.isNotEmpty()
            BusyState.inCall = micInUse
            val playing = audio.isMusicActive
            BusyState.audioPlaying = playing
            val casting = fg != null && fg in Config.CAST_PACKAGES
            BusyState.casting = casting
            val busy = (fg != null && fg in Config.OFFICE_PACKAGES) || micInUse || playing || casting
            if (busy != BusyState.rawBusy) { BusyState.rawBusy = busy; BusyState.rawSince = now }

            val wanted = when (BusyState.mode) {
                "red" -> "red"
                "green" -> "green"
                else -> if (busy) "red" else "green"
            }

            // Debounce by TIME: switch only after the new state has lasted the chosen number of seconds.
            val target = when {
                current == null || BusyState.mode != "auto" -> { pendingSince = 0L; wanted }
                wanted == current -> { pendingSince = 0L; wanted }
                else -> {
                    if (pendingSince == 0L) pendingSince = now
                    val needMs = (if (wanted == "red") DelaySettings.toRedSec else DelaySettings.toGreenSec) * 1000L
                    if (now - pendingSince >= needMs) wanted else current!!
                }
            }

            if (target != current || !light.isOpen || now - lastSend >= Config.RESEND_MS) {
                val ok = if (target == "red") light.set(255, 0, 0) else light.set(0, 255, 0)
                if (ok) { current = target; lastSend = now; pendingSince = 0L }
            }
            BusyState.color = current ?: "none"
        } catch (e: Exception) {
            Log.e("BusyLight", "poll failed", e)
        }
    }

    override fun onDestroy() {
        exec?.shutdownNow(); exec = null
        api?.stop()
        try { light.set(0, 255, 0) } catch (_: Exception) { }
        light.close()
        super.onDestroy()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun hasUsageAccess(ctx: Context): Boolean {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
