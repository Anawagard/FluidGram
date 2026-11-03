"""Regression checks for opt-in outgoing document-upload activity filtering.

Source-level checks only. A second Telegram account is required to verify
what recipients see; these tests do not claim server-side invisibility.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class DocumentUploadGhostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
        cls.patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
        cls.labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_only_document_upload_typing_rpc_and_opt_in(self):
        method = self.privacy.split(
            "public static boolean shouldSuppressDocumentUploadIndicator(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("instanceof TLRPC.TL_messages_setTyping", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageUploadDocumentAction", method)
        self.assertIn('enabled("ghost_hide_document_upload_indicator")', method)
        self.assertIn("getBoolean(key, false)", self.privacy)
        for forbidden in (
            "TL_messages_sendMedia", "TL_messages_setEncryptedTyping",
            "TL_messages_readHistory", "TL_account_updateStatus",
            "TL_sendMessageUploadVideoAction", "TL_sendMessageUploadPhotoAction"
        ):
            self.assertNotIn(forbidden, method)

    def test_guard_precedes_real_native_network_dispatch(self):
        guard = self.patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
        guard = guard.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
        self.assertIn("|| FluidPrivacy.shouldSuppressDocumentUploadIndicator(object)", guard)
        self.assertIn("No native_sendRequest", guard)
        self.assertIn("onComplete.run(new TLRPC.TL_boolTrue(), null)", guard)
        self.assertIn("return;", guard)

    def test_independent_disabled_by_default_with_limits(self):
        self.assertIn('toggle("Не показывать «отправляет файл…»"', self.labs)
        self.assertIn('"ghost_hide_document_upload_indicator", false', self.labs)
        self.assertIn('"ghost_hide_video_upload_indicator", false', self.labs)
        self.assertIn("Передача файлов и сообщений", self.labs)
        self.assertIn("Статус онлайн, прочтение", self.labs)


if __name__ == "__main__":
    unittest.main()
