# Brightness Booster Pro — Fire TV Stick 4K (1st gen)

This is an Android project targeting Fire OS 6 / Android 7.1 (API 25), package id `com.brightnessbooster.pro`.

## What it does
- Opens a video selected from local/network-visible storage using Android's file picker.
- Displays the video through an OpenGL ES fragment shader.
- Provides brightness lift, gamma and shadow recovery controls.
- Includes Fire TV Leanback launcher entry and a landscape remote-friendly interface.

## Important limitations
- Processing works only for videos played inside this app.
- It cannot modify Netflix, Prime Video, YouTube or other apps.
- It cannot increase the physical backlight of the LG television.
- MediaPlayer codec/container support depends on the Fire TV firmware.
- This is an initial source build and has not been compiled or tested on physical AFTMM hardware. Verify playback and remote navigation before relying on it.

## Build an installable APK using GitHub Actions
1. Create a GitHub repository and upload the contents of this folder (not the ZIP itself).
2. Open the repository's **Actions** tab.
3. Run **Build Fire TV APK** (or push to `main`).
4. Open the completed workflow run and download the artifact named `BrightnessBoosterPro-firetv-apk`.
5. Extract `app-debug.apk`.

## Install on Fire TV
1. Install **Downloader** from the Amazon Appstore.
2. Enable Developer Options: Settings → My Fire TV → About → select the device name 7 times; then Developer Options → Install unknown apps → Downloader → On.
3. Transfer the APK to a trusted HTTPS download location you control, or use ADB from a computer on the same network.
4. With ADB enabled, install using: `adb install app-debug.apk`.

Do not install APKs from unknown third-party mirrors. A debug APK is suitable for personal testing, not Play Store distribution.
