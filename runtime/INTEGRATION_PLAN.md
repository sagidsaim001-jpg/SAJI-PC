# SAJI PC Runtime Integration Plan

## Current verified scope

The Android application is an ARM32-only shell. It can select a document through Android's Storage Access Framework and inspect the first two bytes for the Windows MZ signature. It does not execute Windows binaries.

## Required execution chain

A practical 32-bit x86 Windows compatibility stack on 32-bit ARM Android needs all of these layers:

1. Android document access and private app storage.
2. A Linux userspace compatible with the Android host. A Debian armhf root filesystem is a candidate, but must be tested on the target Android 13 device.
3. A process/container boundary such as a carefully configured proot-style environment, or an Android-native port. Android's Bionic libc is not interchangeable with a glibc-based ARMHF root filesystem.
4. Box86 built for the ARM32 host environment. Box86 translates x86 Linux userspace programs to ARM; it is not itself a Windows PE loader.
5. A matching 32-bit x86 Wine userspace, including its required libraries and registry/prefix setup.
6. Display, audio, input, filesystem and process lifecycle bridges between Wine/Linux and Android.
7. EXE launch orchestration with architecture checks, exit-code reporting and logs.

## Integration gates

### Gate A: Android shell
- Build armeabi-v7a debug APK.
- Verify package ABI and install on a real 32-bit Android 13 device.
- Test document selection and persistent URI permission.

### Gate B: ARMHF userspace
- Produce or obtain a reproducible armhf rootfs with package/license manifests.
- Verify that an ARMHF executable runs in the selected Android isolation strategy.
- Do not ship a rootfs until its redistribution terms and size are reviewed.

### Gate C: Box86
- Pin an upstream Box86 commit.
- Cross-build for the selected ARM32 Linux userspace.
- Verify box86 --version and a small x86 Linux ELF test program inside the rootfs.
- Upstream project: https://github.com/ptitSeb/box86 (MIT).

### Gate D: Wine
- Build or package 32-bit x86 Wine for the exact Box86/rootfs combination.
- Verify wine --version, prefix creation and a minimal Windows PE test.
- Wine is LGPL-licensed; preserve notices and source-offer/source distribution obligations for any distributed binaries.

### Gate E: Android integration
- Bridge Android document URIs into controlled runtime storage.
- Implement display/audio/input, suspend/resume and process cleanup.
- Enable the Launch button only after all runtime checks pass.

## Current status

- Android shell: implemented; CI build still being validated.
- EXE selection and MZ signature check: implemented, not device-tested yet.
- ARMHF rootfs: not integrated.
- Box86: not integrated.
- 32-bit Wine: not integrated.
- Windows EXE execution: not available.
- Device installation test: not performed by CI; requires the APK to be installed and exercised on the user's Infinix device.

## Why the layers cannot be skipped

Box86 targets x86 Linux user-space applications on ARM Linux. A Windows PE executable needs Wine, and Wine itself depends on a compatible Linux userspace and libraries. A successful Android APK build only verifies the launcher shell and native JNI library, not this execution chain.
