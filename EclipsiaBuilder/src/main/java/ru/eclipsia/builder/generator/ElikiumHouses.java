package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Плотные кварталы жилых домов Эликия. {@code ~50-70} домов в 11 кварталах
 * + {@code 4} фирменных «двора-колодца» (4 дома вокруг 3×3 центра с
 * фонтаном/колодцем).
 *
 * <p><b>Материальные семьи</b>:
 * <ul>
 *   <li>40% — фахверк OAK_PLANKS + DARK_OAK_LOG (белые/жёлтые окна);</li>
 *   <li>30% — DARK_OAK_PLANKS + STONE_BRICKS;</li>
 *   <li>20% — STONE_BRICKS + SPRUCE_LOG;</li>
 *   <li>10% — POLISHED_BLACKSTONE + DARK_OAK_LOG (богатые).</li>
 * </ul>
 *
 * <p><b>Типы крыш</b>: двускатная, шатровая, с башенкой, с мансардой.
 *
 * <p><b>Размещение</b>: сетка с шагом 11 (дома 9-10 + 1-2 узких прохода
 * между домами — фахверк-город «стенка к стенке»). Двери ориентированы
 * к ближайшей стороне квартала (попытка смотреть на улицу).
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

    /**
     * Кварталы — широкие зоны для плотной застройки. Перекрытия с POI/площадями
     * автоматически отбраковываются {@link #isFreeFootprint}.
     * Координаты: {xMin, zMin, xMax, zMax}.
     */
    private static final int[][] BLOCKS = {
            // Запад
            {-145, -110, -50, -55},   // 0: W-N
            {-145,  -45, -50, -15},   // 1: W-C-N
            {-145,   30, -50,  62},   // 2: W-C-S (между лавкой и рынком)
            {-145,   80, -50, 120},   // 3: W-S
            // Восток
            {  90, -115, 145, -65},   // 4: E-N
            {  90,  -25, 145,  25},   // 5: E-C
            {  90,   70, 145, 120},   // 6: E-S
            // Север (выше собора)
            { -55, -145,  10, -90},   // 7: N-W
            {  15, -145,  80, -90},   // 8: N-C
            // Юг (ниже собора и площадей)
            { -50,   55,   8, 120},   // 9: S-W (около спавна)
            {  15,   55,  80, 120},   // 10: S-C
    };

    /**
     * Дворы-колодцы: 4 дома вокруг 3×3 центра с фонтаном/деревом/колодцем.
     * {cx, cz} — центр двора. Сам дом-карре занимает ~17×17.
     */
    private static final int[][] COURTYARDS = {
            {-90, -25},   // запад-центр
            {-90,  95},   // запад-юг
            { 55, -90},   // север
            {115,   0},   // восток
    };

    public long build() {
        long count = 0;
        int totalPlaced = 0;
        int courtyardsPlaced = 0;

        // 1. Сначала ставим дворы-колодцы (приоритет — они занимают по 17×17).
        for (int[] cy : COURTYARDS) {
            if (buildCourtyard(cy[0], cy[1])) {
                courtyardsPlaced++;
                totalPlaced += 4;
            }
        }

        // 2. Плотная сетка домов в кварталах.
        for (int[] block : BLOCKS) {
            int placed = fillBlock(block[0], block[1], block[2], block[3]);
            totalPlaced += placed;
        }
        plugin.getLogger().info("ElikiumHouses: " + totalPlaced + " домов ("
                + courtyardsPlaced + " дворов-колодцев + " + BLOCKS.length + " кварталов).");
        return count;
    }

    /** Плотно заполнить квартал домами с шагом 11 (стенка к стенке). */
    private int fillBlock(int xMin, int zMin, int xMax, int zMax) {
        int placed = 0;
        int step = 11;
        for (int x = xMin + 5; x + 5 <= xMax; x += step) {
            for (int z = zMin + 5; z + 5 <= zMax; z += step) {
                int w = 8 + rng.nextInt(3); // 8..10
                int d = 8 + rng.nextInt(3); // 8..10
                int hxMin = x - w / 2, hxMax = x + w / 2;
                int hzMin = z - d / 2, hzMax = z + d / 2;
                if (!isFreeFootprint(hxMin - 1, hzMin - 1, hxMax + 1, hzMax + 1)) {
                    continue;
                }
                int doorFacing = pickDoorFacing(x, z, xMin, zMin, xMax, zMax);
                buildHouse(x, z, w, d, rng.nextInt(), doorFacing);
                ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
                placed++;
            }
        }
        return placed;
    }

    /**
     * Двор-колодец: 4 дома по сторонам света вокруг центрального
     * 3×3 фонтана/колодца. Все 4 дома получают двери, направленные
     * на центр (в проход).
     *
     * @return {@code true} если хотя бы 3 из 4 домов поставлены успешно
     */
    private boolean buildCourtyard(int cx, int cz) {
        // Внешний footprint двора — 19×19
        int extXMin = cx - 9, extXMax = cx + 9;
        int extZMin = cz - 9, extZMax = cz + 9;
        if (!isFreeFootprint(extXMin, extZMin, extXMax, extZMax)) return false;
        if (!WorldGenerator.isInsideCityPolygon(extXMin, extZMin)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMin, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMin)) return false;
        if (ElikiumCity.insideCathedralZone(extXMin, extZMin)
                || ElikiumCity.insideCathedralZone(extXMax, extZMax)) return false;

        // 4 дома по сторонам, 7×7 каждый, обращены дверьми к центру.
        // N, E, S, W
        int[][] houseSpots = {
                {cx,       cz - 6},   // N (door facing south = к центру)
                {cx + 6,   cz},       // E
                {cx,       cz + 6},   // S
                {cx - 6,   cz},       // W
        };
        int[] facings = {2, 3, 0, 1}; // 0=N,1=E,2=S,3=W (дверь смотрит на центр)
        int built = 0;
        for (int i = 0; i < 4; i++) {
            int hx = houseSpots[i][0];
            int hz = houseSpots[i][1];
            int hw = 7, hd = 7;
            int hxMin = hx - hw / 2, hxMax = hx + hw / 2;
            int hzMin = hz - hd / 2, hzMax = hz + hd / 2;
            buildHouse(hx, hz, hw, hd, rng.nextInt(), facings[i]);
            ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
            built++;
        }

        // Центральный двор 3×3: фонтан (1×1 WATER + STONE_BRICKS вокруг) + 2 цветочных горшка
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE, cz + dz, Material.STONE_BRICKS);
            }
        }
        painter.place(cx, Y_BASE + 1, cz, Material.WATER);
        // Угловые цветочные горшки и лавка
        BlockData pot = Material.FLOWER_POT.createBlockData();
        painter.placeData(cx - 1, Y_BASE + 1, cz - 1, pot);
        painter.placeData(cx + 1, Y_BASE + 1, cz + 1, pot);
        // Фонарь над фонтаном
        painter.place(cx, Y_BASE + 4, cz, Material.SOUL_LANTERN);
        for (int dy = 1; dy <= 3; dy++) {
            painter.place(cx, Y_BASE + dy, cz, dy == 1 ? Material.WATER : Material.OAK_FENCE);
        }

        ctx.pois.add(new ElikiumCity.POI("§7Двор-колодец", "§8...", cx, Y_BASE + 6, cz));
        return built >= 3;
    }

    /** Возвращает направление двери (0=N,1=E,2=S,3=W) — наружу к ближайшей стороне квартала. */
    private int pickDoorFacing(int cx, int cz, int bxMin, int bzMin, int bxMax, int bzMax) {
        int dN = cz - bzMin;
        int dS = bzMax - cz;
        int dW = cx - bxMin;
        int dE = bxMax - cx;
        int min = Math.min(Math.min(dN, dS), Math.min(dW, dE));
        if (min == dN) return 0;
        if (min == dE) return 1;
        if (min == dS) return 2;
        return 3;
    }

    private boolean isFreeFootprint(int x1, int z1, int x2, int z2) {
        int[][] corners = {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2},
                           {(x1 + x2) / 2, (z1 + z2) / 2}};
        for (int[] c : corners) {
            if (!WorldGenerator.isInsideCityPolygon(c[0], c[1])) return false;
            if (ElikiumCity.insideCathedralZone(c[0], c[1])) return false;
        }
        ElikiumCity.Footprint candidate = new ElikiumCity.Footprint(x1, z1, x2, z2);
        for (ElikiumCity.Footprint f : ctx.occupied) {
            if (candidate.overlaps(f, 0)) return false;
        }
        // Не накрыть улицу (>15% площади)
        int streetCount = 0;
        int total = 0;
        for (int x = x1; x <= x2; x += 2) {
            for (int z = z1; z <= z2; z += 2) {
                total++;
                if (ctx.streetCells.contains(ElikiumCity.packCoord(x, z))) streetCount++;
            }
        }
        if (total > 0 && streetCount * 7 > total) return false;
        return true;
    }

    /**
     * Построить готический жилой дом.
     *
     * @param facing 0=N,1=E,2=S,3=W — направление двери (на эту сторону)
     */
    private void buildHouse(int cx, int cz, int w, int d, int seed, int facing) {
        Random hr = new Random(seed);
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;

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
            // Промежуточные fахверк-столбы
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
                    : (hr.nextDouble() < 0.6 ? Material.YELLOW_STAINED_GLASS : Material.GLASS_PANE);
            placeWindowRow(xMin, xMax, zMin, yBase + 1, winMat);
            placeWindowRow(xMin, xMax, zMax, yBase + 1, winMat);
            placeWindowCol(zMin, zMax, xMin, yBase + 1, winMat);
            placeWindowCol(zMin, zMax, xMax, yBase + 1, winMat);
        }

        // Крыша
        int roofType = hr.nextInt(4);
        int roofYBase = Y_BASE + 2 + floors * floorH;
        Material roofFill = (family >= 2) ? Material.DARK_OAK_PLANKS : Material.OAK_PLANKS;
        switch (roofType) {
            case 0: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill); break;
            case 1: buildHipRoof(xMin, zMin, xMax, zMax, roofYBase, roofFill); break;
            case 2: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
                    buildRoofTurret(cx, cz, roofYBase + 4, roofFill, pillar);
                    break;
            case 3: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
                    buildDormer(cx, cz, zMin, zMax, roofYBase + 1, roofFill, facing);
                    break;
            default: buildGableRoof(xMin, zMin, xMax, zMax, roofYBase, d >= w, roofFill);
        }

        // Дверь — на стороне `facing`
        placeDoor(cx, cz, xMin, xMax, zMin, zMax, facing);

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

        // Внутреннее освещение (фонарь под потолком)
        painter.place(cx, Y_BASE + 1 + floorH, cz, Material.LANTERN);

        // Декор у входа — обязателен у каждого дома
        placeDoorDecor(cx, cz, xMin, xMax, zMin, zMax, facing, hr);

        // Лозы (30%)
        if (hr.nextDouble() < 0.3) {
            int vineX = (hr.nextBoolean() ? xMin - 1 : xMax + 1);
            int vineZ = cz + (hr.nextInt(d) - d / 2);
            for (int dy = 0; dy < 4; dy++) {
                painter.place(vineX, Y_BASE + 4 + dy, vineZ, Material.VINE);
            }
        }

        // Настенный SOUL_TORCH на одной из боковых стен (50%)
        if (hr.nextDouble() < 0.5) {
            int torchSide = hr.nextInt(2) == 0 ? -1 : 1;
            int torchX = (torchSide == -1 ? xMin - 1 : xMax + 1);
            painter.place(torchX, Y_BASE + 4, cz, Material.SOUL_TORCH);
        }
    }

    /** Поставить двойную дверь + проём в зависимости от направления. */
    private void placeDoor(int cx, int cz, int xMin, int xMax, int zMin, int zMax, int facing) {
        int dx = cx, dz = cz;
        String facingStr;
        switch (facing) {
            case 0: dz = zMin; facingStr = "south"; break;
            case 1: dx = xMax; facingStr = "west"; break;
            case 2: dz = zMax; facingStr = "north"; break;
            default: dx = xMin; facingStr = "east"; break;
        }
        painter.place(dx, Y_BASE + 2, dz, Material.AIR);
        painter.place(dx, Y_BASE + 3, dz, Material.AIR);
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=" + facingStr + ",hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=" + facingStr + ",hinge=left]");
        painter.placeData(dx, Y_BASE + 2, dz, door);
        painter.placeData(dx, Y_BASE + 3, dz, doorTop);
        // Фонарь над дверью
        painter.place(dx, Y_BASE + 5, dz, Material.LANTERN);
    }

    /** Декор у входа: горшок с цветами + бочка/ящик ВСЕГДА. */
    private void placeDoorDecor(int cx, int cz, int xMin, int xMax, int zMin, int zMax,
                                 int facing, Random hr) {
        // Координата сразу за дверью (на улице)
        int outX = cx, outZ = cz;
        int sideX1 = cx, sideX2 = cx, sideZ1 = cz, sideZ2 = cz;
        switch (facing) {
            case 0: outZ = zMin - 1; sideX1 = cx - 1; sideX2 = cx + 1; sideZ1 = sideZ2 = zMin - 1; break;
            case 1: outX = xMax + 1; sideZ1 = cz - 1; sideZ2 = cz + 1; sideX1 = sideX2 = xMax + 1; break;
            case 2: outZ = zMax + 1; sideX1 = cx - 1; sideX2 = cx + 1; sideZ1 = sideZ2 = zMax + 1; break;
            default: outX = xMin - 1; sideZ1 = cz - 1; sideZ2 = cz + 1; sideX1 = sideX2 = xMin - 1;
        }
        // Горшок с цветком слева
        painter.place(sideX1, Y_BASE + 2, sideZ1, Material.FLOWER_POT);
        // Бочка справа
        painter.place(sideX2, Y_BASE + 2, sideZ2, hr.nextBoolean() ? Material.BARREL : Material.OAK_PLANKS);
        // Иногда дополнительный ящик (30%)
        if (hr.nextDouble() < 0.3) {
            painter.place(sideX2, Y_BASE + 3, sideZ2, Material.OAK_SLAB);
        }
        // Коврик 1×1 у входа (для богатых)
        if (hr.nextDouble() < 0.3) {
            painter.place(outX, Y_BASE + 2, outZ, Material.PURPLE_CARPET);
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
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean perim = (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                    if (perim) painter.place(cx + dx, yBase + dy, cz + dz, fillMat);
                }
            }
        }
        painter.place(cx, yBase + 1, cz - 1, Material.PURPLE_STAINED_GLASS);
        painter.place(cx, yBase + 3, cz, Material.DEEPSLATE_BRICKS);
        painter.place(cx, yBase + 4, cz, Material.END_ROD);
    }

    private void buildDormer(int cx, int cz, int zMin, int zMax,
                              int yBase, Material fillMat, int facing) {
        int side = (facing == 0 ? zMin - 1 : zMax + 1);
        // Гарантируем что мы не вылезем из крыши — клампим
        int dormerZ = (facing == 0 ? zMin + 1 : zMax - 1);
        for (int dx = -1; dx <= 1; dx++) {
            painter.place(cx + dx, yBase, dormerZ, fillMat);
            painter.place(cx + dx, yBase + 1, dormerZ, fillMat);
        }
        painter.place(cx, yBase + 1, dormerZ, Material.PURPLE_STAINED_GLASS);
        painter.place(cx, yBase + 2, dormerZ, fillMat);
    }
}
