import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from apply_fluidgram import replace_exact, MOTION_CLASS, FPS_CLASS, THEME_CHANGES, FLUIDGLASS_COLORS, ICON

class FluidGramTest(unittest.TestCase):
    def test_strict_patch(self):
        self.assertEqual(replace_exact("a", "a", "b", "test"), "b")
        with self.assertRaises(RuntimeError):
            replace_exact("aaa", "a", "b", "test")
    def test_fps_range(self):
        self.assertIn("return smooth60 && best60 != null ? best60", FPS_CLASS)
        self.assertIn("if (smooth60 && low == 60 && high == 60) return range;", FPS_CLASS)
        self.assertNotIn("new Range", FPS_CLASS)
    def test_coloros(self):
        self.assertIn("defaultColors[key_chat_outBubble] = 0xffefffde;", THEME_CHANGES)
    def test_fluidglass(self):
        self.assertEqual(FLUIDGLASS_COLORS["chat_outBubble"], "#FF295358")
        self.assertEqual(FLUIDGLASS_COLORS["windowBackgroundWhite"], "#ff101c2b")
        self.assertGreaterEqual(len(FLUIDGLASS_COLORS), 20)
    def test_motion(self):
        self.assertIn("PathInterpolator", MOTION_CLASS)
        self.assertIn("330L", MOTION_CLASS)
        self.assertIn("areSizeAndRateSupported(width, height, 60.0)", MOTION_CLASS)
    def test_camera_safety(self):
        from apply_fluidgram import patch_round_camera_extras
        self.assertTrue(callable(patch_round_camera_extras))
        self.assertIn("return choose(advertised, true);", FPS_CLASS)
        self.assertIn("!smooth60 && low == 30 && high == 30", FPS_CLASS)
        self.assertIn("isOnePlus15()", MOTION_CLASS)
        self.assertIn("boolean prefer60", MOTION_CLASS)
    def test_branding(self):
        self.assertIn("<vector", ICON)
        self.assertNotIn("paper plane", ICON)

if __name__ == "__main__":
    unittest.main()
