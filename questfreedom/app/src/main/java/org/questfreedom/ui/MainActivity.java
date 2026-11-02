package org.questfreedom.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.questfreedom.core.LauncherFilter;
import org.questfreedom.core.PackagePolicy;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QuestFreedom 0.2: portable 2D Android launcher (not an AOSP ROM, bootloader or XR runtime).
 * All settings actions use public Android intents. No system packages are modified.
 */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff08121f;
    private static final int PANEL = 0xff17283a;
    private static final int PANEL_ALT = 0xff22394b;
    private static final int FOREGROUND = 0xffeaf7ff;
    private static final int MUTED = 0xffa1b4c5;
    private static final int ACCENT = 0xff72e4d3;
    private final List<ResolveInfo> launchables = new ArrayList<>();
    private SharedPreferences preferences;
    private boolean hideMeta;
    private EditText search;
    private LinearLayout results;
    private TextView counter;
    private TextView time;
    private LinearLayout root;

    private int dp(float n) {
        return Math.round(getResources().getDisplayMetrics().density * n);
    }

    private GradientDrawable surface(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), 0xff324a5a);
        return d;
    }

    private TextView text(String s, int size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView action(String label, Runnable click) {
        TextView v = text(label, 14, FOREGROUND, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(surface(PANEL, 15));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(w -> click.run());
        return v;
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "Not available on this Android device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getPreferences(MODE_PRIVATE);
        hideMeta = preferences.getBoolean("hide_meta", true);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (time != null) time.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        refreshPackages();
    }

    private void addActionPair(LinearLayout parent, String left, Runnable leftAction,
                               String right, Runnable rightAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(54));
        rowParams.bottomMargin = dp(10);
        parent.addView(row, rowParams);
        TextView a = action(left, leftAction), b = action(right, rightAction);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -1, 1);
        p1.rightMargin = dp(6);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -1, 1);
        p2.leftMargin = dp(6);
        row.addView(a, p1);
        row.addView(b, p2);
    }

    private void render() {
        ScrollView container = new ScrollView(this);
        container.setFillViewport(true);
        container.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{BACKGROUND, 0xff102a3b, BACKGROUND}
        ));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        root.setFocusableInTouchMode(true);
        container.addView(root);
        setContentView(container);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView mark = text("◆  QUESTFREEDOM", 13, ACCENT, true);
        top.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        time = text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()), 13, MUTED, false);
        top.addView(time);

        TextView title = text("Android, your way.", 29, FOREGROUND, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(14);
        root.addView(title, titleParams);
        root.addView(text("0.2 ALPHA  /  EXPERIMENTAL LAUNCHER", 12, ACCENT, true));

        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "unknown ABI";
        TextView device = text("Android " + Build.VERSION.RELEASE + "  ·  " + abi, 12, MUTED, false);
        LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(-1, -2);
        deviceParams.topMargin = dp(5);
        deviceParams.bottomMargin = dp(18);
        root.addView(device, deviceParams);

        addActionPair(root, "Wi-Fi", () -> safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)),
                "Bluetooth", () -> safeStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addActionPair(root, "System settings", () -> safeStart(new Intent(Settings.ACTION_SETTINGS)),
                "Files", () -> {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType("*/*");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    safeStart(pick);
                });

        TextView heading = text("Your applications", 20, FOREGROUND, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(12);
        root.addView(heading, hp);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search apps or package name");
        search.setHintTextColor(MUTED);
        search.setTextColor(FOREGROUND);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(surface(PANEL, 14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.topMargin = dp(12);
        sp.bottomMargin = dp(9);
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                showPackages();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        counter = text("", 13, MUTED, false);
        root.addView(counter);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = dp(8);
        root.addView(results, resultParams);

        addActionPair(root, "Refresh apps", this::refreshPackages,
                hideMeta ? "Show Meta apps" : "Hide Meta apps", () -> {
                    hideMeta = !hideMeta;
                    preferences.edit().putBoolean("hide_meta", hideMeta).apply();
                    render();
                    refreshPackages();
                });

        TextView info = text("Meta services remain on the device. This app changes only what is shown here; it does not replace Horizon OS.", 12, MUTED, false);
        info.setPadding(dp(14), dp(13), dp(14), dp(13));
        info.setBackground(surface(PANEL_ALT, 13));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));
        root.requestFocus();
    }

    private void refreshPackages() {
        if (results == null) return;
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        launchables.clear();
        for (ResolveInfo item : pm.queryIntentActivities(query, 0)) {
            if (item.activityInfo != null && !getPackageName().equals(item.activityInfo.packageName)) {
                launchables.add(item);
            }
        }
        showPackages();
    }

    private void toggleFavorite(String packageName) {
        Set<String> favorites = favorites();
        if (!favorites.remove(packageName)) favorites.add(packageName);
        preferences.edit().putStringSet("favorites", favorites).apply();
        Toast.makeText(this, favorites.contains(packageName) ? "Pinned to top" : "Unpinned", Toast.LENGTH_SHORT).show();
        showPackages();
    }

    private Set<String> favorites() {
        return new HashSet<>(preferences.getStringSet("favorites", Collections.emptySet()));
    }

    private void showPackages() {
        if (results == null || search == null) return;
        PackageManager pm = getPackageManager();
        String q = search.getText().toString();
        Set<String> favorites = favorites();
        List<ResolveInfo> visible = new ArrayList<>();
        for (ResolveInfo item : launchables) {
            String pkg = item.activityInfo.packageName;
            if (PackagePolicy.shouldShow(pkg, hideMeta)
                    && LauncherFilter.matches(item.loadLabel(pm).toString(), pkg, q)) {
                visible.add(item);
            }
        }
        visible.sort((a, b) -> {
            boolean pinnedA = favorites.contains(a.activityInfo.packageName);
            boolean pinnedB = favorites.contains(b.activityInfo.packageName);
            if (pinnedA != pinnedB) return pinnedA ? -1 : 1;
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
        counter.setText(visible.size() + " applications  ·  Long-press to pin a favorite");
        results.removeAllViews();

        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int columns = Math.max(1, Math.min(5, (widthDp - 40) / 172));
        int cardWidth = Math.max(100, (widthDp - 40 - columns * 12) / columns);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        results.addView(grid);
        for (ResolveInfo app : visible) {
            String pkg = app.activityInfo.packageName;
            String cls = app.activityInfo.name;
            String appName = app.loadLabel(pm).toString();
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackground(surface(favorites.contains(pkg) ? PANEL_ALT : PANEL, 14));
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription("Open " + appName + ". Long press to pin or unpin.");
            ImageView icon = new ImageView(this);
            try { icon.setImageDrawable(app.loadIcon(pm)); } catch (RuntimeException ignored) {}
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
            iconParams.rightMargin = dp(9);
            card.addView(icon, iconParams);
            TextView name = text((favorites.contains(pkg) ? "★ " : "") + appName, 14, FOREGROUND, true);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            card.setOnClickListener(v -> {
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(pkg, cls);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                safeStart(launch);
            });
            card.setOnLongClickListener(v -> {
                toggleFavorite(pkg);
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(cardWidth);
            lp.height = dp(82);
            lp.setMargins(dp(6), dp(5), dp(6), dp(5));
            grid.addView(card, lp);
        }
        if (visible.isEmpty()) {
            TextView empty = text("No matching apps. Try a different search or show hidden apps.", 14, MUTED, false);
            empty.setPadding(dp(10), dp(16), dp(10), dp(24));
            results.addView(empty);
        }
    }
}
