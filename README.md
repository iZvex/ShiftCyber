# Shift Cyber

> Android-приложение для планирования сменного графика, учёта рабочего времени (почасовых и дневных смен с доп. выработкой), расчёта заработка и синхронизации через сервисы Google. Интерфейс — русский, валюта — рубли (₽).

## 1. Назначение и статус реализации (v1.4.0 Release)

**Shift Cyber** помогает вести сменный график (включая циклы вида «4 рабочих / 2 выходных»), вручную создавать и редактировать **почасовые** и **дневные** смены, добавлять сдельную доп. оплату к дневным сменам, рассчитывать доход в рублях, синхронизировать расписание через **сервисы Google**, получать напоминания и отслеживать смену через два виджета рабочего стола Android.

В версии **v1.4.0** добавлена вкладка **«Поддержка»** с персонализированной баннерной рекламой **Appodeal** (Яндекс РСЯ, VK Реклама / `my_target`, Start.io), согласием по **152-ФЗ** и разовой поддержкой разработчика (Илья, самозанятый / НПД) через **ЮKassa**, навсегда отключающей всю рекламу в приложении. Состояние монетизации (`user_data.json`, `consent.json`, `payment_state.json`) хранится строго в JSON-файлах через `Gson` с атомарной записью на `Dispatchers.IO`.

### Сводка реализованных возможностей (v1.4.0)

| Подсистема | Статус | Реализация в коде |
|---|---|---|
| Монетизация **Appodeal SDK 3.5.0.+** (Яндекс РСЯ, VK Реклама, Start.io) + кастомные таргетинг-атрибуты (`setExtraData`), согласие **152-ФЗ** и разовая поддержка через **ЮKassa** с вечным отключением рекламы | Реализовано | `monetization/{UserData,DataManager,DataCollector,ConsentManager,YooKassaManager,AppodealIntegration,AdDisplayManager,AdController}.kt`, `ui/SupportScreen.kt` |
| Два формата смен: **Почасовая** (`часы × ставка`) и **Дневная смена** (`1 день = фикс. сумма за день`) | Реализовано | `data/ShiftEntity.kt` (`ShiftPayType`), `domain/ShiftMath.kt`, `ui/Dialogs.kt`, `ui/Screens.kt` |
| Блок доп. заработка дневной смены («Комментарий → сколько сделано → сумма за 1 действие») с суммированием в итог смены | Реализовано | `data/ShiftEntity.kt` (`ShiftExtraItem`), `domain/ShiftJsonCodec.kt`, `domain/ShiftMath.kt`, `ui/Dialogs.kt` |
| Синхронизация через сервисы Google (Google Sign-In + Google Drive `appDataFolder` REST API, прямой экспорт/импорт JSON в Google Диск через SAF и системный Google Auto Backup) | Реализовано | `sync/GoogleDriveSyncManager.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `MainActivity.kt` |
| Два виджета рабочего стола (`4×2` и `Мини 2×2`), `@string`-метки, `installLocation="internalOnly"`, `drawable-xhdpi` превью и `setWidgetPreview` (API 35) для каталога лаунчера (включая Xiaomi HyperOS) | Реализовано | `widget/ShiftWidgetProvider.kt`, `widget/ShiftCompactWidgetProvider.kt`, `widget/SystemSyncReceiver.kt`, `widget/WidgetUpdater.kt` |
| Фирменная иконка приложения (Adaptive Icon API 26+ и растровые `mipmap-mdpi..xxxhdpi`) | Реализовано | `res/mipmap-*/ic_launcher*.png`, `res/mipmap-anydpi-v26/ic_launcher*.xml`, `res/values/colors.xml` |
| Календарь смен и генератор циклов «N рабочих / M выходных» (для почасовых и дневных смен) с защитой от дубликатов и защитой ручных смен | Реализовано | `domain/CycleGenerator.kt`, `ui/Dialogs.kt` (`CycleGeneratorDialog`), `ui/Screens.kt` (`CalendarScreen`) |
| Статистика за месяц, выбранный день, произвольный период дат и за всё время (с учётом почасовых, ночных и дневных смен + доп. заработка) | Реализовано | `ui/Screens.kt` (`StatsScreen`), `domain/ShiftMath.kt` |
| Раздел FAQ в «Настройках» по всем рабочим функциям приложения (включая «Поддержку», без описания самого раздела настроек) | Реализовано | `ui/Screens.kt` (`FunctionalFaqItems`) |
| Безопасная миграция БД Room `v1 → v2` (`MIGRATION_1_2`) без удаления пользовательских данных | Реализовано | `data/ShiftDatabase.kt` |
| Подписанная Release-сборка (`isDebuggable = false`) и модульные unit-тесты (`testReleaseUnitTest`) | Реализовано | `app/build.gradle.kts`, `.github/workflows/android-apk.yml`, `app/src/test/java/ru/shiftcyber/**` |

---

## 2. Технологии и актуальная конфигурация

- **Платформа:** Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)
- **Application ID / Namespace:** `ru.shiftcyber` (`versionCode = 5`, `versionName = "1.4.0"`)
- **Режим сборки:** Release (`assembleRelease`, `isDebuggable = false`, V1 + V2 APK Signing)
- **Язык и компилятор:** Kotlin `2.0.21`, JDK / JVM target `17`
- **Gradle / AGP / KSP:** Gradle `8.11.1`, Android Gradle Plugin `8.9.2`, KSP `2.0.21-1.0.28`
- **UI:** Jetpack Compose (`androidx.compose:compose-bom:2025.02.00`, Material 3, Material Icons Extended) + Cyberpunk Neon UI
- **Хранение смен:** Room `2.6.1` (`shift-cyber.db`, схема `version = 2`, `MIGRATION_1_2`)
- **Хранение данных монетизации, согласия и оплаты (строго JSON):** `com.google.code.gson:gson:2.10.1` + атомарная запись temp → rename на `Dispatchers.IO` (`files/user_data.json`, `files/consent.json`, `files/payment_state.json`)
- **Рекламная медиация (РФ):** `com.appodeal.ads:sdk:3.5.0.+` + адаптеры `yandex:3.5.0.+` (РСЯ), `my_target:3.5.0.+` (VK Реклама) и `com.startapp:inapp-sdk:5.0.2` (Start.io)
- **Тестирование:** JUnit `4.13.2` (`testReleaseUnitTest`)
- **CI/CD:** GitHub Actions (`.github/workflows/android-apk.yml`) → подписанный артефакт **`ShiftCyber-release-apk`**

---

## 3. Архитектура подсистемы монетизации и поддержки (`ru.shiftcyber.monetization`)

```text
app/src/main/java/ru/shiftcyber/monetization/
├── UserData.kt             # Модель UserData + ConsentState + PaymentState + AdBannerFormat
├── DataManager.kt          # Gson JSON I/O в context.filesDir (user_data.json, consent.json, payment_state.json) строго на Dispatchers.IO с атомарной записью
├── DataCollector.kt        # Сбор поведенческих/технических параметров сессии, типа экрана и текущей вкладки
├── ConsentManager.kt       # Согласие на обработку ПДн по 152-ФЗ (consent.json + setTrackingConsent)
├── YooKassaManager.kt      # Поддержка разработчика 50/100/500 ₽ через WebView ЮKassa, проверка статуса и вечное отключение рекламы (payment_state.json)
├── AppodealIntegration.kt  # Инициализация Appodeal SDK (Smart Banner / MREC) и передача setExtraData / setCustomFilter
├── AdDisplayManager.kt     # Безопасный показ/скрытие баннера во внутреннем FrameLayout (View.GONE при ошибке или оплате, соответствие RuStore)
└── AdController.kt         # Главный координатор: проверяет isAdsDisabled() ПЕРЕД любым Appodeal.show() / инициализацией
```

---

## 4. Настройка ключей Appodeal и ЮKassa (для самозанятого, НПД)

Ключи передаются через `gradle.properties` или переменные окружения / GitHub Actions Secrets (без хардкода в репозитории):

```properties
APPODEAL_APP_KEY=ваш_ключ_из_кабинета_Appodeal
YOOKASSA_PAYMENT_URL=https://yookassa.ru/my/i/ваша_платежная_ссылка/l
YOOKASSA_SHOP_ID=        # опционально (если подключен API-магазин ЮKassa)
YOOKASSA_SECRET_KEY=     # опционально (секретный ключ API ЮKassa)
```

### 4.1. Как получить `APP_KEY` в Appodeal и подключить сети РФ
1. Зарегистрируйтесь в кабинете **Appodeal** (`https://app.appodeal.com`) — для разработчиков из РФ доступны выплаты и работа со статусом самозанятого / физлица.
2. Перейдите в **Apps → Add App**, выберите платформу **Android**, укажите пакет `ru.shiftcyber` и название **Shift Cyber** (при публикации в RuStore выберите магазин RuStore / APK).
3. Скопируйте выданный **App Key** (48-символьная строка) и пропишите его в `APPODEAL_APP_KEY` (в `gradle.properties` или в `Settings → Secrets and variables → Actions` репозитория GitHub).
4. В разделе **Mediation Settings (Line Items)** включите российские сети **Yandex Ads (РСЯ)**, **MyTarget / VK Реклама** и **Start.io**, добавив блоки для форматов **Banner** и **MREC (300×250)**. Пока `APPODEAL_APP_KEY` не задан, приложение безопасно работает в тестовом режиме Appodeal (`setTesting(true)`).

### 4.2. Как создать Payment Link в ЮKassa для самозанятого (НПД)
1. Откройте `https://yookassa.ru`, нажмите **Подключить ЮKassa** и выберите статус **Самозанятый (плательщик НПД)** — паспорт + ИНН, привязка к приложению **«Мой налог»** для автоматического формирования чеков при каждом платеже.
2. После активации кабинета откройте раздел **Выставление счетов / Платёжные ссылки (Умная ссылка)** → **Создать ссылку**:
   - Назначение платежа: `Поддержка разработчика приложения Shift Cyber (без НДС)`.
   - Сумма: свободная или фиксированная (приложение передаёт выбранный номинал `50 / 100 / 500 ₽`).
   - В настройках перенаправления после успешной оплаты (**Return URL / URL успешной оплаты**) укажите deep-link приложения:
     `shiftcyber://payment-success`
3. Скопируйте готовую ссылку вида `https://yookassa.ru/my/i/...` в параметр `YOOKASSA_PAYMENT_URL` (в `gradle.properties` или GitHub Secrets). После успешной оплаты ЮKassa перенаправит пользователя на `shiftcyber://payment-success`, `YooKassaManager` запишет `"adsDisabled": true` в `payment_state.json`, и вся реклама в приложении отключится навсегда.

---

## 5. Сборка и установка Release APK

Workflow `.github/workflows/android-apk.yml` запускает модульные тесты Release-варианта (`testReleaseUnitTest`, включая `MonetizationArchitectureTest`) и собирает подписанный Release APK (`assembleRelease` + `lintVitalRelease`):

1. Открой вкладку **Actions** в репозитории `iZvex/ShiftCyber` и выбери workflow **Build Shift Cyber APK**.
2. Открой успешный запуск и скачай артефакт **`ShiftCyber-release-apk`**.
3. Распакуй архив и установи `app-release.apk`.
