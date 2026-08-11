# Streamer

A self-hosted IP camera app for Android, in the spirit of IP Webcam. Turns a spare Android phone (tested target: Galaxy A54 5G) into a discreet camera server you control over your network.

## What it does

- **MJPEG stream** at `http://phone:8080/video.mjpeg` — viewable in any browser, VLC, ffmpeg, OBS, Home Assistant, etc.
- **Snapshot** at `/photo.jpg`
- **Web UI** at `/` — live view + controls
- **Remote camera controls** via REST: torch, front/back switch, zoom, autofocus, record
- **On-device recording** to MP4 (in the app's private files dir)
- **Motion detection** (frame-diff) with event log at `/motion/events` and optional auto-record
- **Sensor readout** (accelerometer, light, battery) at `/sensors`
- **Basic auth** on all endpoints
- **HTTPS** with a self-signed cert generated on first run
- **Foreground service** — keeps camera running with the screen off
- **Optional start-on-boot**
- **Stealth mode** — screen brightness to zero, minimal notification, so the phone reads as off

Not yet: native RTSP H.264 (MJPEG covers virtually every viewer app today; RTSP is on the roadmap and requires a camera-session refactor).

## Build & install

### 1. Install Android Studio (once)

Grab it from https://developer.android.com/studio. When it opens, let it download the SDK (it prompts you). Any recent version (Iguana or newer) works.

### 2. Open this project

- `File → Open` → point at this `Streamer/` folder.
- Android Studio will read `settings.gradle.kts`, detect the Gradle version, and download the Gradle wrapper on its own. First sync takes a few minutes while it fetches CameraX, NanoHTTPD, and BouncyCastle.
- If it prompts to install missing SDK components (build-tools, platform 34), accept.

### 3. Enable developer mode on your phone

On the Galaxy A54:
- `Settings → About phone → Software information` → tap "Build number" seven times.
- Back one screen: `Developer options` appears.
- Toggle **USB debugging** on.
- Plug the phone into your computer with USB. Accept the "Allow USB debugging?" prompt.

### 4. Build + install

- In Android Studio, top toolbar: device dropdown should show your A54. If not, click it and pick your phone.
- Hit the green ▶ Run button. It builds a debug APK and pushes it to the phone.

That's it — the app is on the phone.

### 5. First run

- Open **Streamer** on the phone.
- Set a **username and password** (please don't skip this).
- Check **Use HTTPS** if you want.
- Check **Stealth mode** to make the phone look off while streaming.
- Hit **Start**.
- Grant camera, mic, and (Android 13+) notification permissions.
- The main screen shows one or more URLs like `http://192.168.1.42:8080/` — open one in a browser on any device on the same Wi-Fi.

Log in with the credentials you set, and you're live.

## Remote access from outside the LAN

Do **not** port-forward. Install [Tailscale](https://tailscale.com) on the phone and on whatever you view from — log in with the same account on both — and open the same URL using the phone's Tailscale IP (starts with `100.x.x.x`). No router config, no exposure to the public internet.

## Endpoints (for scripting / Home Assistant / etc.)

All require basic auth if you set a user/pass.

| Endpoint            | Method | Purpose                                    |
|---------------------|--------|--------------------------------------------|
| `/`                 | GET    | Web UI                                     |
| `/video.mjpeg`      | GET    | MJPEG live stream                          |
| `/photo.jpg`        | GET    | Latest single frame as JPEG                |
| `/status`           | GET    | JSON snapshot of state                     |
| `/sensors`          | GET    | Accelerometer, light, battery              |
| `/motion/events`    | GET    | Last 200 motion events                     |
| `/control/torch`    | POST   | Toggle flash (`?on=1` / `?on=0` optional) |
| `/control/switch`   | POST   | Front ↔ back camera                        |
| `/control/zoom`     | POST   | `?ratio=2.5`                               |
| `/control/focus`    | POST   | Trigger autofocus                          |
| `/control/record`   | POST   | Start/stop MP4 recording                   |

## Config knobs

Persisted in SharedPreferences (`streamer_prefs`). The UI covers the common ones; others (resolution, fps, jpeg quality, port) can be edited in `Config.kt` or by dropping in a settings screen.

## Notes on the Galaxy A54 specifically

- **Battery**: keep it plugged in during streaming. Long-term 100% charging swells lithium cells — a smart plug that cycles between ~40–80% keeps the battery healthy for months of always-on use.
- **Samsung battery optimization is aggressive**. After install:
  `Settings → Battery → Background usage limits → Never sleeping apps` → add **Streamer**.
- **Camera-in-use indicator**: Android 12+ shows a green dot in the status bar whenever the camera is active. This is enforced by the OS and cannot be hidden from an app. In stealth mode with the screen off, no one sees it — but if someone unlocks the phone, they'll see it.
- **Overheating**: continuous camera + encoding warms the phone. Don't seal it in a closed enclosure without airflow.

## Legality

Recording people — especially audio, and especially in private spaces (bathrooms, bedrooms, changing areas) — without consent is illegal in most jurisdictions, even on your own property. This tool is intended for cameras on your own property with your own knowledge (pet cam, workshop, package watch, etc.). Deploying it against people who don't know they're being recorded can land you in serious legal trouble regardless of how well it's hidden.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/streamer/app/
│   ├── MainActivity.kt         # setup UI + start service
│   ├── StreamerService.kt      # foreground service, orchestrates everything
│   ├── BootReceiver.kt         # optional start-on-boot
│   ├── Config.kt               # settings + persistence
│   ├── FrameBus.kt             # shared frame + motion event bus
│   ├── camera/CameraController.kt
│   ├── http/HttpServer.kt      # NanoHTTPD + auth + endpoints
│   ├── http/Assets.kt          # serve /assets/web/*
│   ├── http/Tls.kt             # self-signed cert loader
│   ├── http/SelfSignedCert.kt  # BouncyCastle cert generation
│   ├── motion/MotionDetector.kt
│   ├── recording/VideoRecorder.kt
│   ├── rtsp/RtspStreamer.kt    # stub for v2
│   └── sensors/SensorProvider.kt
├── assets/web/                 # HTML/CSS/JS for the browser UI
└── res/                        # Android resources
```
