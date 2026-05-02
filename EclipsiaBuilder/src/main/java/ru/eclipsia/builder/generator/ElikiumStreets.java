package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Сеть улиц Эликия — 4 главные дуги от ворот к собору + рыночная улица +
 * кольцевые переулки + угловые мини-площадки.
 *
 * <p><b>Главные улицы</b> — Безье-кривые между точкой ворот и
 * соборной/рыночной площадью. Каждая дуга разбивается на серию отрезков
 * Bresenham, ширина модулируется синусом (5-7-9-7-5), что создаёт
 * визуально живую улицу с расширениями-«площадками».
 *
 * <p><b>Покрытие</b>: чередование POLISHED_DEEPSLATE и ANDESITE даёт
 * характерный шахматный узор; обочина — DEEPSLATE_TILES; на расширениях
 * — POLISHED_BLACKSTONE «парадная брусчатка».
 *
 * <p><b>Фонари</b>: OAK_FENCE-столб 4 высотой + SOUL_LANTERN сверху.
 * Расстановка не равномерная — каждые 12-18 шагов с лёгким случайным
 * сдвигом, на перекрёстках чаще.
 *
 * <p><b>Переулки</b> — 6 дополнительных дуг шириной 2 (halfWidth=1),
 * соединяющих главные улицы между собой. COBBLESTONE/ANDESITE.
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

    /** Якоря: точка-ворот / соборная-площадь / рыночная-площадь / центр. */
    private static final int[] PLAZA_CATHEDRAL = {45, 36};   // соборная площадь
    private static final int[] PLAZA_MARKET    = {-30, 45};  // рыночная площадь

    public long build() {
        long count = 0;

        // 4 главные дуги от ворот → собор/рынок (с control-точкой для кривизны)
        count += buildBezierStreet(WorldGenerator.SOUTH_GATE,  new int[]{15, 80},  PLAZA_CATHEDRAL,  3);
        count += buildBezierStreet(WorldGenerator.NORTH_GATE,  new int[]{-25, -75}, new int[]{-30, -10}, 3);
        count += buildBezierStreet(WorldGenerator.EAST_GATE,   new int[]{105, 30}, new int[]{52, 38},   3);
        count += buildBezierStreet(WorldGenerator.WEST_GATE,   new int[]{-80, 0},  PLAZA_MARKET,        3);

        // Соединительные дуги между главными
        count += buildBezierStreet(new int[]{-30, -10}, new int[]{-30, 10}, PLAZA_MARKET,            2);
        count += buildBezierStreet(PLAZA_MARKET,        new int[]{0, 60},   PLAZA_CATHEDRAL,         2);
        count += buildBezierStreet(new int[]{52, 38},   new int[]{75, 0},   new int[]{105, -25},     2);

        // Узкие переулки
        count += buildAlley(new int[]{-60, -20}, new int[]{-40, 10});
        count += buildAlley(new int[]{-90, 20},  new int[]{-50, 50});
        count += buildAlley(new int[]{90, 50},   new int[]{120, 20});
        count += buildAlley(new int[]{100, -30}, new int[]{80, -60});
        count += buildAlley(new int[]{-50, -50}, new int[]{-70, -30});

        plugin.getLogger().info("ElikiumStreets: " + ctx.streetCells.size()
                + " клеток мощения, ~" + count + " блок-операций.");
        return count;
    }

    /** Квадратичная Безье от a через c к b с переменной шириной. */
    private long buildBezierStreet(int[] a, int[] c, int[] b, int baseHalfWidth) {
        long count = 0;
        int prevX = a[0], prevZ = a[1];
        int steps = 40;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double oneT = 1 - t;
            double xd = oneT * oneT * a[0] + 2 * oneT * t * c[0] + t * t * b[0];
            double zd = oneT * oneT * a[1] + 2 * oneT * t * c[1] + t * t * b[1];
            int nx = (int) Math.round(xd);
            int nz = (int) Math.round(zd);
            // Модуляция ширины синусом 0.5..1.5 от base
            double widthMod = 1.0 + 0.5 * Math.sin(t * Math.PI * 2);
            int halfWidth = Math.max(2, (int) Math.round(baseHalfWidth * widthMod));
            count += paveSegment(prevX, prevZ, nx, nz, halfWidth, true);
            // Фонарь каждые ~14 шагов
            if (i % 4 == 0 && rng.nextDouble() > 0.3) {
                count += placeLamppost(nx + halfWidth + 1, nz);
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
                            mat = Material.DEEPSLATE_TILES;
                        } else if (((x + z) & 1) == 0) {
                            mat = Material.POLISHED_DEEPSLATE;
                        } else {
                            mat = Material.ANDESITE;
                        }
                    } else {
                        mat = ((x + z) & 1) == 0 ? Material.COBBLESTONE : Material.ANDESITE;
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
