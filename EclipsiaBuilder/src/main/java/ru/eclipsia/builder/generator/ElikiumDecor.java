package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Уличный декор: дополнительные мелочи на улицах между POI и домами.
 * Настенные SOUL_TORCH, телега у рынка, стопки сена/дров, водостоки.
 *
 * <p>Детали привязаны к ключевым точкам, чтобы выглядели уместно:
 * скамьи и горшки у плаз, дрова у кузницы, сено у склада, телеги у
 * рыночной площади и ворот.
 *
 * <p>Декор у входов в индивидуальные дома — в {@link ElikiumHouses}.
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

        // Телеги у рыночной площади
        count += buildCart(-22, 35, "X");
        count += buildCart(-38, 55, "Z");

        // Телега у южных ворот
        count += buildCart(WorldGenerator.SOUTH_GATE[0] - 4, WorldGenerator.SOUTH_GATE[1] - 8, "Z");

        // Стопки дров вдоль главных улиц (несколько кучек)
        for (int[] pos : new int[][]{
                {0, 60}, {20, 0}, {-50, 10},
                {60, 20}, {-20, 70}, {80, -20}}) {
            count += buildLogStack(pos[0], pos[1]);
        }

        // Стопки сена
        for (int[] pos : new int[][]{
                {-95, 10}, {-92, -5}, // у склада
                {-30, -30}}) { // у таверны
            for (int i = 0; i < 3; i++) {
                painter.place(pos[0] + i, Y_BASE + 2, pos[1], Material.HAY_BLOCK);
                count++;
            }
        }

        // Водостоки IRON_BARS на перекрёстках
        for (int[] pos : new int[][]{
                {15, 80}, {-25, -75}, {105, 30}, {0, 0}, {-50, 30}, {-70, 0}, {60, -30}}) {
            int x = pos[0], z = pos[1];
            painter.place(x, Y_BASE, z, Material.IRON_BARS);
            // Под решёткой — WATER
            painter.place(x, Y_BASE - 1, z, Material.WATER);
            count += 2;
        }

        // Канавки COBBLESTONE_WALL вдоль главных улиц с водой
        for (int[] pos : new int[][]{
                {12, 60, 18, 60},
                {-30, -10, -30, -40},
                {80, 30, 95, 30},
                {-35, 50, -55, 50}}) {
            count += buildDrainChannel(pos[0], pos[1], pos[2], pos[3]);
        }

        return count;
    }

    /** Простая телега: 4×2 платформа + 2 колеса. */
    private long buildCart(int cx, int cz, String axis) {
        long count = 0;
        if ("X".equals(axis)) {
            for (int dx = -1; dx <= 2; dx++) {
                painter.place(cx + dx, Y_BASE + 2, cz, Material.OAK_PLANKS);
                painter.place(cx + dx, Y_BASE + 2, cz + 1, Material.OAK_PLANKS);
                count += 2;
            }
            // Колёса
            painter.place(cx - 1, Y_BASE + 1, cz, Material.OAK_LOG);
            painter.place(cx + 2, Y_BASE + 1, cz, Material.OAK_LOG);
            painter.place(cx - 1, Y_BASE + 1, cz + 1, Material.OAK_LOG);
            painter.place(cx + 2, Y_BASE + 1, cz + 1, Material.OAK_LOG);
            // Оглобли OAK_FENCE
            painter.place(cx + 3, Y_BASE + 2, cz, Material.OAK_FENCE);
            painter.place(cx + 4, Y_BASE + 2, cz, Material.OAK_FENCE);
            painter.place(cx + 3, Y_BASE + 2, cz + 1, Material.OAK_FENCE);
            painter.place(cx + 4, Y_BASE + 2, cz + 1, Material.OAK_FENCE);
            // Сундук на телеге
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

    /** Стопка дров — 3×2×2 OAK_LOG. */
    private long buildLogStack(int cx, int cz) {
        long count = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockData log = Material.OAK_LOG.createBlockData("[axis=x]");
                painter.placeData(cx + dx, Y_BASE + 2 + dy, cz, log);
                count++;
            }
        }
        return count;
    }

    /** Канавка водостока — STONE_BRICKS + WATER + COBBLESTONE_WALL стенки. */
    private long buildDrainChannel(int x1, int z1, int x2, int z2) {
        long count = 0;
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        int cx = x1, cz = z1;
        while (true) {
            // Канавка y=Y_BASE WATER (в углублении)
            painter.place(cx, Y_BASE - 1, cz, Material.STONE_BRICKS);
            painter.place(cx, Y_BASE, cz, Material.WATER);
            // Стенки
            if (Math.abs(x2 - x1) > Math.abs(z2 - z1)) {
                painter.place(cx, Y_BASE, cz + 1, Material.COBBLESTONE_WALL);
                painter.place(cx, Y_BASE, cz - 1, Material.COBBLESTONE_WALL);
            } else {
                painter.place(cx + 1, Y_BASE, cz, Material.COBBLESTONE_WALL);
                painter.place(cx - 1, Y_BASE, cz, Material.COBBLESTONE_WALL);
            }
            count += 4;
            if (cx == x2 && cz == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 < dx)  { err += dx; cz += sz; }
        }
        return count;
    }
}
