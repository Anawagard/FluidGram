"""ColorOS 17 styling regression for Telegram's real attachment sheet.

The palette is applied to Telegram ThemeColors and the user-selectable Dark Blue
asset; this test does not claim to create a runtime blur or change uploads.
"""
import ast
import re
import unittest
from pathlib import Path

PATCH = Path(__file__).resolve().parents[1] / "scripts" / "apply_fluidgram.py"


def palette(name):
    tree = ast.parse(PATCH.read_text(encoding="utf-8"))
    return ast.literal_eval(next(
        node.value for node in tree.body
        if isinstance(node, ast.Assign)
        and any(isinstance(target, ast.Name) and target.id == name
                for target in node.targets)
    ))


def rgb_contrast_with_white(color):
    rgb = [(color >> shift) & 255 for shift in (16, 8, 0)]
    linear = [v / 255 / 12.92 if v / 255 <= 0.04045
              else ((v / 255 + 0.055) / 1.055) ** 2.4 for v in rgb]
    luminance = sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))
    return 1.05 / (luminance + 0.05)


class AttachmentSheetColorosTest(unittest.TestCase):
    def test_real_telegram_attachment_keys(self):
        changes = palette("THEME_CHANGES")
        expected = {
            "key_chat_attachActiveTab": "0xff087C85",
            "key_chat_attachUnactiveTab": "0xff6C8794",
            "key_chat_attachGalleryBackground": "0xff087C85",
            "key_chat_attachAudioBackground": "0xffA84F60",
            "key_chat_attachPhotoBackground": "0x1016A6A2",
            "key_chat_attachPermissionText": "0xff486675",
            "key_chat_attachEmptyImage": "0xffC4D4DB",
        }
        for key, color in expected.items():
            with self.subTest(key=key):
                matches = [(before, after) for before, after in changes.items()
                           if before.startswith("defaultColors[" + key + "] =")]
                self.assertEqual(len(matches), 1)
                self.assertIn(color + ";", matches[0][1])

    def test_action_tiles_remain_readable_and_overlay_is_lightweight(self):
        changes = palette("THEME_CHANGES")
        for key in ("key_chat_attachGalleryBackground", "key_chat_attachAudioBackground"):
            value = next(after for before, after in changes.items() if key in before)
            color = int(value.split("0x", 1)[1].split(";", 1)[0], 16)
            self.assertGreaterEqual(rgb_contrast_with_white(color), 4.5)
        photo = next(after for before, after in changes.items()
                     if "key_chat_attachPhotoBackground" in before)
        argb = int(photo.split("0x", 1)[1].split(";", 1)[0], 16)
        self.assertLessEqual(argb >> 24, 0x20)

    def test_light_attachment_permission_copy_has_accessible_contrast(self):
        changes = palette("THEME_CHANGES")
        value = next(after for before, after in changes.items()
                     if before.startswith("defaultColors[key_chat_attachPermissionText] ="))
        color = int(value.split("0x", 1)[1].split(";", 1)[0], 16)
        self.assertEqual(color >> 24, 0xFF)
        self.assertGreaterEqual(rgb_contrast_with_white(color), 7.0)

    def test_dark_blue_attachment_palette_is_complete(self):
        dark = palette("FLUIDGLASS_COLORS")
        for key in (
            "chat_attachActiveTab", "chat_attachUnactiveTab",
            "chat_attachGalleryBackground", "chat_attachAudioBackground",
            "chat_attachPhotoBackground", "chat_attachCheckBoxBackground",
            "chat_attachPermissionText", "chat_attachEmptyImage",
        ):
            with self.subTest(key=key):
                self.assertRegex(dark[key], r"^#[0-9a-fA-F]{8}$")

    def test_no_frame_loop_or_network_modification(self):
        script = PATCH.read_text(encoding="utf-8")
        section = script[script.index("THEME_CHANGES = {"):
                         script.index("\n\ndef patch_round_camera_extras")]
        self.assertNotIn("Choreographer", section)
        self.assertNotIn("sendRequestInternal", section)
        self.assertNotIn("postFrameCallback", section)


if __name__ == "__main__":
    unittest.main()
