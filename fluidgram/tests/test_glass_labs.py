import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
class GlassLabsRegression(unittest.TestCase):
    def test_ambient_blur_is_decorative_only(self):
        source = (ROOT / "android" / "FluidGlass.java").read_text()
        self.assertIn("RenderEffect.createBlurEffect", source)
        self.assertIn("Build.VERSION.SDK_INT >= 31", source)
        self.assertIn("setRenderEffect(enabled", source)
        self.assertNotIn("Choreographer", source)
        self.assertNotIn("postDelayed", source)
        self.assertIn("PixelFormat.TRANSLUCENT", source)
    def test_glass_caches_shaders_and_avoids_redundant_gpu_blur(self):
        source = (ROOT / "android" / "FluidGlass.java").read_text()
        self.assertIn("onBoundsChange(Rect bounds)", source)
        self.assertIn("onSizeChanged(int w, int h, int oldw, int oldh)", source)
        self.assertIn("if (blurEnabled == enabled) return;", source)
        self.assertIn("paint.setShader(fillShader)", source)
        self.assertIn("paint.setShader(baseShader)", source)
        panel_draw = source.split("@Override public void draw(Canvas canvas) {", 1)[1].split("@Override public void setAlpha", 1)[0]
        self.assertNotIn("new LinearGradient", panel_draw)
        self.assertNotIn("new Path", panel_draw)
        self.assertNotIn("new RectF", panel_draw)

    def test_glass_is_used_in_settings_and_paused(self):
        source = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertGreaterEqual(source.count("FluidGlass.surface("), 3)
        self.assertIn("FluidGlass.setAmbientBlur(glassBackdrop, false)", source)
        self.assertIn("scene.addView(glassBackdrop", source)
        self.assertIn("scene.addView(scroll", source)
    def test_overlay_includes_glass(self):
        source = (ROOT / "scripts" / "apply_fluidgram.py").read_text()
        self.assertIn('"FluidGlass.java"', source)
        self.assertIn("APP_VERSION_CODE=7248", source)

    def test_refresh_hud_is_event_driven_and_releases_listener(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("registerDisplayListener(displayListener, adaptiveHandler)", source)
        self.assertIn("unregisterDisplayListener(displayListener)", source)
        self.assertNotIn("postFrameCallback", source)
        self.assertNotIn("Choreographer", source)
        self.assertIn("onDisplayChanged(int id)", source)
        self.assertIn("not measured app FPS", source)
    def test_adaptive_refresh_respects_energy_budget(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("SystemClock.elapsedRealtime()", source)
        self.assertIn("now - lastPowerCheckMs >= 15000L", source)
        self.assertIn("batteryPercent(a) > 20", source)
        self.assertIn("!pm.isPowerSaveMode()", source)
        self.assertIn("if (!allowHighRefresh(a))", source)
        self.assertIn("idleReset.run()", source)
        self.assertIn("lp.preferredRefreshRate != 0f", source)
    def test_hud_label_does_not_claim_fps(self):
        source = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertIn("Не измеряет FPS приложения", source)
        self.assertIn("Энергетический профиль", source)
        self.assertNotIn("Adaptive 1–165", source)

    def test_background_releases_display_hints(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("releaseRefreshHint(a);", source)
        self.assertIn("lp.preferredDisplayModeId = 0;", source)
        self.assertIn("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)", source)
        self.assertIn("if (setting != 0 && !allowHighRefresh(a)) setting = 0;", source)

    def test_scroll_events_are_throttled(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("now - lastScrollHintMs < 100L", source)
        self.assertIn("lastScrollHintMs = 0L;", source)
        self.assertIn("removeOnScrollChangedListener(scrollListener)", source)

    def test_power_saver_events_restore_automatic_mode(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("PowerManager.ACTION_POWER_SAVE_MODE_CHANGED", source)
        self.assertIn("a.registerReceiver(powerReceiver", source)
        self.assertIn("powerOwner.unregisterReceiver(powerReceiver)", source)
        self.assertIn("lastPowerCheckMs = 0L;", source)
        self.assertIn("refresh(a);", source)

    def test_glass_reacts_to_battery_and_cleans_up(self):
        source = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertIn("Intent.ACTION_BATTERY_CHANGED", source)
        self.assertIn("ACTION_POWER_SAVE_MODE_CHANGED", source)
        self.assertIn("unregisterReceiver(batteryReceiver)", source)
        self.assertIn("FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur())", source)
        self.assertIn("intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)", source)
        self.assertIn("intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)", source)
        self.assertIn("if (glassBackdrop == null) return;", source)
        self.assertIn("cachedBatteryPercent = level * 100 / scale;", source)
        self.assertIn("cachedBatteryPercent = -1;", source)
        self.assertIn("cachedBatteryPercent >= 0 ? cachedBatteryPercent", source)

    def test_energy_lab_is_on_demand_without_background_sampling(self):
        source = (ROOT / "android" / "FluidEnergy.java").read_text()
        self.assertIn("BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER", source)
        self.assertIn("BatteryManager.BATTERY_PROPERTY_CURRENT_NOW", source)
        self.assertIn("SystemClock.elapsedRealtime()", source)
        self.assertIn("WeakHashMap<Activity, Boolean>", source)
        self.assertIn("sensor unavailable", source)
        self.assertIn("WHOLE PHONE", source)
        self.assertNotIn("postDelayed", source)
        self.assertNotIn("Choreographer", source)
        self.assertNotIn("WakeLock", source)

    def test_energy_lab_lifecycle_and_ui(self):
        refresh = (ROOT / "android" / "FluidRefresh.java").read_text()
        labs = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        patch = (ROOT / "scripts" / "apply_fluidgram.py").read_text()
        self.assertIn("FluidEnergy.enter(a);", refresh)
        self.assertIn("FluidEnergy.exit(a);", refresh)
        self.assertIn("FluidEnergy.report(a)", refresh)
        self.assertIn("FluidEnergy.report(this)", labs)
        self.assertIn("FluidEnergy.reset(this)", labs)
        self.assertIn('"FluidEnergy.java"', patch)
        self.assertIn("GLASS LABS  1.4", labs)

    def test_battery_threshold_is_event_driven_and_coalesced(self):
        source = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn("filter.addAction(Intent.ACTION_BATTERY_CHANGED)", source)
        self.assertIn("if (state == lastBatteryGuardState) return;", source)
        self.assertIn("lastBatteryGuardState = state;", source)
        self.assertIn("lastBatteryGuardState = -1;", source)
        self.assertIn("level * 100 / scale > 20", source)
        self.assertIn("lastPowerCheckMs = SystemClock.elapsedRealtime();", source)

if __name__ == "__main__":
    unittest.main()
