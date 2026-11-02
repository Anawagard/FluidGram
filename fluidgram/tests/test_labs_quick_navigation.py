import unittest
from pathlib import Path

LABS = (Path(__file__).resolve().parents[1] / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

class ColorOSQuickNavigationTests(unittest.TestCase):
    def test_six_real_section_targets(self):
        for title in (
            "ЭКРАН  /  ADAPTIVE REFRESH",
            "ПРИВАТНОСТЬ  /  GHOST LAB",
            "УВЕДОМЛЕНИЯ  /  TELEGRAM",
            "ЭНЕРГОЭФФЕКТИВНОСТЬ",
            "VPN · WIREGUARD",
            "ДИАГНОСТИКА  /  COLOROS",
        ):
            self.assertIn('section("' + title + '")', LABS)
            self.assertIn('"' + title + '"}', LABS)

    def test_ghost_lab_jump_uses_existing_accessible_navigation(self):
        self.assertIn('{"Ghost Lab", "ПРИВАТНОСТЬ  /  GHOST LAB"}', LABS)
        self.assertIn('section("ПРИВАТНОСТЬ  /  GHOST LAB")', LABS)
        self.assertIn('chip.setOnClickListener(v -> jumpToSection(destination[1]));', LABS)
        self.assertIn('chip.setContentDescription("Перейти к разделу " + destination[0]);', LABS)

    def test_accessible_one_shot_jump(self):
        for token in (
            "HorizontalScrollView rail = new HorizontalScrollView(this);",
            "chip.setMinHeight(dp(48));",
            "chip.setContentDescription(",
            "chip.setFocusable(true);",
            "sectionAnchors.clear();",
            "sectionAnchors.put(title, t);",
            "if (current != labsScroll || anchor.getParent() != root) return;",
            "int targetY = Math.max(0, anchor.getTop() - dp(12));",
            "current.smoothScrollTo(0, targetY);",
            "current.scrollTo(0, targetY);",
        ):
            self.assertIn(token, LABS)
        self.assertNotIn("postDelayed(", LABS.split("private View quickNavigation()", 1)[1].split("private void card(", 1)[0])

if __name__ == "__main__":
    unittest.main()
