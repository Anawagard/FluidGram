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
    private static final int RATE_144 = 4;
    private static final int RATE_NONE = 0;
    private static boolean requested;
    private static int acceptedVotes;
    private static int successfulReleases;
    private static int failedReleases;
    private static int transitions144;
    private static int failures144;
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
        if (requested) acceptedVotes++;
        return requested;
    }
    /** Diagnostic only: 144Hz bridge between the 165 vote and normal auto mode.
     * Never leave the bridge active after the transition timer.
     */
    public static boolean bridge144(String packageName) {
        if (!requested) return false;
        boolean ok = call(packageName, RATE_144);
        if (ok) transitions144++;
        else failures144++;
        return ok;
    }
    public static void release(String packageName) {
        if (requested) {
            if (call(packageName, RATE_NONE)) {
                requested = false;
                successfulReleases++;
            } else {
                failedReleases++;
            }
        }
    }
    /** One explicitly timed second withdrawal, only after the inactivity timer.
     * Never issue a guessed vendor transaction or vote for 120Hz: that can pin
     * 120Hz instead of restoring LTPO.
     */
    public static void confirmRelease(String packageName) {
        if (call(packageName, RATE_NONE)) {
            requested = false;
            successfulReleases++;
        } else {
            failedReleases++;
        }
    }
    public static String diagnostic() {
        return state + "; запрос активен: " + requested
                + "; принятых 165: " + acceptedVotes
                + "; сбросов: " + successfulReleases
                + "; ошибок сброса: " + failedReleases
                + "; переходов 144: " + transitions144
                + "; ошибок перехода: " + failures144;
    }
}
