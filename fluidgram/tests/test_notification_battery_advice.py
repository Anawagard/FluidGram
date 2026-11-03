"""Battery advice in push diagnostics must be conditional, read-only and actionable."""
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")


class NotificationBatteryAdviceTest(unittest.TestCase):
    def test_conditional_battery_advice(self):
        start = SOURCE.index("private String notificationHealthReport()")
        end = SOURCE.index("// App Standby is independent", start)
        report = SOURCE[start:end]
        self.assertIn("boolean optimized = !power.isIgnoringBatteryOptimizations(getPackageName());", report)
        self.assertIn("boolean powerSave = power.isPowerSaveMode();", report)
        self.assertIn("if (optimized || powerSave)", report)
        self.assertIn("if (powerSave) report.append", report)
        self.assertIn("if (optimized) report.append", report)
        self.assertIn("проверьте разрешение фоновой работы FluidGram в ColorOS", report)
        self.assertIn("временно отключите общее энергосбережение", report)
        self.assertNotIn("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", report)
        self.assertNotIn("startActivity(", report)


if __name__ == "__main__":
    unittest.main()
