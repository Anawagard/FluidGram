from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
refresh = (BASE / "android/FluidRefresh.java").read_text(encoding="utf-8")
labs = (BASE / "android/FluidLabsActivity.java").read_text(encoding="utf-8")
patch = (BASE / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")

def test_mode_five_is_opt_in():
    assert 'getInt("refresh_mode", 4) != 5' in refresh
    assert '"165 Гц · касание / прокрутка / покой"' in labs

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
    branch = refresh.split('if (prefs(a).getInt("refresh_mode", 4) == 5) {', 2)[1]
    assert 'releaseRefreshHint(a);' in branch
    assert 'FluidOplusRate.request(vendorPackage);' in branch
    assert 'return;' in branch.split('if (best != null)', 1)[0]
