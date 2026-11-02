package com.example.busylight

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Tracks which app is in the foreground using Usage Access events. */
@Suppress("DEPRECATION")
class ForegroundTracker(ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var since = System.currentTimeMillis() - 6 * 3600_000L
    private var current: String? = null
    var currentClass: String? = null
        private set

    fun update(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(since, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { current = e.packageName; currentClass = e.className }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == current) { current = null; currentClass = null }
            }
        }
        since = now - 5000   // small overlap so late-arriving events aren't missed
        return current
    }
}
