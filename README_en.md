# VCAM — Android Virtual Camera

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

An Xposed / LSPosed / LSPatch module that replaces camera feed and microphone input in any app.

> ⚠️ **DO NOT USE FOR ANY ILLEGAL PURPOSE. YOU TAKE FULL RESPONSIBILITY.**

---

## Features

- 🎥 **Video Replacement** — Replace camera preview & capture with local videos or network streams
- 🎤 **Microphone Control** — Mute, audio replacement, or video audio sync modes
- 📸 **Photo Replacement** — Intercept photo capture requests, inject custom images
- 🔀 **Random Playback** — Shuffle through your video collection
- 🔔 **Notification Control** — Quick video switching from the notification shade
- 🌐 **Network Streaming** — RTSP/RTMP/HLS streaming source support
- 📱 **Jetpack Compose UI** — Modern Material3 interface
- 🔧 **LSPatch Compatible** — No root required with LSPatch injection

---

## Supported Platforms

| Framework | Method | Root Required |
|-----------|--------|---------------|
| LSPatch | APK injection (no root) | ❌ No |
| LSPosed | Xposed module | ✅ Yes |
| EdXposed | Xposed module | ✅ Yes |
| Original Xposed | Xposed module | ✅ Yes |

- Android 5.0+
- Camera1 & Camera2 API support
- H.264/H.265 hardware decoding

---

## Quick Start

### Installation

1. **LSPatch (recommended, no root)**：Load the module APK in LSPatch Manager, select target apps, apply injection
2. **LSPosed**：Enable the module in LSPosed scope for target apps, force-stop the targets
3. **Original Xposed**：Enable module in Xposed Installer, reboot device

### Usage

1. Grant target app "read storage" permission, force-stop it
2. Open target app, enter camera preview — a toast will show the resolution (e.g. "宽：1920 高：1080")
3. Prepare a replacement video matching that resolution, name it `virtual.mp4`, place in the `Camera1` directory:
   - With storage permission: `/Internal Storage/DCIM/Camera1/virtual.mp4`
   - Without storage permission: `/Internal Storage/Android/data/[package]/files/Camera1/virtual.mp4`
4. Reopen the target app — the camera feed is now replaced

### Control Files (global, real-time)

| File | Effect | Location |
|------|--------|----------|
| `no-silent.jpg` | Enable video audio | `DCIM/Camera1/` |
| `disable.jpg` | Temporarily disable replacement | `DCIM/Camera1/` |
| `no_toast.jpg` | Suppress toast messages | `DCIM/Camera1/` |
| `force_show.jpg` | Force show directory hint | `DCIM/Camera1/` |
| `private_dir.jpg` | Force private directory | `DCIM/Camera1/` |

> 💡 All above controls can also be configured via the VCAM management app UI — no manual file creation needed.

---

## App Interface

The VCAM management app is built with Jetpack Compose Material3, featuring a three-tab layout:

- **Home** — Module status at a glance: playback mode, mic mode, video audio, notification state
- **Manage** — Video/audio file management with import, selection, and deletion
- **Settings** — General settings (random play, audio, mic hook), advanced settings (private dir, toast), network streaming

---

## FAQ

### Front camera orientation is wrong?
Front camera replacement videos generally need horizontal flip + 90° right rotation. The post-process resolution must match the toast prompt.

### Black screen / camera fails to open?
- Verify video path is correct (only one level of `Camera1` directory)
- Some system cameras cannot be hooked
- Check video codec compatibility

### Blurred or distorted video?
Video resolution doesn't match the prompted resolution. Adjust and retry.

### Management app shows "Module Inactive" under LSPatch?
Fixed in v5.0+. The management app and target app run in separate processes — the management app does not need an Xposed environment.

---

## Development

```bash
# Clone
git clone https://github.com/Yaahua/vcam.git

# Requirements
# JDK 17+, Gradle 8.4, AGP 8.2.2

# Build
./gradlew assembleRelease
```

For detailed development log and troubleshooting, see [docs/DEVELOPMENT_LOG.md](./docs/DEVELOPMENT_LOG.md)

### Tech Stack

| Component | Version |
|-----------|---------|
| Gradle | 8.4 |
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.04.00 |
| Xposed API | 82 (compileOnly) |

---

## Credits

- Hook methodology：[CameraHook](https://github.com/wangwei1237/CameraHook)
- H.264 hardware decoding：[Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG-YUV conversion：[CSDN Blog](https://blog.csdn.net/jacke121/article/details/73888732)

---

## License

This project is for educational and research purposes only. Users must comply with local laws and regulations. The author assumes no liability for any use of this project.