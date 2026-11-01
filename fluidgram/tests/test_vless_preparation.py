from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class VlessPreparationTests(unittest.TestCase):
    def test_imported_into_android_project(self):
        p=(ROOT/"scripts/apply_fluidgram.py").read_text()
        self.assertIn('"FluidVlessProfile.java"',p)
    def test_ui_is_explicit_about_no_connection(self):
        ui=(ROOT/"android/FluidLabsActivity.java").read_text()
        self.assertIn('FluidVlessProfile.inspect(',ui)
        self.assertIn('Подключение недоступно до интеграции движка',ui)
    def test_no_secret_persistence(self):
        src=(ROOT/"android/FluidVlessProfile.java").read_text()
        for forbidden in ("SharedPreferences","FileOutputStream","Log.","Socket","VpnService"):
            self.assertNotIn(forbidden,src)
    def test_rejects_insecure_transport(self):
        src=(ROOT/"android/FluidVlessProfile.java").read_text()
        self.assertIn('security.equals("tls")',src)
        self.assertIn('security.equals("reality")',src)
        self.assertIn('UUID.fromString(user)',src)
if __name__=="__main__": unittest.main()
