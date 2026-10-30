# FluidGram — инструкция сборки APK (v0.2)

Это экспериментальный клиент. Код успешно проверяется, но устанавливаемый APK ещё не получен.

1. Создайте отдельный публичный GitHub-репозиторий FluidGram и перенесите из ветки разработчика папку fluidgram/ и .github/workflows/fluidgram-apk.yml. Обновите имя ветки в workflow.
2. Получите собственные api_id и api_hash на https://my.telegram.org.
3. В Firebase Console добавьте Android-приложение dev.fluidgram.oneplus15.web и скачайте google-services.json.
4. Создайте долговременный ключ подписи в JDK: keytool -genkeypair -keystore fluidgram-release.jks -alias fluidgram -keyalg RSA -keysize 3072 -validity 10000.
5. GitHub Settings → Secrets and variables → Actions: установите FLUIDGRAM_API_ID, FLUIDGRAM_API_HASH, FLUIDGRAM_GOOGLE_SERVICES_JSON_B64, FLUIDGRAM_KEYSTORE_B64, FLUIDGRAM_KEY_ALIAS, FLUIDGRAM_STORE_PASSWORD и FLUIDGRAM_KEY_PASSWORD.
6. PowerShell для Base64: [Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\\path\\google-services.json')). Аналогично для JKS. НЕ отправляйте значения в чат и НЕ добавляйте в git.
7. Actions → FluidGram Android APK (OnePlus 15) → Run workflow. После успешной сборки скачать FluidGram-OnePlus15-alpha2 и проверить APK-SHA256.

Без этих секретов сборка пропускается. Тесты не доказывают корректную работу камеры на OnePlus 15. Нужна проверка на телефоне. Исходники Telegram: https://github.com/DrKLO/Telegram (GPL-2.0-or-later).
