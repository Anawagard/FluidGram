from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
refresh = (BASE / "android/FluidRefresh.java").read_text(encoding="utf-8")
labs = (BASE / "android/FluidLabsActivity.java").read_text(encoding="utf-8")
patch = (BASE / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")

def test_coloros_auto_migration_and_retired_vendor_modes():
    assert 'getBoolean("ltpo_standard_120_v1", false)' in refresh
    assert 'putInt("refresh_mode", 0)' in refresh
    assert 'if (mode < 0 || mode > 4)' in refresh
    assert 'FluidOplusRate.confirmRelease(c.getPackageName())' in refresh
    assert 'int setting = selectedMode(a);' in refresh
    assert 'int mode = FluidRefresh.selectedMode(this);' in labs
    assert '"165 Гц · касание / прокрутка / покой"' not in labs
    assert '"165 → 144 → Auto · эксперимент LTPO"' not in labs

def test_optional_hints_capped_without_pinning_auto_mode():
    assert 'Math.min(120f, maxSupportedHz(a))' in refresh
    assert refresh.count('getRefreshRate() <= 120.5f') == 2
    assert 'lp.preferredDisplayModeId = 0;' in refresh
    assert 'lp.preferredRefreshRate = 0f;' in refresh
    assert 'if (selectedMode(a) == 4 && allowHighRefresh(a))' in refresh

def test_idle_restores_coloros():
    assert 'adaptiveHandler.postDelayed(idleReset, 1200)' in refresh
    assert 'lp.preferredDisplayModeId = 0;' in refresh
    assert 'lp.preferredRefreshRate = 0f;' in refresh

def test_guards_and_activity_lifecycle():
    assert 'if (!allowHighRefresh(a))' in refresh
    assert 'FluidRefresh.onInteraction(this);' in patch
    assert 'FluidRefresh.pause(this);' in patch
    assert 'FluidRefresh.onInteraction(this);' in labs

def test_diagnostics_disclaims_guarantee():
    assert 'OS owns actual Hz and idle LTPO' in refresh
    assert 'не может гарантировать' in labs

def test_patch_existing_upstream_interaction_instead_of_duplicate():
    assert 'voipLaunchedInBackground = false;' in patch
    assert 'return replace_exact(src,\n            """    public void onUserInteraction()' in patch
    assert '"""    @Override\n    public void onUserInteraction()' not in patch

def test_vendor_uses_firmware_proxy_not_guessed_binder_transactions():
    vendor = (BASE / "android/FluidOplusRate.java").read_text(encoding="utf-8")
    assert 'requestGameRefreshRate' in vendor
    assert 'RATE_165 = 7' in vendor
    assert 'RATE_NONE = 0' in vendor
    assert 'candidate.getParameterTypes()' in vendor
    assert 'binder.transact(' not in vendor
    assert 'return requested;' in vendor

def test_vendor_release_during_idle_and_pause():
    assert 'FluidOplusRate.request(vendorPackage)' in refresh
    assert 'FluidOplusRate.release(vendorPackage)' in refresh
    assert 'if (adaptiveOwner == a) stopAdaptive();' in refresh

def test_vendor_mode_does_not_pin_android_display_mode():
    branch = refresh.split('if (selectedMode(a) == 5) {', 2)[1]
    assert 'releaseRefreshHint(a);' in branch
    assert 'FluidOplusRate.request(vendorPackage);' in branch
    assert 'return;' in branch.split('if (best != null)', 1)[0]

def test_second_zero_vote_is_cancelled_on_new_interaction():
    vendor = (BASE / "android/FluidOplusRate.java").read_text(encoding="utf-8")
    assert 'void confirmRelease(String packageName)' in vendor
    assert 'adaptiveHandler.removeCallbacks(idleConfirm);' in refresh
    assert 'adaptiveHandler.postDelayed(idleConfirm, 350);' in refresh
    assert 'FluidOplusRate.confirmRelease(vendorPackage)' in refresh
    assert 'RATE_120' not in vendor

def test_idle_probe_is_snapshot_not_continuous_polling():
    assert 'postDelayed(idleProbeThree, 3000)' in refresh
    assert 'postDelayed(idleProbeSix, 6000)' in refresh
    assert 'idleProbeGeneration != generation' in refresh
    assert 'cancelIdleProbes();' in refresh
    assert 'completedIdleReport = "FluidGram LTPO' in refresh
    assert 'public static String idleReport()' in refresh

def test_copy_preserves_completed_snapshot():
    assert 'FluidRefresh.idleReport()' in labs
    assert 'Снимок LTPO через 3 и 6 секунд' in labs
    assert 'completedIdleReport' not in labs

def test_mode_six_uses_short_bridge_and_cancels_on_touch():
    vendor = (BASE / "android/FluidOplusRate.java").read_text()
    assert 'RATE_144 = 4' in vendor
    assert 'FluidOplusRate.bridge144(vendorPackage)' in refresh
    assert 'postDelayed(idleBridgeRelease, 250)' in refresh
    assert 'removeCallbacks(idleBridgeRelease)' in refresh
    assert '165 → 144 → Auto' not in labs

def test_notification_settings_preserve_telegram_push():
    assert 'ACTION_APP_NOTIFICATION_SETTINGS' in labs
    assert 'areNotificationsEnabled()' in labs
    assert 'ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS' in labs
    assert 'FirebaseMessagingService' not in labs

def test_touch_restores_165_after_bridge_is_cancelled():
    vendor = (BASE / "android/FluidOplusRate.java").read_text()
    assert 'requested && lastRateId == RATE_165' in vendor
    assert 'lastRateId = RATE_144;' in vendor
    assert 'lastRateId = RATE_NONE;' in vendor
