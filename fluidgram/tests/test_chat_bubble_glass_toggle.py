"""The experimental bubble switch must affect real Telegram theme color reads."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GLASS = (ROOT / "android/FluidGlass.java").read_text(encoding="utf-8")
LABS = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")
PATCH = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")

class ChatBubbleGlassToggleTests(unittest.TestCase):
    def test_user_opt_in_default_off_and_restart_hint(self):
        self.assertIn('"experimental_glass_bubbles", false', LABS)
        self.assertIn('Перезапустите FluidGram', LABS)
        self.assertIn('Для применения полностью закройте', LABS)

    def test_actual_telegram_theme_color_interception(self):
        self.assertIn('rewrite(theme_file, patch_bubble_glass)', PATCH)
        self.assertIn('FluidGlass.chatBubbleColor(', PATCH)
        self.assertIn('fluidGramBaseColor(key, isDefault, ignoreAnimation)', PATCH)
        self.assertIn('return replace_exact(s, original, replacement, theme_file)', PATCH)

    def test_only_bubble_colors_modified_when_enabled(self):
        self.assertIn('Theme.key_chat_inBubble', GLASS)
        self.assertIn('Theme.key_chat_outBubble', GLASS)
        self.assertIn('if (!enabled) return color;', GLASS)
        self.assertIn('int alpha = (color >>> 24) / 2;', GLASS)
        self.assertIn('(color & 0x00FFFFFF) | (alpha << 24)', GLASS)
        self.assertIn('getBoolean("experimental_glass_bubbles", false)', GLASS)

    def test_no_network_or_background_activity(self):
        self.assertNotIn('sendRequestInternal', GLASS)
        self.assertNotIn('Choreographer', GLASS)
        self.assertNotIn('postDelayed', GLASS)

if __name__ == "__main__":
    unittest.main()
