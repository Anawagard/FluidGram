package org.telegram.ui;
import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewTreeObserver;

/** FluidGram display hints: Android/ColorOS remains the final refresh-rate arbiter. */
public final class FluidRefresh {
    public static final String PREF = "fluidgram_labs";
    private static Activity owner;
    private static TextView hud;
    private static DisplayManager displayManager;
    private static Activity powerOwner;
    private static BroadcastReceiver powerReceiver;
    private static DisplayManager.DisplayListener displayListener;
    private static long lastPowerCheckMs;
    private static boolean highRefreshAllowed;
    private static int lastBatteryGuardState = -1;
    // Cached while a foreground Activity owns the listener: no thermal binder call per scroll.
    private static PowerManager thermalManager;
    private static PowerManager.OnThermalStatusChangedListener thermalListener;
    private static boolean thermalKnown;
    private static boolean thermalHot;
    private static final Handler adaptiveHandler = new Handler(Looper.getMainLooper());
    private static Activity adaptiveOwner;
    private static View adaptiveDecor;
    private static ViewTreeObserver.OnScrollChangedListener scrollListener;
    private static Runnable idleReset;
    private static long lastScrollHintMs;
    private FluidRefresh() {}
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static float maxSupportedHz(Activity a) {
        Display d = a.getWindowManager().getDefaultDisplay();
        float max = d.getRefreshRate();
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            Display.Mode active = d.getMode();
            for (Display.Mode mode : d.getSupportedModes()) {
                if (mode.getPhysicalWidth() == active.getPhysicalWidth()
                        && mode.getPhysicalHeight() == active.getPhysicalHeight()
                        && mode.getRefreshRate() > max) max = mode.getRefreshRate();
            }
        }
        return max;
    }
    public static int batteryPercent(Context c) {
        Intent state = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (state == null) return 100;
        int level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? 100 : level * 100 / scale;
    }
    /** Android 10+ thermal policy: severe or higher yields control to ColorOS. */
    public static boolean isThermallyConstrained(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 29) return false;
        if (thermalKnown) return thermalHot;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
    }
    /** 0 system/ColorOS auto; 1 high FPS hint; 2 highest compatible mode;
        3 high FPS hint with low battery fallback; 4 scroll-only adaptive hint. */
    public static void apply(Activity a) {
        if (a == null) return;
        Window window = a.getWindow();
        SharedPreferences p = prefs(a);
        int setting = p.getInt("refresh_mode", 4);
        float highest = maxSupportedHz(a);
        // All high-refresh modes yield to the battery guard, not only mode 3.
        if (setting != 0 && !allowHighRefresh(a)) setting = 0;
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.preferredDisplayModeId = 0;
        lp.preferredRefreshRate = 0f; // Return full authority to ColorOS for system auto mode.
        if (setting == 1 || setting == 3) {
            // A hint, not a forced refresh rate. The OS can select 90Hz anyway.
            lp.preferredRefreshRate = highest;
        } else if (setting == 2 && android.os.Build.VERSION.SDK_INT >= 23) {
            Display display = a.getWindowManager().getDefaultDisplay();
            Display.Mode active = display.getMode();
            Display.Mode best = null;
            for (Display.Mode item : display.getSupportedModes()) {
                if (item.getPhysicalWidth() != active.getPhysicalWidth()
                        || item.getPhysicalHeight() != active.getPhysicalHeight()) continue;
                if (best == null || item.getRefreshRate() > best.getRefreshRate()) best = item;
            }
            if (best != null) {
                lp.preferredDisplayModeId = best.getModeId();
            } else {
                lp.preferredRefreshRate = highest;
            }
        }
        window.setAttributes(lp);
    }
    /** Cached energy budget: avoid querying the battery receiver on every scroll event. */
    private static boolean allowHighRefresh(Activity a) {
        long now = SystemClock.elapsedRealtime();
        if (lastPowerCheckMs == 0L || now - lastPowerCheckMs >= 15000L) {
            highRefreshAllowed = batteryPercent(a) > 20;
            lastPowerCheckMs = now;
        }
        // Power saver is cheap to query and must take effect immediately,
        // even if the sticky battery reading is cached for 15 seconds.
        PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
        return highRefreshAllowed && (pm == null || !pm.isPowerSaveMode())
                && !isThermallyConstrained(a);
    }
    private static void stopAdaptive() {
        if (idleReset != null) adaptiveHandler.removeCallbacks(idleReset);
        if (adaptiveDecor != null && scrollListener != null
                && adaptiveDecor.getViewTreeObserver().isAlive()) {
            adaptiveDecor.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        }
        adaptiveOwner = null;
        adaptiveDecor = null;
        scrollListener = null;
        idleReset = null;
        lastScrollHintMs = 0L;
    }
    /** Release hints as soon as an Activity loses focus; ColorOS owns background refresh. */
    private static void releaseRefreshHint(Activity a) {
        if (a == null) return;
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        if (lp.preferredRefreshRate != 0f || lp.preferredDisplayModeId != 0) {
            lp.preferredRefreshRate = 0f;
            lp.preferredDisplayModeId = 0;
            a.getWindow().setAttributes(lp);
        }
    }
    private static void startAdaptive(Activity a) {
        stopAdaptive();
        adaptiveOwner = a;
        adaptiveDecor = a.getWindow().getDecorView();
        final View decor = adaptiveDecor;
        final float max = maxSupportedHz(a);
        idleReset = () -> {
            if (adaptiveOwner != a) return;
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            if (lp.preferredRefreshRate != 0f || lp.preferredDisplayModeId != 0) {
                lp.preferredRefreshRate = 0f;
                lp.preferredDisplayModeId = 0;
                a.getWindow().setAttributes(lp);
            }
        };
        scrollListener = () -> {
            if (adaptiveOwner != a || !decor.isAttachedToWindow()) return;
            // Coalesce rapid scroll callbacks: at most ten hint/handler updates
            // per second instead of one per frame at 120/165 Hz.
            long now = SystemClock.elapsedRealtime();
            if (now - lastScrollHintMs < 100L) return;
            lastScrollHintMs = now;
            if (!allowHighRefresh(a)) {
                adaptiveHandler.removeCallbacks(idleReset);
                idleReset.run();
                return;
            }
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            if (lp.preferredRefreshRate != max) {
                lp.preferredDisplayModeId = 0;
                lp.preferredRefreshRate = max;
                a.getWindow().setAttributes(lp);
            }
            adaptiveHandler.removeCallbacks(idleReset);
            adaptiveHandler.postDelayed(idleReset, 1200);
        };
        decor.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
    }
    public static void resume(Activity a) {
        FluidEnergy.enter(a);
        if (powerOwner != a) {
            stopPowerListener();
            powerOwner = a;
            lastBatteryGuardState = batteryPercent(a) > 20 ? 1 : 0;
            highRefreshAllowed = lastBatteryGuardState == 1;
            lastPowerCheckMs = 0L; // Never reuse a stale guard after returning to foreground.
            powerReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (powerOwner != a || intent == null) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        if (level < 0 || scale <= 0) return;
                        int percent = level * 100 / scale;
                        int state = percent > 20 ? 1 : 0;
                        highRefreshAllowed = state == 1;
                        // A single foreground battery receiver drives both refresh and glass.
                        if (a instanceof FluidLabsActivity) {
                            ((FluidLabsActivity) a).updateGlassPowerPolicy(percent);
                        }
                        lastPowerCheckMs = SystemClock.elapsedRealtime();
                        // Avoid reconfiguring the window on every battery broadcast.
                        if (state == lastBatteryGuardState) return;
                        lastBatteryGuardState = state;
                    } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                        lastPowerCheckMs = 0L;
                        if (a instanceof FluidLabsActivity) {
                            ((FluidLabsActivity) a).updateGlassPowerPolicy(-1);
                        }
                    } else {
                        return;
                    }
                    refresh(a);
                }
            };
            IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            a.registerReceiver(powerReceiver, filter);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                thermalManager = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
                if (thermalManager != null) {
                    thermalHot = thermalManager.getCurrentThermalStatus()
                            >= PowerManager.THERMAL_STATUS_SEVERE;
                    thermalKnown = true;
                    thermalListener = status -> a.runOnUiThread(() -> {
                        if (powerOwner != a) return;
                        boolean hot = status >= PowerManager.THERMAL_STATUS_SEVERE;
                        if (thermalKnown && thermalHot == hot) return;
                        thermalHot = hot;
                        thermalKnown = true;
                        refresh(a); // Release high-refresh hints promptly; restore only when safe.
                        if (a instanceof FluidLabsActivity) {
                            ((FluidLabsActivity) a).updateGlassThermalPolicy();
                        }
                    });
                    thermalManager.addThermalStatusListener(thermalListener);
                }
            }
        }
        refresh(a);
    }
    private static void stopPowerListener() {
        if (android.os.Build.VERSION.SDK_INT >= 29
                && thermalManager != null && thermalListener != null) {
            thermalManager.removeThermalStatusListener(thermalListener);
        }
        thermalManager = null;
        thermalListener = null;
        thermalKnown = false;
        thermalHot = false;
        if (powerOwner != null && powerReceiver != null) {
            powerOwner.unregisterReceiver(powerReceiver);
        }
        powerOwner = null;
        powerReceiver = null;
        lastBatteryGuardState = -1;
    }
    public static void refresh(Activity a) {
        stopAdaptive();
        apply(a);
        // No scroll observer at all when power saver, low charge or thermal guard
        // already prohibit high refresh. System broadcasts restore it when safe.
        if (prefs(a).getInt("refresh_mode", 4) == 4 && allowHighRefresh(a)) {
            startAdaptive(a);
        }
        boolean keep = prefs(a).getBoolean("keep_screen_on", false);
        if (keep) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopHud();
        if (prefs(a).getBoolean("show_vsync", false)) startHud(a);
    }
    public static void pause(Activity a) {
        FluidEnergy.exit(a);
        if (owner == a) stopHud();
        if (adaptiveOwner == a) stopAdaptive();
        if (powerOwner == a) stopPowerListener();
        releaseRefreshHint(a);
        // The user-requested stay-awake flag is foreground-only.
        if (a != null) a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    public static String diagnostic(Activity a) {
        Display d = a.getWindowManager().getDefaultDisplay();
        StringBuilder b = new StringBuilder();
        b.append("FluidGram v1.0; ColorOS controls final refresh rate\n");
        b.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ")
                .append(android.os.Build.MODEL).append("\n");
        b.append("ColorOS/Android reported Hz: ").append(d.getRefreshRate()).append("\n");
        b.append("Max advertised Hz: ").append(maxSupportedHz(a)).append("\n");
        b.append("Mode: ").append(prefs(a).getInt("refresh_mode", 4)).append("\n");
        b.append("Battery: ").append(batteryPercent(a)).append("%\n");
        PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
        b.append("System power saver: ").append(pm != null && pm.isPowerSaveMode()).append("\n");
        b.append("Thermal guard severe+: ").append(isThermallyConstrained(a)).append("\n");
        WindowManager.LayoutParams attrs = a.getWindow().getAttributes();
        b.append("Requested Hz: ").append(attrs.preferredRefreshRate).append("\n");
        b.append("Requested mode ID: ").append(attrs.preferredDisplayModeId).append("\n");
        b.append("HUD: event-driven display mode listener, not measured app FPS\n");
        b.append(FluidEnergy.report(a)).append("\n");
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            for (Display.Mode m : d.getSupportedModes()) {
                b.append("Mode #").append(m.getModeId()).append(": ")
                        .append(m.getPhysicalWidth()).append("x").append(m.getPhysicalHeight())
                        .append(" @ ").append(m.getRefreshRate()).append(" Hz\n");
            }
        }
        return b.toString();
    }
    private static int px(Activity a, int dp) {
        return (int) (dp * a.getResources().getDisplayMetrics().density + 0.5f);
    }
    /** No per-frame callbacks: the diagnostic HUD updates only on display-mode changes. */
    private static void updateHud(Activity a, TextView label) {
        if (owner != a || hud != label) return;
        Display d = a.getWindowManager().getDefaultDisplay();
        label.setText("Экран • " + Math.round(d.getRefreshRate()) + " Гц");
    }
    private static void startHud(Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) decor;
        TextView label = new TextView(a);
        label.setTextColor(Color.rgb(145, 255, 238));
        label.setTextSize(12);
        label.setPadding(px(a, 12), px(a, 7), px(a, 12), px(a, 7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB0A2535);
        bg.setCornerRadius(px(a, 16));
        bg.setStroke(px(a, 1), 0x8870EBDD);
        label.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        lp.topMargin = px(a, 85);
        lp.rightMargin = px(a, 10);
        frame.addView(label, lp);
        owner = a;
        hud = label;
        updateHud(a, label);
        displayManager = (DisplayManager) a.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayListener = new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) {}
                @Override public void onDisplayRemoved(int id) {}
                @Override public void onDisplayChanged(int id) {
                    if (owner == a && id == a.getWindowManager().getDefaultDisplay().getDisplayId()) {
                        updateHud(a, label);
                    }
                }
            };
            displayManager.registerDisplayListener(displayListener, adaptiveHandler);
        }
    }
    private static void stopHud() {
        if (displayManager != null && displayListener != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        displayListener = null;
        displayManager = null;
        if (hud != null && hud.getParent() instanceof FrameLayout) {
            ((FrameLayout) hud.getParent()).removeView(hud);
        }
        hud = null;
        owner = null;
    }
}
