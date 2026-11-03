"""Contrast regression for ColorOS dark chat-list timestamps."""
import ast
import unittest
from pathlib import Path

PATCH = Path(__file__).resolve().parents[1] / "scripts" / "apply_fluidgram.py"


class DarkChatListTimestampTest(unittest.TestCase):
    def test_timestamp_is_readable_against_dark_list_background(self):
        tree = ast.parse(PATCH.read_text(encoding="utf-8"))
        palette = next(ast.literal_eval(node.value) for node in tree.body
                       if isinstance(node, ast.Assign)
                       and any(isinstance(target, ast.Name)
                               and target.id == "FLUIDGLASS_COLORS"
                               for target in node.targets))
        foreground = palette["chats_date"]
        background = palette["windowBackgroundWhite"]
        self.assertEqual(foreground, "#ffB5C7D7")

        def luminance(argb):
            rgb = [int(argb[i:i + 2], 16) / 255 for i in (3, 5, 7)]
            linear = [v / 12.92 if v <= 0.04045 else
                      ((v + 0.055) / 1.055) ** 2.4 for v in rgb]
            return sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))

        l1, l2 = sorted((luminance(foreground), luminance(background)), reverse=True)
        self.assertGreaterEqual((l1 + 0.05) / (l2 + 0.05), 4.5)


if __name__ == "__main__":
    unittest.main()
