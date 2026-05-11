package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Площади Эликия v2 — увеличенная соборная (17×23 перед собором) и
 * рыночная (15×19 на западе). Обе помечают зону как occupied.
 *
 * <p><b>Соборная площадь</b> (центр (45, 38)):
 * <ul>
 *   <li>Концентрические кольца мощения: GILDED_BLACKSTONE центр →
 *       POLISHED_BLACKSTONE_BRICKS → POLISHED_BLACKSTONE → DEEPSLATE_TILES;</li>
 *   <li>Золотой крест 7×7 из GOLD_BLOCK;</li>
 *   <li>4 высоких фонарных столба (OAK_FENCE 5 + SOUL_LANTERN);</li>
 *   <li>2 клумбы 3×2 с ALLIUM/LILAC;</li>
 *   <li>4 скамейки (OAK_STAIRS);</li>
 *   <li>Колокол на OAK_FENCE столбе 5 высотой;</li>
 * </ul>
 *
 * <p><b>Рыночная площадь</b> (центр (-30, 45)):
 * <ul>
 *   <li>Мощение POLISHED_DEEPSLATE+ANDESITE;</li>
 *   <li>5 прилавков с навесами;</li>
 *   <li>Центральный фонтан 3×3 (STONE_BRICKS + WATER + SEA_LANTERN);</li>
 *   <li>4 лавочки, бочки;</li>
 * </ul>
 */
public final class ElikiumPlazas {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    /** Соборная площадь — увеличенная. */
    static final int CATH_PLAZA_CX = 45;
    static final int CATH_PLAZA_CZ = 38;
    static final int CATH_PLAZA_HALF_X = 10;  // 21 wide
    static final int CATH_PLAZA_HALF_Z = 13;  // 27 deep

    /** Рыночная площадь — увеличенная, смещена южнее канала. */
    static final int MARKET_PLAZA_CX = -30;
    static final int MARKET_PLAZA_CZ = 65;
    static final int MARKET_PLAZA_HALF_X = 9;  // 19 wide
    static final int MARKET_PLAZA_HALF_Z = 11; // 23 deep

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final ElikiumCity ctx;

    public ElikiumPlazas(Plugin plugin, RegionPainter painter, Random rng, ElikiumCity ctx) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.ctx = ctx;
    }

    public long build() {
        long count = 0;
        count += buildCathedralPlaza();
        count += buildMarketPlaza();
        // Регистрируем footprint-ы
        ctx.occupied.add(new ElikiumCity.Footprint(
                CATH_PLAZA_CX - CATH_PLAZA_HALF_X - 1,
                CATH_PLAZA_CZ - CATH_PLAZA_HALF_Z - 1,
                CATH_PLAZA_CX + CATH_PLAZA_HALF_X + 1,
                CATH_PLAZA_CZ + CATH_PLAZA_HALF_Z + 1));
        ctx.occupied.add(new ElikiumCity.Footprint(
                MARKET_PLAZA_CX - MARKET_PLAZA_HALF_X - 1,
                MARKET_PLAZA_CZ - MARKET_PLAZA_HALF_Z - 1,
                MARKET_PLAZA_CX + MARKET_PLAZA_HALF_X + 1,
                MARKET_PLAZA_CZ + MARKET_PLAZA_HALF_Z + 1));
        // POI-якоря для вывесок
        ctx.pois.add(new ElikiumCity.POI("§5Площадь Всевидящего Ока",
                "Соборная площадь",
                CATH_PLAZA_CX, Y_BASE + 6, CATH_PLAZA_CZ + CATH_PLAZA_HALF_Z + 1));
        ctx.pois.add(new ElikiumCity.POI("§eРыночная площадь",
                "торговля и ремёсла",
                MARKET_PLAZA_CX, Y_BASE + 6, MARKET_PLAZA_CZ + MARKET_PLAZA_HALF_Z + 1));
        return count;
    }

    private long buildCathedralPlaza() {
        long count = 0;
        // Мощение — концентрические кольца
        for (int dx = -CATH_PLAZA_HALF_X; dx <= CATH_PLAZA_HALF_X; dx++) {
            for (int dz = -CATH_PLAZA_HALF_Z; dz <= CATH_PLAZA_HALF_Z; dz++) {
                int x = CATH_PLAZA_CX + dx, z = CATH_PLAZA_CZ + dz;
                if (ElikiumCity.insideCathedralZone(x, z)) continue;
                int cheb = Math.max(Math.abs(dx) * 13 / 10, Math.abs(dz));
                Material mat;
                if (cheb >= CATH_PLAZA_HALF_Z) mat = Material.DEEPSLATE_TILES;
                else if (cheb >= CATH_PLAZA_HALF_Z - 3) mat = Material.POLISHED_BLACKSTONE;
                else if (cheb >= CATH_PLAZA_HALF_Z - 6) mat = Material.POLISHED_BLACKSTONE_BRICKS;
                else mat = ((dx + dz) & 1) == 0
                        ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                painter.place(x, Y_BASE, z, mat);
                count++;
            }
        }
        // Золотой крест 7×7 (длинная ось Z)
        for (int dz = -3; dz <= 3; dz++) {
            painter.place(CATH_PLAZA_CX, Y_BASE, CATH_PLAZA_CZ + dz, Material.GOLD_BLOCK);
        }
        for (int dx = -1; dx <= 1; dx++) {
            painter.place(CATH_PLAZA_CX + dx, Y_BASE, CATH_PLAZA_CZ, Material.GOLD_BLOCK);
        }
        count += 9;

        // 4 фонарных столба по углам (высокие, 5 блоков)
        int hx = CATH_PLAZA_HALF_X - 2;
        int hz = CATH_PLAZA_HALF_Z - 2;
        for (int sx : new int[]{-1, +1}) {
            for (int sz : new int[]{-1, +1}) {
                count += buildLamppost(CATH_PLAZA_CX + sx * hx, CATH_PLAZA_CZ + sz * hz, true);
            }
        }

        // 2 клумбы (ALLIUM/LILAC) по бокам креста — увеличенные
        count += buildFlowerBed(CATH_PLAZA_CX - 6, CATH_PLAZA_CZ - 5, Material.ALLIUM);
        count += buildFlowerBed(CATH_PLAZA_CX + 5, CATH_PLAZA_CZ + 3, Material.LILAC);
        count += buildFlowerBed(CATH_PLAZA_CX - 6, CATH_PLAZA_CZ + 3, Material.ALLIUM);
        count += buildFlowerBed(CATH_PLAZA_CX + 5, CATH_PLAZA_CZ - 5, Material.LILAC);

        // 4 скамейки (OAK_STAIRS) — обращены к центру
        placeBench(CATH_PLAZA_CX - 7, CATH_PLAZA_CZ + 3, "east");
        placeBench(CATH_PLAZA_CX + 6, CATH_PLAZA_CZ - 3, "west");
        placeBench(CATH_PLAZA_CX - 7, CATH_PLAZA_CZ - 7, "east");
        placeBench(CATH_PLAZA_CX + 6, CATH_PLAZA_CZ + 6, "west");
        count += 8;

        // Колокол по центру северной стороны — звонница 3×1
        int bellX = CATH_PLAZA_CX, bellZ = CATH_PLAZA_CZ - CATH_PLAZA_HALF_Z + 2;
        for (int dy = 1; dy <= 5; dy++) {
            painter.place(bellX - 1, Y_BASE + dy, bellZ, Material.DARK_OAK_LOG);
            painter.place(bellX + 1, Y_BASE + dy, bellZ, Material.DARK_OAK_LOG);
        }
        painter.place(bellX, Y_BASE + 5, bellZ, Material.DARK_OAK_LOG);
        painter.place(bellX, Y_BASE + 4, bellZ, Material.BELL);
        painter.place(bellX, Y_BASE + 6, bellZ, Material.PURPLE_BANNER);
        count += 13;

        // 4 тёмных статуи/пьедестала по углам площади
        for (int[] p : new int[][]{
                {CATH_PLAZA_CX - 6, CATH_PLAZA_CZ - 10},
                {CATH_PLAZA_CX + 6, CATH_PLAZA_CZ - 10},
                {CATH_PLAZA_CX - 6, CATH_PLAZA_CZ + 10},
                {CATH_PLAZA_CX + 6, CATH_PLAZA_CZ + 10}}) {
            painter.place(p[0], Y_BASE + 1, p[1], Material.POLISHED_BLACKSTONE_BRICKS);
            painter.place(p[0], Y_BASE + 2, p[1], Material.DEEPSLATE_BRICK_WALL);
            painter.place(p[0], Y_BASE + 3, p[1], Material.SOUL_LANTERN);
            count += 3;
        }

        // Дополнительные бочки и горшки по периметру
        painter.place(CATH_PLAZA_CX - 8, Y_BASE + 1, CATH_PLAZA_CZ, Material.BARREL);
        painter.place(CATH_PLAZA_CX + 8, Y_BASE + 1, CATH_PLAZA_CZ, Material.BARREL);
        painter.place(CATH_PLAZA_CX, Y_BASE + 1, CATH_PLAZA_CZ + 10, Material.FLOWER_POT);
        count += 3;

        return count;
    }

    private long buildMarketPlaza() {
        long count = 0;
        // Мощение — тёмная рваная брусчатка
        for (int dx = -MARKET_PLAZA_HALF_X; dx <= MARKET_PLAZA_HALF_X; dx++) {
            for (int dz = -MARKET_PLAZA_HALF_Z; dz <= MARKET_PLAZA_HALF_Z; dz++) {
                int x = MARKET_PLAZA_CX + dx, z = MARKET_PLAZA_CZ + dz;
                if (!WorldGenerator.isInsideCityPolygon(x, z)) continue;
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                int bucket = Math.floorMod(x * 13 + z * 17, 11);
                Material mat;
                if (cheb == MARKET_PLAZA_HALF_X || cheb == MARKET_PLAZA_HALF_Z) {
                    mat = Material.DEEPSLATE_BRICKS;
                } else if (bucket <= 2) {
                    mat = Material.ANDESITE;
                } else if (bucket <= 6) {
                    mat = Material.POLISHED_DEEPSLATE;
                } else {
                    mat = Material.COBBLED_DEEPSLATE;
                }
                painter.place(x, Y_BASE, z, mat);
                count++;
            }
        }

        // Центральный фонтан 3×3 с STONE_BRICKS + WATER + SEA_LANTERN
        int fx = MARKET_PLAZA_CX, fz = MARKET_PLAZA_CZ;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(fx + dx, Y_BASE + 1, fz + dz, Material.STONE_BRICKS);
            }
        }
        painter.place(fx, Y_BASE + 1, fz, Material.WATER);
        painter.place(fx, Y_BASE, fz, Material.SEA_LANTERN);
        // Бортики фонтана
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    painter.place(fx + dx, Y_BASE + 1, fz + dz, Material.STONE_BRICK_WALL);
                }
            }
        }
        // Столб фонтана
        painter.place(fx, Y_BASE + 2, fz, Material.STONE_BRICKS);
        painter.place(fx, Y_BASE + 3, fz, Material.STONE_BRICKS);
        painter.place(fx, Y_BASE + 4, fz, Material.SOUL_LANTERN);
        count += 30;

        // 5 прилавков — крупнее и разложены по периметру площади
        int[][] stallOffsets = {
                {-7, -8}, {3, -8},
                {-7, 7}, {4, 7},
                {7, -1},
        };
        Material[] stallWools = {
                Material.YELLOW_WOOL, Material.RED_WOOL,
                Material.PURPLE_WOOL, Material.GREEN_WOOL,
                Material.WHITE_WOOL,
        };
        Material[] stallItems = {
                Material.BREAD, Material.IRON_SWORD,
                Material.EMERALD, Material.BOOK, Material.AMETHYST_SHARD,
        };
        for (int i = 0; i < stallOffsets.length; i++) {
            int sx = MARKET_PLAZA_CX + stallOffsets[i][0];
            int sz = MARKET_PLAZA_CZ + stallOffsets[i][1];
            count += buildStall(sx, sz, stallWools[i], stallItems[i]);
        }

        // 4 лавочки
        placeBench(MARKET_PLAZA_CX - 2, MARKET_PLAZA_CZ + 5, "east");
        placeBench(MARKET_PLAZA_CX + 5, MARKET_PLAZA_CZ - 4, "west");
        placeBench(MARKET_PLAZA_CX - 2, MARKET_PLAZA_CZ - 5, "east");
        placeBench(MARKET_PLAZA_CX + 5, MARKET_PLAZA_CZ + 4, "west");

        // 6 бочек вокруг площади
        painter.place(MARKET_PLAZA_CX - 3, Y_BASE + 1, MARKET_PLAZA_CZ - 8, Material.BARREL);
        painter.place(MARKET_PLAZA_CX + 5, Y_BASE + 1, MARKET_PLAZA_CZ - 7, Material.BARREL);
        painter.place(MARKET_PLAZA_CX - 6, Y_BASE + 1, MARKET_PLAZA_CZ + 5, Material.BARREL);
        painter.place(MARKET_PLAZA_CX + 6, Y_BASE + 1, MARKET_PLAZA_CZ + 4, Material.BARREL);
        painter.place(MARKET_PLAZA_CX - 4, Y_BASE + 1, MARKET_PLAZA_CZ + 8, Material.BARREL);
        painter.place(MARKET_PLAZA_CX + 3, Y_BASE + 1, MARKET_PLAZA_CZ + 8, Material.BARREL);
        count += 10;

        // 4 фонарных столба
        count += buildLamppost(MARKET_PLAZA_CX - 6, MARKET_PLAZA_CZ - 6, false);
        count += buildLamppost(MARKET_PLAZA_CX + 6, MARKET_PLAZA_CZ - 6, false);
        count += buildLamppost(MARKET_PLAZA_CX - 6, MARKET_PLAZA_CZ + 6, false);
        count += buildLamppost(MARKET_PLAZA_CX + 6, MARKET_PLAZA_CZ + 6, false);

        return count;
    }

    private long buildLamppost(int x, int z, boolean tall) {
        int height = tall ? 5 : 4;
        for (int dy = 1; dy <= height; dy++) {
            painter.place(x, Y_BASE + dy, z, Material.OAK_FENCE);
        }
        painter.place(x, Y_BASE + height + 1, z, Material.SOUL_LANTERN);
        return height + 1;
    }

    private long buildFlowerBed(int cx, int cz, Material flower) {
        long count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE, cz + dz, Material.GRASS_BLOCK);
                painter.place(cx + dx, Y_BASE + 1, cz + dz, flower);
                count += 2;
            }
        }
        return count;
    }

    private void placeBench(int x, int z, String facing) {
        BlockData stairData = Material.OAK_STAIRS.createBlockData("[facing=" + facing + ",half=bottom]");
        painter.placeData(x, Y_BASE + 1, z, stairData);
        painter.placeData(x, Y_BASE + 1, z + 1, stairData);
    }

    private long buildStall(int cx, int cz, Material woolColor, Material item) {
        long count = 0;
        // 3×2 прилавок
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockData slab = Material.OAK_SLAB.createBlockData("[type=top]");
                painter.placeData(cx + dx, Y_BASE + 1, cz + dz, slab);
                count++;
            }
        }
        // Передние и задние столбы
        for (int dx = 0; dx <= 2; dx += 2) {
            painter.place(cx + dx, Y_BASE + 2, cz, Material.OAK_FENCE);
            painter.place(cx + dx, Y_BASE + 3, cz, Material.OAK_FENCE);
            painter.place(cx + dx, Y_BASE + 2, cz + 1, Material.OAK_FENCE);
            painter.place(cx + dx, Y_BASE + 3, cz + 1, Material.OAK_FENCE);
            count += 4;
        }
        // Наклонный тент 3×2
        for (int dx = 0; dx <= 2; dx++) {
            painter.place(cx + dx, Y_BASE + 4, cz, woolColor);
            painter.place(cx + dx, Y_BASE + 5, cz + 1, woolColor);
            count += 2;
        }
        // Товар
        Material platter = item;
        if (item == Material.BREAD) platter = Material.HAY_BLOCK;
        else if (item == Material.IRON_SWORD) platter = Material.IRON_BLOCK;
        else if (item == Material.EMERALD) platter = Material.EMERALD_BLOCK;
        else if (item == Material.BOOK) platter = Material.LECTERN;
        else if (item == Material.AMETHYST_SHARD) platter = Material.AMETHYST_BLOCK;
        painter.place(cx + 1, Y_BASE + 2, cz, platter);
        painter.place(cx + 1, Y_BASE + 1, cz + 2, rng.nextBoolean() ? Material.BARREL : Material.CHEST);
        count += 2;
        return count;
    }
}
