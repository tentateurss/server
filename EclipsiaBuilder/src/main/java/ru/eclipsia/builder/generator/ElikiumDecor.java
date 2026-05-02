package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Уличный декор v2 — МАССИВНОЕ количество мелочей на улицах между POI и
 * домами. Город МЁРТВ без деталей — бочки, ящики, цветы, фонари,
 * скамейки, телеги, дрова, сено, лозы ВЕЗДЕ.
 *
 * <p>Детали привязаны к ключевым точкам и размазаны по всему городу:
 * <ul>
 *   <li>У ВХОДОВ: BARREL, FLOWER_POT, PURPLE_CARPET</li>
 *   <li>ВДОЛЬ УЛИЦ: скамейки OAK_STAIRS, дрова OAK_LOG, HAY_BALE</li>
 *   <li>НА СТЕНАХ: SOUL_TORCH, VINE, висячие горшки</li>
 *   <li>У ВОРОТ: будки стражи 3×3, WATER_CAULDRON, SOUL_TORCH</li>
 *   <li>2-3 телеги на улицах</li>
 * </ul>
 */
public final class ElikiumDecor {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final ElikiumCity ctx;

    public ElikiumDecor(Plugin plugin, RegionPainter painter, Random rng, ElikiumCity ctx) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.ctx = ctx;
    }

    public long build() {
        long count = 0;

        // === ТЕЛЕГИ — 5 шт, разбросаны по городу ===
        count += buildCart(-22, 35, "X");
        count += buildCart(-38, 55, "Z");
        count += buildCart(WorldGenerator.SOUTH_GATE[0] - 4, WorldGenerator.SOUTH_GATE[1] - 8, "Z");
        count += buildCart(WorldGenerator.NORTH_GATE[0] + 6, WorldGenerator.NORTH_GATE[1] + 8, "Z");
        count += buildCart(70, 0, "X");

        // === СТОПКИ ДРОВ — вдоль улиц (больше для плотности) ===
        for (int[] pos : new int[][]{
                {0, 60}, {20, 0}, {-50, 10},
                {60, 20}, {-20, 70}, {80, -20},
                {110, 50}, {115, 48}, {-15, -50}, {-65, -25},
                {-40, 80}, {30, 70}, {-100, -20}, {90, -60},
                {-60, 40}, {50, 100}, {-80, 100},
                {-120, 50}, {120, -50}, {-70, -60}, {70, 80},
                {-35, -80}, {35, 80}, {-90, 30}, {90, -30},
                {-130, 0}, {130, 0}, {-50, -100}, {50, 100}}) {
            count += buildLogStack(pos[0], pos[1]);
        }

        // === СТОПКИ СЕНА ===
        for (int[] pos : new int[][]{
                {-95, 10}, {-92, -5},
                {-30, -30},
                {0, 80}, {-5, 80},
                {70, 90}, {-110, 60},
                {100, 40}, {-60, -50}}) {
            for (int i = 0; i < 3; i++) {
                painter.place(pos[0] + i, Y_BASE + 2, pos[1], Material.HAY_BLOCK);
                count++;
            }
        }

        // === ОРУЖЕЙНАЯ СТОЙКА у кузницы ===
        count += buildWeaponRack(108, 60);

        // === УЛИЧНЫЕ СКАМЕЙКИ — много, вдоль улиц и на расширениях ===
        for (int[] s : new int[][]{
                {-25, 50, 1}, {-35, 50, 0},
                {30, 25, 1}, {60, 25, 0},
                {-60, 0, 0}, {30, -30, 1},
                {-80, 30, 1}, {80, 40, 0},
                {-30, 80, 1}, {50, -50, 0},
                {-100, -50, 1}, {110, -40, 0},
                {-40, -70, 0}, {70, -80, 1},
                {0, 95, 1}, {20, 100, 0}}) {
            count += buildBench(s[0], s[1], s[2]);
        }

        // === ЦВЕТОЧНЫЕ ГОРШКИ — массово у площадей и перекрёстков ===
        for (int[] p : new int[][]{
                {38, 25}, {52, 25}, {38, 51}, {52, 51},
                {-38, 36}, {-22, 36}, {-38, 54}, {-22, 54},
                {0, 110}, {-5, 110},
                {-60, 10}, {-60, -10}, {60, 10}, {60, -10},
                {-80, 40}, {80, -40}, {-40, 60}, {40, 60},
                {-100, 70}, {100, -70}, {0, -80}, {0, 80},
                {-30, -60}, {30, -60}, {-20, 100}, {20, 100}}) {
            painter.place(p[0], Y_BASE + 1, p[1], Material.STONE_BRICKS);
            painter.place(p[0], Y_BASE + 2, p[1], Material.FLOWER_POT);
            count += 2;
        }

        // === БОЧКИ — разбросаны по улицам (больше для плотности) ===
        for (int[] pos : new int[][]{
                {-50, 20}, {50, -20}, {-80, -40}, {80, 40},
                {-30, 70}, {30, -70}, {-100, 30}, {100, -30},
                {-60, 80}, {60, -80}, {-20, 40}, {20, -40},
                {0, 50}, {0, -50}, {-40, 0}, {40, 0},
                {-120, 30}, {120, -30}, {-70, 100}, {70, -100},
                {-45, -45}, {45, 45}, {-90, -60}, {90, 60},
                {-110, 80}, {110, -80}, {-25, -90}, {25, 90}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1], Material.BARREL);
                count++;
            }
        }

        // === НАСТЕННЫЕ ФОНАРИ (SOUL_LANTERN и SOUL_TORCH) — вдоль улиц ===
        for (int[] pos : new int[][]{
                {-40, 20}, {40, -20}, {-70, 0}, {70, 0},
                {-100, 50}, {100, -50}, {-20, 80}, {20, -80},
                {-60, 60}, {60, -60}, {-30, -40}, {30, 40},
                {-80, 80}, {80, -80}, {-110, 0}, {110, 0},
                {0, 70}, {0, -70}, {-50, -30}, {50, 30},
                {-120, 40}, {120, -40}, {-90, -70}, {90, 70},
                {-130, 20}, {130, -20}, {-45, -100}, {45, 100},
                {-70, -40}, {70, 40}, {-35, 110}, {35, -110}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 4, pos[1], Material.SOUL_LANTERN);
                count++;
            }
        }

        // === КОВРЫ PURPLE_CARPET у важных зданий ===
        painter.place(-30, Y_BASE + 1, -18, Material.PURPLE_CARPET);
        painter.place(-30, Y_BASE + 1, 29, Material.PURPLE_CARPET);
        painter.place(105, Y_BASE + 1, -43, Material.PURPLE_CARPET);
        count += 3;

        // === РЫНОЧНЫЕ ПАЛАТКИ — навесы с товарами ===
        count += buildMarketStall(-25, 48, "X");
        count += buildMarketStall(-15, 48, "X");
        count += buildMarketStall(-35, 48, "X");
        count += buildMarketStall(35, 30, "Z");
        count += buildMarketStall(40, 30, "Z");
        count += buildMarketStall(-5, 65, "X");
        count += buildMarketStall(5, 65, "X");
        count += buildMarketStall(-45, 0, "Z");
        count += buildMarketStall(55, -5, "Z");
        count += buildMarketStall(-20, -35, "X");
        count += buildMarketStall(85, 35, "X");
        count += buildMarketStall(-85, 60, "Z");

        // === ДОПОЛНИТЕЛЬНЫЕ БОЧКИ (ещё 20) ===
        for (int[] pos : new int[][]{
                {-15, 20}, {15, -20}, {-60, -30}, {60, 30},
                {-95, 50}, {95, -50}, {-35, 90}, {35, -90},
                {-75, -15}, {75, 15}, {-5, -35}, {5, 35},
                {-110, -10}, {110, 10}, {-55, 70}, {55, -70},
                {-25, -55}, {25, 55}, {-85, 85}, {85, -85}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1], Material.BARREL);
                if (rng.nextBoolean()) {
                    painter.place(pos[0], Y_BASE + 2, pos[1], Material.BARREL);
                }
                count += 2;
            }
        }

        // === ДОПОЛНИТЕЛЬНЫЕ ЦВЕТОЧНЫЕ ГОРШКИ (ещё 16) ===
        for (int[] p : new int[][]{
                {-70, 20}, {70, -20}, {-30, 50}, {30, -50},
                {-95, -30}, {95, 30}, {-45, 75}, {45, -75},
                {-15, -65}, {15, 65}, {-110, 45}, {110, -45},
                {-55, -85}, {55, 85}, {-80, 110}, {80, -110}}) {
            if (WorldGenerator.isInsideCityPolygon(p[0], p[1])
                    && !ElikiumCity.insideCathedralZone(p[0], p[1])) {
                painter.place(p[0], Y_BASE + 1, p[1], Material.STONE_BRICKS);
                painter.place(p[0], Y_BASE + 2, p[1], Material.FLOWER_POT);
                count += 2;
            }
        }

        // === ДОПОЛНИТЕЛЬНЫЕ ФОНАРИ (ещё 20) ===
        for (int[] pos : new int[][]{
                {-25, 10}, {25, -10}, {-55, 40}, {55, -40},
                {-85, -20}, {85, 20}, {-15, 75}, {15, -75},
                {-105, 60}, {105, -60}, {-65, -50}, {65, 50},
                {-35, 95}, {35, -95}, {-75, 30}, {75, -30},
                {-115, -40}, {115, 40}, {-45, 110}, {45, -110}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 4, pos[1], Material.SOUL_LANTERN);
                count++;
            }
        }

        // === ЯЩИКИ (CHEST) и CRAFTING_TABLE по городу ===
        for (int[] pos : new int[][]{
                {-40, 15}, {40, -15}, {-70, 45}, {70, -45},
                {-100, -25}, {100, 25}, {-20, 85}, {20, -85},
                {-55, -55}, {55, 55}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1],
                        rng.nextBoolean() ? Material.CHEST : Material.CRAFTING_TABLE);
                count++;
            }
        }

        // === УКАЗАТЕЛИ на перекрёстках (через POI) ===
        ctx.pois.add(new ElikiumCity.POI("§f→ Собор", "§7прямо",
                10, Y_BASE + 5, 30));
        ctx.pois.add(new ElikiumCity.POI("§f← Ворота", "§7налево",
                -10, Y_BASE + 5, 80));
        ctx.pois.add(new ElikiumCity.POI("§f↑ Таверна", "§7направо",
                -20, Y_BASE + 5, -10));

        return count;
    }

    /**
     * Рыночная палатка: 4 столба DARK_OAK_FENCE + навес из PURPLE_WOOL +
     * прилавок SPRUCE_STAIRS + товары (BARREL, MELON, PUMPKIN, LANTERN).
     */
    private long buildMarketStall(int cx, int cz, String axis) {
        long count = 0;
        if (!WorldGenerator.isInsideCityPolygon(cx, cz)) return 0;
        if (ElikiumCity.insideCathedralZone(cx, cz)) return 0;
        int len = 3, width = 2;
        // 4 столба
        for (int dy = 1; dy <= 4; dy++) {
            if ("X".equals(axis)) {
                painter.place(cx - len, Y_BASE + dy, cz - width, Material.DARK_OAK_FENCE);
                painter.place(cx + len, Y_BASE + dy, cz - width, Material.DARK_OAK_FENCE);
                painter.place(cx - len, Y_BASE + dy, cz + width, Material.DARK_OAK_FENCE);
                painter.place(cx + len, Y_BASE + dy, cz + width, Material.DARK_OAK_FENCE);
            } else {
                painter.place(cx - width, Y_BASE + dy, cz - len, Material.DARK_OAK_FENCE);
                painter.place(cx + width, Y_BASE + dy, cz - len, Material.DARK_OAK_FENCE);
                painter.place(cx - width, Y_BASE + dy, cz + len, Material.DARK_OAK_FENCE);
                painter.place(cx + width, Y_BASE + dy, cz + len, Material.DARK_OAK_FENCE);
            }
            count += 4;
        }
        // Навес из шерсти
        Material wool = rng.nextBoolean() ? Material.PURPLE_WOOL : Material.GRAY_WOOL;
        if ("X".equals(axis)) {
            for (int dx = -len; dx <= len; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    painter.place(cx + dx, Y_BASE + 5, cz + dz, wool);
                    count++;
                }
            }
        } else {
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -len; dz <= len; dz++) {
                    painter.place(cx + dx, Y_BASE + 5, cz + dz, wool);
                    count++;
                }
            }
        }
        // Прилавок (столешница из ступенек)
        BlockData slab = Material.SPRUCE_SLAB.createBlockData("[type=top]");
        if ("X".equals(axis)) {
            for (int dx = -len + 1; dx <= len - 1; dx++) {
                painter.placeData(cx + dx, Y_BASE + 1, cz, slab);
                count++;
            }
        } else {
            for (int dz = -len + 1; dz <= len - 1; dz++) {
                painter.placeData(cx, Y_BASE + 1, cz + dz, slab);
                count++;
            }
        }
        // Товары на прилавке
        Material[] goods = {Material.MELON, Material.PUMPKIN, Material.BARREL,
                Material.LANTERN, Material.CAKE};
        if ("X".equals(axis)) {
            for (int dx = -1; dx <= 1; dx++) {
                Material item = goods[rng.nextInt(goods.length)];
                painter.place(cx + dx, Y_BASE + 2, cz, item);
                count++;
            }
        } else {
            for (int dz = -1; dz <= 1; dz++) {
                Material item = goods[rng.nextInt(goods.length)];
                painter.place(cx, Y_BASE + 2, cz + dz, item);
                count++;
            }
        }
        // Фонарь
        painter.place(cx, Y_BASE + 4, cz, Material.SOUL_LANTERN);
        count++;
        return count;
    }

    /** Простая телега: 4×2 платформа + 2 колеса + оглобли + сундук. */
    private long buildCart(int cx, int cz, String axis) {
        long count = 0;
        if ("X".equals(axis)) {
            for (int dx = -1; dx <= 2; dx++) {
                painter.place(cx + dx, Y_BASE + 2, cz, Material.OAK_PLANKS);
                painter.place(cx + dx, Y_BASE + 2, cz + 1, Material.OAK_PLANKS);
                count += 2;
            }
            painter.place(cx - 1, Y_BASE + 1, cz, Material.OAK_LOG);
            painter.place(cx + 2, Y_BASE + 1, cz, Material.OAK_LOG);
            painter.place(cx - 1, Y_BASE + 1, cz + 1, Material.OAK_LOG);
            painter.place(cx + 2, Y_BASE + 1, cz + 1, Material.OAK_LOG);
            painter.place(cx + 3, Y_BASE + 2, cz, Material.OAK_FENCE);
            painter.place(cx + 4, Y_BASE + 2, cz, Material.OAK_FENCE);
            painter.place(cx + 3, Y_BASE + 2, cz + 1, Material.OAK_FENCE);
            painter.place(cx + 4, Y_BASE + 2, cz + 1, Material.OAK_FENCE);
            painter.place(cx, Y_BASE + 3, cz, Material.CHEST);
            count += 9;
        } else {
            for (int dz = -1; dz <= 2; dz++) {
                painter.place(cx, Y_BASE + 2, cz + dz, Material.OAK_PLANKS);
                painter.place(cx + 1, Y_BASE + 2, cz + dz, Material.OAK_PLANKS);
                count += 2;
            }
            painter.place(cx, Y_BASE + 1, cz - 1, Material.OAK_LOG);
            painter.place(cx, Y_BASE + 1, cz + 2, Material.OAK_LOG);
            painter.place(cx + 1, Y_BASE + 1, cz - 1, Material.OAK_LOG);
            painter.place(cx + 1, Y_BASE + 1, cz + 2, Material.OAK_LOG);
            painter.place(cx, Y_BASE + 2, cz + 3, Material.OAK_FENCE);
            painter.place(cx, Y_BASE + 2, cz + 4, Material.OAK_FENCE);
            painter.place(cx + 1, Y_BASE + 2, cz + 3, Material.OAK_FENCE);
            painter.place(cx + 1, Y_BASE + 2, cz + 4, Material.OAK_FENCE);
            painter.place(cx, Y_BASE + 3, cz, Material.CHEST);
            count += 9;
        }
        return count;
    }

    /** Оружейная стойка: DARK_OAK_FENCE + IRON_BARS + ANVIL + GRINDSTONE. */
    private long buildWeaponRack(int cx, int cz) {
        long count = 0;
        for (int dy = 1; dy <= 3; dy++) {
            painter.place(cx, Y_BASE + dy, cz, Material.DARK_OAK_FENCE);
            painter.place(cx + 3, Y_BASE + dy, cz, Material.DARK_OAK_FENCE);
            count += 2;
        }
        for (int dx = 1; dx <= 2; dx++) {
            painter.place(cx + dx, Y_BASE + 3, cz, Material.IRON_BARS);
            count++;
        }
        painter.place(cx + 1, Y_BASE + 1, cz - 1, Material.ANVIL);
        painter.place(cx + 2, Y_BASE + 1, cz - 1, Material.GRINDSTONE);
        count += 2;
        return count;
    }

    /** Уличная скамейка из OAK_STAIRS — 3 ступеньки в ряд. */
    private long buildBench(int cx, int cz, int dir) {
        long count = 0;
        String facingStr;
        int dx = 0, dz = 0;
        switch (dir) {
            case 0: facingStr = "east";  dz = 1; break;
            case 1: facingStr = "south"; dx = 1; break;
            case 2: facingStr = "west";  dz = 1; break;
            default: facingStr = "north"; dx = 1; break;
        }
        BlockData stair = Material.OAK_STAIRS.createBlockData(
                "[facing=" + facingStr + ",half=bottom]");
        for (int i = 0; i < 3; i++) {
            painter.placeData(cx + dx * i, Y_BASE + 1, cz + dz * i, stair);
            count++;
        }
        return count;
    }

    /** Стопка дров — 3×2×2 OAK_LOG. */
    private long buildLogStack(int cx, int cz) {
        long count = 0;
        if (!WorldGenerator.isInsideCityPolygon(cx, cz)) return 0;
        if (ElikiumCity.insideCathedralZone(cx, cz)) return 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockData log = Material.OAK_LOG.createBlockData("[axis=x]");
                painter.placeData(cx + dx, Y_BASE + 2 + dy, cz, log);
                count++;
            }
        }
        return count;
    }
}
