package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Площади Эликия — соборная (15×21 перед собором) и рыночная (12×15
 * на западе). Обе помечают зону как occupied, чтобы дома не наезжали.
 *
 * <p><b>Соборная площадь</b> (центр (45, 38)):
 * <ul>
 *   <li>Концентрические кольца мощения: GILDED_BLACKSTONE центр →
 *       POLISHED_BLACKSTONE_BRICKS кольцо → POLISHED_BLACKSTONE кольцо
 *       → DEEPSLATE_TILES обод;</li>
 *   <li>Золотой крест 7×7 из GOLD_BLOCK по оси Z;</li>
 *   <li>4 высоких фонарных столба (OAK_FENCE+SOUL_LANTERN);</li>
 *   <li>2 клумбы 3×2 с ALLIUM/LILAC;</li>
 *   <li>4 скамейки (OAK_STAIRS);</li>
 *   <li>Колокол на OAK_FENCE столбе 5 высотой;</li>
 * </ul>
 *
 * <p><b>Рыночная площадь</b> (центр (-30, 45)):
 * <ul>
 *   <li>Мощение POLISHED_DEEPSLATE+ANDESITE;</li>
 *   <li>5 прилавков (OAK_SLAB + WOOL навес на OAK_FENCE);</li>
 *   <li>На прилавках ITEM_FRAME с товарами через NamedItemStack
 *       (BREAD, IRON_SWORD, EMERALD, BOOK, AMETHYST_SHARD);</li>
 *   <li>Центральный фонтан 3×3 (STONE_BRICKS + WATER + SEA_LANTERN);</li>
 *   <li>2 лавочки.</li>
 * </ul>
 */
public final class ElikiumPlazas {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    /** Соборная площадь. */
    static final int CATH_PLAZA_CX = 45;
    static final int CATH_PLAZA_CZ = 38;
    static final int CATH_PLAZA_HALF_X = 8;  // 17 wide
    static final int CATH_PLAZA_HALF_Z = 11; // 23 deep

    /** Рыночная площадь. */
    static final int MARKET_PLAZA_CX = -30;
    static final int MARKET_PLAZA_CZ = 45;
    static final int MARKET_PLAZA_HALF_X = 7;  // 15 wide
    static final int MARKET_PLAZA_HALF_Z = 9;  // 19 deep

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
        // Регистрируем footprint-ы чтобы дома не наезжали
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
        // Мощение
        for (int dx = -CATH_PLAZA_HALF_X; dx <= CATH_PLAZA_HALF_X; dx++) {
            for (int dz = -CATH_PLAZA_HALF_Z; dz <= CATH_PLAZA_HALF_Z; dz++) {
                int x = CATH_PLAZA_CX + dx, z = CATH_PLAZA_CZ + dz;
                if (ElikiumCity.insideCathedralZone(x, z)) continue;
                int cheb = Math.max(Math.abs(dx) * 11 / 8, Math.abs(dz));
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
        // 4 фонарных столба по углам
        int hx = CATH_PLAZA_HALF_X - 1;
        int hz = CATH_PLAZA_HALF_Z - 1;
        for (int sx : new int[]{-1, +1}) {
            for (int sz : new int[]{-1, +1}) {
                count += buildLamppost(CATH_PLAZA_CX + sx * hx, CATH_PLAZA_CZ + sz * hz, true);
            }
        }
        // 2 клумбы (ALLIUM/LILAC) по бокам креста
        count += buildFlowerBed(CATH_PLAZA_CX - 5, CATH_PLAZA_CZ - 4, Material.ALLIUM);
        count += buildFlowerBed(CATH_PLAZA_CX + 4, CATH_PLAZA_CZ + 2, Material.LILAC);
        // 4 скамейки (OAK_STAIRS) — обращены к центру
        placeBench(CATH_PLAZA_CX - 6, CATH_PLAZA_CZ + 3, "east");
        placeBench(CATH_PLAZA_CX + 5, CATH_PLAZA_CZ - 3, "west");
        placeBench(CATH_PLAZA_CX - 6, CATH_PLAZA_CZ - 6, "east");
        placeBench(CATH_PLAZA_CX + 5, CATH_PLAZA_CZ + 5, "west");
        count += 8;
        // Колокол по центру северной стороны (ближе к собору)
        int bellX = CATH_PLAZA_CX, bellZ = CATH_PLAZA_CZ - CATH_PLAZA_HALF_Z + 1;
        for (int dy = 1; dy <= 4; dy++) {
            painter.place(bellX, Y_BASE + dy, bellZ, Material.OAK_FENCE);
        }
        painter.place(bellX, Y_BASE + 5, bellZ, Material.OAK_PLANKS);
        painter.place(bellX, Y_BASE + 4, bellZ, Material.BELL);
        count += 6;
        return count;
    }

    private long buildMarketPlaza() {
        long count = 0;
        // Мощение
        for (int dx = -MARKET_PLAZA_HALF_X; dx <= MARKET_PLAZA_HALF_X; dx++) {
            for (int dz = -MARKET_PLAZA_HALF_Z; dz <= MARKET_PLAZA_HALF_Z; dz++) {
                int x = MARKET_PLAZA_CX + dx, z = MARKET_PLAZA_CZ + dz;
                if (!WorldGenerator.isInsideCityPolygon(x, z)) continue;
                Material mat;
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb == MARKET_PLAZA_HALF_X || cheb == MARKET_PLAZA_HALF_Z) {
                    mat = Material.DEEPSLATE_TILES;
                } else if (((x + z) & 1) == 0) {
                    mat = Material.POLISHED_DEEPSLATE;
                } else {
                    mat = Material.ANDESITE;
                }
                painter.place(x, Y_BASE, z, mat);
                count++;
            }
        }
        // Центральный фонтан 3×3
        int fx = MARKET_PLAZA_CX, fz = MARKET_PLAZA_CZ;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    painter.place(fx + dx, Y_BASE + 1, fz + dz, Material.STONE_BRICKS);
                    painter.place(fx + dx, Y_BASE + 2, fz + dz, Material.STONE_BRICK_WALL);
                }
            }
        }
        painter.place(fx, Y_BASE + 1, fz, Material.SEA_LANTERN);
        painter.place(fx, Y_BASE + 2, fz, Material.WATER);
        count += 17;
        // 5 прилавков расставлены по полукругу
        int[][] stallOffsets = {
                {-5, -6}, {5, -6},
                {-5, 6}, {5, 6},
                {-5, 0},
        };
        Material[] stallWools = {
                Material.YELLOW_WOOL, Material.WHITE_WOOL,
                Material.PURPLE_WOOL, Material.LIME_WOOL,
                Material.RED_WOOL,
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
        // 2 лавочки
        placeBench(MARKET_PLAZA_CX - 4, MARKET_PLAZA_CZ + 3, "east");
        placeBench(MARKET_PLAZA_CX + 3, MARKET_PLAZA_CZ - 3, "west");
        count += 4;
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
        // 2×2 прилавок: OAK_SLAB поверхность на y+1
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockData slab = Material.OAK_SLAB.createBlockData("[type=top]");
                painter.placeData(cx + dx, Y_BASE + 1, cz + dz, slab);
                count++;
            }
        }
        // 4 столба OAK_FENCE
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE + 2, cz + dz, Material.OAK_FENCE);
                painter.place(cx + dx, Y_BASE + 3, cz + dz, Material.OAK_FENCE);
                count += 2;
            }
        }
        // Навес — 2×2 шерсти
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE + 4, cz + dz, woolColor);
                count++;
            }
        }
        // Декоративный товар: блок-плоскость лежит на прилавке
        Material platter = item;
        // Некоторые товары не блоки — заменяем плоским аналогом
        if (item == Material.BREAD) platter = Material.HAY_BLOCK;
        else if (item == Material.IRON_SWORD) platter = Material.IRON_BLOCK;
        else if (item == Material.EMERALD) platter = Material.EMERALD_BLOCK;
        else if (item == Material.BOOK) platter = Material.LECTERN;
        else if (item == Material.AMETHYST_SHARD) platter = Material.AMETHYST_BLOCK;
        painter.place(cx, Y_BASE + 2, cz, platter);
        count++;
        return count;
    }
}
