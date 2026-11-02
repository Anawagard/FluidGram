"""Source-level regression for opt-in Telegram audio-upload activity suppression.
No claim of server-side invisibility; verify on a second Telegram account.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AudioUploadGhostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
        cls.patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
        cls.labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_only_outgoing_audio_activity_and_opt_in(self):
        method = self.privacy.split(
            "public static boolean shouldSuppressAudioUploadIndicator(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("instanceof TLRPC.TL_messages_setTyping", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageUploadAudioAction", method)
        self.assertIn('enabled("ghost_hide_audio_upload_indicator")', method)
        self.assertIn("getBoolean(key, false)", self.privacy)
        for forbidden in (
            "TL_messages_sendMedia", "TL_messages_setEncryptedTyping",
            "TL_messages_readHistory", "TL_account_updateStatus",
            "TL_sendMessageRecordAudioAction", "TL_sendMessageUploadDocumentAction",
        ):
            self.assertNotIn(forbidden, method)

    def test_network_hook_short_circuits_only_selected_activity(self):
        guard = self.patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
        guard = guard.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
        self.assertIn("|| FluidPrivacy.shouldSuppressAudioUploadIndicator(object)", guard)
        self.assertIn("No native_sendRequest", guard)
        self.assertIn("onComplete.run(new TLRPC.TL_boolTrue(), null)", guard)
        self.assertIn("onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)", guard)
        self.assertIn("return;", guard)

    def test_independent_control_disabled_by_default(self):
        self.assertIn('toggle("Не показывать «загружает аудио…»"', self.labs)
        self.assertIn('"ghost_hide_audio_upload_indicator", false', self.labs)
        self.assertIn('"ghost_hide_recording_audio", false', self.labs)
        self.assertIn("Сам файл и сообщение", self.labs)
        self.assertIn("Не скрывает статус онлайн, прочтение", self.labs)


if __name__ == "__main__":
    unittest.main()
