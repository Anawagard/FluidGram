"""Compile and exercise the actual Java WireGuard preflight parser on CI JDK."""
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
        return "[Interface]\nPrivateKey = " + "A".repeat(43) + "=\nAddress = 10.1.2.3/32, fd00::2/128\nDNS = 1.1.1.1\n" +
               "[Peer]\nPublicKey = " + "B".repeat(42) + "A=\nEndpoint = example.com:51820\nAllowedIPs = 0.0.0.0/0, ::/0\n";
    }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "valid" : args[0];
        String src = profile();
        switch (mode) {
            case "bad": src = src.replace("PrivateKey", "NotAKey"); break;
            case "badpadding": src = src.replace("B".repeat(42) + "A=", "B".repeat(43) + "="); break;
            case "large": src += "#" + "Z".repeat(66000); break;
            case "noendpoint": src = src.replace("Endpoint", "NotEndpoint"); break;
            case "split": src = src.replace("0.0.0.0/0, ::/0", "10.0.0.0/8"); break;
            case "ipv4only": src = src.replace("0.0.0.0/0, ::/0", "0.0.0.0/0"); break;
            case "ipv6only": src = src.replace("0.0.0.0/0, ::/0", "::/0"); break;
            case "badroute": src = src.replace("0.0.0.0/0, ::/0", "999.2.3.4/55"); break;
            case "badaddress": src = src.replace("10.1.2.3/32", "10.1.2.999/32"); break;
            case "badport": src = src.replace(":51820", ":65536"); break;
            case "badipv6": src = src.replace("::/0", "2001:db8::g/64"); break;
            case "badipv6endpoint": src = src.replace("example.com:51820", "[2001:db8::g]:51820"); break;
            case "badipv6colon": src = src.replace("example.com:51820", "[1:2:3:4:5:6:7:8:]:51820"); break;
            case "unsafe": src += "PostUp = echo unsafe\n"; break;
            case "duplicate": src = src.replace("Address = ", "PrivateKey = " + "A".repeat(43) + "=\nAddress = "); break;
            case "ipv6endpoint": src = src.replace("example.com:51820", "[2001:db8::1]:51820"); break;
            case "noipv6addr": src = src.replace(", fd00::2/128", ""); break;
            case "baddns": src = src.replace("DNS = 1.1.1.1", "DNS = https://evil.invalid/path"); break;
            case "baddnscomma": src = src.replace("DNS = 1.1.1.1", "DNS = 1.1.1.1,"); break;
            case "dnsdomain": src = src.replace("DNS = 1.1.1.1", "DNS = dns.example.com"); break;
            case "dnsipv6": src = src.replace("DNS = 1.1.1.1", "DNS = 2606:4700:4700::1111"); break;
            case "badendpointhost": src = src.replace("example.com:51820", "-bad..host:51820"); break;
            case "duplicateDefault": src = src.replace("0.0.0.0/0, ::/0", "0.0.0.0/0, ::/0, 0.0.0.0/0"); break;
            case "badipv6address": src = src.replace("fd00::2/128", "fd00:::2/128"); break;
            case "multi": src += "[Peer]\nPublicKey = " + "C".repeat(42) + "A=\nEndpoint = peer.example:1234\nAllowedIPs = 10.0.0.0/8\n"; break;
        }
        boolean valid = mode.equals("valid") || mode.equals("split") ||
                mode.equals("ipv4only") || mode.equals("ipv6only") ||
                mode.equals("ipv6endpoint") || mode.equals("multi") ||
                mode.equals("noipv6addr") || mode.equals("dnsdomain") || mode.equals("dnsipv6");
        try {
            FluidVpnProfile.Info info = FluidVpnProfile.inspect(new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
            if (!valid) throw new AssertionError("Unsafe profile accepted: " + mode);
            if (info.peers != (mode.equals("multi") ? 2 : 1) || !info.hasDns)
                throw new AssertionError("Wrong metadata: " + mode);
            if (info.fullTunnel != !(mode.equals("split") || mode.equals("ipv4only") || mode.equals("ipv6only")))
                throw new AssertionError("Full tunnel misclassified: " + mode);
            if (info.ipv4Default != !(mode.equals("split") || mode.equals("ipv6only")))
                throw new AssertionError("IPv4 default misclassified");
            if (info.ipv6Default != !(mode.equals("split") || mode.equals("ipv4only")))
                throw new AssertionError("IPv6 default misclassified");
            if (!info.ipv4Address || info.ipv6Address == mode.equals("noipv6addr"))
                throw new AssertionError("Interface address families misclassified");
            if (info.dualStackConfigured != (info.fullTunnel && !mode.equals("noipv6addr")))
                throw new AssertionError("Dual-stack configuration misclassified");
            System.out.println("OK");
        } catch (FluidVpnProfile.InvalidProfile e) {
            if (valid) throw e;
            if (e.getMessage().contains("AAAA") || e.getMessage().contains("BBBB"))
                throw new AssertionError("Credential leaked");
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
    def test_reject_noncanonical_key_padding(self): self.check("badpadding", "REJECTED")
    def test_reject_oversized_profile(self): self.check("large", "REJECTED")
    def test_reject_peer_without_endpoint(self): self.check("noendpoint", "REJECTED")
    def test_split_tunnel_not_mislabelled(self): self.check("split", "OK")
    def test_ipv4_only_is_not_full_tunnel(self): self.check("ipv4only", "OK")
    def test_ipv6_only_is_not_full_tunnel(self): self.check("ipv6only", "OK")
    def test_reject_invalid_allowed_ips(self): self.check("badroute", "REJECTED")
    def test_reject_invalid_interface_address(self): self.check("badaddress", "REJECTED")
    def test_reject_invalid_endpoint_port(self): self.check("badport", "REJECTED")
    def test_reject_invalid_ipv6_route(self): self.check("badipv6", "REJECTED")
    def test_reject_invalid_ipv6_endpoint(self): self.check("badipv6endpoint", "REJECTED")
    def test_reject_trailing_colon_ipv6(self): self.check("badipv6colon", "REJECTED")
    def test_reject_unsupported_shell_directive(self): self.check("unsafe", "REJECTED")
    def test_reject_duplicate_private_key(self): self.check("duplicate", "REJECTED")
    def test_accept_numeric_ipv6_endpoint(self): self.check("ipv6endpoint", "OK")
    def test_accept_multiple_peers(self): self.check("multi", "OK")
    def test_warn_missing_ipv6_interface_address(self): self.check("noipv6addr", "OK")
    def test_reject_url_in_dns(self): self.check("baddns", "REJECTED")
    def test_reject_empty_dns_entry(self): self.check("baddnscomma", "REJECTED")
    def test_accept_dns_hostname(self): self.check("dnsdomain", "OK")
    def test_accept_dns_ipv6(self): self.check("dnsipv6", "OK")
    def test_reject_bad_endpoint_hostname(self): self.check("badendpointhost", "REJECTED")
    def test_reject_duplicate_default_route(self): self.check("duplicateDefault", "REJECTED")
    def test_reject_bad_ipv6_interface_address(self): self.check("badipv6address", "REJECTED")

    def test_no_persistence_or_background_work(self):
        source = (ROOT / "android" / "FluidVpnProfile.java").read_text()
        self.assertNotIn("SharedPreferences", source)
        self.assertNotIn("Log.", source)
        self.assertNotIn("WakeLock", source)
        self.assertNotIn("VpnService", source)  # no fake tunnel or permission prompt
        self.assertIn("Arrays.fill(raw, (byte) 0)", source)
        self.assertIn("ipv4Default && ipv6Default", source)
        labs = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", labs)
        self.assertIn("FluidVpnProfile.inspect(input)", labs)
        self.assertIn("VPN · аудит профиля", labs)
        self.assertIn("info.dualStackConfigured", labs)

if __name__ == "__main__": unittest.main()
