"""Regression checks for restoring FluidGram Labs position on rotation.

These are source-level checks; device behavior is verified by the APK test plan.
"""
import unittest
from pathlib import Path

LABS = (Path(__file__).resolve().parents[1] / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")


class LabsRotationScrollTests(unittest.TestCase):
    def test_scroll_position_saved_as_transient_ui_state(self):
        self.assertIn('STATE_LABS_SCROLL_Y = "fluidgram_labs_scroll_y"', LABS)
        self.assertIn("onSaveInstanceState(Bundle outState)", LABS)
        self.assertIn("labsScroll == null ? 0 : labsScroll.getScrollY()", LABS)
        self.assertIn("outState.putInt(STATE_LABS_SCROLL_Y", LABS)
        self.assertIn("super.onSaveInstanceState(outState);", LABS)

    def test_recreation_restores_after_layout_without_animation_or_polling(self):
        self.assertIn("if (savedInstance != null)", LABS)
        self.assertIn("restoreScrollAfterRecreation(savedInstance.getInt(STATE_LABS_SCROLL_Y, 0))", LABS)
        self.assertIn("if (target == null || savedY <= 0) return;", LABS)
        self.assertIn("target.post(() -> {", LABS)
        self.assertIn("if (target != labsScroll || isFinishing() || isDestroyed()) return;", LABS)
        self.assertIn("int maxY = Math.max(0, root.getHeight() - target.getHeight());", LABS)
        self.assertIn("target.scrollTo(0, Math.min(savedY, maxY));", LABS)
        restoration = LABS.split("private void restoreScrollAfterRecreation(", 1)[1].split(
            "@Override protected void onResume()", 1
        )[0]
        self.assertNotIn("smoothScrollTo", restoration)
        self.assertNotIn("postDelayed", restoration)
        self.assertNotIn("Choreographer", restoration)

    def test_sensitive_vpn_profile_not_saved_in_bundle(self):
        saving = LABS.split("onSaveInstanceState(Bundle outState)", 1)[1].split(
            "private void restoreScrollAfterRecreation(", 1
        )[0]
        self.assertNotIn("pendingVpnConfig", saving)
        self.assertNotIn("putSerializable", saving)
        self.assertNotIn("putParcelable", saving)


if __name__ == "__main__":
    unittest.main()
