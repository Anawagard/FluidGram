"""Regression checks for event-driven thermal protection; device validation still required."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REFRESH = (ROOT / "android" / "FluidRefresh.java").read_text(encoding="utf-8")
LABS = (ROOT / "android" / "FluidLabsActivity.java").read_text(encoding="utf-8")

class ThermalGuardRegression(unittest.TestCase):
    def test_android_10_guard_and_severe_threshold(self):
        self.assertIn("Build.VERSION.SDK_INT < 29", REFRESH)
        self.assertIn("PowerManager.THERMAL_STATUS_SEVERE", REFRESH)
        self.assertIn("!isThermallyConstrained(a)", REFRESH)

    def test_foreground_listener_unregisters_on_pause(self):
        self.assertIn("addThermalStatusListener(thermalListener)", REFRESH)
        self.assertIn("removeThermalStatusListener(thermalListener)", REFRESH)
        self.assertIn("if (powerOwner == a) stopPowerListener();", REFRESH)
        self.assertIn("if (thermalKnown) return thermalHot;", REFRESH)
        self.assertIn("if (thermalKnown && thermalHot == hot) return;", REFRESH)

    def test_glass_blur_respects_thermal_status(self):
        self.assertIn("void updateGlassThermalPolicy()", LABS)
        self.assertIn("!FluidRefresh.isThermallyConstrained(this)", LABS)
        self.assertNotIn("new BroadcastReceiver()", LABS)
        self.assertIn("updateGlassPowerPolicy(-1)", LABS)
        self.assertIn("((FluidLabsActivity) a).updateGlassThermalPolicy();", REFRESH)
        self.assertIn("FluidGlass.setAmbientBlur(glassBackdrop, false)", LABS)

    def test_no_thermal_polling_or_extra_wakelocks(self):
        self.assertNotIn("postFrameCallback", REFRESH)
        self.assertNotIn("WakeLock", REFRESH)
        self.assertNotIn("Thread.sleep", REFRESH)
        self.assertIn("Thermal guard severe+", REFRESH)

if __name__ == "__main__":
    unittest.main()
