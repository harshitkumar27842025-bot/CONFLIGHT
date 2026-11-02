package com.example.busylight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the light service when the TV boots, and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val wanted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!wanted) return
        try {
            ctx.startForegroundService(Intent(ctx, BusyLightService::class.java))
            BootInfo.record(ctx, "service started by ${action.substringAfterLast('.')}")
        } catch (e: Exception) {
            BootInfo.record(ctx, "FAILED (${action.substringAfterLast('.')}): ${e.javaClass.simpleName}")
        }
    }
}
