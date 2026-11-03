"""On-demand Android App Standby diagnostics must remain read-only and API guarded."""
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")


class NotificationStandbyDiagnosticsTest(unittest.TestCase):
    def test_standby_bucket_is_guarded_and_read_only(self):
        start = SOURCE.index("private String notificationHealthReport()")
        end = SOURCE.index("private void render()", start)
        report = SOURCE[start:end]
        self.assertIn("android.os.Build.VERSION.SDK_INT >= 28", report)
        self.assertIn("getSystemService(Context.USAGE_STATS_SERVICE)", report)
        self.assertIn("usage.getAppStandbyBucket()", report)
        self.assertIn("STANDBY_BUCKET_RESTRICTED", report)
        self.assertIn("Режим ожидания приложения:", report)
        self.assertIn("catch (SecurityException ignored)", report)
        self.assertNotIn("setAppStandbyBucket(", report)
        self.assertNotIn("startActivity(", report)


if __name__ == "__main__":
    unittest.main()
