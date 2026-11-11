# Shift Cyber

> Android-приложение для планирования сменного графика, учёта рабочего времени и расчёта заработка. Интерфейс — русский, валюта — рубли (₽).

## 1. Назначение и статус реализации

**Shift Cyber** помогает вести сменный график (включая циклы вида «4 рабочих / 2 выходных»), вручную создавать и редактировать смены, учитывать фактически отработанные часы (включая ночные смены через полночь и на границе месяцев), рассчитывать доход в рублях, получать напоминания и отслеживать расписание через виджет рабочего стола Android.

### Сводка реализованных возможностей (v1.2.0)

| Подсистема | Статус | Реализация в коде |
|---|---|---|
| Календарь смен (выбор дня, переключение месяцев, переход к «Сегодня», итоги дня и месяца) | Реализовано | `ui/Screens.kt` (`CalendarScreen`), `domain/ShiftMath.kt` |
| Генератор циклов «N рабочих / M выходных» без ручных строковых шаблонов | Реализовано | `domain/CycleGenerator.kt` (`generateWorkRestCycle`), `ui/Dialogs.kt` (`CycleGeneratorDialog`) |
| Защита от дубликатов при повторной генерации и защита ручных смен (`isManual = true`) без подтверждения | Реализовано | `domain/CycleGenerator.kt` (`resolveConflicts`, `ManualConflictPolicy`), `MainActivity.kt` (`db.withTransaction`) |
| Ручное добавление, редактирование, удаление смен, выбор цветового акцента | Реализовано | `ui/Dialogs.kt` (`ShiftEditorDialog`), `ui/Screens.kt` (`ShiftRow`), `data/ShiftDao.kt` |
| Быстрая корректировка длительности `−1 ч` / `+1 ч` с защитой от нулевой/отрицательной длительности | Реализовано | `domain/ShiftMath.kt` (`canAdjustHours`, `adjustDurationHours`), `ui/Screens.kt` |
| Учёт времени и заработка в `₽` без вычета перерывов и без промежуточного округления | Реализовано | `domain/ShiftMath.kt` (`hours`, `earnings`, `formatCurrency`, `formatHours`) |
| Корректный расчёт ночных смен через полночь и на границе суток/месяца/года | Реализовано | `domain/ShiftMath.kt` (`overlapMillis`, `calculatePeriodStats`, `isCrossMidnight`, `timeRangeLabel`) |
| Статистика за месяц, выбранный день, произвольный период дат и за всё время | Реализовано | `ui/Screens.kt` (`StatsScreen`), `domain/ShiftMath.kt` |
| Настройка почасовой ставки по умолчанию (с возможностью применить к будущим сменам) | Реализовано | `ui/Screens.kt` (`SettingsScreen`), `data/AppSettings.kt`, `data/SettingsDao.kt` |
| Уведомления за 24 ч и за 1 ч до смены, настройка переключателей, отмена при удалении/редактировании | Реализовано | `notifications/ReminderScheduler.kt`, `notifications/ShiftReminderWorker.kt` |
| Светлая и тёмная темы (Cyberpunk Neon / VKUI) с контрастной палитрой и сохранением между запусками | Реализовано | `ui/ShiftCyberTheme.kt`, `data/AppSettings.kt` |
| Виджет рабочего стола Android (`RemoteViews` без несовместимых `<View>`, обновление из Room БД, `BOOT_COMPLETED`, адаптация к размеру, открытие по нажатию, закрепление на HyperOS) | Реализовано в коде (требует финальной проверки на физическом устройстве) | `widget/ShiftWidgetProvider.kt`, `widget/WidgetUpdater.kt`, `res/layout/shift_widget.xml`, `res/xml/shift_widget_info.xml` |
| Модульные unit-тесты доменной логики и состояния виджета | Реализовано | `app/src/test/java/ru/shiftcyber/domain/*`, `app/src/test/java/ru/shiftcyber/widget/*` |

---

## 2. Технологии и актуальная конфигурация

Параметры сверены с текущими Gradle-файлами репозитория:

- **Платформа:** Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)
- **Application ID / Namespace:** `ru.shiftcyber` (`versionCode = 3`, `versionName = "1.2.0"`)
- **Язык и компилятор:** Kotlin `2.0.21`, JDK / JVM target `17`
- **Gradle / AGP / KSP:** Gradle `8.11.1`, Android Gradle Plugin `8.9.2`, KSP `2.0.21-1.0.28`
- **UI:** Jetpack Compose (`androidx.compose:compose-bom:2025.02.00`, Material 3, Material Icons Extended)
- **Хранение данных:** Room `2.6.1` (`shift-cyber.db`, схема `version = 1`), Kotlin Coroutines & `Flow` (`collectAsState`)
- **Фоновые задачи и уведомления:** WorkManager `2.10.0` (`CoroutineWorker`), `NotificationCompat`
- **Виджет:** `AppWidgetProvider` + `RemoteViews`
- **Тестирование:** JUnit `4.13.2` (`testDebugUnitTest`)
- **CI/CD:** GitHub Actions (`.github/workflows/android-apk.yml`) -> артефакт `ShiftCyber-debug-apk`

---

## 3. Архитектура и структура исходников

```text
app/src/
├── main/
│   ├── AndroidManifest.xml
│   ├── java/ru/shiftcyber/
│   │   ├── MainActivity.kt                  # Точка входа, координация БД/уведомлений/виджета
│   │   ├── data/
│   │   │   ├── AppSettings.kt               # Таблица app_settings (ставка, тема, уведомления, прозрачность виджета)
│   │   │   ├── SettingsDao.kt               # DAO настроек (Flow + разовое чтение для фоновых задач)
│   │   │   ├── ShiftDatabase.kt             # Room Database (shift-cyber.db, version = 1)
│   │   │   ├── ShiftDao.kt                  # DAO смен (CRUD, выборка за период и пересечения)
│   │   │   └── ShiftEntity.kt               # Таблица shifts (startMillis, endMillis, hourlyRate, isManual и др.)
│   │   ├── domain/
│   │   │   ├── CycleGenerator.kt            # Генератор цикла N/M, защита от дубликатов и перезаписи ручных смен
│   │   │   └── ShiftMath.kt                 # Единый доменный расчёт часов, заработка, границ периодов и форматирования
│   │   ├── notifications/
│   │   │   ├── ReminderScheduler.kt         # Планирование/отмена уникальных задач WorkManager по ID смены
│   │   │   └── ShiftReminderWorker.kt       # Показ уведомлений с проверкой актуальности смены в БД и разрешений
│   │   ├── ui/
│   │   │   ├── Dialogs.kt                   # Диалоги создания/редактирования смены и генератора цикла
│   │   │   ├── Screens.kt                   # Экраны: Главная, Календарь, Статистика, Настройки
│   │   │   └── ShiftCyberTheme.kt           # Цветовая система светлой и тёмной тем, управление системными барами
│   │   └── widget/
│   │       ├── ShiftWidgetProvider.kt       # AppWidgetProvider (APPWIDGET_UPDATE, BOOT_COMPLETED, MY_PACKAGE_REPLACED)
│   │       └── WidgetUpdater.kt             # Формирование состояния виджета из Room БД, кэш SharedPreferences и RemoteViews
│   └── res/
│       ├── drawable/
│       │   ├── widget_bg_glass.xml
│       │   ├── widget_bg_solid.xml
│       │   └── widget_bg_translucent.xml
│       ├── drawable-nodpi/
│       │   └── shift_widget_preview.png     # Оптимизированное превью виджета (480×480)
│       ├── layout/
│       │   └── shift_widget.xml             # Разметка виджета только на @RemoteView-элементах
│       ├── values/
│       │   ├── strings.xml
│       │   └── styles.xml
│       └── xml/
│           └── shift_widget_info.xml        # Метаданные виджета (размеры, previewLayout, targetCellWidth/Height)
└── test/java/ru/shiftcyber/
    ├── domain/
    │   ├── CycleGeneratorTest.kt
    │   └── ShiftMathTest.kt
    └── widget/
        └── WidgetStateTest.kt
```

---

## 4. Ключевые доменные правила и сохранность данных

1. **Совместимость базы данных (Room v1):**
   - Сохранены `applicationId = "ru.shiftcyber"`, имя БД `shift-cyber.db` и `version = 1` без destructive migrations.
   - Поле `ShiftEntity.breakMinutes` оставлено в схеме таблицы `shifts` для полной совместимости при установке новой версии APK поверх ранее установленной, но **не вычитается** ни из часов, ни из заработка.
2. **Учёт ночных смен и границ периодов:**
   - Смена, в которой время окончания меньше времени начала (например, `20:00` → `08:00`), автоматически сохраняется с окончанием на следующие сутки (`+1 дн.`).
   - В статистике за день, месяц и выбранный диапазон дат длительность и заработок рассчитываются по точному пересечению интервала смены с полуинтервалом `[fromMillis, toMillis)` без промежуточного округления.
3. **Генератор циклического графика (`CycleGenerator`):**
   - Поддерживает любой цикл «N рабочих / M выходных» (по умолчанию `4/2`), начиная строго с выбранной даты начала на заданное число дней вперёд.
   - Повторный запуск с теми же параметрами не создаёт дубликатов.
   - Ручные смены (`isManual = true`, включая смены, скорректированные кнопками `±1 ч` или через диалог редактирования) по умолчанию пропускаются и не перезаписываются; перезапись возможна только при включении соответствующего флага и повторном подтверждении в диалоге.
   - Запись изменений в БД выполняется атомарно внутри `db.withTransaction`.
4. **Виджет рабочего стола:**
   - Причиной системной ошибки «Не удалось загрузить виджет» в промежуточной версии было использование тега `<View>` в `shift_widget.xml` (`android.view.View` не помечен аннотацией `@RemoteView` в Android SDK и вызывает `InflateException` в `RemoteViews`). Разделитель заменён на `@RemoteView`-совместимый `<FrameLayout>`, а превью уменьшено до `480×480` для стабильной работы каталога виджетов HyperOS / Android 14+.
   - Виджет обновляется при запуске приложения, любых изменениях смен/настроек, по таймеру системы, после перезагрузки устройства (`BOOT_COMPLETED`) и после обновления APK (`MY_PACKAGE_REPLACED`), а по нажатию открывает приложение.

---

## 5. Сборка, тестирование и установка APK

### Через GitHub Actions (основной способ, без ПК)

Workflow `.github/workflows/android-apk.yml` автоматически запускает модульные тесты (`gradle --no-daemon testDebugUnitTest`) и собирает отладочный APK (`gradle --no-daemon assembleDebug`).

1. Открой вкладку **Actions** в репозитории `iZvex/ShiftCyber` и выбери workflow **Build Shift Cyber APK**.
2. Дождись зелёного статуса выполнения, открой завершённый запуск и скачай артефакт **`ShiftCyber-debug-apk`** (хранится 14 дней).
3. Распакуй архив и установи `app-debug.apk` **поверх текущей версии приложения**, не удаляя старую — существующие смены и настройки сохранятся.

### Локальная сборка и тесты (опционально)

Требуются JDK 17, Android SDK Platform 35 и Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleDebug
```

Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

### Диагностика виджета через `adb` (при необходимости на устройстве)

```bash
adb logcat -v time | grep -iE "AppWidget|RemoteViews|ShiftWidget|ru.shiftcyber"
```
