# SAJI PC Runtime Integration Plan

## Product target

Build an Android armeabi-v7a application that can run compatible 32-bit x86 Windows PE programs on an ARM32 Android device. This does not imply support for every Windows program: Wine compatibility, x86 instruction coverage, available memory, graphics APIs and device drivers all constrain which programs will work. x64 Windows PE is out of scope for this ARM32 build.

## Required execution chain

1. Android SAF imports an EXE into private app storage.
2. An Android ARM32 PRoot process starts a Debian ARMHF/glibc root filesystem without root access.
3. ARMHF Box86 runs inside that root filesystem and translates x86 Linux processes.
4. i386 Wine32 and its i386 libraries load the 32-bit Windows PE executable.
5. Xvfb provides an X11 display; x11vnc exposes that display to the Android app's desktop viewer.
6. The Android app manages runtime installation, process lifecycle, EXE arguments, logs, keyboard/mouse/touch input and eventual audio routing.
7. Launch is enabled only after architecture checks and a target-device smoke test pass.

Android Bionic and Debian ARMHF/glibc are different userspaces. The native Android PRoot binary and the ARMHF guest binaries must stay separate.

## Current verified scope

- Android shell can import a file using the Storage Access Framework.
- Imported files are copied into private app storage.
- The PE parser checks MZ/PE headers and reports the machine type.
- ARM32 Android APK CI build has passed.
- No Windows program has yet been executed on the target phone.

## Build pipelines now in the repository

- .github/workflows/android-arm32.yml: produces the ARM32 debug APK.
- .github/workflows/box86-android-arm32.yml: an Android/Bionic Box86 compile probe. This is not the final guest Box86 build; the latest source compatibility fix is still awaiting CI confirmation.
- .github/workflows/armhf-wine-runtime.yml: cross-compiles Box86 for ARMHF Linux, bootstraps Debian Trixie armhf, adds i386 multiarch, installs Wine32:i386, Xvfb and x11vnc, and packages a rootfs artifact. This workflow is not yet verified.
- .github/workflows/proot-android-arm32.yml: attempts to cross-compile upstream PRoot-rs and its loader shim for Android ARM32. This workflow is not yet verified.

## Integration gates

### Gate A: ARMHF Box86 and rootfs
- Build Box86 with an ARMv7 hard-float toolchain.
- Build a Debian ARMHF rootfs with i386 Wine32 and desktop-server packages.
- Verify ELF machine types and dependency availability.
- Smoke-test Box86 and Wine in an ARM execution environment; QEMU CI checks are not a substitute for device execution.

### Gate B: Android PRoot
- Build the Android ARM32 PRoot executable and loader shim.
- Validate PRoot on the actual Android 13 device against the Debian ARMHF rootfs.
- Confirm ptrace/seccomp restrictions and ELF execution behavior.

### Gate C: APK integration
- Package the PRoot executable and loader shim into the APK's native library directory.
- Add a secure first-run runtime bundle download and extraction flow with free-space checks, progress, integrity verification and retry support.
- Bind Android EXE imports into the guest filesystem.
- Start PRoot, Xvfb, x11vnc, Box86 and Wine in a controlled process group.

### Gate D: Desktop interaction
- Implement an Android VNC viewer for the Xvfb display.
- Add keyboard, mouse and touch-to-pointer forwarding.
- Add audio routing only after a working, low-latency bridge is selected.
- Add suspend/resume, process cleanup, logs and crash reporting.

### Gate E: Target device verification
- Install on a real ARM32 Android 13 phone.
- Verify PRoot can execute ARMHF /bin/sh.
- Verify Box86 can launch an x86 Linux test binary.
- Verify Wine32 can create a prefix and open a small Windows PE test.
- Only then enable the normal EXE Launch control.

## Compatibility and safety notes

- Box86 translates x86 Linux userspace. It does not load Windows PE by itself; Wine is required.
- Only 32-bit x86 Windows executables are targeted in this project scope.
- Wine compatibility is per-application. No emulator can promise that every Windows application will run.
- The phone's CPU must support the ARMv7/NEON features used by the selected Box86 dynarec build; this must be probed on-device.
- Runtime packages require license notices and reproducible source references. Box86 is MIT; Wine is LGPL; PRoot-rs and PRoot are GPL-family components. Keep their notices and corresponding source obligations with distributed builds.
- CI can compile and package binaries, but it cannot prove that the user's phone permits the required PRoot ptrace behavior or that the runtime renders correctly on that device.

## Present status

The project is in active runtime construction. The latest APK build is a launcher/import shell, not yet a Windows emulator. ARMHF Box86 + Wine32 rootfs and Android ARM32 PRoot now have dedicated workflows; their CI results and the actual APK integration remain outstanding.
