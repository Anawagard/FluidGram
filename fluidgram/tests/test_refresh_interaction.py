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
