package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Органичные летающие острова — заменяют старые «летающие тарелки»
 * (3-этажные диски). Здесь форма — каплевидная, с зауженным низом,
 * естественно изогнутая шумом, с холмами/деревьями/руинами наверху
 * и водопадами/висящими корнями по бокам.
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>На каждом уровне Y определяем «целевой радиус» по плавной
 *       функции (большой сверху, узкий внизу) + шум для неровностей.</li>
 *   <li>Заполняем диск этого радиуса со срезанными углами.</li>
 *   <li>Поверхность сверху — травяной блок / мох / песок.</li>
 *   <li>Снизу из самой нижней точки — длинные хвосты-корни.</li>
 *   <li>На вершине добавляем мини-холм / маленькое дерево / руины.</li>
 * </ol>
 */
public final class BeachIslands {

    private final RegionPainter p;
    private final Random rng;
    private final SimplexNoiseGenerator shapeNoise;

    public BeachIslands(RegionPainter p, Random rng, long seed) {
        this.p = p;
        this.rng = rng;
        this.shapeNoise = new SimplexNoiseGenerator(seed ^ 0x1518A11DL);
    }

    /**
     * Сгенерировать органичный остров.
     *
     * @param cx        центр X
     * @param cz        центр Z
     * @param topY      Y верхнего слоя травы
     * @param topRadius максимальный радиус сверху (5..12)
     * @param depth     глубина (от верха до острого низа), типично 8..14
     * @param theme     тема (DARK/MOSSY/CRYSTAL/RUINED/CHERRY)
     */
    public void organic(int cx, int cz, int topY, int topRadius, int depth, Theme theme) {
        Material core = theme.core;
        Material surface = theme.surface;
        Material accent = theme.accent;

        // Тело острова: сверху вниз радиус сужается по квадратичной кривой.
        for (int layer = 0; layer < depth; layer++) {
            int y = topY - layer;
            // Линейная переменная 0..1 (0 — верх, 1 — низ)
            double t = (double) layer / (depth - 1);
            // Радиус по кривой: r = topR * (1 - t^1.5)
            double rTarget = topRadius * (1.0 - Math.pow(t, 1.5));
            // Шум для волнистости границы
            double noiseScale = 0.18;
            for (int dx = -topRadius; dx <= topRadius; dx++) {
                for (int dz = -topRadius; dz <= topRadius; dz++) {
                    double n = shapeNoise.noise(
                            (cx + dx) * noiseScale,
                            y * 0.10,
                            (cz + dz) * noiseScale);
                    double rEff = rTarget + n * 1.5;
                    if (rEff < 0.5) continue;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > rEff) continue;

                    Material m;
                    if (layer == 0) {
                        m = surface; // верхний слой — газон/мох/песок
                    } else if (layer == 1) {
                        m = (rng.nextInt(4) == 0) ? Material.DIRT : core;
                    } else {
                        // глубже — каменное ядро
                        m = (rng.nextInt(8) == 0) ? accent : core;
                    }
                    p.place(cx + dx, y, cz + dz, m);
                }
            }
        }

        // Хвост (длинные «корни» из самой нижней точки)
        int bottomY = topY - depth + 1;
        for (int i = 0; i < 4 + rng.nextInt(3); i++) {
            int dx = rng.nextInt(3) - 1, dz = rng.nextInt(3) - 1;
            int len = 4 + rng.nextInt(6);
            for (int k = 0; k < len; k++) {
                p.place(cx + dx, bottomY - 1 - k, cz + dz,
                        rng.nextInt(4) == 0 ? accent : core);
            }
        }

        // Свисающие лианы / корни по периметру
        int hangCount = 8 + rng.nextInt(6);
        for (int i = 0; i < hangCount; i++) {
            double a = rng.nextDouble() * 2 * Math.PI;
            int hx = cx + (int) Math.round(Math.cos(a) * (topRadius - 1));
            int hz = cz + (int) Math.round(Math.sin(a) * (topRadius - 1));
            int hangLen = 3 + rng.nextInt(5);
            for (int k = 0; k < hangLen; k++) {
                p.place(hx, topY - 2 - k, hz, Material.HANGING_ROOTS);
            }
        }

        // Декорации сверху по теме.
        decorateTop(cx, cz, topY, topRadius, theme);

        // Водопад с края (10% шанс)
        if (rng.nextInt(3) == 0) {
            double a = rng.nextDouble() * 2 * Math.PI;
            int wx = cx + (int) Math.round(Math.cos(a) * (topRadius - 2));
            int wz = cz + (int) Math.round(Math.sin(a) * (topRadius - 2));
            // источник
            p.place(wx, topY, wz, Material.WATER);
            // струя
            for (int k = 1; k < depth + 6; k++) {
                p.place(wx, topY - k, wz, Material.WATER);
            }
        }
    }

    private void decorateTop(int cx, int cz, int topY, int radius, Theme theme) {
        switch (theme) {
            case DARK_RUINS -> {
                // Развалины колонны и арки
                p.place(cx, topY + 1, cz, Material.POLISHED_BLACKSTONE_WALL);
                p.place(cx, topY + 2, cz, Material.POLISHED_BLACKSTONE_WALL);
                p.place(cx, topY + 3, cz, Material.SOUL_LANTERN);
                for (int i = 0; i < 3; i++) {
                    int dx = rng.nextInt(radius - 1) - (radius - 1) / 2;
                    int dz = rng.nextInt(radius - 1) - (radius - 1) / 2;
                    p.place(cx + dx, topY + 1, cz + dz, Material.MOSSY_COBBLESTONE);
                }
                p.place(cx + 2, topY + 1, cz - 2, Material.CHEST);
            }
            case MOSSY_HILL -> {
                // Маленький холм + дерево
                int hillR = Math.min(3, radius - 2);
                for (int dx = -hillR; dx <= hillR; dx++) {
                    for (int dz = -hillR; dz <= hillR; dz++) {
                        int d2 = dx * dx + dz * dz;
                        if (d2 <= hillR * hillR) {
                            p.place(cx + dx, topY + 1, cz + dz, Material.MOSS_BLOCK);
                            if (d2 <= (hillR - 1) * (hillR - 1)) {
                                p.place(cx + dx, topY + 2, cz + dz, Material.MOSS_BLOCK);
                            }
                        }
                    }
                }
                // Маленький дуб поверх холма
                int treeH = 4 + rng.nextInt(2);
                for (int dy = 0; dy < treeH; dy++) {
                    p.place(cx, topY + 3 + dy, cz, Material.OAK_LOG);
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        p.place(cx + dx, topY + 2 + treeH, cz + dz, Material.OAK_LEAVES);
                    }
                }
                p.place(cx, topY + 3 + treeH, cz, Material.OAK_LEAVES);
            }
            case CRYSTAL -> {
                // Аметистовый кристалл-шипы
                p.place(cx, topY + 1, cz, Material.AMETHYST_BLOCK);
                p.place(cx, topY + 2, cz, Material.AMETHYST_BLOCK);
                p.place(cx, topY + 3, cz, Material.AMETHYST_CLUSTER);
                for (int i = 0; i < 5 + rng.nextInt(4); i++) {
                    int dx = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    int dz = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    if (dx * dx + dz * dz < radius * radius) {
                        p.place(cx + dx, topY + 1, cz + dz, Material.AMETHYST_CLUSTER);
                    }
                }
            }
            case CHERRY -> {
                // Сакура наверху
                BeachTrees.cherryBlossom(p, rng, cx, cz, topY);
                for (int i = 0; i < 6; i++) {
                    int dx = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    int dz = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    if (dx * dx + dz * dz < radius * radius) {
                        p.place(cx + dx, topY + 1, cz + dz, Material.PINK_PETALS);
                    }
                }
            }
            case CAMPSITE -> {
                // Маленький лагерь — костёр и палатка
                p.place(cx, topY + 1, cz, Material.SOUL_CAMPFIRE);
                p.place(cx + 2, topY + 1, cz, Material.OAK_LOG);
                p.place(cx - 2, topY + 1, cz, Material.OAK_LOG);
                p.place(cx, topY + 1, cz + 2, Material.OAK_LOG);
                // Палатка 3×2
                for (int dx = -1; dx <= 1; dx++) {
                    p.place(cx + dx, topY + 1, cz - 3, Material.RED_WOOL);
                    p.place(cx + dx, topY + 2, cz - 3, Material.RED_WOOL);
                }
                p.place(cx + 1, topY + 1, cz - 3, Material.CHEST);
            }
            case BARE -> {
                // Просто пара цветов
                for (int i = 0; i < 4; i++) {
                    int dx = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    int dz = rng.nextInt(radius * 2 - 1) - (radius - 1);
                    if (dx * dx + dz * dz < radius * radius) {
                        Material flower = switch (rng.nextInt(3)) {
                            case 0 -> Material.POPPY;
                            case 1 -> Material.DANDELION;
                            default -> Material.SHORT_GRASS;
                        };
                        p.place(cx + dx, topY + 1, cz + dz, flower);
                    }
                }
            }
        }
    }

    /** Цепной мост между двумя островами (или между островом и землёй). */
    public void chainBridge(int x1, int y1, int z1, int x2, int y2, int z2) {
        int steps = (int) Math.ceil(Math.sqrt(
                (x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1)
        ));
        if (steps < 2) return;
        for (int k = 0; k <= steps; k++) {
            double t = (double) k / steps;
            int x = x1 + (int) Math.round((x2 - x1) * t);
            int z = z1 + (int) Math.round((z2 - z1) * t);
            // лёгкий провис посередине
            double sag = Math.sin(t * Math.PI) * 1.5;
            int y = (int) Math.round(y1 + (y2 - y1) * t - sag);
            p.place(x, y, z, Material.CHAIN);
            if (k % 3 == 0 && k > 0 && k < steps) {
                // редкие планки
                p.place(x, y - 1, z, Material.OAK_PLANKS);
            }
        }
    }

    public enum Theme {
        DARK_RUINS(Material.DEEPSLATE, Material.GRASS_BLOCK, Material.COBBLED_DEEPSLATE),
        MOSSY_HILL(Material.STONE, Material.GRASS_BLOCK, Material.MOSSY_COBBLESTONE),
        CRYSTAL(Material.DEEPSLATE, Material.AMETHYST_BLOCK, Material.CALCITE),
        CHERRY(Material.STONE, Material.GRASS_BLOCK, Material.PINK_CONCRETE_POWDER),
        CAMPSITE(Material.STONE, Material.PODZOL, Material.COBBLESTONE),
        BARE(Material.STONE, Material.GRASS_BLOCK, Material.GRAVEL);

        final Material core, surface, accent;
        Theme(Material core, Material surface, Material accent) {
            this.core = core;
            this.surface = surface;
            this.accent = accent;
        }
    }
}
