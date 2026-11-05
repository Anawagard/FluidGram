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
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.view.HapticFeedbackConstants;

/** FluidGram Labs — standalone, theme-independent experimental control center. */
public final class FluidLabsActivity extends Activity {
    private LinearLayout root;
    private ScrollView labsScroll;
    private static final String STATE_LABS_SCROLL_Y = "fluidgram_labs_scroll_y";
    private final java.util.Map<String, View> sectionAnchors = new java.util.HashMap<>();
    private FluidGlass.Backdrop glassBackdrop;
    private SharedPreferences preferences;
    private boolean neon;
    private static final int VPN_IMPORT_REQUEST = 17011;
    private static final int VPN_PERMISSION_REQUEST = 17012;
    // Imported profile exists in RAM only; never saved in preferences or a Bundle.
    private com.wireguard.config.Config pendingVpnConfig;
    private String vpnImportSummary = "Профиль ещё не проверен";
    private String vlessSummary = "VLESS пока не подключается: движок не установлен";
    // Cached only while visible; shared with FluidRefresh's single power receiver.
    private int cachedBatteryPercent = -1;
    private static final int BG = 0xFF050A11;
    private static final int ACCENT = 0xFFE2F9FF;
    private static final String[] GHOST_INDICATOR_KEYS = {
        "ghost_hide_typing", "ghost_hide_recording_audio",
        "ghost_hide_audio_upload_indicator", "ghost_hide_recording_video",
        "ghost_hide_recording_round", "ghost_hide_photo_upload_indicator",
        "ghost_hide_video_upload_indicator", "ghost_hide_document_upload_indicator",
        "ghost_hide_choosing_sticker"
    };
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
        if (savedInstance != null) {
            restoreScrollAfterRecreation(savedInstance.getInt(STATE_LABS_SCROLL_Y, 0));
        }
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        // Keep the current Labs section visible after a rotation or Activity recreation.
        // UI-only state: never persist VPN profiles or chat data.
        outState.putInt(STATE_LABS_SCROLL_Y, labsScroll == null ? 0 : labsScroll.getScrollY());
        super.onSaveInstanceState(outState);
    }
    private void restoreScrollAfterRecreation(int savedY) {
        final ScrollView target = labsScroll;
        if (target == null || savedY <= 0) return;
        target.post(() -> {
            // Do not restore into a stale view after a theme change or Activity finish.
            if (target != labsScroll || isFinishing() || isDestroyed()) return;
            int maxY = Math.max(0, root.getHeight() - target.getHeight());
            target.scrollTo(0, Math.min(savedY, maxY));
        });
    }
    @Override protected void onResume() {
        super.onResume();
        FluidRefresh.resume(this);
        updateGlassPowerPolicy(-1);
    }
    @Override protected void onPause() {
        if (glassBackdrop != null) FluidGlass.setAmbientBlur(glassBackdrop, false);
        cachedBatteryPercent = -1;
        FluidRefresh.pause(this);
        super.onPause();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) connectApprovedVpn();
            else Toast.makeText(this, "Разрешение Android на VPN не предоставлено", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode != VPN_IMPORT_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        // User-selected URI is read once. No persistable permission or secret storage.
        try (java.io.InputStream input = getContentResolver().openInputStream(data.getData())) {
            FluidVpnTunnel.Imported imported = FluidVpnTunnel.importProfile(input, getPackageName());
            pendingVpnConfig = imported.config;
            FluidVpnProfile.Info info = imported.info;
            String coverage = info.fullTunnel ? "IPv4 и IPv6 · оба маршрута /0"
                    : info.ipv4Default ? "только IPv4 /0 · IPv6 вне полного маршрута"
                    : info.ipv6Default ? "только IPv6 /0 · IPv4 вне полного маршрута"
                    : "только выборочные маршруты";
            vpnImportSummary = "Формат проверен: " + info.peers + " peer · " + coverage
                    + (info.hasDns ? " · DNS указан" : " · DNS не указан")
                    + (info.dualStackConfigured ? " · адреса IPv4+IPv6 есть"
                       : info.fullTunnel ? " · ВНИМАНИЕ: отсутствует адрес одного из IP-семейств"
                       : " · выборочная маршрутизация")
                    + ". Это НЕ проверка подключения или защиты от утечек.";
            Toast.makeText(this, "Профиль проверен. Для подключения нужно согласие Android.", Toast.LENGTH_LONG).show();
        } catch (FluidVpnProfile.InvalidProfile invalid) {
            pendingVpnConfig = null;
            // These messages are fixed parser strings and never contain private keys.
            vpnImportSummary = "Проверка не пройдена: " + invalid.getMessage();
        } catch (Exception ignored) {
            // Never log file contents, paths, provider errors or credentials.
            pendingVpnConfig = null;
            vpnImportSummary = "Не удалось прочитать или проверить конфигурацию";
        }
        render();
    }

    private void openVpnFile() {
        if (FluidVpnTunnel.isConnected() || FluidVpnTunnel.isBusy()
                || FluidVpnTunnel.requiresCleanup()) {
            Toast.makeText(this, "Сначала отключите текущий VPN", Toast.LENGTH_SHORT).show();
            return;
        }
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

    private void requestVpnConnect() {
        if (pendingVpnConfig == null) {
            Toast.makeText(this, "Сначала импортируйте профиль WireGuard", Toast.LENGTH_LONG).show();
            return;
        }
        if (FluidVpnTunnel.isBusy() || FluidVpnTunnel.isConnected()) return;
        Intent consent;
        try {
            consent = android.net.VpnService.prepare(this);
        } catch (SecurityException ignored) {
            Toast.makeText(this, "Android запретил запуск VPN", Toast.LENGTH_LONG).show();
            return;
        }
        if (consent != null) {
            try {
                startActivityForResult(consent, VPN_PERMISSION_REQUEST);
            } catch (android.content.ActivityNotFoundException ignored) {
                Toast.makeText(this, "Системное разрешение VPN недоступно", Toast.LENGTH_LONG).show();
            }
        } else {
            connectApprovedVpn();
        }
    }

    private void connectApprovedVpn() {
        if (pendingVpnConfig == null) return;
        if (FluidVpnTunnel.connect(this, pendingVpnConfig, success -> {
            if (success) pendingVpnConfig = null; // Do not retain duplicate key references.
            if (!isFinishing() && !isDestroyed()) render();
            Toast.makeText(getApplicationContext(),
                    success ? "VPN запущен · только FluidGram" : "Не удалось запустить VPN",
                    Toast.LENGTH_LONG).show();
        })) render();
    }

    private void stopVpn() {
        if (FluidVpnTunnel.disconnect(success -> {
            if (!isFinishing() && !isDestroyed()) render();
            Toast.makeText(getApplicationContext(),
                    success ? "VPN отключён" : "Не удалось отключить VPN",
                    Toast.LENGTH_LONG).show();
        })) render();
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        FluidRefresh.onInteraction(this);
    }

    @Override protected void onDestroy() {
        pendingVpnConfig = null;
        super.onDestroy();
    }

    /** Reuse FluidRefresh's foreground power receiver instead of registering a second one. */
    void updateGlassPowerPolicy(int batteryPercent) {
        if (batteryPercent >= 0 && batteryPercent <= 100) cachedBatteryPercent = batteryPercent;
        if (glassBackdrop != null) FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
    }

    // Called only when Android reports a thermal threshold transition.
    void updateGlassThermalPolicy() {
        if (glassBackdrop != null) FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
    }

    private boolean allowGlassBlur() {
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        return !preferences.getBoolean("reduce_glass_transparency", false)
                && (cachedBatteryPercent >= 0 ? cachedBatteryPercent
                : FluidRefresh.batteryPercent(this)) > 20
                && (power == null || !power.isPowerSaveMode())
                && !FluidRefresh.isThermallyConstrained(this);
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
    /**
     * On-demand Android notification health snapshot. No network calls, polling,
     * wake locks, background services, or persisted chat/notification metadata.
     * FCM delivery latency cannot be measured from these OS settings.
     */
    private String notificationHealthReport() {
        StringBuilder report = new StringBuilder();
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            boolean allowed = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            report.append("Разрешение Android 13+: ")
                    .append(allowed ? "выдано" : "НЕ выдано").append("\n");
        }
        if (manager == null) {
            report.append("Служба уведомлений Android недоступна.\n");
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                report.append("Уведомления приложения: ")
                        .append(manager.areNotificationsEnabled() ? "разрешены" : "ОТКЛЮЧЕНЫ")
                        .append("\n");
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                java.util.List<android.app.NotificationChannel> channels =
                        manager.getNotificationChannels();
                int blocked = 0;
                int quiet = 0;
                for (android.app.NotificationChannel channel : channels) {
                    int importance = channel.getImportance();
                    if (importance == android.app.NotificationManager.IMPORTANCE_NONE) blocked++;
                    else if (importance <= android.app.NotificationManager.IMPORTANCE_LOW) quiet++;
                }
                report.append("Каналов: ").append(channels.size())
                        .append("; отключено: ").append(blocked)
                        .append("; с низким приоритетом: ").append(quiet).append("\n");
                report.append("Отключённые каналы могут быть намеренно заглушёнными чатами.\n");
            }
        }
        android.os.PowerManager power =
                (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            boolean optimized = !power.isIgnoringBatteryOptimizations(getPackageName());
            boolean powerSave = power.isPowerSaveMode();
            report.append("Оптимизация батареи: ")
                    .append(optimized ? "действует" : "для приложения исключена")
                    .append("\n");
            report.append("Энергосбережение Android: ")
                    .append(powerSave ? "включено" : "выключено")
                    .append("\n");
            // Advice is conditional and read-only; never silently change OS settings.
            if (optimized || powerSave) {
                report.append("Для проверки задержек push: ");
                if (powerSave) report.append("временно отключите общее энергосбережение; ");
                if (optimized) report.append("проверьте разрешение фоновой работы FluidGram в ColorOS; ");
                report.append("затем сравните уведомления при заблокированном экране.\n");
            }
        }
        // App Standby is independent of battery optimization. Read only on demand.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.app.usage.UsageStatsManager usage =
                    (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usage != null) {
                try {
                    int bucket = usage.getAppStandbyBucket();
                    String standby = bucket <= android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE
                            ? "активный"
                            : bucket <= android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET
                            ? "часто используемый"
                            : bucket <= android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT
                            ? "периодический"
                            : bucket <= android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE
                            ? "редкий"
                            : bucket <= android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED
                            ? "ограниченный" : "неизвестный";
                    report.append("Режим ожидания приложения: ").append(standby)
                            .append(" (").append(bucket).append(")\n");
                } catch (SecurityException ignored) {
                    report.append("Режим ожидания приложения: недоступен\n");
                }
            }
        }
        // App-only WireGuard cannot route Google Play Services FCM traffic.
        // Warn without claiming that delivery is actually broken.
        if (FluidVpnTunnel.isConnected()) {
            report.append("Встроенный WireGuard: подключён, только FluidGram.\\n");
            report.append("FCM обрабатывают Google Play Services вне VPN FluidGram. "
                    + "Если сеть блокирует push, сравните доставку с системным VPN.\\n");
        } else {
            report.append("Встроенный WireGuard: не подключён.\\n");
            report.append("Если сеть блокирует Telegram, без доступного прокси/VPN "
                    + "сообщения и уведомления могут не доставляться.\\n");
        }
        report.append("\nЭто проверка настроек, НЕ тест доставки push. ")
                .append("Задержку FCM, соединения Telegram и ограничения автозапуска ColorOS ")
                .append("Android не раскрывает через эти API. Сравните получение сообщения ")
                .append("с обычным Telegram при заблокированном экране.");
        return report.toString();
    }

    private void render() {
        // Rebuilding glass surfaces must not throw the user back to the top.
        final ScrollView previousScroll = labsScroll;
        final int previousY = previousScroll == null ? 0 : previousScroll.getScrollY();
        neon = preferences.getBoolean("neon_labs", true);
        ScrollView scroll = new ScrollView(this);
        labsScroll = scroll;
        sectionAnchors.clear();
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(38));
        scroll.addView(root);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(BG);
        glassBackdrop = null;
        if (!preferences.getBoolean("reduce_glass_transparency", false)) {
            glassBackdrop = new FluidGlass.Backdrop(this);
            scene.addView(glassBackdrop, new FrameLayout.LayoutParams(-1, -1));
            FluidGlass.setAmbientBlur(glassBackdrop, allowGlassBlur());
        }
        scene.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        if (previousScroll != null) {
            scroll.post(() -> {
                // Ignore stale restoration after a second render or Activity finish.
                if (labsScroll != scroll || isFinishing() || isDestroyed()) return;
                int limit = Math.max(0, root.getHeight() - scroll.getHeight());
                scroll.scrollTo(0, Math.min(previousY, limit));
            });
        }
        TextView back = text("‹   НАЗАД К НАСТРОЙКАМ", 13, ACCENT, true);
        back.setPadding(dp(8), dp(8), dp(8), dp(20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(21), dp(22), dp(21), dp(23));
        hero.setBackground(FluidGlass.surface(this, true));
        hero.setElevation(dp(8));
        TextView eyebrow = text("FLUIDGRAM  /  GLASS LABS  1.6", 12, 0xFFB6FFEE, true);
        hero.addView(eyebrow, margins(9));
        TextView headline = text("Живое стекло.\nТвой Telegram.", 27, Color.WHITE, true);
        hero.addView(headline, margins(10));
        TextView desc = text("Стеклянные слои, световые отражения и плавность с контролем расхода энергии.", 14, 0xFFC6DBE7, false);
        hero.addView(desc);
        root.addView(hero, margins(15));
        root.addView(quickNavigation(), margins(22));

        section("ЭКРАН  /  ADAPTIVE REFRESH");
        int mode = FluidRefresh.selectedMode(this);
        card("Частота обновления", MODE_NAMES[Math.max(0, Math.min(mode, MODE_NAMES.length - 1))],
            "Рекомендуется ColorOS · Автоматически: без запросов частоты от FluidGram. Дополнительные режимы ограничены 120 Гц, но могут мешать снижению LTPO в покое.", () -> {
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
             "Android сообщает режимы дисплея, а не реальную частоту LTPO. ColorOS Auto не гарантирует 1 Гц.", null);
        section("ОСОБЕННОСТИ  /  LIVE");
        toggle("Экран · частота HUD", "Показывает частоту дисплея по событиям Android, без постоянной отрисовки. Не измеряет FPS приложения.",
               "show_vsync", false);
        toggle("Экран без сна", "Не выключать экран, пока FluidGram открыт. Может повышать расход энергии.",
               "keep_screen_on", false);
        toggle("Непрозрачные карточки", "Контрастные поверхности без декоративного размытия и прозрачных слоёв в Labs. Можно отключить в любой момент.",
               "reduce_glass_transparency", false);
        toggle("Спокойная навигация", "Мгновенные переходы между разделами Labs без анимации прокрутки. Также учитывает системное отключение анимаций.",
               "reduce_motion_labs", false);
        toggle("Glass highlights", "Световые отражения и контрастная подсветка стеклянных элементов.",
               "neon_labs", true);
        toggle("Liquid Glass · сообщения (эксперимент)",
               "Дополнительно уменьшает непрозрачность входящих и исходящих пузырей в 2 раза "
               + "в реальных чатах Telegram, включая светлую и тёмную темы. "
               + "Это прозрачность без оптического преломления и без размытия фона. "
               + "Для применения полностью закройте и снова откройте FluidGram.",
               "experimental_glass_bubbles", false);
        toggle("Тактильное переключение", "Короткий виброотклик при смене камеры видеокружка.",
               "camera_haptic", true);
        section("ПРИВАТНОСТЬ  /  GHOST LAB");
        toggle("Не показывать «печатает…»",
               "Опционально блокирует исходящие Telegram messages.setTyping с действием typing "
               + "до отправки в сеть. Не скрывает прочтение, онлайн, запись, загрузку "
               + "или действия в секретных чатах. Другие устройства могут показывать статус.",
               "ghost_hide_typing", false);
        toggle("Не показывать «записывает голосовое…»",
               "Блокирует только исходящий индикатор записи аудио через messages.setTyping "
               + "с действием recordAudio. Не блокирует загрузку или отправку голосового, "
               + "не скрывает видеозапись, онлайн, прочтение и действия других клиентов. "
               + "Работу проверьте с другим аккаунтом.",
               "ghost_hide_recording_audio", false);
        toggle("Не показывать «загружает аудио…»",
               "Отдельно скрывает только исходящий индикатор загрузки аудио "
               + "(messages.setTyping: uploadAudio). Сам файл и сообщение "
               + "продолжают отправляться. Не скрывает статус онлайн, прочтение, "
               + "секретные чаты и активность других устройств. Проверьте с другим аккаунтом.",
               "ghost_hide_audio_upload_indicator", false);
        toggle("Не показывать «записывает видео…»",
               "Блокирует только исходящий индикатор записи обычного видео "
               + "(messages.setTyping: recordVideo). Не блокирует "
               + "отправку видео, загрузки, звонки, прочтение или статус онлайн. "
               + "Другие клиенты Telegram продолжат передавать свои индикаторы. "
               + "Проверьте работу с другим аккаунтом.",
               "ghost_hide_recording_video", false);
        toggle("Не показывать «записывает видеокружок…»",
               "Отдельно блокирует только исходящий индикатор записи видеокружка "
               + "(messages.setTyping: recordRound). Не блокирует запись, загрузку "
               + "или отправку кружков, уведомления и сообщения. Не скрывает "
               + "онлайн, прочтение и активность других устройств.",
               "ghost_hide_recording_round", false);
        toggle("Не показывать «отправляет фото…»",
               "Блокирует только исходящий индикатор загрузки фотографии "
               + "(messages.setTyping: uploadPhoto), но не саму загрузку, отправку "
               + "или получение фото. Не скрывает онлайн, прочтение и действия "
               + "других клиентов Telegram. Проверьте с другим аккаунтом.",
               "ghost_hide_photo_upload_indicator", false);
        toggle("Не показывать «отправляет видео…»",
               "По отдельному согласию блокирует только исходящие индикаторы "
               + "загрузки видео и видеокружков (messages.setTyping: uploadVideo / "
               + "uploadRound). Сами файлы и сообщения продолжают отправляться. "
               + "Не скрывает статус онлайн, прочтение или активность других "
               + "клиентов Telegram. Проверьте с другим аккаунтом.",
               "ghost_hide_video_upload_indicator", false);
        toggle("Не показывать «отправляет файл…»",
               "Отдельно блокирует только исходящий индикатор загрузки документа "
               + "(messages.setTyping: uploadDocument). Передача файлов и сообщений "
               + "не блокируется. Статус онлайн, прочтение, секретные чаты и "
               + "активность других устройств не скрываются. Проверьте с другим аккаунтом.",
               "ghost_hide_document_upload_indicator", false);
        toggle("Не показывать «выбирает стикер…»",
               "Отдельно блокирует только исходящий индикатор выбора стикера "
               + "(messages.setTyping: chooseSticker). Сами стикеры и сообщения "
               + "отправляются как обычно. Не скрывает онлайн, прочтение, действия "
               + "других устройств и секретных чатов. Проверьте с другим аккаунтом.",
               "ghost_hide_choosing_sticker", false);
        card("Сброс индикаторов Ghost", "Вернуть стандартное поведение Telegram",
            "Отключает только девять отдельных ограничений исходящих индикаторов "
                + "набора, записи и загрузки. Сообщения, уведомления и настройки "
                + "приватности Telegram не изменяются.",
            () -> new AlertDialog.Builder(this)
                    .setTitle("Сбросить ограничения индикаторов?")
                    .setMessage("После подтверждения FluidGram снова будет передавать "
                            + "поддерживаемые индикаторы действий Telegram обычным способом. "
                            + "Настройки других устройств не изменятся.")
                    .setPositiveButton("Сбросить", (dialog, which) -> {
                        SharedPreferences.Editor editor = preferences.edit();
                        for (String key : GHOST_INDICATOR_KEYS) editor.putBoolean(key, false);
                        editor.apply();
                        Toast.makeText(this, "Ограничения индикаторов отключены", Toast.LENGTH_SHORT).show();
                        render();
                    })
                    .setNegativeButton("Отмена", null).show());
        section("УВЕДОМЛЕНИЯ  /  TELEGRAM");
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notificationsEnabled = notificationManager != null
                && notificationManager.areNotificationsEnabled();
        android.os.PowerManager notificationPower =
                (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryExempt = notificationPower != null
                && notificationPower.isIgnoringBatteryOptimizations(getPackageName());
        card("Доставка уведомлений", notificationsEnabled ? "Разрешены Android" : "Отключены Android",
            "FluidGram использует оригинальный механизм push Telegram. "
                + "Для своевременной доставки разрешите уведомления и фоновую работу "
                + "в ColorOS. Скорость зависит также от сети, FCM и серверов Telegram.",
            () -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                try { startActivity(intent); }
                catch (Exception error) {
                    Toast.makeText(this, "Откройте системные настройки уведомлений FluidGram", Toast.LENGTH_LONG).show();
                }
            });
        card("Фоновая работа ColorOS", batteryExempt ? "Исключение из оптимизации включено" : "Оптимизация батареи включена",
            "Откройте настройки питания и разрешите FluidGram работу в фоне, если сообщения "
                + "приходят с задержкой. Это увеличит расход батареи. Автоматического обхода ограничений нет.",
            () -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Exception error) {
                    Toast.makeText(this, "Настройки энергосбережения недоступны", Toast.LENGTH_LONG).show();
                }
            });
        card("Проверка push · Android", "Диагностика по запросу",
            "Проверяет разрешение Android 13+, каналы уведомлений и энергосбережение. "
                + "Не отправляет тестовые сообщения и не измеряет задержку серверов Telegram.",
            () -> {
                // Copy exactly the report displayed in this dialog.
                final String reportSnapshot = notificationHealthReport();
                new AlertDialog.Builder(this)
                    .setTitle("FluidGram · диагностика уведомлений")
                    .setMessage(reportSnapshot)
                    .setNeutralButton("Настройки уведомлений", (dialog, which) -> {
                        Intent settings = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                        try { startActivity(settings); }
                        catch (Exception ignored) {
                            Toast.makeText(this, "Откройте настройки уведомлений FluidGram вручную", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Скопировать отчёт", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard == null) {
                            Toast.makeText(this, "Буфер обмена недоступен", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                                "FluidGram notification diagnostics", reportSnapshot));
                        Toast.makeText(this, "Отчёт уведомлений скопирован", Toast.LENGTH_SHORT).show();
                    })
                    .setPositiveButton("Закрыть", null)
                    .show();
            });
        section("ЭНЕРГОЭФФЕКТИВНОСТЬ");
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        int charge = cachedBatteryPercent >= 0 ? cachedBatteryPercent
                : FluidRefresh.batteryPercent(this);
        boolean hot = FluidRefresh.isThermallyConstrained(this);
        card("Энергетический профиль", charge + "% · " +
                (saver ? "энергосбережение Android" : "обычный режим")
                + (hot ? " · ТЕРМОЗАЩИТА" : ""),
            "При ≤20%, системном энергосбережении или сильном нагреве Android (severe+) отключаются запросы повышенной частоты и размытие Glass Labs. Возврат автоматический; экономия требует измерений.", null);
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
        section("VLESS · ПОДГОТОВКА");
        card("Проверить VLESS-ссылку", vlessSummary,
            "Проверка формата vless://, UUID, TLS/REALITY и транспорта. " +
            "Подключение и импорт ключей пока НЕ реализованы; ссылка не сохраняется.",
            () -> {
                final android.widget.EditText field = new android.widget.EditText(this);
                field.setSingleLine(true);
                field.setHint("vless://UUID@server:443?security=reality...");
                new AlertDialog.Builder(this).setTitle("Проверка VLESS")
                    .setView(field)
                    .setPositiveButton("Проверить", (dialog, which) -> {
                        try {
                            FluidVlessProfile.Info info = FluidVlessProfile.inspect(field.getText().toString().trim());
                            vlessSummary = "Формат: " + info.transport + " / " + info.security +
                                    ". Подключение недоступно до интеграции движка.";
                        } catch (IllegalArgumentException error) {
                            vlessSummary = error.getMessage();
                        } finally {
                            field.getText().clear();
                        }
                        render();
                    }).setNegativeButton("Отмена", null).show();
            });
        section("VPN · WIREGUARD");
        card("Состояние VPN", FluidVpnTunnel.statusLabel(),
            "Встроенный WireGuard (официальный движок). Только трафик FluidGram; другие приложения не маршрутизируются. "
            + "Состояние туннеля не доказывает доступность сервера или отсутствие утечек.", null);
        card("Импорт WireGuard .conf", vpnImportSummary,
            "Один пользовательский профиль, до 64 КиБ. Ключи хранятся только в оперативной памяти до отключения. "
            + "Никаких встроенных серверов, фоновых проверок или сохранения конфигураций.",
            this::openVpnFile);
        card("Подключить VPN", pendingVpnConfig != null ? "Профиль загружен · требуется согласие Android"
                : "Импортируйте профиль для подключения",
            "Android запросит системное разрешение на VPN. Для работы нужен ваш действующий WireGuard-сервер. "
            + "Подключение выполняется по запросу, без автозапуска.",
            this::requestVpnConnect);
        card("Отключить VPN", "Остановить активный туннель",
            "При отключении VPN-трафик FluidGram возвращается в обычную сеть. "
            + "Системная блокировка без VPN может затронуть другие приложения в режиме «только FluidGram».",
            this::stopVpn);
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
        card("Снимок LTPO через 3 и 6 секунд", "Сохранённое измерение без касаний",
            "Снимок относится к прежнему экспериментальному режиму. Для проверки ColorOS Auto используйте системный счётчик. "
            + "Снимки сохраняются до следующего завершённого измерения. "
            + "Частоты Android могут отличаться от счётчика LTPO ColorOS.",
            () -> new AlertDialog.Builder(this)
                .setTitle("LTPO · измерение покоя")
                .setMessage(FluidRefresh.idleReport())
                .setPositiveButton("Скопировать", (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(
                            "FluidGram LTPO idle report", FluidRefresh.idleReport()));
                })
                .setNegativeButton("Закрыть", null).show());
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
    /** One-shot, accessible ColorOS-style navigation; no polling or animation loop. */
    private View quickNavigation() {
        HorizontalScrollView rail = new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setFillViewport(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(2), dp(2), dp(2), dp(2));
        String[][] destinations = {
            {"Экран", "ЭКРАН  /  ADAPTIVE REFRESH"},
            {"Ghost Lab", "ПРИВАТНОСТЬ  /  GHOST LAB"},
            {"Уведомления", "УВЕДОМЛЕНИЯ  /  TELEGRAM"},
            {"Энергия", "ЭНЕРГОЭФФЕКТИВНОСТЬ"},
            {"VPN", "VPN · WIREGUARD"},
            {"Диагностика", "ДИАГНОСТИКА  /  COLOROS"}
        };
        for (String[] destination : destinations) {
            TextView chip = text(destination[0], 14, ACCENT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            chip.setPadding(dp(17), dp(10), dp(17), dp(10));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(0xB0274558);
            pill.setCornerRadius(dp(24));
            pill.setStroke(dp(1), 0x7795C9D9);
            chip.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x44FFFFFF), pill, null));
            chip.setFocusable(true);
            chip.setContentDescription("Перейти к разделу " + destination[0]);
            chip.setOnClickListener(v -> jumpToSection(destination[1]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.rightMargin = dp(9);
            chips.addView(chip, params);
        }
        rail.addView(chips);
        return rail;
    }

    private void jumpToSection(String title) {
        View anchor = sectionAnchors.get(title);
        ScrollView current = labsScroll;
        if (anchor == null || current == null) return;
        current.post(() -> {
            // Ignore a queued click if render() has already replaced the layout.
            if (current != labsScroll || anchor.getParent() != root) return;
            int targetY = Math.max(0, anchor.getTop() - dp(12));
            boolean animationsAllowed = android.os.Build.VERSION.SDK_INT < 26
                    || android.animation.ValueAnimator.areAnimatorsEnabled();
            if (preferences.getBoolean("reduce_motion_labs", false) || !animationsAllowed) {
                current.scrollTo(0, targetY);
            } else {
                current.smoothScrollTo(0, targetY);
            }
        });
    }

    private void section(String title) {
        TextView t = text(title, 12, ACCENT, true);
        t.setPadding(dp(4), dp(6), dp(4), dp(11));
        root.addView(t);
        sectionAnchors.put(title, t);
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
            if ("neon_labs".equals(key) || "reduce_glass_transparency".equals(key)) render();
            if ("experimental_glass_bubbles".equals(key)) {
                Toast.makeText(this, "Перезапустите FluidGram для применения стекла в чатах",
                        Toast.LENGTH_LONG).show();
            }
            FluidRefresh.refresh(this);
        });
        panel.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        root.addView(panel, margins(10));
    }
}
