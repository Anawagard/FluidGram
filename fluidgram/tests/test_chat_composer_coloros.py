"""Regression checks for ColorOS 17 conversation and message composer styling.

These keys render inside Telegram's actual ChatActivity and message composer,
not the FluidGram Labs settings screen.
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


class ConversationColorosRegression(unittest.TestCase):
    def test_real_telegram_chat_composer_keys(self):
        light = palette("THEME_CHANGES")
        expected = {
            "key_chat_inBubble": "0xD0F8FCFE",
            "key_chat_messagePanelBackground": "0xF2F8FCFE",
            "key_chat_messagePanelHint": "0xff728A97",
            "key_chat_messagePanelIcons": "0xff6C8794",
            "key_chat_messagePanelCursor": "0xff16A6A2",
            "key_chat_outReplyLine": "0xff16A6A2",
            "key_chat_outSentCheck": "0xff168C86",
        }
        for key, color in expected.items():
            with self.subTest(key=key):
                values = [v for k, v in light.items()
                          if k.startswith("defaultColors[" + key + "] =")]
                self.assertEqual(len(values), 1)
                self.assertIn(color + ";", values[0])

    def test_light_chat_bubbles_are_frosted_but_selected_remain_readable(self):
        # Actual Telegram chat bubble theme keys, not a Labs-only preview.
        light = palette("THEME_CHANGES")
        expected = {
            "key_chat_inBubble": 0xD0F8FCFE,
            "key_chat_outBubble": 0xC8DDF7F4,
            "key_chat_inBubbleSelected": 0xE4E7F3F7,
            "key_chat_outBubbleSelected": 0xE4CAEEE9,
        }
        for key, color in expected.items():
            with self.subTest(key=key):
                old = next(k for k in light if k.startswith("defaultColors[" + key + "] ="))
                value = int(light[old].split("0x", 1)[1].split(";", 1)[0], 16)
                self.assertEqual(value, color)
                self.assertGreaterEqual(value >> 24, 0xC0)
                self.assertLess(value >> 24, 0xFF)
        for side in ("in", "out"):
            self.assertGreater(
                expected["key_chat_" + side + "BubbleSelected"] >> 24,
                expected["key_chat_" + side + "Bubble"] >> 24,
            )

    def test_light_composer_is_subtly_translucent_not_invisible(self):
        light = palette("THEME_CHANGES")
        value = next(v for k, v in light.items()
                     if k.startswith("defaultColors[key_chat_messagePanelBackground] ="))
        argb = int(value.split("0x", 1)[1].split(";", 1)[0], 16)
        self.assertGreaterEqual(argb >> 24, 0xE0)
        self.assertLess(argb >> 24, 0xFF)

    def test_dark_blue_chat_composer_has_matching_controls(self):
        dark = palette("FLUIDGLASS_COLORS")
        for key in (
            "chat_messagePanelBackground", "chat_messagePanelHint",
            "chat_messagePanelIcons", "chat_messagePanelCursor",
            "chat_messagePanelSend", "chat_inBubbleSelected",
            "chat_outReplyLine", "chat_inReplyLine",
            "chat_outTimeText", "chat_outSentCheck"
        ):
            with self.subTest(key=key):
                self.assertRegex(dark[key], r"^#[0-9a-fA-F]{8}$")
        self.assertEqual(dark["chat_messagePanelBackground"], "#6D212C3A")
        self.assertEqual(dark["chat_messagePanelSend"], "#ff62F3DC")

    def test_no_new_frame_callbacks_or_network_changes(self):
        script = PATCH.read_text(encoding="utf-8")
        section = script[script.index("THEME_CHANGES = {"):
                         script.index("\n\ndef patch_round_camera_extras")]
        self.assertNotIn("Choreographer", section)
        self.assertNotIn("postDelayed", section)
        self.assertNotIn("sendRequestInternal", section)


if __name__ == "__main__":
    unittest.main()
