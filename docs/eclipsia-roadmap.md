# Eclipsia — Roadmap

> **Формат:** живой чеклист. Отмечай выполненное `[x]`, оставляй заметки прямо в строках.  
> **Связанные документы:** [`eclipsia-plan.md`](eclipsia-plan.md) — стратегия (зачем), этот файл — тактика (что и когда).  
> **Последнее обновление:** 2026-05-11 (после слияния PR #52 — EclipsiaHUD).

---

## Где мы сейчас

- Сервер: Paper 1.20.4 + Java 17, dev-площадка `dev_flat` в `TestServer/`
- 8 живых модулей: `EclipsiaCore`, `EclipsiaItems`, `EclipsiaSkills`, `EclipsiaMobs`, `EclipsiaPerks`, `EclipsiaLobby`, `EclipsiaTests`, `EclipsiaHUD`
- `EclipsiaBuilder` (процедурный генератор) → переведён в `archive/`, билд карты теперь ручной
- UI: server-side рендер через Adventure API (без зависимости от ресурс-пака)
- Ресурс-пак: скелет в `resourcepack/`, мокапы в `texture/`, кастомный font ещё не нарезан

---

## Фаза 0 — Bootstrap (готово)

- [x] Архивирован `EclipsiaBuilder`, удалены 7 миров и runtime-мусор (PR #51)
- [x] Базовый dev-сервер `TestServer/` с flat-площадкой `dev_flat`
- [x] Документация: `docs/eclipsia-plan.md`, `TestServer/README.md`, `archive/README.md`
- [x] Очистка `.gitignore` (миры, логи, `usercache.json`, runtime-папки)
- [x] CHANGELOG актуализирован

## Фаза 1 — HUD без ресурс-пака (готово)

- [x] Аудит существующего UI-кода в 7 модулях
- [x] Новый модуль `EclipsiaHUD` (PR #52, слит):
  - [x] `SidebarService` — per-player scoreboard, 15 строк
  - [x] `TabListService` — header/footer с плейсхолдерами
  - [x] `BossBarRegistry` — multi-bar per player (XP-bar из Items не трогаем)
  - [x] `TitleCinematicService` — welcome / level-up / boss-spawn / region-enter
  - [x] `RegionRegistry` + `RegionEnterListener` — конфиг-зоны (замена сломанного `RegionTitleListener`)
  - [x] `FloatingLabelService` + `LabelHandle` — TextDisplay-метки
  - [x] `ModernDamageDisplay` — TextDisplay damage numbers + toggle legacy/modern/both
  - [x] `Theme` — единая палитра, MiniMessage wrapper
  - [x] `EclipsiaHUDAPI` — public singleton (soft-dep)
  - [x] `/hud` (alias `/eui`): sidebar / tablist / reload / test / labels
  - [x] `config.yml` со всеми тумблерами и шаблонами
- [x] Cleanup HUD-интеграции (текущий PR):
  - [x] Удалён сломанный `EclipsiaCore/listener/RegionTitleListener.java`
  - [x] `ExperienceManager.levelUp()` мигрирован на soft-dep вызов `EclipsiaHUDAPI.showLevelUp()` через рефлексию (fallback на старое поведение, если HUD выключен)
  - [x] Эта дорожная карта (`docs/eclipsia-roadmap.md`)

## Фаза 2 — Ресурс-пак (план, ~2-4 часа работы)

**Цель:** второй слой UI поверх server-side HUD. Кастомные иконки HP/MP/XP заменяют ванильные сердечки/опытную полоску.

- [ ] Подготовка ассетов
  - [ ] Нарезать мокапы из `texture/` под целевые размеры (HotBar.png, inventory.png, lefthand.png, «Выбранный слот.png», SwordInclass.png, bowinclass.png, staffinclass.png)
  - [ ] Создать иконки HP/MP/XP-полосок (Full/Empty состояния, 9 уровней заполнения для плавной анимации)
  - [ ] Иконки баффов/дебаффов (если будем переопределять)
- [ ] Custom Font
  - [ ] `resourcepack/assets/eclipsia/font/default.json` — bitmap-провайдер с negative ascent
  - [ ] Нарезка спрайтов на индивидуальные глифы (по 1 PNG на каждый бар-сегмент)
  - [ ] Юникод-байндинг — частный диапазон `U+E000`-`U+F8FF`
- [ ] Custom Models
  - [ ] Меч (CMD `300`), лук (`301`), посох (`302`) под классы warrior/archer/mage
  - [ ] Иконки скиллов в хотбаре (через CustomModelData предметов-«пилюль»)
- [ ] Custom Sounds (опционально)
  - [ ] UI-клики, открытие инвентаря, левел-ап, активация скилла
- [ ] Раздача пака
  - [ ] Хостинг на GitHub Releases (тэг `pack-v0.1`) или встроенный HTTP
  - [ ] `server.properties`: `resource-pack=...`, `resource-pack-sha1=...`, `require-resource-pack=true`
- [ ] Документация: workflow «изменил PNG → перепаковал → подменил SHA1 → /reload»

**Связь с HUD:** ресурс-пак ничего не ломает в server-side HUD. Если игрок откажется загружать пак, увидит наш Adventure-UI как есть.

## Фаза 3 — Билд карты вручную (план, setup ~1 час; сам билд — твоя работа)

- [ ] Multiverse-Core: создать второй мир `build` (creative, no-damage, no-spawn, normal generator)
- [ ] WorldGuard: глобальные флаги `build`-мира (`build allow`, `pvp deny`), регион `__global__`
- [ ] WorldEdit + FAWE: проверить, что `//brush`, `//gen`, `//paste` работают в `build`-мире
- [ ] Документация в `docs/builder-workflow.md`:
  - [ ] Список рекомендованных клиентских модов (Axiom, Litematica, Schematic Brush)
  - [ ] Команды-фавориты, brush-пресеты
  - [ ] Workflow «билд → схематика → перенос в prod-мир»
- [ ] Источники бесплатных ассетов (Builder's Refuge, Planet Minecraft)

Сам контент карты (Эликий, Берег, подземелья) — ручная работа автора проекта, не Девина.

## Фаза 4 — Бесплатные RPG-плагины (план, ~2-3 часа)

Все free, без премиума.

- [ ] **Nexo** (форк Oraxen) или официальный Oraxen
  - [ ] Установка, базовый конфиг
  - [ ] Один кастомный предмет с CMD как proof-of-concept
  - [ ] Интеграция с `EclipsiaItems` (если решим использовать Oraxen для visual-моделей)
- [ ] **MythicMobs (free version)**
  - [ ] Один кастомный моб с MythicMobs-скиллами
  - [ ] Дружба с `EclipsiaMobs/BossManager` (или решение об отказе и использовании только своего)
- [ ] **AuraSkills** (опционально)
  - [ ] Решение: дополняет ли наш `EclipsiaSkills` или дублирует
- [ ] **BetonQuest** + **Citizens**
  - [ ] Установка обоих
  - [ ] Один NPC-торговец и один квестодатель как PoC
  - [ ] Связка с `EclipsiaHUDAPI.spawnLabelOn()` для квест-маркеров (! / ?)
- [ ] Все конфиги версионируются в `TestServer/plugins/<plugin>/config.yml`

## Фаза 5 — NPC + квесты (план, ~3-4 часа после Фазы 4)

- [ ] Citizens-NPC с floating-labels через наш `EclipsiaHUDAPI`
  - [ ] «!» жёлтый над NPC с доступным квестом
  - [ ] «?» при готовности сдать
  - [ ] Имя NPC и роль (gradient hex)
- [ ] BetonQuest-сценарии:
  - [ ] Стартовый туториальный квест (поговори → убей → получи награду)
  - [ ] Один длинный квест с цепочкой условий
- [ ] Уведомления через `TitleCinematicService` (новый этап квеста, завершение)
- [ ] Sidebar-трекер активных квестов (через extension к `SidebarService`)

## Фаза 6 — Расширение контента

- [ ] Перки: расширить дерево с 28 узлов до 56+ (см. план §10)
- [ ] Скиллы-эклипсы: добавить второй и третий ряд на класс
- [ ] Боссы: 2-3 новых босса на разные биомы
- [ ] Лут: PoE-style редкости + аффиксы (расширить `EclipsiaItems`)
- [ ] Социальные системы (L2-style): кланы, осады, рейды (свой плагин или модуль `EclipsiaSocial`)

## Фаза 7 — Деплой и DevOps

- [ ] Хостинг VPS (Hetzner / Vultr / собственный)
- [ ] systemd-unit для автозапуска
- [ ] Бэкапы CoreProtect + миров через cron
- [ ] GitHub Actions:
  - [ ] CI собирает все 8 модулей при push
  - [ ] Артефакты `.jar` публикуются как assets PR / release
  - [ ] Опционально: автодеплой на VPS по тегу
- [ ] Мониторинг: bStats (уже включён) + Grafana для серверных метрик
- [ ] Лицензия (MIT/Apache-2.0/собственная)

## Фаза 8 — Production

- [ ] Альфа-тест: 5-10 приглашённых игроков
- [ ] Сбор фидбэка через канал в Discord/чате
- [ ] Балансировка цифр (HP/DMG/XP-кривая)
- [ ] Открытие для всех

---

## Технический долг (текущий)

- [ ] `HUDActionBarListener` (EclipsiaSkills) и `PlayerHUDManager` (EclipsiaItems) используют разные планировщики (10t и 20t). Когда будет время — консолидировать в один источник.
- [ ] `HUDActionBarListener` использует legacy `§` коды, остальной новый код — Adventure. Когда-нибудь переписать на MiniMessage.
- [ ] `DamageDisplay` (старый ArmorStand-подход) и `ModernDamageDisplay` (TextDisplay) сосуществуют. Решить, какой оставить как default, и удалить второй.
- [ ] Папка `netrogat/` в репо — что это, не понятно (похоже на legacy-копию). Разобраться, нужна ли.
- [ ] `RegionTitleListener` удалён в этой фазе.

## Принципы (фиксируем, чтобы не забывать)

1. **Server-side first.** Любая фича должна работать на ванильном клиенте без RP, даже если RP добавляет красоты сверху.
2. **Soft-dep всё, что можно.** Один модуль не должен валиться без другого — используем `EclipsiaHUDAPI.getInstance() == null` и рефлексию.
3. **Конфиг → код.** Любая «магическая» цифра/строка должна быть в `config.yml`.
4. **Adventure API над legacy.** Новый код пишется через `Component`/`MiniMessage`, legacy `§codes` — только для совместимости со старым.
5. **Без премиум-плагинов.** Только бесплатное (и наше).
6. **Тесты в `EclipsiaTests`.** Бизнес-логика покрывается, UI и интеграция — ручным smoke-тестом через `/hud test`, `/eclipsia test` и т.п.
7. **PR-флоу.** Один PR = одна логически завершённая фича. Коммиты внутри PR — семантические, по подсистемам.

---

## Контакты для решений

Если что-то меняется в плане (новая фаза, отказ от компонента, смена приоритетов) — фиксируем здесь.

| Дата | Изменение | Источник |
|------|-----------|----------|
| 2026-05-XX | Решение: отказ от процедурки, ручной билд | пользователь |
| 2026-05-XX | Решение: красивый UI собираем без RP-зависимости (server-side first) | пользователь |
| 2026-05-11 | PR #52 — EclipsiaHUD слит | пользователь |
| 2026-05-11 | PR #53 (текущий) — cleanup + этот roadmap | пользователь |
