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

        // === ТЕЛЕГИ — 8 шт, разбросаны по городу ===
        count += buildCart(-22, 35, "X");
        count += buildCart(-38, 55, "Z");
        count += buildCart(WorldGenerator.SOUTH_GATE[0] - 4, WorldGenerator.SOUTH_GATE[1] - 8, "Z");
        count += buildCart(WorldGenerator.NORTH_GATE[0] + 6, WorldGenerator.NORTH_GATE[1] + 8, "Z");
        count += buildCart(70, 0, "X");
        count += buildCart(-80, -40, "X");
        count += buildCart(90, 70, "Z");
        count += buildCart(-110, 30, "X");

        // === СТОПКИ ДРОВ — массово вдоль улиц ===
        for (int[] pos : new int[][]{
                {0, 60}, {20, 0}, {-50, 10},
                {60, 20}, {-20, 70}, {80, -20},
                {110, 50}, {115, 48}, {-15, -50}, {-65, -25},
                {-40, 80}, {30, 70}, {-100, -20}, {90, -60},
                {-60, 40}, {50, 100}, {-80, 100},
                {-120, 50}, {120, -50}, {-70, -60}, {70, 80},
                {-35, -80}, {35, 80}, {-90, 30}, {90, -30},
                {-130, 0}, {130, 0}, {-50, -100}, {50, 100},
                {-75, 10}, {75, -10}, {-45, -35}, {45, 35},
                {-110, -70}, {110, 70}, {-25, -110}, {25, 110},
                {-85, 60}, {85, -60}, {-55, -85}, {55, 85},
                {-130, 40}, {130, -40}, {-15, 95}, {15, -95},
                {-100, 90}, {100, -90}, {-60, -110}, {60, 110}}) {
            count += buildLogStack(pos[0], pos[1]);
        }

        // === СТОПКИ СЕНА ===
        for (int[] pos : new int[][]{
                {-95, 10}, {-92, -5},
                {-30, -30},
                {0, 80}, {-5, 80},
                {70, 90}, {-110, 60},
                {100, 40}, {-60, -50},
                {-120, -30}, {120, 30},
                {-80, 80}, {80, -80},
                {-40, -90}, {40, 90},
                {-100, -80}, {100, 80}}) {
            for (int i = 0; i < 3; i++) {
                painter.place(pos[0] + i, Y_BASE + 2, pos[1], Material.HAY_BLOCK);
                count++;
            }
            // Дополнительный тюк сверху для объёма
            if (rng.nextDouble() < 0.5) {
                painter.place(pos[0] + 1, Y_BASE + 3, pos[1], Material.HAY_BLOCK);
                count++;
            }
        }

        // === ОРУЖЕЙНАЯ СТОЙКА у кузницы ===
        count += buildWeaponRack(108, 60);

        // === УЛИЧНЫЕ СКАМЕЙКИ — массово вдоль улиц и на расширениях ===
        for (int[] s : new int[][]{
                {-25, 50, 1}, {-35, 50, 0},
                {30, 25, 1}, {60, 25, 0},
                {-60, 0, 0}, {30, -30, 1},
                {-80, 30, 1}, {80, 40, 0},
                {-30, 80, 1}, {50, -50, 0},
                {-100, -50, 1}, {110, -40, 0},
                {-40, -70, 0}, {70, -80, 1},
                {0, 95, 1}, {20, 100, 0},
                {-55, -30, 1}, {55, 30, 0},
                {-90, 60, 1}, {90, -60, 0},
                {-10, -40, 0}, {10, 40, 1},
                {-70, -90, 0}, {70, 90, 1},
                {-120, 0, 1}, {120, 0, 0},
                {-45, 100, 1}, {45, -100, 0},
                {-85, -20, 0}, {85, 20, 1},
                {-15, 70, 1}, {15, -70, 0}}) {
            count += buildBench(s[0], s[1], s[2]);
        }

        // === ЦВЕТОЧНЫЕ ГОРШКИ — массово по всему городу ===
        for (int[] p : new int[][]{
                {38, 25}, {52, 25}, {38, 51}, {52, 51},
                {-38, 36}, {-22, 36}, {-38, 54}, {-22, 54},
                {0, 110}, {-5, 110},
                {-60, 10}, {-60, -10}, {60, 10}, {60, -10},
                {-80, 40}, {80, -40}, {-40, 60}, {40, 60},
                {-100, 70}, {100, -70}, {0, -80}, {0, 80},
                {-30, -60}, {30, -60}, {-20, 100}, {20, 100},
                {-70, 20}, {70, -20}, {-50, -40}, {50, 40},
                {-90, 50}, {90, -50}, {-110, 10}, {110, -10},
                {-35, -90}, {35, 90}, {-75, -50}, {75, 50},
                {-120, 60}, {120, -60}, {-45, 75}, {45, -75},
                {-85, -30}, {85, 30}, {-15, -60}, {15, 60},
                {-130, 20}, {130, -20}, {-55, -70}, {55, 70},
                {-95, 80}, {95, -80}, {-25, 90}, {25, -90},
                {-105, -40}, {105, 40}, {-65, 100}, {65, -100}}) {
            painter.place(p[0], Y_BASE + 1, p[1], Material.STONE_BRICKS);
            painter.place(p[0], Y_BASE + 2, p[1], Material.FLOWER_POT);
            count += 2;
        }

        // === БОЧКИ — массово по всему городу ===
        for (int[] pos : new int[][]{
                {-50, 20}, {50, -20}, {-80, -40}, {80, 40},
                {-30, 70}, {30, -70}, {-100, 30}, {100, -30},
                {-60, 80}, {60, -80}, {-20, 40}, {20, -40},
                {0, 50}, {0, -50}, {-40, 0}, {40, 0},
                {-120, 30}, {120, -30}, {-70, 100}, {70, -100},
                {-45, -45}, {45, 45}, {-90, -60}, {90, 60},
                {-110, 80}, {110, -80}, {-25, -90}, {25, 90},
                {-52, -25}, {52, 25}, {-82, 10}, {82, -10},
                {-35, 60}, {35, -60}, {-105, -20}, {105, 20},
                {-65, -65}, {65, 65}, {-95, 40}, {95, -40},
                {-15, 80}, {15, -80}, {-75, -75}, {75, 75},
                {-125, 10}, {125, -10}, {-40, -80}, {40, 80},
                {-88, 70}, {88, -70}, {-55, 95}, {55, -95},
                {-10, -65}, {10, 65}, {-115, -50}, {115, 50},
                {-48, -55}, {48, 55}, {-130, 30}, {130, -30}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1], Material.BARREL);
                // Дополнительная бочка рядом (40% шанс)
                if (rng.nextDouble() < 0.4) {
                    painter.place(pos[0] + 1, Y_BASE + 1, pos[1], Material.BARREL);
                    count++;
                }
                count++;
            }
        }

        // === НАСТЕННЫЕ ФОНАРИ (SOUL_LANTERN и SOUL_TORCH) — по всему городу ===
        for (int[] pos : new int[][]{
                {-40, 20}, {40, -20}, {-70, 0}, {70, 0},
                {-100, 50}, {100, -50}, {-20, 80}, {20, -80},
                {-60, 60}, {60, -60}, {-30, -40}, {30, 40},
                {-80, 80}, {80, -80}, {-110, 0}, {110, 0},
                {0, 70}, {0, -70}, {-50, -30}, {50, 30},
                {-120, 40}, {120, -40}, {-90, -70}, {90, 70},
                {-130, 20}, {130, -20}, {-45, -100}, {45, 100},
                {-70, -40}, {70, 40}, {-35, 110}, {35, -110},
                {-55, 15}, {55, -15}, {-85, 25}, {85, -25},
                {-105, -35}, {105, 35}, {-25, 55}, {25, -55},
                {-75, 85}, {75, -85}, {-15, -95}, {15, 95},
                {-95, -45}, {95, 45}, {-65, 70}, {65, -70},
                {-115, 55}, {115, -55}, {-5, 100}, {5, -100},
                {-45, -65}, {45, 65}, {-135, 0}, {135, 0},
                {-85, -55}, {85, 55}, {-35, 75}, {35, -75}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 4, pos[1], Material.SOUL_LANTERN);
                count++;
            }
        }

        // === КОВРЫ PURPLE_CARPET у важных зданий ===
        painter.place(-30, Y_BASE + 1, -18, Material.PURPLE_CARPET); // таверна
        painter.place(-30, Y_BASE + 1, 29, Material.PURPLE_CARPET);  // лавка
        painter.place(105, Y_BASE + 1, -43, Material.PURPLE_CARPET); // гильдия
        count += 3;

        // === ЛОЗЫ (VINE) на старых домах ===
        for (int[] pos : new int[][]{
                {-80, -30}, {80, 30}, {-120, 50}, {120, -50},
                {-60, -80}, {60, 80}, {-100, 70}, {100, -70},
                {-55, -50}, {55, 50}, {-110, -20}, {110, 20},
                {-70, 95}, {70, -95}, {-90, -45}, {90, 45}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                for (int dy = 0; dy < 4 + rng.nextInt(4); dy++) {
                    painter.place(pos[0], Y_BASE + 3 + dy, pos[1], Material.VINE);
                    count++;
                }
            }
        }

        // === ЯЩИКИ (OAK_PLANKS + OAK_SLAB сверху) ===
        for (int[] pos : new int[][]{
                {-48, 15}, {48, -15}, {-75, -35}, {75, 35},
                {-28, 65}, {28, -65}, {-95, 20}, {95, -20},
                {-65, -55}, {65, 55}, {-115, 40}, {115, -40},
                {-35, 85}, {35, -85}, {-105, -10}, {105, 10}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1], Material.OAK_PLANKS);
                BlockData slab = Material.OAK_SLAB.createBlockData("[type=top]");
                painter.placeData(pos[0], Y_BASE + 2, pos[1], slab);
                count += 2;
            }
        }

        // === УКАЗАТЕЛИ на перекрёстках (через POI) ===
        ctx.pois.add(new ElikiumCity.POI("§f→ Собор", "§7прямо",
                10, Y_BASE + 5, 30));
        ctx.pois.add(new ElikiumCity.POI("§f← Ворота", "§7налево",
                -10, Y_BASE + 5, 80));
        ctx.pois.add(new ElikiumCity.POI("§f↑ Таверна", "§7направо",
                -20, Y_BASE + 5, -10));
        ctx.pois.add(new ElikiumCity.POI("§f→ Кузница", "§7направо",
                70, Y_BASE + 5, 40));
        ctx.pois.add(new ElikiumCity.POI("§f← Рынок", "§7налево",
                -50, Y_BASE + 5, 50));

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
