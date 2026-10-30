import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from apply_fluidgram import replace_exact, MOTION_CLASS, FPS_CLASS, THEME_CHANGES, ICON

class FluidGramTest(unittest.TestCase):
    def test_strict_patch(self):
        self.assertEqual(replace_exact("a", "a", "b", "test"), "b")
        with self.assertRaises(RuntimeError):
            replace_exact("aaa", "a", "b", "test")
    def test_fps_range(self):
        self.assertIn("return best30 != null ? best30 : closest;", FPS_CLASS)
        self.assertIn("if (low == 30 && high == 30) return range;", FPS_CLASS)
        self.assertNotIn("new Range", FPS_CLASS)
    def test_coloros(self):
        self.assertIn("defaultColors[key_chat_outBubble] = 0xffefffde;", THEME_CHANGES)
    def test_motion(self):
        self.assertIn("PathInterpolator", MOTION_CLASS)
        self.assertIn("440L", MOTION_CLASS)
    def test_branding(self):
        self.assertIn("<vector", ICON)
        self.assertNotIn("paper plane", ICON)

if __name__ == "__main__":
    unittest.main()
