package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Окружение основного мира: всё, что снаружи стен Эликия. Делится по
 * сторонам света:
 *
 * <ul>
 *   <li><b>Север</b> — поля пшеницы, ветряная мельница, редкие дубы.</li>
 *   <li><b>Восток</b> — лес из берёз и дубов, ручей с мостиком,
 *       охотничий домик.</li>
 *   <li><b>Запад</b> — холмы (уже сформированы фазой 1) +
 *       заброшенная шахта (вход в горе) с вагонеткой и сундуком.</li>
 *   <li><b>Юг</b> — рыбацкая деревня на берегу озера, пирс, остров с
 *       ивой (озеро уже выкопано фазой 1).</li>
 * </ul>
 *
 * <p>Деревья сажаются через {@link World#generateTree}, что лучше чем
 * вручную выкладывать листву и логи — у деревьев получается естественная
 * форма и листва биом-окрашена правильно.
 */
public final class WorldSurroundings {

    private static final int CX = WorldGenerator.CITY_X;
    private static final int CZ = WorldGenerator.CITY_Z;
    private static final int HALF = WorldGenerator.CITY_HALF;
    private static final int BASE_Y = WorldGenerator.BASE_GROUND_Y; // 64
    private static final int FLOOR_Y = WorldGenerator.CITY_FLOOR_Y; // 70

    private final Plugin plugin;
    private final World world;

    public WorldSurroundings(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void buildAll(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: фаза 5 — окружение Эликия…");

        buildNorthFields(p, rng);
        buildEastForest(p, rng);
        buildWestMine(p, rng);
        buildSouthFishingVillage(p, rng);
    }

    // =========================================================================
    // СЕВЕР: поля пшеницы + ветряная мельница
    // =========================================================================

    private void buildNorthFields(RegionPainter p, Random rng) {
        // 5 рядов пшеницы по 20 блоков, западнее северной дороги.
        int fieldX0 = -25;
        int fieldX1 = -6;
        int fieldZ0 = -90;
        int fieldZ1 = -50;
        int yField = FLOOR_Y - 1;

        for (int x = fieldX0; x <= fieldX1; x++) {
            for (int z = fieldZ0; z <= fieldZ1; z++) {
                if ((x - fieldX0) % 4 == 0) {
                    // борозда — DIRT_PATH
                    p.place(x, yField, z, Material.DIRT_PATH);
                } else {
                    p.place(x, yField, z, Material.FARMLAND);
                    p.place(x, yField + 1, z, Material.WHEAT);
                }
            }
        }

        // Ещё одно поле — тыквы и редкие сено-стога — справа от дороги.
        for (int x = 6; x <= 25; x++) {
            for (int z = -90; z <= -50; z++) {
                if (rng.nextDouble() < 0.04) {
                    p.place(x, FLOOR_Y, z, Material.PUMPKIN);
                } else if (rng.nextDouble() < 0.02) {
                    p.place(x, FLOOR_Y, z, Material.HAY_BLOCK);
                }
            }
        }

        // Ветряная мельница на холме (FLOOR_Y + 4).
        int mx = -28, mz = -110;
        // Холм-постамент (просто столбик GRASS под основанием).
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    p.place(mx + dx, FLOOR_Y - 1, mz + dz, Material.GRASS_BLOCK);
                    p.place(mx + dx, FLOOR_Y - 2, mz + dz, Material.DIRT);
                }
            }
        }
        // Башня мельницы 8 высотой (OAK_PLANKS), 3×3 база.
        for (int dy = 0; dy < 8; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean isShell = (dx == -1 || dx == 1 || dz == -1 || dz == 1);
                    if (isShell) {
                        p.place(mx + dx, FLOOR_Y + dy, mz + dz, Material.OAK_PLANKS);
                    } else {
                        p.place(mx + dx, FLOOR_Y + dy, mz + dz, Material.AIR);
                    }
                }
            }
        }
        // Дверь.
        p.place(mx, FLOOR_Y + 1, mz + 1, Material.AIR);
        p.place(mx, FLOOR_Y + 2, mz + 1, Material.AIR);
        // Конусная крыша.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(mx + dx, FLOOR_Y + 8, mz + dz, Material.DARK_OAK_PLANKS);
            }
        }
        p.place(mx, FLOOR_Y + 9, mz, Material.DARK_OAK_PLANKS);

        // «Крылья» — крест из OAK_FENCE, длина 5, торчат на восток мельницы.
        int axleX = mx + 2; // ось чуть восточнее
        int axleY = FLOOR_Y + 5;
        int axleZ = mz;
        // Крест в плоскости YZ:
        for (int d = -4; d <= 4; d++) {
            if (d == 0) continue;
            p.place(axleX, axleY + d, axleZ, Material.OAK_FENCE);
            p.place(axleX, axleY, axleZ + d, Material.OAK_FENCE);
        }
        // Втулка.
        p.place(axleX, axleY, axleZ, Material.OAK_LOG);

        // Несколько редких дубов на лугах между полями и городом.
        // (z0 < z1, иначе rng.nextInt(z1-z0)=nextInt(-10) даёт фиксированный z.)
        scatterTrees(p, rng, -40, -45, 25, -35, 6, TreeType.TREE);

        // FloatingText.
        FloatingText.createSign(plugin, world,
                mx + 0.5, FLOOR_Y + 11, mz + 0.5, "§7Ветряная мельница");
    }

    // =========================================================================
    // ВОСТОК: лес + ручей + мостик + охотничий домик
    // =========================================================================

    private void buildEastForest(RegionPainter p, Random rng) {
        // Лес: 40-50 деревьев в зоне x=50..150, z=-50..50, плотность ~5%.
        int forestX0 = 50, forestX1 = 145;
        int forestZ0 = -50, forestZ1 = 50;
        int trees = 0, target = 45;
        int attempts = 0, attemptsCap = 600;
        while (trees < target && attempts < attemptsCap) {
            attempts++;
            int x = forestX0 + rng.nextInt(forestX1 - forestX0);
            int z = forestZ0 + rng.nextInt(forestZ1 - forestZ0);
            // Не сажаем на дороге (z=±3).
            if (Math.abs(z) <= 4) continue;
            if (rng.nextDouble() < 0.5) {
                placeTreeAt(p, rng, x, z, TreeType.BIRCH);
            } else {
                placeTreeAt(p, rng, x, z, TreeType.TREE);
            }
            trees++;
        }

        // Ручей: WATER ширина 2-3, идёт почти параллельно дороге со смещением,
        // от северо-востока (x=110, z=-50) к юго-востоку (x=110, z=50).
        // Делаем извилистым: 5 опорных точек.
        int[][] streamPts = {
                { 110, -50 }, { 105, -25 }, { 115, 0 }, { 112, 25 }, { 108, 50 }
        };
        for (int i = 0; i + 1 < streamPts.length; i++) {
            int sx = streamPts[i][0], sz = streamPts[i][1];
            int ex = streamPts[i + 1][0], ez = streamPts[i + 1][1];
            // Прокладка воды на y=FLOOR_Y-2 (ниже травы).
            p.path(sx, sz, ex, ez, FLOOR_Y - 1, 1, () -> Material.WATER);
        }

        // Охотничий домик: 6×6, OAK_PLANKS, рядом с ручьём.
        int hx = 130 - 3, hz = -10 - 3;
        int yBase = FLOOR_Y;
        // Пол.
        for (int x = hx; x < hx + 6; x++) {
            for (int z = hz; z < hz + 6; z++) {
                p.place(x, yBase, z, Material.OAK_PLANKS);
            }
        }
        // Стены.
        for (int dy = 1; dy <= 4; dy++) {
            for (int x = hx; x < hx + 6; x++) {
                p.place(x, yBase + dy, hz, Material.OAK_LOG);
                p.place(x, yBase + dy, hz + 5, Material.OAK_LOG);
            }
            for (int z = hz; z < hz + 6; z++) {
                p.place(hx, yBase + dy, z, Material.OAK_LOG);
                p.place(hx + 5, yBase + dy, z, Material.OAK_LOG);
            }
        }
        // Очищаем интерьер.
        for (int dy = 1; dy <= 3; dy++) {
            for (int x = hx + 1; x < hx + 5; x++) {
                for (int z = hz + 1; z < hz + 5; z++) {
                    p.place(x, yBase + dy, z, Material.AIR);
                }
            }
        }
        // Крыша 6×6 DARK_OAK_PLANKS на y=5.
        for (int x = hx; x < hx + 6; x++) {
            for (int z = hz; z < hz + 6; z++) {
                p.place(x, yBase + 5, z, Material.DARK_OAK_PLANKS);
            }
        }
        // Дверь на запад (к ручью).
        p.place(hx, yBase + 1, hz + 2, Material.AIR);
        p.place(hx, yBase + 2, hz + 2, Material.AIR);
        // Камин и трофей.
        p.place(hx + 1, yBase + 1, hz + 4, Material.NETHERRACK);
        p.place(hx + 1, yBase + 2, hz + 4, Material.FIRE);
        p.place(hx + 4, yBase + 1, hz + 4, Material.CRAFTING_TABLE);
        p.place(hx + 4, yBase + 2, hz + 4, Material.SKELETON_SKULL);

        FloatingText.createSign(plugin, world,
                hx + 3.0, yBase + 7, hz + 3.0, "§7Охотничий домик");
    }

    // =========================================================================
    // ЗАПАД: заброшенная шахта (вход в горе)
    // =========================================================================

    private void buildWestMine(RegionPainter p, Random rng) {
        // Вход 3×3 в склоне холма. Холмы у нас формируются фазой 1
        // (горы на x<-80), сажаем шахту на x=-110, z=0.
        int mx = -110;
        int mz = 0;

        // Туннель 3×3, длина 12 блоков, чуть наклонный вниз.
        int yEntrance = FLOOR_Y;
        for (int dl = 0; dl < 12; dl++) {
            int yy = yEntrance - dl / 4; // лёгкий уклон вниз
            for (int oy = 1; oy <= 3; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    p.place(mx - dl, yy + oy, mz + oz, Material.AIR);
                }
            }
            // Опоры из DARK_OAK_LOG каждые 3 блока.
            if (dl % 3 == 0) {
                p.place(mx - dl, yy + 1, mz - 1, Material.DARK_OAK_LOG);
                p.place(mx - dl, yy + 1, mz + 1, Material.DARK_OAK_LOG);
                p.place(mx - dl, yy + 2, mz - 1, Material.DARK_OAK_LOG);
                p.place(mx - dl, yy + 2, mz + 1, Material.DARK_OAK_LOG);
                p.place(mx - dl, yy + 3, mz, Material.DARK_OAK_LOG);
            }
        }
        // Рельс и вагонетка-сундук в конце туннеля.
        int endX = mx - 11;
        int endY = yEntrance - 2;
        for (int dl = 0; dl <= 8; dl++) {
            p.place(mx - dl, endY, mz, Material.RAIL);
        }
        // Сундук на конечной (заменяет вагонетку — её надо спавнить как entity).
        p.place(endX, endY + 1, mz, Material.CHEST);
        // Несколько руд в стенах туннеля.
        Material[] ores = { Material.IRON_ORE, Material.COAL_ORE, Material.GOLD_ORE };
        for (int i = 0; i < 8; i++) {
            int dl = 1 + rng.nextInt(11);
            int side = rng.nextBoolean() ? -1 : 1;
            int dy = 1 + rng.nextInt(3);
            p.place(mx - dl, FLOOR_Y - dl / 4 + dy, mz + side * 2, ores[rng.nextInt(ores.length)]);
        }

        FloatingText.createSign(plugin, world,
                mx + 0.5, FLOOR_Y + 5, mz + 0.5, "§8Заброшенная шахта");
    }

    // =========================================================================
    // ЮГ: рыбацкая деревня + пирс на озере
    // =========================================================================

    private void buildSouthFishingVillage(RegionPainter p, Random rng) {
        // Озеро уже у CZ=95, остров у того же центра 8×8. Деревня — на берегу,
        // т.е. около z=70..78 (между городом и озером), 3 домика 5×5.
        int yBase = FLOOR_Y;
        int[][] hutCoords = {
                { -10, 75 }, { 0, 73 }, { 10, 75 }
        };
        for (int[] hc : hutCoords) {
            int hx = hc[0] - 2, hz = hc[1] - 2;
            // Пол.
            for (int x = hx; x < hx + 5; x++) {
                for (int z = hz; z < hz + 5; z++) {
                    p.place(x, yBase, z, Material.OAK_PLANKS);
                }
            }
            // Стены.
            for (int dy = 1; dy <= 3; dy++) {
                for (int x = hx; x < hx + 5; x++) {
                    p.place(x, yBase + dy, hz, Material.OAK_PLANKS);
                    p.place(x, yBase + dy, hz + 4, Material.OAK_PLANKS);
                }
                for (int z = hz; z < hz + 5; z++) {
                    p.place(hx, yBase + dy, z, Material.OAK_PLANKS);
                    p.place(hx + 4, yBase + dy, z, Material.OAK_PLANKS);
                }
            }
            // Очистка.
            for (int dy = 1; dy <= 2; dy++) {
                for (int x = hx + 1; x < hx + 4; x++) {
                    for (int z = hz + 1; z < hz + 4; z++) {
                        p.place(x, yBase + dy, z, Material.AIR);
                    }
                }
            }
            // Крыша двускатная.
            for (int dx = 0; dx < 5; dx++) {
                int peakOff = 2 - Math.abs(dx - 2);
                for (int dz = 0; dz < 5; dz++) {
                    p.place(hx + dx, yBase + 4 + peakOff, hz + dz, Material.DARK_OAK_PLANKS);
                }
            }
            // Дверь на север.
            p.place(hx + 2, yBase + 1, hz, Material.AIR);
            p.place(hx + 2, yBase + 2, hz, Material.AIR);
        }

        // Пирс: 3 ряда OAK_PLANKS длиной 12, идущие на юг к озеру.
        int pierX = 0, pierZ0 = 80, pierLen = 14;
        for (int dl = 0; dl < pierLen; dl++) {
            for (int dx = -1; dx <= 1; dx++) {
                p.place(pierX + dx, yBase, pierZ0 + dl, Material.OAK_PLANKS);
            }
            // Перила
            if (dl > 0) {
                p.place(pierX - 2, yBase + 1, pierZ0 + dl, Material.OAK_FENCE);
                p.place(pierX + 2, yBase + 1, pierZ0 + dl, Material.OAK_FENCE);
            }
        }
        // Лодка-каркас (символическая) на конце пирса.
        for (int dx = -1; dx <= 1; dx++) {
            p.place(pierX + dx, yBase, pierZ0 + pierLen, Material.OAK_PLANKS);
        }

        // Сети: COBWEB на OAK_FENCE между двумя домиками.
        for (int dz = 0; dz < 4; dz++) {
            p.place(-5, yBase + 3, 75 + dz, Material.OAK_FENCE);
            p.place(5, yBase + 3, 75 + dz, Material.OAK_FENCE);
            if (dz % 2 == 0) {
                p.place(-5, yBase + 4, 75 + dz, Material.COBWEB);
                p.place(5, yBase + 4, 75 + dz, Material.COBWEB);
            }
        }

        // Ива на острове (центр озера, x=0, z=95). Остров — DIRT/GRASS уже
        // выложен фазой 1; сажаем «иву» вручную: DARK_OAK_LOG ствол + VINE.
        int wx = 0, wz = 95;
        int wlogY = WorldGenerator.LAKE_WATER_Y + 1; // 64
        for (int dy = 0; dy < 6; dy++) {
            p.place(wx, wlogY + dy, wz, Material.DARK_OAK_LOG);
        }
        // Крона из листвы.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                p.place(wx + dx, wlogY + 6, wz + dz, Material.DARK_OAK_LEAVES);
                p.place(wx + dx, wlogY + 5, wz + dz, Material.DARK_OAK_LEAVES);
            }
        }
        // Свисающие ветки (VINE по бокам).
        for (int dx : new int[] { -2, 2 }) {
            for (int dy = 0; dy < 3; dy++) {
                p.place(wx + dx, wlogY + 4 - dy, wz, Material.VINE);
            }
        }

        FloatingText.createSign(plugin, world,
                pierX + 0.5, yBase + 4, pierZ0 + pierLen / 2.0, "§bРыбацкая деревня");
        FloatingText.createSign(plugin, world,
                wx + 0.5, wlogY + 8, wz + 0.5, "§bОзеро Эликия");
    }

    // =========================================================================
    // Хелперы
    // =========================================================================

    private void scatterTrees(RegionPainter p, Random rng,
                               int x0, int z0, int x1, int z1,
                               int count, TreeType type) {
        // Нормализуем диапазоны — nextInt(отрицательное) падает/вырождается.
        int xLo = Math.min(x0, x1), xHi = Math.max(x0, x1);
        int zLo = Math.min(z0, z1), zHi = Math.max(z0, z1);
        int xSpan = Math.max(1, xHi - xLo);
        int zSpan = Math.max(1, zHi - zLo);
        for (int i = 0; i < count; i++) {
            int x = xLo + rng.nextInt(xSpan);
            int z = zLo + rng.nextInt(zSpan);
            placeTreeAt(p, rng, x, z, type);
        }
    }

    /**
     * Ставит дерево вручную через {@link RegionPainter}: ствол
     * 5–6 блоков + крона из листьев.
     *
     * <p>Почему не {@link World#generateTree}: на момент фазы 5 весь
     * ландшафт фазы 1 ещё в очереди {@code RegionPainter}
     * (фактический {@code flush()} вызывается всего один раз в конце
     * {@code WorldGenerator.generate}). {@code World.generateTree} работает
     * с реальными блоками мира, поэтому в несколько раз из
     * ста деревьев реально прорастало лишь одно–два.
     */
    private void placeTreeAt(RegionPainter p, Random rng, int x, int z, TreeType type) {
        Material log;
        Material leaves;
        if (type == TreeType.BIRCH) {
            log = Material.BIRCH_LOG;
            leaves = Material.BIRCH_LEAVES;
        } else {
            log = Material.OAK_LOG;
            leaves = Material.OAK_LEAVES;
        }

        int yBase = FLOOR_Y;
        int trunkH = 4 + rng.nextInt(2); // 4–5

        // Ствол.
        for (int dy = 0; dy < trunkH; dy++) {
            p.place(x, yBase + dy, z, log);
        }

        // Крона: два ряда 5×5 (без углов) + верхний 3×3.
        int yCanopy = yBase + trunkH;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rng.nextBoolean()) continue;
                p.place(x + dx, yCanopy, z + dz, leaves);
                p.place(x + dx, yCanopy - 1, z + dz, leaves);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                p.place(x + dx, yCanopy + 1, z + dz, leaves);
            }
        }
        p.place(x, yCanopy + 2, z, leaves);
        // Верхушка ствола.
        p.place(x, yCanopy, z, log);
        p.place(x, yCanopy + 1, z, log);
    }
}
