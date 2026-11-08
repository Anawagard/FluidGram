"""Source regression guards for truthful foreground charge-counter reports."""
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "android" / "FluidEnergy.java").read_text(encoding="utf-8")

class EnergyReportTests(unittest.TestCase):
    def test_charging_and_invalid_intervals_excluded(self):
        self.assertIn("Intent.ACTION_BATTERY_CHANGED", SOURCE)
        self.assertIn("BatteryManager.BATTERY_STATUS_DISCHARGING", SOURCE)
        self.assertIn("BatteryManager.BATTERY_STATUS_NOT_CHARGING", SOURCE)
        self.assertIn("plugged == 0 && (status ==", SOURCE)
        self.assertIn("startedUnplugged && unplugged(activity)", SOURCE)
        self.assertIn("startedUnplugged && unplugged(context)", SOURCE)
        self.assertIn("startedCharge >= end", SOURCE)
        self.assertIn("skippedChargingSegments++", SOURCE)
        self.assertIn("Excluded charging/uncertain segments:", SOURCE)

    def test_discharge_rate_uses_only_valid_interval_duration(self):
        self.assertIn("measuredForegroundMs +=", SOURCE)
        self.assertIn("validMs +=", SOURCE)
        self.assertIn("totalDelta * 3600.0 / validMs", SOURCE)
        self.assertIn("samples > 0 && validMs > 0L", SOURCE)
        self.assertIn("NOT app-only", SOURCE)
        self.assertIn("charging between endpoint checks cannot be detected", SOURCE)
        # 1000 microamp-hours over one hour equals 1 milliamp.
        self.assertAlmostEqual(1000 * 3600.0 / 3600000, 1.0)

    def test_report_uses_actual_newlines_not_literal_backslash_n(self):
        self.assertIn('append(" s\\n");', SOURCE)
        self.assertIn('NOT app-only)\\n");', SOURCE)
        self.assertNotIn(r'append(" s\\n");', SOURCE)
        self.assertNotIn(r'NOT app-only)\\n");', SOURCE)

    def test_no_periodic_background_sampling(self):
        for forbidden in ("WakeLock", "AlarmManager", "postDelayed", "Thread.sleep"):
            self.assertNotIn(forbidden, SOURCE)
        self.assertIn("WHOLE PHONE", SOURCE)
        self.assertIn("No background sampling", SOURCE)

if __name__ == "__main__":
    unittest.main()
