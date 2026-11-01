package org.telegram.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Read-only, bounded WireGuard profile preflight. This is NOT a VPN tunnel.
 * No private keys, peer keys, addresses or endpoints are retained in Info.
 * Do not log input or exceptions: profiles contain credentials.
 */
public final class FluidVpnProfile {
    public static final int MAX_CONFIG_BYTES = 64 * 1024;
    private FluidVpnProfile() {}

    public static final class Info {
        public final int peers;
        public final boolean hasDns;
        public final boolean fullTunnel;
        private Info(int peers, boolean hasDns, boolean fullTunnel) {
            this.peers = peers;
            this.hasDns = hasDns;
            this.fullTunnel = fullTunnel;
        }
    }

    public static final class InvalidProfile extends Exception {
        public InvalidProfile(String message) { super(message); }
    }

    /** Reads at most 64 KiB plus one sentinel byte, closes nothing (caller owns stream). */
    public static Info inspect(InputStream input) throws IOException, InvalidProfile {
        if (input == null) throw new InvalidProfile("Не удалось открыть файл");
        byte[] raw = new byte[MAX_CONFIG_BYTES + 1];
        try {
            int length = 0;
            while (length < raw.length) {
                int count = input.read(raw, length, raw.length - length);
                if (count < 0) break;
                if (count == 0) {
                    int single = input.read();
                    if (single < 0) break;
                    raw[length++] = (byte) single;
                } else {
                    length += count;
                }
            }
            if (length > MAX_CONFIG_BYTES || input.read() != -1)
                throw new InvalidProfile("Файл превышает ограничение 64 КиБ");
            String source = new String(raw, 0, length, StandardCharsets.UTF_8);
            if (source.indexOf('\u0000') >= 0) throw new InvalidProfile("Неподдерживаемый формат файла");
            return parse(source);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static boolean looksLikeKey(String value) {
        if (value.length() != 44 || value.charAt(43) != '=') return false;
        for (int i = 0; i < 43; i++) {
            char c = value.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/')) return false;
        }
        return true;
    }

    private static Info parse(String source) throws InvalidProfile {
        String section = "";
        boolean privateKey = false, address = false, dns = false, fullTunnel = false;
        boolean peerKey = false, peerRoutes = false, peerEndpoint = false;
        int peers = 0;
        for (String rawLine : source.replace("\r", "").split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                String next = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                if ("peer".equals(section)) {
                    if (!peerKey || !peerRoutes || !peerEndpoint)
                        throw new InvalidProfile("Проверьте PublicKey, Endpoint и AllowedIPs у каждого Peer");
                    peers++;
                }
                if (!"interface".equals(next) && !"peer".equals(next))
                    throw new InvalidProfile("Неизвестный раздел WireGuard");
                if ("interface".equals(next) && !section.isEmpty())
                    throw new InvalidProfile("Повторный раздел Interface");
                if ("peer".equals(next) && !privateKey)
                    throw new InvalidProfile("Раздел Interface должен идти первым");
                section = next;
                peerKey = peerRoutes = peerEndpoint = false;
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 1 || section.isEmpty())
                throw new InvalidProfile("Ожидался файл конфигурации WireGuard .conf");
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).trim();
            if (value.isEmpty()) throw new InvalidProfile("В конфигурации есть пустое обязательное значение");
            if ("interface".equals(section)) {
                if ("privatekey".equals(key)) privateKey = looksLikeKey(value);
                if ("address".equals(key)) address = value.contains("/");
                if ("dns".equals(key)) dns = true;
            } else if ("peer".equals(section)) {
                if ("publickey".equals(key)) peerKey = looksLikeKey(value);
                if ("endpoint".equals(key)) peerEndpoint = value.contains(":");
                if ("allowedips".equals(key)) {
                    peerRoutes = value.contains("/");
                    for (String route : value.split(",")) {
                        String cidr = route.trim();
                        if ("0.0.0.0/0".equals(cidr) || "::/0".equals(cidr)) fullTunnel = true;
                    }
                }
            }
        }
        if ("peer".equals(section)) {
            if (!peerKey || !peerRoutes || !peerEndpoint)
                throw new InvalidProfile("Проверьте PublicKey, Endpoint и AllowedIPs у каждого Peer");
            peers++;
        }
        if (!privateKey || !address || peers == 0)
            throw new InvalidProfile("Нужны Interface (PrivateKey, Address) и хотя бы один Peer");
        return new Info(peers, dns, fullTunnel);
    }
}
