"""Regression checks for the Labs reduced-motion and preserved-scroll UX."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class LabsMotionAccessibilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.labs = (ROOT / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_reduced_motion_is_opt_in_and_respects_android_animation_setting(self):
        self.assertIn('toggle("Спокойная навигация"', self.labs)
        self.assertIn('"reduce_motion_labs", false', self.labs)
        self.assertIn('preferences.getBoolean("reduce_motion_labs", false)', self.labs)
        self.assertIn("android.os.Build.VERSION.SDK_INT < 26", self.labs)
        self.assertIn("android.animation.ValueAnimator.areAnimatorsEnabled()", self.labs)
        self.assertIn("current.scrollTo(0, targetY);", self.labs)
        self.assertIn("current.smoothScrollTo(0, targetY);", self.labs)

    def test_glass_setting_rerender_keeps_scroll_position_safely(self):
        self.assertIn("final ScrollView previousScroll = labsScroll;", self.labs)
        self.assertIn("previousScroll.getScrollY()", self.labs)
        self.assertIn("if (previousScroll != null) {", self.labs)
        self.assertIn("if (labsScroll != scroll || isFinishing() || isDestroyed()) return;", self.labs)
        self.assertIn("int limit = Math.max(0, root.getHeight() - scroll.getHeight());", self.labs)
        self.assertIn("scroll.scrollTo(0, Math.min(previousY, limit));", self.labs)
        self.assertIn('if ("neon_labs".equals(key) || "reduce_glass_transparency".equals(key)) render();', self.labs)

    def test_no_background_animation_loop_added(self):
        self.assertNotIn("Choreographer", self.labs)
        self.assertNotIn("postFrameCallback", self.labs)
        self.assertNotIn("ObjectAnimator", self.labs)


if __name__ == "__main__":
    unittest.main()
