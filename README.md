# Shift Cyber

> Android-приложение для планирования сменного графика, учёта рабочего времени (почасовых и дневных смен с доп. выработкой), расчёта заработка и синхронизации через сервисы Google. Интерфейс — русский, валюта — рубли (₽).

## 1. Назначение и статус реализации (v1.3.0 Release)

**Shift Cyber** помогает вести сменный график (включая циклы вида «4 рабочих / 2 выходных»), вручную создавать и редактировать **почасовые** и **дневные** смены, добавлять сдельную доп. оплату к дневным сменам, рассчитывать доход в рублях, синхронизировать расписание через **сервисы Google**, получать напоминания и отслеживать смену через два виджета рабочего стола Android.

### Сводка реализованных возможностей (v1.3.0)

| Подсистема | Статус | Реализация в коде |
|---|---|---|
| Два формата смен: **Почасовая** (`часы × ставка`) и **Дневная смена** (`1 день = фикс. сумма за день`) | Реализовано | `data/ShiftEntity.kt` (`ShiftPayType`), `domain/ShiftMath.kt`, `ui/Dialogs.kt`, `ui/Screens.kt` |
| Блок доп. заработка дневной смены («Комментарий → сколько сделано → сумма за 1 действие») с суммированием в итог смены | Реализовано | `data/ShiftEntity.kt` (`ShiftExtraItem`), `domain/ShiftJsonCodec.kt`, `domain/ShiftMath.kt`, `ui/Dialogs.kt` |
| Синхронизация через сервисы Google (Google Sign-In + Google Drive `appDataFolder` REST API, прямой экспорт/импорт JSON в Google Диск через SAF и системный Google Auto Backup) | Реализовано | `sync/GoogleDriveSyncManager.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `MainActivity.kt` |
| Два виджета рабочего стола (`4×2` и `Мини 2×2`), `@string`-метки, `installLocation="internalOnly"`, `drawable-xhdpi` превью и `setWidgetPreview` (API 35) для каталога лаунчера (включая Xiaomi HyperOS) | Реализовано в коде (требует финальной проверки на физическом устройстве) | `widget/ShiftWidgetProvider.kt`, `widget/ShiftCompactWidgetProvider.kt`, `widget/SystemSyncReceiver.kt`, `widget/WidgetUpdater.kt` |
| Фирменная иконка приложения (Adaptive Icon API 26+ и растровые `mipmap-mdpi..xxxhdpi`) | Реализовано | `res/mipmap-*/ic_launcher*.png`, `res/mipmap-anydpi-v26/ic_launcher*.xml`, `res/values/colors.xml` |
| Календарь смен и генератор циклов «N рабочих / M выходных» (для почасовых и дневных смен) с защитой от дубликатов и защитой ручных смен | Реализовано | `domain/CycleGenerator.kt`, `ui/Dialogs.kt` (`CycleGeneratorDialog`), `ui/Screens.kt` (`CalendarScreen`) |
| Статистика за месяц, выбранный день, произвольный период дат и за всё время (с учётом почасовых, ночных и дневных смен + доп. заработка) | Реализовано | `ui/Screens.kt` (`StatsScreen`), `domain/ShiftMath.kt` |
| Раздел FAQ в «Настройках» по всем рабочим функциям приложения (взамен описания БД, без описания самого раздела настроек) | Реализовано | `ui/Screens.kt` (`FunctionalFaqItems`) |
| Безопасная миграция БД Room `v1 → v2` (`MIGRATION_1_2`) без удаления пользовательских данных | Реализовано | `data/ShiftDatabase.kt` |
| Подписанная Release-сборка (`isDebuggable = false`) и модульные unit-тесты (`testReleaseUnitTest`) | Реализовано | `app/build.gradle.kts`, `.github/workflows/android-apk.yml`, `app/src/test/java/ru/shiftcyber/**` |

---

## 2. Технологии и актуальная конфигурация

- **Платформа:** Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)
- **Application ID / Namespace:** `ru.shiftcyber` (`versionCode = 4`, `versionName = "1.3.0"`)
- **Режим сборки:** Release (`assembleRelease`, `isDebuggable = false`, V1 + V2 APK Signing)
- **Язык и компилятор:** Kotlin `2.0.21`, JDK / JVM target `17`
- **Gradle / AGP / KSP:** Gradle `8.11.1`, Android Gradle Plugin `8.9.2`, KSP `2.0.21-1.0.28`
- **UI:** Jetpack Compose (`androidx.compose:compose-bom:2025.02.00`, Material 3, Material Icons Extended)
- **Хранение данных:** Room `2.6.1` (`shift-cyber.db`, схема `version = 2`, явная миграция `MIGRATION_1_2`)
- **Сервисы Google:** `com.google.android.gms:play-services-auth:21.3.0` + HTTPS Google Drive API v3 (`appDataFolder`) + Android Storage Access Framework + Android Auto Backup
- **Фоновые задачи и уведомления:** WorkManager `2.10.0` (`CoroutineWorker`), `NotificationCompat`
- **Виджеты:** `AppWidgetProvider` (`ShiftWidgetProvider` 4×2 и `ShiftCompactWidgetProvider` 2×2) + `RemoteViews`
- **Тестирование:** JUnit `4.13.2` (`testReleaseUnitTest`)
- **CI/CD:** GitHub Actions (`.github/workflows/android-apk.yml`) → подписанный артефакт **`ShiftCyber-release-apk`**

---

## 3. Архитектура и структура исходников

```text
app/src/
├── main/
│   ├── AndroidManifest.xml
│   ├── java/ru/shiftcyber/
│   │   ├── MainActivity.kt                  # Точка входа, координация БД, Google Sync, SAF, уведомлений и виджетов
│   │   ├── data/
│   │   │   ├── AppSettings.kt               # Таблица app_settings (почасовая/дневная ставки, тема, Google Sync)
│   │   │   ├── SettingsDao.kt               # DAO настроек
│   │   │   ├── ShiftDatabase.kt             # Room Database (shift-cyber.db, v2 + MIGRATION_1_2)
│   │   │   ├── ShiftDao.kt                  # DAO смен
│   │   │   └── ShiftEntity.kt               # Таблица shifts + ShiftPayType (HOURLY/DAILY) + ShiftExtraItem
│   │   ├── domain/
│   │   │   ├── CycleGenerator.kt            # Генератор цикла N/M для почасовых и дневных смен
│   │   │   ├── ShiftJsonCodec.kt            # JSON-сериализация доп. действий и полного снимка синхронизации
│   │   │   └── ShiftMath.kt                 # Расчёт часов, дневных ставок, доп. выработки и статистики за период
│   │   ├── notifications/
│   │   │   ├── ReminderScheduler.kt         # Планирование/отмена уникальных задач WorkManager
│   │   │   └── ShiftReminderWorker.kt       # Показ уведомлений за 24 ч и за 1 ч
│   │   ├── sync/
│   │   │   └── GoogleDriveSyncManager.kt    # Google Sign-In, Drive REST API (appDataFolder), SAF экспорт/импорт, Auto Backup
│   │   ├── ui/
│   │   │   ├── Dialogs.kt                   # Диалог смены (Почасовая / Дневная + доп. заработок) и генератора цикла
│   │   │   ├── Screens.kt                   # Главная, Календарь, Статистика, Настройки (ставки, Google Sync, FAQ)
│   │   │   └── ShiftCyberTheme.kt           # Светлая и тёмная темы Cyberpunk Neon / VKUI
│   │   └── widget/
│   │       ├── ShiftWidgetProvider.kt       # Основной виджет 4×2
│   │       ├── ShiftCompactWidgetProvider.kt# Компактный виджет Мини 2×2
│   │       ├── SystemSyncReceiver.kt        # Системный ресивер BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET
│   │       └── WidgetUpdater.kt             # Обновление обоих виджетов из Room БД и публикация превью API 35
│   └── res/
│       ├── drawable/                        # Фоны виджетов (solid, translucent, glass)
│       ├── drawable-xhdpi/                  # Превью виджетов для каталога лаунчера (480×280 и 260×260)
│       ├── layout/                          # shift_widget.xml (4×2) и shift_widget_compact.xml (2×2)
│       ├── mipmap-anydpi-v26/               # Adaptive иконки ic_launcher и ic_launcher_round
│       ├── mipmap-mdpi..xxxhdpi/            # Растровые иконки приложения для всех плотностей экрана
│       ├── values/                          # colors.xml, strings.xml, styles.xml
│       └── xml/                             # shift_widget_info.xml, shift_widget_compact_info.xml, backup_rules.xml
└── test/java/ru/shiftcyber/
    ├── domain/
    │   ├── CycleGeneratorTest.kt
    │   ├── ShiftJsonCodecTest.kt
    │   └── ShiftMathTest.kt
    └── widget/
        └── WidgetStateTest.kt
```

---

## 4. Ключевые решения в v1.3.0

1. **Каталог виджетов (включая Xiaomi HyperOS):**
   - Добавлены `<application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round">` и `android:installLocation="internalOnly"` (без них часть лаунчеров Android скрывает приложение из каталога виджетов).
   - Системные события `BOOT_COMPLETED` / `TIME_SET` вынесены из `<receiver>` виджета в отдельный `SystemSyncReceiver`, а названия и описания переведены на строковые ресурсы `@string/...`.
   - Снижены минимальные размеры (`180×96 dp` и `110×96 dp`) под экраны с крупным масштабом сетки и добавлен второй виджет **`Shift Cyber · Мини 2×2`**, благодаря чему на Xiaomi HyperOS приложение отображается сразу в основном списке **«Приложения с несколькими виджетами»**.
2. **Почасовые и дневные смены + доп. заработок:**
   - При создании/редактировании смены и в генераторе цикла доступен переключатель **«Почасовая»** / **«Дневная смена»**.
   - Для дневной смены `1 день = фиксированная ставка за день + сумма всех позиций доп. заработка`, где каждая позиция содержит **комментарий**, **количество сделанных действий** и **сумму за 1 действие**.
3. **Синхронизация через Google:**
   - Поддержаны три совместимых механизма: системный **Google Auto Backup**, облачная синхронизация через **Google Sign-In + Google Drive `appDataFolder`** и прямое сохранение/восстановление JSON-файла в приложении **Google Диск** через системный диалог Android (работает сразу без настройки OAuth Client ID в Google Cloud).
4. **Сохранность данных при обновлении:**
   - Схема Room обновлена до `version = 2` через явную `MIGRATION_1_2` (`ALTER TABLE ... ADD COLUMN ... DEFAULT ...`) без потери ранее сохранённых смен и настроек.

---

## 5. Сборка и установка Release APK

### Через GitHub Actions

Workflow `.github/workflows/android-apk.yml` запускает модульные тесты Release-варианта (`testReleaseUnitTest`) и собирает подписанный Release APK (`assembleRelease`):

1. Открой вкладку **Actions** в репозитории `iZvex/ShiftCyber` и выбери workflow **Build Shift Cyber APK**.
2. Открой успешный запуск и скачай артефакт **`ShiftCyber-release-apk`**.
3. Распакуй архив и установи `app-release.apk`.

> **Важно при переходе с Debug на Release:** в Android отладочный APK (`app-debug.apk`, подписанный тестовым debug-ключом) и релизный APK (`app-release.apk`, подписанный release-ключом) имеют разные цифровые подписи. При самом первом переходе с Debug-сборки на Release-сборку Android попросит удалить старую отладочную копию (предварительно можно сохранить график в Google Диск), а все последующие обновления `ShiftCyber-release-apk` будут устанавливаться поверх друг друга с сохранением базы данных.
