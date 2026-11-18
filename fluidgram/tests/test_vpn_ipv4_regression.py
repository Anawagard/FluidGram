"""Exercise WireGuard IPv4 parser using Java 17, not a source-text assertion."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "android" / "FluidVpnProfile.java"

HARNESS = """
import java.lang.reflect.Method;
public class ProfileIpv4Regression {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("org.telegram.ui.FluidVpnProfile");
        Method ipv4 = cls.getDeclaredMethod("isIpv4", String.class);
        ipv4.setAccessible(true);
        String[] accepted = {"0.0.0.0", "192.168.1.1", "255.255.255.255"};
        String[] rejected = {"010.0.0.1", "192.168.001.1", "00.0.0.0",
            "256.1.1.1", "1.2.3", "1.2.3.4.5", "1.2.3.-1"};
        for (String value : accepted)
            if (!Boolean.TRUE.equals(ipv4.invoke(null, value)))
                throw new AssertionError("Valid IPv4 rejected: " + value);
        for (String value : rejected)
            if (!Boolean.FALSE.equals(ipv4.invoke(null, value)))
                throw new AssertionError("Ambiguous/invalid IPv4 accepted: " + value);
    }
}
"""

class WireGuardIpv4Regression(unittest.TestCase):
    def test_ipv4_parser_rejects_ambiguous_octets(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK unavailable; GitHub validate installs Java 17")
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            harness = root / "ProfileIpv4Regression.java"
            harness.write_text(HARNESS, encoding="utf-8")
            subprocess.run(["javac", "-d", str(root), str(SOURCE), str(harness)],
                           check=True, capture_output=True, text=True)
            subprocess.run(["java", "-cp", str(root), "ProfileIpv4Regression"],
                           check=True, capture_output=True, text=True)

if __name__ == "__main__":
    unittest.main()
