# SAJI PC

An experimental Android ARM32 project intended to host a 32-bit Windows application compatibility stack.

## Current status

This is the initial Android starter shell, **not a working Windows emulator yet**.

Included:
- Android application shell
- ARM32-only ABI filter: `armeabi-v7a`
- Native C++ JNI library stub
- GitHub Actions workflow to build and upload a debug APK

Not integrated yet:
- Box86
- 32-bit Wine
- ARMHF userspace/runtime
- graphics, audio, input and filesystem integration
- Windows EXE installer/launcher

## Planned architecture

Android ARM32 app -> ARMHF userspace -> Box86 -> 32-bit Wine -> Windows x86 application.

Each runtime component must be integrated from its upstream source with license notices, reproducible build steps, and device testing.

## Build

GitHub Actions runs `gradle assembleDebug` and uploads the APK as an artifact named `SAJI-PC-ARM32-debug`.
