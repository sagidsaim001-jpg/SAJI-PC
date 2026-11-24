# SAJI PC

SAJI PC is an experimental **ARM32 Android** compatibility environment for running selected **32-bit x86 Windows applications** through Wine.

## App capabilities in version 0.3.0

- ARM32-only Android APK (armeabi-v7a).
- Windows EXE import through Android's document picker.
- PE signature and architecture validation. Only x86 / 32-bit Windows PE is accepted for launch.
- First-run runtime installer with SHA-256 verification, available-storage checks and guarded tar extraction.
- Runtime manager for Debian ARMHF, Box86, i386 Wine32 and Android ARM32 PRoot.
- Desktop view planned through Xvfb, x11vnc and noVNC in an Android WebView.
- Start/stop desktop session and display runtime/launch errors.

## Intended runtime

Android ARM32 app -> Android ARM32 PRoot -> Debian ARMHF/glibc rootfs -> ARMHF Box86 -> i386 Wine32 -> Windows x86 PE application.

Box86 translates x86 Linux userspace. Wine provides the Windows compatibility layer. The first desktop implementation uses a virtual X11 display and noVNC. Audio forwarding and hardware-accelerated graphics are not implemented yet.

## Build and runtime status

The Android app including the importer, runtime installer and launch UI has compiled successfully in GitHub Actions. The APK build result verifies Android code only.

Separate CI workflows are building:
- Android ARM32 PRoot-rs and its loader shim.
- Debian Trixie ARMHF root filesystem.
- Box86 built for ARMHF Linux/glibc.
- i386 Wine32, Xvfb, x11vnc and noVNC runtime packages.
- Runtime artifacts and a signed-by-checksum download manifest.

The runtime components are not yet all verified, published and tested together on a physical Android 13 ARM32 device. Until those gates pass, this repository must not be described as a completed Windows emulator.

## Compatibility limits

- This build targets 32-bit x86 Windows applications only. Windows x64 programs are out of scope.
- Wine compatibility varies by application. Some software needs Windows APIs, graphics, drivers, services or .NET features that may not be available.
- Software requiring DirectX hardware acceleration, kernel drivers, anti-cheat, or unsupported APIs may not work.
- Performance depends on the phone's ARM CPU, RAM and the amount of x86 translation required.

## GitHub Actions

Open the repository's Actions tab to inspect the latest ARM32 APK and runtime build jobs. APK build success is not proof that Windows software executes; the runtime and device smoke tests are separate gates.
