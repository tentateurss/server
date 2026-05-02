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

        // === СТОПКИ ДРОВ — вдоль улиц ===
        for (int[] pos : new int[][]{
                {0, 60}, {20, 0}, {-50, 10},
                {60, 20}, {-20, 70}, {80, -20},
                {110, 50}, {115, 48}, {-15, -50}, {-65, -25},
                {-40, 80}, {30, 70}, {-100, -20}, {90, -60},
                {-60, 40}, {50, 100}, {-80, 100}}) {
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

        // === БОЧКИ — разбросаны по улицам ===
        for (int[] pos : new int[][]{
                {-50, 20}, {50, -20}, {-80, -40}, {80, 40},
                {-30, 70}, {30, -70}, {-100, 30}, {100, -30},
                {-60, 80}, {60, -80}, {-20, 40}, {20, -40},
                {0, 50}, {0, -50}, {-40, 0}, {40, 0}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 1, pos[1], Material.BARREL);
                count++;
            }
        }

        // === НАСТЕННЫЕ ФОНАРИ (SOUL_TORCH) — вдоль улиц ===
        for (int[] pos : new int[][]{
                {-40, 20}, {40, -20}, {-70, 0}, {70, 0},
                {-100, 50}, {100, -50}, {-20, 80}, {20, -80},
                {-60, 60}, {60, -60}, {-30, -40}, {30, 40},
                {-80, 80}, {80, -80}, {-110, 0}, {110, 0},
                {0, 70}, {0, -70}, {-50, -30}, {50, 30}}) {
            if (WorldGenerator.isInsideCityPolygon(pos[0], pos[1])
                    && !ElikiumCity.insideCathedralZone(pos[0], pos[1])) {
                painter.place(pos[0], Y_BASE + 4, pos[1], Material.SOUL_TORCH);
                count++;
            }
        }

        // === КОВРЫ PURPLE_CARPET у важных зданий ===
        painter.place(-30, Y_BASE + 1, -18, Material.PURPLE_CARPET); // таверна
        painter.place(-30, Y_BASE + 1, 29, Material.PURPLE_CARPET);  // лавка
        painter.place(105, Y_BASE + 1, -43, Material.PURPLE_CARPET); // гильдия
        count += 3;

        // === УКАЗАТЕЛИ на перекрёстках (через POI) ===
        ctx.pois.add(new ElikiumCity.POI("§f→ Собор", "§7прямо",
                10, Y_BASE + 5, 30));
        ctx.pois.add(new ElikiumCity.POI("§f← Ворота", "§7налево",
                -10, Y_BASE + 5, 80));
        ctx.pois.add(new ElikiumCity.POI("§f↑ Таверна", "§7направо",
                -20, Y_BASE + 5, -10));

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
