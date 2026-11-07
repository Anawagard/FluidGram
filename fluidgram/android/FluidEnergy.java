package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
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
    private static boolean startedUnplugged;
    private static int skippedChargingSegments;
    private static long deltaUah;
    private static long measuredForegroundMs;
    private static int measuredSegments;
    private static int completedSegments;

    private FluidEnergy() {}

    private static int chargeCounter(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return Integer.MIN_VALUE;
        int value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        return value > 0 ? value : Integer.MIN_VALUE;
    }

    private static boolean unplugged(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return plugged == 0 && (status == BatteryManager.BATTERY_STATUS_DISCHARGING
                || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING);
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
            startedUnplugged = unplugged(activity);
        }
        visible.put(activity, Boolean.TRUE);
    }

    public static synchronized void exit(Activity activity) {
        if (activity == null || visible.remove(activity) == null) return;
        if (!visible.isEmpty()) return;
        foregroundMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
        int end = chargeCounter(activity);
        if (startedUnplugged && unplugged(activity)
                && startedCharge != Integer.MIN_VALUE && end != Integer.MIN_VALUE
                && startedCharge >= end) {
            deltaUah += (long) startedCharge - end;
            measuredForegroundMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
            measuredSegments++;
        } else {
            skippedChargingSegments++;
        }
        completedSegments++;
        startedMs = 0L;
        startedCharge = Integer.MIN_VALUE;
    }

    /** Reset the comparison baseline without ending a foreground session. */
    public static synchronized void reset(Context context) {
        foregroundMs = 0L;
        deltaUah = 0L;
        measuredForegroundMs = 0L;
        measuredSegments = 0;
        completedSegments = 0;
        skippedChargingSegments = 0;
        if (!visible.isEmpty()) {
            startedMs = SystemClock.elapsedRealtime();
            startedCharge = chargeCounter(context);
            startedUnplugged = unplugged(context);
        }
    }

    public static synchronized String report(Context context) {
        long activeMs = foregroundMs;
        long totalDelta = deltaUah;
        long validMs = measuredForegroundMs;
        int samples = measuredSegments;
        int segments = completedSegments;
        int skipped = skippedChargingSegments;
        if (!visible.isEmpty()) {
            activeMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
            int end = chargeCounter(context);
            if (startedUnplugged && unplugged(context)
                    && startedCharge != Integer.MIN_VALUE && end != Integer.MIN_VALUE
                    && startedCharge >= end) {
                totalDelta += (long) startedCharge - end;
                validMs += Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
                samples++;
            } else {
                skipped++;
            }
            segments++;
        }
        int current = currentNow(context);
        StringBuilder out = new StringBuilder();
        out.append("FluidGram Energy Lab — device-wide observations\n");
        out.append("Foreground time: ").append(activeMs / 1000L).append(" s\n");
        if (samples > 0 && validMs > 0L) {
            out.append("Valid measurement time: ").append(validMs / 1000L).append(" s\\n");
            out.append("Approx. device discharge rate: ")
                    .append(String.format(Locale.ROOT, "%.1f", totalDelta * 3600.0 / validMs))
                    .append(" mA (charge delta / valid time; NOT app-only)\\n");
            out.append("Battery charge change: ")
                    .append(String.format(Locale.ROOT, "%+.1f", totalDelta / 1000.0))
                    .append(" mAh (positive = discharge)\n");
        } else {
            out.append("Battery charge change: no valid unplugged interval (or sensor unavailable)\n");
        }
        out.append("Charge-counter samples: ").append(samples).append("/")
                .append(segments).append(" foreground segments\n");
        out.append("Excluded charging/uncertain segments: ").append(skipped).append("\n");
        if (current != Integer.MIN_VALUE && current != 0) {
            out.append("Instantaneous device current: ")
                    .append(String.format(Locale.ROOT, "%+.0f", current / 1000.0))
                    .append(" mA (device sensor sign)\n");
        } else {
            out.append("Instantaneous device current: sensor unavailable\n");
        }
        out.append("These values cover the WHOLE PHONE, including other apps, radio and screen. ");
        out.append("They do not isolate FluidGram battery use. Charging or uncertain endpoints are excluded; charging between endpoint checks cannot be detected. No background sampling.");
        return out.toString();
    }
}
