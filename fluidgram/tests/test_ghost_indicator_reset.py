"""Regression checks for the opt-in Ghost indicator reset action."""
import re
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")
KEYS = (
    "ghost_hide_typing",
    "ghost_hide_recording_audio",
    "ghost_hide_audio_upload_indicator",
    "ghost_hide_recording_video",
    "ghost_hide_recording_round",
    "ghost_hide_photo_upload_indicator",
    "ghost_hide_video_upload_indicator",
    "ghost_hide_document_upload_indicator",
    "ghost_hide_choosing_sticker",
)


class GhostIndicatorResetTests(unittest.TestCase):
    def test_all_indicators_are_in_reset_list(self):
        match = re.search(r"GHOST_INDICATOR_KEYS = \\{(.*?)\\};", SOURCE, re.S)
        self.assertIsNotNone(match)
        self.assertEqual(set(re.findall(r'"(ghost_[^"]+)"', match.group(1))), set(KEYS))

    def test_reset_requires_confirmation_and_disables_only_indicators(self):
        start = SOURCE.index('card("Сброс индикаторов Ghost"')
        end = SOURCE.index('section("УВЕДОМЛЕНИЯ  /  TELEGRAM")', start)
        block = SOURCE[start:end]
        self.assertIn('new AlertDialog.Builder(this)', block)
        self.assertIn('.setPositiveButton("Сбросить"', block)
        self.assertIn('.setNegativeButton("Отмена", null)', block)
        self.assertIn('for (String key : GHOST_INDICATOR_KEYS) editor.putBoolean(key, false);', block)
        self.assertIn('editor.apply();', block)
        self.assertNotIn('preferences.edit().clear()', block)


if __name__ == "__main__":
    unittest.main()
