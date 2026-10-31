package org.telegram.ui;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.view.HapticFeedbackConstants;

/** FluidGram Labs — standalone, theme-independent experimental control center. */
public final class FluidLabsActivity extends Activity {
    private LinearLayout root;
    private SharedPreferences preferences;
    private boolean neon;
    private static final int BG = 0xFF0B1728;
    private static final int ACCENT = 0xFF55E8D7;
    private static final String[] MODE_NAMES = {
        "ColorOS · Автоматически", "Adaptive+ · Высокая плавность",
        "Максимум · до 165 Гц", "Умный · экономия при 25%"
    };
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int first, int second, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first, second});
        d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), neon ? 0xAA3FFFE9 : 0x665C91B0);
        return d;
    }
    @Override public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        preferences = FluidRefresh.prefs(this);
        neon = preferences.getBoolean("neon_labs", true);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        FluidRefresh.resume(this);
    }
    @Override protected void onPause() {
        FluidRefresh.pause(this);
        super.onPause();
    }
    private TextView text(String content, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    private LinearLayout.LayoutParams margins(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private void render() {
        neon = preferences.getBoolean("neon_labs", true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(38));
        scroll.addView(root);
        setContentView(scroll);
        TextView back = text("‹   НАЗАД К НАСТРОЙКАМ", 13, ACCENT, true);
        back.setPadding(dp(8), dp(8), dp(8), dp(20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(21), dp(22), dp(21), dp(23));
        hero.setBackground(background(neon ? 0xFF12635F : 0xFF1E3D57,
                0xFF12263C, 26, true));
        TextView eyebrow = text("FLUIDGRAM  /  LABS  0.5", 12, 0xFFB6FFEE, true);
        hero.addView(eyebrow, margins(9));
        TextView headline = text("Свой Telegram.\nСвои правила.", 27, Color.WHITE, true);
        hero.addView(headline, margins(10));
        TextView desc = text("Плавность, визуальные эффекты и приватные настройки для OnePlus 15.", 14, 0xFFC6DBE7, false);
        hero.addView(desc);
        root.addView(hero, margins(24));

        section("ЭКРАН  /  165 Гц");
        int mode = preferences.getInt("refresh_mode", 1);
        card("Частота обновления", MODE_NAMES[Math.max(0, Math.min(mode, 3))],
            "ColorOS может отклонить запрос приложения.", () -> {
                new AlertDialog.Builder(this).setTitle("Частота обновления")
                    .setSingleChoiceItems(MODE_NAMES, mode, (dlg, which) -> {
                        preferences.edit().putInt("refresh_mode", which).apply();
                        dlg.dismiss();
                        FluidRefresh.refresh(this);
                        render();
                    }).setNegativeButton("Отмена", null).show();
            });
        Display d = getWindowManager().getDefaultDisplay();
        card("Экран устройства",
             Math.round(d.getRefreshRate()) + " Гц сейчас  /  до " +
                     Math.round(FluidRefresh.maxSupportedHz(this)) + " Гц доступно",
             "Значения сообщает Android, не обещание стабильных 165 FPS.", null);
        section("ОСОБЕННОСТИ  /  LIVE");
        toggle("VSync HUD", "Показывать частоту кадровых импульсов Android в плавающей капсуле. Не равно реальному FPS видео.",
               "show_vsync", false);
        toggle("Экран без сна", "Не выключать экран, пока FluidGram открыт. Может повышать расход энергии.",
               "keep_screen_on", false);
        toggle("Neon Glass", "Бирюзовые стеклянные карточки и контрастная панель FluidGram Labs.",
               "neon_labs", true);
        toggle("Тактильное переключение", "Короткий виброотклик при смене камеры видеокружка.",
               "camera_haptic", true);
        section("ДИАГНОСТИКА  /  COLOROS");
        card("Скопировать режимы экрана", "Отладка 90 / 120 / 165 Гц",
            "Экспортирует только модель устройства, частоты дисплея и выбранный режим. Без переписок и ключей.",
            () -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FluidGram display report", FluidRefresh.diagnostic(this)));
                Toast.makeText(this, "Параметры дисплея скопированы", Toast.LENGTH_SHORT).show();
            });
        TextView footer = text("165 Гц запрашиваются только среди режимов, доступных через Android. При «Автоматически» управление остаётся у ColorOS. Параметры не требуют root или скрытых разрешений.", 12, 0xFF7895AC, false);
        footer.setPadding(dp(8), dp(12), dp(8), dp(3));
        root.addView(footer);
    }
    private void section(String title) {
        TextView t = text(title, 12, ACCENT, true);
        t.setPadding(dp(4), dp(6), dp(4), dp(11));
        root.addView(t);
    }
    private void card(String heading, String value, String description, Runnable click) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17), dp(16), dp(17), dp(16));
        panel.setBackground(background(0xFF1B3046, neon ? 0xFF122D37 : 0xFF14253A, 20, true));
        panel.addView(text(heading, 17, Color.WHITE, true), margins(5));
        panel.addView(text(value, 14, ACCENT, true), margins(6));
        panel.addView(text(description, 12, 0xFFA9BECD, false));
        if (click != null) {
            panel.setClickable(true);
            panel.setOnClickListener(v -> {
                panel.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                click.run();
            });
        }
        root.addView(panel, margins(10));
    }
    private void toggle(String name, String details, String key, boolean initial) {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(15), dp(13), dp(13), dp(13));
        panel.setBackground(background(0xFF1B3046, 0xFF112B34, 20, true));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(name, 16, Color.WHITE, true), margins(5));
        labels.addView(text(details, 12, 0xFFADBECE, false));
        panel.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(preferences.getBoolean(key, initial));
        panel.addView(sw);
        sw.setOnCheckedChangeListener((button, isChecked) -> {
            preferences.edit().putBoolean(key, isChecked).apply();
            if ("neon_labs".equals(key)) render();
            FluidRefresh.refresh(this);
        });
        panel.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        root.addView(panel, margins(10));
    }
}
