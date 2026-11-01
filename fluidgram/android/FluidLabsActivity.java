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
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.view.HapticFeedbackConstants;

/** FluidGram Labs — standalone, theme-independent experimental control center. */
public final class FluidLabsActivity extends Activity {
    private LinearLayout root;
    private FluidGlass.Backdrop glassBackdrop;
    private SharedPreferences preferences;
    private boolean neon;
    private static final int VPN_IMPORT_REQUEST = 17011;
    private String vpnImportSummary = "Профиль ещё не проверен";
    private boolean batteryReceiverRegistered;
    // Event-driven glass policy: no polling or per-frame battery work.
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (glassBackdrop == null) return;
            // Battery broadcasts already carry the level. Avoid a second
            // sticky registerReceiver(null, ...) query for each event.
            if (intent != null && Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    android.os.PowerManager pm = (android.os.PowerManager)
                            getSystemService(Context.POWER_SERVICE);
                    boolean enabled = level * 100 / scale > 20
                            && (pm == null || !pm.isPowerSaveMode());
                    FluidGlass.setAmbientBlur(glassBackdrop, enabled);
                    return;
                }
            }
            FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
        }
    };
    private static final int BG = 0xFF050A11;
    private static final int ACCENT = 0xFFE2F9FF;
    private static final String[] MODE_NAMES = {
        "ColorOS · Автоматически", "Adaptive+ · Высокая плавность",
        "Максимум · совместимый режим", "Умный · экономия при 20%",
        "Adaptive · прокрутка / покой"
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
        if (!batteryReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            registerReceiver(batteryReceiver, filter);
            batteryReceiverRegistered = true;
        }
        if (glassBackdrop != null) FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
    }
    @Override protected void onPause() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        if (glassBackdrop != null) FluidGlass.setAmbientBlur(glassBackdrop, false);
        FluidRefresh.pause(this);
        super.onPause();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_IMPORT_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        // User-selected URI is read once. No persistable permission or secret storage.
        try (java.io.InputStream input = getContentResolver().openInputStream(data.getData())) {
            FluidVpnProfile.Info info = FluidVpnProfile.inspect(input);
            String coverage = info.fullTunnel ? "IPv4 и IPv6 · оба маршрута /0"
                    : info.ipv4Default ? "только IPv4 /0 · IPv6 вне полного маршрута"
                    : info.ipv6Default ? "только IPv6 /0 · IPv4 вне полного маршрута"
                    : "только выборочные маршруты";
            vpnImportSummary = "Формат проверен: " + info.peers + " peer · " + coverage
                    + (info.hasDns ? " · DNS указан" : " · DNS не указан")
                    + ". Это НЕ проверка подключения или защиты от утечек.";
            Toast.makeText(this, "Формат профиля проверен. VPN не подключён.", Toast.LENGTH_LONG).show();
        } catch (FluidVpnProfile.InvalidProfile invalid) {
            // These messages are fixed parser strings and never contain private keys.
            vpnImportSummary = "Проверка не пройдена: " + invalid.getMessage();
        } catch (Exception ignored) {
            // Never log file contents, paths, provider errors or credentials.
            vpnImportSummary = "Не удалось прочитать конфигурацию";
        }
        render();
    }

    private void openVpnFile() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(pick, VPN_IMPORT_REQUEST);
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, "Системный выбор файлов недоступен", Toast.LENGTH_LONG).show();
        }
    }

    private boolean allowGlassBlur() {
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        return FluidRefresh.batteryPercent(this) > 20
                && (power == null || !power.isPowerSaveMode());
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
        scroll.setBackgroundColor(Color.TRANSPARENT);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(38));
        scroll.addView(root);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(BG);
        glassBackdrop = new FluidGlass.Backdrop(this);
        scene.addView(glassBackdrop, new FrameLayout.LayoutParams(-1, -1));
        FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
        scene.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        TextView back = text("‹   НАЗАД К НАСТРОЙКАМ", 13, ACCENT, true);
        back.setPadding(dp(8), dp(8), dp(8), dp(20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(21), dp(22), dp(21), dp(23));
        hero.setBackground(FluidGlass.surface(this, true));
        hero.setElevation(dp(8));
        TextView eyebrow = text("FLUIDGRAM  /  GLASS LABS  1.2", 12, 0xFFB6FFEE, true);
        hero.addView(eyebrow, margins(9));
        TextView headline = text("Живое стекло.\nТвой Telegram.", 27, Color.WHITE, true);
        hero.addView(headline, margins(10));
        TextView desc = text("Стеклянные слои, световые отражения и плавность с контролем расхода энергии.", 14, 0xFFC6DBE7, false);
        hero.addView(desc);
        root.addView(hero, margins(24));

        section("ЭКРАН  /  ADAPTIVE REFRESH");
        int mode = preferences.getInt("refresh_mode", 4);
        card("Частота обновления", MODE_NAMES[Math.max(0, Math.min(mode, 4))],
            "При прокрутке запрашивает совместимый максимум, в покое — системный режим. При 20% заряда и энергосбережении повышенный запрос отключается.", () -> {
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
        toggle("Экран · частота HUD", "Показывает частоту дисплея по событиям Android, без постоянной отрисовки. Не измеряет FPS приложения.",
               "show_vsync", false);
        toggle("Экран без сна", "Не выключать экран, пока FluidGram открыт. Может повышать расход энергии.",
               "keep_screen_on", false);
        toggle("Glass highlights", "Световые отражения и контрастная подсветка стеклянных элементов.",
               "neon_labs", true);
        toggle("Тактильное переключение", "Короткий виброотклик при смене камеры видеокружка.",
               "camera_haptic", true);
        section("ЭНЕРГОЭФФЕКТИВНОСТЬ");
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        int charge = FluidRefresh.batteryPercent(this);
        card("Энергетический профиль", charge + "% · " +
                (saver ? "энергосбережение Android" : "обычный режим"),
            "Все режимы повышенной частоты отключают запрос при ≤20% или энергосбережении. При уходе из приложения запрос снимается. Экономия требует измерений.", null);
        section("ENERGY LAB  /  БЕЗ ФОНОВОГО ОПРОСА");
        card("Измерение заряда устройства", "Снимок по запросу",
            "Сравнивай показания до и после использования. Датчик учитывает весь телефон, а не только FluidGram. Никаких таймеров и фонового мониторинга.",
            () -> new AlertDialog.Builder(this)
                    .setTitle("Energy Lab · измерение")
                    .setMessage(FluidEnergy.report(this))
                    .setPositiveButton("Скопировать", (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText(
                                    "FluidGram energy report", FluidEnergy.report(this)));
                            Toast.makeText(this, "Энергетический отчёт скопирован", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Закрыть", null).show());
        card("Новая точка отсчёта", "Сбросить измерение",
            "Обнуляет накопленное время и изменение заряда. Не меняет системные настройки батареи.",
            () -> {
                FluidEnergy.reset(this);
                Toast.makeText(this, "Новая точка отсчёта сохранена", Toast.LENGTH_SHORT).show();
            });
        section("VPN · подготовка");
        card("Импорт WireGuard .conf", vpnImportSummary,
            "Строгая проверка IPv4/IPv6, ключей и адресов без подключения. Секретные ключи не сохраняются. "
            + "Файл ограничен 64 КиБ; серверы не предоставляются.",
            this::openVpnFile);
        card("Встроенное подключение", "Пока недоступно · туннель не создан",
            "Для реального подключения нужен проверенный VPN-движок, системное согласие Android "
            + "и ваш сервер или профиль. Импорт сам по себе не защищает трафик.", null);
        card("VPN-настройки Android", "Открыть системные настройки",
            "Для управления уже установленными VPN-приложениями на телефоне.",
            () -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));
                } catch (android.content.ActivityNotFoundException ignored) {
                    Toast.makeText(this, "Настройки VPN недоступны", Toast.LENGTH_LONG).show();
                }
            });
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
        panel.setBackground(FluidGlass.surface(this, false));
        panel.setElevation(dp(5));
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
        panel.setBackground(FluidGlass.surface(this, false));
        panel.setElevation(dp(5));
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
