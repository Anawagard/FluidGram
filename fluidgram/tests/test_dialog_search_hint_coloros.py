"""Regression guard for readable search hints in ColorOS dark dialogs."""
import ast
import unittest
from pathlib import Path

PATCH = Path(__file__).resolve().parents[1] / "scripts" / "apply_fluidgram.py"


def palette():
    tree = ast.parse(PATCH.read_text(encoding="utf-8"))
    node = next(
        node for node in tree.body
        if isinstance(node, ast.Assign)
        and any(isinstance(t, ast.Name) and t.id == "FLUIDGLASS_COLORS" for t in node.targets)
    )
    return ast.literal_eval(node.value)


class DialogSearchHintColorosTest(unittest.TestCase):
    def test_dark_search_hint_is_readable(self):
        colors = palette()
        self.assertEqual(colors["dialogSearchHint"], "#ffC0D4E1")
        self.assertEqual(colors["dialogSearchBackground"], "#ff1c3043")
        foreground = int(colors["dialogSearchHint"][3:], 16)
        background = int(colors["dialogSearchBackground"][3:], 16)

        def luminance(rgb):
            channels = [(rgb >> shift) & 255 for shift in (16, 8, 0)]
            linear = [v / 255 / 12.92 if v / 255 <= 0.04045
                      else ((v / 255 + 0.055) / 1.055) ** 2.4
                      for v in channels]
            return sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))

        contrast = (luminance(foreground) + 0.05) / (luminance(background) + 0.05)
        self.assertGreaterEqual(contrast, 4.5)


if __name__ == "__main__":
    unittest.main()
