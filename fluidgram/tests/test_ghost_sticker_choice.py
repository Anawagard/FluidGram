"""Source-level regression for opt-in sticker-choice activity suppression.

Network filtering is scoped to messages.setTyping(chooseSticker), not sticker
messages or server-side online/read status. Verify the visible result using a
second Telegram account on a real device.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class StickerChoiceGhostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
        cls.patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
        cls.labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_only_ordinary_sticker_choice_activity_opt_in(self):
        method = self.privacy.split(
            "public static boolean shouldSuppressChoosingSticker(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("instanceof TLRPC.TL_messages_setTyping", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageChooseStickerAction", method)
        self.assertIn('enabled("ghost_hide_choosing_sticker")', method)
        self.assertIn("getBoolean(key, false)", self.privacy)
        for unrelated in (
            "TL_messages_sendMedia", "TL_messages_setEncryptedTyping",
            "TL_messages_readHistory", "TL_account_updateStatus",
            "TL_sendMessageTypingAction", "TL_sendMessageCancelAction",
        ):
            self.assertNotIn(unrelated, method)

    def test_network_intercept_precedes_native_send(self):
        guard = self.patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
        guard = guard.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
        self.assertIn("|| FluidPrivacy.shouldSuppressChoosingSticker(object)) {", guard)
        self.assertIn("No native_sendRequest", guard)
        self.assertIn("onComplete.run(new TLRPC.TL_boolTrue(), null)", guard)
        self.assertIn("onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)", guard)
        self.assertIn("return;", guard)

    def test_separate_off_by_default_ui_and_limitations(self):
        self.assertIn('toggle("Не показывать «выбирает стикер…»"', self.labs)
        self.assertIn('"ghost_hide_choosing_sticker", false', self.labs)
        self.assertIn('"ghost_hide_typing", false', self.labs)
        self.assertIn("Сами стикеры и сообщения", self.labs)
        self.assertIn("Не скрывает онлайн, прочтение", self.labs)


if __name__ == "__main__":
    unittest.main()
