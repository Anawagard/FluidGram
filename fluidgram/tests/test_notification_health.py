"""Regression checks for on-demand Android push settings diagnostics.

These are source-level tests; they cannot prove actual FCM delivery latency.
"""
import unittest
from pathlib import Path

LABS = (Path(__file__).resolve().parents[1] / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")


class NotificationHealthTests(unittest.TestCase):
    def test_exposed_in_labs_and_user_initiated(self):
        self.assertIn('card("Проверка push · Android", "Диагностика по запросу"', LABS)
        self.assertIn("final String reportSnapshot = notificationHealthReport();", LABS)
        self.assertIn(".setMessage(reportSnapshot)", LABS)
        self.assertIn("ACTION_APP_NOTIFICATION_SETTINGS", LABS)

    def test_report_can_be_copied_only_on_user_action(self):
        dialog = LABS.split('card("Проверка push · Android"', 1)[1].split(
            'section("ЭНЕРГОЭФФЕКТИВНОСТЬ")', 1)[0]
        self.assertIn('.setNegativeButton("Скопировать отчёт"', dialog)
        self.assertIn("ClipboardManager", dialog)
        self.assertIn("ClipData.newPlainText(", dialog)
        self.assertIn("notificationHealthReport()", dialog)
        self.assertIn("if (clipboard == null)", dialog)
        self.assertIn('setPositiveButton("Закрыть"', dialog)
        self.assertNotIn("sendRequest", dialog)

    def test_checks_real_android_controls_with_api_guards(self):
        report = LABS.split("private String notificationHealthReport() {", 1)[1].split("\n    private void render()", 1)[0]
        self.assertIn("android.os.Build.VERSION.SDK_INT >= 33", report)
        self.assertIn("android.Manifest.permission.POST_NOTIFICATIONS", report)
        self.assertIn("android.os.Build.VERSION.SDK_INT >= 26", report)
        self.assertIn("manager.getNotificationChannels()", report)
        self.assertIn("IMPORTANCE_NONE", report)
        self.assertIn("isIgnoringBatteryOptimizations(getPackageName())", report)
        self.assertIn("power.isPowerSaveMode()", report)

    def test_no_push_suppression_or_background_polling(self):
        report = LABS.split("private String notificationHealthReport() {", 1)[1].split("\n    private void render()", 1)[0]
        for unsafe in ("WakeLock", "AlarmManager", "sendRequest", "setExact", "registerReceiver",
                       "startService", "Thread.sleep", "postDelayed", "SharedPreferences.Editor"):
            self.assertNotIn(unsafe, report)
        self.assertIn("НЕ тест доставки push", report)


if __name__ == "__main__":
    unittest.main()
