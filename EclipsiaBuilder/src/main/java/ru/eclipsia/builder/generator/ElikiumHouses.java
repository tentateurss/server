package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Жилые дома Эликия v4 — массивные 2-3 этажные средневековые дома с
 * ПЛОТНОЙ застройкой (вплотную друг к другу, промежутки — только узкие
 * арки 2 блока шириной).
 *
 * <p><b>Размеры</b>: 12×14 .. 16×18. Высота 2-3 этажа (12-18 блоков).
 *
 * <p><b>4 материальные семьи (тёмная палитра)</b>:
 * <ul>
 *   <li>35% — SPRUCE_PLANKS + DARK_OAK_LOG (тёмный фахверк)</li>
 *   <li>30% — DARK_OAK_PLANKS + STONE_BRICKS</li>
 *   <li>20% — DEEPSLATE_BRICKS + SPRUCE_LOG</li>
 *   <li>15% — POLISHED_BLACKSTONE + DARK_OAK (богатые)</li>
 * </ul>
 *
 * <p><b>4 типа крыш</b>: двускатная, четырёхскатная, с башенкой, с
 * мансардным окном — все разные.
 *
 * <p><b>Дворы-колодцы</b>: 4 дома вокруг 3×3 дворика с фонтаном или
 * деревом в кадке, лавочка, горшки с цветами, SOUL_LANTERN.
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
     * Кварталы — широкие зоны для плотной застройки. Шаг 14
     * (под крупные дома 12-16 шириной) обеспечивает здания вплотную
     * друг к другу. Перекрытия с POI/площадями отбраковываются.
     */
    private static final int[][] BLOCKS = {
            // Запад — 8 зон (плотная застройка вдоль стены)
            {-145, -120, -55, -80},
            {-145, -78, -55, -30},
            {-145, -28, -55,  25},
            {-145,  27, -55,  70},
            {-145,  72, -55, 120},
            {-115, -45, -55,  10},
            {-115,  12, -55,  55},
            {-115,  57, -55, 100},
            // Восток — 8 зон
            {  55, -120, 145, -75},
            {  55,  -73, 145, -20},
            {  55,  -18, 145,  30},
            {  55,   32, 145,  75},
            {  55,   77, 145, 120},
            {  65,  -55, 140,   0},
            {  65,    2, 140,  50},
            {  65,   52, 140, 100},
            // Север (выше собора) — 6 зон
            { -60, -145,  -5, -90},
            {  -3, -145,  55, -90},
            {  57, -145, 120, -90},
            { -60,  -88,  -5, -55},
            {   0,  -88,  55, -55},
            {  57,  -88, 120, -55},
            // Юг (ниже собора и площадей) — 6 зон
            { -55,   45, -10, 120},
            {  -8,   60,  50, 120},
            {  52,   45, 120, 120},
            { -55,   30,   5,  43},
            {  50,   30, 120,  43},
            {   5,   85,  50, 120},
            // Центральная полоса — 8 зон (заполняем ВСЁ между POI)
            { -50,  -53,  -5, -15},
            {   0,  -53,  10, -15},
            {  12,  -53,  55, -15},
            { -50,  -13, -12,  25},
            {  12,  -13,  55,  25},
            { -50,   55, -12,  85},
            {  12,   55,  55,  85},
            { -12,   60,  10,  85},
    };

    /**
     * Дворы-колодцы: 4 дома вокруг 3×3 центра с фонтаном.
     * Больше дворов для заполнения пространства.
     */
    private static final int[][] COURTYARDS = {
            {-90, -25}, {-90, 95}, {55, -90}, {115, 0},
            {-70, 50}, {70, 60}, {-80, -70}, {100, -70},
            {-100, 50}, {100, 50}, {-70, -100}, {70, -100},
            {-30, -70}, {30, 90}, {-110, -50}, {110, -50},
    };

    public long build() {
        long count = 0;
        int totalPlaced = 0;
        int courtyardsPlaced = 0;

        for (int[] cy : COURTYARDS) {
            if (buildCourtyard(cy[0], cy[1])) {
                courtyardsPlaced++;
                totalPlaced += 4;
            }
        }

        for (int[] block : BLOCKS) {
            int placed = fillBlock(block[0], block[1], block[2], block[3]);
            totalPlaced += placed;
        }
        plugin.getLogger().info("ElikiumHouses: " + totalPlaced + " домов ("
                + courtyardsPlaced + " дворов-колодцев + " + BLOCKS.length + " кварталов).");
        return count;
    }

    /**
     * Плотно заполнить квартал домами. Шаг 10-11 (вплотную).
     * Каждый дом получает рандомный сдвиг ±1 блок чтобы убрать
     * визуальную сетку, но сохранить плотность.
     */
    private int fillBlock(int xMin, int zMin, int xMax, int zMax) {
        int placed = 0;
        int step = 12;
        boolean offsetRow = false;
        for (int z = zMin + 7; z + 7 <= zMax; z += step) {
            int rowOffset = offsetRow ? step / 2 : 0;
            offsetRow = !offsetRow;
            for (int x = xMin + 7 + rowOffset; x + 7 <= xMax; x += step) {
                int w = 8 + rng.nextInt(5);  // 8..12
                int d = 10 + rng.nextInt(5); // 10..14
                int cx = x + rng.nextInt(3) - 1;
                int cz = z + rng.nextInt(3) - 1;
                int hxMin = cx - w / 2, hxMax = cx + w / 2;
                int hzMin = cz - d / 2, hzMax = cz + d / 2;
                if (!isFreeFootprint(hxMin, hzMin, hxMax, hzMax)) {
                    continue;
                }
                int doorFacing = pickDoorFacing(cx, cz, xMin, zMin, xMax, zMax);
                buildHouse(cx, cz, w, d, rng.nextInt(), doorFacing);
                ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
                placed++;
            }
        }
        return placed;
    }

    /**
     * Двор-колодец: 4 дома по сторонам света вокруг центрального
     * 3×3 фонтана/колодца. Дома стоят вплотную (расстояние 6 от центра).
     */
    private boolean buildCourtyard(int cx, int cz) {
        int extXMin = cx - 14, extXMax = cx + 14;
        int extZMin = cz - 14, extZMax = cz + 14;
        if (!isFreeFootprint(extXMin, extZMin, extXMax, extZMax)) return false;
        if (!WorldGenerator.isInsideCityPolygon(extXMin, extZMin)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMin, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMin)) return false;
        if (ElikiumCity.insideCathedralZone(extXMin, extZMin)
                || ElikiumCity.insideCathedralZone(extXMax, extZMax)) return false;

        int[][] houseSpots = {
                {cx,       cz - 9},
                {cx + 9,   cz},
                {cx,       cz + 9},
                {cx - 9,   cz},
        };
        int[] facings = {2, 3, 0, 1};
        int built = 0;
        for (int i = 0; i < 4; i++) {
            int hx = houseSpots[i][0];
            int hz = houseSpots[i][1];
            int hw = 10, hd = 12;
            int hxMin = hx - hw / 2, hxMax = hx + hw / 2;
            int hzMin = hz - hd / 2, hzMax = hz + hd / 2;
            buildHouse(hx, hz, hw, hd, rng.nextInt(), facings[i]);
            ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
            built++;
        }

        // Центральный 3×3 двор: каменное мощение + колодец/фонтан
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE, cz + dz, Material.COBBLESTONE);
            }
        }
        // Колодец 1×1 со стенками STONE_BRICK_WALL
        for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            painter.place(cx + off[0], Y_BASE + 1, cz + off[1], Material.STONE_BRICK_WALL);
        }
        painter.place(cx, Y_BASE + 1, cz, Material.WATER);
        // Балка над колодцем + фонарь
        painter.place(cx - 1, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx + 1, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx, Y_BASE + 2, cz, Material.CHAIN);
        painter.place(cx, Y_BASE + 4, cz, Material.LANTERN);

        // Цветочные горшки и SOUL_LANTERN в углах двора
        painter.place(cx - 1, Y_BASE + 1, cz - 1, Material.FLOWER_POT);
        painter.place(cx + 1, Y_BASE + 1, cz - 1, Material.FLOWER_POT);
        painter.place(cx - 1, Y_BASE + 1, cz + 1, Material.FLOWER_POT);
        painter.place(cx + 1, Y_BASE + 1, cz + 1, Material.SOUL_LANTERN);

        // Лавочка в углу
        BlockData bench = Material.OAK_STAIRS.createBlockData("[facing=east,half=bottom]");
        painter.placeData(cx + 1, Y_BASE + 1, cz, bench);

        ctx.pois.add(new ElikiumCity.POI("§7Двор", "§8...", cx, Y_BASE + 6, cz));
        return built >= 3;
    }

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
            if (candidate.overlaps(f, 1)) return false;
        }
        int streetCount = 0;
        int totalCells = Math.max(1, (x2 - x1 + 1) * (z2 - z1 + 1));
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (ctx.streetCells.contains(ElikiumCity.packCoord(x, z))) streetCount++;
            }
        }
        if ((double) streetCount / totalCells > 0.25) return false;
        return true;
    }

    /**
     * Построить ОДИН качественный средневековый дом 2-3 этажа с
     * фундаментом, ставнями, скатной крышей, козырьком и лестницей.
     *
     * @param facing 0=N, 1=E, 2=S, 3=W — куда смотрит дверь
     */
    private void buildHouse(int cx, int cz, int w, int d, int seed, int facing) {
        Random hr = new Random(seed);
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;

        // Выбор материальной семьи
        int family = pickFamily(hr);
        Material wallA, wallB, pillar, foundation;
        switch (family) {
            case 0: // тёмный фахверк
                wallA = Material.SPRUCE_PLANKS;
                wallB = Material.STONE_BRICKS;
                pillar = Material.DARK_OAK_LOG;
                foundation = Material.COBBLED_DEEPSLATE;
                break;
            case 1:
                wallA = Material.DARK_OAK_PLANKS;
                wallB = Material.STONE_BRICKS;
                pillar = Material.DARK_OAK_LOG;
                foundation = Material.DEEPSLATE_BRICKS;
                break;
            case 2:
                wallA = Material.DEEPSLATE_BRICKS;
                wallB = Material.COBBLED_DEEPSLATE;
                pillar = Material.SPRUCE_LOG;
                foundation = Material.COBBLED_DEEPSLATE;
                break;
            default: // богатый дом
                wallA = Material.POLISHED_BLACKSTONE;
                wallB = Material.POLISHED_BLACKSTONE_BRICKS;
                pillar = Material.DARK_OAK_LOG;
                foundation = Material.POLISHED_BLACKSTONE_BRICKS;
        }
        Material roofMat = Material.DARK_OAK_STAIRS;
        Material roofFill = Material.DARK_OAK_PLANKS;

        // Количество этажей: 2-3 (больше у крупных домов)
        int floors = (w >= 14 || d >= 16 || family == 3) ? 3 : 2;
        if (hr.nextDouble() < 0.4) floors = 3;
        int floorH = 5;

        // 1. ЦОКОЛЬ (Y_BASE)
        for (int x = xMin - 1; x <= xMax + 1; x++) {
            for (int z = zMin - 1; z <= zMax + 1; z++) {
                if (x < xMin || x > xMax || z < zMin || z > zMax) {
                    painter.place(x, Y_BASE, z, foundation);
                }
            }
        }
        // 2. ОСНОВАНИЕ (Y_BASE+1) — каменный фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, foundation);
            }
        }

        // 3. СТЕНЫ ВСЕХ ЭТАЖЕЙ
        for (int floor = 0; floor < floors; floor++) {
            int yBase = Y_BASE + 2 + floor * floorH;
            Material floorWall = (floor == 0) ? wallA : wallB;
            if (floor >= 2) floorWall = wallA; // чередование

            // Перекрытие между этажами (пол)
            if (floor > 0) {
                for (int x = xMin; x <= xMax; x++) {
                    for (int z = zMin; z <= zMax; z++) {
                        painter.place(x, yBase - 1, z, Material.SPRUCE_PLANKS);
                    }
                }
            }

            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                    if (!perim) continue;
                    boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                    Material mat = corner ? pillar : floorWall;
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, z, mat);
                    }
                }
            }
            // Промежуточные фахверк-столбы (только семьи 0 и 1)
            if (family == 0 || family == 1) {
                for (int x = xMin + 4; x <= xMax - 4; x += 4) {
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, zMin, pillar);
                        painter.place(x, yBase + dy, zMax, pillar);
                    }
                }
                for (int z = zMin + 4; z <= zMax - 4; z += 4) {
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(xMin, yBase + dy, z, pillar);
                        painter.place(xMax, yBase + dy, z, pillar);
                    }
                }
            }

            // ОКНА на каждом этаже
            Material winMat = (family == 3)
                    ? Material.PURPLE_STAINED_GLASS_PANE
                    : (hr.nextDouble() < 0.7 ? Material.YELLOW_STAINED_GLASS_PANE : Material.GLASS_PANE);
            placeWindowsOnWall(xMin, xMax, zMin, yBase + 1, winMat, "south", true, hr);
            placeWindowsOnWall(xMin, xMax, zMax, yBase + 1, winMat, "north", true, hr);
            placeWindowsOnSide(zMin, zMax, xMin, yBase + 1, winMat, "east", false, hr);
            placeWindowsOnSide(zMin, zMax, xMax, yBase + 1, winMat, "west", false, hr);
        }

        // 4. ДВЕРЬ + СТУПЕНЬКИ + КОЗЫРЁК (только 1-й этаж)
        placeDoorWithPorch(cx, cz, xMin, xMax, zMin, zMax, facing, family, hr, roofMat);

        // 5. КАРНИЗ — STAIRS перевёрнутые свесом наружу
        int wallTopY = Y_BASE + 1 + floors * floorH;
        int eaveY = wallTopY + 1;
        BlockData eaveN = roofMat.createBlockData("[facing=south,half=top,shape=straight]");
        BlockData eaveS = roofMat.createBlockData("[facing=north,half=top,shape=straight]");
        BlockData eaveW = roofMat.createBlockData("[facing=east,half=top,shape=straight]");
        BlockData eaveE = roofMat.createBlockData("[facing=west,half=top,shape=straight]");
        for (int x = xMin - 1; x <= xMax + 1; x++) {
            painter.placeData(x, eaveY, zMin - 1, eaveN);
            painter.placeData(x, eaveY, zMax + 1, eaveS);
        }
        for (int z = zMin; z <= zMax; z++) {
            painter.placeData(xMin - 1, eaveY, z, eaveW);
            painter.placeData(xMax + 1, eaveY, z, eaveE);
        }

        // 6. КРЫША — выбор из 4 типов
        int roofType = hr.nextInt(4);
        boolean alongZ = d >= w;
        switch (roofType) {
            case 0: // Двускатная
                buildStairsRoof(xMin, zMin, xMax, zMax, eaveY, alongZ, roofMat, roofFill);
                break;
            case 1: // Четырёхскатная (hip)
                buildHipRoof(xMin, zMin, xMax, zMax, eaveY, roofMat, roofFill);
                break;
            case 2: // С башенкой 3×3
                buildStairsRoof(xMin, zMin, xMax, zMax, eaveY, alongZ, roofMat, roofFill);
                buildRoofTower(cx, cz, eaveY + 3, roofFill, pillar);
                break;
            default: // С мансардным окном
                buildStairsRoof(xMin, zMin, xMax, zMax, eaveY, alongZ, roofMat, roofFill);
                buildDormerWindow(cx, zMax, eaveY + 1, roofMat, roofFill, hr);
                break;
        }

        // 7. ДЫМОХОД (60% шанс)
        if (hr.nextDouble() < 0.6) {
            int chOffsetX = (hr.nextBoolean() ? -1 : +1) * (w / 2 - 2);
            int chX = cx + chOffsetX;
            int chZ = cz - 1;
            int chTop = eaveY + 5;
            for (int y = wallTopY; y <= chTop; y++) {
                painter.place(chX, y, chZ, Material.COBBLESTONE);
            }
            painter.place(chX, chTop + 1, chZ, Material.CAMPFIRE);
        }

        // 8. ВНУТРЕННЕЕ освещение (на каждом этаже)
        for (int floor = 0; floor < floors; floor++) {
            painter.place(cx, Y_BASE + 2 + floor * floorH + floorH - 1, cz, Material.LANTERN);
        }

        // 9. ЛОЗЫ на 30% домов
        if (hr.nextDouble() < 0.3) {
            int vineX = (hr.nextBoolean() ? xMin - 1 : xMax + 1);
            int vineZ = cz + (hr.nextInt(d) - d / 2);
            for (int dy = 0; dy < 6; dy++) {
                painter.place(vineX, Y_BASE + 3 + dy, vineZ, Material.VINE);
            }
        }
    }

    /** Четырёхскатная крыша (hip roof) со STAIRS на скатах. */
    private void buildHipRoof(int xMin, int zMin, int xMax, int zMax,
                               int eaveY, Material roofMat, Material fillMat) {
        int spanX = xMax - xMin;
        int spanZ = zMax - zMin;
        int maxRise = Math.min(spanX, spanZ) / 2 + 1;
        for (int rise = 0; rise <= maxRise; rise++) {
            int y = eaveY + rise;
            int xl = xMin + rise, xr = xMax - rise;
            int zn = zMin + rise, zs = zMax - rise;
            if (xl > xr || zn > zs) break;
            // Заполнить внутренность сплошным заполнителем
            for (int x = xl; x <= xr; x++) {
                for (int z = zn; z <= zs; z++) {
                    painter.place(x, y, z, fillMat);
                }
            }
            // STAIRS на краях скатов
            BlockData stairN = roofMat.createBlockData("[facing=south,half=bottom]");
            BlockData stairS = roofMat.createBlockData("[facing=north,half=bottom]");
            BlockData stairW = roofMat.createBlockData("[facing=east,half=bottom]");
            BlockData stairE = roofMat.createBlockData("[facing=west,half=bottom]");
            for (int x = xl; x <= xr; x++) {
                painter.placeData(x, y, zn, stairN);
                painter.placeData(x, y, zs, stairS);
            }
            for (int z = zn + 1; z < zs; z++) {
                painter.placeData(xl, y, z, stairW);
                painter.placeData(xr, y, z, stairE);
            }
        }
    }

    /** Башенка 3×3 на крыше. */
    private void buildRoofTower(int cx, int cz, int yBase, Material fill, Material pillar) {
        for (int dy = 0; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean perim = (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                    if (!perim && dy < 4) continue;
                    painter.place(cx + dx, yBase + dy, cz + dz,
                            (Math.abs(dx) == 1 && Math.abs(dz) == 1) ? pillar : fill);
                }
            }
        }
        // Шпиль
        painter.place(cx, yBase + 5, cz, fill);
        painter.place(cx, yBase + 6, cz, Material.END_ROD);
        // Окошко
        painter.place(cx, yBase + 2, cz - 1, Material.GLASS_PANE);
        painter.place(cx, yBase + 2, cz + 1, Material.GLASS_PANE);
    }

    /** Мансардное окно (dormer window) на крыше. */
    private void buildDormerWindow(int cx, int zMax, int yBase, Material roofMat,
                                    Material fill, Random hr) {
        // Маленький выступ 3×2 на южной стороне крыши
        for (int dx = -1; dx <= 1; dx++) {
            painter.place(cx + dx, yBase, zMax + 1, fill);
            painter.place(cx + dx, yBase + 1, zMax + 1, fill);
            painter.place(cx + dx, yBase + 2, zMax + 1, fill);
        }
        // Окно в центре выступа
        painter.place(cx, yBase + 1, zMax + 1, Material.GLASS_PANE);
        // Маленькая крышка
        BlockData dormerRoof = roofMat.createBlockData("[facing=north,half=bottom]");
        for (int dx = -1; dx <= 1; dx++) {
            painter.placeData(cx + dx, yBase + 3, zMax + 1, dormerRoof);
        }
    }

    /**
     * Дверь + 2 ступени OAK_STAIRS вниз к улице + козырёк STAIRS на 2 столбах
     * + цепь+LANTERN, висящий с козырька.
     */
    private void placeDoorWithPorch(int cx, int cz, int xMin, int xMax, int zMin, int zMax,
                                    int facing, int family, Random hr, Material roofMat) {
        int dx = cx, dz = cz;
        String facingStr;
        int outDx = 0, outDz = 0;
        switch (facing) {
            case 0: dz = zMin; facingStr = "south"; outDz = -1; break;
            case 1: dx = xMax; facingStr = "west";  outDx = +1; break;
            case 2: dz = zMax; facingStr = "north"; outDz = +1; break;
            default: dx = xMin; facingStr = "east"; outDx = -1;
        }
        // 1. Проём + дверь
        painter.place(dx, Y_BASE + 2, dz, Material.AIR);
        painter.place(dx, Y_BASE + 3, dz, Material.AIR);
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=" + facingStr + ",hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=" + facingStr + ",hinge=left]");
        painter.placeData(dx, Y_BASE + 2, dz, door);
        painter.placeData(dx, Y_BASE + 3, dz, doorTop);

        // 2. Ступенька OAK_STAIRS перед дверью
        BlockData stairOut = Material.OAK_STAIRS.createBlockData(
                "[facing=" + invertFacing(facingStr) + ",half=bottom]");
        painter.placeData(dx + outDx, Y_BASE + 1, dz + outDz, stairOut);

        // 3. КОЗЫРЁК — 2 OAK_FENCE-столба + STAIRS навес над дверью
        int p1Dx = outDx * 2, p1Dz = outDz * 2;
        int leftDx, leftDz;
        if (outDx != 0) { leftDx = 0; leftDz = -1; } else { leftDx = -1; leftDz = 0; }
        int rightDx = -leftDx, rightDz = -leftDz;
        for (int dy = 1; dy <= 4; dy++) {
            painter.place(dx + p1Dx + leftDx, Y_BASE + dy, dz + p1Dz + leftDz, Material.OAK_FENCE);
            painter.place(dx + p1Dx + rightDx, Y_BASE + dy, dz + p1Dz + rightDz, Material.OAK_FENCE);
        }
        painter.place(dx + p1Dx + leftDx, Y_BASE + 4, dz + p1Dz + leftDz, Material.OAK_LOG);
        painter.place(dx + p1Dx + rightDx, Y_BASE + 4, dz + p1Dz + rightDz, Material.OAK_LOG);
        BlockData awningStair = Material.OAK_STAIRS.createBlockData(
                "[facing=" + facingStr + ",half=top]");
        painter.placeData(dx + outDx, Y_BASE + 5, dz + outDz, awningStair);
        painter.placeData(dx + outDx + leftDx, Y_BASE + 5, dz + outDz + leftDz, awningStair);
        painter.placeData(dx + outDx + rightDx, Y_BASE + 5, dz + outDz + rightDz, awningStair);
        painter.place(dx + p1Dx, Y_BASE + 5, dz + p1Dz, Material.OAK_PLANKS);

        // 4. ЦЕПЬ + LANTERN
        painter.place(dx + outDx, Y_BASE + 4, dz + outDz, Material.CHAIN);
        painter.place(dx + outDx, Y_BASE + 3, dz + outDz, Material.LANTERN);

        // 5. ДЕКОР: горшок слева, бочка справа, SOUL_TORCH на стене
        painter.place(dx + outDx + leftDx, Y_BASE + 2, dz + outDz + leftDz, Material.FLOWER_POT);
        painter.place(dx + outDx + rightDx, Y_BASE + 2, dz + outDz + rightDz,
                hr.nextBoolean() ? Material.BARREL : Material.OAK_PLANKS);
        // Коврик у важных зданий (10%)
        if (hr.nextDouble() < 0.4) {
            Material carpet = (family == 3) ? Material.PURPLE_CARPET : Material.RED_CARPET;
            painter.place(dx + outDx, Y_BASE + 2, dz + outDz, carpet);
        }
        // Настенный SOUL_TORCH у двери
        painter.place(dx + leftDx, Y_BASE + 4, dz + leftDz, Material.SOUL_TORCH);
    }

    private String invertFacing(String f) {
        switch (f) {
            case "north": return "south";
            case "south": return "north";
            case "east":  return "west";
            default:      return "east";
        }
    }

    private void placeWindowsOnWall(int xMin, int xMax, int z, int yBase, Material winMat,
                                     String shutterFacing, boolean longSide, Random hr) {
        int interval = longSide ? 3 : 4;
        for (int x = xMin + 2; x <= xMax - 2; x += interval) {
            painter.place(x, yBase, z, winMat);
            painter.place(x, yBase + 1, z, winMat);
            BlockData shutterL = Material.OAK_TRAPDOOR.createBlockData(
                    "[facing=" + shutterFacing + ",half=top,open=true]");
            BlockData shutterR = Material.OAK_TRAPDOOR.createBlockData(
                    "[facing=" + shutterFacing + ",half=top,open=true]");
            painter.placeData(x - 1, yBase + 1, z, shutterL);
            painter.placeData(x + 1, yBase + 1, z, shutterR);
        }
    }

    private void placeWindowsOnSide(int zMin, int zMax, int x, int yBase, Material winMat,
                                     String shutterFacing, boolean longSide, Random hr) {
        int interval = longSide ? 3 : 4;
        for (int z = zMin + 2; z <= zMax - 2; z += interval) {
            painter.place(x, yBase, z, winMat);
            painter.place(x, yBase + 1, z, winMat);
            BlockData shutter = Material.OAK_TRAPDOOR.createBlockData(
                    "[facing=" + shutterFacing + ",half=top,open=true]");
            painter.placeData(x, yBase + 1, z - 1, shutter);
            painter.placeData(x, yBase + 1, z + 1, shutter);
        }
    }

    /**
     * Двускатная крыша из STAIRS (настоящие скаты) с заполненным
     * внутренним объёмом — никаких дырок.
     */
    private void buildStairsRoof(int xMin, int zMin, int xMax, int zMax,
                                  int eaveY, boolean alongZ, Material roofMat, Material fillMat) {
        if (alongZ) {
            int span = xMax - xMin;
            int half = (span + 1) / 2;
            for (int rise = 0; rise <= half; rise++) {
                int y = eaveY + rise;
                int xL = xMin + rise;
                int xR = xMax - rise;
                if (xL > xR) break;
                BlockData stairW = roofMat.createBlockData("[facing=east,half=bottom]");
                BlockData stairE = roofMat.createBlockData("[facing=west,half=bottom]");
                for (int z = zMin - 1; z <= zMax + 1; z++) {
                    if (xL == xR) {
                        painter.place(xL, y, z, fillMat);
                    } else {
                        painter.placeData(xL, y, z, stairW);
                        painter.placeData(xR, y, z, stairE);
                        // Заполнить внутренность между скатами
                        for (int x = xL + 1; x < xR; x++) {
                            painter.place(x, y, z, fillMat);
                        }
                    }
                }
                // Фронтоны (торцевые стены крыши)
                for (int x = Math.max(xL, xMin); x <= Math.min(xR, xMax); x++) {
                    painter.place(x, y, zMin, Material.DEEPSLATE_BRICKS);
                    painter.place(x, y, zMax, Material.DEEPSLATE_BRICKS);
                }
            }
        } else {
            int span = zMax - zMin;
            int half = (span + 1) / 2;
            for (int rise = 0; rise <= half; rise++) {
                int y = eaveY + rise;
                int zN = zMin + rise;
                int zS = zMax - rise;
                if (zN > zS) break;
                BlockData stairN = roofMat.createBlockData("[facing=south,half=bottom]");
                BlockData stairS = roofMat.createBlockData("[facing=north,half=bottom]");
                for (int x = xMin - 1; x <= xMax + 1; x++) {
                    if (zN == zS) {
                        painter.place(x, y, zN, fillMat);
                    } else {
                        painter.placeData(x, y, zN, stairN);
                        painter.placeData(x, y, zS, stairS);
                        // Заполнить внутренность между скатами
                        for (int z = zN + 1; z < zS; z++) {
                            painter.place(x, y, z, fillMat);
                        }
                    }
                }
                // Фронтоны (торцевые стены крыши)
                for (int z = Math.max(zN, zMin); z <= Math.min(zS, zMax); z++) {
                    painter.place(xMin, y, z, Material.DEEPSLATE_BRICKS);
                    painter.place(xMax, y, z, Material.DEEPSLATE_BRICKS);
                }
            }
        }
    }

    private int pickFamily(Random hr) {
        double r = hr.nextDouble();
        if (r < 0.35) return 0;
        if (r < 0.65) return 1;
        if (r < 0.85) return 2;
        return 3;
    }
}
