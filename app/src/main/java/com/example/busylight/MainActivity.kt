package com.example.busylight

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lockBtn: Button
    private var unlocked = false   // true after the PIN was entered, until the app is left
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { status.text = describe(); ui.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        DelaySettings.load(this)
        CustomApps.load(this)
        // Opening the app (or plugging in the light, which opens it) also starts the service.
        try { startForegroundService(Intent(this, BusyLightService::class.java)) } catch (_: Exception) { }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        status = TextView(this).apply { textSize = 18f }
        root.addView(status)

        fun btn(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label; setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        fun lockedBtn(label: String, action: () -> Unit) = btn(label) { requirePin(action) }
        btn("1. Grant Usage Access") {
            try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            catch (_: ActivityNotFoundException) { status.text = "No settings screen on this device. Use adb (see README)." }
        }
        btn("2. Start light service") { startForegroundService(Intent(this, BusyLightService::class.java)) }
        lockedBtn("Stop service") { stopService(Intent(this, BusyLightService::class.java)) }
        btn("Allow running in background (battery)") {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (_: Exception) { status.text = "No battery settings screen on this device." }
        }
        if (Config.ALLOW_LEARNING) {
        lockedBtn("Learn app (15 s): press, then show the casting app") {
            BusyState.learnNote = "Learning... now open the casting app / start casting, and keep it on screen for 15 seconds."
            ui.postDelayed({
                val pkg = BusyState.foregroundApp
                if (pkg != null && pkg != packageName) {
                    CustomApps.add(this@MainActivity, pkg)
                    BusyState.learnNote = "Learned: $pkg"
                } else {
                    BusyState.learnNote = "Nothing learned (ConFlight itself was on screen, or Usage Access is off). Try again."
                }
            }, 15_000)
        }
        lockedBtn("Forget learned apps") {
            CustomApps.clear(this@MainActivity)
            BusyState.learnNote = "Learned apps cleared."
        }
        }
        lockedBtn("Force RED") { BusyState.mode = "red" }
        lockedBtn("Force GREEN") { BusyState.mode = "green" }
        lockedBtn("Automatic") { BusyState.mode = "auto" }

        root.addView(TextView(this).apply { text = "\nDelays (seconds, 1-30)"; textSize = 18f })
        stepper(root, "Green -> RED delay", { DelaySettings.toRedSec }) { DelaySettings.toRedSec = it }
        stepper(root, "Red -> GREEN delay", { DelaySettings.toGreenSec }) { DelaySettings.toGreenSec = it }

        lockBtn = Button(this)
        lockBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lockBtn.setOnClickListener { lockMenu() }
        root.addView(TextView(this).apply { text = "\nSettings lock"; textSize = 18f })
        root.addView(lockBtn)
        refreshLockLabel()

        setContentView(ScrollView(this).apply { addView(root) })
    }

    /** A label with [ - ] [ + ] buttons that changes a saved delay value. */
    private fun stepper(root: LinearLayout, title: String, get: () -> Int, set: (Int) -> Unit) {
        val label = TextView(this)
        label.textSize = 18f
        fun refreshLabel() { label.text = "$title: ${get()} s" }
        fun change(delta: Int) {
            requirePin {
                set((get() + delta).coerceIn(DelaySettings.MIN, DelaySettings.MAX))
                DelaySettings.save(this)
                refreshLabel()
            }
        }
        val minus = Button(this)
        minus.text = "  -  "
        minus.setOnClickListener { change(-1) }
        val plus = Button(this)
        plus.text = "  +  "
        plus.setOnClickListener { change(1) }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(minus)
        row.addView(plus)
        root.addView(label)
        root.addView(row)
        refreshLabel()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun refreshLockLabel() {
        lockBtn.text = if (PinLock.isSet(this)) "Settings lock: ON (tap to change or remove PIN)"
                       else "Settings lock: OFF (tap to set a PIN)"
    }

    private fun askPin(title: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Runs [action] right away if no PIN is set or it was already entered; otherwise asks for the PIN first. */
    private fun requirePin(action: () -> Unit) {
        if (!PinLock.isSet(this) || unlocked) { action(); return }
        askPin("Settings are locked. Enter PIN") { pin ->
            if (PinLock.check(this, pin)) { unlocked = true; action() }
            else toast("Wrong PIN")
        }
    }

    private fun lockMenu() {
        if (!PinLock.isSet(this)) {
            askPin("Choose a PIN (4-8 digits)") { pin ->
                if (pin.length in 4..8) {
                    PinLock.set(this, pin); unlocked = false
                    toast("PIN set. Settings are now locked.")
                } else toast("PIN must be 4-8 digits")
                refreshLockLabel()
            }
        } else {
            requirePin {
                AlertDialog.Builder(this)
                    .setTitle("Settings lock")
                    .setItems(arrayOf("Change PIN", "Remove PIN")) { _, which ->
                        if (which == 0) {
                            askPin("New PIN (4-8 digits)") { pin ->
                                if (pin.length in 4..8) { PinLock.set(this, pin); toast("PIN changed") }
                                else toast("PIN must be 4-8 digits")
                                refreshLockLabel()
                            }
                        } else {
                            PinLock.clear(this); unlocked = false
                            toast("PIN removed. Settings are unlocked.")
                            refreshLockLabel()
                        }
                    }
                    .show()
            }
        }
    }

    override fun onStop() { super.onStop(); unlocked = false }

    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(refresh) }

    private fun describe(): String = buildString {
        appendLine("Usage access: ${if (BusyLightService.hasUsageAccess(this@MainActivity)) "granted" else "NOT granted"}")
        appendLine("USB light: ${if (BusyState.usbPresent) "connected" else "not found"}")
        appendLine("Light color: ${BusyState.color}")
        appendLine("Mode: ${BusyState.mode}")
        appendLine("Foreground app: ${BusyState.foregroundApp ?: "-"}")
        appendLine("Foreground screen: ${BusyState.foregroundClass ?: "-"}")
        appendLine("Microphone in use (any app): ${BusyState.inCall}")
        appendLine("Audio playing (any app): ${BusyState.audioPlaying}")
        appendLine("Casting to this TV: ${BusyState.casting}")
        appendLine("Learned apps: ${if (CustomApps.packages.isEmpty()) "none" else CustomApps.packages.joinToString()}")
        if (BusyState.learnNote.isNotEmpty()) appendLine(BusyState.learnNote)
        appendLine("Busy signal: ${if (BusyState.rawBusy) "ON" else "OFF"} for ${(SystemClock.elapsedRealtime() - BusyState.rawSince) / 1000} s")
        appendLine("Last automatic start: ${BootInfo.last(this@MainActivity)}")
        appendLine("USB debug: ${UsbLight.lastStatus}")
        append("API: http://${localIp()}:${Config.API_PORT}/status")
    }

    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "device-ip"
    } catch (_: Exception) { "device-ip" }
}
