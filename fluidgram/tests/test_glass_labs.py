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
    def test_glass_is_used_in_settings_and_paused(self):
        source = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertGreaterEqual(source.count("FluidGlass.surface("), 3)
        self.assertIn("FluidGlass.setAmbientBlur(glassBackdrop, false)", source)
        self.assertIn("scene.addView(glassBackdrop", source)
        self.assertIn("scene.addView(scroll", source)
    def test_overlay_includes_glass(self):
        source = (ROOT / "scripts" / "apply_fluidgram.py").read_text()
        self.assertIn('"FluidGlass.java"', source)
        self.assertIn("APP_VERSION_CODE=7243", source)

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

if __name__ == "__main__":
    unittest.main()
