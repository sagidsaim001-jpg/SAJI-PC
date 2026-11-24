# SAJI PC

SAJI PC is an experimental **ARM32 Android** compatibility environment for selected **32-bit x86 Windows applications** through Wine.

## Current build

- Android APK target: armeabi-v7a only.
- Version: 0.3.0.
- Direct APK: https://github.com/sagidsaim001-jpg/SAJI-PC/releases/download/apk-latest/SAJI-PC-ARM32-debug.apk
- APK checksum: https://github.com/sagidsaim001-jpg/SAJI-PC/releases/download/apk-latest/SAJI-PC-ARM32-debug.apk.sha256
- Android ARM32 PRoot package: https://github.com/sagidsaim001-jpg/SAJI-PC/releases/download/proot-latest/SAJI-PC-PRoot-Android-ARM32.tar.gz

## Implemented in the Android app

- Import Windows EXE files with Android's document picker.
- Check PE signature and machine type. Only Windows x86 / 32-bit PE is accepted for launch.
- Keep imported EXEs in app-private storage.
- Download and integrity-check the ARMHF Linux + Wine32 runtime bundle when published.
- Install runtime with free-space checks and guarded archive extraction.
- Browse 32-bit EXE files in the Wine prefix after installation.
- Start and stop a PRoot desktop session.
- Display the virtual desktop with noVNC in Android WebView.
- Run a PRoot + Box86 + Wine32 device self-test. The EXE launch action remains disabled until this test passes; the test runs after installation and again on a later app start.

## Verified build milestones

- APK source including EXE import, runtime installer and device-test flow compiled and was uploaded by GitHub Actions.
- Android ARM32 PRoot and its loader compiled as ELF32 ARM binaries. The PRoot release package is published.
- Box86 compiled for Debian ARMHF/glibc.
- Debian Trixie ARMHF rootfs was bootstrapped in CI.
- i386 Wine32, Xvfb, x11vnc, noVNC and websockify installed into the rootfs in CI.
- QEMU smoke test ran Box86 and reported Wine 10.0.

The final ARMHF/Wine32 runtime archive and its manifest are still being packaged and published. The Android app has not yet been tested on the target phone, so the overall Windows compatibility chain is not yet confirmed on-device.

## Intended architecture

Android ARM32 app -> Android ARM32 PRoot -> Debian ARMHF/glibc rootfs -> ARMHF Box86 -> i386 Wine32 -> Windows x86 PE application.

## Compatibility limits

- This build targets 32-bit x86 Windows software only. Windows x64 apps are outside this ARM32 build's scope.
- Wine compatibility varies by app. Some Windows software requires APIs, DirectX features, drivers, services or .NET versions that may not be available.
- Hardware-accelerated 3D and audio forwarding are not implemented in this first desktop path.
- PRoot needs to work under the device's Android kernel/security policy; the built-in phone self-test is designed to check this before enabling EXE launch.

## GitHub Actions

Open the repository's Actions tab to inspect APK, PRoot and ARMHF Wine runtime workflows. A successful APK compile alone does not prove Windows execution.
