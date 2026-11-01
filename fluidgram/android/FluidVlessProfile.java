package org.telegram.ui;
import android.net.Uri;
import java.util.UUID;
/** Validation only. This class never opens sockets or stores credentials. */
public final class FluidVlessProfile {
    private FluidVlessProfile() {}
    public static final class Info {
        public final String host, transport, security;
        public final int port;
        private Info(String host, int port, String transport, String security) {
            this.host=host; this.port=port; this.transport=transport; this.security=security;
        }
    }
    public static Info inspect(String link) {
        if (link == null || link.length() > 8192 || !link.startsWith("vless://"))
            throw new IllegalArgumentException("Некорректная VLESS-ссылка");
        Uri uri;
        try { uri=Uri.parse(link); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Некорректная VLESS-ссылка"); }
        if (!"vless".equals(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("Не указан адрес сервера");
        String user=uri.getUserInfo();
        try { UUID.fromString(user); }
        catch (Exception ex) { throw new IllegalArgumentException("Неверный UUID"); }
        int port=uri.getPort();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Неверный порт");
        String transport=uri.getQueryParameter("type");
        if (transport == null || transport.isEmpty()) transport="tcp";
        String security=uri.getQueryParameter("security");
        if (security == null || security.isEmpty()) security="none";
        if (!transport.equals("tcp") && !transport.equals("ws") && !transport.equals("grpc"))
            throw new IllegalArgumentException("Транспорт пока не поддерживается");
        if (!security.equals("tls") && !security.equals("reality"))
            throw new IllegalArgumentException("Требуется TLS или REALITY");
        if (security.equals("reality") && (uri.getQueryParameter("pbk")==null
                || uri.getQueryParameter("sni")==null))
            throw new IllegalArgumentException("REALITY: отсутствует pbk или sni");
        return new Info(uri.getHost(),port,transport,security);
    }
}
