package org.telegram.ui;

import android.content.Context;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.Interface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Opt-in, RAM-only WireGuard tunnel. No bundled servers, profile persistence,
 * background polling, secret logging, or wake locks. The Android OS must
 * authorize VpnService before connect() is called.
 *
 * The official WireGuard backend owns the Android VPN service and socket
 * protection. Only this app is included in the VPN route.
 */
public final class FluidVpnTunnel {
    public interface Callback { void onComplete(boolean success); }

    public static final class Imported {
        public final FluidVpnProfile.Info info;
        public final Config config;
        private Imported(FluidVpnProfile.Info info, Config config) {
            this.info = info;
            this.config = config;
        }
    }

    private enum Status { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }
    private static final Object LOCK = new Object();
    private static volatile Status status = Status.DISCONNECTED;
    private static GoBackend backend;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Tunnel TUNNEL = new Tunnel() {
        @Override public String getName() { return "FluidGram"; }
        @Override public void onStateChange(State state) {
            if (state == State.DOWN && status == Status.CONNECTED) {
                status = Status.DISCONNECTED;
            }
        }
    };

    private FluidVpnTunnel() {}

    /**
     * Bounded import, using the existing strict preflight and the official
     * WireGuard parser. Raw file bytes are wiped before returning. Immutable
     * parsed key objects necessarily remain in RAM until disconnected/GC.
     */
    public static Imported importProfile(InputStream input, String ownPackage)
            throws IOException, FluidVpnProfile.InvalidProfile, Exception {
        if (input == null) throw new FluidVpnProfile.InvalidProfile("Не удалось открыть файл");
        byte[] bytes = new byte[FluidVpnProfile.MAX_CONFIG_BYTES + 1];
        try {
            int size = 0;
            while (size < bytes.length) {
                int n = input.read(bytes, size, bytes.length - size);
                if (n < 0) break;
                if (n == 0) {
                    int one = input.read();
                    if (one < 0) break;
                    bytes[size++] = (byte) one;
                } else size += n;
            }
            if (size > FluidVpnProfile.MAX_CONFIG_BYTES)
                throw new FluidVpnProfile.InvalidProfile("Файл превышает ограничение 64 КиБ");
            FluidVpnProfile.Info info = FluidVpnProfile.inspect(
                    new ByteArrayInputStream(bytes, 0, size));
            Config original = Config.parse(new ByteArrayInputStream(bytes, 0, size));
            // Do not accept app routing from the imported file: only FluidGram.
            com.wireguard.config.Interface source = original.getInterface();
            Interface.Builder iface = new Interface.Builder()
                    .addAddresses(source.getAddresses())
                    .addDnsServers(source.getDnsServers())
                    .addDnsSearchDomains(source.getDnsSearchDomains())
                    .setKeyPair(source.getKeyPair())
                    .includeApplication(ownPackage);
            if (source.getMtu().isPresent()) iface.setMtu(source.getMtu().get());
            if (source.getListenPort().isPresent())
                iface.setListenPort(source.getListenPort().get());
            Config scoped = new Config.Builder()
                    .setInterface(iface.build())
                    .addPeers(original.getPeers())
                    .build();
            return new Imported(info, scoped);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static boolean isBusy() {
        return status == Status.CONNECTING || status == Status.DISCONNECTING;
    }

    public static boolean isConnected() {
        return status == Status.CONNECTED;
    }

    /** An uncertain backend state must be cleaned up before loading a new profile. */
    public static boolean requiresCleanup() {
        return status == Status.ERROR;
    }

    public static String statusLabel() {
        switch (status) {
            case CONNECTING: return "Подключение…";
            case CONNECTED: return "VPN активен · только FluidGram";
            case DISCONNECTING: return "Отключение…";
            case ERROR: return "Ошибка VPN · нажмите «Отключить» для очистки";
            default: return "VPN отключён";
        }
    }

    /** Must be called from UI only after VpnService.prepare() returns null. */
    public static boolean connect(Context context, Config config, Callback callback) {
        if (config == null) return false;
        try {
            if (VpnService.prepare(context) != null) return false;
        } catch (SecurityException ignored) {
            return false;
        }
        synchronized (LOCK) {
            if (status != Status.DISCONNECTED) return false;
            status = Status.CONNECTING;
        }
        Context app = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                synchronized (LOCK) {
                    if (backend == null) backend = new GoBackend(app);
                }
                backend.setState(TUNNEL, Tunnel.State.UP, config);
                ok = backend.getState(TUNNEL) == Tunnel.State.UP;
                status = ok ? Status.CONNECTED : Status.ERROR;
            } catch (Exception | LinkageError ignored) {
                // Never log exceptions: third-party error messages can contain endpoints.
                status = Status.ERROR;
            }
            boolean result = ok;
            if (callback != null) MAIN.post(() -> callback.onComplete(result));
        }, "FluidGramVpnConnect");
        worker.start();
        return true;
    }

    public static boolean disconnect(Callback callback) {
        synchronized (LOCK) {
            if (status != Status.CONNECTED && status != Status.ERROR) return false;
            status = Status.DISCONNECTING;
        }
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                if (backend != null) backend.setState(TUNNEL, Tunnel.State.DOWN, null);
                ok = true;
                status = Status.DISCONNECTED;
            } catch (Exception | LinkageError ignored) {
                status = Status.ERROR;
            }
            boolean result = ok;
            if (callback != null) MAIN.post(() -> callback.onComplete(result));
        }, "FluidGramVpnDisconnect");
        worker.start();
        return true;
    }
}
