# Eclipsia Resource Pack

Ресурс-пак с кастомными текстурами для:
- Иконок выбора класса (воин/лучник/маг)
- Хотбара, выделения слота, оффхенда
- Фона инвентаря

## Структура

```
resourcepack/
├── pack.mcmeta                                 # pack_format=22 (Minecraft 1.20.4)
└── assets/minecraft/
    ├── models/item/
    │   ├── iron_sword.json                     # CMD 200 → class_warrior
    │   ├── bow.json                            # CMD 201 → class_archer
    │   ├── blaze_rod.json                      # CMD 202 → class_mage
    │   └── eclipsia/
    │       ├── class_warrior.json
    │       ├── class_archer.json
    │       └── class_mage.json
    └── textures/
        ├── item/eclipsia/
        │   ├── class_warrior.png               # из SwordInclass.png
        │   ├── class_archer.png                # из bowinclass.png
        │   └── class_mage.png                  # из staffinclass.png
        └── gui/
            ├── container/inventory.png
            └── sprites/hud/
                ├── hotbar.png                  # из HotBar.png
                ├── hotbar_selection.png        # из «Выбранный слот.png»
                └── hotbar_offhand_left.png     # из lefthand.png
```

## CustomModelData

| Material   | CMD  | Что показывает |
|------------|-----:|----------------|
| IRON_SWORD | 200  | class_warrior  |
| BOW        | 201  | class_archer   |
| BLAZE_ROD  | 202  | class_mage     |

(Зарезервировано для будущих текстур: 1/2/3 — эклипсы навыков, 10/11/12 — поддержки, 100/101 — амулеты.)

## Развёртывание

### 1. Упакуйте в zip

Уже собрано: `D:\EclipsiaProject\TestServer\eclipsia-resourcepack.zip` (9.81 MB).

Чтобы пересобрать вручную:
```powershell
Compress-Archive -Path D:\EclipsiaProject\resourcepack\* `
                 -DestinationPath D:\EclipsiaProject\TestServer\eclipsia-resourcepack.zip `
                 -CompressionLevel Optimal -Force
```

### 2. Получите SHA-1

```powershell
(Get-FileHash D:\EclipsiaProject\TestServer\eclipsia-resourcepack.zip -Algorithm SHA1).Hash
```

Текущий SHA-1: `CF5DA72DD22B46AAF6DE5E78A8C0E7FE7D7FD620`

### 3. Захостите zip и пропишите URL

Куда положить:
- **GitHub Releases** (рекомендуется) — `https://github.com/<user>/<repo>/releases/download/v1.0/eclipsia-resourcepack.zip`
- **Dropbox/Yandex Disk** — публичная ссылка, заменив `?dl=0` на `?dl=1`
- **Свой сервер** через Nginx/Apache — `https://your-domain.com/eclipsia.zip`

### 4. Обновите конфиг сервера

Файл `TestServer/plugins/EclipsiaCore/config.yml`:

```yaml
resource-pack:
  enabled: true
  url: "https://your-host.example.com/eclipsia-resourcepack.zip"
  sha1: "CF5DA72DD22B46AAF6DE5E78A8C0E7FE7D7FD620"
  prompt-message: "§6Для полного игрового опыта рекомендуется установить ресурс-пак!"
  required: false
  check-on-join: true
```

После изменения URL пересчитывайте SHA-1: при изменении содержимого zip хеш меняется.

### 5. Перезапустите сервер

```
/reload confirm
```
или полный рестарт. Игроки получат предложение установить пак при заходе.

## Что произойдёт без ресурс-пака

Игра отрендерит ванильные текстуры:
- IRON_SWORD/BOW/BLAZE_ROD в GUI выбора класса
- Стандартный хотбар/инвентарь Minecraft

Логика игры от этого не страдает.
