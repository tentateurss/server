# Eclipsia — план «перезапуска» проекта

> **Статус:** черновик v1, май 2026  
> **Цель:** перевести проект на чистый workflow: ручной билд карты в dev-мире, фокус на красивом UI, ванильный Minecraft без модов, только плагины (свои + бесплатные).

---

## 1. Что меняется по сравнению с текущим состоянием

| Было | Стало |
|------|-------|
| Процедурный `EclipsiaBuilder` строил Эликий/Берег/горы/собор | **Отказались.** Карта строится руками в dev-мире через WorldEdit/FAWE/Axiom |
| 7+ закоммиченных миров (`world/`, `beach/`, `lobby/`, `*_nether/`, `*_the_end/`) | Все миры удалены из git и из dev-сервера. Один чистый `dev_flat` мир для тестов |
| Версия Minecraft зафиксирована на 1.20.4 | На этом этапе **остаёмся на 1.20.4** ради совместимости с уже работающими плагинами. Апгрейд на 1.21.x — отдельная фаза (см. §7) |
| UI: дефолтный Minecraft + точечные кастомные текстуры (хотбар, иконки классов) | UI собирается как полноценная тема (custom font HUD, кастомные меню без «сундучного» вида, переиспользование наших мокапов из `texture/`) |
| Билд-артефакты `.jar` в `dist/plugins/` под управлением вручную | План: GitHub Actions собирает плагины из исходников и публикует JARы как артефакты PR / релиза (см. §6) |

Существующие модули (`EclipsiaCore`, `EclipsiaItems`, `EclipsiaSkills`, `EclipsiaMobs`, `EclipsiaPerks`, `EclipsiaLobby`, `EclipsiaTests`) сохраняются и развиваются дальше — они и есть наш RPG-стек, бесплатных аналогов нам не нужно.

`EclipsiaBuilder` перемещён в `archive/EclipsiaBuilder/` (исходники сохранены как референс, плагин из сборки исключён).

---

## 2. Стек сервера

### Ядро
- **Paper 1.20.4** (последний билд `1.20.4-RXXX`). Java 17.
- Параметры запуска: `java -Xms4G -Xmx4G --add-modules=jdk.incubator.vector -jar paper.jar --nogui` + Aikar flags при переезде на хост.

### Бесплатные плагины-фундамент (ставим в `TestServer/plugins/`):
| Плагин | За что отвечает | Источник |
|--------|------------------|----------|
| **Multiverse-Core** | управление мирами, `dev_flat` для тестов, отдельный `build` для билда финальной карты | SpigotMC |
| **WorldEdit** | базовые операции `//set`, `//copy`, `//paste`, схематики | EngineHub |
| **FastAsyncWorldEdit (FAWE)** | асинхронные масштабные операции, brush-движок | IntellectualSites |
| **WorldGuard** | защита регионов, флаги, границы PvP/PvE-зон | EngineHub |
| **Vault** | API экономики/прав (нужен другим плагинам) | MilkBowl |
| **PlaceholderAPI** | плейсхолдеры для HUD/меню (`%eclipsia_mana%`, `%eclipsia_level%`) | extendedclip |
| **CoreProtect** | rollback, аудит. **Обязателен ещё на этапе билда** | PlayPro |
| **bStats** | метрики (уже стоит) | bStats |
| **Citizens** *(позже)* | NPC: торговцы, квестодатели | citizensnpcs.co |
| **BetonQuest** *(позже)* | квесты, диалоги | BetonQuest |
| **Geyser + Floodgate** *(опционально)* | поддержка Bedrock-клиентов | GeyserMC |

«Премиум» плагины (ItemsAdder / MMOCore / Oraxen / MythicMobs Premium) **не ставим** — у нас уже есть свой RPG-стек.

### Наши плагины (модули в репо)
- `EclipsiaCore` — API, профили, мана, классы, права
- `EclipsiaItems` — предметы, аффиксы, 8-слотовая экипировка, GUI
- `EclipsiaSkills` — навыки-эклипсы, регенерация маны
- `EclipsiaMobs` — кастомные мобы, GatekeeperBoss, опыт
- `EclipsiaPerks` — дерево перков (28 узлов, расширение запланировано)
- `EclipsiaLobby` — лобби, создание персонажей
- `EclipsiaTests` — автотесты (внутренний DevOps-инструмент)
- `EclipsiaHUD` *(новый, §3)* — кастомный HUD через bossbar/actionbar/title

---

## 3. UI — это центральная цель проекта

Без модов «не-майнкрафтовский» вид собирается из 3 техник, все три уже частично у нас есть:

### 3.1 Resource Pack (уже есть скелет в `resourcepack/`)
Сейчас покрыто:
- Кастомный hotbar / выделение слота / off-hand
- Иконки классов (warrior/archer/mage через CustomModelData 200/201/202)
- Фон инвентаря

Нужно добавить:
- **Custom Font** (`assets/eclipsia/font/default.json`) — глифы для HUD-картинок (HP-бар, MP-бар, минимапа, иконки скиллов, рамки рарности предметов). Это и есть «секрет красивого UI» — рисуем PNG, отдаём как символ шрифта с отрицательным смещением, поверх любого экрана.
- **Custom Models** для оружия классов (меч/лук/посох с уникальными моделями через CMD)
- **Custom Sounds** (`assets/eclipsia/sounds/`) — UI-клики, открытие меню, левел-ап
- **Текстуры брони** через CustomModelData (или через core_shaders при апгрейде на 1.21.x — там это сильно проще)

Наши высококачественные мокапы в `texture/` (HotBar.png, inventory.png, lefthand.png, «Выбранный слот.png», SwordInclass.png, bowinclass.png, staffinclass.png) пока лежат отдельно от resource pack — их надо **интегрировать** в `resourcepack/assets/...` с уменьшением до целевых размеров и нарезкой на спрайты для custom font.

### 3.2 Server-side HUD-плагин (`EclipsiaHUD`, новый)
Java-плагин, который каждый тик:
1. Берёт `currentMana` / `level` / `xp` / активные баффы из `EclipsiaAPI`
2. Формирует строку из глифов custom font (например: `\uE001  85/100  \uE010 12/40 XP`)
3. Шлёт игроку через `Player#sendActionBar()` или Title API

Это полностью server-side: клиент видит «полоски HP/MP в стиле L2», но никаких модов и никакой Forge-зависимости.

Аналогично для:
- **Полноценное «не-сундучное» меню** — `Player#openInventory(InventoryHolder)` с CHEST_27, фон — символ custom font, элементы — `ItemStack` с пустым `display name` и `CustomModelData` для иконок. Получается окно, визуально не похожее на сундук.
- **Title-cinematic** для входа в зону / победы над боссом (уже частично сделано в `RegionTitleListener`).

### 3.3 Хостинг ресурс-пака
- Сейчас инструкция в `resourcepack/README.md` ссылается на `D:\EclipsiaProject\...` — это локальный путь, надо переехать на GitHub Releases.
- Целевой URL после первого релиза: `https://github.com/tentateurss/server/releases/download/rp-vX.Y/eclipsia-resourcepack.zip`
- SHA-1 пересчитывается автоматически в CI (см. §6).
- В `EclipsiaCore/config.yml` пишем `resource-pack.url` + `sha1`.

---

## 4. Карта и dev-площадка

### 4.1 dev_flat — мир для разработки
- Тип: `flat` (1 layer bedrock + 2 layers stone + 1 layer dirt + 1 layer grass).
- Используем для:
  - Тестирования плагинов, скиллов, GUI, ресурс-пака
  - Прототипирования отдельных зданий (пол даёт чистый фон без рельефа)
  - Запуска `/build` команд (если когда-то вернём процедурные мини-зоны)

### 4.2 build — мир для финального ручного билда
- Создаётся через `/mv create build normal -t flat` и **остаётся пустым** до начала продакшен-билда.
- Туда переносим референс-схематики через FAWE.

### 4.3 Финальная игровая карта (когда наступит время)
- Базовый рельеф: WorldPainter / Axiom → экспорт → загружается как `world` мир Multiverse.
- Постройки: ручной билд + готовые схематики с BuildersRefuge / Polymart-free для второстепенного декора.
- Зоны (стартовый город, лагеря 1-5/5-10/10-15/15-20, данжи, PvP-локации) размечаем регионами WorldGuard.

---

## 5. Геймплейный roadmap (PoE + L2)

Этапы 0–3 (Core, Mobs, Items, Perks) **выполнены** — см. `ROADMAP.md` и `CHANGELOG.md`. Новые этапы:

### Этап 4: UI-перезапуск (текущий) [P0]
- [ ] Перенести `EclipsiaBuilder` в `archive/`
- [ ] Удалить старые миры из git
- [ ] Настроить `dev_flat` в `TestServer`
- [ ] Собрать `assets/eclipsia/font/default.json` (минимум 1 глиф = MP bar)
- [ ] Создать модуль `EclipsiaHUD` (рендер action-bar через custom font)
- [ ] Интегрировать мокапы из `texture/` в `resourcepack/`
- [ ] Скрипт сборки resource pack → ZIP + SHA-1 в GitHub Actions
- [ ] Релизить ресурс-пак через GitHub Releases

### Этап 5: NPC и квесты [P1]
- [ ] Citizens 1.20.4
- [ ] BetonQuest, базовая ветка стартовых квестов
- [ ] 5 NPC в стартовом городе (квестодатель, торговец, кузнец, банкир, телепортист)

### Этап 6: Данжи и групповой контент [P1]
- [ ] Адаптировать `EclipsiaMobs` под инстансы (изолированные миры через Multiverse-Inventories)
- [ ] 3 данжа разной сложности, 5 боссов
- [ ] Простейшая система пати (`/party invite/leave`)
- [ ] Таблица лидеров (PlaceholderAPI + SQLite)

### Этап 7: PvP и социальные системы (L2-style) [P2]
- [ ] WorldGuard PvP-зоны (PK / open PvP)
- [ ] Кланы (`/clan create/invite/wars`)
- [ ] Осады точек интереса (раз в неделю, окно 2 часа)

### Этап 8: Endgame (PoE-style) [P2]
- [ ] Карты (Maps) — генерируемые инстансы с модификаторами
- [ ] Лиги (сезоны, ладдер)
- [ ] Уникальные предметы с механиками-триггерами

### Этап 9: Миграция данных и масштабирование [P3]
- [ ] Перевести `IPlayerDataStorage` с PDC на SQLite при 50+ онлайн
- [ ] Async I/O для всех ёмких операций
- [ ] Spark-профайлинг, оптимизация горячих путей

---

## 6. CI / сборка / деплой

Сейчас JARы коммитятся в `dist/plugins/` руками. Это нормально для одиночной разработки, но плохо для воспроизводимости.

Целевая схема:
1. **GitHub Actions** (`.github/workflows/build.yml`):
   - На каждый push/PR — `mvn -B package` для всех модулей.
   - Артефакты PR: `EclipsiaCore.jar`, `EclipsiaItems.jar`, ... — собираются и аплоадятся как PR-attachments.
   - На теги `v*` — публикация в GitHub Releases.
2. **Resource pack** собирается тем же workflow:
   - `cd resourcepack && zip -r ../eclipsia-resourcepack.zip .`
   - SHA-1 пишется в `release-notes`.
3. **Тестовый сервер** на VPS (`projectbots@138.124.112.60` или новый):
   - `git pull && ./deploy.sh` — скрипт качает последние JARы из GitHub Releases и кладёт в `plugins/`.
   - Используется только когда явно решим хостить.

В этой PR-итерации CI **не настраиваем** — сначала консолидируем структуру репо, потом отдельной PR добавим workflow.

---

## 7. Апгрейд версии Minecraft (отдельная фаза, не сейчас)

Поднимать с 1.20.4 до 1.21.11 имеет смысл **после** того, как UI-перезапуск стабилизируется. Что потребует апгрейд:
- Обновить `paper-api` версии в каждом `pom.xml` модуля.
- Обновить `pack_format` в `resourcepack/pack.mcmeta` (1.20.4 = 22, 1.21.11 = ≥48, см. https://minecraft.wiki/w/Pack_format).
- Пройти по API изменениям 1.21: `ItemMeta` → `DataComponent`, `EnchantmentTarget`, `Material#values()`.
- Пересобрать JARы, прогнать `EclipsiaTests`.

Версия Java по требованиям Paper:
- 1.20.4 → Java 17
- 1.21.x → Java 21
- Paper 26.1+ → Java 25

---

## 8. Что попадает в текущий PR

- [x] `docs/eclipsia-plan.md` (этот документ)
- [x] `EclipsiaBuilder/` → `archive/EclipsiaBuilder/` + `archive/README.md`
- [x] Удаление миров: `TestServer/{world,beach,lobby,world_nether,world_the_end,lobby_nether,lobby_the_end}`
- [x] `.gitignore` — игнорим будущие генерируемые миры/логи
- [x] `TestServer/server.properties` — `level-name=dev_flat`, `level-type=minecraft:flat`, `generate-structures=false`
- [x] `TestServer/bukkit.yml` — указатель на flat-генератор для `dev_flat`
- [x] `TestServer/start.sh` (Linux/macOS), сохраняем `start.bat` для Windows
- [x] Обновление корневого `README.md` — отражение нового направления
- [x] Запись в `CHANGELOG.md` (v1.1.0-bootstrap)

**Что НЕ трогаем в этом PR** (отдельные PR при необходимости):
- Логика существующих плагинов (`EclipsiaCore`, `Items`, `Skills`, `Mobs`, `Perks`, `Lobby`, `Tests`)
- `netrogat/` (исторический бандл, рассмотрим отдельно)
- Сборные JAR в `dist/plugins/`
- Текстурные мокапы в `texture/` (мигрируем в `resourcepack/` отдельным PR)

---

## 9. Открытые вопросы для обсуждения

1. **EclipsiaBuilder** — перенести в `archive/` (как в этом PR) или удалить совсем? Сейчас выбран вариант «архив» для возможного будущего переиспользования в мини-данжах.
2. **`netrogat/`** — что это и нужен ли он в репо? (выглядит как форк под другой сервер).
3. **Версия Minecraft** — действительно стоим на 1.20.4 или поднимаем на 1.21.x уже сейчас? (рекомендация: не сейчас).
4. **Хостинг ресурс-пака** — GitHub Releases или свой CDN?
5. **EclipsiaHUD** — пишем как отдельный модуль или встраиваем в `EclipsiaCore`?

---

_Документ — живой. Изменения через PR в `docs/eclipsia-plan.md`._
