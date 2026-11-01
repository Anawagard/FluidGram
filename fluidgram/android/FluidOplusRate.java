package org.telegram.ui;

import android.os.IBinder;
import android.os.Build;
import java.lang.reflect.Method;

/**
 * Opt-in, best-effort ColorOS display vote. Uses ONLY the firmware's own
 * IOplusScreenMode proxy and its declared method signature: never guesses
 * transaction IDs or sends arbitrary Parcel payloads to a vendor service.
 * A failure has no side effects and must not be reported as 165Hz enabled.
 */
public final class FluidOplusRate {
    private static final String IFACE = "com.oplus.screenmode.IOplusScreenMode";
    private static final int RATE_165 = 7;
    private static final int RATE_NONE = 0;
    private static boolean requested;
    private static String state = "Не проверено";
    private FluidOplusRate() {}

    private static boolean call(String packageName, int rateId) {
        try {
            if (!"OnePlus".equalsIgnoreCase(Build.MANUFACTURER)
                    && !"OPPO".equalsIgnoreCase(Build.MANUFACTURER)) {
                state = "Не устройство OnePlus/OPPO";
                return false;
            }
            Class<?> manager = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) manager.getMethod("getService", String.class)
                    .invoke(null, "oplusscreenmode");
            if (binder == null) {
                state = "Служба oplusscreenmode недоступна";
                return false;
            }
            Class<?> stub = Class.forName(IFACE + "$Stub");
            Object proxy = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            if (proxy == null) {
                state = "Интерфейс ColorOS недоступен";
                return false;
            }
            Method method = null;
            for (Method candidate : proxy.getClass().getMethods()) {
                Class<?>[] p = candidate.getParameterTypes();
                if (candidate.getName().equals("requestGameRefreshRate")
                        && p.length == 2 && p[0] == String.class
                        && (p[1] == int.class || p[1] == Integer.class)) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                state = "Прошивка не предоставляет совместимый метод";
                return false;
            }
            Object result = method.invoke(proxy, packageName, rateId);
            boolean ok = result == null || Boolean.TRUE.equals(result)
                    || (result instanceof Integer && ((Integer) result) == 1);
            state = ok ? (rateId == RATE_NONE ? "Запрос снят" : "Запрос 165 Гц принят службой")
                    : "Служба отклонила запрос";
            return ok;
        } catch (Throwable ignored) {
            // Hidden API restrictions, permission denial and firmware mismatch
            // are expected on some releases: never claim success.
            state = "ColorOS отклонила или заблокировала вызов";
            return false;
        }
    }

    public static boolean request(String packageName) {
        if (requested) return true;
        requested = call(packageName, RATE_165);
        return requested;
    }
    public static void release(String packageName) {
        if (requested) {
            if (call(packageName, RATE_NONE)) requested = false;
        }
    }
    public static String diagnostic() { return state + "; запрос активен: " + requested; }
}
