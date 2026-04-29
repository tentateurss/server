# ECLIPSIA PROJECT - МОДУЛЬНАЯ АРХИТЕКТУРА

## Текущая структура

```
D:\EclipsiaProject\
├── EclipsiaCore\              # ✅ Базовый плагин (Этап 0)
│   ├── API для других плагинов
│   ├── Система классов
│   ├── Хранение данных игроков
│   ├── Система прав (админы)
│   └── Базовые команды
│
├── EclipsiaMobs\              # 🔜 Этап 1 (следующий)
├── EclipsiaItems\             # 🔜 Этап 2
├── EclipsiaDungeons\          # 🔜 Этап 4
└── TestServer\                # Тестовый сервер
```

---

## Принципы модульной архитектуры

### 1. Независимость модулей
- Каждый плагин = отдельный Maven проект
- Собственный pom.xml, plugin.yml
- Можно включать/выключать независимо

### 2. Зависимости
```
EclipsiaCore (базовый)
    ↓ зависит
EclipsiaMobs
    ↓ зависит
EclipsiaItems
    ↓ зависит
EclipsiaDungeons
```

### 3. Взаимодействие через API
```java
// В EclipsiaMobs:
EclipsiaAPI api = EclipsiaAPI.getInstance();
int playerLevel = api.getPlayerLevel(player);
String className = api.getPlayerClassName(player);
```

---

## Этап 1: EclipsiaMobs (следующий)

### Функционал:
- Кастомные мобы из конфига
- Система опыта и уровней
- Дроп орбов (валюта)
- Зоны спавна мобов
- Скейлинг по уровню игрока

### Структура:
```
EclipsiaMobs\
├── pom.xml
├── src\main\
│   ├── java\ru\eclipsia\mobs\
│   │   ├── EclipsiaMobs.java
│   │   ├── mob\
│   │   │   ├── CustomMob.java
│   │   │   └── MobManager.java
│   │   ├── spawn\
│   │   │   ├── SpawnZone.java
│   │   │   └── SpawnManager.java
│   │   ├── experience\
│   │   │   └── ExperienceManager.java
│   │   └── listeners\
│   │       ├── MobSpawnListener.java
│   │       └── MobDeathListener.java
│   └── resources\
│       ├── plugin.yml
│       ├── config.yml
│       └── mobs.yml
```

### Зависимость в pom.xml:
```xml
<dependencies>
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.20.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- EclipsiaCore API -->
    <dependency>
        <groupId>ru.eclipsia</groupId>
        <artifactId>EclipsiaCore</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Конфиг mobs.yml:
```yaml
mobs:
  zombie_warrior:
    display-name: "§cЗомби-воин"
    entity-type: ZOMBIE
    level: 5
    health: 100
    damage: 15
    experience: 50
    drops:
      orbs:
        min: 5
        max: 15
        chance: 100
    spawn-zones:
      - world_spawn_1
      - forest_zone
```

---

## Этап 2: EclipsiaItems

### Функционал:
- Генерация предметов с аффиксами
- Редкости (Обычный, Магический, Редкий, Уникальный)
- Инвентарь предметов
- Торговля между игроками

### Структура:
```
EclipsiaItems\
├── pom.xml
├── src\main\
│   ├── java\ru\eclipsia\items\
│   │   ├── EclipsiaItems.java
│   │   ├── item\
│   │   │   ├── CustomItem.java
│   │   │   ├── ItemGenerator.java
│   │   │   └── ItemManager.java
│   │   ├── affix\
│   │   │   ├── Affix.java
│   │   │   └── AffixManager.java
│   │   ├── rarity\
│   │   │   └── Rarity.java
│   │   └── inventory\
│   │       └── CustomInventory.java
│   └── resources\
│       ├── plugin.yml
│       ├── config.yml
│       ├── items.yml
│       └── affixes.yml
```

---

## Этап 3: EclipsiaPerks (расширение Core)

Добавляется в EclipsiaCore, не отдельный плагин:
```
EclipsiaCore\
└── src\main\java\ru\eclipsia\core\
    └── perks\
        ├── Perk.java
        ├── PerkTree.java
        └── PerkManager.java
```

---

## Этап 4: EclipsiaDungeons

### Функционал:
- Инстансы данжей
- Боссы с механиками
- Групповой контент (пати)
- Таблица лидеров

### Структура:
```
EclipsiaDungeons\
├── pom.xml
├── src\main\
│   ├── java\ru\eclipsia\dungeons\
│   │   ├── EclipsiaDungeons.java
│   │   ├── dungeon\
│   │   │   ├── Dungeon.java
│   │   │   └── DungeonManager.java
│   │   ├── instance\
│   │   │   ├── DungeonInstance.java
│   │   │   └── InstanceManager.java
│   │   ├── boss\
│   │   │   ├── Boss.java
│   │   │   └── BossManager.java
│   │   └── party\
│   │       ├── Party.java
│   │       └── PartyManager.java
│   └── resources\
│       ├── plugin.yml
│       ├── config.yml
│       └── dungeons.yml
```

---

## Взаимодействие плагинов через события

### Пример: EclipsiaMobs → EclipsiaCore

```java
// В EclipsiaMobs при убийстве моба:
public class MobDeathListener implements Listener {
    
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        
        CustomMob mob = getMobData(event.getEntity());
        
        // Добавляем опыт через API
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        api.addExperience(killer, mob.getExperience());
        
        // Вызываем кастомное событие
        Bukkit.getPluginManager().callEvent(
            new EclipsiaPlayerGainExperienceEvent(killer, mob.getExperience())
        );
    }
}
```

### Пример: EclipsiaItems → EclipsiaMobs

```java
// В EclipsiaItems при дропе предмета:
public class ItemDropListener implements Listener {
    
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        CustomMob mob = EclipsiaMobsAPI.getMob(event.getEntity());
        if (mob == null) return;
        
        // Генерируем предмет на основе уровня моба
        CustomItem item = ItemGenerator.generate(mob.getLevel());
        event.getDrops().add(item.toItemStack());
    }
}
```

---

## Преимущества модульной архитектуры

### ✅ Разделение ответственности
- EclipsiaCore = данные, классы, права
- EclipsiaMobs = мобы, опыт
- EclipsiaItems = предметы, лут
- EclipsiaDungeons = данжи, боссы

### ✅ Легкая разработка
- Можно работать над модулями параллельно
- Изменения в одном модуле не ломают другие
- Проще тестировать отдельные части

### ✅ Гибкость
- Можно отключить ненужные модули
- Легко добавлять новые модули
- Можно заменить модуль на другую реализацию

### ✅ Масштабируемость
- Каждый модуль можно оптимизировать отдельно
- Легко распределить нагрузку
- Готовность к сетевой архитектуре

---

## Следующие шаги

1. **Пересобрать EclipsiaCore** с новым API
2. **Протестировать** систему прав и админ команды
3. **Начать разработку EclipsiaMobs** (Этап 1)
4. **Создать конфиги** для мобов
5. **Реализовать систему опыта**

---

**Дата:** 21.04.2026  
**Версия Core:** 0.1.0-SNAPSHOT  
**Статус:** Готов к Этапу 1
