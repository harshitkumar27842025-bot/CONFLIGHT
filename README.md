# ConFlight (Android / Android TV)

Turns a USB busy light RED when the TV is busy and GREEN when it is free.
Made for the Kuando / Plenom Busylight Omega (also supports Luxafor Flag and Blink(1)).

## When the light is RED (automatic mode)
- Word, Excel, PowerPoint, Microsoft 365 app or WPS Office is on screen
- Any app is using the microphone
- Any app is playing audio
- The TV is being cast to (Google Cast receiver on screen)
- An app you taught it with "Learn app" is on screen (for example a casting app)
Otherwise the light is GREEN. Idle apps (open but silent) keep it green.

## Get the APK (no Android Studio needed)
1. Upload all files of this project to a GitHub repository.
2. Open the repository's "Actions" tab > "Build APK" > "Run workflow".
3. When it finishes (about 5 minutes), open the run and download the "conflight-apk"
   artifact (zip). Unzip it to get ConFlight.apk.
4. Copy ConFlight.apk to the TV (USB drive) and open it with the TV's file manager.
   Allow "install unknown apps" for the file manager when asked.
(Alternative: open this folder in Android Studio and choose Build > Build APK(s).)

## First run on the TV
1. Plug the Busylight into the TV's USB port.
2. Open "ConFlight" and tap Allow on the USB popup. Tick "Use by default for this USB device".
3. Press "Grant Usage Access" and enable ConFlight.
   If the TV has no such screen, run from a PC:
   adb shell appops set com.example.busylight GET_USAGE_STATS allow
4. Press "Start light service".

## Main features
- Adjustable delays: green -> red and red -> green (1 to 30 seconds, saved)
- Learn app: teach it extra apps (casting receivers, etc.) once; they are remembered
- Settings lock: optional 4-8 digit PIN for the settings buttons
- Starts automatically at TV boot, after an update, and when the light is plugged in
- Status screen with live signals, plus a web status page

## Web API (same Wi-Fi)
GET http://<tv-ip>:8765/status | /red | /green | /auto
(No password: anyone on the network can change the color.)

## Customize
Config.kt - package names, port, timing, and ALLOW_LEARNING (false hides the Learn buttons).

## Notes
The technical app ID stays com.example.busylight. Change it before publishing on the
Play Store, because "com.example" is not allowed there. Changing it installs as a new
app (learned apps and PIN are not carried over).
