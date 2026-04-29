package ru.eclipsia.builder.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import ru.eclipsia.builder.EclipsiaBuilder;

import java.util.*;

/**
 * Менеджер структур
 */
public class StructureManager {
    
    private final EclipsiaBuilder plugin;
    private final Map<String, Structure> structures;
    
    public StructureManager(EclipsiaBuilder plugin) {
        this.plugin = plugin;
        this.structures = new HashMap<>();
    }
    
    /**
     * Загрузить структуры из конфига
     */
    public void loadStructures() {
        structures.clear();
        
        ConfigurationSection structuresSection = plugin.getConfig().getConfigurationSection("structures");
        if (structuresSection == null) {
            plugin.getLogger().warning("Секция structures не найдена в конфиге!");
            return;
        }
        
        for (String id : structuresSection.getKeys(false)) {
            ConfigurationSection structureSection = structuresSection.getConfigurationSection(id);
            if (structureSection == null) continue;
            
            Structure structure = new Structure(
                id,
                structureSection.getString("world"),
                structureSection.getInt("x"),
                structureSection.getInt("y"),
                structureSection.getInt("z"),
                structureSection.getString("type", "CUSTOM")
            );
            
            structures.put(id, structure);
        }
        
        plugin.getLogger().info("Загружено структур: " + structures.size());
    }
    
    /**
     * Построить структуру по ID
     */
    public boolean buildStructure(String id) {
        Structure structure = structures.get(id);
        if (structure == null) return false;
        
        World world = Bukkit.getWorld(structure.worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир " + structure.worldName + " не найден!");
            return false;
        }
        
        Location loc = new Location(world, structure.x, structure.y, structure.z);
        
        // Генерируем структуру в зависимости от типа
        switch (structure.type.toUpperCase()) {
            case "SPAWN" -> buildSpawn(loc);
            case "BOSS_ARENA" -> buildBossArena(loc);
            case "HUB" -> buildHub(loc);
            case "CAMP" -> buildCamp(loc);
            case "DUNGEON" -> buildDungeon(loc);
            case "PORTAL" -> buildPortal(loc);
            default -> plugin.getLogger().warning("Неизвестный тип структуры: " + structure.type);
        }
        
        // Обрабатываем features (спавн мобов, NPC и т.д.)
        processFeatures(id, loc);
        
        return true;
    }
    
    /**
     * Обработать features структуры
     */
    private void processFeatures(String structureId, Location baseLoc) {
        ConfigurationSection structureSection = plugin.getConfig().getConfigurationSection("structures." + structureId);
        if (structureSection == null) return;
        
        ConfigurationSection featuresSection = structureSection.getConfigurationSection("features");
        if (featuresSection == null) return;
        
        // Проверяем есть ли зоны спавна мобов
        for (String key : featuresSection.getKeys(false)) {
            ConfigurationSection feature = featuresSection.getConfigurationSection(key);
            if (feature == null) continue;
            
            String type = feature.getString("type");
            if ("MOB_SPAWN_ZONE".equals(type)) {
                // Регистрируем зону спавна в EclipsiaMobs
                registerMobSpawnZone(structureId, baseLoc, feature);
            } else if ("BOSS_SPAWN".equals(type)) {
                // Регистрируем точку спавна босса
                registerBossSpawn(structureId, baseLoc, feature);
            } else if ("WORLDGUARD_BORDER".equals(type)) {
                // Создаём WorldGuard-регион с флагом entry=deny
                registerWorldGuardBorder(baseLoc, feature);
            }
        }
    }

    /**
     * Создать WorldGuard-регион с границами и флагом entry=deny на основе
     * declarative-описания в structures.yml.
     * <pre>
     * features:
     *   - type: WORLDGUARD_BORDER
     *     region_name: "beach_border"
     *     x_min: -50
     *     z_min: -50
     *     x_max: 100
     *     z_max: 100
     *     message: "§cВы не можете покинуть Берег."
     * </pre>
     */
    private void registerWorldGuardBorder(Location baseLoc, ConfigurationSection feature) {
        String worldName = baseLoc.getWorld().getName();
        String regionName = feature.getString("region_name");
        if (regionName == null || regionName.isEmpty()) {
            plugin.getLogger().warning("WORLDGUARD_BORDER: region_name не указан, пропускаю");
            return;
        }

        int xMin = feature.getInt("x_min", -50);
        int zMin = feature.getInt("z_min", -50);
        int xMax = feature.getInt("x_max", 100);
        int zMax = feature.getInt("z_max", 100);

        String message = feature.getString("message",
                "§cВы не можете покинуть эту зону.");

        createWorldGuardBorder(worldName, regionName, xMin, zMin, xMax, zMax, message);
    }
    
    /**
     * Зарегистрировать зону спавна мобов
     */
    private void registerMobSpawnZone(String structureId, Location baseLoc, ConfigurationSection feature) {
        // Проверяем что EclipsiaMobs загружен
        if (Bukkit.getPluginManager().getPlugin("EclipsiaMobs") == null) {
            plugin.getLogger().warning("EclipsiaMobs не загружен, зона спавна не создана");
            return;
        }
        
        try {
            int radius = feature.getInt("radius", 30);
            int level = feature.getInt("level", 1);
            List<String> mobTypeNames = feature.getStringList("mob_types");
            
            // Конвертируем названия в EntityType
            List<org.bukkit.entity.EntityType> mobTypes = new java.util.ArrayList<>();
            for (String typeName : mobTypeNames) {
                try {
                    mobTypes.add(org.bukkit.entity.EntityType.valueOf(typeName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Неизвестный тип моба: " + typeName);
                }
            }
            
            // Регистрируем через рефлексию
            Class<?> structureSpawnManagerClass = Class.forName("ru.eclipsia.mobs.spawn.StructureSpawnManager");
            Object manager = structureSpawnManagerClass.getMethod("getInstance").invoke(null);
            
            structureSpawnManagerClass.getMethod("registerSpawnZone", 
                String.class, Location.class, int.class, int.class, List.class)
                .invoke(manager, structureId, baseLoc, radius, level, mobTypes);
            
            plugin.getLogger().info("Зарегистрирована зона спавна мобов для " + structureId + 
                                   " (радиус: " + radius + ", уровень: " + level + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка регистрации зоны спавна: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Зарегистрировать точку спавна босса
     */
    private void registerBossSpawn(String structureId, Location baseLoc, ConfigurationSection feature) {
        String bossType = feature.getString("boss_type");
        int x = feature.getInt("x", 0);
        int y = feature.getInt("y", 0);
        int z = feature.getInt("z", 0);
        
        Location spawnLoc = new Location(baseLoc.getWorld(), x, y, z);
        
        plugin.getLogger().info("Зарегистрирована точка спавна босса " + bossType + 
                               " для " + structureId + " в " + x + "," + y + "," + z);
    }
    
    /**
     * Построить все структуры
     */
    public int buildAll() {
        int count = 0;
        for (String id : structures.keySet()) {
            if (buildStructure(id)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Получить список ID структур
     */
    public List<String> getStructureIds() {
        return new ArrayList<>(structures.keySet());
    }
    
    /**
     * Построить спавн (Берег) в стиле dark fantasy.
     * <p>
     * Палитра:
     *   - {@link Material#BLACKSTONE} — основной чёрный песок;
     *   - {@link Material#BASALT}/{@link Material#POLISHED_BLACKSTONE} —
     *     обломки и кольцо вокруг костра;
     *   - {@link Material#SOUL_CAMPFIRE} — лазурный огонь в центре;
     *   - {@link Material#AMETHYST_BLOCK} + {@link Material#AMETHYST_CLUSTER} —
     *     светящиеся фиолетовые кристаллы;
     *   - {@link Material#SOUL_WALL_TORCH} — фиолетовые факелы;
     *   - {@link Material#DARK_OAK_LOG} + {@link Material#AZALEA_LEAVES} —
     *     мёртвые скрюченные деревья;
     *   - {@link Material#WATER} — тёмное «море» к югу.
     */
    private void buildSpawn(Location loc) {
        World world = loc.getWorld();
        int cx = loc.getBlockX();
        int gy = loc.getBlockY();          // ground Y (y=4 для FLAT)
        int cz = loc.getBlockZ();
        int floorY = gy;
        int feetY = gy + 1;

        // 1) Земля: круг 25×25 из BLACKSTONE с вкраплениями BASALT и SOUL_SAND.
        java.util.Random rng = new java.util.Random(1337L);
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (dx * dx + dz * dz > 12 * 12) continue;
                Material floor;
                int r = rng.nextInt(100);
                if (r < 8) floor = Material.BASALT;
                else if (r < 12) floor = Material.SOUL_SAND;
                else if (r < 18) floor = Material.COBBLED_DEEPSLATE;
                else floor = Material.BLACKSTONE;
                world.getBlockAt(cx + dx, floorY, cz + dz).setType(floor);
                // Очистим воздух над землёй на 4 блока (на случай мусора).
                for (int dy = 1; dy <= 4; dy++) {
                    world.getBlockAt(cx + dx, floorY + dy, cz + dz).setType(Material.AIR);
                }
            }
        }

        // 2) Море на юге (Z >= 8): тёмная вода с торчащими камнями.
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = 8; dz <= 16; dz++) {
                if (dx * dx + dz * dz > 16 * 16) continue;
                world.getBlockAt(cx + dx, floorY, cz + dz).setType(Material.WATER);
                world.getBlockAt(cx + dx, floorY - 1, cz + dz).setType(Material.DEEPSLATE);
            }
        }
        // Чёрные камни торчат из воды.
        int[][] rocks = {{-5, 11}, {3, 13}, {-9, 14}, {7, 10}, {0, 15}};
        for (int[] r : rocks) {
            world.getBlockAt(cx + r[0], floorY + 1, cz + r[1]).setType(Material.COBBLED_DEEPSLATE);
            world.getBlockAt(cx + r[0], floorY + 2, cz + r[1]).setType(Material.MOSSY_COBBLESTONE);
        }

        // 3) Костёр в центре + кольцо камней вокруг.
        world.getBlockAt(cx, feetY, cz).setType(Material.SOUL_CAMPFIRE);
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}}) {
            world.getBlockAt(cx + d[0], feetY, cz + d[1]).setType(
                    rng.nextBoolean() ? Material.POLISHED_BLACKSTONE : Material.COBBLED_DEEPSLATE);
        }
        // 4 бревна вокруг костра, лежащих по сторонам света.
        world.getBlockAt(cx - 2, feetY, cz).setType(Material.DARK_OAK_LOG);
        world.getBlockAt(cx + 2, feetY, cz).setType(Material.DARK_OAK_LOG);
        world.getBlockAt(cx, feetY, cz - 2).setType(Material.DARK_OAK_LOG);

        // 4) Тренировочные манекены (4 шт): сено + резная тыква сверху.
        int[][] dummyOffsets = {{-4, -4}, {4, -4}, {-4, -1}, {4, -1}};
        for (int[] off : dummyOffsets) {
            int dxv = off[0], dzv = off[1];
            world.getBlockAt(cx + dxv, feetY, cz + dzv).setType(Material.HAY_BLOCK);
            world.getBlockAt(cx + dxv, feetY + 1, cz + dzv).setType(Material.CARVED_PUMPKIN);
        }

        // 5) Тотем возрождения: столб 4 блока + аметист сверху.
        int tx = cx - 6, tz = cz - 6;
        world.getBlockAt(tx, feetY, tz).setType(Material.POLISHED_BLACKSTONE);
        world.getBlockAt(tx, feetY + 1, tz).setType(Material.POLISHED_BLACKSTONE_WALL);
        world.getBlockAt(tx, feetY + 2, tz).setType(Material.POLISHED_BLACKSTONE_WALL);
        world.getBlockAt(tx, feetY + 3, tz).setType(Material.AMETHYST_BLOCK);
        world.getBlockAt(tx, feetY + 4, tz).setType(Material.AMETHYST_BLOCK);

        // 6) Лодка-обломок у воды (плашмя на боку).
        int bx = cx + 6, bz = cz + 7;
        for (int dx = 0; dx < 4; dx++) {
            world.getBlockAt(bx + dx, feetY, bz).setType(Material.DARK_OAK_PLANKS);
            world.getBlockAt(bx + dx, feetY + 1, bz).setType(Material.DARK_OAK_PLANKS);
        }
        world.getBlockAt(bx + 1, feetY + 2, bz).setType(Material.DARK_OAK_LOG);

        // 7) Мёртвые деревья (3 шт), скрюченные, без листвы.
        buildDeadTree(world, cx - 8, feetY, cz + 2, 5);
        buildDeadTree(world, cx + 9, feetY, cz - 5, 4);
        buildDeadTree(world, cx - 5, feetY, cz + 6, 6);

        // 8) Светящиеся фиолетовые кристаллы (друзы аметиста по периметру).
        int[][] crystalsAt = {{-9, -2}, {-7, -7}, {-2, -9}, {3, -8}, {8, -6},
                              {10, 0}, {9, 4}, {5, 6}, {-8, 5}, {-10, 1}};
        for (int[] c : crystalsAt) {
            world.getBlockAt(cx + c[0], floorY, cz + c[1]).setType(Material.AMETHYST_BLOCK);
            world.getBlockAt(cx + c[0], feetY, cz + c[1]).setType(Material.AMETHYST_CLUSTER);
        }

        // 9) Soul-факелы (фиолетовое пламя) на стенах тотема и манекенов.
        world.getBlockAt(tx, feetY + 5, tz).setType(Material.SOUL_TORCH);
        for (int[] off : dummyOffsets) {
            // факел рядом с манекеном
            world.getBlockAt(cx + off[0] + (off[0] > 0 ? 1 : -1), feetY, cz + off[1])
                    .setType(Material.SOUL_TORCH);
        }

        // 10) Сухие кусты в случайных местах.
        for (int i = 0; i < 12; i++) {
            int dx = rng.nextInt(21) - 10;
            int dz = rng.nextInt(21) - 10;
            if (dx * dx + dz * dz > 100) continue;
            if (dx == 0 && dz == 0) continue;
            org.bukkit.block.Block above = world.getBlockAt(cx + dx, feetY, cz + dz);
            if (above.getType() == Material.AIR) {
                above.setType(Material.DEAD_BUSH);
            }
        }

        // 11) Табличка-указатель.
        org.bukkit.block.Block sign = world.getBlockAt(cx + 5, feetY, cz - 6);
        sign.setType(Material.DARK_OAK_SIGN);
        if (sign.getState() instanceof org.bukkit.block.Sign s) {
            s.line(0, net.kyori.adventure.text.Component.text("§5§lБерег"));
            s.line(1, net.kyori.adventure.text.Component.text("§7Начало пути"));
            s.line(2, net.kyori.adventure.text.Component.text(""));
            s.line(3, net.kyori.adventure.text.Component.text("§8Изгнан, но не сломлен"));
            s.update();
        }

        plugin.getLogger().info("Построен спавн на Берегу в " + cx + ", " + gy + ", " + cz);
    }

    /**
     * Скрюченное мёртвое дерево из {@link Material#DARK_OAK_LOG}.
     * Без листвы, ветви расходятся в стороны.
     */
    private void buildDeadTree(World world, int x, int yBase, int z, int height) {
        // Главный ствол (с лёгким наклоном на 1 блок).
        for (int i = 0; i < height; i++) {
            int offset = (i >= height - 2) ? 1 : 0;
            world.getBlockAt(x + offset, yBase + i, z).setType(Material.DARK_OAK_LOG);
        }
        // 2-3 коротких ветви наверху.
        int top = yBase + height - 1;
        world.getBlockAt(x + 1, top, z + 1).setType(Material.DARK_OAK_LOG);
        world.getBlockAt(x - 1, top - 1, z).setType(Material.DARK_OAK_LOG);
        world.getBlockAt(x, top, z - 1).setType(Material.DARK_OAK_LOG);
        // Иногда ветка свисает.
        world.getBlockAt(x + 2, top, z + 1).setType(Material.DARK_OAK_LOG);
    }
    
    /**
     * Построить арену босса
     */
    private void buildBossArena(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Каменный пол 15x15
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE_BRICKS);
            }
        }
        
        // Столбы по углам
        for (int i = 0; i < 4; i++) {
            world.getBlockAt(x - 7, y + 1 + i, z - 7).setType(Material.STONE_BRICKS);
            world.getBlockAt(x + 7, y + 1 + i, z - 7).setType(Material.STONE_BRICKS);
            world.getBlockAt(x - 7, y + 1 + i, z + 7).setType(Material.STONE_BRICKS);
            world.getBlockAt(x + 7, y + 1 + i, z + 7).setType(Material.STONE_BRICKS);
        }
        
        // Алтарь в центре
        world.getBlockAt(x, y + 1, z).setType(Material.NETHER_BRICK_FENCE);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.NETHER_BRICK_FENCE);
        world.getBlockAt(x, y + 1, z + 1).setType(Material.NETHER_BRICK_FENCE);
        world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.NETHER_BRICK_FENCE);
        
        plugin.getLogger().info("Построена арена босса в " + x + ", " + y + ", " + z);
    }
    
    /**
     * Построить хаб (город)
     */
    private void buildHub(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Каменный пол 40x40
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE_BRICKS);
            }
        }
        
        // Фонтан в центре
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.WATER);
            }
        }
        
        // Кольцо вокруг фонтана
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.STONE_BRICK_STAIRS);
                }
            }
        }
        
        // Фонари
        world.getBlockAt(x - 5, y + 1, z).setType(Material.GLOWSTONE);
        world.getBlockAt(x + 5, y + 1, z).setType(Material.GLOWSTONE);
        world.getBlockAt(x, y + 1, z - 5).setType(Material.GLOWSTONE);
        world.getBlockAt(x, y + 1, z + 5).setType(Material.GLOWSTONE);
        
        plugin.getLogger().info("Построен хаб в " + x + ", " + y + ", " + z);
    }
    
    /**
     * Построить лагерь
     */
    private void buildCamp(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Костер
        world.getBlockAt(x, y + 1, z).setType(Material.CAMPFIRE);
        
        // Палатка (простая)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.WHITE_WOOL);
                }
            }
        }
        
        // Ящики
        world.getBlockAt(x + 3, y + 1, z).setType(Material.OAK_PLANKS);
        world.getBlockAt(x - 3, y + 1, z).setType(Material.OAK_PLANKS);
        
        plugin.getLogger().info("Построен лагерь в " + x + ", " + y + ", " + z);
    }
    
    /**
     * Построить подземелье
     */
    private void buildDungeon(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Каменный пол 20x20
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE_BRICKS);
            }
        }
        
        // Стены
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = 1; dy <= 5; dy++) {
                world.getBlockAt(x + dx, y + dy, z - 10).setType(Material.STONE_BRICKS);
                world.getBlockAt(x + dx, y + dy, z + 10).setType(Material.STONE_BRICKS);
            }
        }
        
        for (int dz = -10; dz <= 10; dz++) {
            for (int dy = 1; dy <= 5; dy++) {
                world.getBlockAt(x - 10, y + dy, z + dz).setType(Material.STONE_BRICKS);
                world.getBlockAt(x + 10, y + dy, z + dz).setType(Material.STONE_BRICKS);
            }
        }
        
        // Факелы
        world.getBlockAt(x - 8, y + 2, z - 8).setType(Material.TORCH);
        world.getBlockAt(x + 8, y + 2, z - 8).setType(Material.TORCH);
        world.getBlockAt(x - 8, y + 2, z + 8).setType(Material.TORCH);
        world.getBlockAt(x + 8, y + 2, z + 8).setType(Material.TORCH);
        
        plugin.getLogger().info("Построено подземелье в " + x + ", " + y + ", " + z);
    }
    
    /**
     * Построить портал
     */
    private void buildPortal(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Рамка портала из обсидиана
        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 1, y + dy, z).setType(Material.OBSIDIAN);
        }
        
        for (int dx = -1; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + dx, y + 4, z).setType(Material.OBSIDIAN);
        }
        
        // Портал (пурпурное стекло)
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.PURPLE_STAINED_GLASS);
        }
        
        plugin.getLogger().info("Построен портал в " + x + ", " + y + ", " + z);
    }
    
    /**
     * Создать невидимую стену вокруг региона через собственный
     * {@code WorldBorderListener} Eclipsia (без зависимости от WorldGuard).
     * Граница работает как прямоугольник в плоскости XZ: игрок,
     * пытающийся выйти из неё, телепортируется обратно и видит сообщение.
     */
    private void createWorldGuardBorder(String worldName, String regionName,
                                        int xMin, int zMin, int xMax, int zMax,
                                        String denyMessage) {
        plugin.getBorderListener().registerBorder(
                worldName, xMin, zMin, xMax, zMax, denyMessage);
        plugin.getLogger().info("Создана граница региона: " + regionName
                + " (" + worldName + ")");
    }
    
    /**
     * Класс структуры
     */
    private static class Structure {
        final String id;
        final String worldName;
        final int x, y, z;
        final String type;
        
        Structure(String id, String worldName, int x, int y, int z, String type) {
            this.id = id;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }
    }
}
