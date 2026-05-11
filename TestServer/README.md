# TestServer — dev-площадка для Eclipsia

Локальный Paper-сервер для разработки и тестирования плагинов Eclipsia.

## Что это

- **Версия:** Paper 1.20.4 (Java 17).
- **Мир:** `dev_flat` — плоский мир (bedrock + 2 stone + dirt + grass, биом plains). Подходит для тестов скиллов, GUI, ресурс-пака, размещения референс-построек.
- **Назначение:** dev sandbox, **не прод**. Все миры и кэши в `.gitignore` — они генерируются на лету и не коммитятся.

## Первый запуск

1. Скачайте Paper 1.20.4: https://papermc.io/downloads/paper. Положите JAR как `paper.jar` в эту папку.
2. Скачайте/постройте плагины из репо (см. корневой `README.md`) и положите в `plugins/`:
   - `EclipsiaCore.jar`
   - `EclipsiaItems.jar`
   - `EclipsiaSkills.jar`
   - `EclipsiaMobs.jar`
   - `EclipsiaPerks.jar`
   - `EclipsiaLobby.jar`
   - `EclipsiaTests.jar` *(опционально, для автотестов)*
3. Поставьте бесплатные плагины-зависимости в `plugins/`:
   - [Multiverse-Core](https://www.spigotmc.org/resources/multiverse-core.390/)
   - [WorldEdit](https://dev.bukkit.org/projects/worldedit)
   - [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/)
   - [WorldGuard](https://dev.bukkit.org/projects/worldguard)
   - [Vault](https://www.spigotmc.org/resources/vault.34315/)
   - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
   - [CoreProtect](https://www.spigotmc.org/resources/coreprotect.8631/)
4. Примите EULA (уже принят в `eula.txt`).
5. Запустите:
   - **Linux/macOS:** `./start.sh`
   - **Windows:** `start.bat`

## Структура

```
TestServer/
├── server.properties     # level-name=dev_flat, level-type=flat
├── bukkit.yml            # настройки спавна, генератор по умолчанию
├── spigot.yml            # настройки Spigot
├── eula.txt              # EULA accepted
├── ops.json              # список админов
├── start.sh              # Linux/macOS launcher (Aikar's flags)
├── start.bat             # Windows launcher
├── plugins/              # плагины (JARы НЕ в git)
│   ├── EclipsiaCore/     # data-папки трэкаются для конфигов
│   ├── EclipsiaItems/
│   └── ...
├── config/               # paper config
└── dev_flat/             # мир (генерируется автоматически, gitignored)
```

## Дополнительные миры

После запуска создайте через Multiverse:
```
/mv create build flat       # пустой мир для финального ручного билда
/mv create test_arena flat  # отдельная арена для тестов боссов
```

## Сброс мира

```
# Остановите сервер
rm -rf dev_flat/
# Запустите снова — мир пересоздастся
```

## Известные особенности

- Текущая версия плагинов рассчитана на 1.20.4. При апгрейде Paper версии обновите все `pom.xml` модулей (см. `docs/eclipsia-plan.md`, §7).
- `EclipsiaBuilder` выведен из активной разработки и переехал в `archive/EclipsiaBuilder/` — карта теперь строится руками, а не процедурно.
