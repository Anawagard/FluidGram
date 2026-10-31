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
        self.assertIn("APP_VERSION_CODE=7242", source)

if __name__ == "__main__":
    unittest.main()
