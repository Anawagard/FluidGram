package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.os.BatteryManager;
import android.os.SystemClock;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Foreground energy observations without timers, wake locks or background work.
 * Charge counter measures the whole device, NOT FluidGram's isolated consumption.
 * Missing sensor data is reported as unavailable rather than guessed.
 */
public final class FluidEnergy {
    private static final WeakHashMap<Activity, Boolean> visible = new WeakHashMap<>();
    private static long startedMs;
    private static long foregroundMs;
    private static int startedCharge = Integer.MIN_VALUE;
    private static long deltaUah;
    private static int measuredSegments;
    private static int completedSegments;

    private FluidEnergy() {}

    private static int chargeCounter(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return Integer.MIN_VALUE;
        int value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        return value > 0 ? value : Integer.MIN_VALUE;
    }

    private static int currentNow(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return Integer.MIN_VALUE;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
    }

    public static synchronized void enter(Activity activity) {
        if (activity == null || visible.containsKey(activity)) return;
        if (visible.isEmpty()) {
            startedMs = SystemClock.elapsedRealtime();
            startedCharge = chargeCounter(activity);
        }
        visible.put(activity, Boolean.TRUE);
    }

    public static synchronized void exit(Activity activity) {
        if (activity == null || visible.remove(activity) == null) return;
        if (!visible.isEmpty()) return;
        foregroundMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
        int end = chargeCounter(activity);
        if (startedCharge != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
            deltaUah += (long) startedCharge - end;
            measuredSegments++;
        }
        completedSegments++;
        startedMs = 0L;
        startedCharge = Integer.MIN_VALUE;
    }

    /** Reset the comparison baseline without ending a foreground session. */
    public static synchronized void reset(Context context) {
        foregroundMs = 0L;
        deltaUah = 0L;
        measuredSegments = 0;
        completedSegments = 0;
        if (!visible.isEmpty()) {
            startedMs = SystemClock.elapsedRealtime();
            startedCharge = chargeCounter(context);
        }
    }

    public static synchronized String report(Context context) {
        long activeMs = foregroundMs;
        long totalDelta = deltaUah;
        int samples = measuredSegments;
        int segments = completedSegments;
        if (!visible.isEmpty()) {
            activeMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
            int end = chargeCounter(context);
            if (startedCharge != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
                totalDelta += (long) startedCharge - end;
                samples++;
            }
            segments++;
        }
        int current = currentNow(context);
        StringBuilder out = new StringBuilder();
        out.append("FluidGram Energy Lab — device-wide observations\n");
        out.append("Foreground time: ").append(activeMs / 1000L).append(" s\n");
        if (samples > 0) {
            out.append("Battery charge change: ")
                    .append(String.format(Locale.ROOT, "%+.1f", totalDelta / 1000.0))
                    .append(" mAh (positive = discharge)\n");
        } else {
            out.append("Battery charge change: sensor unavailable\n");
        }
        out.append("Charge-counter samples: ").append(samples).append("/")
                .append(segments).append(" foreground segments\n");
        if (current != Integer.MIN_VALUE && current != 0) {
            out.append("Instantaneous device current: ")
                    .append(String.format(Locale.ROOT, "%+.0f", current / 1000.0))
                    .append(" mA (device sensor sign)\n");
        } else {
            out.append("Instantaneous device current: sensor unavailable\n");
        }
        out.append("These values cover the WHOLE PHONE, including other apps, radio and screen. ");
        out.append("They do not isolate FluidGram battery use. No background sampling.");
        return out.toString();
    }
}
