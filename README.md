# SAJI PC

Experimental ARM32 Android project for running compatible 32-bit x86 Windows applications.

## Honest status

This repository is under active runtime development. The existing APK is currently a launcher and EXE-import shell; it is **not yet a working Windows emulator**. Do not use the APK build badge or a successful Android build as proof that Windows software already runs.

Implemented in the Android shell:
- ARM32-only APK target (armeabi-v7a)
- Android Storage Access Framework EXE import
- Private app-storage copy
- Windows PE signature and machine-type inspection
- Launch remains disabled until a real runtime passes verification

Runtime build pipelines:
- ARMHF Debian root filesystem with i386 Wine32, Xvfb and x11vnc
- Box86 cross-build for ARMHF Linux userspace
- PRoot-rs cross-build for Android ARM32
- Android ARM32 APK build

The runtime pipelines are still being verified. Their artifacts are not yet integrated into the APK, and they have not been tested on the target phone.

## Intended compatibility

The first target is 32-bit x86 Windows PE software that works with Wine. x64 Windows applications are outside this ARM32 build's scope. Wine compatibility varies by application; no claim is made that every Windows program will run.

## Planned runtime architecture

Android ARM32 app -> Android ARM32 PRoot -> Debian ARMHF/glibc rootfs -> ARMHF Box86 -> i386 Wine32 -> Windows x86 PE application.

For the first desktop path, Xvfb and x11vnc provide an X11 display that can be shown in an Android viewer. Audio, input, lifecycle, installation and device-specific behavior still need integration and testing.

## Build artifacts

GitHub Actions publishes debug APK and runtime component artifacts. Open the repository's Actions tab and select a successful run's Artifacts section. A successful APK build only verifies the Android shell and does not prove Windows EXE execution.
