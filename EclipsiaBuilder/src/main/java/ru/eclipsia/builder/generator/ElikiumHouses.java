package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Плотные кварталы жилых домов Эликия — 4 материальные семьи × 4 типа
 * крыш, размеры 8×10..12×14, 2-3 этажа, у каждого свой декор у входа.
 *
 * <p><b>Материальные семьи</b>:
 * <ul>
 *   <li>40% — фахверк OAK_PLANKS + DARK_OAK_LOG (белые/жёлтые окна);</li>
 *   <li>30% — DARK_OAK_PLANKS + STONE_BRICKS;</li>
 *   <li>20% — STONE_BRICKS + SPRUCE_LOG;</li>
 *   <li>10% — POLISHED_BLACKSTONE + DARK_OAK_LOG (богатые).</li>
 * </ul>
 *
 * <p><b>Типы крыш</b>:
 * <ol>
 *   <li>Двускатная (стандарт) OAK_STAIRS;</li>
 *   <li>Четырёхскатная (пирамида) DARK_OAK_STAIRS;</li>
 *   <li>С башенкой 3×3 на коньке;</li>
 *   <li>С мансардным окном (dormer) PURPLE_GLASS.</li>
 * </ol>
 *
 * <p><b>Размещение</b>: дома группируются в 6 кварталов вокруг главных
 * улиц. В каждом квартале — сетка 3×3 или 2×4 ячеек по 12-14 блоков с
 * промежутками 2-3 блока (узкие переулки между домами).
 */
public final class ElikiumHouses {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final ElikiumCity ctx;

    public ElikiumHouses(Plugin plugin, RegionPainter painter, Random rng, ElikiumCity ctx) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.ctx = ctx;
    }

    /** Кварталы — прямоугольные зоны для плотной застройки. */
    private static final int[][] BLOCKS = {
            // {xMin, zMin, xMax, zMax}
            {-90, -55, -45, -10},  // West-North block
            {-90,  -5, -50,  35},  // West-Center block (south of warehouse)
            { -85,  60, -10, 110},  // South-West block (around market)
            {  35, -110, 80, -65},  // North-Center block
            {  90,  10, 140,  45},  // East-Center block
            {  90, -100, 140, -55}, // East-North block (near guildhall)
    };

    public long build() {
        long count = 0;
        int totalPlaced = 0;
        for (int[] block : BLOCKS) {
            int placed = fillBlock(block[0], block[1], block[2], block[3]);
            totalPlaced += placed;
        }
        plugin.getLogger().info("ElikiumHouses: " + totalPlaced + " домов в "
                + BLOCKS.length + " кварталах.");
        return count;
    }

    /** Плотно заполнить квартал домами с шагом 12-14 блоков. */
    private int fillBlock(int xMin, int zMin, int xMax, int zMax) {
        int placed = 0;
        // Сетка с шагом 14 (плотная застройка)
        int step = 14;
        for (int x = xMin + 6; x + 6 <= xMax; x += step) {
            for (int z = zMin + 6; z + 6 <= zMax; z += step) {
                int w = 9 + rng.nextInt(3); // 9..11
                int d = 9 + rng.nextInt(3); // 9..11
                int hxMin = x - w / 2, hxMax = x + w / 2;
                int hzMin = z - d / 2, hzMax = z + d / 2;
                if (!isFreeFootprint(hxMin - 2, hzMin - 2, hxMax + 2, hzMax + 2)) {
                    continue;
                }
                buildHouse(x, z, w, d, rng.nextInt());
                ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
                placed++;
            }
        }
        return placed;
    }

    private boolean isFreeFootprint(int x1, int z1, int x2, int z2) {
        // Все 4 угла внутри полигона
        int[][] corners = {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}};
        for (int[] c : corners) {
            if (!WorldGenerator.isInsideCityPolygon(c[0], c[1])) return false;
            if (ElikiumCity.insideCathedralZone(c[0], c[1])) return false;
        }
        // Не пересекать другие footprint-ы
        ElikiumCity.Footprint candidate = new ElikiumCity.Footprint(x1, z1, x2, z2);
        for (ElikiumCity.Footprint f : ctx.occupied) {
            if (candidate.overlaps(f, 1)) return false;
        }
        // Не накрыть улицу (>20% площади)
        int streetCount = 0;
        int total = 0;
        for (int x = x1; x <= x2; x += 2) {
            for (int z = z1; z <= z2; z += 2) {
                total++;
                if (ctx.streetCells.contains(ElikiumCity.packCoord(x, z))) streetCount++;
            }
        }
        if (total > 0 && streetCount * 5 > total) return false;
        return true;
    }

    /** Построить готический жилой дом с вариативными материалами и крышей. */
    private void buildHouse(int cx, int cz, int w, int d, int seed) {
        Random hr = new Random(seed);
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;

        // Выбор материальной семьи
        int family = pickFamily(hr);
        Material wallA, wallB, pillar;
        switch (family) {
            case 0: wallA = Material.OAK_PLANKS; wallB = Material.DEEPSLATE_BRICKS; pillar = Material.DARK_OAK_LOG; break;
            case 1: wallA = Material.DARK_OAK_PLANKS; wallB = Material.STONE_BRICKS; pillar = Material.DARK_OAK_LOG; break;
            case 2: wallA = Material.STONE_BRICKS; wallB = Material.DEEPSLATE_BRICKS; pillar = Material.SPRUCE_LOG; break;
            default: wallA = Material.POLISHED_BLACKSTONE; wallB = Material.POLISHED_BLACKSTONE_BRICKS; pillar = Material.DARK_OAK_LOG; break;
        }

        int floors = 2 + hr.nextInt(2); // 2..3
        int floorH = 4;

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
            }
        }

        // Этажи
        for (int floor = 0; floor < floors; floor++) {
            int yBase = Y_BASE + 2 + floor * floorH;
            Material wall = (floor == 0) ? wallB : wallA;
            // Периметр
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                    if (!perim) continue;
                    boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                    Material mat = corner ? pillar : wall;
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, z, mat);
                    }
                }
            }
            // Промежуточные столбы (фахверк)
            if (family == 0 || family == 1) {
                for (int x = xMin + 4; x <= xMax - 4; x += 4) {
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, zMin, pillar);
                        painter.place(x, yBase + dy, zMax, pillar);
                    }
                }
            }
            // Окна
            Material winMat = (family == 3)
                    ? Material.PURPLE_STAINED_GLASS
                    : (hr.nextDouble() < 0.5 ? Material.YELLOW_STAINED_GLASS : Material.GLASS_PANE);
            placeWindowRow(xMin, xMax, zMin, yBase + 1, winMat);
            placeWindowRow(xMin, xMax, zMax, yBase + 1, winMat);
            placeWindowCol(zMin, zMax, xMin, yBase + 1, winMat);
            placeWindowCol(zMin, zMax, xMax, yBase + 1, winMat);
        }

        // Крыша — выбор типа
        int roofType = hr.nextInt(4);
        int roofYBase = Y_BASE + 2 + floors * floorH;
        Material roofMat = (family >= 2) ? Material.DARK_OAK_STAIRS : Material.OAK_STAIRS;
        Material roofFill = (family >= 2) ? Material.DARK_OAK_PLANKS : Material.OAK_PLANKS;
        switch (roofType) {
            case 0: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill); break;
            case 1: buildHipRoof(xMin, zMin, xMax, zMax, roofYBase, roofFill); break;
            case 2: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
                    buildRoofTurret(cx, cz, roofYBase + 4, roofFill, pillar);
                    break;
            case 3: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
                    buildDormer(cx, zMax, roofYBase + 1, roofFill);
                    break;
            default: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
        }

        // Дверь — главный вход на южной стене
        painter.place(cx, Y_BASE + 2, zMax, Material.AIR);
        painter.place(cx, Y_BASE + 3, zMax, Material.AIR);
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=north,hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=north,hinge=left]");
        painter.placeData(cx, Y_BASE + 2, zMax, door);
        painter.placeData(cx, Y_BASE + 3, zMax, doorTop);
        // Над дверью — фонарь
        painter.place(cx, Y_BASE + 5, zMax, Material.LANTERN);

        // Дымоход (60% шанс)
        if (hr.nextDouble() < 0.6) {
            int chOffsetX = (hr.nextBoolean() ? -1 : +1) * (w / 2 - 2);
            int chX = cx + chOffsetX;
            int chZ = cz - 1;
            int chTop = roofYBase + 6;
            for (int y = roofYBase; y <= chTop; y++) {
                painter.place(chX, y, chZ, Material.COBBLESTONE);
            }
            painter.place(chX, chTop + 1, chZ, Material.CAMPFIRE);
        }

        // Внутреннее освещение
        painter.place(cx, Y_BASE + 2 + floorH - 1, cz, Material.LANTERN);

        // Декор у входа
        if (hr.nextDouble() < 0.7) {
            painter.place(cx - 1, Y_BASE + 2, zMax + 1,
                    hr.nextBoolean() ? Material.BARREL : Material.OAK_PLANKS);
        }
        if (hr.nextDouble() < 0.6) {
            BlockData pot = Material.FLOWER_POT.createBlockData();
            painter.placeData(cx + 1, Y_BASE + 2, zMax + 1, pot);
        }
        // Лозы на 30% домов
        if (hr.nextDouble() < 0.3) {
            for (int dy = 0; dy < 4; dy++) {
                painter.place(xMax + 1, Y_BASE + 4 + dy, cz, Material.VINE);
            }
        }
    }

    private int pickFamily(Random hr) {
        double r = hr.nextDouble();
        if (r < 0.40) return 0;
        if (r < 0.70) return 1;
        if (r < 0.90) return 2;
        return 3;
    }

    private void placeWindowRow(int xMin, int xMax, int z, int y, Material mat) {
        for (int x = xMin + 2; x <= xMax - 2; x += 3) {
            painter.place(x, y, z, mat);
            painter.place(x, y + 1, z, mat);
        }
    }

    private void placeWindowCol(int zMin, int zMax, int x, int y, Material mat) {
        for (int z = zMin + 2; z <= zMax - 2; z += 3) {
            painter.place(x, y, z, mat);
            painter.place(x, y + 1, z, mat);
        }
    }

    private void buildGableRoof(int xMin, int zMin, int xMax, int zMax,
                                 int yBase, boolean alongZ, Material fillMat) {
        int span = alongZ ? (xMax - xMin) : (zMax - zMin);
        int roofH = span / 2 + 1;
        int cx = (xMin + xMax) / 2, cz = (zMin + zMax) / 2;
        for (int rise = 0; rise <= roofH; rise++) {
            int y = yBase + rise;
            if (alongZ) {
                int dx = (span / 2) - rise;
                if (dx < 0) break;
                int xL = cx - dx, xR = cx + dx;
                for (int z = zMin; z <= zMax; z++) {
                    painter.place(xL, y, z, fillMat);
                    if (xR != xL) painter.place(xR, y, z, fillMat);
                }
                // Фронтоны
                for (int x = xL; x <= xR; x++) {
                    painter.place(x, y, zMin, Material.DEEPSLATE_BRICKS);
                    painter.place(x, y, zMax, Material.DEEPSLATE_BRICKS);
                }
            } else {
                int dz = (span / 2) - rise;
                if (dz < 0) break;
                int zN = cz - dz, zS = cz + dz;
                for (int x = xMin; x <= xMax; x++) {
                    painter.place(x, y, zN, fillMat);
                    if (zS != zN) painter.place(x, y, zS, fillMat);
                }
                for (int z = zN; z <= zS; z++) {
                    painter.place(xMin, y, z, Material.DEEPSLATE_BRICKS);
                    painter.place(xMax, y, z, Material.DEEPSLATE_BRICKS);
                }
            }
        }
    }

    private void buildHipRoof(int xMin, int zMin, int xMax, int zMax,
                              int yBase, Material fillMat) {
        int curXMin = xMin, curXMax = xMax, curZMin = zMin, curZMax = zMax;
        int rise = 0;
        while (curXMin <= curXMax && curZMin <= curZMax) {
            int y = yBase + rise;
            for (int x = curXMin; x <= curXMax; x++) {
                painter.place(x, y, curZMin, fillMat);
                painter.place(x, y, curZMax, fillMat);
            }
            for (int z = curZMin + 1; z < curZMax; z++) {
                painter.place(curXMin, y, z, fillMat);
                painter.place(curXMax, y, z, fillMat);
            }
            curXMin++; curXMax--; curZMin++; curZMax--;
            rise++;
        }
    }

    private void buildRoofTurret(int cx, int cz, int yBase, Material fillMat, Material roofMat) {
        // 3×3 башенка
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean perim = (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                    if (perim) painter.place(cx + dx, yBase + dy, cz + dz, fillMat);
                }
            }
        }
        // Окно
        painter.place(cx, yBase + 1, cz - 1, Material.PURPLE_STAINED_GLASS);
        // Шпиль
        painter.place(cx, yBase + 3, cz, Material.DEEPSLATE_BRICKS);
        painter.place(cx, yBase + 4, cz, Material.END_ROD);
    }

    private void buildDormer(int cx, int zMax, int yBase, Material fillMat) {
        // Мансардное окно — выступ из крыши
        for (int dx = -1; dx <= 1; dx++) {
            painter.place(cx + dx, yBase, zMax - 1, fillMat);
            painter.place(cx + dx, yBase + 1, zMax - 1, fillMat);
        }
        painter.place(cx, yBase + 1, zMax - 1, Material.PURPLE_STAINED_GLASS);
        painter.place(cx, yBase + 2, zMax - 1, fillMat);
    }
}
