package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Процедурная генерация dark-fantasy локации «Берег» с НАСТОЯЩИМ рельефом
 * на базе {@link LandscapeGenerator} (Simplex noise, 0 deps).
 *
 * <p><b>Ключевое отличие от плоской версии:</b> теперь земля не плоская,
 * а сгенерирована шумом — океан → пляж → лес с холмами → горная стена 50+
 * блоков высотой. Все ключевые структуры (лагерь, арена, мост, пещера,
 * дорога) <b>выравниваются</b> до {@code GROUND_Y} с помощью flatten-функций,
 * благодаря чему существующие алгоритмы расстановки декораций продолжают
 * работать без изменений. Деревья мёртвого леса и валуны ставятся на
 * фактической высоте земли — рельеф виден.
 *
 * <p>Зоны (см. {@link LandscapeGenerator}):
 * <pre>
 *   z &lt; -100   — океан с тёмной водой
 *   -100..-50   — чёрный пляж с дюнами
 *   -50..100    — мёртвый лес на холмах (±11 блоков)
 *   z &gt; 100    — горная стена (до +45 блоков)
 * </pre>
 *
 * <p>Композиция структур (с севера на юг):
 * <pre>
 *   z = -85..-110 — обломки кораблей (4 шт.) на мелководье
 *   z = -60       — стартовый лагерь (Ø 25), плато до GROUND_Y
 *   z = -48..49   — главная тропа через лес, выровненная до GROUND_Y
 *   z = 40        — развилка тропы (обход арены)
 *   z = 49..68    — мост через ров к арене
 *   z = 80        — арена Хранителя Врат (Ø 25), на платформе
 *   z = 100..130  — пещера, прорезающая горную стену
 *   z = 135..230  — каменная дорога к городу Эликий
 * </pre>
 */
public final class BeachGenerator {

    public static final String GENERATED_FLAG = "eclipsia_beach_generated";
    public static final String ROUND2_FLAG = "eclipsia_beach_round2";

    /** Y-уровень платформ структур (камп, арена, тропа, мост, дорога). */
    public static final int GROUND_Y = 4;

    public static final int CAMP_X = 0, CAMP_Z = -60, CAMP_RADIUS = 12;
    public static final int ARENA_X = 0, ARENA_Z = 80, ARENA_RADIUS = 12;
    public static final int CAVE_ENTRANCE_Z = 100;
    public static final int CAVE_EXIT_Z = 130;
    public static final int ELIKIUM_ARCH_Z = 230;

    /** Регион ландшафтной генерации (включительно). */
    private static final int LAND_X_MIN = -150, LAND_X_MAX = 150;
    private static final int LAND_Z_MIN = -150, LAND_Z_MAX = 250;

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

    /** Запустить генерацию. {@code onFinish} вызывается после полной заливки. */
    public void generate(Runnable onFinish) {
        if (isAlreadyGenerated()) {
            plugin.getLogger().info("BeachGenerator: локация уже была сгенерирована, пропуск.");
            if (onFinish != null) onFinish.run();
            return;
        }

        plugin.getLogger().info("BeachGenerator: запуск генерации Берега в '" + world.getName() + "'…");
        long seed = world.getSeed() ^ 0xBEACEL;
        landscape = new LandscapeGenerator(world, seed);
        RegionPainter p = new RegionPainter(plugin, world, seed);
        p.begin();
        Random rng = p.rng();

        // === Phase A: НАСТОЯЩИЙ ландшафт (noise) ===
        generateTerrain(p);

        // === Phase B: лагерь (платформа + декор) ===
        paintCamp(p, rng);

        // === Phase C: лес (на реальной высоте земли) + тропа (выровнена) ===
        paintDeadForest(p, rng);
        paintMainPath(p, rng);
        paintPathForks(p, rng);

        // === Phase D: мост + арена ===
        paintArenaBridge(p, rng);
        paintArena(p, rng);

        // === Phase E: пещера + дорога к городу ===
        paintCave(p, rng);
        paintRoadToCity(p, rng);

        // === Phase F: летающие острова и обломки кораблей ===
        paintShipwrecks(p, rng);
        paintFloatingIslands(p, rng);

        p.flush(() -> {
            postProcessSigns();
            // Спавн в лагере, чтобы игрок не падал из неба.
            world.setSpawnLocation(CAMP_X, GROUND_Y + 2, CAMP_Z);
            markGenerated();
            plugin.getLogger().info("BeachGenerator: Берег готов!");
            if (onFinish != null) onFinish.run();
        });
    }

    // =========================================================================
    // Phase A — ландшафт через LandscapeGenerator (noise)
    // =========================================================================

    /**
     * Покрыть весь регион реальным рельефом. Работает следующим образом:
     * для каждого столба (x,z) — определяем высоту поверхности по noise,
     * заполняем подповерхностный слой DIRT/STONE (с пещерами в горах/лесу),
     * ставим поверхностный блок и заливаем океан водой.
     *
     * <p>Не трогаем структурные зоны: лагерь, арена, тропу, мост, пещеру,
     * дорогу — там paint-методы потом сами выровняют до {@link #GROUND_Y}.
     */
    private void generateTerrain(RegionPainter p) {
        plugin.getLogger().info("BeachGenerator: фаза A — рельеф (~"
                + ((LAND_X_MAX - LAND_X_MIN + 1) * (LAND_Z_MAX - LAND_Z_MIN + 1))
                + " столбов)");

        for (int x = LAND_X_MIN; x <= LAND_X_MAX; x++) {
            for (int z = LAND_Z_MIN; z <= LAND_Z_MAX; z++) {
                // Пропуск зон, где будут структуры — там flatten сам поставит
                // правильные блоки, а лишние setBlock тратят TPS.
                if (isStructureZone(x, z)) continue;

                int surface = landscape.getHeight(x, z);
                LandscapeGenerator.Zone zone = landscape.getZone(x, z);

                // Подповерхностный слой
                if (surface > GROUND_Y) {
                    // Поднимаем землю выше базы
                    final Material fill = (zone == LandscapeGenerator.Zone.MOUNTAIN)
                            ? Material.STONE : Material.DIRT;
                    for (int y = GROUND_Y + 1; y < surface; y++) {
                        if (zone != LandscapeGenerator.Zone.OCEAN
                                && zone != LandscapeGenerator.Zone.BEACH
                                && landscape.isCave(x, y, z)) {
                            p.place(x, y, z, Material.AIR);
                        } else {
                            p.place(x, y, z, fill);
                        }
                    }
                } else if (surface < GROUND_Y) {
                    // Опускаем землю ниже базы (океан)
                    for (int y = surface + 1; y <= GROUND_Y; y++) {
                        p.place(x, y, z, Material.AIR);
                    }
                }

                // Поверхность
                p.place(x, surface, z, landscape.getSurfaceBlock(x, z));

                // Океан: залить воду до GROUND_Y+1 (зеркало воды)
                if (zone == LandscapeGenerator.Zone.OCEAN) {
                    for (int y = surface + 1; y <= GROUND_Y + 1; y++) {
                        p.place(x, y, z, Material.WATER);
                    }
                } else {
                    // Очистить любой FLAT-мусор сразу над поверхностью
                    p.place(x, surface + 1, z, Material.AIR);
                }
            }
        }
    }

    /**
     * Точка попадает в зону структуры, которая будет выровнена paint-методом?
     * Здесь мы пропускаем эти точки в фазе ландшафта.
     */
    private boolean isStructureZone(int x, int z) {
        // ВАЖНО: каждая зона должна ТОЧНО соответствовать радиусу flatten-функции,
        // иначе на границе остаются блоки old-FLAT (дыры в полу).
        int dx = x - CAMP_X, dz = z - CAMP_Z;
        if (dx * dx + dz * dz <= 15 * 15) return true;            // лагерь (flat r=15)
        dx = x - ARENA_X; dz = z - ARENA_Z;
        if (dx * dx + dz * dz <= 15 * 15) return true;            // арена (flat r=15)
        if (Math.abs(x) <= 3 && z >= -48 && z <= 49) return true; // тропа (узкая 7)
        if (Math.abs(x) <= 4 && z >= 49 && z <= 68) return true;  // мост (9)
        if (Math.abs(x) <= 7 && z >= 99 && z <= 131) return true; // пещера
        if (Math.abs(x) <= 6 && z >= 135 && z <= 230) return true;// дорога (узкая 13)
        // Развилки боковых тропок (x=-18..-3, x=3..18 полоса z=40..56)
        if (Math.abs(x) >= 3 && Math.abs(x) <= 18 && z >= 38 && z <= 56) return true;
        return false;
    }

    // =========================================================================
    // FLATTEN HELPERS
    // =========================================================================

    /**
     * Выровнять диск (cx,cz, r) до уровня {@code targetY}.
     * Срезает всё выше targetY (AIR), засыпает ниже DIRT, поверхность —
     * указанный блок surface (или DIRT по умолчанию).
     */
    private void flattenDisk(RegionPainter p, int cx, int cz, int radius,
                             int targetY, Material surface) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                int x = cx + dx, z = cz + dz;
                flattenColumn(p, x, z, targetY, surface);
            }
        }
    }

    /** Выровнять прямоугольную полосу до targetY. */
    private void flattenStrip(RegionPainter p, int xMin, int zMin,
                              int xMax, int zMax, int targetY, Material surface) {
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                flattenColumn(p, x, z, targetY, surface);
            }
        }
    }

    /** Выровнять отдельный столб до targetY с указанной поверхностью. */
    private void flattenColumn(RegionPainter p, int x, int z, int targetY, Material surface) {
        // Срезать всё выше targetY до targetY+30 (запас на горы)
        for (int y = targetY + 1; y <= targetY + 50; y++) {
            p.place(x, y, z, Material.AIR);
        }
        // Засыпать (если ниже)
        for (int y = -10; y <= targetY - 1; y++) {
            // ставим только если ниже базовой высоты (для океана)
            // Не форсим STONE/DIRT глубоко — нижние слои уже из FLAT
        }
        // Если земля сейчас ниже — добавить DIRT от текущей высоты до targetY-1
        int natural = landscape.getHeight(x, z);
        if (natural < targetY) {
            for (int y = natural; y <= targetY - 1; y++) {
                p.place(x, y, z, Material.DIRT);
            }
        }
        // Поверхность
        p.place(x, targetY, z, surface);
    }

    /** Опора (столб) от {@code yTop} до земли — каменные кирпичи. */
    private void supportColumn(RegionPainter p, int x, int z, int yTop) {
        int yBottom = Math.min(GROUND_Y - 1, landscape.getHeight(x, z));
        for (int y = yBottom; y < yTop; y++) {
            p.place(x, y, z, Material.STONE_BRICKS);
        }
    }

    // =========================================================================
    // Phase B — лагерь (с выравненной платформой)
    // =========================================================================

    private void paintCamp(RegionPainter p, Random rng) {
        int cx = CAMP_X, cz = CAMP_Z;

        // Выровнять диск под лагерь (r=15 = isStructureZone).
        flattenDisk(p, cx, cz, 15, GROUND_Y, Material.COBBLED_DEEPSLATE);
        p.clearAir(cx - 15, GROUND_Y + 1, cz - 15, cx + 15, GROUND_Y + 8, cz + 15);

        // Пол: COBBLED_DEEPSLATE диск 11.
        p.fillDisk(cx, GROUND_Y, cz, 11, RegionPainter.weighted(rng,
                Material.COBBLED_DEEPSLATE, 60,
                Material.POLISHED_BLACKSTONE, 30,
                Material.DEEPSLATE_BRICKS, 10));

        // Частокол 22×22.
        for (int dx = -11; dx <= 11; dx++) {
            p.column(cx + dx, GROUND_Y + 1, cz - 11, 4, () -> Material.DARK_OAK_LOG);
            if (!(dx >= -2 && dx <= 2)) {
                p.column(cx + dx, GROUND_Y + 1, cz + 11, 4, () -> Material.DARK_OAK_LOG);
            }
        }
        for (int dz = -11; dz <= 11; dz++) {
            p.column(cx - 11, GROUND_Y + 1, cz + dz, 4, () -> Material.DARK_OAK_LOG);
            p.column(cx + 11, GROUND_Y + 1, cz + dz, 4, () -> Material.DARK_OAK_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            p.place(cx + dx, GROUND_Y + 5, cz + 11, Material.DARK_OAK_LOG);
        }
        p.place(cx - 3, GROUND_Y + 4, cz + 11, Material.SOUL_TORCH);
        p.place(cx + 3, GROUND_Y + 4, cz + 11, Material.SOUL_TORCH);

        // Костёр в центре + камни + брёвна-лавки + навес.
        p.place(cx, GROUND_Y + 1, cz, Material.SOUL_CAMPFIRE);
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}}) {
            p.place(cx + d[0], GROUND_Y + 1, cz + d[1], Material.POLISHED_BLACKSTONE);
        }
        p.place(cx - 2, GROUND_Y + 1, cz, Material.DARK_OAK_LOG);
        p.place(cx + 2, GROUND_Y + 1, cz, Material.DARK_OAK_LOG);
        p.place(cx, GROUND_Y + 1, cz + 2, Material.DARK_OAK_LOG);
        p.place(cx, GROUND_Y + 1, cz - 2, Material.DARK_OAK_LOG);
        for (int[] st : new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}}) {
            p.column(cx + st[0], GROUND_Y + 1, cz + st[1], 4, () -> Material.DARK_OAK_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    p.place(cx + dx, GROUND_Y + 5, cz + dz, Material.DARK_OAK_PLANKS);
                }
            }
        }

        // Палатка 5×4 на западе.
        int px = cx - 9, pz = cz - 2;
        p.fillBox(px, GROUND_Y + 1, pz, px + 4, GROUND_Y + 3, pz + 3, () -> Material.AIR);
        for (int dx = 0; dx <= 4; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                Material mat = ((dx + dy) % 4 == 0) ? Material.DARK_OAK_PLANKS : Material.GRAY_WOOL;
                p.place(px + dx, GROUND_Y + dy, pz, mat);
                p.place(px + dx, GROUND_Y + dy, pz + 3, mat);
            }
        }
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                p.place(px + dx, GROUND_Y + 4, pz + dz, Material.DARK_OAK_PLANKS);
            }
        }
        p.place(px + 1, GROUND_Y + 1, pz + 1, Material.RED_BED);
        p.place(px + 1, GROUND_Y + 1, pz + 2, Material.RED_BED);
        p.place(px + 3, GROUND_Y + 1, pz + 1, Material.CHEST);
        p.place(px + 2, GROUND_Y + 2, pz, Material.WALL_TORCH);
        p.place(px - 1, GROUND_Y + 1, pz + 1, Material.BARREL);

        // Тренировочная площадка.
        int tgx = cx - 8, tgz = cz + 2;
        for (int dx = 0; dx < 8; dx++) {
            for (int dz = 0; dz < 8; dz++) {
                if ((dx == 0 || dx == 7 || dz == 0 || dz == 7)
                        && !(dx == 3 && dz == 7)) {
                    p.place(tgx + dx, GROUND_Y + 1, tgz + dz, Material.OAK_FENCE);
                }
            }
        }
        int[][] dummies = {{tgx + 2, tgz + 2}, {tgx + 5, tgz + 2}, {tgx + 2, tgz + 5}, {tgx + 5, tgz + 5}};
        for (int[] d : dummies) {
            p.column(d[0], GROUND_Y + 1, d[1], 2, () -> Material.DARK_OAK_FENCE);
            p.place(d[0], GROUND_Y + 3, d[1], Material.CARVED_PUMPKIN);
        }
        p.place(tgx + 4, GROUND_Y + 1, tgz, Material.DARK_OAK_SIGN);

        // Тотем возрождения.
        int totemX = cx + 7, totemZ = cz - 7;
        p.column(totemX, GROUND_Y + 1, totemZ, 6, () -> Material.POLISHED_BLACKSTONE_WALL);
        p.place(totemX, GROUND_Y + 7, totemZ, Material.AMETHYST_BLOCK);
        p.place(totemX, GROUND_Y + 8, totemZ, Material.AMETHYST_CLUSTER);
        int[][] ring = {{-2,0},{2,0},{0,-2},{0,2},{-1,-1},{1,-1},{-1,1},{1,1}};
        for (int[] r : ring) {
            p.place(totemX + r[0], GROUND_Y + 1, totemZ + r[1], Material.AMETHYST_CLUSTER);
        }

        // Склад.
        int sx = cx + 3, sz = cz + 3;
        for (int[] pl : new int[][]{{0,0},{4,0},{0,3},{4,3}}) {
            p.column(sx + pl[0], GROUND_Y + 1, sz + pl[1], 3, () -> Material.SPRUCE_LOG);
        }
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                p.place(sx + dx, GROUND_Y + 4, sz + dz, Material.SPRUCE_PLANKS);
            }
        }
        p.place(sx + 1, GROUND_Y + 1, sz + 1, Material.CHEST);
        p.place(sx + 2, GROUND_Y + 1, sz + 1, Material.CHEST);
        p.place(sx + 3, GROUND_Y + 1, sz + 1, Material.CHEST);
        p.place(sx + 1, GROUND_Y + 1, sz + 2, Material.BARREL);
        p.place(sx + 2, GROUND_Y + 1, sz + 2, Material.BARREL);
        p.place(sx + 3, GROUND_Y + 1, sz + 2, Material.BARREL);
        p.place(sx, GROUND_Y + 1, sz + 1, Material.DARK_OAK_SIGN);

        p.place(cx + 1, GROUND_Y + 1, cz + 12, Material.DARK_OAK_SIGN);
        p.place(cx - 1, GROUND_Y + 1, cz + 12, Material.DARK_OAK_SIGN);
    }

    // =========================================================================
    // Phase C — мёртвый лес (на реальной высоте) + тропа
    // =========================================================================

    private void paintDeadForest(RegionPainter p, Random rng) {
        // 220 деревьев трёх типов на ХОЛМАХ.
        for (int i = 0; i < 220; i++) {
            int x = -90 + rng.nextInt(180);
            int z = -45 + rng.nextInt(140);
            if (Math.abs(x) <= 4) continue;
            if (inCamp(x, z) || inArena(x, z)) continue;
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            int gy = landscape.getHeight(x, z);
            int kind = rng.nextInt(3);
            switch (kind) {
                case 0 -> deadTreeStraight(p, x, z, gy, 12 + rng.nextInt(4));
                case 1 -> deadTreeCrooked(p, x, z, gy, 9 + rng.nextInt(3));
                default -> deadTreeSplit(p, x, z, gy, 7 + rng.nextInt(3));
            }
        }

        // Кусты, грибы, пни, руны, лужи, кости, паутина — все на gy+1
        spreadDecor(p, rng, 250, Material.DEAD_BUSH, 1);
        spreadDecor(p, rng, 150, Material.AMETHYST_CLUSTER, 2);
        spreadDecor(p, rng, 60, Material.DARK_OAK_LOG, 1);
        spreadDecor(p, rng, 30, Material.CHISELED_DEEPSLATE, 1);
        spreadDecor(p, rng, 80, Material.BONE_BLOCK, 1);
        spreadDecor(p, rng, 200, Material.COBWEB, 1);
        spreadDecor(p, rng, 50, Material.SOUL_LANTERN, 1);
        spreadDecor(p, rng, 100, Material.AMETHYST_CLUSTER, 1);
        spreadDecor(p, rng, 80, Material.RED_MUSHROOM, 1);
        spreadDecor(p, rng, 80, Material.BROWN_MUSHROOM, 1);
        spreadDecor(p, rng, 60, Material.FERN, 1);
        spreadDecor(p, rng, 60, Material.LARGE_FERN, 1);

        // Лужи (3×3 воды на земле).
        for (int i = 0; i < 15; i++) {
            int x = -60 + rng.nextInt(120);
            int z = -40 + rng.nextInt(100);
            if (Math.abs(x) <= 4 || inCamp(x, z) || inArena(x, z)) continue;
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
            int gy = landscape.getHeight(x, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx * dx + dz * dz <= 2) {
                        p.place(x + dx, gy, z + dz, Material.WATER);
                    }
                }
            }
        }

        // Сломанные телеги.
        int[][] carts = {{-25, 0}, {20, 20}, {-35, 35}, {30, 55}};
        for (int[] c : carts) buildCart(p, c[0], c[1], landscape.getHeight(c[0], c[1]));
    }

    private void spreadDecor(RegionPainter p, Random rng, int count, Material mat, int yOffset) {
        for (int i = 0; i < count; i++) {
            int x = -90 + rng.nextInt(180);
            int z = -45 + rng.nextInt(140);
            if (Math.abs(x) <= 4) continue;
            if (inCamp(x, z) || inArena(x, z)) continue;
            if (landscape.getZone(x, z) != LandscapeGenerator.Zone.FOREST) continue;
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

    private void deadTreeStraight(RegionPainter p, int x, int z, int gy, int h) {
        for (int dy = 0; dy < h; dy++) {
            p.place(x, gy + 1 + dy, z, Material.DARK_OAK_LOG);
        }
        int top = gy + h;
        p.place(x + 1, top, z, Material.DARK_OAK_LOG);
        p.place(x - 1, top, z, Material.DARK_OAK_LOG);
        p.place(x, top, z + 1, Material.DARK_OAK_LOG);
        p.place(x, top, z - 1, Material.DARK_OAK_LOG);
        p.place(x + 1, top, z + 1, Material.COBWEB);
        p.place(x - 1, top, z - 1, Material.COBWEB);
    }

    private void deadTreeCrooked(RegionPainter p, int x, int z, int gy, int h) {
        int curX = x, curZ = z;
        for (int dy = 0; dy < h; dy++) {
            p.place(curX, gy + 1 + dy, curZ, Material.DARK_OAK_LOG);
            if (dy == h / 3) curX += 1;
            if (dy == 2 * h / 3) curZ += 1;
        }
        p.place(curX + 1, gy + h, curZ, Material.DARK_OAK_LOG);
        p.place(curX, gy + h, curZ - 1, Material.COBWEB);
    }

    private void deadTreeSplit(RegionPainter p, int x, int z, int gy, int h) {
        for (int dy = 0; dy < h; dy++) {
            p.place(x,     gy + 1 + dy, z,     Material.DARK_OAK_LOG);
            p.place(x + 1, gy + 1 + dy, z,     Material.DARK_OAK_LOG);
            p.place(x,     gy + 1 + dy, z + 1, Material.DARK_OAK_LOG);
        }
    }

    private void paintMainPath(RegionPainter p, Random rng) {
        int z1 = CAMP_Z + 12;
        int z2 = 49;
        // Полоса под тропу (узкая 7) — выровнять до GROUND_Y.
        flattenStrip(p, -3, z1, 3, z2, GROUND_Y, Material.DIRT_PATH);

        Supplier<Material> pathMat = RegionPainter.weighted(rng,
                Material.DIRT_PATH, 65,
                Material.GRAVEL, 25,
                Material.COARSE_DIRT, 10);
        p.path(0, z1, 0, z2, GROUND_Y + 1, 2, pathMat);

        // Бордюры.
        for (int z = z1; z <= z2; z++) {
            p.place(-3, GROUND_Y + 1, z, Material.BLACKSTONE);
            p.place(+3, GROUND_Y + 1, z, Material.BLACKSTONE);
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 10) {
            p.place(-3, GROUND_Y + 2, z, Material.SOUL_TORCH);
            p.place(+3, GROUND_Y + 2, z, Material.SOUL_TORCH);
        }
        p.place(0, GROUND_Y + 2, z1, Material.DARK_OAK_SIGN);
    }

    private void paintPathForks(RegionPainter p, Random rng) {
        int forkZ = 40;
        // Выровнять боковые полосы развилок до GROUND_Y
        flattenStrip(p, -18, forkZ - 1, -3, forkZ + 6, GROUND_Y, Material.DIRT_PATH);
        flattenStrip(p,  3, forkZ - 1, 18, forkZ + 6, GROUND_Y, Material.DIRT_PATH);
        // И «крюк» обратно (x=-18..-5 ↘ z=forkZ+6..16)
        flattenStrip(p, -18, forkZ + 6, -5, forkZ + 16, GROUND_Y, Material.DIRT_PATH);
        flattenStrip(p,  5, forkZ + 6, 18, forkZ + 16, GROUND_Y, Material.DIRT_PATH);

        p.place(-4, GROUND_Y + 2, forkZ, Material.OAK_SIGN);
        p.place(+4, GROUND_Y + 2, forkZ, Material.OAK_SIGN);
        p.place(-18, GROUND_Y + 1, forkZ + 12, Material.LANTERN);
        p.place(+18, GROUND_Y + 1, forkZ + 12, Material.LANTERN);
        // Указатели на каждом изгибе
        p.place(-12, GROUND_Y + 1, forkZ + 4, Material.SOUL_TORCH);
        p.place(+12, GROUND_Y + 1, forkZ + 4, Material.SOUL_TORCH);
    }

    // =========================================================================
    // Phase D — мост + арена
    // =========================================================================

    private void paintArenaBridge(RegionPainter p, Random rng) {
        int z1 = 49, z2 = 68;
        // Выровнять полосу моста (9 блоков).
        flattenStrip(p, -4, z1, 4, z2, GROUND_Y, Material.STONE_BRICKS);

        // Под мостом — тёмная вода (ров).
        p.fillBox(-4, GROUND_Y - 1, z1, 4, GROUND_Y, z2, () -> Material.WATER);
        p.fillBox(-4, GROUND_Y - 2, z1, 4, GROUND_Y - 1, z2, () -> Material.DEEPSLATE);

        // Настил моста.
        for (int z = z1; z <= z2; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                p.place(dx, GROUND_Y + 1, z, Material.STONE_BRICKS);
            }
        }
        for (int z = z1; z <= z2; z++) {
            p.place(-3, GROUND_Y + 1, z, Material.STONE_BRICK_WALL);
            p.place(+3, GROUND_Y + 1, z, Material.STONE_BRICK_WALL);
        }
        // Столбы-фонари.
        for (int z = z1; z <= z2; z += 5) {
            p.column(-3, GROUND_Y + 2, z, 2, () -> Material.POLISHED_BLACKSTONE_WALL);
            p.column(+3, GROUND_Y + 2, z, 2, () -> Material.POLISHED_BLACKSTONE_WALL);
            p.place(-3, GROUND_Y + 4, z, Material.SOUL_LANTERN);
            p.place(+3, GROUND_Y + 4, z, Material.SOUL_LANTERN);
        }
        // Арка перед мостом.
        for (int dy = 1; dy <= 5; dy++) {
            p.place(-3, GROUND_Y + dy, z1 - 1, Material.STONE_BRICKS);
            p.place(+3, GROUND_Y + dy, z1 - 1, Material.STONE_BRICKS);
        }
        for (int dx = -3; dx <= 3; dx++) {
            p.place(dx, GROUND_Y + 6, z1 - 1, Material.STONE_BRICKS);
        }
        p.place(0, GROUND_Y + 2, z1 - 1, Material.DARK_OAK_SIGN);
    }

    private void paintArena(RegionPainter p, Random rng) {
        int ax = ARENA_X, az = ARENA_Z;
        // Платформа арены (r=15 = isStructureZone).
        flattenDisk(p, ax, az, 15, GROUND_Y, Material.STONE_BRICKS);
        p.clearAir(ax - 15, GROUND_Y + 1, az - 15, ax + 15, GROUND_Y + 15, az + 15);

        // Концентрические кольца.
        p.fillDisk(ax, GROUND_Y, az, 12, () -> Material.STONE_BRICKS);
        p.fillRing(ax, GROUND_Y, az, 5, 6, () -> Material.CRACKED_STONE_BRICKS);
        p.fillRing(ax, GROUND_Y, az, 8, 9, () -> Material.MOSSY_STONE_BRICKS);
        p.fillRing(ax, GROUND_Y, az, 10, 11, () -> Material.CRACKED_STONE_BRICKS);

        p.fillBox(ax - 2, GROUND_Y, az - 2, ax + 2, GROUND_Y, az + 2, () -> Material.AMETHYST_BLOCK);

        // 8 столбов и алтарь (как было).
        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};
        for (double angDeg : angles) {
            double r = Math.toRadians(angDeg);
            int sx = ax + (int) Math.round(Math.cos(r) * 11);
            int sz = az + (int) Math.round(Math.sin(r) * 11);
            p.column(sx, GROUND_Y + 1, sz, 8, () -> Material.POLISHED_BLACKSTONE_WALL);
            p.place(sx, GROUND_Y + 9, sz, Material.AMETHYST_BLOCK);
            p.place(sx, GROUND_Y + 10, sz, Material.AMETHYST_CLUSTER);
        }
        double[] fireAngles = {45, 135, 225, 315};
        for (double angDeg : fireAngles) {
            double r = Math.toRadians(angDeg);
            int sx = ax + (int) Math.round(Math.cos(r) * 8);
            int sz = az + (int) Math.round(Math.sin(r) * 8);
            p.place(sx, GROUND_Y + 1, sz, Material.SOUL_CAMPFIRE);
        }

        int aax = ax - 3, aaz = az - 5;
        for (int step = 0; step < 3; step++) {
            p.fillBox(aax - step, GROUND_Y + 1, aaz - step,
                    aax + 6 + step, GROUND_Y + 1 + step, aaz + 4 + step,
                    () -> Material.POLISHED_BLACKSTONE);
        }
        p.fillBox(aax + 1, GROUND_Y + 4, aaz + 1, aax + 5, GROUND_Y + 4, aaz + 3,
                () -> Material.OBSIDIAN);
        for (int dy = 1; dy <= 6; dy++) {
            p.place(aax, GROUND_Y + 4 + dy, aaz + 2, Material.OBSIDIAN);
            p.place(aax + 6, GROUND_Y + 4 + dy, aaz + 2, Material.OBSIDIAN);
        }
        for (int dx = 1; dx <= 5; dx++) {
            p.place(aax + dx, GROUND_Y + 10, aaz + 2, Material.OBSIDIAN);
        }
        p.place(ax, GROUND_Y + 2, az - 3, Material.POLISHED_BLACKSTONE);
        p.place(ax, GROUND_Y + 3, az - 3, Material.CAULDRON);
        int[][] bones = {{ax - 4, az - 3}, {ax + 4, az - 3}, {ax - 5, az - 4}};
        for (int[] bp : bones) {
            p.place(bp[0], GROUND_Y + 1, bp[1], Material.BONE_BLOCK);
            p.place(bp[0], GROUND_Y + 2, bp[1], Material.BONE_BLOCK);
        }
        double[] chainAngles = {0, 90, 180, 270};
        for (double angDeg : chainAngles) {
            double r = Math.toRadians(angDeg);
            int sx = ax + (int) Math.round(Math.cos(r) * 11);
            int sz = az + (int) Math.round(Math.sin(r) * 11);
            int dx = (int) Math.signum(ax - sx);
            int dz = (int) Math.signum(az - sz);
            for (int k = 1; k <= 5; k++) {
                p.place(sx + dx * k, GROUND_Y + 9 - k / 2, sz + dz * k, Material.CHAIN);
            }
        }
    }

    // =========================================================================
    // Phase E — пещера в горе + дорога к городу
    // =========================================================================

    private void paintCave(RegionPainter p, Random rng) {
        int z1 = CAVE_ENTRANCE_Z;
        int z2 = CAVE_EXIT_Z;

        // Полоса под пещеру: выровнять снизу до GROUND_Y, расчистить сверху всю гору.
        flattenStrip(p, -7, z1, 7, z2, GROUND_Y, Material.SMOOTH_STONE);
        // Расчистить тоннель (выше базовой высоты горы).
        p.clearAir(-5, GROUND_Y + 1, z1, 5, GROUND_Y + 12, z2);

        // Пол + руны.
        for (int z = z1; z <= z2; z++) {
            for (int dx = -5; dx <= 5; dx++) {
                p.place(dx, GROUND_Y, z, Material.SMOOTH_STONE);
            }
            if ((z - z1) % 3 == 0) {
                p.place(0, GROUND_Y, z, Material.AMETHYST_BLOCK);
            }
        }
        // Стены и потолок (теперь это РЕАЛЬНО внутри горы!).
        for (int z = z1; z <= z2; z++) {
            for (int dy = 1; dy <= 12; dy++) {
                p.place(-6, GROUND_Y + dy, z, Material.DEEPSLATE);
                p.place(+6, GROUND_Y + dy, z, Material.DEEPSLATE);
            }
            for (int dx = -5; dx <= 5; dx++) {
                p.place(dx, GROUND_Y + 13, z, Material.DEEPSLATE);
            }
            if ((z - z1) % 3 == 0) {
                p.place(-6, GROUND_Y + 5, z, Material.AMETHYST_BLOCK);
                p.place(+6, GROUND_Y + 5, z, Material.AMETHYST_BLOCK);
                p.place(-5, GROUND_Y + 5, z, Material.AMETHYST_CLUSTER);
                p.place(+5, GROUND_Y + 5, z, Material.AMETHYST_CLUSTER);
            }
        }
        for (int z = z1 + 5; z < z2; z += 10) {
            p.column(-4, GROUND_Y + 1, z, 11, () -> Material.COBBLED_DEEPSLATE);
            p.column(+4, GROUND_Y + 1, z, 11, () -> Material.COBBLED_DEEPSLATE);
            for (int dx = -3; dx <= 3; dx++) {
                p.place(dx, GROUND_Y + 11, z, Material.COBBLED_DEEPSLATE);
            }
            p.place(-4, GROUND_Y + 3, z, Material.SOUL_TORCH);
            p.place(+4, GROUND_Y + 3, z, Material.SOUL_TORCH);
        }
        for (int i = 0; i < 20; i++) {
            int dx = -4 + rng.nextInt(9);
            int z = z1 + rng.nextInt(z2 - z1);
            int len = 1 + rng.nextInt(3);
            for (int dy = 0; dy < len; dy++) {
                p.place(dx, GROUND_Y + 12 - dy, z, Material.POINTED_DRIPSTONE);
            }
        }
        int midZ = (z1 + z2) / 2;
        p.place(0, GROUND_Y + 1, midZ, Material.POLISHED_BLACKSTONE);
        p.place(0, GROUND_Y + 2, midZ, Material.LANTERN);

        // Над входом — большой кристалл-ориентир.
        p.place(0, GROUND_Y + 14, z1, Material.AMETHYST_BLOCK);
        p.place(0, GROUND_Y + 15, z1, Material.AMETHYST_BLOCK);
        p.place(0, GROUND_Y + 16, z1, Material.AMETHYST_CLUSTER);
        p.column(-6, GROUND_Y + 1, z1 - 1, 12, () -> Material.BLACKSTONE);
        p.column(+6, GROUND_Y + 1, z1 - 1, 12, () -> Material.BLACKSTONE);
        p.place(-5, GROUND_Y + 3, z1 - 1, Material.SOUL_TORCH);
        p.place(+5, GROUND_Y + 3, z1 - 1, Material.SOUL_TORCH);
        p.place(0, GROUND_Y + 12, z1 - 1, Material.DARK_OAK_SIGN);
    }

    private void paintRoadToCity(RegionPainter p, Random rng) {
        int z1 = CAVE_EXIT_Z + 5;
        int z2 = ELIKIUM_ARCH_Z - 1;
        // Узкая дорога 13 — НЕ срезаем всю гору, оставляем горный рельеф по бокам.
        flattenStrip(p, -6, z1, 6, z2, GROUND_Y, Material.COBBLESTONE);

        // Брусчатка.
        for (int z = z1; z <= z2; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                p.place(dx, GROUND_Y, z, Material.COBBLESTONE);
            }
        }
        // Фонари.
        for (int z = z1; z <= z2; z += 15) {
            p.column(-4, GROUND_Y + 1, z, 3, () -> Material.OAK_FENCE);
            p.column(+4, GROUND_Y + 1, z, 3, () -> Material.OAK_FENCE);
            p.place(-4, GROUND_Y + 4, z, Material.LANTERN);
            p.place(+4, GROUND_Y + 4, z, Material.LANTERN);
        }
        // Живые деревья вдоль дороги (на реальной высоте).
        for (int z = z1 + 5; z <= z2; z += 8) {
            if (rng.nextBoolean()) {
                int x = -8 - rng.nextInt(4);
                buildLiveTree(p, x, z, landscape.getHeight(x, z));
            }
            if (rng.nextBoolean()) {
                int x = +8 + rng.nextInt(4);
                buildLiveTree(p, x, z, landscape.getHeight(x, z));
            }
        }
        // Цветы (на реальной высоте).
        for (int i = 0; i < 80; i++) {
            int x = -20 + rng.nextInt(40);
            int z = z1 + rng.nextInt(z2 - z1);
            if (Math.abs(x) <= 4) continue;
            int gy = landscape.getHeight(x, z);
            Material flower = switch (rng.nextInt(4)) {
                case 0 -> Material.POPPY;
                case 1 -> Material.DANDELION;
                case 2 -> Material.OXEYE_DAISY;
                default -> Material.SHORT_GRASS;
            };
            p.place(x, gy + 1, z, flower);
        }

        // Мост через реку.
        int midZ = (z1 + z2) / 2;
        p.fillBox(-6, GROUND_Y, midZ - 4, 6, GROUND_Y, midZ + 4, () -> Material.WATER);
        p.fillBox(-6, GROUND_Y - 1, midZ - 4, 6, GROUND_Y - 1, midZ + 4, () -> Material.DIRT);
        for (int z = midZ - 3; z <= midZ + 3; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                p.place(dx, GROUND_Y + 1, z, Material.STONE_BRICKS);
            }
        }
        for (int z = midZ - 3; z <= midZ + 3; z++) {
            p.place(-4, GROUND_Y + 1, z, Material.STONE_BRICK_WALL);
            p.place(+4, GROUND_Y + 1, z, Material.STONE_BRICK_WALL);
        }

        // Арка «Эликий».
        int arkZ = ELIKIUM_ARCH_Z;
        for (int dy = 1; dy <= 7; dy++) {
            p.place(-5, GROUND_Y + dy, arkZ, Material.STONE_BRICKS);
            p.place(+5, GROUND_Y + dy, arkZ, Material.STONE_BRICKS);
        }
        for (int dx = -5; dx <= 5; dx++) {
            p.place(dx, GROUND_Y + 8, arkZ, Material.STONE_BRICKS);
        }
        p.place(-4, GROUND_Y + 6, arkZ, Material.LANTERN);
        p.place(+4, GROUND_Y + 6, arkZ, Material.LANTERN);
        p.place(0, GROUND_Y + 1, arkZ, Material.DARK_OAK_SIGN);
        p.place(-2, GROUND_Y + 1, arkZ - 1, Material.DARK_OAK_SIGN);
    }

    private void buildLiveTree(RegionPainter p, int x, int z, int gy) {
        int h = 4;
        for (int dy = 0; dy < h; dy++) {
            p.place(x, gy + 1 + dy, z, Material.BIRCH_LOG);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(x + dx, gy + h, z + dz, Material.OAK_LEAVES);
                p.place(x + dx, gy + h + 1, z + dz, Material.OAK_LEAVES);
            }
        }
        p.place(x, gy + h + 2, z, Material.OAK_LEAVES);
    }

    // =========================================================================
    // Phase F — обломки кораблей + летающие острова
    // =========================================================================

    private void paintShipwrecks(RegionPainter p, Random rng) {
        // 4 ржавых остова кораблей на мелководье океана.
        int[][] wrecks = {{-85, -90}, {-30, -95}, {45, -88}, {100, -100}};
        for (int[] w : wrecks) shipwreck(p, w[0], w[1], rng.nextInt(4));
    }

    private void shipwreck(RegionPainter p, int x, int z, int rot) {
        int y = GROUND_Y;
        for (int dx = 0; dx < 8; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                if ((dx + dz * 3) % 5 != 2) {
                    p.place(x + dx, y, z + dz, Material.DARK_OAK_PLANKS);
                }
            }
        }
        for (int dx = 0; dx < 8; dx++) {
            if (dx != 3) {
                p.place(x + dx, y + 1, z, Material.SPRUCE_PLANKS);
                p.place(x + dx, y + 1, z + 3, Material.SPRUCE_PLANKS);
            }
        }
        p.column(x + 3, y + 1, z + 1, 5, () -> Material.DARK_OAK_LOG);
        p.place(x + 3, y + 6, z + 1, Material.COBWEB);
        p.place(x + 2, y + 4, z + 1, Material.GRAY_WOOL);
        p.place(x + 4, y + 4, z + 1, Material.GRAY_WOOL);
        p.place(x + 3, y + 3, z + 1, Material.GRAY_WOOL);
    }

    private void paintFloatingIslands(RegionPainter p, Random rng) {
        floatingIsland(p, CAMP_X + 15, GROUND_Y + 30, CAMP_Z - 10, 5,
                () -> Material.STONE_BRICKS, true, false);
        floatingIsland(p, -30, GROUND_Y + 40, 0, 8,
                () -> Material.COBBLESTONE, false, true);
        floatingIsland(p, 35, GROUND_Y + 25, 30, 5,
                () -> Material.DIRT, false, false);
        floatingNest(p, 35, GROUND_Y + 26, 30);
        floatingIsland(p, -40, GROUND_Y + 35, 120, 4,
                () -> Material.AMETHYST_BLOCK, false, false);
        floatingIsland(p, -50, GROUND_Y + 20, -90, 3,
                () -> Material.DARK_OAK_PLANKS, false, false);
        shipwreck(p, -52, -91, 0);
        floatingIsland(p, ARENA_X, GROUND_Y + 45, ARENA_Z, 6,
                () -> Material.BLACKSTONE, false, false);
        double[] chAng = {45, 135, 225, 315};
        for (double angDeg : chAng) {
            double r = Math.toRadians(angDeg);
            int sx = ARENA_X + (int) Math.round(Math.cos(r) * 11);
            int sz = ARENA_Z + (int) Math.round(Math.sin(r) * 11);
            int y1 = GROUND_Y + 10;
            int y2 = GROUND_Y + 45;
            int steps = y2 - y1;
            for (int k = 0; k < steps; k++) {
                int ix = sx + (int) Math.round((ARENA_X - sx) * (k / (double) steps));
                int iz = sz + (int) Math.round((ARENA_Z - sz) * (k / (double) steps));
                p.place(ix, y1 + k, iz, Material.CHAIN);
            }
        }
    }

    private void floatingIsland(RegionPainter p, int cx, int y, int cz,
                                int radius, Supplier<Material> mat,
                                boolean withPlatform, boolean withRuins) {
        p.fillDisk(cx, y, cz, radius, mat);
        p.fillDisk(cx, y - 1, cz, radius - 1, mat);
        p.fillDisk(cx, y - 2, cz, Math.max(1, radius - 3), mat);
        p.fillDisk(cx, y + 1, cz, radius - 1, () -> Material.MOSS_BLOCK);
        for (int i = 0; i < 6; i++) {
            int ox = -radius + 1 + (i * (radius * 2 - 2)) / 6;
            int oz = (i % 2 == 0) ? -1 : 1;
            int len = 3 + (i % 3);
            for (int k = 0; k < len; k++) {
                p.place(cx + ox, y - 3 - k, cz + oz, Material.HANGING_ROOTS);
            }
        }
        for (int k = 0; k < y - GROUND_Y - 1; k++) {
            p.place(cx, y - 3 - k, cz, Material.HANGING_ROOTS);
        }
        if (withPlatform) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                p.place(cx + dx, y + 2, cz + radius - 1, Material.OAK_FENCE);
                p.place(cx + dx, y + 2, cz - radius + 1, Material.OAK_FENCE);
            }
        }
        if (withRuins) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && Math.random() > 0.3) {
                        p.place(cx + dx, y + 2, cz + dz, Material.COBBLESTONE);
                    }
                }
            }
            p.place(cx, y + 2, cz, Material.CHEST);
        }
    }

    private void floatingNest(RegionPainter p, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) >= 2 && Math.abs(dx) + Math.abs(dz) <= 3) {
                    p.place(cx + dx, y + 1, cz + dz, Material.OAK_PLANKS);
                }
            }
        }
        p.place(cx, y + 1, cz, Material.HAY_BLOCK);
    }

    // =========================================================================
    // Помощники
    // =========================================================================

    private boolean inCamp(int x, int z) {
        int dx = x - CAMP_X, dz = z - CAMP_Z;
        return dx * dx + dz * dz <= (CAMP_RADIUS + 2) * (CAMP_RADIUS + 2);
    }

    private boolean inArena(int x, int z) {
        int dx = x - ARENA_X, dz = z - ARENA_Z;
        return dx * dx + dz * dz <= (ARENA_RADIUS + 2) * (ARENA_RADIUS + 2);
    }

    // =========================================================================
    // Постобработка — таблички
    // =========================================================================

    private void postProcessSigns() {
        setSignLines(CAMP_X + 1, GROUND_Y + 1, CAMP_Z + 12,
                "§5§lПуть испытаний", "§7→ Лес", "§7→ Арена", "§8Хранителя Врат");
        setSignLines(CAMP_X - 1, GROUND_Y + 1, CAMP_Z + 12,
                "§5§l← Берег", "§7Лагерь", "§7безопасен", "");
        setSignLines(-4, GROUND_Y + 2, 40,
                "§d← Арена", "§7Хранителя", "§8(обход)", "");
        setSignLines(+4, GROUND_Y + 2, 40,
                "§dАрена →", "§7Хранителя", "§8(обход)", "");
        setSignLines(0, GROUND_Y + 2, 48,
                "§5§lАрена", "§5§lХранителя Врат", "§c§lПриготовься", "");
        setSignLines(0, GROUND_Y + 12, CAVE_ENTRANCE_Z - 1,
                "§5§lВрата", "§5§lв Эликий", "", "");
        setSignLines(0, GROUND_Y + 1, ELIKIUM_ARCH_Z,
                "§6§lЭликий", "§7город света", "", "");
        setSignLines(-2, GROUND_Y + 1, ELIKIUM_ARCH_Z - 1,
                "§aСтражник:", "§fДобро пожаловать,", "§fпутник!", "");
        setSignLines(CAMP_X + 3, GROUND_Y + 1, CAMP_Z + 4,
                "§6§lПрипасы", "§7сундуки", "§7и бочки", "");
        setSignLines(CAMP_X - 4, GROUND_Y + 1, CAMP_Z + 2,
                "§e§lМанекены", "§7Испытай", "§7свой навык", "");
        setSignLines(0, GROUND_Y + 2, CAMP_Z + 12,
                "§d§l→ Путь", "§d§lиспытаний", "§7Арена: 140 блоков", "");
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
