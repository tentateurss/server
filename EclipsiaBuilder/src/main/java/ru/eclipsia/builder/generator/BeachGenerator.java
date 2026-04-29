package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Процедурная генерация dark-fantasy локации «Берег» с глубоким океаном,
 * настоящим лесом, органичными летающими островами и амфитеатром босса.
 *
 * <p>Версия 2 («v2»). По сравнению с v1:
 * <ul>
 *   <li>Океан копается до y={@link BeachOcean#FLOOR_Y} (-21), бескрайний по
 *       краям мира; из воды торчат острые скалы-шпили.</li>
 *   <li>Деревья — настоящие, с листвой и корнями, 5 типов
 *       ({@link BeachTrees}).</li>
 *   <li>Летающие острова — каплевидные, с водопадами и руинами
 *       ({@link BeachIslands}).</li>
 *   <li>Лагерь, арена, проход в город — крупнее в ~1.7×.</li>
 *   <li>Дорога к боссу — синусоида в обход локации.</li>
 *   <li>Проход в Эликий — собор-коридор с арками и кристаллами.</li>
 *   <li>Граница мира — настоящая Bukkit WorldBorder + декоративная
 *       линия скал по периметру.</li>
 * </ul>
 */
public final class BeachGenerator {

    /** Версионный маркер: смена строки приводит к пересборке мира. */
    public static final String GENERATED_FLAG = "eclipsia_beach_generated_v10";

    /** Базовая высота платформ (камп, арена, тропа, мост, дорога). */
    public static final int GROUND_Y = 4;

    // ===== Координаты ключевых структур =====
    public static final int CAMP_X = 0, CAMP_Z = -55, CAMP_RADIUS = 22;
    public static final int ARENA_X = 0, ARENA_Z = 95, ARENA_RADIUS = 22;
    public static final int CAVE_ENTRANCE_Z = 120;
    public static final int CAVE_EXIT_Z = 165;
    public static final int ELIKIUM_ARCH_Z = 240;

    /** Регион ландшафтной генерации (включительно). Расширен против v1. */
    private static final int LAND_X_MIN = -180, LAND_X_MAX = 180;
    private static final int LAND_Z_MIN = -200, LAND_Z_MAX = 280;

    /** Радиус видимой Bukkit WorldBorder (диаметр = 2×). */
    private static final int WORLD_BORDER_RADIUS = 175;

    private final Plugin plugin;
    private final World world;
    private LandscapeGenerator landscape;

    public BeachGenerator(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public boolean isAlreadyGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    /** Сбросить флаг (для команды force-regen). */
    public void resetMarker() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().remove(key);
    }

    public void generate(Runnable onFinish) {
        if (isAlreadyGenerated()) {
            plugin.getLogger().info("BeachGenerator(v10): уже сгенерировано, пропуск.");
            applyWorldBorder();
            if (onFinish != null) onFinish.run();
            return;
        }

        plugin.getLogger().info("BeachGenerator(v10): начинаю генерацию '" + world.getName() + "'…");
        long seed = world.getSeed() ^ 0xBEACEC0DEL;
        landscape = new LandscapeGenerator(world, seed);
        RegionPainter p = new RegionPainter(plugin, world, seed);
        p.begin();
        Random rng = p.rng();

        // ===== A. Ландшафт + глубокий океан =====
        generateTerrain(p, rng);
        BeachOcean ocean = new BeachOcean(p, rng, seed);
        carveDeepOcean(p, ocean, rng);

        // ===== B. Лагерь =====
        paintCamp(p, rng);
        paintBeachDecorations(p, rng);
        paintBeachForestTransition(p, rng);

        // ===== C. Лес + извилистая дорога =====
        paintForest(p, rng);
        paintWindingRoad(p, rng);
        paintPerimeterPines(p, rng);
        paintBorderRocks(p, rng);
        paintPerimeterWall(p, rng);
        paintIslandBoundary(p, rng); // v5: сплошное ограждение по contour
        paintMountainDecor(p, rng);  // v6: мох/руины/жилы на голых горах
        paintSkyClouds(p, rng);

        // ===== D. Арена =====
        paintArena(p, rng);

        // ===== E. Грандиозный проход в город =====
        paintGrandPassage(p, rng);
        paintRoadToCity(p, rng);

        // ===== F. Летающие острова =====
        BeachIslands islands = new BeachIslands(p, rng, seed);
        paintFloatingArchipelago(p, islands, rng);

        // ===== G. Финал =====
        p.flush(() -> {
            postProcessSigns();
            applyWorldBorder();
            world.setSpawnLocation(CAMP_X, GROUND_Y + 2, CAMP_Z);
            markGenerated();
            plugin.getLogger().info("BeachGenerator(v10): Берег готов!");
            if (onFinish != null) onFinish.run();
        });
    }

    /** Установить настоящую Bukkit WorldBorder. */
    private void applyWorldBorder() {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 30); // центр между лагерем (-55) и ареной (95)
        border.setSize(WORLD_BORDER_RADIUS * 2);
        border.setDamageAmount(1.0);
        border.setDamageBuffer(2.0);
        border.setWarningDistance(8);
        border.setWarningTime(10);
    }

    // =========================================================================
    // A. ЛАНДШАФТ
    // =========================================================================

    private void generateTerrain(RegionPainter p, Random rng) {
        plugin.getLogger().info("BeachGenerator: рельеф (~"
                + ((LAND_X_MAX - LAND_X_MIN + 1) * (LAND_Z_MAX - LAND_Z_MIN + 1))
                + " столбов)");

        for (int x = LAND_X_MIN; x <= LAND_X_MAX; x++) {
            for (int z = LAND_Z_MIN; z <= LAND_Z_MAX; z++) {
                if (isStructureZone(x, z)) continue;
                LandscapeGenerator.Zone zone = landscape.getZone(x, z);
                if (zone == LandscapeGenerator.Zone.OCEAN) continue; // делаем в фазе carveDeepOcean

                int surface = landscape.getHeight(x, z);

                // Подповерхностный слой: землю поднять или опустить.
                if (surface > GROUND_Y) {
                    Material fill = (zone == LandscapeGenerator.Zone.MOUNTAIN)
                            ? Material.STONE : Material.DIRT;
                    for (int y = GROUND_Y + 1; y < surface; y++) {
                        if (zone != LandscapeGenerator.Zone.BEACH
                                && landscape.isCave(x, y, z)) {
                            p.place(x, y, z, Material.AIR);
                        } else {
                            p.place(x, y, z, fill);
                        }
                    }
                }

                // Очистить воздух выше поверхности (старая генерация / FLAT) —
                // фиксированная верхняя граница GROUND_Y+50 покрывает все
                // старые горы и деревья v1, но не достаёт до новых
                // летающих островов (они генерируются ПОСЛЕ — операции
                // в очереди приходят позже и кладут блоки поверх).
                int clearTop = Math.max(surface + 1, GROUND_Y + 50);
                for (int y = surface + 1; y <= clearTop; y++) {
                    p.place(x, y, z, Material.AIR);
                }
                // Поверхность
                p.place(x, surface, z, landscape.getSurfaceBlock(x, z));
            }
        }
    }

    /** Океан копаем глубоко и заполняем водой + добавляем скалы. */
    private void carveDeepOcean(RegionPainter p, BeachOcean ocean, Random rng) {
        plugin.getLogger().info("BeachGenerator: копаю океан (глубина "
                + (BeachOcean.WATER_Y - BeachOcean.FLOOR_Y) + " блоков)");
        // Пропустим столбы внутри structure-zone (мост, лагерь и т.п.).
        BeachOcean.OceanPredicate inOceanZone = (x, z) ->
                landscape.getZone(x, z) == LandscapeGenerator.Zone.OCEAN
                        && !isStructureZone(x, z);

        ocean.carveOcean(LAND_X_MIN, LAND_X_MAX, LAND_Z_MIN, LAND_Z_MAX, inOceanZone);

        // Скалы только в основном «играемом» прямоугольнике, чтобы не мусорить
        // далеко за горизонтом и подсветить визуально границу.
        ocean.scatterRockSpires(-160, 160, -180, 260, inOceanZone, 0.45);

        // Ещё больше скал в северном «открытом море» — для атмосферы из 1-го скрина.
        for (int i = 0; i < 60; i++) {
            int x = -150 + rng.nextInt(300);
            int z = -180 + rng.nextInt(60);
            if (inOceanZone.test(x, z)) {
                ocean.spire(x, z, 8 + rng.nextInt(11));
            }
        }
        // Затопленные корабли в северной глубине.
        int[][] wrecks = {{-80, -150}, {-20, -170}, {60, -160}, {120, -140}};
        for (int[] w : wrecks) {
            if (inOceanZone.test(w[0], w[1])) {
                ocean.shipwreck(w[0], w[1], rng.nextBoolean());
            }
        }
    }

    /**
     * Радиус структуры + slope. ВНИМАНИЕ: значения должны точно совпадать
     * с фактическим footprint paint-методов, иначе будут «дыры»
     * (см. v2 → v3 фикс).
     */
    private static final int CAMP_FOOTPRINT  = CAMP_RADIUS + 10;  // 32
    private static final int ARENA_FOOTPRINT = ARENA_RADIUS + 10; // 32
    private static final int ROAD_HALF_WIDTH = 6;                  // 13 wide
    /** Ширина плавного slope-кайма у платформ (camp/arena). */
    private static final int PLATFORM_SLOPE = 9;

    /**
     * Точка попадает в зону структуры? Эти столбы пропускаем в фазе ландшафта,
     * paint-методы потом сами выровняют (с плавной slope-каёмкой).
     */
    private boolean isStructureZone(int x, int z) {
        // Лагерь (включая slope-кайму)
        int dx = x - CAMP_X, dz = z - CAMP_Z;
        if (dx * dx + dz * dz <= CAMP_FOOTPRINT * CAMP_FOOTPRINT) return true;
        // Арена (включая slope)
        dx = x - ARENA_X; dz = z - ARENA_Z;
        if (dx * dx + dz * dz <= ARENA_FOOTPRINT * ARENA_FOOTPRINT) return true;
        // Извилистая дорога к арене (синусоида) — НЕ пропускаем теперь:
        // дорога рисуется ПОВЕРХ натурального рельефа, а не вырезается из него.
        // (см. paintWindingRoad в v3)
        // Подвод к арене с юга
        if (Math.abs(x) <= 4 && z >= 70 && z < ARENA_Z - ARENA_RADIUS) return true;
        // Мост/проход арена→пещера
        if (Math.abs(x) <= 6 && z >= 116 && z <= 124) return true;
        // Грандиозный проход в город (с фасадом — шире)
        if (Math.abs(x) <= 16 && z >= CAVE_ENTRANCE_Z - 4 && z <= CAVE_EXIT_Z + 4) return true;
        // Дорога к городу
        if (Math.abs(x) <= 8 && z >= CAVE_EXIT_Z + 1 && z <= ELIKIUM_ARCH_Z + 1) return true;
        return false;
    }

    /** Внутри дороги (не slope, а сама полоса)? Используется paintForest. */
    private boolean inRoadStrip(int x, int z) {
        if (z < CAMP_Z + CAMP_RADIUS - 4 || z > ARENA_Z - ARENA_RADIUS) return false;
        double centerX = Math.sin((z - 20) * 0.06) * 35.0;
        return Math.abs(x - centerX) <= ROAD_HALF_WIDTH;
    }

    // =========================================================================
    // B. ЛАГЕРЬ (расширенный)
    // =========================================================================

    private void paintCamp(RegionPainter p, Random rng) {
        int cx = CAMP_X, cz = CAMP_Z;
        int r = CAMP_RADIUS;

        // Платформа + широкий плавный slope-скирт.
        // core=23 + slope=9 = 32 = CAMP_FOOTPRINT.
        softFlattenDisk(p, cx, cz, r + 1, PLATFORM_SLOPE, GROUND_Y,
                Material.COBBLED_DEEPSLATE, Material.GRASS_BLOCK);

        // Пол: смешанный
        p.fillDisk(cx, GROUND_Y, cz, r, RegionPainter.weighted(rng,
                Material.COBBLED_DEEPSLATE, 50,
                Material.POLISHED_BLACKSTONE, 25,
                Material.DEEPSLATE_BRICKS, 15,
                Material.MOSSY_COBBLESTONE, 10));

        // Окружающий частокол с башнями на углах. 8-угольный, не квадрат.
        for (int i = 0; i < 64; i++) {
            double a = i * (Math.PI / 32);
            int wx = cx + (int) Math.round(Math.cos(a) * r);
            int wz = cz + (int) Math.round(Math.sin(a) * r);
            // вход на юге (z+r) — пропуск
            if (Math.abs(wx - cx) <= 3 && wz - cz >= r - 1) continue;
            p.column(wx, GROUND_Y + 1, wz, 4 + (i % 4 == 0 ? 1 : 0),
                    () -> Material.DARK_OAK_LOG);
        }
        // 4 башни по сторонам (NW, NE, SW, SE) высотой 8.
        int[][] towerOffsets = {{-r + 2, -r + 2}, {r - 2, -r + 2}, {-r + 2, r - 2}, {r - 2, r - 2}};
        for (int[] t : towerOffsets) {
            buildTower(p, cx + t[0], cz + t[1]);
        }

        // Главные ворота на юге.
        for (int dy = 1; dy <= 5; dy++) {
            p.place(cx - 4, GROUND_Y + dy, cz + r, Material.DARK_OAK_LOG);
            p.place(cx + 4, GROUND_Y + dy, cz + r, Material.DARK_OAK_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            p.place(cx + dx, GROUND_Y + 5, cz + r, Material.DARK_OAK_LOG);
            p.place(cx + dx, GROUND_Y + 6, cz + r, Material.DARK_OAK_PLANKS);
        }
        p.place(cx - 4, GROUND_Y + 5, cz + r, Material.SOUL_LANTERN);
        p.place(cx + 4, GROUND_Y + 5, cz + r, Material.SOUL_LANTERN);

        // === Центральный костёр + большой каменный круг ===
        p.fillDisk(cx, GROUND_Y, cz, 5, () -> Material.POLISHED_BLACKSTONE);
        p.place(cx, GROUND_Y + 1, cz, Material.SOUL_CAMPFIRE);
        // Камни вокруг
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            p.place(cx + d[0], GROUND_Y + 1, cz + d[1], Material.POLISHED_BLACKSTONE_WALL);
        }
        // Брёвна-лавки на 4-х сторонах
        for (int[] d : new int[][]{{-3,0},{3,0},{0,-3},{0,3}}) {
            p.place(cx + d[0], GROUND_Y + 1, cz + d[1], Material.OAK_LOG);
        }
        // Большой навес 9×9 на 6 столбах
        int[][] pillars = {{-4,-4},{4,-4},{-4,4},{4,4},{-4,0},{4,0}};
        for (int[] s : pillars) {
            p.column(cx + s[0], GROUND_Y + 1, cz + s[1], 6, () -> Material.DARK_OAK_LOG);
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    p.place(cx + dx, GROUND_Y + 7, cz + dz, Material.DARK_OAK_LOG);
                }
                p.place(cx + dx, GROUND_Y + 8, cz + dz, Material.DARK_OAK_PLANKS);
            }
        }
        // Подвесные фонари с навеса
        for (int[] lh : new int[][]{{-3,-3},{3,-3},{-3,3},{3,3},{0,0}}) {
            p.place(cx + lh[0], GROUND_Y + 6, cz + lh[1], Material.SOUL_LANTERN);
        }

        // === Большая палатка-таверна на западе ===
        int tX = cx - 14, tZ = cz - 4;
        buildTavern(p, tX, tZ);

        // v9: Манекены и тренировочная площадка УДАЛЕНЫ по запросу.
        // Голограммы про них тоже не спавнятся (см. postProcessSigns).

        // === Алтарь возрождения на севере (большой) ===
        int altX = cx, altZ = cz - 14;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.place(altX + dx, GROUND_Y + 1, altZ + dz, Material.POLISHED_BLACKSTONE);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(altX + dx, GROUND_Y + 2, altZ + dz, Material.POLISHED_BLACKSTONE);
            }
        }
        p.place(altX, GROUND_Y + 3, altZ, Material.AMETHYST_BLOCK);
        p.place(altX, GROUND_Y + 4, altZ, Material.AMETHYST_BLOCK);
        p.place(altX, GROUND_Y + 5, altZ, Material.AMETHYST_CLUSTER);
        // 4 опоры
        for (int[] o : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            p.column(altX + o[0], GROUND_Y + 2, altZ + o[1], 4, () -> Material.POLISHED_BLACKSTONE_WALL);
            p.place(altX + o[0], GROUND_Y + 6, altZ + o[1], Material.SOUL_LANTERN);
        }

        // === Склад-сарай ===
        int sX = cx + 10, sZ = cz + 6;
        buildStorageShed(p, sX, sZ);

        // === Указатель у выхода === (v7: текст вынесен в голограммы)
    }

    private void buildTower(RegionPainter p, int cx, int cz) {
        // 3×3 башня высотой 8 + крыша
        for (int dy = 1; dy <= 8; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                        p.place(cx + dx, GROUND_Y + dy, cz + dz, Material.DARK_OAK_LOG);
                    }
                }
            }
        }
        // Крыша
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.place(cx + dx, GROUND_Y + 9, cz + dz, Material.DARK_OAK_PLANKS);
            }
        }
        // Зубцы
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && (dx + dz) % 2 == 0) {
                    p.place(cx + dx, GROUND_Y + 10, cz + dz, Material.DARK_OAK_FENCE);
                }
            }
        }
        // Фонарь сверху
        p.place(cx, GROUND_Y + 10, cz, Material.SOUL_LANTERN);
    }

    private void buildTavern(RegionPainter p, int x, int z) {
        // 6×5 таверна-палатка с двускатной крышей
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 6; dz++) {
                p.place(x + dx, GROUND_Y + 1, z + dz, Material.SPRUCE_PLANKS);
            }
        }
        // Стены
        for (int dx = 0; dx < 7; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                Material m = ((dx + dy) % 4 == 0) ? Material.DARK_OAK_PLANKS : Material.GRAY_WOOL;
                p.place(x + dx, GROUND_Y + dy, z, m);
                p.place(x + dx, GROUND_Y + dy, z + 5, m);
            }
        }
        for (int dz = 0; dz < 6; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                p.place(x, GROUND_Y + dy, z + dz, Material.DARK_OAK_PLANKS);
                p.place(x + 6, GROUND_Y + dy, z + dz, Material.DARK_OAK_PLANKS);
            }
        }
        // Двускатная крыша
        for (int dz = 0; dz < 6; dz++) {
            for (int dx = 0; dx < 7; dx++) {
                int peak = 4 + (Math.min(dx, 6 - dx));
                p.place(x + dx, GROUND_Y + peak, z + dz, Material.DARK_OAK_LOG);
            }
        }
        // Дверь
        p.place(x + 3, GROUND_Y + 1, z + 5, Material.AIR);
        p.place(x + 3, GROUND_Y + 2, z + 5, Material.AIR);
        // Внутри
        p.place(x + 1, GROUND_Y + 1, z + 1, Material.RED_BED);
        p.place(x + 2, GROUND_Y + 1, z + 1, Material.RED_BED);
        p.place(x + 5, GROUND_Y + 1, z + 1, Material.CHEST);
        p.place(x + 5, GROUND_Y + 1, z + 2, Material.BARREL);
        p.place(x + 1, GROUND_Y + 1, z + 4, Material.CRAFTING_TABLE);
        p.place(x + 2, GROUND_Y + 1, z + 4, Material.SMOKER);
        p.place(x + 4, GROUND_Y + 1, z + 4, Material.CAULDRON);
        // Свет
        p.place(x + 1, GROUND_Y + 4, z + 1, Material.SOUL_LANTERN);
        p.place(x + 5, GROUND_Y + 4, z + 4, Material.SOUL_LANTERN);
    }

    private void buildStorageShed(RegionPainter p, int x, int z) {
        for (int[] pl : new int[][]{{0,0},{4,0},{0,3},{4,3}}) {
            p.column(x + pl[0], GROUND_Y + 1, z + pl[1], 3, () -> Material.SPRUCE_LOG);
        }
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                p.place(x + dx, GROUND_Y + 4, z + dz, Material.SPRUCE_PLANKS);
            }
        }
        // Сундуки и бочки
        for (int dx = 1; dx <= 3; dx++) {
            p.place(x + dx, GROUND_Y + 1, z + 1, Material.CHEST);
            p.place(x + dx, GROUND_Y + 1, z + 2, Material.BARREL);
        }
        // v7: табличка убрана — голограммы.
    }

    // =========================================================================
    // C. ЛЕС (с настоящими деревьями) + извилистая дорога
    // =========================================================================

    private void paintForest(RegionPainter p, Random rng) {
        // 320 деревьев 6 типов (плотнее лес = меньше «дыр» через лесной полог)
        int trees = 320;
        for (int i = 0; i < trees; i++) {
            int x = -110 + rng.nextInt(220);
            int z = -45 + rng.nextInt(160);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            if (isNearWindingRoad(x, z, 8)) continue;
            if (inCamp(x, z) || inArena(x, z)) continue;

            int gy = landscape.getHeight(x, z);
            int kind = rng.nextInt(100);
            if (kind < 25) {
                BeachTrees.twistedDead(p, rng, x, z, gy);
            } else if (kind < 45) {
                BeachTrees.bigDarkOak(p, rng, x, z, gy);
            } else if (kind < 60) {
                BeachTrees.ancientPine(p, rng, x, z, gy);
            } else if (kind < 75) {
                BeachTrees.willowTree(p, rng, x, z, gy);
            } else if (kind < 88) {
                BeachTrees.cherryBlossom(p, rng, x, z, gy);
            } else {
                BeachTrees.sacredOak(p, rng, x, z, gy);
            }
        }

        // Декорации (плотнее v3 + кусты, фидбэк v4: «больше кустиков»)
        spreadDecor(p, rng, 350, Material.DEAD_BUSH, 1);
        spreadDecor(p, rng, 200, Material.AMETHYST_CLUSTER, 1);
        spreadDecor(p, rng, 100, Material.BONE_BLOCK, 1);
        spreadDecor(p, rng, 150, Material.COBWEB, 1);
        spreadDecor(p, rng, 220, Material.RED_MUSHROOM, 1);
        spreadDecor(p, rng, 220, Material.BROWN_MUSHROOM, 1);
        // Большие гриб-блоки (giant mushrooms) — атмосфера тёмного леса
        for (int i = 0; i < 60; i++) {
            int x = -100 + rng.nextInt(200);
            int z = -40 + rng.nextInt(150);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            if (inCamp(x, z) || inArena(x, z) || isNearWindingRoad(x, z, 5)) continue;
            int gy = landscape.getHeight(x, z);
            buildGiantMushroom(p, rng, x, gy, z);
        }
        // Кусты — много разных типов:
        spreadDecor(p, rng, 250, Material.FERN, 1);
        spreadDecor(p, rng, 180, Material.LARGE_FERN, 1);
        spreadDecor(p, rng, 200, Material.AZALEA, 1);
        spreadDecor(p, rng, 80, Material.FLOWERING_AZALEA, 1);
        spreadDecor(p, rng, 120, Material.SWEET_BERRY_BUSH, 1);
        spreadDecor(p, rng, 100, Material.SHORT_GRASS, 1);
        spreadDecor(p, rng, 80, Material.TALL_GRASS, 1);
        spreadDecor(p, rng, 60, Material.PINK_PETALS, 1);
        spreadDecor(p, rng, 60, Material.LILY_OF_THE_VALLEY, 1);
        spreadDecor(p, rng, 40, Material.WITHER_ROSE, 1);
        // Атмосферное освещение: фонари + glow-berries в лесу.
        spreadDecor(p, rng, 130, Material.SOUL_LANTERN, 1);
        spreadDecor(p, rng, 80, Material.LANTERN, 1);
        spreadDecor(p, rng, 60, Material.JACK_O_LANTERN, 1);
        // Свечи на пнях (через placeData бы, но short_grass-метод просто
        // ставит блок поверх земли — оставим как есть).
        spreadDecor(p, rng, 50, Material.CANDLE, 1);
        spreadDecor(p, rng, 50, Material.SOUL_CAMPFIRE, 1);

        // Лужи
        for (int i = 0; i < 25; i++) {
            int x = -80 + rng.nextInt(160);
            int z = -40 + rng.nextInt(140);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            if (inCamp(x, z) || inArena(x, z) || isNearWindingRoad(x, z, 4)) continue;
            int gy = landscape.getHeight(x, z);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 4) {
                        p.place(x + dx, gy, z + dz, Material.WATER);
                    }
                }
            }
        }

        // Сломанные телеги
        int[][] carts = {{-35, -10}, {30, 30}, {-50, 50}, {45, 70}, {-60, 20}};
        for (int[] c : carts) {
            int gy = landscape.getHeight(c[0], c[1]);
            buildCart(p, c[0], c[1], gy);
        }
    }

    private void spreadDecor(RegionPainter p, Random rng, int count, Material mat, int yOffset) {
        for (int i = 0; i < count; i++) {
            int x = -110 + rng.nextInt(220);
            int z = -45 + rng.nextInt(160);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            if (inCamp(x, z) || inArena(x, z)) continue;
            if (isNearWindingRoad(x, z, 3)) continue;
            int gy = landscape.getHeight(x, z);
            p.place(x, gy + yOffset, z, mat);
        }
    }

    private void buildCart(RegionPainter p, int x, int z, int gy) {
        if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) return;
        p.place(x,     gy + 1, z,     Material.OAK_LOG);
        p.place(x + 1, gy + 1, z,     Material.OAK_PLANKS);
        p.place(x + 2, gy + 1, z,     Material.OAK_LOG);
        p.place(x,     gy + 2, z,     Material.OAK_FENCE);
        p.place(x + 2, gy + 2, z,     Material.OAK_FENCE);
        p.place(x + 1, gy + 1, z + 1, Material.BARREL);
    }

    /**
     * Плотный пояс высоких сосен по периметру локации — закрывает горизонт
     * и создаёт ощущение, что лес уходит в бесконечность.
     */
    private void paintPerimeterPines(RegionPainter p, Random rng) {
        int placed = 0;
        for (int attempt = 0; attempt < 1500 && placed < 350; attempt++) {
            int x = -130 + rng.nextInt(260);
            int z = -50 + rng.nextInt(170);
            // приоритет — близко к границе FOREST зоны
            double dCenterX = Math.abs(x);
            double dCenterZ = Math.abs(z - 30);
            // отбрасываем «середину» с вероятностью
            double bias = Math.max(dCenterX / 110.0, dCenterZ / 80.0);
            if (bias < 0.55 && rng.nextDouble() > bias) continue;

            LandscapeGenerator.Zone zone = landscape.getZone(x, z);
            if (zone != LandscapeGenerator.Zone.FOREST
                    && zone != LandscapeGenerator.Zone.MOUNTAIN) continue;
            if (isStructureZone(x, z)) continue;
            if (isNearWindingRoad(x, z, 8)) continue;

            int gy = landscape.getHeight(x, z);
            // Высокие сосны 18-28 блоков
            BeachTrees.giantPine(p, rng, x, z, gy);
            placed++;
        }
    }

    /**
     * Скальные выступы по краям локации (особенно на стыке forest/ocean
     * и в дальней горной зоне). Закрывают «обрыв в воздух».
     */
    private void paintBorderRocks(RegionPainter p, Random rng) {
        for (int i = 0; i < 200; i++) {
            int x = -150 + rng.nextInt(300);
            int z = -90 + rng.nextInt(220);
            LandscapeGenerator.Zone zone = landscape.getZone(x, z);
            if (zone == LandscapeGenerator.Zone.OCEAN) continue;
            if (isStructureZone(x, z)) continue;
            if (isNearWindingRoad(x, z, 6)) continue;
            // Биас к границам
            double dCenterX = Math.abs(x);
            double dCenterZ = Math.abs(z - 30);
            double bias = Math.max(dCenterX / 130.0, dCenterZ / 90.0);
            if (bias < 0.6) continue;

            int gy = landscape.getHeight(x, z);
            int rockH = 4 + rng.nextInt(8);
            int rockR = 1 + rng.nextInt(2);
            for (int dx = -rockR; dx <= rockR; dx++) {
                for (int dz = -rockR; dz <= rockR; dz++) {
                    if (dx * dx + dz * dz > rockR * rockR) continue;
                    int top = gy + rockH - (dx * dx + dz * dz);
                    for (int y = gy + 1; y <= top; y++) {
                        Material m = (rng.nextInt(4) == 0)
                                ? Material.MOSSY_COBBLESTONE
                                : Material.COBBLED_DEEPSLATE;
                        p.place(x + dx, y, z + dz, m);
                    }
                }
            }
            // Вершина — иногда аметист или кости
            if (rng.nextInt(5) == 0) {
                p.place(x, gy + rockH + 1, z, Material.AMETHYST_CLUSTER);
            } else if (rng.nextInt(8) == 0) {
                p.place(x, gy + rockH + 1, z, Material.BONE_BLOCK);
            }
        }
    }

    // =========================================================================
    // ПЛЯЖ — декорации (камни/скалы/обломки)
    // =========================================================================

    /** Пляж сейчас — пустой чёрный песок. Заполняем камнями/скалами/обломками. */
    private void paintBeachDecorations(RegionPainter p, Random rng) {
        // Скальные выступы по всему пляжу
        for (int i = 0; i < 220; i++) {
            int x = -120 + rng.nextInt(240);
            int z = -90 + rng.nextInt(45); // только пляжная зона
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.BEACH) continue;
            if (isStructureZone(x, z)) continue;
            int gy = landscape.getHeight(x, z);
            int rockH = 2 + rng.nextInt(6);
            int rockR = 1 + rng.nextInt(2);
            for (int dx = -rockR; dx <= rockR; dx++) {
                for (int dz = -rockR; dz <= rockR; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rockR * rockR) continue;
                    int top = gy + rockH - d2;
                    for (int y = gy + 1; y <= top; y++) {
                        Material m = switch (rng.nextInt(5)) {
                            case 0 -> Material.MOSSY_COBBLESTONE;
                            case 1 -> Material.COBBLED_DEEPSLATE;
                            case 2 -> Material.BLACKSTONE;
                            case 3 -> Material.STONE;
                            default -> Material.GRAVEL;
                        };
                        p.place(x + dx, y, z + dz, m);
                    }
                }
            }
            if (rng.nextInt(4) == 0) {
                p.place(x, gy + rockH + 1, z, Material.POINTED_DRIPSTONE);
            }
        }
        // Острые шпили
        for (int i = 0; i < 80; i++) {
            int x = -120 + rng.nextInt(240);
            int z = -90 + rng.nextInt(45);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.BEACH) continue;
            if (isStructureZone(x, z)) continue;
            int gy = landscape.getHeight(x, z);
            int h = 4 + rng.nextInt(6);
            for (int k = 1; k <= h; k++) {
                p.place(x, gy + k, z, Material.POINTED_DRIPSTONE);
            }
        }
        // Кости/скелеты-обломки на песке
        for (int i = 0; i < 40; i++) {
            int x = -120 + rng.nextInt(240);
            int z = -90 + rng.nextInt(45);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.BEACH) continue;
            if (isStructureZone(x, z)) continue;
            int gy = landscape.getHeight(x, z);
            p.place(x, gy + 1, z, Material.BONE_BLOCK);
            if (rng.nextBoolean()) p.place(x + 1, gy + 1, z, Material.BONE_BLOCK);
        }
        // Обломки кораблей (палубы из досок)
        int[][] wrecks = {{-70, -75}, {60, -80}, {-30, -85}};
        for (int[] w : wrecks) {
            int gy = landscape.getHeight(w[0], w[1]);
            buildWreck(p, rng, w[0], w[1], gy);
        }
        // Сухие кусты
        for (int i = 0; i < 60; i++) {
            int x = -120 + rng.nextInt(240);
            int z = -90 + rng.nextInt(45);
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.BEACH) continue;
            if (isStructureZone(x, z)) continue;
            int gy = landscape.getHeight(x, z);
            p.place(x, gy + 1, z, Material.DEAD_BUSH);
        }
    }

    private void buildWreck(RegionPainter p, Random rng, int x, int z, int gy) {
        // Палуба 5×3 + мачта-обломок
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (rng.nextInt(5) == 0) continue;
                p.place(x + dx, gy, z + dz, Material.DARK_OAK_PLANKS);
            }
        }
        // Мачта-обломок
        for (int k = 1; k <= 4; k++) {
            p.place(x, gy + k, z, Material.DARK_OAK_LOG);
        }
        p.place(x, gy + 5, z, Material.DARK_OAK_FENCE);
        p.place(x + 1, gy + 1, z, Material.BARREL);
    }

    /** Beach↔Forest промежуточная зона: mossy stone + редкие деревья на стыке. */
    private void paintBeachForestTransition(RegionPainter p, Random rng) {
        // z ≈ -55..-45 — пограничная полоса.
        for (int i = 0; i < 250; i++) {
            int x = -120 + rng.nextInt(240);
            int z = -55 + rng.nextInt(15);
            if (isStructureZone(x, z)) continue;
            LandscapeGenerator.Zone zone = landscape.getZone(x, z);
            if (zone != LandscapeGenerator.Zone.BEACH
                    && zone != LandscapeGenerator.Zone.FOREST) continue;
            int gy = landscape.getHeight(x, z);
            // Камни-валуны
            if (rng.nextInt(3) == 0) {
                int rockH = 2 + rng.nextInt(3);
                for (int k = 1; k <= rockH; k++) {
                    Material m = (rng.nextInt(2) == 0) ? Material.MOSSY_COBBLESTONE
                                                       : Material.COBBLESTONE;
                    p.place(x, gy + k, z, m);
                }
            } else if (rng.nextInt(4) == 0) {
                // Редкое дерево-сосна
                BeachTrees.giantPine(p, rng, x, z, gy);
            } else {
                // Кусты + папоротники
                Material plant = switch (rng.nextInt(4)) {
                    case 0 -> Material.AZALEA;
                    case 1 -> Material.LARGE_FERN;
                    case 2 -> Material.SHORT_GRASS;
                    default -> Material.DEAD_BUSH;
                };
                p.place(x, gy + 1, z, plant);
            }
        }
    }

    /**
     * Сплошное ограждение по контуру острова: сканируем всю карту,
     * для каждой клетки на стыке land/ocean ставим:
     *  - на BEACH: высокий валун (4-8 блоков)
     *  - на FOREST: ёлку или плотный куст
     * Игрок не должен видеть «край мира» из любой точки локации.
     */
    private void paintIslandBoundary(RegionPainter p, Random rng) {
        int placed = 0;
        // ШАГ 1 (v6: было 2 — слишком разреженно), checking 8 neighbours
        for (int x = -160; x <= 160; x++) {
            for (int z = -90; z <= 220; z++) {
                if (isStructureZone(x, z)) continue;
                LandscapeGenerator.Zone here = landscape.getZone(x, z);
                if (here == LandscapeGenerator.Zone.OCEAN) continue;
                // Проверяем 8 соседей на расстоянии 3 — гарантия плотного обхода
                boolean coast = false;
                for (int[] off : new int[][]{{3,0},{-3,0},{0,3},{0,-3},
                                              {3,3},{-3,3},{3,-3},{-3,-3}}) {
                    LandscapeGenerator.Zone zn = landscape.getZone(x + off[0], z + off[1]);
                    if (zn == LandscapeGenerator.Zone.OCEAN) { coast = true; break; }
                }
                if (!coast) continue;
                // Лёгкий пропуск только для разнообразия
                if (rng.nextInt(5) == 0) continue;

                int gy = landscape.getHeight(x, z);
                if (here == LandscapeGenerator.Zone.BEACH) {
                    // Высокий валун-камень (закрывает обзор за горизонт)
                    int rockH = 4 + rng.nextInt(5); // 4..8
                    int rockR = 1 + rng.nextInt(2);
                    for (int dx = -rockR; dx <= rockR; dx++) {
                        for (int dz = -rockR; dz <= rockR; dz++) {
                            int d2 = dx*dx + dz*dz;
                            if (d2 > rockR*rockR) continue;
                            int top = gy + rockH - d2;
                            for (int y = gy + 1; y <= top; y++) {
                                Material m = switch (rng.nextInt(5)) {
                                    case 0 -> Material.COBBLED_DEEPSLATE;
                                    case 1 -> Material.MOSSY_COBBLESTONE;
                                    case 2 -> Material.BLACKSTONE;
                                    case 3 -> Material.STONE;
                                    default -> Material.DEEPSLATE;
                                };
                                p.place(x + dx, y, z + dz, m);
                            }
                        }
                    }
                    if (rng.nextInt(3) == 0) {
                        p.place(x, gy + rockH + 1, z, Material.POINTED_DRIPSTONE);
                    }
                } else {
                    // FOREST / MOUNTAIN — высокие ёлки + кусты
                    int treeH = 12 + rng.nextInt(8);
                    // Крона
                    for (int dy = treeH - 6; dy <= treeH; dy++) {
                        int radius = (treeH - dy) + 1;
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (dx*dx + dz*dz > radius*radius + 1) continue;
                                if (rng.nextInt(8) == 0) continue;
                                p.place(x + dx, gy + dy, z + dz, Material.SPRUCE_LEAVES);
                            }
                        }
                    }
                    // Ствол
                    for (int dy = 0; dy <= treeH - 1; dy++) {
                        p.place(x, gy + 1 + dy, z, Material.SPRUCE_LOG);
                    }
                    // Кусты у основания
                    if (rng.nextInt(2) == 0) {
                        Material bush = switch (rng.nextInt(4)) {
                            case 0 -> Material.AZALEA;
                            case 1 -> Material.SWEET_BERRY_BUSH;
                            case 2 -> Material.LARGE_FERN;
                            default -> Material.FERN;
                        };
                        p.place(x + 1, gy + 1, z, bush);
                        p.place(x - 1, gy + 1, z, bush);
                    }
                }
                placed++;
            }
        }
    }

    private void paintPerimeterWall(RegionPainter p, Random rng) {
        // X-границы: |x| ≈ 130..150 + Z до 180.
        // Z-граница: z ≈ 180..190.
        for (int attempt = 0; attempt < 800; attempt++) {
            int x, z;
            if (rng.nextBoolean()) {
                // X-стенка (запад/восток)
                x = (rng.nextBoolean() ? -1 : 1) * (135 + rng.nextInt(15));
                z = -50 + rng.nextInt(230);
            } else {
                // Z-стенка (север)
                x = -150 + rng.nextInt(300);
                z = 175 + rng.nextInt(20);
            }
            if (isStructureZone(x, z)) continue;
            LandscapeGenerator.Zone zone = landscape.getZone(x, z);
            if (zone == LandscapeGenerator.Zone.OCEAN) continue;
            int gy = landscape.getHeight(x, z);
            // Огромная скала (8-16 блоков)
            int rockH = 8 + rng.nextInt(8);
            int rockR = 2 + rng.nextInt(2);
            for (int dx = -rockR; dx <= rockR; dx++) {
                for (int dz = -rockR; dz <= rockR; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rockR * rockR) continue;
                    int top = gy + rockH - d2 / 2;
                    for (int y = gy + 1; y <= top; y++) {
                        Material m = switch (rng.nextInt(4)) {
                            case 0 -> Material.MOSSY_COBBLESTONE;
                            case 1 -> Material.COBBLED_DEEPSLATE;
                            case 2 -> Material.STONE;
                            default -> Material.DEEPSLATE;
                        };
                        p.place(x + dx, y, z + dz, m);
                    }
                }
            }
        }
    }

    // =========================================================================
    // ОБЛАКА — искусственные облачные острова
    // =========================================================================

    /** Гигантский гриб 4-7 блоков высотой со шляпкой 5-7 в диаметре. */
    private void buildGiantMushroom(RegionPainter p, Random rng, int x, int gy, int z) {
        boolean red = rng.nextBoolean();
        int stemH = 4 + rng.nextInt(4);
        Material stem = Material.MUSHROOM_STEM;
        Material cap = red ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK;
        // Ствол
        for (int dy = 1; dy <= stemH; dy++) {
            p.place(x, gy + dy, z, stem);
        }
        // Шляпка — диск
        int capR = 2 + rng.nextInt(2);
        int capY = gy + stemH;
        for (int dx = -capR; dx <= capR; dx++) {
            for (int dz = -capR; dz <= capR; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > capR * capR) continue;
                if (d2 > (capR - 1) * (capR - 1)) {
                    // Боковинки шляпки
                    p.place(x + dx, capY, z + dz, cap);
                }
                p.place(x + dx, capY + 1, z + dz, cap);
            }
        }
        // Точка на верху
        p.place(x, capY + 2, z, cap);
    }

    // =========================================================================
    // ОФОРМЛЕНИЕ ГОР — мох, лианы, аметистовые жилы, руины
    // =========================================================================

    private void paintMountainDecor(RegionPainter p, Random rng) {
        // Сканируем mountain-зону, на каждые ~5 блоков заменяем верхний
        // блок камня на мох/мшистый булыжник, добавляем лианы, цветы.
        for (int x = -150; x <= 150; x += 2) {
            for (int z = 100; z <= 220; z += 2) {
                if (isStructureZone(x, z)) continue;
                LandscapeGenerator.Zone zone = landscape.getZone(x, z);
                if (zone != LandscapeGenerator.Zone.MOUNTAIN) continue;
                int gy = landscape.getHeight(x, z);
                if (gy < GROUND_Y + 2) continue;
                int r = rng.nextInt(10);
                if (r < 3) {
                    p.place(x, gy, z, Material.MOSS_BLOCK);
                    if (rng.nextInt(2) == 0) {
                        p.place(x, gy + 1, z, Material.MOSS_CARPET);
                    }
                } else if (r < 5) {
                    p.place(x, gy, z, Material.MOSSY_COBBLESTONE);
                } else if (r < 7) {
                    p.place(x, gy, z, Material.STONE);
                } else if (r == 7) {
                    // Аметистовая жила
                    p.place(x, gy, z, Material.AMETHYST_BLOCK);
                    if (rng.nextInt(2) == 0) {
                        p.place(x, gy + 1, z, Material.AMETHYST_CLUSTER);
                    }
                } else if (r == 8) {
                    // Лианы свисают со скалы
                    for (int dy = -1; dy >= -3 - rng.nextInt(3); dy--) {
                        p.place(x, gy + dy, z, Material.VINE);
                    }
                } else {
                    // Цветок / папоротник на камне
                    Material flora = switch (rng.nextInt(5)) {
                        case 0 -> Material.FERN;
                        case 1 -> Material.AZALEA;
                        case 2 -> Material.LILY_OF_THE_VALLEY;
                        case 3 -> Material.GLOW_LICHEN;
                        default -> Material.SHORT_GRASS;
                    };
                    p.place(x, gy + 1, z, flora);
                }
            }
        }
        // Декоративные руины на горах (старые колонны / разбитые арки)
        for (int i = 0; i < 18; i++) {
            int x = -120 + rng.nextInt(240);
            int z = 110 + rng.nextInt(100);
            if (isStructureZone(x, z)) continue;
            LandscapeGenerator.Zone zone = landscape.getZone(x, z);
            if (zone != LandscapeGenerator.Zone.MOUNTAIN) continue;
            int gy = landscape.getHeight(x, z);
            buildMountainRuin(p, rng, x, gy, z);
        }
    }

    private void buildMountainRuin(RegionPainter p, Random rng, int x, int gy, int z) {
        int kind = rng.nextInt(3);
        if (kind == 0) {
            // Кусок арки
            int h = 3 + rng.nextInt(3);
            for (int dy = 1; dy <= h; dy++) {
                p.place(x - 1, gy + dy, z, Material.MOSSY_COBBLESTONE);
                p.place(x + 1, gy + dy, z, Material.MOSSY_COBBLESTONE);
            }
            p.place(x, gy + h, z, Material.MOSSY_COBBLESTONE);
            if (rng.nextInt(2) == 0) {
                p.place(x - 1, gy + h, z, Material.AIR); // отвалившийся бок
            }
        } else if (kind == 1) {
            // Колонна / обломок
            int h = 4 + rng.nextInt(4);
            for (int dy = 1; dy <= h; dy++) {
                Material col = (dy <= h - 1)
                        ? Material.POLISHED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_WALL;
                p.place(x, gy + dy, z, col);
            }
            // Ломающийся верх
            if (rng.nextInt(2) == 0) {
                p.place(x + 1, gy + h - 2, z, Material.MOSSY_COBBLESTONE);
            }
        } else {
            // Разбитая каменная плита
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (rng.nextInt(3) == 0) continue;
                    p.place(x + dx, gy + 1, z + dz, Material.COBBLESTONE_SLAB);
                }
            }
            p.place(x, gy + 2, z, Material.AMETHYST_CLUSTER);
        }
    }

    private void paintSkyClouds(RegionPainter p, Random rng) {
        // ПАЛИТРА (фидбэк v5: убрать розовый/пурпур): тёмно-серый / серый /
        // тёмно-синий / синий / cyan. Только тёмные тона.
        Material[] cloudMats = {
            Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.GRAY_CONCRETE,
            Material.BLUE_WOOL, Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.GRAY_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.CYAN_TERRACOTTA
        };
        // Layer 1: ПЛОТНЫЙ облачный «потолок» y=78-95, всё небо закрыто.
        // Сетка 24×24 — гарантия что не будет «дырок» в небе.
        for (int gx = -180; gx <= 180; gx += 24) {
            for (int gz = -100; gz <= 260; gz += 24) {
                int cx = gx + rng.nextInt(20) - 10;
                int cz = gz + rng.nextInt(20) - 10;
                int cy = 80 + rng.nextInt(15); // 80..94
                int blobs = 3 + rng.nextInt(4);
                Material mainMat = cloudMats[rng.nextInt(cloudMats.length)];
                for (int b = 0; b < blobs; b++) {
                    int bx = cx + rng.nextInt(28) - 14;
                    int bz = cz + rng.nextInt(28) - 14;
                    int by = cy + rng.nextInt(6) - 3;
                    int rx = 7 + rng.nextInt(8);
                    int rz = 7 + rng.nextInt(8);
                    int ry = 2 + rng.nextInt(3);
                    int shape = rng.nextInt(4);
                    Material mat = (rng.nextInt(3) == 0)
                            ? cloudMats[rng.nextInt(cloudMats.length)]
                            : mainMat;
                    buildCloudShape(p, rng, bx, by, bz, rx, ry, rz, mat, shape);
                    // v8: МНОГО света — 5-8 источников на каждом блобе.
                    int lights = 5 + rng.nextInt(4);
                    for (int li = 0; li < lights; li++) {
                        Material lit = switch (rng.nextInt(5)) {
                            case 0 -> Material.GLOWSTONE;
                            case 1 -> Material.SHROOMLIGHT;
                            case 2 -> Material.OCHRE_FROGLIGHT;
                            case 3 -> Material.PEARLESCENT_FROGLIGHT;
                            default -> Material.SEA_LANTERN;
                        };
                        int lx = bx + rng.nextInt(Math.max(1, rx * 2)) - rx;
                        int ly = by + rng.nextInt(Math.max(1, ry * 2)) - ry;
                        int lz = bz + rng.nextInt(Math.max(1, rz * 2)) - rz;
                        p.place(lx, ly, lz, lit);
                    }
                    // v8: ВСЕГДА «дождь» из stained_glass под каждым блобом (не 50%, а всегда).
                    Material rainMat = switch (rng.nextInt(5)) {
                        case 0 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                        case 1 -> Material.BLUE_STAINED_GLASS_PANE;
                        case 2 -> Material.CYAN_STAINED_GLASS_PANE;
                        case 3 -> Material.WHITE_STAINED_GLASS_PANE;
                        default -> Material.GRAY_STAINED_GLASS_PANE;
                    };
                    int strands = 12 + rng.nextInt(10); // 12-22 «капли»
                    for (int rain = 0; rain < strands; rain++) {
                        int rxOff = rng.nextInt(rx * 2 + 1) - rx;
                        int rzOff = rng.nextInt(rz * 2 + 1) - rz;
                        int len = 2 + rng.nextInt(5); // длина струйки 2-6
                        for (int dl = 0; dl < len; dl++) {
                            p.place(bx + rxOff, by - ry - 1 - dl, bz + rzOff, rainMat);
                        }
                    }
                    // v8: Декоративные витражи по бокам ВСЕГДА (5-8 шт).
                    int decCount = 5 + rng.nextInt(4);
                    Material[] glassMats = {
                        Material.LIGHT_BLUE_STAINED_GLASS,
                        Material.BLUE_STAINED_GLASS,
                        Material.CYAN_STAINED_GLASS,
                        Material.WHITE_STAINED_GLASS,
                        Material.LIGHT_GRAY_STAINED_GLASS
                    };
                    for (int dec = 0; dec < decCount; dec++) {
                        int sideX = (rng.nextBoolean() ? 1 : -1) * rx;
                        int sideZ = rng.nextInt(rz * 2 + 1) - rz;
                        int hy = by + rng.nextInt(ry * 2 + 1) - ry;
                        p.place(bx + sideX, hy, bz + sideZ,
                                glassMats[rng.nextInt(glassMats.length)]);
                    }
                }
            }
        }
        // Layer 2: высокий «свод» y=105-115 — большие тёмные пятна, редкие.
        for (int i = 0; i < 90; i++) {
            int cx = -180 + rng.nextInt(360);
            int cz = -100 + rng.nextInt(360);
            int cy = 105 + rng.nextInt(12);
            int rx = 10 + rng.nextInt(10);
            int rz = 10 + rng.nextInt(10);
            int ry = 1 + rng.nextInt(2);
            Material mat = cloudMats[rng.nextInt(cloudMats.length)];
            buildCloudShape(p, rng, cx, cy, cz, rx, ry, rz, mat, rng.nextInt(4));
        }
        // Layer 3: низкий «туман» y=68-72 над морем, разорванный.
        for (int i = 0; i < 40; i++) {
            int cx = -180 + rng.nextInt(360);
            int cz = -120 + rng.nextInt(80);
            int cy = 68 + rng.nextInt(5);
            int rx = 5 + rng.nextInt(6);
            int rz = 5 + rng.nextInt(6);
            int ry = 1;
            Material mat = (rng.nextInt(2) == 0)
                    ? Material.LIGHT_GRAY_WOOL
                    : Material.GRAY_WOOL;
            buildCloudShape(p, rng, cx, cy, cz, rx, ry, rz, mat, 1);
        }
    }

    /** Облако с разной формой: 0=эллипсоид, 1=плоское, 2=каплевидное, 3=зубчатое. */
    private void buildCloudShape(RegionPainter p, Random rng, int cx, int cy, int cz,
                                 int rx, int ry, int rz, Material mat, int shape) {
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    double nx = (double) dx / rx;
                    double ny = (double) dy / ry;
                    double nz = (double) dz / rz;
                    boolean inShape;
                    switch (shape) {
                        case 1: { // плоское (плита) — тонкое, широкое
                            double e = nx * nx + nz * nz;
                            inShape = e <= 1.0 && Math.abs(dy) <= ry / 2 + 1;
                            break;
                        }
                        case 2: { // каплевидное — широкое сверху, узкое снизу
                            double widthFactor = 1.0 + 0.5 * ny;
                            double e = (nx * nx + nz * nz) / (widthFactor * widthFactor);
                            inShape = e <= 1.0 && ny >= -1.0;
                            break;
                        }
                        case 3: { // зубчатое — эллипс с крупным шумом по контуру
                            double e = nx * nx + ny * ny + nz * nz;
                            double bump = (rng.nextInt(100) - 50) / 200.0;
                            inShape = e <= 1.0 + bump;
                            break;
                        }
                        default: { // эллипсоид
                            double e = nx * nx + ny * ny + nz * nz;
                            inShape = e <= 1.0;
                        }
                    }
                    if (!inShape) continue;
                    // Поверхность рваная
                    double edge = nx * nx + ny * ny + nz * nz;
                    if (edge > 0.7 && rng.nextInt(4) == 0) continue;
                    p.place(cx + dx, cy + dy, cz + dz, mat);
                }
            }
        }
    }

    /**
     * Извилистая дорога от выхода лагеря (z=CAMP_Z+CAMP_RADIUS) к арене.
     * Сначала уходит на восток, потом обратно через лес, потом к арене.
     * Используется sin(z) для центральной кривой + дополнительные обходы.
     */
    private void paintWindingRoad(RegionPainter p, Random rng) {
        int z1 = CAMP_Z + CAMP_RADIUS;       // -33
        // v7: тянем дорогу ВПЛОТНУЮ к арене (до её внешнего радиуса),
        // больше нет «обрезки» дорожки перед платформой.
        int z2 = ARENA_Z - ARENA_RADIUS - 1; //  72
        final int arenaFloorY = GROUND_Y + 8;

        // Дорога ИДЁТ ПОВЕРХ натурального рельефа.
        // Концы прижимаются: south-конец к GROUND_Y (камп), north-конец к arenaFloorY.
        // ПЛАВНОСТЬ: лимит изменения pathY ±1 блок на z (фидбэк v3: «дорожка
        // резко вниз уходит»).
        double pathY = GROUND_Y;
        for (int z = z1; z <= z2; z++) {
            double centerX = Math.sin((z - 20) * 0.06) * 35.0;
            int cx = (int) Math.round(centerX);
            int natCenter = landscape.getHeight(cx, z);
            // Плавное приближение к forest, но не больше ±1 блок/z
            double targetUnclamped = pathY + (natCenter - pathY) * 0.3;
            double delta = targetUnclamped - pathY;
            if (delta > 1.0) delta = 1.0;
            if (delta < -1.0) delta = -1.0;
            pathY += delta;
            // Прижатие к концам
            int dStart = z - z1, dEnd = z2 - z;
            if (dStart < 8) pathY = GROUND_Y + (pathY - GROUND_Y) * dStart / 8.0;
            if (dEnd   < 8) {
                // Конец — поднимаемся к арене на y=12.
                double t = dEnd / 8.0;
                pathY = arenaFloorY + (pathY - arenaFloorY) * t;
            }
            int targetY = (int) Math.round(pathY);

            // Полотно дороги: ширина 7 (dx=-3..3) — ДЕРЕВЯННЫЕ ДОСКИ
            // (фидбэк v3: «дорожка квадратная, надо дерево»)
            for (int dx = -3; dx <= 3; dx++) {
                int x = cx + dx;
                int nat = landscape.getHeight(x, z);
                int top = Math.max(targetY + 6, nat + 4);
                for (int y = targetY + 1; y <= top; y++) {
                    p.place(x, y, z, Material.AIR);
                }
                int bottom = Math.min(nat, targetY) - 2;
                for (int y = bottom; y < targetY; y++) {
                    p.place(x, y, z, Material.DIRT);
                }
                // Узор «доски + центральная дорожка»
                Material surf;
                if (Math.abs(dx) <= 1) surf = Material.OAK_PLANKS;
                else if (Math.abs(dx) == 2) surf = Material.SPRUCE_PLANKS;
                else surf = Material.DARK_OAK_PLANKS;
                p.place(x, targetY, z, surf);
            }
            // Полублоки по краям дороги — мягкий бордюр (фидбэк v3)
            for (int side = -1; side <= 1; side += 2) {
                int x = cx + side * 4;
                int nat = landscape.getHeight(x, z);
                int top = Math.max(targetY + 6, nat + 4);
                for (int y = targetY + 1; y <= top; y++) {
                    p.place(x, y, z, Material.AIR);
                }
                for (int y = Math.min(nat, targetY) - 1; y < targetY; y++) {
                    p.place(x, y, z, Material.DIRT);
                }
                p.place(x, targetY, z, Material.COBBLED_DEEPSLATE_SLAB);
            }
            // Slope-кайма (dx=±5..±7): плавный спуск к лесу.
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 5; k <= 7; k++) {
                    int x = cx + side * k;
                    int nat = landscape.getHeight(x, z);
                    double t = (k - 4) / 4.0; // 0.25..0.75
                    int colY = (int) Math.round(targetY * (1 - t) + nat * t);
                    // v7: убрал GRASS_BLOCK на самом краю — теперь slope
                    // полностью каменный, плавно сходит в forest без резкой смены.
                    boolean nearArena = (z2 - z) < 10;
                    Material surf = nearArena
                            ? ((k == 5) ? Material.STONE_BRICKS
                              : (k == 6) ? Material.MOSSY_STONE_BRICKS
                                         : Material.MOSSY_COBBLESTONE)
                            : ((k == 5) ? Material.MOSSY_COBBLESTONE
                              : (k == 6) ? Material.COBBLESTONE
                                         : Material.MOSS_BLOCK);
                    int top = Math.max(targetY + 6, nat + 4);
                    for (int y = colY + 1; y <= top; y++) {
                        p.place(x, y, z, Material.AIR);
                    }
                    for (int y = Math.min(nat, colY) - 1; y < colY; y++) {
                        p.place(x, y, z, Material.DIRT);
                    }
                    p.place(x, colY, z, surf);
                }
            }
            // Фонари каждые 8 z
            if ((z - z1) % 8 == 0) {
                p.column(cx - 4, targetY + 1, z, 3, () -> Material.OAK_FENCE);
                p.place (cx - 4, targetY + 4, z, Material.LANTERN);
                p.column(cx + 4, targetY + 1, z, 3, () -> Material.OAK_FENCE);
                p.place (cx + 4, targetY + 4, z, Material.LANTERN);
            }
        }
    }

    /** Точка ближе чем threshold к извилистой дороге? */
    private boolean isNearWindingRoad(int x, int z, int threshold) {
        if (z < CAMP_Z + CAMP_RADIUS - 4 || z > ARENA_Z - ARENA_RADIUS) return false;
        double centerX = Math.sin((z - 20) * 0.06) * 35.0;
        return Math.abs(x - centerX) <= threshold;
    }

    // =========================================================================
    // D. АРЕНА (амфитеатр)
    // =========================================================================

    private void paintArena(RegionPainter p, Random rng) {
        int ax = ARENA_X, az = ARENA_Z;
        int r = ARENA_RADIUS;
        // Поднимаем пол арены НАД натуральным forest-уровнем —
        // фидбэк v3: «арена вырезана в земле».
        final int floorY = GROUND_Y + 8; // = 12, выше среднего leса

        // Платформа: подножие из stone (всё, что между GROUND_Y и floorY) +
        // плавный slope-скирт от floorY вниз к натуральному рельефу.
        // v7: slope вокруг арены — STONE_BRICKS / MOSSY_STONE_BRICKS (был
        // GRASS_BLOCK), чтобы лес не обрывался резко перед въездом.
        softFlattenDisk(p, ax, az, r + 1, PLATFORM_SLOPE, floorY,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS);
        // Дополнительная очистка воздуха над ареной для трибун/колонн.
        p.clearAir(ax - r - 1, floorY + 1, az - r - 1,
                   ax + r + 1, floorY + 20, az + r + 1);

        // Концентрические кольца разных текстур
        p.fillDisk(ax, floorY, az, r, () -> Material.STONE_BRICKS);
        p.fillRing(ax, floorY, az, 6, 7, () -> Material.CRACKED_STONE_BRICKS);
        p.fillRing(ax, floorY, az, 11, 12, () -> Material.MOSSY_STONE_BRICKS);
        p.fillRing(ax, floorY, az, 16, 17, () -> Material.CRACKED_STONE_BRICKS);

        // Центр — большой алтарь из аметиста (ступенчатый)
        for (int step = 0; step < 3; step++) {
            int sr = 4 - step;
            for (int dx = -sr; dx <= sr; dx++) {
                for (int dz = -sr; dz <= sr; dz++) {
                    if (dx * dx + dz * dz > sr * sr) continue;
                    Material m = (step == 2) ? Material.AMETHYST_BLOCK : Material.POLISHED_BLACKSTONE;
                    p.place(ax + dx, floorY + 1 + step, az + dz, m);
                }
            }
        }
        p.place(ax, floorY + 4, az, Material.AMETHYST_CLUSTER);

        // Амфитеатр: ступенчатые трибуны на 3 уровнях.
        // ВАЖНО: вырезаем 5-блочный коридор-проход с юга (вход с дороги)
        // и севера (к воротам босса), чтобы можно было пройти насквозь.
        for (int level = 0; level < 4; level++) {
            int rIn = r - 4 - level * 3;
            int rOut = rIn + 2;
            int yLevel = floorY + 2 + level * 2;
            for (int dx = -rOut; dx <= rOut; dx++) {
                for (int dz = -rOut; dz <= rOut; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 < rIn * rIn || d2 > rOut * rOut) continue;
                    // ПРОХОДЫ: 5-блочные туннели с юга (dz<0,|dx|≤2) и севера (dz>0,|dx|≤2)
                    if (Math.abs(dx) <= 2) continue;
                    Material seat = (level % 2 == 0) ? Material.POLISHED_BLACKSTONE
                                                     : Material.POLISHED_BLACKSTONE_BRICKS;
                    p.place(ax + dx, yLevel, az + dz, seat);
                    if (level < 3) {
                        p.place(ax + dx, yLevel + 1, az + dz, seat);
                    }
                }
            }
        }
        // Полы проходов через трибуны — мощёные.
        for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dz) < 5) continue; // центр не трогаем (там алтарь)
            for (int dx = -2; dx <= 2; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                p.place(ax + dx, floorY, az + dz, Material.POLISHED_BLACKSTONE);
                // Воздух выше пола в коридоре
                for (int dy = 1; dy <= 8; dy++) {
                    p.place(ax + dx, floorY + dy, az + dz, Material.AIR);
                }
            }
        }
        // Декор по краям проходов: SOUL_LANTERN на колоннах через каждые 4 z.
        for (int dz = -r + 2; dz <= r - 2; dz += 4) {
            if (Math.abs(dz) < 6) continue;
            p.place(ax - 3, floorY + 1, az + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(ax - 3, floorY + 2, az + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(ax - 3, floorY + 3, az + dz, Material.SOUL_LANTERN);
            p.place(ax + 3, floorY + 1, az + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(ax + 3, floorY + 2, az + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(ax + 3, floorY + 3, az + dz, Material.SOUL_LANTERN);
        }

        // 12 колонн по периметру
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI / 6);
            int sx = ax + (int) Math.round(Math.cos(a) * (r - 3));
            int sz = az + (int) Math.round(Math.sin(a) * (r - 3));
            // Большая колонна 2×2
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    p.column(sx + dx, floorY + 1, sz + dz, 12,
                            () -> Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            p.place(sx, floorY + 13, sz, Material.AMETHYST_BLOCK);
            p.place(sx, floorY + 14, sz, Material.AMETHYST_CLUSTER);
        }

        // 4 жаровни на диагоналях (между трибунами и центром)
        for (double angDeg : new double[]{45, 135, 225, 315}) {
            double a = Math.toRadians(angDeg);
            int sx = ax + (int) Math.round(Math.cos(a) * 5);
            int sz = az + (int) Math.round(Math.sin(a) * 5);
            p.place(sx, floorY + 1, sz, Material.SOUL_CAMPFIRE);
            // Подставка
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    p.place(sx + dx, floorY, sz + dz, Material.POLISHED_BLACKSTONE);
                }
            }
        }

        // Ворота на север (выход) — арка из полированного блекстоуна
        // с аметистом наверху (БЕЗ центрального столба — фидбэк v3).
        int gateZ = az + r + 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                // Сделать арку: внутри 5×4 — воздух, по краям и сверху — обсидиан
                boolean edge = (Math.abs(dx) == 3) || (dy == 6);
                if (edge) {
                    p.place(ax + dx, floorY + dy, gateZ, Material.OBSIDIAN);
                } else {
                    p.place(ax + dx, floorY + dy, gateZ, Material.AIR);
                }
            }
        }
        // Замковый камень арки — БЕЗ central amethyst (раньше торчал
        // как «столб» по фидбэку v4). Декор сместим в стороны.
        p.place(ax - 2, floorY + 7, gateZ, Material.AMETHYST_BLOCK);
        p.place(ax + 2, floorY + 7, gateZ, Material.AMETHYST_BLOCK);
        // Колонны рядом
        p.column(ax - 4, floorY + 1, gateZ, 8, () -> Material.POLISHED_BLACKSTONE_BRICKS);
        p.column(ax + 4, floorY + 1, gateZ, 8, () -> Material.POLISHED_BLACKSTONE_BRICKS);
        p.place(ax - 4, floorY + 9, gateZ, Material.SOUL_LANTERN);
        p.place(ax + 4, floorY + 9, gateZ, Material.SOUL_LANTERN);

        // Цепи от арены к высокому небесному острову (визуальная связь)
        for (double angDeg : new double[]{0, 90, 180, 270}) {
            double a = Math.toRadians(angDeg);
            int sx = ax + (int) Math.round(Math.cos(a) * (r - 2));
            int sz = az + (int) Math.round(Math.sin(a) * (r - 2));
            for (int k = 1; k <= 35; k++) {
                p.place(sx, floorY + 12 + k, sz, Material.CHAIN);
            }
        }

        // Кости и руины на периферии
        for (int i = 0; i < 6; i++) {
            double a = rng.nextDouble() * 2 * Math.PI;
            int sx = ax + (int) Math.round(Math.cos(a) * (r - 5));
            int sz = az + (int) Math.round(Math.sin(a) * (r - 5));
            p.place(sx, floorY + 1, sz, Material.BONE_BLOCK);
            if (rng.nextBoolean()) {
                p.place(sx, floorY + 2, sz, Material.BONE_BLOCK);
            }
        }
        // Никаких столбов в южном проходе — фидбэк v3: «посреди прохода
        // стоит столб». Знак убран.
    }

    // =========================================================================
    // E. ГРАНДИОЗНЫЙ ПРОХОД (собор-коридор)
    // =========================================================================

    private void paintGrandPassage(RegionPainter p, Random rng) {
        int z1 = CAVE_ENTRANCE_Z;
        int z2 = CAVE_EXIT_Z;
        // Получаемся бесшовно от арены: проход и роад-то-сити идут на floorY=12.
        final int floorY = GROUND_Y + 8;

        // Подвод-мост от арены к проходу (всё на floorY)
        flattenStrip(p, -5, 117, 5, z1 - 1, floorY, Material.STONE_BRICKS);
        for (int z = 117; z < z1; z++) {
            for (int dx = -4; dx <= 4; dx++) {
                p.place(dx, floorY + 1, z, Material.STONE_BRICKS);
            }
            p.place(-5, floorY + 1, z, Material.STONE_BRICK_WALL);
            p.place(+5, floorY + 1, z, Material.STONE_BRICK_WALL);
        }

        // Платформа коридора шириной 17, выровнять
        flattenStrip(p, -8, z1, 8, z2, floorY, Material.POLISHED_BLACKSTONE);
        // Очистить высоту БОЛЬШЕ (фидбэк v3: в коридоре «столб» — реформа
        // из неочищенной горы). Чистим до y=floorY+40.
        p.clearAir(-9, floorY + 1, z1, 9, floorY + 40, z2);

        // Пол: красивая мозаика (центральная дорожка + узор)
        for (int z = z1; z <= z2; z++) {
            for (int dx = -8; dx <= 8; dx++) {
                Material floor;
                if (Math.abs(dx) <= 1) floor = Material.SMOOTH_QUARTZ;
                else if (Math.abs(dx) <= 4) floor = Material.POLISHED_BLACKSTONE;
                else floor = Material.POLISHED_DEEPSLATE;
                p.place(dx, floorY, z, floor);
            }
            // Аметистовые «руны» каждые 5 блоков
            if ((z - z1) % 5 == 0) {
                p.place(0, floorY, z, Material.AMETHYST_BLOCK);
            }
        }

        // Стены коридора
        for (int z = z1; z <= z2; z++) {
            for (int dy = 1; dy <= 12; dy++) {
                Material wall = (dy <= 4) ? Material.DEEPSLATE_BRICKS
                              : (dy <= 8) ? Material.POLISHED_DEEPSLATE
                              : Material.POLISHED_BLACKSTONE_BRICKS;
                p.place(-9, floorY + dy, z, wall);
                p.place(+9, floorY + dy, z, wall);
            }
        }

        // Готические колонны каждые 6 блоков с обеих сторон
        for (int z = z1 + 3; z <= z2 - 3; z += 6) {
            buildCathedralColumn(p, -7, z, floorY);
            buildCathedralColumn(p, +7, z, floorY);
            // Аметистовые «витражи» между колоннами
            for (int dy = 4; dy <= 8; dy++) {
                p.place(-9, floorY + dy, z + 2, Material.PURPLE_STAINED_GLASS);
                p.place(+9, floorY + dy, z + 2, Material.PURPLE_STAINED_GLASS);
                p.place(-9, floorY + dy, z + 3, Material.PURPLE_STAINED_GLASS);
                p.place(+9, floorY + dy, z + 3, Material.PURPLE_STAINED_GLASS);
            }
        }

        // Арочный свод (cosin curve)
        for (int z = z1; z <= z2; z++) {
            // Арка переменной высоты
            for (int dx = -8; dx <= 8; dx++) {
                int peak = 13 + (int) Math.round(Math.cos(dx * Math.PI / 16) * 2);
                p.place(dx, floorY + peak, z, Material.POLISHED_BLACKSTONE);
            }
            // Кессонный потолок
            if ((z - z1) % 2 == 0) {
                for (int dx = -7; dx <= 7; dx += 2) {
                    p.place(dx, floorY + 12, z, Material.SMOOTH_QUARTZ);
                }
            }
        }

        // Свисающие лампады по центру каждые 4 блока
        for (int z = z1 + 2; z <= z2; z += 4) {
            for (int k = 1; k <= 3; k++) {
                p.place(0, floorY + 12 - k, z, Material.CHAIN);
            }
            p.place(0, floorY + 9, z, Material.SOUL_LANTERN);
        }

        // Боковые ниши (статуи) каждые 12 блоков
        for (int z = z1 + 6; z <= z2 - 6; z += 12) {
            buildStatueNiche(p, -8, z, floorY);
            buildStatueNiche(p, +8, z, floorY);
        }

        // Грандиозная арка-вход + ФАСАД (скальный обрыв вокруг)
        buildGrandEntry(p, z1 - 1, true, floorY);
        buildGrandEntry(p, z2 + 1, false, floorY);
        buildPassageFacade(p, rng, z1 - 1, true, floorY);
        buildPassageFacade(p, rng, z2 + 1, false, floorY);

        // ПОРТАЛ В ЭЛИКИЙ — у ВЫХОДА из прохода (north-сторона),
        // огорожен чёрной шерстью «как будто прохода нет» (фидбэк v4).
        // Wall ставим на z2-10, комната занимает z2-9..z2 (внутри коридора).
        buildPostBossPortal(p, z2 - 10, floorY);
    }

    /**
     * Закрытая комнатка с нерабочим nether-порталом (визуально),
     * стены — BLACK_WOOL. Открывает её EclipsiaMobs после смерти босса.
     */
    private void buildPostBossPortal(RegionPainter p, int z, int floorY) {
        // === Главная стенка из чёрной шерсти (закрывает проход дальше) ===
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 1; dy <= 12; dy++) {
                p.place(dx, floorY + dy, z, Material.BLACK_WOOL);
            }
        }
        // Аметистовая «руна» в центре стены
        for (int dy = 5; dy <= 7; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 6) {
                    p.place(dx, floorY + dy, z, Material.AMETHYST_BLOCK);
                } else {
                    p.place(dx, floorY + dy, z, Material.AMETHYST_BLOCK);
                }
            }
        }
        p.place(-2, floorY + 6, z, Material.AMETHYST_CLUSTER);
        p.place(+2, floorY + 6, z, Material.AMETHYST_CLUSTER);

        // === Портальная комната за стенкой ===
        int pz = z + 1; // комната 9 блоков вглубь до z+9
        // Пол — обсидиан с аметистовыми «рунами»
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = pz; dz <= pz + 8; dz++) {
                Material floor = (Math.abs(dx) <= 1 && (dz - pz) % 2 == 0)
                        ? Material.AMETHYST_BLOCK
                        : Material.POLISHED_BLACKSTONE;
                p.place(dx, floorY, dz, floor);
            }
        }
        // Стены комнаты
        for (int dz = pz; dz <= pz + 8; dz++) {
            for (int dy = 1; dy <= 12; dy++) {
                Material wall = (dy <= 4) ? Material.DEEPSLATE_BRICKS
                              : (dy <= 8) ? Material.POLISHED_DEEPSLATE
                              : Material.POLISHED_BLACKSTONE_BRICKS;
                p.place(-8, floorY + dy, dz, wall);
                p.place(+8, floorY + dy, dz, wall);
            }
        }
        // Задняя стена комнаты (z = pz + 8 + 1)
        int backZ = pz + 9;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 1; dy <= 12; dy++) {
                p.place(dx, floorY + dy, backZ, Material.DEEPSLATE_BRICKS);
            }
        }
        // Потолок арочный
        for (int dz = pz; dz <= pz + 8; dz++) {
            for (int dx = -8; dx <= 8; dx++) {
                int peak = 12 + (int) Math.round(Math.cos(dx * Math.PI / 16) * 1);
                p.place(dx, floorY + peak, dz, Material.POLISHED_BLACKSTONE);
            }
        }

        // === v7: Никакой обсидиановой рамки и nether-портала. ===
        // Это просто гладкая стена в конце прохода. После победы над боссом
        // EclipsiaMobs/PortalListener запускает там частицы и при подходе
        // телепортирует игрока в мир `elikium`.
        // Отметим точку «портала» декоративным подсветом — soul_lantern по
        // углам (внутри стены), но сама стена остаётся непроницаемой.
        int portalCenterZ = pz + 4;
        // Дальняя задняя стена комнаты тоже из BLACK_WOOL (загадочная зона):
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                p.place(dx, floorY + dy, backZ, Material.BLACK_WOOL);
            }
        }
        // Лестница-постамент перед стеной (визуальный фокус)
        for (int step = 0; step < 3; step++) {
            int dz = portalCenterZ - 1 - step;
            for (int dx = -3; dx <= 3; dx++) {
                p.place(dx, floorY + 1 + step, dz, Material.POLISHED_BLACKSTONE_SLAB);
            }
        }
        // Колонны рядом с порталом
        for (int dz : new int[]{portalCenterZ - 2, portalCenterZ + 2}) {
            p.column(-4, floorY + 1, dz, 8, () -> Material.QUARTZ_PILLAR);
            p.column(+4, floorY + 1, dz, 8, () -> Material.QUARTZ_PILLAR);
            p.place(-4, floorY + 9, dz, Material.SOUL_LANTERN);
            p.place(+4, floorY + 9, dz, Material.SOUL_LANTERN);
        }
        // Свечи / candles по полу комнаты
        int[][] candles = {
            {-5, pz + 1}, {+5, pz + 1},
            {-5, pz + 7}, {+5, pz + 7},
            {-3, portalCenterZ + 3}, {+3, portalCenterZ + 3}
        };
        for (int[] c : candles) {
            p.place(c[0], floorY + 1, c[1], Material.SOUL_LANTERN);
        }
        // Цепи свисающие с потолка
        for (int dz = pz + 1; dz <= pz + 7; dz += 2) {
            for (int k = 1; k <= 3; k++) {
                p.place(0, floorY + 11 - k, dz, Material.CHAIN);
            }
        }
        // Череп / decoration на задней стене
        p.place(0, floorY + 5, backZ - 1 + 0 /* spotted but not implemented */,
                Material.SCULK);
        p.place(-1, floorY + 5, backZ - 1, Material.SCULK);
        p.place(+1, floorY + 5, backZ - 1, Material.SCULK);
        // Аметистовые кластеры по углам
        p.place(-7, floorY + 11, pz, Material.AMETHYST_CLUSTER);
        p.place(+7, floorY + 11, pz, Material.AMETHYST_CLUSTER);
        p.place(-7, floorY + 11, pz + 8, Material.AMETHYST_CLUSTER);
        p.place(+7, floorY + 11, pz + 8, Material.AMETHYST_CLUSTER);
    }

    /**
     * Скальный фасад с обеих сторон от арки прохода — закрывает «срез горы»,
     * делает вход в пещеру похожим на серьёзный портал.
     */
    private void buildPassageFacade(RegionPainter p, Random rng, int z, boolean facingNorth, int floorY) {
        // 30-блочные «крылья» скал по обе стороны от арки.
        int facadeZ = z + (facingNorth ? -1 : 1); // фасад чуть впереди арки
        for (int dx = -30; dx <= 30; dx++) {
            if (Math.abs(dx) <= 10) continue; // зона арки
            double t = (Math.abs(dx) - 10) / 20.0;
            int h = 22 - (int) Math.round(t * 8 + rng.nextDouble() * 4);
            int depth = 2 + rng.nextInt(3);
            for (int dz = 0; dz < depth; dz++) {
                int realZ = facadeZ + (facingNorth ? -dz : dz);
                int natural = landscape.getHeight(dx, realZ);
                int top = Math.max(natural, floorY) + h;
                for (int y = floorY + 1; y <= top; y++) {
                    Material m = pickFacadeStone(rng);
                    p.place(dx, y, realZ, m);
                }
                if (rng.nextInt(8) == 0) {
                    p.place(dx, top + 1, realZ, Material.AMETHYST_CLUSTER);
                }
            }
        }
        // По центру — выпуклый клиновой свод
        for (int dx = -10; dx <= 10; dx++) {
            int boost = (int) Math.round(Math.cos(dx * Math.PI / 20) * 6);
            for (int dy = 17; dy <= 17 + boost; dy++) {
                p.place(dx, floorY + dy, facadeZ, pickFacadeStone(rng));
            }
        }
    }

    private Material pickFacadeStone(Random rng) {
        int r = rng.nextInt(100);
        if (r < 40) return Material.COBBLED_DEEPSLATE;
        if (r < 70) return Material.STONE;
        if (r < 85) return Material.MOSSY_COBBLESTONE;
        if (r < 95) return Material.DEEPSLATE;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private void buildCathedralColumn(RegionPainter p, int x, int z, int floorY) {
        p.column(x, floorY + 1, z, 10, () -> Material.QUARTZ_PILLAR);
        p.place(x, floorY + 11, z, Material.SMOOTH_QUARTZ);
        p.place(x, floorY, z, Material.QUARTZ_BLOCK);
    }

    private void buildStatueNiche(RegionPainter p, int wallX, int z, int floorY) {
        int sign = (wallX < 0) ? 1 : -1;
        int nx = wallX + sign;
        for (int dy = 1; dy <= 4; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(wallX, floorY + dy, z + dz, Material.AIR);
            }
        }
        p.place(nx, floorY + 1, z, Material.POLISHED_BLACKSTONE_SLAB);
        p.place(nx, floorY + 2, z, Material.OBSIDIAN);
        p.place(nx, floorY + 3, z, Material.OBSIDIAN);
        p.place(nx, floorY + 4, z, Material.AMETHYST_CLUSTER);
        p.place(nx, floorY + 5, z, Material.SOUL_LANTERN);
    }

    private void buildGrandEntry(RegionPainter p, int z, boolean facingNorth, int floorY) {
        for (int dy = 1; dy <= 14; dy++) {
            p.place(-9, floorY + dy, z, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(+9, floorY + dy, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int dx = -9; dx <= 9; dx++) {
            int peak = 13 + (int) Math.round(Math.cos(dx * Math.PI / 18) * 2);
            p.place(dx, floorY + peak + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        // Декор НАД аркой смещён в стороны (фидбэк v5: «столб в гейте»).
        p.place(-3, floorY + 16, z, Material.AMETHYST_BLOCK);
        p.place(+3, floorY + 16, z, Material.AMETHYST_BLOCK);
        p.place(-7, floorY + 5, z, Material.SOUL_LANTERN);
        p.place(+7, floorY + 5, z, Material.SOUL_LANTERN);
    }

    private void paintRoadToCity(RegionPainter p, Random rng) {
        int z1 = CAVE_EXIT_Z + 1;
        int z2 = ELIKIUM_ARCH_Z - 1;
        // Дорога к городу — продолжение прохода (на floorY=12).
        final int floorY = GROUND_Y + 8;
        flattenStrip(p, -6, z1, 6, z2, floorY, Material.COBBLESTONE);

        for (int z = z1; z <= z2; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                p.place(dx, floorY, z, Material.COBBLESTONE);
            }
        }
        // Фонари
        for (int z = z1; z <= z2; z += 12) {
            p.column(-4, floorY + 1, z, 3, () -> Material.OAK_FENCE);
            p.column(+4, floorY + 1, z, 3, () -> Material.OAK_FENCE);
            p.place(-4, floorY + 4, z, Material.LANTERN);
            p.place(+4, floorY + 4, z, Material.LANTERN);
        }
        // Живые деревья + цветы
        for (int z = z1 + 3; z <= z2; z += 6) {
            if (rng.nextBoolean()) {
                int x = -8 - rng.nextInt(4);
                BeachTrees.roadsideBirch(p, rng, x, z, landscape.getHeight(x, z));
            }
            if (rng.nextBoolean()) {
                int x = +8 + rng.nextInt(4);
                BeachTrees.roadsideBirch(p, rng, x, z, landscape.getHeight(x, z));
            }
        }
        for (int i = 0; i < 100; i++) {
            int x = -20 + rng.nextInt(40);
            int z = z1 + rng.nextInt(z2 - z1);
            if (Math.abs(x) <= 4) continue;
            int gy = landscape.getHeight(x, z);
            Material flower = switch (rng.nextInt(5)) {
                case 0 -> Material.POPPY;
                case 1 -> Material.DANDELION;
                case 2 -> Material.OXEYE_DAISY;
                case 3 -> Material.CORNFLOWER;
                default -> Material.SHORT_GRASS;
            };
            p.place(x, gy + 1, z, flower);
        }
        // Арка «Эликий»
        int arkZ = ELIKIUM_ARCH_Z;
        for (int dy = 1; dy <= 9; dy++) {
            p.place(-6, floorY + dy, arkZ, Material.STONE_BRICKS);
            p.place(+6, floorY + dy, arkZ, Material.STONE_BRICKS);
        }
        for (int dx = -6; dx <= 6; dx++) {
            p.place(dx, floorY + 10, arkZ, Material.STONE_BRICKS);
        }
        p.place(0, floorY + 11, arkZ, Material.AMETHYST_BLOCK);
        p.place(0, floorY + 12, arkZ, Material.AMETHYST_CLUSTER);
        p.place(-5, floorY + 7, arkZ, Material.LANTERN);
        p.place(+5, floorY + 7, arkZ, Material.LANTERN);
        // Знак убран (фидбэк: «столб посреди дороги»).
    }

    // =========================================================================
    // F. ЛЕТАЮЩИЕ ОСТРОВА (органичные)
    // =========================================================================

    private void paintFloatingArchipelago(RegionPainter p, BeachIslands islands, Random rng) {
        // 8 островов вокруг локации с разными темами
        islands.organic(-50, -20, GROUND_Y + 28, 6, 10, BeachIslands.Theme.MOSSY_HILL);
        islands.organic(60, 0, GROUND_Y + 35, 7, 12, BeachIslands.Theme.DARK_RUINS);
        islands.organic(-80, 40, GROUND_Y + 22, 5, 9, BeachIslands.Theme.CRYSTAL);
        islands.organic(75, 55, GROUND_Y + 40, 6, 11, BeachIslands.Theme.CHERRY);
        islands.organic(-30, 80, GROUND_Y + 30, 5, 10, BeachIslands.Theme.CAMPSITE);
        islands.organic(45, 95, GROUND_Y + 48, 8, 14, BeachIslands.Theme.DARK_RUINS);
        islands.organic(-90, 120, GROUND_Y + 25, 5, 9, BeachIslands.Theme.BARE);
        islands.organic(0, 155, GROUND_Y + 55, 9, 15, BeachIslands.Theme.CRYSTAL);

        // Большой остров над ареной
        islands.organic(ARENA_X, ARENA_Z, GROUND_Y + 60, 8, 14, BeachIslands.Theme.DARK_RUINS);

        // Цепные мосты между парой островов
        islands.chainBridge(-50, GROUND_Y + 28, -20, 60, GROUND_Y + 35, 0);
        islands.chainBridge(75, GROUND_Y + 40, 55, 45, GROUND_Y + 48, 95);
    }

    // =========================================================================
    // FLATTEN HELPERS
    // =========================================================================

    private void flattenDisk(RegionPainter p, int cx, int cz, int radius,
                             int targetY, Material surface) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                flattenColumn(p, cx + dx, cz + dz, targetY, surface);
            }
        }
    }

    /**
     * «Мягкий» диск: внутри coreRadius — flat targetY, потом slope-кайма
     * шириной slopeWidth, плавно поднимающаяся (или опускающаяся) к
     * натуральной высоте окружающего рельефа. Закрывает «дыры» вокруг
     * платформ и убирает резкие срезы.
     */
    private void softFlattenDisk(RegionPainter p, int cx, int cz, int coreRadius,
                                 int slopeWidth, int targetY,
                                 Material core, Material edge) {
        int totalR = coreRadius + slopeWidth;
        int totalR2 = totalR * totalR;
        int coreR2 = coreRadius * coreRadius;
        for (int dx = -totalR; dx <= totalR; dx++) {
            for (int dz = -totalR; dz <= totalR; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > totalR2) continue;
                int x = cx + dx, z = cz + dz;
                int natural = (landscape != null) ? landscape.getHeight(x, z) : targetY;
                LandscapeGenerator.Zone zone = (landscape != null)
                        ? landscape.getZone(x, z) : LandscapeGenerator.Zone.FOREST;
                int colY;
                Material surface;
                if (d2 <= coreR2) {
                    colY = targetY;
                    surface = core;
                } else {
                    double dist = Math.sqrt(d2);
                    double t = (dist - coreRadius) / slopeWidth; // 0..1
                    colY = (int) Math.round(targetY + (natural - targetY) * t);
                    // На пляже/океане slope-поверхность должна быть beach-material,
                    // а не grass — фидбэк v4: «земля вокруг лагеря не сочетается с пляжем».
                    surface = switch (zone) {
                        case BEACH -> Material.BLACK_CONCRETE_POWDER;
                        case OCEAN -> Material.GRAVEL;
                        default -> edge;
                    };
                }
                // Срезать всё выше colY
                for (int y = colY + 1; y <= GROUND_Y + 60; y++) {
                    p.place(x, y, z, Material.AIR);
                }
                // Под поверхностью — гарантированный слой грунта
                int fillBottom = Math.min(GROUND_Y - 3, colY - 1);
                for (int y = colY - 1; y >= fillBottom; y--) {
                    p.place(x, y, z,
                            (y < GROUND_Y - 1) ? Material.STONE : Material.DIRT);
                }
                p.place(x, colY, z, surface);
                // На океанской стороне slope: налить воду от colY+1 до WATER_Y.
                if (zone == LandscapeGenerator.Zone.OCEAN) {
                    for (int y = colY + 1; y <= GROUND_Y + 1; y++) {
                        p.place(x, y, z, Material.WATER);
                    }
                }
            }
        }
    }

    private void flattenStrip(RegionPainter p, int xMin, int zMin,
                              int xMax, int zMax, int targetY, Material surface) {
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                flattenColumn(p, x, z, targetY, surface);
            }
        }
    }

    private void flattenColumn(RegionPainter p, int x, int z, int targetY, Material surface) {
        // Срезать всё выше до targetY+50 (запас на горы)
        for (int y = targetY + 1; y <= targetY + 50; y++) {
            p.place(x, y, z, Material.AIR);
        }
        // Засыпать снизу до targetY (если земля ниже)
        int natural = (landscape != null) ? landscape.getHeight(x, z) : -64;
        if (natural < targetY) {
            for (int y = natural; y <= targetY - 1; y++) {
                p.place(x, y, z, Material.DIRT);
            }
        }
        p.place(x, targetY, z, surface);
    }

    // =========================================================================
    // Помощники
    // =========================================================================

    private boolean inCamp(int x, int z) {
        int dx = x - CAMP_X, dz = z - CAMP_Z;
        return dx * dx + dz * dz <= (CAMP_RADIUS + 3) * (CAMP_RADIUS + 3);
    }

    private boolean inArena(int x, int z) {
        int dx = x - ARENA_X, dz = z - ARENA_Z;
        return dx * dx + dz * dz <= (ARENA_RADIUS + 3) * (ARENA_RADIUS + 3);
    }

    // =========================================================================
    // Постобработка — таблички
    // =========================================================================

    private void postProcessSigns() {
        // v6: всё через голограммы (TextDisplay), не через таблички.
        spawnHologram(CAMP_X, GROUND_Y + 5, CAMP_Z + CAMP_RADIUS - 2,
                "§d§l✦ Берег ✦",
                "§7Лагерь под защитой Хранителей",
                "§a§l→ §7Лес испытаний на севере");
        spawnHologram(0, GROUND_Y + 14, ARENA_Z - ARENA_RADIUS - 1,
                "§c§l⚔ Арена Хранителя Врат ⚔",
                "§4§lОстановись, путник.",
                "§7За этими вратами — испытание силы.");
        spawnHologram(0, GROUND_Y + 14, CAVE_ENTRANCE_Z,
                "§5§l⛧ Великий Проход ⛧",
                "§7За пещерой — путь в Эликий.");
        spawnHologram(0, GROUND_Y + 4, ELIKIUM_ARCH_Z,
                "§6§l✧ Эликий ✧",
                "§7Город света и тайн",
                "§e(Скоро откроется...)");
        // v9: голограммы про манекены УДАЛЕНЫ (вместе с самими манекенами).
        // Также чистим старые голограммы оставшиеся в мире от прошлых версий.
        if (world != null) {
            int feetY = GROUND_Y + 1;
            int[][] oldDummyOffsets = {
                {-5, -4}, {-3, -4}, {-1, -4}, { 1, -4},
                { 3, -4}, { 5, -4},
                {-5, -1}, { 5, -1},
                { 0, -5}, { 7, -4}, {-7, -4}
            };
            for (int[] off : oldDummyOffsets) {
                org.bukkit.Location loc = new org.bukkit.Location(world,
                        CAMP_X + off[0] + 0.5, feetY + 3, CAMP_Z + off[1] + 0.5);
                for (org.bukkit.entity.Entity e : world.getNearbyEntities(loc, 1.0, 2.0, 1.0)) {
                    if (e instanceof org.bukkit.entity.TextDisplay) {
                        String t = ((org.bukkit.entity.TextDisplay) e).getText();
                        if (t != null && (t.contains("Манекен") || t.contains("Чемпион Берега")
                                || t.contains("Мишень для лучника") || t.contains("Щит-стенка")
                                || t.contains("Лучник") || t.contains("Воин")
                                || t.contains("Берсерк") || t.contains("Маг")
                                || t.contains("Рыцарь") || t.contains("Некромант")
                                || t.contains("Витязь") || t.contains("Бей мечом"))) {
                            e.remove();
                        }
                    }
                }
            }
        }
    }

    /**
     * Создаёт голограмму (TextDisplay) на указанных координатах.
     * Если в той же точке уже есть голограмма — не дублирует.
     */
    private void spawnHologram(int x, int y, int z, String... lines) {
        if (world == null) return;
        org.bukkit.Location loc = new org.bukkit.Location(world, x + 0.5, y, z + 0.5);
        // Удаляем старые голограммы в этой точке (на случай regen).
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
            if (e instanceof org.bukkit.entity.TextDisplay) {
                e.remove();
            }
        }
        org.bukkit.entity.TextDisplay td = world.spawn(loc,
                org.bukkit.entity.TextDisplay.class);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        td.text(net.kyori.adventure.text.Component.text(sb.toString()));
        td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        td.setBackgroundColor(org.bukkit.Color.fromARGB(180, 0, 0, 0));
        td.setShadowed(true);
        // v8: НЕ просвечивают сквозь стены, иначе видно с арены/прохода и мешает.
        td.setSeeThrough(false);
        // v8: ограничиваем максимальную дистанцию видимости (8 блоков),
        // чтобы голограммы лагеря не светились на арене.
        td.setViewRange(0.25f); // ≈ 8 блоков на vanilla render distance
        // Притушим свечение — дефолтный bright=15 светит как маяк через всё.
        try {
            td.setBrightness(new org.bukkit.entity.Display.Brightness(8, 6));
        } catch (Throwable ignored) { /* старые версии Paper */ }
        td.setPersistent(true);
    }

    private void setSignLines(int x, int y, int z, String l1, String l2, String l3, String l4) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getState() instanceof Sign s) {
            s.line(0, net.kyori.adventure.text.Component.text(l1));
            s.line(1, net.kyori.adventure.text.Component.text(l2));
            s.line(2, net.kyori.adventure.text.Component.text(l3));
            s.line(3, net.kyori.adventure.text.Component.text(l4));
            s.update();
        }
    }
}
