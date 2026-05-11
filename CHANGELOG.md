# CHANGELOG

## Версия 1.2.0-hud — 11.05.2026

**Новый модуль `EclipsiaHUD` — серверный UI без ресурс-пака.**

### Новое
- ✅ **`EclipsiaHUD/`** — 8-й активный модуль, чистый аддитив. Не трогает существующий ActionBar HUD (EclipsiaSkills) и XP-bossbar (EclipsiaItems).
- ✅ Public API [`ru.eclipsia.hud.api.EclipsiaHUDAPI`](EclipsiaHUD/src/main/java/ru/eclipsia/hud/api/EclipsiaHUDAPI.java) для других модулей: `showLevelUp/showBossSpawn/showRegionEnter/showWelcome`, `setSidebarVisible`, `showBossBar/hideBossBar`, `spawnLabel`, `showDamage`. Доступ через `EclipsiaHUDAPI.getInstance()` — возвращает `null` при отсутствии HUD-плагина (soft dependency).
- ✅ **Sidebar** ([SidebarService](EclipsiaHUD/src/main/java/ru/eclipsia/hud/sidebar/SidebarService.java)) — per-player scoreboard 15 строк, hex-цвета, обновление раз в секунду, toggle `/hud sidebar`.
- ✅ **TabList** ([TabListService](EclipsiaHUD/src/main/java/ru/eclipsia/hud/tablist/TabListService.java)) — header/footer с плейсхолдерами `<player>`/`<player_count>`/`<level>`/`<class>`/`<world>`.
- ✅ **BossBar registry** ([BossBarRegistry](EclipsiaHUD/src/main/java/ru/eclipsia/hud/bossbar/BossBarRegistry.java)) — per-player реестр баров по ключу. НЕ конкурирует с XP-bar из EclipsiaItems.
- ✅ **Title cinematic** ([TitleCinematicService](EclipsiaHUD/src/main/java/ru/eclipsia/hud/title/TitleCinematicService.java)) — welcome / level-up / boss-spawn / region-enter через Adventure `showTitle()` (вместо deprecated `sendTitle()`).
- ✅ **Region registry** ([RegionRegistry](EclipsiaHUD/src/main/java/ru/eclipsia/hud/region/RegionRegistry.java) + [RegionEnterListener](EclipsiaHUD/src/main/java/ru/eclipsia/hud/region/RegionEnterListener.java)) — конфиг-зоны (`world` + bbox) с шаблоном MiniMessage в `name`. **Заменяет сломанный `EclipsiaCore/listener/RegionTitleListener`**, который хардкодил миры `world`/`beach` (удалены в v1.1.0-bootstrap).
- ✅ **Floating labels** ([FloatingLabelService](EclipsiaHUD/src/main/java/ru/eclipsia/hud/floatlabel/FloatingLabelService.java)) — TextDisplay (1.19.4+), billboard, прозрачный фон, scale, TTL. Заменяет ArmorStand-метки.
- ✅ **Damage numbers — modern** ([ModernDamageDisplay](EclipsiaHUD/src/main/java/ru/eclipsia/hud/damage/ModernDamageDisplay.java)) — TextDisplay-цифры, цвет по `DamageType`, крит = жирный + увеличенный масштаб + восклицательный знак. Старый `DamageDisplay` (ArmorStand) остаётся, переключение через `damage-numbers.mode` (`legacy` / `modern` / `both`).
- ✅ **Theme** ([Theme](EclipsiaHUD/src/main/java/ru/eclipsia/hud/theme/Theme.java)) — единая палитра: цвета классов (warrior/archer/mage), рарностей (PoE-style), типов урона; обёртка над MiniMessage.
- ✅ Конфиг [`config.yml`](EclipsiaHUD/src/main/resources/config.yml) — все блоки опциональны, шаблоны фраз и тайминги без перекомпиляции.
- ✅ Команда `/hud sidebar | tablist | reload | test <title|welcome|boss|region|damage|label> | labels clear` (alias `/eui`).

### Что НЕ изменилось
- Логика существующих плагинов не тронута. `EclipsiaCore/listener/RegionTitleListener.java` физически остался в репо как «мёртвый код» (worlds `world`/`beach` отсутствуют → листенер ни на что не отвечает). Удаление — в следующем PR после деплоя и проверки нового RegionEnterListener.
- `EclipsiaMobs/ExperienceManager#levelUp` всё ещё использует deprecated `sendTitle(...)`. Миграцию на `EclipsiaHUDAPI.showLevelUp()` сделаем отдельным PR — это требует разметить вызов через soft-dep (как сейчас сделан `givePerkPoint` через рефлексию).
- `HUDActionBarListener` (EclipsiaSkills) и `PlayerHUDManager` (EclipsiaItems) — без изменений.

### Статистика модуля
- 14 Java-файлов, ~1100 строк кода
- 0 transitive dependencies (только paper-api `provided`)
- Сборка: `mvn -B package -DskipTests` (после `EclipsiaCore` `mvn install`)

---

## Версия 1.1.0-bootstrap — 11.05.2026

**Перезапуск проекта: фокус на ручной билд карты и кастомный UI.**

### Изменения архитектуры
- ⚠️ `EclipsiaBuilder` (процедурный генератор Эликия/Берега/гор/собора) выведен из активной разработки. Исходники сохранены в `archive/EclipsiaBuilder/` как референс. Команды `/build *` больше не работают.
- 🗑️ Удалены сгенерированные миры из git (`TestServer/{world,beach,lobby,world_nether,world_the_end,lobby_nether,lobby_the_end}`) — миры теперь не коммитятся, генерируются на лету.
- 🗑️ Удалены runtime-артефакты из git (`TestServer/logs/`, `usercache.json`, `version_history.json`, `.console_history`, `banned-*.json`, `whitelist.json`).
- ✅ `TestServer` переведён на чистый `dev_flat` мир: плоская тестовая площадка для разработки (`level-name=dev_flat`, `level-type=minecraft:flat`).
- ✅ Добавлен `TestServer/start.sh` (Linux/macOS, Aikar's flags).
- ✅ Обновлён `.gitignore`: миры, кэши, логи и JARы плагинов теперь игнорируются.

### Документация
- ✅ Новый файл [`docs/eclipsia-plan.md`](docs/eclipsia-plan.md) — детальный roadmap нового направления (стек, плагины, UI через custom font, этапы 4-9, CI, апгрейд версии MC).
- ✅ Новый [`TestServer/README.md`](TestServer/README.md) — инструкция запуска dev-сервера.
- ✅ Новый [`archive/README.md`](archive/README.md) — назначение архивной зоны.
- ✅ Обновлён корневой `README.md` — отражение нового направления.

### Что НЕ изменилось
- Логика и API существующих плагинов: `EclipsiaCore`, `EclipsiaItems`, `EclipsiaSkills`, `EclipsiaMobs`, `EclipsiaPerks`, `EclipsiaLobby`, `EclipsiaTests` — без изменений в этом PR.
- Версия Minecraft: остаётся 1.20.4 (апгрейд на 1.21.x — отдельная фаза, см. план §7).
- `netrogat/`, `dist/plugins/`, `texture/` — без изменений.

---

## Версия 1.0.0 — 25.04.2026 (ФИНАЛЬНЫЙ РЕЛИЗ предыдущей итерации)

---

## 🆕 НОВЫЕ ФУНКЦИИ

### EclipsiaCore
- ✅ Добавлена система маны (currentMana, maxMana)
- ✅ Метод `updateProfile()` в API
- ✅ RegionTitleListener - отображение названий зон через title
- ✅ Автомиграция старых профилей с добавлением маны

### EclipsiaSkills
- ✅ **SkillsGUI** - GUI управления навыками (15 слотов)
- ✅ **ManaBarListener** - отображение маны через Boss Bar
- ✅ **ManaRegenerationListener** - регенерация 2% в секунду
- ✅ **SkillData** - сериализация/десериализация навыков
- ✅ Проверка и расход маны при использовании навыков
- ✅ Сохранение навыков в equipmentData профиля
- ✅ Команда `/skills` - открытие GUI
- ✅ Команда `/giveskill` - тестирование навыков
- ✅ Интеграция в MainMenuGUI (кнопка "Навыки")

### EclipsiaMobs
- ✅ **GatekeeperBoss** - Хранитель Врат (первый босс)
  - 3 фазы боя (66% HP, 33% HP)
  - Способности: Удар по земле, Призыв стражей
  - Награды: 500 XP, 100 орбов, предметы
  - Снятие границы Берега после победы
- ✅ **BossManager** - управление боссами
- ✅ **StructureSpawnManager** - зоны спавна в структурах
- ✅ **BossDeathListener** - обработка смерти боссов
- ✅ Команда `/boss` - управление боссами

### EclipsiaBuilder
- ✅ Расширен config.yml - 9 структур с детальными features
- ✅ Автоматическое создание плоских миров через Multiverse-Core
- ✅ WorldGuard border для Берега
- ✅ Интеграция зон спавна мобов
- ✅ Новые типы структур: DUNGEON, PORTAL
- ✅ Обработка features: MOB_SPAWN_ZONE, BOSS_SPAWN

### EclipsiaLobby
- ✅ Исправлена выдача стартовых навыков (отложенная через scheduler)
- ✅ Улучшена интеграция с EclipsiaSkills

### EclipsiaItems
- ✅ Обновлен MainMenuGUI - добавлена кнопка "Навыки"
- ✅ Обновлен MainMenuGUIListener - обработка клика на навыки

---

## 🔧 ИСПРАВЛЕНИЯ

### EclipsiaCore
- Исправлена компиляция PlayerProfile с новыми полями маны
- Добавлены геттеры getCurrentMana() и getMaxMana()
- Добавлены сеттеры в Builder

### EclipsiaSkills
- Исправлена рефлексия в LobbyListener для выдачи навыков
- Убран action bar (заменен на Boss Bar)
- Исправлена зависимость от EclipsiaCore

### EclipsiaItems
- Упрощен EquipmentGUIListener (убрана старая логика эклипсов)

---

## 📊 СТАТИСТИКА

### Добавлено файлов: 12
- GatekeeperBoss.java
- BossManager.java
- BossCommand.java
- BossDeathListener.java
- StructureSpawnManager.java
- SkillsGUI.java
- SkillData.java
- ManaBarListener.java
- ManaRegenerationListener.java
- GiveSkillCommand.java
- SkillsCommand.java
- RegionTitleListener.java

### Изменено файлов: 15
- PlayerProfile.java
- EclipsiaAPI.java
- EclipsiaCore.java
- EclipsiaSkills.java
- EclipsiaMobs.java
- EclipsiaBuilder.java
- EclipsiaLobby.java
- StructureManager.java
- SkillManager.java
- SkillListener.java
- LobbyListener.java
- MainMenuGUI.java
- MainMenuGUIListener.java
- EquipmentGUIListener.java
- config.yml (EclipsiaBuilder)

### Строк кода: ~3500+

---

## 🎯 ВЫПОЛНЕННЫЕ ЗАДАЧИ

### Критичные (High Priority) - 100%
1. ✅ Исправлен метод giveStarterSkill
2. ✅ Создан SkillsGUI с 15 слотами
3. ✅ Добавлена обработка кликов в SkillsGUI
4. ✅ Интегрирован SkillsGUI в MainMenuGUI
5. ✅ Добавлена команда /skills
6. ✅ Добавлена система маны
7. ✅ Добавлена проверка и расход маны
8. ✅ Добавлена регенерация маны
9. ✅ Сохранение/загрузка навыков
10. ✅ Создан Хранитель Врат
11. ✅ Все плагины скомпилированы и установлены

### Средний приоритет (Medium) - 100%
1. ✅ RegionTitleListener
2. ✅ Создание плоских миров
3. ✅ WorldGuard border
4. ✅ Расширение structures.yml
5. ✅ Интеграция спавна мобов
6. ✅ Отображение маны в HUD (Boss Bar)
7. ✅ Команда тестирования навыков

### Низкий приоритет (Low) - Отложено
1. ⏸️ Переделка системы перков (2000×2000)
2. ⏸️ Система квестов
3. ⏸️ NPC система

---

## 🚀 ПРОИЗВОДИТЕЛЬНОСТЬ

- Регенерация маны: каждую секунду (20 тиков)
- Обновление Boss Bar: каждые 0.5 секунды (10 тиков)
- Способности босса: проверка каждую секунду
- Все операции асинхронные где возможно

---

## 📦 РАЗМЕРЫ ПЛАГИНОВ

```
EclipsiaCore.jar       13.0 MB  (+0.5 MB)
EclipsiaItems.jar      13.0 MB  (без изменений)
EclipsiaSkills.jar     25 KB    (+10 KB)
EclipsiaMobs.jar       38 KB    (+15 KB)
EclipsiaBuilder.jar    11 KB    (+3 KB)
EclipsiaLobby.jar      18 KB    (+2 KB)
EclipsiaPerks.jar      35 KB    (без изменений)
```

---

## 🔮 ПЛАНЫ НА БУДУЩЕЕ

### Версия 1.1.0
- Система квестов (базовая)
- NPC с диалогами
- Больше боссов (3-5 новых)
- Дополнительные структуры

### Версия 1.2.0
- Расширенное дерево перков (2000×2000)
- Подземелья с процедурной генерацией
- PvP арены
- Гильдии

### Версия 2.0.0
- Рейды на 5-10 игроков
- Сезонные события
- Торговая система
- Крафт и профессии

---

## 📅 ДАТА РЕЛИЗА

**25 апреля 2026 года, 15:12 (UTC+3)**

---

## 👥 КОМАНДА

- Senior Minecraft Java Developer
- Архитектура: Paper 1.20.4
- Язык: Java 17
- Зависимости: Multiverse-Core, WorldGuard

---

## 🎉 ИТОГ

**Сервер Eclipsia RPG полностью готов к запуску!**

Все критичные и важные системы реализованы, протестированы и готовы к использованию. Игроки могут создавать персонажей, использовать навыки, сражаться с боссами и исследовать мир.

**Время разработки:** ~8 часов  
**Качество кода:** Production-ready  
**Статус:** ✅ ГОТОВ К РЕЛИЗУ
