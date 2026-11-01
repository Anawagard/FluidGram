"""Compile and exercise the VPN controller with deterministic Android/WireGuard stubs.

This validates permission, RAM-only import, app-scoped routing and state
transitions; it is NOT a substitute for an Android APK/device tunnel test.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/content/Context.java": """package android.content;
public class Context { public Context getApplicationContext() { return this; } }
""",
    "android/content/Intent.java": "package android.content; public class Intent {}",
    "android/net/VpnService.java": """package android.net;
public class VpnService {
    public static boolean deny;
    public static android.content.Intent prepare(android.content.Context c) {
        return deny ? new android.content.Intent() : null;
    }
}
""",
    "android/os/Looper.java": """package android.os;
public class Looper { public static Looper getMainLooper() { return new Looper(); } }
""",
    "android/os/Handler.java": """package android.os;
public class Handler {
    public Handler(Looper l) {}
    public void post(Runnable runnable) { runnable.run(); }
}
""",
    "com/wireguard/android/backend/Tunnel.java": """package com.wireguard.android.backend;
public interface Tunnel {
    enum State { UP, DOWN }
    String getName();
    void onStateChange(State state);
}
""",
    "com/wireguard/android/backend/GoBackend.java": """package com.wireguard.android.backend;
import com.wireguard.config.Config;
public class GoBackend {
    private Tunnel.State state = Tunnel.State.DOWN;
    public GoBackend(android.content.Context context) {}
    public void setState(Tunnel tunnel, Tunnel.State newState, Config config) {
        if (newState == Tunnel.State.UP && config == null) throw new IllegalArgumentException();
        state = newState;
        tunnel.onStateChange(newState);
    }
    public Tunnel.State getState(Tunnel tunnel) { return state; }
}
""",
    "com/wireguard/config/Interface.java": """package com.wireguard.config;
import java.util.*;
public class Interface {
    private final Set<String> included = new HashSet<>();
    public Collection<String> getAddresses() { return Collections.emptyList(); }
    public Collection<String> getDnsServers() { return Collections.emptyList(); }
    public Collection<String> getDnsSearchDomains() { return Collections.emptyList(); }
    public Object getKeyPair() { return new Object(); }
    public Optional<Integer> getMtu() { return Optional.empty(); }
    public Optional<Integer> getListenPort() { return Optional.empty(); }
    public Set<String> getIncludedApplications() { return included; }
    public static class Builder {
        private Interface iface = new Interface();
        public Builder addAddresses(Collection<String> v) { return this; }
        public Builder addDnsServers(Collection<String> v) { return this; }
        public Builder addDnsSearchDomains(Collection<String> v) { return this; }
        public Builder setKeyPair(Object v) { return this; }
        public Builder includeApplication(String v) { iface.included.add(v); return this; }
        public Builder setMtu(int v) { return this; }
        public Builder setListenPort(int v) { return this; }
        public Interface build() { return iface; }
    }
}
""",
    "com/wireguard/config/Config.java": """package com.wireguard.config;
import java.io.*; import java.util.*;
public class Config {
    private Interface iface;
    private Collection<String> peers;
    public Config(Interface i, Collection<String> p) { iface=i; peers=p; }
    public static Config parse(InputStream input) throws IOException {
        while (input.read() >= 0) {}
        return new Config(new Interface.Builder().build(), Arrays.asList("peer"));
    }
    public Interface getInterface() { return iface; }
    public Collection<String> getPeers() { return peers; }
    public static class Builder {
        private Interface iface;
        private Collection<String> peers;
        public Builder setInterface(Interface i) { iface=i; return this; }
        public Builder addPeers(Collection<String> p) { peers=p; return this; }
        public Config build() { return new Config(iface, peers); }
    }
}
""",
}
HARNESS = r"""
import org.telegram.ui.FluidVpnProfile;
import org.telegram.ui.FluidVpnTunnel;
import android.net.VpnService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class TunnelSmoke {
    private static void waitFor(boolean target) throws Exception {
        for (int i=0;i<100;i++) {
            if (FluidVpnTunnel.isConnected() == target && !FluidVpnTunnel.isBusy()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out: " + FluidVpnTunnel.statusLabel());
    }
    public static void main(String[] args) throws Exception {
        String src = "[Interface]\nPrivateKey = " + "A".repeat(43) +
            "=\nAddress = 10.1.2.3/32, fd00::2/128\nDNS = 1.1.1.1\n" +
            "[Peer]\nPublicKey = " + "B".repeat(42) + "A=\n" +
            "Endpoint = example.com:51820\nAllowedIPs = 0.0.0.0/0, ::/0\n";
        FluidVpnTunnel.Imported imported = FluidVpnTunnel.importProfile(
            new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)), "dev.fluidgram.oneplus15.web");
        if (!imported.info.fullTunnel) throw new AssertionError("Metadata lost");
        if (!imported.config.getInterface().getIncludedApplications().contains("dev.fluidgram.oneplus15.web"))
            throw new AssertionError("Not app-scoped");
        if (imported.config.getInterface().getIncludedApplications().size() != 1)
            throw new AssertionError("Other apps included");
        android.content.Context context = new android.content.Context();
        VpnService.deny = true;
        if (FluidVpnTunnel.connect(context, imported.config, null))
            throw new AssertionError("Started without Android permission");
        VpnService.deny = false;
        if (!FluidVpnTunnel.connect(context, imported.config, null))
            throw new AssertionError("Connect rejected");
        waitFor(true);
        if (!FluidVpnTunnel.disconnect(null)) throw new AssertionError("Disconnect rejected");
        waitFor(false);
        if (FluidVpnTunnel.isConnected()) throw new AssertionError("Tunnel still connected");
        try {
            FluidVpnTunnel.importProfile(new ByteArrayInputStream(new byte[65537]), "app");
            throw new AssertionError("Oversized import accepted");
        } catch (FluidVpnProfile.InvalidProfile expected) {}
        System.out.println("VPN_CONTROLLER_OK");
    }
}
"""

class WireGuardControllerRegression(unittest.TestCase):
    def test_compile_and_permission_state_machine(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            paths = []
            for name, content in STUBS.items():
                p = base / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content, encoding="utf-8")
                paths.append(str(p))
            harness = base / "TunnelSmoke.java"
            harness.write_text(HARNESS, encoding="utf-8")
            paths += [str(harness), str(ROOT / "android" / "FluidVpnProfile.java"),
                      str(ROOT / "android" / "FluidVpnTunnel.java")]
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", d] + paths,
                           check=True, capture_output=True)
            out = subprocess.run(["java", "-cp", d, "TunnelSmoke"],
                                 check=True, capture_output=True, text=True)
            self.assertEqual(out.stdout.strip(), "VPN_CONTROLLER_OK")

    def test_android_manifest_and_pinned_dependency(self):
        patch = (ROOT / "scripts" / "apply_fluidgram.py").read_text()
        self.assertIn("com.wireguard.android:tunnel:1.0.20260102", patch)
        self.assertIn('"minSdkVersion 23", "minSdkVersion 24"', patch)
        self.assertIn("com.wireguard.android.backend.GoBackend$VpnService", patch)
        self.assertIn("android.permission.BIND_VPN_SERVICE", patch)
        self.assertIn('android:exported="false"', patch)
        self.assertIn('"FluidVpnTunnel.java"', patch)

    def test_consent_is_explicit_and_ui_has_disconnect(self):
        labs = (ROOT / "android" / "FluidLabsActivity.java").read_text()
        self.assertIn("android.net.VpnService.prepare(this)", labs)
        self.assertIn("VPN_PERMISSION_REQUEST", labs)
        self.assertIn("FluidVpnTunnel.connect(this", labs)
        self.assertIn("FluidVpnTunnel.disconnect(", labs)
        self.assertIn("pendingVpnConfig = null;", labs)

    def test_no_secret_persistence_or_background_polling(self):
        source = (ROOT / "android" / "FluidVpnTunnel.java").read_text()
        self.assertIn("Arrays.fill(bytes, (byte) 0)", source)
        self.assertIn("includeApplication(ownPackage)", source)
        for forbidden in ("SharedPreferences", "FileOutputStream", "Log.", "WakeLock",
                          "postDelayed", "Choreographer"):
            self.assertNotIn(forbidden, source)

    def test_power_guard_does_not_install_idle_scroll_listener(self):
        refresh = (ROOT / "android" / "FluidRefresh.java").read_text()
        self.assertIn('getInt("refresh_mode", 4) == 4', refresh)
        self.assertIn('getInt("refresh_mode", 4) == 5) && allowHighRefresh(a)', refresh)
        self.assertIn("lastPowerCheckMs = 0L; // Never reuse a stale guard", refresh)
        self.assertIn("if (powerOwner == a) stopPowerListener();", refresh)

if __name__ == "__main__":
    unittest.main()
