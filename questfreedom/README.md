# QuestFreedom OS 0.2 alpha — experimental Android launcher + AOSP product overlay

> **Not a custom Quest 3 ROM. Not a Meta/Horizon OS replacement. Do not flash any of these files.**

This branch isolates QuestFreedom under `questfreedom/` from the FluidGram application.
APK launcher features:
- High-contrast responsive layout for wider Android displays, with icon+name cards.
- Package/name search, favorites (long-press an app to toggle), saved app visibility preference.
- Android Settings, Wi-Fi, Bluetooth and Files shortcuts; uses only public intents.
- Displays Android version and ABI; no special privileges, root, package deletion or Meta account modification.
- Default filtering of Meta app families **in the launcher only**, optionally reversible. Meta components continue running on the device.

### CI: build and real generic Android emulator test
GitHub Actions compiles the debug APK with Android Gradle Plugin 8.8.2, Java 17 and Gradle 8.10.2; runs pure-Java JUnit tests, installs APK to generic Android API 32 emulator, launches the activity and checks its process. A screenshot is archived.
This emulator does **not** reproduce Horizon OS, the Quest 3 bootloader, its hardware or VR tracking.

### Experimental virtual AOSP Cuttlefish ARM64 product
`aosp/device/questfreedom/virtual/` contains a product overlay for an Android 15 AOSP source tree.
1. Build the launcher APK with `gradle -p questfreedom :app:assembleDebug`.
2. Copy the product overlay to `device/questfreedom/virtual` in an Android 15 AOSP checkout.
3. Copy the built APK to `device/questfreedom/virtual/prebuilts/QuestFreedomLauncher.apk`.
4. Use the AOSP build environment: `source build/envsetup.sh`, `lunch questfreedom_cf_arm64_phone-trunk_staging-userdebug`, `m`.
These commands describe an *untested* full AOSP build and do not modify Quest hardware.

### Missing hardware work before any Quest ROM exists
Vendor board-support source and legally redistributable device blobs, verified bootloader path, Android HAL for stereo display, optics, controllers, tracking, audio and camera, recovery path, testing on real hardware. None is implemented or validated.

### Security and privacy
App preferences remain local to the launcher. No Internet permission, telemetry, root or disabling of services.
