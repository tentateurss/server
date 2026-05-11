package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;

/**
 * Глубокий бескрайний океан с торчащими острыми скалами.
 * Заменяет старую «лужицу» из 2 блоков воды поверх FLAT-травы.
 *
 * <p>Алгоритм:
 * <ul>
 *   <li>Дно океана — на {@link #FLOOR_Y} (-25 относительно базы=4 → y=-21).</li>
 *   <li>Между {@code FLOOR_Y} и {@code WATER_Y} (=GROUND_Y+1) заливаем
 *       водой, очищая FLAT-террейн.</li>
 *   <li>На дне разбрасываем камни/гравий + редкий тёмный песок.</li>
 *   <li>Сверху — острые скалы-шпили из COBBLED_DEEPSLATE/POINTED_DRIPSTONE
 *       высотой 6..18 блоков, торчащие из воды.</li>
 *   <li>Остатки кораблей лежат частично затопленными.</li>
 * </ul>
 */
public final class BeachOcean {

    /** Дно океана — глубоко вниз. */
    public static final int FLOOR_Y = -21;
    /** Поверхность воды (то же, что зеркало моря). */
    public static final int WATER_Y = 5;

    private final RegionPainter p;
    private final Random rng;
    private final SimplexNoiseGenerator floorNoise;
    private final SimplexNoiseGenerator rockNoise;

    public BeachOcean(RegionPainter p, Random rng, long seed) {
        this.p = p;
        this.rng = rng;
        this.floorNoise = new SimplexNoiseGenerator(seed ^ 0xCEAA110DL);
        this.rockNoise  = new SimplexNoiseGenerator(seed ^ 0xC110EA11L);
    }

    /**
     * Залить океаном прямоугольник [xMin..xMax] × [zMin..zMax] для тех (x,z),
     * у которых {@code shouldFlood(x,z)} вернёт true.
     */
    public void carveOcean(int xMin, int xMax, int zMin, int zMax,
                           OceanPredicate shouldFlood) {
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                if (!shouldFlood.test(x, z)) continue;
                // Локальная глубина: чем дальше от берега, тем глубже.
                double n = floorNoise.noise(x * 0.020, z * 0.020);
                int floor = FLOOR_Y + (int) Math.round(n * 3.0);
                // Очистить столбик от FLOOR до WATER_Y+1 в воду
                for (int y = floor + 1; y <= WATER_Y; y++) {
                    p.place(x, y, z, Material.WATER);
                }
                // Дно
                Material bottom = switch (rng.nextInt(10)) {
                    case 0, 1 -> Material.GRAVEL;
                    case 2 -> Material.SAND;
                    case 3 -> Material.MOSSY_COBBLESTONE;
                    default -> Material.COBBLED_DEEPSLATE;
                };
                p.place(x, floor, z, bottom);
                // Сразу очистить всё, что выше воды
                for (int y = WATER_Y + 1; y <= WATER_Y + 6; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }
    }

    /**
     * Расставить торчащие из воды скалы-шпили. Размещаются не ближе 8
     * блоков друг к другу (через rockNoise).
     */
    public void scatterRockSpires(int xMin, int xMax, int zMin, int zMax,
                                  OceanPredicate shouldPlace, double density) {
        for (int x = xMin; x <= xMax; x += 4) {
            for (int z = zMin; z <= zMax; z += 4) {
                if (!shouldPlace.test(x, z)) continue;
                double n = rockNoise.noise(x * 0.05, z * 0.05);
                if (n < 1 - density) continue;
                int ox = x + rng.nextInt(4) - 2;
                int oz = z + rng.nextInt(4) - 2;
                if (!shouldPlace.test(ox, oz)) continue;
                int height = 6 + rng.nextInt(13); // 6..18 блоков торчит
                spire(ox, oz, height);
            }
        }
    }

    /** Один остроконечный шпиль высотой {@code height} блоков. */
    public void spire(int x, int z, int height) {
        // Низ — широкое основание (3-4 блока)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 5) continue;
                p.place(x + dx, FLOOR_Y, z + dz, Material.DEEPSLATE);
                p.place(x + dx, FLOOR_Y + 1, z + dz, Material.DEEPSLATE);
            }
        }
        // Тело — сужающаяся колонна
        int half = height / 2;
        for (int dy = 0; dy < height; dy++) {
            // радиус линейно сужается
            double rTarget = 2.5 - (double) dy / height * 2.0;
            int y = WATER_Y - 4 + dy;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rTarget) continue;
                    Material m;
                    if (dy < height / 3) {
                        m = (rng.nextInt(5) == 0) ? Material.MOSSY_COBBLESTONE
                                                  : Material.COBBLED_DEEPSLATE;
                    } else if (dy < 2 * height / 3) {
                        m = Material.BLACKSTONE;
                    } else {
                        m = (rng.nextInt(3) == 0) ? Material.POINTED_DRIPSTONE
                                                  : Material.DEEPSLATE;
                    }
                    p.place(x + dx, y, z + dz, m);
                }
            }
        }
        // Острый «коготь» сверху
        int topY = WATER_Y - 4 + height;
        p.place(x, topY, z, Material.POINTED_DRIPSTONE);
        p.place(x, topY - 1, z, Material.POINTED_DRIPSTONE);
        // Случайно — кристалл аметиста сверху для атмосферы
        if (rng.nextInt(5) == 0) {
            p.place(x, topY + 1, z, Material.AMETHYST_CLUSTER);
        }
    }

    /** Затопленный наполовину остов корабля (декорация в океане). */
    public void shipwreck(int x, int z, boolean rotated) {
        int y = FLOOR_Y + 1; // на дне
        int xLen = rotated ? 4 : 9;
        int zLen = rotated ? 9 : 4;
        // Корпус
        for (int dx = 0; dx < xLen; dx++) {
            for (int dz = 0; dz < zLen; dz++) {
                if ((dx + dz * 3) % 5 != 2 || rng.nextInt(4) == 0) {
                    p.place(x + dx, y, z + dz, Material.DARK_OAK_PLANKS);
                }
            }
        }
        // Борта
        for (int dx = 0; dx < xLen; dx++) {
            if (rng.nextInt(3) == 0) continue;
            p.place(x + dx, y + 1, z, Material.SPRUCE_PLANKS);
            p.place(x + dx, y + 1, z + zLen - 1, Material.SPRUCE_PLANKS);
            if (rng.nextInt(2) == 0) {
                p.place(x + dx, y + 2, z, Material.SPRUCE_PLANKS);
            }
        }
        // Мачта-обломок
        int mx = x + xLen / 2, mz = z + zLen / 2;
        int mastH = 4 + rng.nextInt(3);
        for (int k = 0; k < mastH; k++) {
            p.place(mx, y + 1 + k, mz, Material.DARK_OAK_LOG);
        }
        p.place(mx, y + mastH + 1, mz, Material.COBWEB);
        // Иногда ящик с сокровищами
        if (rng.nextBoolean()) {
            p.place(x + 1, y + 1, z + 1, Material.CHEST);
        }
    }

    @FunctionalInterface
    public interface OceanPredicate {
        boolean test(int x, int z);
    }
}
