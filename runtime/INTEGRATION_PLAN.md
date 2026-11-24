# SAJI PC Runtime Integration Plan

## Product target

An Android armeabi-v7a application for selected 32-bit x86 Windows PE software. Windows x64 is out of scope for this ARM32 build. Application compatibility remains Wine-dependent.

## Execution chain

1. Android SAF imports an EXE to private storage.
2. Android ARM32 PRoot starts a Debian ARMHF/glibc rootfs without root.
3. ARMHF Box86 translates x86 Linux processes.
4. i386 Wine32 loads the Windows PE file.
5. Xvfb, x11vnc and noVNC provide a virtual desktop in Android WebView.
6. The app controls process lifecycle, EXE selection, logs and touch/keyboard/mouse interaction.
7. An on-device Wine self-test must pass before EXE launch is enabled.

## Verified milestones

- Android APK with EXE import, PE architecture validation, runtime installer, installed-app browser, desktop viewer and device self-test compiles in GitHub Actions.
- Android ARM32 PRoot executable and static loader compile as ELF32 ARM binaries; release asset is published at the proot-latest tag.
- Box86 ARMHF Linux/glibc cross-compilation completed in CI.
- Debian Trixie ARMHF root filesystem bootstrap completed in CI.
- i386 Wine32 and X11/noVNC dependencies installed in CI.
- QEMU smoke test successfully ran Box86 and Wine 10.0.
- The final rootfs archive/manifest release is still being built and verified.
- PRoot/Wine has not yet been exercised on the target Android phone.

## Build workflows

- .github/workflows/android-arm32.yml: ARM32 debug APK and direct apk-latest release.
- .github/workflows/proot-android-arm32.yml: Termux-compatible Android ARM32 PRoot build and proot-latest release.
- .github/workflows/armhf-wine-runtime.yml: ARMHF Box86, Debian Trixie rootfs, i386 Wine32, Xvfb, x11vnc, noVNC, runtime bundle and runtime-latest release.
- .github/workflows/box86-android-arm32.yml: Android/Bionic Box86 probe; not the primary guest Box86 runtime.

## Integration gates

### Gate A: Android runtime components
- PRoot and loader compile for ARM32 and are published. CI has passed.
- Install and execute PRoot under the target Android app UID. Still requires device verification.

### Gate B: ARMHF Wine rootfs
- Box86 ARMHF Linux build, Debian ARMHF rootfs and i386 Wine32 package installation have succeeded in CI.
- QEMU smoke test reports Wine 10.0.
- Final archive manifest/checksum publication is pending.

### Gate C: APK integration
- RuntimeManager downloads and verifies the manifest, rootfs and PRoot archive.
- Archive extraction rejects path traversal and checks required files.
- APK starts PRoot and runs Box86/Wine --version as an on-device self-test.
- Launch remains disabled unless the self-test passes.
- Runtime bundle must be published before first-run runtime installation can succeed.

### Gate D: Desktop
- Xvfb + x11vnc + noVNC are included in the guest rootfs and a loopback-only desktop launcher is prepared.
- Real Android WebView desktop session, input behavior, lifecycle and display responsiveness remain to be tested on device.
- Hardware 3D acceleration and audio forwarding are not implemented.

### Gate E: Device validation
- Install APK on real ARM32 Android 13 phone.
- Download and install runtime.
- Verify PRoot can execute ARMHF guest commands.
- Verify Box86 starts Wine32 and device self-test passes.
- Install a small Windows x86 test application and verify its desktop interaction.
- Only after these checks can the project be called a working Windows application runner.

## Compatibility and licensing

- Box86 translates x86 Linux userspace; Wine provides Windows API compatibility.
- No claim is made that every Windows program runs.
- Windows x64, kernel-driver apps and software requiring unsupported hardware graphics/APIs are outside this initial target.
- Preserve upstream license notices and corresponding source obligations for Box86, Wine, PRoot, talloc, noVNC and dependencies.

## Present status

The Android app and Android ARM32 PRoot binaries compile; Box86 ARMHF plus Wine32 passed a QEMU smoke test. The final runtime bundle and physical-phone test are still outstanding. This is not yet a fully verified Windows emulator.
