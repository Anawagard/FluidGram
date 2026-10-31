package org.telegram.ui;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** FluidGram display hints: Android/ColorOS remains the final refresh-rate arbiter. */
public final class FluidRefresh {
    public static final String PREF = "fluidgram_labs";
    private static Activity owner;
    private static TextView hud;
    private static Choreographer.FrameCallback ticker;
    private static long tickStartNs;
    private static int ticks;
    private FluidRefresh() {}
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static float maxSupportedHz(Activity a) {
        Display d = a.getWindowManager().getDefaultDisplay();
        float max = 0f;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            for (Display.Mode mode : d.getSupportedModes()) {
                if (mode.getRefreshRate() > max) max = mode.getRefreshRate();
            }
        }
        return max > 0f ? max : d.getRefreshRate();
    }
    public static int batteryPercent(Context c) {
        Intent state = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (state == null) return 100;
        int level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? 100 : level * 100 / scale;
    }
    /** 0 system/ColorOS auto; 1 high FPS hint; 2 highest supported mode;
        3 high FPS hint with low battery fallback. */
    public static void apply(Activity a) {
        if (a == null) return;
        Window window = a.getWindow();
        SharedPreferences p = prefs(a);
        int setting = p.getInt("refresh_mode", 1);
        float highest = maxSupportedHz(a);
        if (setting == 3 && batteryPercent(a) <= 25) setting = 0;
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
    public static void resume(Activity a) { refresh(a); }
    public static void refresh(Activity a) {
        apply(a);
        boolean keep = prefs(a).getBoolean("keep_screen_on", false);
        if (keep) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopHud();
        if (prefs(a).getBoolean("show_vsync", false)) startHud(a);
    }
    public static void pause(Activity a) { if (owner == a) stopHud(); }
    public static String diagnostic(Activity a) {
        Display d = a.getWindowManager().getDefaultDisplay();
        StringBuilder b = new StringBuilder();
        b.append("FluidGram v0.5; ColorOS controls final refresh rate\n");
        b.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ")
                .append(android.os.Build.MODEL).append("\n");
        b.append("ColorOS/Android reported Hz: ").append(d.getRefreshRate()).append("\n");
        b.append("Max advertised Hz: ").append(maxSupportedHz(a)).append("\n");
        b.append("Mode: ").append(prefs(a).getInt("refresh_mode", 0)).append("\n");
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
    private static void startHud(Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) decor;
        TextView text = new TextView(a);
        text.setTextColor(Color.rgb(145, 255, 238));
        text.setTextSize(12);
        text.setPadding(px(a, 12), px(a, 7), px(a, 12), px(a, 7));
        text.setText("VSync • -- Hz");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB0A2535);
        bg.setCornerRadius(px(a, 16));
        bg.setStroke(px(a, 1), 0x8870EBDD);
        text.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        lp.topMargin = px(a, 85);
        lp.rightMargin = px(a, 10);
        frame.addView(text, lp);
        owner = a;
        hud = text;
        tickStartNs = 0L;
        ticks = 0;
        ticker = new Choreographer.FrameCallback() {
            @Override public void doFrame(long ns) {
                if (owner != a || hud != text) return;
                if (tickStartNs == 0) tickStartNs = ns;
                ticks++;
                if (ns - tickStartNs >= 1000000000L) {
                    int sampled = Math.round(ticks * 1000000000f / (ns - tickStartNs));
                    text.setText("VSync • " + sampled + " Hz");
                    tickStartNs = ns;
                    ticks = 0;
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        };
        Choreographer.getInstance().postFrameCallback(ticker);
    }
    private static void stopHud() {
        if (ticker != null) {
            Choreographer.getInstance().removeFrameCallback(ticker);
            ticker = null;
        }
        if (hud != null && hud.getParent() instanceof FrameLayout) {
            ((FrameLayout) hud.getParent()).removeView(hud);
        }
        hud = null;
        owner = null;
    }
}
