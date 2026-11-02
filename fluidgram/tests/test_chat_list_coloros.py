"""Regression tests for ColorOS-style Telegram chat-list colors.

These keys are consumed by Telegram's real DialogsActivity/Theme renderer,
not by the standalone FluidGram Labs screen.
"""
import ast
import unittest
from pathlib import Path

PATCH = Path(__file__).resolve().parents[1] / "scripts" / "apply_fluidgram.py"


def palette(name):
    tree = ast.parse(PATCH.read_text(encoding="utf-8"))
    assignment = next(
        node for node in tree.body
        if isinstance(node, ast.Assign)
        and any(isinstance(t, ast.Name) and t.id == name for t in node.targets)
    )
    return ast.literal_eval(assignment.value)


class ChatListColorosRegression(unittest.TestCase):
    def test_light_theme_patches_real_chat_list_keys(self):
        changes = palette("THEME_CHANGES")
        expected = {
            "key_chats_tabUnreadActiveBackground": "0xff16A6A2",
            "key_chats_pinnedOverlay": "0x0B16A6A2",
            "key_chats_tabletSelectedOverlay": "0x1816A6A2",
            "key_chats_message": "0xff647C89",
            "key_chats_actionBackground": "0xff16A6A2",
            "key_chats_actionPressedBackground": "0xff0B817E",
        }
        for key, color in expected.items():
            with self.subTest(key=key):
                matching = [new for old, new in changes.items()
                            if "defaultColors[" + key + "]" in old]
                self.assertEqual(len(matching), 1)
                self.assertIn(color, matching[0])

    def test_dark_blue_preset_covers_chat_list_and_navigation(self):
        dark = palette("FLUIDGLASS_COLORS")
        expected = (
            "actionBarTabActiveText", "actionBarTabUnactiveText",
            "actionBarTabLine", "chats_tabUnreadActiveBackground",
            "chats_tabUnreadUnactiveBackground", "chats_pinnedOverlay",
            "chats_tabletSelectedOverlay", "chats_archiveBackground",
            "chats_actionPressedBackground", "chats_messageArchived",
            "dialogSearchHint",
        )
        for key in expected:
            with self.subTest(key=key):
                self.assertRegex(dark[key], r"^#[0-9a-fA-F]{8}$")

    def test_translucent_overlays_and_no_animation_loop(self):
        changes = palette("THEME_CHANGES")
        for key in ("key_chats_pinnedOverlay", "key_chats_tabletSelectedOverlay"):
            value = next(new for old, new in changes.items() if key in old)
            color = int(value.rsplit("0x", 1)[1].split(";", 1)[0], 16)
            self.assertLess((color >> 24) & 0xff, 0x40)
        script = PATCH.read_text(encoding="utf-8")
        start = script.index("THEME_CHANGES = {")
        end = script.index("\n\ndef patch_round_camera_extras", start)
        palette_section = script[start:end]
        self.assertNotIn("Choreographer", palette_section)
        self.assertNotIn("postDelayed", palette_section)


if __name__ == "__main__":
    unittest.main()
