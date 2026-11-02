package com.example.busylight

import android.content.Context
import android.os.SystemClock

object Config {
    const val API_PORT = 8765
    const val POLL_MS = 500L            // how often the TV is checked
    const val RESEND_MS = 10_000L       // keep-alive re-send of the same color

    /** Light goes RED whenever one of these is in the foreground. */
    val OFFICE_PACKAGES = setOf(
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.officehubrow",     // Microsoft 365 app
        "cn.wps.moffice_eng",                    // WPS Office (Google Play version)
        "cn.wps.moffice",                        // WPS Office (other builds)
        "cn.wps.moffice_i18n"                    // WPS Office (international builds)
    )

    /** Light goes RED whenever one of these is in the foreground: screen casting / mirroring receivers. */
    val CAST_PACKAGES = setOf(
        "com.google.android.apps.mediashell"     // Chromecast built-in / Google Cast Receiver (Android TV)
        // Add your TV's Miracast / screen-mirroring app here if it has a different package name.
    )

    /** Light goes RED if one of these is in the foreground AND any audio is playing (songs, videos). */
    val PLAYBACK_PACKAGES = setOf(
        "com.android.chrome",
        "com.google.android.youtube.tv",            // YouTube on Android TV
        "com.google.android.youtube",               // YouTube (phones / some TVs)
        "com.google.android.apps.youtube.music",    // YouTube Music
        "com.google.android.youtube.tvmusic"        // YouTube Music on Android TV
    )

    /** Light goes RED only if one of these is in the foreground AND the microphone is in use (a call). */
    val MEETING_PACKAGES = setOf(
        "com.microsoft.teams",
        "us.zoom.videomeetings",
        "com.google.android.apps.tachyon",      // Google Meet
        "com.cisco.webex.meetings",
        "com.skype.raider",
        "com.android.chrome"                    // Meet in the browser
    )
}

/** Shared state between service, API and UI. */
object BusyState {
    @Volatile var mode = "auto"          // auto | red | green
    @Volatile var color = "none"         // what the light was last set to
    @Volatile var foregroundApp: String? = null
    @Volatile var foregroundClass: String? = null
    @Volatile var inCall = false
    @Volatile var audioPlaying = false
    @Volatile var casting = false
    @Volatile var learnNote = ""
    @Volatile var usbPresent = false
    @Volatile var usageAccess = false
    @Volatile var rawBusy = false                       // instant signal, before the delay is applied
    @Volatile var rawSince = SystemClock.elapsedRealtime()  // when rawBusy last changed

    fun json(): String =
        """{"color":"$color","mode":"$mode","foregroundApp":${foregroundApp?.let { "\"$it\"" } ?: "null"},""" +
        """"inCall":$inCall,"audioPlaying":$audioPlaying,"casting":$casting,"usbLightConnected":$usbPresent,"usageAccessGranted":$usageAccess}"""
}


/** User-adjustable delays (seconds), saved on the device. Applied precisely (checked twice per second). */
object DelaySettings {
    private const val PREFS = "busylight_settings"
    const val MIN = 1
    const val MAX = 30

    @Volatile var toRedSec = 2           // green -> red: state must stay "busy" this many seconds
    @Volatile var toGreenSec = 3         // red -> green: state must stay "free" this many seconds

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        toRedSec = p.getInt("toRed", 2).coerceIn(MIN, MAX)
        toGreenSec = p.getInt("toGreen", 3).coerceIn(MIN, MAX)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("toRed", toRedSec).putInt("toGreen", toGreenSec).apply()
    }
}


/** Remembers when the app was last started automatically (boot / update), for the status screen. */
object BootInfo {
    private const val PREFS = "busylight_settings"

    fun record(ctx: Context, text: String) {
        val stamp = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lastAuto", "$stamp  $text").apply()
    }

    fun last(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lastAuto", null)
            ?: "never (no automatic start seen yet)"
}


/** Apps the user taught the app with the "Learn app" button: RED whenever one is on screen. */
object CustomApps {
    private const val PREFS = "busylight_settings"
    @Volatile var packages: Set<String> = emptySet()

    fun load(ctx: Context) {
        packages = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("customRed", emptySet())?.toSet() ?: emptySet()
    }

    fun add(ctx: Context, pkg: String) {
        val updated = packages + pkg
        packages = updated
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("customRed", updated).apply()
    }

    fun clear(ctx: Context) {
        packages = emptySet()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("customRed").apply()
    }
}
