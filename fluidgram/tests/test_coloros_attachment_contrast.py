"""Regression guard for ColorOS 17 dark attachment-tab legibility."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "apply_fluidgram.py"
spec = importlib.util.spec_from_file_location("fluidgram_overlay", SCRIPT)
overlay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(overlay)


class DarkAttachmentContrastTests(unittest.TestCase):
    def test_inactive_tab_remains_legible_on_dark_surface(self):
        background = overlay.FLUIDGLASS_COLORS["dialogBackground"]
        inactive = overlay.FLUIDGLASS_COLORS["chat_attachUnactiveTab"]
        self.assertEqual(inactive, "#ffCADAE4")
        def luminance(color):
            rgb = [int(color[i:i + 2], 16) / 255 for i in (3, 5, 7)]
            linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
            return sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))
        high, low = sorted((luminance(background), luminance(inactive)), reverse=True)
        self.assertGreaterEqual((high + 0.05) / (low + 0.05), 4.5)


if __name__ == "__main__":
    unittest.main()
