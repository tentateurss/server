package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Жилые дома Эликия v3 — массивные «средневековые» дома по эталонному стилю
 * с НАСТОЯЩИМИ скатными крышами из STAIRS, каменным фундаментом, козырьком
 * над дверью, ставнями TRAPDOOR на окнах и ступеньками от двери к улице.
 *
 * <p><b>Размеры</b>: 11×9 .. 14×10. Один этаж + чердак с мансардными окнами.
 *
 * <p><b>Структура снизу вверх</b>:
 * <ol>
 *   <li>Y_BASE: COBBLED_DEEPSLATE цоколь, выдвинут на 1 блок наружу для
 *       визуального основания;</li>
 *   <li>Y_BASE+1: каменное основание STONE_BRICKS/COBBLED_DEEPSLATE по
 *       периметру дома;</li>
 *   <li>Y_BASE+2..+5: первый этаж — стены wallA, угловые столбы pillar,
 *       окна GLASS_PANE/YELLOW_STAINED_GLASS, дверь по facing;</li>
 *   <li>Y_BASE+6: карниз — STAIRS перевёрнутые наружу (eaves overhang);</li>
 *   <li>Y_BASE+7+: скатная крыша из STAIRS до вершины + DEEPSLATE_BRICKS
 *       гребень.</li>
 * </ol>
 *
 * <p><b>Декор у входа</b>: каждый дом получает 2-блочную лестницу OAK_STAIRS,
 * 2-блочный козырёк STAIRS на 2 столбах OAK_FENCE, цепь+LANTERN над дверью,
 * горшок с цветком слева, бочку справа, ставни OAK_TRAPDOOR у окон.
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
     */
    private static final int[][] BLOCKS = {
            // Запад
            {-145, -110, -50, -55},   // 0: W-N
            {-145,  -45, -50, -15},   // 1: W-C-N
            {-145,   30, -50,  62},   // 2: W-C-S
            {-145,   80, -50, 120},   // 3: W-S
            // Восток
            {  90, -115, 145, -65},   // 4: E-N
            {  90,  -25, 145,  25},   // 5: E-C
            {  90,   70, 145, 120},   // 6: E-S
            // Север (выше собора)
            { -55, -145,  10, -90},   // 7: N-W
            {  15, -145,  80, -90},   // 8: N-C
            // Юг (ниже собора и площадей)
            { -50,   55,   8, 120},   // 9: S-W
            {  15,   55,  80, 120},   // 10: S-C
    };

    /**
     * Дворы-колодцы: 4 дома вокруг 3×3 центра с фонтаном.
     */
    private static final int[][] COURTYARDS = {
            {-90, -25}, {-90, 95}, {55, -90}, {115, 0},
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
     * Плотно заполнить квартал домами. Шаг 13-14 (под массивные дома 11×9).
     * Каждый дом получает рандомный сдвиг ±1 блок чтобы убрать визуальную сетку.
     */
    private int fillBlock(int xMin, int zMin, int xMax, int zMax) {
        int placed = 0;
        int step = 14;
        boolean offsetRow = false;
        for (int z = zMin + 6; z + 6 <= zMax; z += step) {
            int rowOffset = offsetRow ? step / 2 : 0;
            offsetRow = !offsetRow;
            for (int x = xMin + 6 + rowOffset; x + 6 <= xMax; x += step) {
                // Случайные размеры
                int w = 11 + rng.nextInt(3); // 11..13
                int d = 8 + rng.nextInt(3);  // 8..10
                // Случайный сдвиг ±1
                int cx = x + rng.nextInt(3) - 1;
                int cz = z + rng.nextInt(3) - 1;
                int hxMin = cx - w / 2, hxMax = cx + w / 2;
                int hzMin = cz - d / 2, hzMax = cz + d / 2;
                if (!isFreeFootprint(hxMin - 2, hzMin - 2, hxMax + 2, hzMax + 2)) {
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
     * 3×3 фонтана/колодца.
     */
    private boolean buildCourtyard(int cx, int cz) {
        int extXMin = cx - 10, extXMax = cx + 10;
        int extZMin = cz - 10, extZMax = cz + 10;
        if (!isFreeFootprint(extXMin, extZMin, extXMax, extZMax)) return false;
        if (!WorldGenerator.isInsideCityPolygon(extXMin, extZMin)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMin, extZMax)
                || !WorldGenerator.isInsideCityPolygon(extXMax, extZMin)) return false;
        if (ElikiumCity.insideCathedralZone(extXMin, extZMin)
                || ElikiumCity.insideCathedralZone(extXMax, extZMax)) return false;

        int[][] houseSpots = {
                {cx,       cz - 7},
                {cx + 7,   cz},
                {cx,       cz + 7},
                {cx - 7,   cz},
        };
        int[] facings = {2, 3, 0, 1};
        int built = 0;
        for (int i = 0; i < 4; i++) {
            int hx = houseSpots[i][0];
            int hz = houseSpots[i][1];
            int hw = 9, hd = 7;
            int hxMin = hx - hw / 2, hxMax = hx + hw / 2;
            int hzMin = hz - hd / 2, hzMax = hz + hd / 2;
            buildHouse(hx, hz, hw, hd, rng.nextInt(), facings[i]);
            ctx.occupied.add(new ElikiumCity.Footprint(hxMin, hzMin, hxMax, hzMax));
            built++;
        }

        // Центральный 3×3 двор: каменное мощение + 1×1 колодец + дерево/фонарь
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(cx + dx, Y_BASE, cz + dz, Material.COBBLESTONE);
            }
        }
        // Колодец 1×1 со стенками STONE_BRICK_WALL и крышей-балкой
        for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            painter.place(cx + off[0], Y_BASE + 1, cz + off[1], Material.STONE_BRICK_WALL);
        }
        painter.place(cx, Y_BASE + 1, cz, Material.WATER);
        // Балка над колодцем
        painter.place(cx - 1, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx + 1, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx, Y_BASE + 3, cz, Material.OAK_LOG);
        painter.place(cx, Y_BASE + 2, cz, Material.CHAIN);
        painter.place(cx, Y_BASE + 4, cz, Material.LANTERN);

        // Цветочные горшки в углах двора
        BlockData pot = Material.FLOWER_POT.createBlockData();
        painter.placeData(cx - 1, Y_BASE + 1, cz - 1, pot);
        painter.placeData(cx + 1, Y_BASE + 1, cz - 1, pot);
        painter.placeData(cx - 1, Y_BASE + 1, cz + 1, pot);
        painter.placeData(cx + 1, Y_BASE + 1, cz + 1, pot);

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
        // Не накрыть улицу — проверяем КАЖДУЮ клетку (раньше каждую вторую).
        int streetCount = 0;
        int total = 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                total++;
                if (ctx.streetCells.contains(ElikiumCity.packCoord(x, z))) streetCount++;
            }
        }
        if (total > 0 && streetCount * 10 > total) return false; // <10% улицы
        return true;
    }

    /**
     * Построить ОДИН качественный средневековый дом с фундаментом, ставнями,
     * STAIRS-крышей, козырьком и лестницей у двери.
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
            case 0: // фахверк
                wallA = Material.OAK_PLANKS;
                wallB = Material.STONE_BRICKS;
                pillar = Material.DARK_OAK_LOG;
                foundation = Material.COBBLED_DEEPSLATE;
                break;
            case 1:
                wallA = Material.DARK_OAK_PLANKS;
                wallB = Material.STONE_BRICKS;
                pillar = Material.DARK_OAK_LOG;
                foundation = Material.STONE_BRICKS;
                break;
            case 2:
                wallA = Material.STONE_BRICKS;
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
        Material roofMat = (family == 0) ? Material.OAK_STAIRS : Material.DARK_OAK_STAIRS;
        Material roofFill = (family == 0) ? Material.OAK_PLANKS : Material.DARK_OAK_PLANKS;

        // 1. ЦОКОЛЬ (Y_BASE) — выдвинут на 1 блок наружу для визуальной базы
        for (int x = xMin - 1; x <= xMax + 1; x++) {
            for (int z = zMin - 1; z <= zMax + 1; z++) {
                if (x < xMin || x > xMax || z < zMin || z > zMax) {
                    // Только периметр (чуть выходит за пределы)
                    painter.place(x, Y_BASE, z, foundation);
                }
            }
        }
        // 2. ОСНОВАНИЕ (Y_BASE+1) — каменный фундамент по периметру и заливка пола
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, foundation);
            }
        }

        // 3. СТЕНЫ ПЕРВОГО ЭТАЖА (Y_BASE+2..+5)
        int floorH = 4;
        int wallTopY = Y_BASE + 1 + floorH;
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                if (!perim) continue;
                boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                Material mat = corner ? pillar : wallA;
                for (int dy = 1; dy <= floorH; dy++) {
                    painter.place(x, Y_BASE + 1 + dy, z, mat);
                }
            }
        }
        // Промежуточные fахверк-столбы (только семьи 0 и 1)
        if (family == 0 || family == 1) {
            for (int x = xMin + 4; x <= xMax - 4; x += 4) {
                for (int dy = 1; dy <= floorH; dy++) {
                    painter.place(x, Y_BASE + 1 + dy, zMin, pillar);
                    painter.place(x, Y_BASE + 1 + dy, zMax, pillar);
                }
            }
            for (int z = zMin + 4; z <= zMax - 4; z += 4) {
                for (int dy = 1; dy <= floorH; dy++) {
                    painter.place(xMin, Y_BASE + 1 + dy, z, pillar);
                    painter.place(xMax, Y_BASE + 1 + dy, z, pillar);
                }
            }
        }

        // 4. ОКНА — по 1 на каждой стене, по 2-3 на длинной
        Material winMat = (family == 3)
                ? Material.PURPLE_STAINED_GLASS_PANE
                : (hr.nextDouble() < 0.7 ? Material.YELLOW_STAINED_GLASS_PANE : Material.GLASS_PANE);
        placeWindowsOnWall(xMin, xMax, zMin, Y_BASE + 3, winMat, "south", true, hr);
        placeWindowsOnWall(xMin, xMax, zMax, Y_BASE + 3, winMat, "north", true, hr);
        placeWindowsOnSide(zMin, zMax, xMin, Y_BASE + 3, winMat, "east", false, hr);
        placeWindowsOnSide(zMin, zMax, xMax, Y_BASE + 3, winMat, "west", false, hr);

        // 5. ДВЕРЬ + СТУПЕНЬКИ + КОЗЫРЁК
        placeDoorWithPorch(cx, cz, xMin, xMax, zMin, zMax, facing, family, hr, roofMat);

        // 6. КАРНИЗ (Y_BASE+6) — STAIRS перевёрнутые свесом наружу
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

        // 7. СКАТНАЯ КРЫША из STAIRS (двускатная вдоль длинной оси)
        boolean alongZ = d >= w;
        buildStairsRoof(xMin, zMin, xMax, zMax, eaveY, alongZ, roofMat, roofFill);

        // 8. ДЫМОХОД (60% шанс)
        if (hr.nextDouble() < 0.6) {
            int chOffsetX = (hr.nextBoolean() ? -1 : +1) * (w / 2 - 2);
            int chX = cx + chOffsetX;
            int chZ = cz - 1;
            int chTop = wallTopY + 7;
            for (int y = wallTopY; y <= chTop; y++) {
                painter.place(chX, y, chZ, Material.COBBLESTONE);
            }
            painter.place(chX, chTop + 1, chZ, Material.CAMPFIRE);
        }

        // 9. ВНУТРЕННЕЕ освещение
        painter.place(cx, Y_BASE + 1 + floorH, cz, Material.LANTERN);

        // 10. ЛОЗЫ на 30% домов (на одной из боковых стен)
        if (hr.nextDouble() < 0.3) {
            int vineX = (hr.nextBoolean() ? xMin - 1 : xMax + 1);
            int vineZ = cz + (hr.nextInt(d) - d / 2);
            for (int dy = 0; dy < 4; dy++) {
                painter.place(vineX, Y_BASE + 3 + dy, vineZ, Material.VINE);
            }
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
        int outDx = 0, outDz = 0;  // направление наружу
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

        // 2. Ступенька OAK_STAIRS перед дверью (1 блок снаружи)
        BlockData stairOut = Material.OAK_STAIRS.createBlockData(
                "[facing=" + invertFacing(facingStr) + ",half=bottom]");
        painter.placeData(dx + outDx, Y_BASE + 1, dz + outDz, stairOut);

        // 3. КОЗЫРЁК — 2 OAK_FENCE-столба + STAIRS навес над дверью
        // Столбы:
        int p1Dx = outDx * 2, p1Dz = outDz * 2;
        int leftDx, leftDz;
        if (outDx != 0) { leftDx = 0; leftDz = -1; } else { leftDx = -1; leftDz = 0; }
        int rightDx = -leftDx, rightDz = -leftDz;
        // 2 столба фланкируют козырёк
        for (int dy = 1; dy <= 4; dy++) {
            painter.place(dx + p1Dx + leftDx, Y_BASE + dy, dz + p1Dz + leftDz, Material.OAK_FENCE);
            painter.place(dx + p1Dx + rightDx, Y_BASE + dy, dz + p1Dz + rightDz, Material.OAK_FENCE);
        }
        // Балка-перекладина
        painter.place(dx + p1Dx + leftDx, Y_BASE + 4, dz + p1Dz + leftDz, Material.OAK_LOG);
        painter.place(dx + p1Dx + rightDx, Y_BASE + 4, dz + p1Dz + rightDz, Material.OAK_LOG);
        // Навес — 3 OAK_STAIRS перевёрнутые наружу, на y+5 над крыльцом
        BlockData awningStair = Material.OAK_STAIRS.createBlockData(
                "[facing=" + facingStr + ",half=top]");
        painter.placeData(dx + outDx, Y_BASE + 5, dz + outDz, awningStair);
        painter.placeData(dx + outDx + leftDx, Y_BASE + 5, dz + outDz + leftDz, awningStair);
        painter.placeData(dx + outDx + rightDx, Y_BASE + 5, dz + outDz + rightDz, awningStair);
        // Подножка навеса (планка)
        painter.place(dx + p1Dx, Y_BASE + 5, dz + p1Dz, Material.OAK_PLANKS);

        // 4. ЦЕПЬ + LANTERN, висящий с навеса над ступенькой
        painter.place(dx + outDx, Y_BASE + 4, dz + outDz, Material.CHAIN);
        painter.place(dx + outDx, Y_BASE + 3, dz + outDz, Material.LANTERN);

        // 5. ДЕКОР: горшок слева, бочка справа (на крыльце)
        painter.place(dx + outDx + leftDx, Y_BASE + 2, dz + outDz + leftDz, Material.FLOWER_POT);
        painter.place(dx + outDx + rightDx, Y_BASE + 2, dz + outDz + rightDz,
                hr.nextBoolean() ? Material.BARREL : Material.OAK_PLANKS);
        // Иногда коврик
        if (hr.nextDouble() < 0.4) {
            Material carpet = (family == 3) ? Material.PURPLE_CARPET : Material.RED_CARPET;
            painter.place(dx + outDx, Y_BASE + 2, dz + outDz, carpet);
        }
    }

    private String invertFacing(String f) {
        switch (f) {
            case "north": return "south";
            case "south": return "north";
            case "east":  return "west";
            default:      return "east";
        }
    }

    /**
     * Окна на стене (в направлении xMin..xMax). Окна 2 блока высотой,
     * с TRAPDOOR-ставнями по бокам.
     */
    private void placeWindowsOnWall(int xMin, int xMax, int z, int yBase, Material winMat,
                                     String shutterFacing, boolean longSide, Random hr) {
        int interval = longSide ? 3 : 4;
        for (int x = xMin + 2; x <= xMax - 2; x += interval) {
            painter.place(x, yBase, z, winMat);
            painter.place(x, yBase + 1, z, winMat);
            // Ставни TRAPDOOR (открыты под углом)
            BlockData shutterL = Material.OAK_TRAPDOOR.createBlockData(
                    "[facing=" + shutterFacing + ",half=top,open=true]");
            BlockData shutterR = Material.OAK_TRAPDOOR.createBlockData(
                    "[facing=" + shutterFacing + ",half=top,open=true]");
            // Слева/справа от окна
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
     * Двускатная крыша из STAIRS (настоящие скаты, а не плоские планки).
     * Если alongZ=true — гребень идёт вдоль оси Z, если false — вдоль X.
     */
    private void buildStairsRoof(int xMin, int zMin, int xMax, int zMax,
                                  int eaveY, boolean alongZ, Material roofMat, Material fillMat) {
        if (alongZ) {
            // Гребень вдоль Z, скаты по X
            int span = xMax - xMin;
            int half = (span + 1) / 2;
            int cx = (xMin + xMax) / 2;
            for (int rise = 0; rise <= half; rise++) {
                int y = eaveY + rise;
                int xL = xMin + rise;
                int xR = xMax - rise;
                if (xL > xR) break;
                BlockData stairW = roofMat.createBlockData("[facing=east,half=bottom]");
                BlockData stairE = roofMat.createBlockData("[facing=west,half=bottom]");
                for (int z = zMin - 1; z <= zMax + 1; z++) {
                    if (xL == xR) {
                        // гребень
                        painter.place(xL, y, z, fillMat);
                    } else {
                        painter.placeData(xL, y, z, stairW);
                        painter.placeData(xR, y, z, stairE);
                        // Заполнение между скатами на этом уровне (внутренность чердака)
                        for (int x = xL + 1; x < xR; x++) {
                            painter.place(x, y - 1, z, Material.AIR); // расчистка чердака
                        }
                    }
                }
                // Фронтон (gable triangle) — на торцах стены продлеваем вверх
                int xL2 = Math.max(xL, xMin);
                int xR2 = Math.min(xR, xMax);
                for (int x = xL2; x <= xR2; x++) {
                    painter.place(x, y, zMin, Material.DEEPSLATE_BRICKS);
                    painter.place(x, y, zMax, Material.DEEPSLATE_BRICKS);
                }
            }
        } else {
            int span = zMax - zMin;
            int half = (span + 1) / 2;
            int cz = (zMin + zMax) / 2;
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
                    }
                }
                int zN2 = Math.max(zN, zMin);
                int zS2 = Math.min(zS, zMax);
                for (int z = zN2; z <= zS2; z++) {
                    painter.place(xMin, y, z, Material.DEEPSLATE_BRICKS);
                    painter.place(xMax, y, z, Material.DEEPSLATE_BRICKS);
                }
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
}
