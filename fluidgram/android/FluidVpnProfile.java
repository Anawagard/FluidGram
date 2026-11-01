package org.telegram.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Read-only, bounded WireGuard preflight, NOT a VPN tunnel or a network probe.
 * Never retain or log private keys, peer keys, addresses or endpoints in Info.
 * Reject unknown directives rather than silently treating an unsafe profile as ready.
 */
public final class FluidVpnProfile {
    public static final int MAX_CONFIG_BYTES = 64 * 1024;
    private FluidVpnProfile() {}

    public static final class Info {
        public final int peers;
        public final boolean hasDns;
        public final boolean fullTunnel;
        public final boolean ipv4Default;
        public final boolean ipv6Default;
        private Info(int peers, boolean hasDns, boolean ipv4Default, boolean ipv6Default) {
            this.peers = peers;
            this.hasDns = hasDns;
            this.ipv4Default = ipv4Default;
            this.ipv6Default = ipv6Default;
            // Both families must have default routes before claiming full coverage.
            this.fullTunnel = ipv4Default && ipv6Default;
        }
    }

    public static final class InvalidProfile extends Exception {
        public InvalidProfile(String message) { super(message); }
    }

    /** Reads at most 64 KiB plus a sentinel; caller owns and closes the stream. */
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
            if (length > MAX_CONFIG_BYTES)
                throw new InvalidProfile("Файл превышает ограничение 64 КиБ");
            String source = new String(raw, 0, length, StandardCharsets.UTF_8);
            if (source.indexOf('\u0000') >= 0 || source.indexOf('\uFFFD') >= 0)
                throw new InvalidProfile("Неподдерживаемый формат файла");
            return parse(source);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    /** Canonical 32-byte base64 key: 43 data characters plus '=' padding. */
    private static boolean looksLikeKey(String value) {
        if (value.length() != 44 || value.charAt(43) != '=') return false;
        int last = -1;
        for (int i = 0; i < 43; i++) {
            char c = value.charAt(i);
            int n = c >= 'A' && c <= 'Z' ? c - 'A'
                    : c >= 'a' && c <= 'z' ? c - 'a' + 26
                    : c >= '0' && c <= '9' ? c - '0' + 52
                    : c == '+' ? 62 : c == '/' ? 63 : -1;
            if (n < 0) return false;
            last = n;
        }
        return (last & 3) == 0;
    }

    private static boolean isIpv4(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + (c - '0');
            }
            if (value > 255) return false;
        }
        return true;
    }

    /** Numeric IPv6 only: no DNS, network lookup or IPv4-mapped shorthand. */
    private static boolean isIpv6(String address) {
        if (address.indexOf(':') < 0 || address.contains(":::")) return false;
        int gap = address.indexOf("::");
        if (gap < 0 && (address.startsWith(":") || address.endsWith(":"))) return false;
        if (gap >= 0 && gap != address.lastIndexOf("::")) return false;
        String[] groups = address.split(":", -1);
        int count = 0;
        for (String group : groups) {
            if (group.isEmpty()) continue;
            if (group.length() > 4) return false;
            for (int i = 0; i < group.length(); i++) {
                char c = group.charAt(i);
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F'))) return false;
            }
            count++;
        }
        return gap >= 0 ? count < 8 : count == 8;
    }

    private static boolean isCidr(String value) {
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/')) return false;
        String host = value.substring(0, slash).trim();
        String bits = value.substring(slash + 1).trim();
        if (bits.isEmpty() || bits.length() > 3) return false;
        for (int i = 0; i < bits.length(); i++)
            if (bits.charAt(i) < '0' || bits.charAt(i) > '9') return false;
        int prefix = Integer.parseInt(bits);
        return isIpv4(host) ? prefix <= 32 : isIpv6(host) && prefix <= 128;
    }

    private static boolean isEndpoint(String value) {
        String host;
        String port;
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close <= 1 || close + 1 >= value.length() || value.charAt(close + 1) != ':')
                return false;
            host = value.substring(1, close);
            port = value.substring(close + 2);
            if (!isIpv6(host)) return false;
        } else {
            int colon = value.lastIndexOf(':');
            if (colon <= 0 || value.indexOf(':') != colon) return false;
            host = value.substring(0, colon);
            port = value.substring(colon + 1);
            if (!host.matches("[A-Za-z0-9._-]+")) return false;
        }
        if (port.isEmpty() || port.length() > 5) return false;
        int n = 0;
        for (int i = 0; i < port.length(); i++) {
            char c = port.charAt(i);
            if (c < '0' || c > '9') return false;
            n = n * 10 + c - '0';
        }
        return n >= 1 && n <= 65535;
    }

    private static boolean validNumber(String value, int max) {
        if (value.isEmpty() || value.length() > 5) return false;
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
            n = n * 10 + c - '0';
        }
        return n <= max;
    }

    private static Info parse(String source) throws InvalidProfile {
        String section = "";
        boolean privateKey = false, address = false, dns = false;
        boolean ipv4Default = false, ipv6Default = false;
        boolean peerKey = false, peerRoutes = false, peerEndpoint = false;
        boolean seenInterface = false, seenDns = false, seenPreshared = false;
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
                if ("interface".equals(next)) {
                    if (seenInterface) throw new InvalidProfile("Повторный раздел Interface");
                    seenInterface = true;
                }
                if ("peer".equals(next) && (!privateKey || !address))
                    throw new InvalidProfile("Раздел Interface должен идти первым");
                section = next;
                peerKey = peerRoutes = peerEndpoint = seenPreshared = false;
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 1 || section.isEmpty())
                throw new InvalidProfile("Ожидался файл конфигурации WireGuard .conf");
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).trim();
            if (value.isEmpty()) throw new InvalidProfile("В конфигурации есть пустое значение");
            if ("interface".equals(section)) {
                switch (key) {
                    case "privatekey":
                        if (privateKey || !looksLikeKey(value)) throw new InvalidProfile("Некорректный PrivateKey");
                        privateKey = true;
                        break;
                    case "address":
                        if (address) throw new InvalidProfile("Повторный Address");
                        for (String item : value.split(",", -1))
                            if (!isCidr(item.trim())) throw new InvalidProfile("Некорректный Address");
                        address = true;
                        break;
                    case "dns":
                        if (seenDns) throw new InvalidProfile("Повторный DNS");
                        seenDns = dns = true;
                        break;
                    case "listenport":
                        if (!validNumber(value, 65535)) throw new InvalidProfile("Некорректный ListenPort");
                        break;
                    case "mtu":
                        if (!validNumber(value, 65535)) throw new InvalidProfile("Некорректный MTU");
                        break;
                    default:
                        throw new InvalidProfile("Неподдерживаемый параметр Interface");
                }
            } else if ("peer".equals(section)) {
                switch (key) {
                    case "publickey":
                        if (peerKey || !looksLikeKey(value)) throw new InvalidProfile("Некорректный PublicKey");
                        peerKey = true;
                        break;
                    case "presharedkey":
                        if (seenPreshared || !looksLikeKey(value)) throw new InvalidProfile("Некорректный PresharedKey");
                        seenPreshared = true;
                        break;
                    case "endpoint":
                        if (peerEndpoint || !isEndpoint(value)) throw new InvalidProfile("Некорректный Endpoint");
                        peerEndpoint = true;
                        break;
                    case "allowedips":
                        if (peerRoutes) throw new InvalidProfile("Повторный AllowedIPs");
                        for (String route : value.split(",", -1)) {
                            String cidr = route.trim();
                            if (!isCidr(cidr)) throw new InvalidProfile("Некорректный AllowedIPs");
                            if ("0.0.0.0/0".equals(cidr)) ipv4Default = true;
                            if ("::/0".equals(cidr)) ipv6Default = true;
                        }
                        peerRoutes = true;
                        break;
                    case "persistentkeepalive":
                        if (!validNumber(value, 65535)) throw new InvalidProfile("Некорректный PersistentKeepalive");
                        break;
                    default:
                        throw new InvalidProfile("Неподдерживаемый параметр Peer");
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
        return new Info(peers, dns, ipv4Default, ipv6Default);
    }
}
