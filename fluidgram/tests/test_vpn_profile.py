"""Compile and execute the actual Java WireGuard preflight parser using the CI JDK."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
import org.telegram.ui.FluidVpnProfile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
public final class FluidVpnProfileSmoke {
    private static String profile() {
        return "[Interface]\nPrivateKey = " + "A".repeat(43) + "=\nAddress = 10.1.2.3/32\nDNS = 1.1.1.1\n" +
               "[Peer]\nPublicKey = " + "B".repeat(43) + "=\nEndpoint = example.com:51820\nAllowedIPs = 0.0.0.0/0, ::/0\n";
    }
    public static void main(String[] args) throws Exception {
        String src = profile();
        if (args.length > 0 && args[0].equals("bad")) src = src.replace("PrivateKey", "NotAKey");
        if (args.length > 0 && args[0].equals("large")) src += "#" + "Z".repeat(66000);
        if (args.length > 0 && args[0].equals("noendpoint")) src = src.replace("Endpoint", "NotEndpoint");
        if (args.length > 0 && args[0].equals("split")) src = src.replace("0.0.0.0/0, ::/0", "10.0.0.0/8");
        try {
            FluidVpnProfile.Info info = FluidVpnProfile.inspect(new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
            if (args.length == 0 && (info.peers != 1 || !info.hasDns || !info.fullTunnel)) throw new AssertionError("Invalid metadata");
            if (args.length > 0 && args[0].equals("split") && info.fullTunnel) throw new AssertionError("Wrong route");
            if (args.length > 0 && !args[0].equals("split")) throw new AssertionError("Invalid profile accepted");
            System.out.println("OK");
        } catch (FluidVpnProfile.InvalidProfile e) {
            if (args.length == 0 || args[0].equals("split")) throw e;
            if (e.getMessage().contains("AAAA") || e.getMessage().contains("BBBB")) throw new AssertionError("Credential leaked");
            System.out.println("REJECTED");
        }
    }
}
'''

class WireGuardPreflight(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.path = Path(cls.tmp.name)
        (cls.path / "FluidVpnProfileSmoke.java").write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(cls.path),
                        str(ROOT / "android" / "FluidVpnProfile.java"),
                        str(cls.path / "FluidVpnProfileSmoke.java")], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def check(self, mode, expected):
        args = ["java", "-cp", str(self.path), "FluidVpnProfileSmoke"]
        if mode: args.append(mode)
        result = subprocess.run(args, check=True, capture_output=True, text=True)
        self.assertEqual(result.stdout.strip(), expected)

    def test_valid_profile_metadata_only(self): self.check(None, "OK")
    def test_reject_missing_private_key(self): self.check("bad", "REJECTED")
    def test_reject_oversized_profile(self): self.check("large", "REJECTED")
    def test_reject_peer_without_endpoint(self): self.check("noendpoint", "REJECTED")
    def test_split_tunnel_not_mislabelled(self): self.check("split", "OK")

    def test_no_persistence_or_background_work(self):
        source = (ROOT / "android" / "FluidVpnProfile.java").read_text()
        self.assertNotIn("SharedPreferences", source)
        self.assertNotIn("Log.", source)
        self.assertNotIn("WakeLock", source)
        self.assertNotIn("VpnService", source)  # no fake tunnel or permission prompt
        self.assertIn("Arrays.fill(raw, (byte) 0)", source)
        labs = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", labs)
        self.assertIn("FluidVpnProfile.inspect(input)", labs)
        self.assertIn("VPN · подготовка", labs)

if __name__ == "__main__": unittest.main()
