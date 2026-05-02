package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Сеть улиц Эликия v2 — 4 главные дуги от ворот к собору + рыночная
 * улица + кольцевые переулки + тупиковые переулки с мини-двориками.
 *
 * <p><b>Главные улицы (3 шт от ворот к собору)</b>: извилистые, ширина
 * 5-7 блоков (расширяется до 9, сужается до 4). Материал: POLISHED_DEEPSLATE
 * + ANDESITE (узор). Фонари: OAK_FENCE высотой 4 + SOUL_LANTERN.
 *
 * <p><b>Переулки (10+ шт)</b>: узкие 2-3 блока, извилистые, некоторые
 * ТУПИКОВЫЕ. Материал: COBBLESTONE или ANDESITE. В тупиках: маленькие
 * дворики с бочками, ящиками, колодцем.
 */
public final class ElikiumStreets {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final ElikiumCity ctx;

    public ElikiumStreets(Plugin plugin, RegionPainter painter, Random rng, ElikiumCity ctx) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.ctx = ctx;
    }

    private static final int[] PLAZA_CATHEDRAL = {45, 36};
    private static final int[] PLAZA_MARKET    = {-30, 45};

    public long build() {
        long count = 0;

        // 4 главные дуги от ворот → собор/рынок (с control-точкой для кривизны)
        count += buildBezierStreet(WorldGenerator.SOUTH_GATE,  new int[]{15, 80},  PLAZA_CATHEDRAL,  3);
        count += buildBezierStreet(WorldGenerator.NORTH_GATE,  new int[]{-25, -75}, new int[]{-30, -10}, 3);
        count += buildBezierStreet(WorldGenerator.EAST_GATE,   new int[]{105, 30}, new int[]{52, 38},   3);
        count += buildBezierStreet(WorldGenerator.WEST_GATE,   new int[]{-80, 0},  PLAZA_MARKET,        3);

        // Соединительные дуги между главными (средняя ширина)
        count += buildBezierStreet(new int[]{-30, -10}, new int[]{-30, 10}, PLAZA_MARKET,            2);
        count += buildBezierStreet(PLAZA_MARKET,        new int[]{0, 60},   PLAZA_CATHEDRAL,         2);
        count += buildBezierStreet(new int[]{52, 38},   new int[]{75, 0},   new int[]{105, -25},     2);
        count += buildBezierStreet(new int[]{-30, -10}, new int[]{10, -30}, new int[]{52, -20},      2);

        // Узкие переулки (проходят насквозь, ширина 2-3 блока)
        count += buildAlley(new int[]{-60, -20}, new int[]{-40, 10});
        count += buildAlley(new int[]{-90, 20},  new int[]{-50, 50});
        count += buildAlley(new int[]{90, 50},   new int[]{120, 20});
        count += buildAlley(new int[]{100, -30}, new int[]{80, -60});
        count += buildAlley(new int[]{-50, -50}, new int[]{-70, -30});
        count += buildAlley(new int[]{-20, 60},  new int[]{20, 80});
        count += buildAlley(new int[]{30, -50},  new int[]{60, -30});
        count += buildAlley(new int[]{-80, 70},  new int[]{-50, 90});
        count += buildAlley(new int[]{70, 80},   new int[]{100, 60});
        count += buildAlley(new int[]{-100, -40}, new int[]{-80, -70});
        // Дополнительные переулки для плотности
        count += buildAlley(new int[]{-70, -60}, new int[]{-100, -85});
        count += buildAlley(new int[]{70, -40},  new int[]{100, -65});
        count += buildAlley(new int[]{-40, 30},  new int[]{-60, 60});
        count += buildAlley(new int[]{40, 30},   new int[]{60, 55});
        count += buildAlley(new int[]{-110, 0},  new int[]{-130, 25});
        count += buildAlley(new int[]{110, 0},   new int[]{130, -25});
        count += buildAlley(new int[]{-30, -80}, new int[]{-10, -100});
        count += buildAlley(new int[]{30, -80},  new int[]{10, -100});

        // Тупиковые переулки с мини-двориками в конце
        count += buildDeadEndAlley(new int[]{-110, -60}, new int[]{-130, -75});
        count += buildDeadEndAlley(new int[]{-115,  85}, new int[]{-135, 105});
        count += buildDeadEndAlley(new int[]{ 110, -90}, new int[]{ 135, -105});
        count += buildDeadEndAlley(new int[]{  60,  90}, new int[]{  35, 115});
        count += buildDeadEndAlley(new int[]{-70, -90},  new int[]{-90, -110});
        count += buildDeadEndAlley(new int[]{ 80, 100},  new int[]{ 60, 115});
        count += buildDeadEndAlley(new int[]{-120, 30},  new int[]{-140, 45});
        count += buildDeadEndAlley(new int[]{ 120, -30}, new int[]{ 140, -45});
        count += buildDeadEndAlley(new int[]{-40, -100}, new int[]{-55, -120});
        count += buildDeadEndAlley(new int[]{ 40, -100}, new int[]{ 55, -120});

        plugin.getLogger().info("ElikiumStreets: " + ctx.streetCells.size()
                + " клеток мощения, ~" + count + " блок-операций.");
        return count;
    }

    /**
     * Тупиковый переулок: узкий (halfWidth=1, ширина 3) +
     * мини-дворик 3×3 в конце с BARREL+CHEST+колодцем.
     */
    private long buildDeadEndAlley(int[] from, int[] to) {
        long count = paveSegment(from[0], from[1], to[0], to[1], 1, false);
        int ex = to[0], ez = to[1];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!WorldGenerator.isInsideCityPolygon(ex + dx, ez + dz)) continue;
                if (ElikiumCity.insideCathedralZone(ex + dx, ez + dz)) continue;
                painter.place(ex + dx, Y_BASE, ez + dz, Material.STONE_BRICKS);
                ctx.streetCells.add(ElikiumCity.packCoord(ex + dx, ez + dz));
                count++;
            }
        }
        // Колодец 1×1
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                painter.place(ex + dx, Y_BASE + 1, ez + dz, Material.STONE_BRICK_WALL);
            }
        }
        painter.place(ex, Y_BASE + 1, ez, Material.WATER);
        // Бочки и ящик в углу
        painter.place(ex - 1, Y_BASE + 2, ez - 1, Material.BARREL);
        painter.place(ex + 1, Y_BASE + 2, ez + 1, Material.BARREL);
        painter.place(ex + 1, Y_BASE + 2, ez - 1, Material.OAK_PLANKS);
        // Настенный фонарь
        painter.place(ex, Y_BASE + 4, ez - 2, Material.SOUL_LANTERN);
        // Цветочный горшок
        painter.place(ex - 1, Y_BASE + 2, ez + 1, Material.FLOWER_POT);
        return count + 8;
    }

    /**
     * Квадратичная Безье от a через c к b с переменной шириной. Фонари
     * ставятся по накопленной длине (каждые ~8-14 блоков).
     */
    private long buildBezierStreet(int[] a, int[] c, int[] b, int baseHalfWidth) {
        long count = 0;
        int prevX = a[0], prevZ = a[1];
        int steps = 40;
        double accDist = 0;
        double nextLamp = 8 + rng.nextDouble() * 4;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double oneT = 1 - t;
            double xd = oneT * oneT * a[0] + 2 * oneT * t * c[0] + t * t * b[0];
            double zd = oneT * oneT * a[1] + 2 * oneT * t * c[1] + t * t * b[1];
            int nx = (int) Math.round(xd);
            int nz = (int) Math.round(zd);
            // Ширина улицы модулируется (5-7 для главных, 3-4 для соединительных)
            double widthMod = 1.08 + 0.25 * Math.sin(t * Math.PI * 2);
            int halfWidth = Math.max(baseHalfWidth, (int) Math.round(baseHalfWidth * widthMod));
            count += paveSegment(prevX, prevZ, nx, nz, halfWidth, true);
            accDist += Math.hypot(nx - prevX, nz - prevZ);
            if (accDist >= nextLamp) {
                // Фонари по обе стороны улицы (не всегда)
                count += placeLamppost(nx + halfWidth + 2, nz);
                if (rng.nextDouble() < 0.3) {
                    count += placeLamppost(nx - halfWidth - 2, nz);
                }
                accDist = 0;
                nextLamp = 8 + rng.nextDouble() * 6;
            }
            prevX = nx;
            prevZ = nz;
        }
        return count;
    }

    /** Узкий переулок halfWidth=1 (ширина 3) с COBBLESTONE/ANDESITE мощением. */
    private long buildAlley(int[] a, int[] b) {
        return paveSegment(a[0], a[1], b[0], b[1], 1, false);
    }

    /** Bresenham-замощение между двумя точками. */
    private long paveSegment(int x1, int z1, int x2, int z2, int halfWidth, boolean main) {
        long count = 0;
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        int cx = x1, cz = z1;
        while (true) {
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                    int x = cx + ox, z = cz + oz;
                    if (!WorldGenerator.isInsideCityPolygon(x, z)) continue;
                    if (ElikiumCity.insideCathedralZone(x, z)) continue;
                    int cheb = Math.max(Math.abs(ox), Math.abs(oz));
                    Material mat;
                    if (main) {
                        if (cheb == halfWidth) {
                            mat = Material.DEEPSLATE_BRICKS;
                        } else {
                            // Узор POLISHED_DEEPSLATE + ANDESITE
                            int bucket = Math.floorMod(x * 31 + z * 17, 9);
                            if (bucket <= 2) mat = Material.ANDESITE;
                            else if (bucket <= 5) mat = Material.POLISHED_DEEPSLATE;
                            else mat = Material.COBBLED_DEEPSLATE;
                        }
                    } else {
                        mat = ((x + z) & 1) == 0
                                ? Material.COBBLESTONE
                                : Material.ANDESITE;
                    }
                    painter.place(x, Y_BASE, z, mat);
                    ctx.streetCells.add(ElikiumCity.packCoord(x, z));
                    count++;
                }
            }
            if (cx == x2 && cz == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 < dx)  { err += dx; cz += sz; }
        }
        return count;
    }

    /** Уличный фонарь: OAK_FENCE столб + SOUL_LANTERN сверху. */
    private long placeLamppost(int x, int z) {
        if (!WorldGenerator.isInsideCityPolygon(x, z)) return 0;
        if (ElikiumCity.insideCathedralZone(x, z)) return 0;
        if (ctx.streetCells.contains(ElikiumCity.packCoord(x, z))) return 0;
        for (int dy = 1; dy <= 4; dy++) {
            painter.place(x, Y_BASE + dy, z, Material.OAK_FENCE);
        }
        painter.place(x, Y_BASE + 5, z, Material.SOUL_LANTERN);
        return 5;
    }
}
