package ru.eclipsia.mobs.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.eclipsia.mobs.mob.MobManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Менеджер спавна мобов.
 * Поддерживает:
 *  — глобальный {@code max-mobs-per-zone} + override per-zone ({@code max-mobs});
 *  — exclusion-зоны ({@code spawn.exclusion-zones} в config.yml), в которых
 *    мобы не спавнятся (лагеря, города и прочие safe-зоны).
 */
public class SpawnManager {

    /** PDC-ключ: в каком spawn-zone был заспавнен моб. Нужен, чтобы cap
     * по зонам работал даже если моб отбрёл от центра (иначе WITHER_SKELETON
     * в роли гейткипера уходит в лес → не попадает в getNearbyEntities →
     * SpawnManager думает что зона пуста → спавнит ещё одного, и так без
     * предела). */
    public static final String ZONE_ID_KEY = "eclipsia_spawn_zone";

    private static SpawnManager instance;

    private final Plugin plugin;
    private final Map<String, SpawnZone> zones;
    private final List<ExclusionZone> exclusions;
    private BukkitTask spawnTask;
    private BukkitTask keepOutTask;

    private boolean spawnEnabled;
    private int spawnRadius;
    private int maxMobsPerZone;
    private int spawnInterval;

    private SpawnManager(Plugin plugin) {
        this.plugin = plugin;
        this.zones = new HashMap<>();
        this.exclusions = new ArrayList<>();
    }

    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("SpawnManager уже инициализирован!");
        }
        instance = new SpawnManager(plugin);
        instance.loadConfig();
        instance.loadZones();
        // На старте сервера ВСЕГДА вычищаем кастом-мобов с предыдущей сессии,
        // чтобы новый ростер из mobs.yml встал сразу. Иначе старые
        // skeleton_archer/zombie_warrior из old config остаются жить
        // на чанках и блокируют per-zone cap (новые не спавнятся).
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> instance.purgeAllCustomMobs(), 60L);
        instance.startSpawning();
    }

    public static SpawnManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SpawnManager не инициализирован!");
        }
        return instance;
    }

    private void loadConfig() {
        spawnEnabled = plugin.getConfig().getBoolean("spawn.enabled", true);
        spawnRadius = plugin.getConfig().getInt("spawn.spawn-radius", 50);
        maxMobsPerZone = plugin.getConfig().getInt("spawn.max-mobs-per-zone", 10);
        spawnInterval = plugin.getConfig().getInt("spawn.spawn-interval", 100);

        // Exclusion-зоны: список {world, x, z, radius}. Y игнорируется — зона
        // вертикальная (цилиндр), т.к. лагеря не требуют точной высоты.
        ConfigurationSection exSection = plugin.getConfig().getConfigurationSection("spawn.exclusion-zones");
        exclusions.clear();
        if (exSection != null) {
            for (String key : exSection.getKeys(false)) {
                ConfigurationSection ex = exSection.getConfigurationSection(key);
                if (ex == null) continue;
                String world = ex.getString("world", "world");
                double x = ex.getDouble("x", 0);
                double z = ex.getDouble("z", 0);
                double r = ex.getDouble("radius", 0);
                if (r <= 0) continue;
                exclusions.add(new ExclusionZone(key, world, x, z, r));
                plugin.getLogger().info("Загружена exclusion-зона: " + key
                        + " (" + world + " @ " + x + "," + z + " r=" + r + ")");
            }
        }
    }

    private void loadZones() {
        File mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(mobsFile);

        ConfigurationSection zonesSection = config.getConfigurationSection("spawn-zones");
        if (zonesSection == null) {
            plugin.getLogger().warning("Секция 'spawn-zones' не найдена в mobs.yml!");
            return;
        }

        for (String zoneId : zonesSection.getKeys(false)) {
            try {
                ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneId);
                SpawnZone zone = new SpawnZone(zoneId, zoneConfig);
                zones.put(zoneId, zone);

                plugin.getLogger().info("Загружена зона: " + zoneId +
                    " (Ур. " + zone.getMinLevel() + "-" + zone.getMaxLevel() +
                    ", max-mobs=" + (zone.getMaxMobs() > 0 ? zone.getMaxMobs() : maxMobsPerZone) + ")");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки зоны: " + zoneId, e);
            }
        }

        plugin.getLogger().info("Загружено зон: " + zones.size());
    }

    /**
     * Запустить автоматический спавн мобов
     */
    private void startSpawning() {
        if (!spawnEnabled) {
            plugin.getLogger().info("Автоматический спавн мобов отключен");
            return;
        }

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (SpawnZone zone : zones.values()) {
                trySpawnInZone(zone);
            }
        }, spawnInterval, spawnInterval);

        plugin.getLogger().info("Автоматический спавн мобов запущен (интервал: " +
            (spawnInterval / 20.0) + " сек)");

        // Keep-out: каждые 2 секунды чистим кастом-мобов из exclusion-зон.
        // Мобы, забредшие в лагерь/арену (через AI-погоню за игроком),
        // должны быть удалены, иначе игрок не может находиться в safe-зоне.
        if (!exclusions.isEmpty()) {
            keepOutTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    this::cleanupExclusions, 40L, 40L);
            plugin.getLogger().info("Keep-out task запущен для "
                    + exclusions.size() + " exclusion-зон");
        }
    }

    /**
     * Пройтись по всем мирам, найти кастом-мобов с PDC-меткой
     * {@link #ZONE_ID_KEY} и удалить тех, кто оказался внутри exclusion-зоны.
     * Боссы (eclipsia_boss) НЕ трогаем — их арена сама себя контролирует.
     */
    private void cleanupExclusions() {
        NamespacedKey key = new NamespacedKey(plugin, ZONE_ID_KEY);
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (e instanceof org.bukkit.entity.Player) continue;
                // Только наши кастом-мобы (имеют ZONE_ID_KEY).
                if (living.getPersistentDataContainer().get(key, PersistentDataType.STRING) == null) continue;
                // Боссов не трогаем.
                if (e.hasMetadata("eclipsia_boss")) continue;
                if (isInExclusion(e.getLocation())) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0 && plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("KeepOut: удалено мобов из exclusion-зон: " + removed);
        }
    }

    /**
     * Попытка заспавнить моба в зоне
     */
    private void trySpawnInZone(SpawnZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) return;

        // Пустая зона (спавн отключён): mobs=[] или spawn-rate=0 — пропуск.
        if (zone.getMobs() == null || zone.getMobs().isEmpty()) return;
        if (zone.getSpawnRate() <= 0) return;

        // Лимит мобов в зоне: per-zone override побеждает глобальный.
        int cap = zone.getMaxMobs() > 0 ? zone.getMaxMobs() : maxMobsPerZone;
        if (cap <= 0) return;
        int mobCount = countMobsInZone(zone, world);
        if (mobCount >= cap) return;

        int toSpawn = Math.min(zone.getSpawnRate(), cap - mobCount);

        for (int i = 0; i < toSpawn; i++) {
            String mobId = zone.getRandomMob();
            if (mobId == null) continue;

            Location spawnLoc = zone.getRandomLocation(world);

            // null = SpawnZone не нашла свободную точку (попала в блок).
            if (spawnLoc == null) continue;
            if (!isLocationSafe(spawnLoc)) continue;
            if (isInExclusion(spawnLoc)) continue;

            LivingEntity mob = MobManager.getInstance().spawnCustomMob(mobId, spawnLoc);
            if (mob != null) {
                // Помечаем, в какой зоне мы его заспавнили.
                mob.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, ZONE_ID_KEY),
                        PersistentDataType.STRING,
                        zone.getId());
            }
        }
    }

    /**
     * Подсчитать мобов в зоне.
     * <p>Считаем по PDC-тегу зоны, а не по радиусу — иначе моб, ушедший за
     * границу зоны (WITHER_SKELETON с AI), не учитывается и SpawnManager
     * начинает спавнить дубликаты.
     */
    private int countMobsInZone(SpawnZone zone, World world) {
        NamespacedKey key = new NamespacedKey(plugin, ZONE_ID_KEY);
        int count = 0;
        for (Entity e : world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (!MobManager.getInstance().isCustomMob(living)) continue;
            String zoneId = living.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (zone.getId().equals(zoneId)) count++;
        }
        return count;
    }

    /**
     * Проверить безопасна ли локация для спавна
     */
    private boolean isLocationSafe(Location location) {
        // Проверяем что блок под ногами твердый
        if (!location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            return false;
        }

        // Проверяем что над головой есть место
        if (location.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Проверить, находится ли локация в одной из exclusion-зон (лагерь и т.п.).
     */
    public boolean isInExclusion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String w = loc.getWorld().getName();
        for (ExclusionZone ex : exclusions) {
            if (!ex.world.equals(w)) continue;
            double dx = loc.getX() - ex.x;
            double dz = loc.getZ() - ex.z;
            if (dx * dx + dz * dz <= ex.radius * ex.radius) return true;
        }
        return false;
    }

    /**
     * Заспавнить моба вручную
     */
    public LivingEntity spawnMob(String mobId, Location location) {
        return MobManager.getInstance().spawnCustomMob(mobId, location);
    }

    /**
     * Удалить ВСЕ кастом-мобы (с PDC-меткой ZONE_ID_KEY) во всех мирах.
     * Используется после reload mobs.yml, чтобы новый конфиг немедленно
     * применился — старые мобы старого ростера despawn'ятся, и зоны
     * перезаспавнят актуальный набор.
     *
     * @return количество удалённых мобов
     */
    public int purgeAllCustomMobs() {
        NamespacedKey key = new NamespacedKey(plugin, ZONE_ID_KEY);
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (e instanceof org.bukkit.entity.Player) continue;
                if (e.hasMetadata("eclipsia_boss")) continue;
                if (living.getPersistentDataContainer().get(key, PersistentDataType.STRING) == null) continue;
                e.remove();
                removed++;
            }
        }
        plugin.getLogger().info("purgeAllCustomMobs: удалено " + removed);
        return removed;
    }

    /**
     * Перезагрузить mobs.yml: чистим зоны, удаляем всех живых
     * кастом-мобов и заново читаем конфиг. Активный spawnTask продолжает
     * работать со свежим списком zones.
     */
    public void reload() {
        zones.clear();
        purgeAllCustomMobs();
        loadZones();
    }

    /**
     * Получить зону по ID
     */
    public SpawnZone getZone(String id) {
        return zones.get(id);
    }

    /**
     * Получить количество зон
     */
    public int getZoneCount() {
        return zones.size();
    }

    /**
     * Остановить спавн мобов
     */
    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            plugin.getLogger().info("Автоматический спавн мобов остановлен");
        }
        if (keepOutTask != null) {
            keepOutTask.cancel();
        }
    }

    /**
     * Иммутабельная запись exclusion-зоны (цилиндр).
     */
    private static final class ExclusionZone {
        final String id;
        final String world;
        final double x;
        final double z;
        final double radius;

        ExclusionZone(String id, String world, double x, double z, double radius) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.z = z;
            this.radius = radius;
        }
    }
}
