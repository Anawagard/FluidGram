# QuestFreedom OS 0.1 alpha
Standalone **experimental AOSP port project**, not a flashable Meta Quest 3 firmware.
Includes a regular Android launcher APK (Meta-free app dependencies) and an AOSP Cuttlefish ARM64 product overlay.

*No actual Quest 3 hardware drivers, signed boot chain, stereo compositor, tracking, controllers or validated recovery are provided.*
The UI filter hides selected Meta package families only within this launcher; it DOES NOT uninstall or disable installed Meta services on a real Quest.

## Tests and build
Android Gradle Plugin 8.8.2, Gradle 8.10.2, Java 17, Android SDK 35:
```bash
cd questfreedom
gradle :app:assembleDebug :app:testDebugUnitTest
```
CI additionally smoke-tests app install/launch in generic Android API 32 AVD. It does **not** simulate the Horizon OS bootloader or Quest 3 hardware. For Meta Spatial Simulator testing, manually install the resulting app APK with Meta VR Android Studio plugin.

## AOSP target (future)
Copy `aosp/device/questfreedom/` to `device/questfreedom/` in official AOSP checkout.
Copy compiled APK to `device/questfreedom/virtual/prebuilts/QuestFreedomLauncher.apk`.
Then use `lunch questfreedom_cf_arm64_phone-userdebug` and `m`. This target is **untested** in a full AOSP source checkout.
DO NOT FLASH TO QUEST 3.
