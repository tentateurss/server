package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Интерьер города Эликий — улицы, площадь перед собором, готические дома,
 * фонарные столбы. Запускается в фазах 4 (улицы) и 5 (точки интереса)
 * {@link WorldGenerator}; выполняется ДО фазы 6 (стены, собор), поэтому
 * стены и собор перезаписывают любые конфликтующие блоки города.
 *
 * <p><b>Стиль</b> — мрачная готика по референсу пользователя:
 * <ul>
 *   <li>стены домов — DEEPSLATE_BRICKS / COBBLED_DEEPSLATE с DARK_OAK_LOG
 *       угловыми колоннами и междуэтажным фахверком;</li>
 *   <li>крыши — высокие двухскатные DARK_OAK_PLANKS / SPRUCE_STAIRS;</li>
 *   <li>окна — PURPLE_STAINED_GLASS (ланцеты с витражами);</li>
 *   <li>двери — DARK_OAK_DOOR (фронтальные, под арочными порталами);</li>
 *   <li>трубы — COBBLED_DEEPSLATE c CAMPFIRE сверху для дыма;</li>
 *   <li>уличные фонари — POLISHED_BLACKSTONE_BRICK_WALL столбы с
 *       LANTERN/SOUL_LANTERN на цепи;</li>
 *   <li>площадь — концентрические кольца POLISHED_BLACKSTONE +
 *       DEEPSLATE_TILES + центральный фонтан с водой и QUARTZ-обрамлением.</li>
 * </ul>
 *
 * <p><b>Геометрия</b>:
 * <ul>
 *   <li>Площадь: 29×29 на (45, 50), к югу от собора (между cathedral
 *       z=27 и краем площади z=64);</li>
 *   <li>Главные улицы шириной 7 (halfWidth=3) ведут от 4 ворот к
 *       центральному перекрёстку (0, 0) и к площади;</li>
 *   <li>~30 готических домов рассеяны вне зон собор/площадь/улицы/стена.</li>
 * </ul>
 */
public final class ElikiumCity {

    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70

    // ===== Cathedral exclusion (с буфером) =====
    private static final int CATHEDRAL_X_MIN = 45 - 34;
    private static final int CATHEDRAL_X_MAX = 45 + 34;
    private static final int CATHEDRAL_Z_MIN = -15 - 46;
    private static final int CATHEDRAL_Z_MAX = -15 + 46;

    // ===== Plaza =====
    private static final int PLAZA_CX = 45;
    private static final int PLAZA_CZ = 50;
    private static final int PLAZA_HALF = 14; // 29×29

    // ===== Streets =====
    private static final int STREET_HALF_WIDTH = 3; // ширина 7

    // ===== Houses =====
    private static final int TARGET_HOUSES = 32;
    private static final int MAX_HOUSE_ATTEMPTS = 600;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final List<int[]> placedHouses = new ArrayList<>();
    private final List<int[]> streetPoints = new ArrayList<>();
    private final Set<Long> streetCells = new HashSet<>();

    private static long packCoord(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public ElikiumCity(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
    }

    /** Полная застройка города. */
    public void build() {
        plugin.getLogger().info("ElikiumCity: строю улицы, площадь, дома, фонари…");
        long ops = 0;
        ops += buildStreets();
        ops += buildPlaza();
        ops += buildHouses();
        ops += buildStreetLamps();
        plugin.getLogger().info("ElikiumCity: ~" + ops + " блок-операций (улицы, площадь, "
                + placedHouses.size() + " домов, фонари).");
    }

    // =========================================================================
    // УЛИЦЫ — соединяют 4 ворот через центральный перекрёсток с площадью
    // =========================================================================

    private long buildStreets() {
        long count = 0;
        // 4 главные радиальные улицы от ворот к центру
        count += pavePath(WorldGenerator.SOUTH_GATE[0], WorldGenerator.SOUTH_GATE[1],
                PLAZA_CX, PLAZA_CZ + PLAZA_HALF + 1);                                // юг → площадь
        count += pavePath(WorldGenerator.NORTH_GATE[0], WorldGenerator.NORTH_GATE[1],
                0, 0);                                                                // север → центр
        count += pavePath(WorldGenerator.EAST_GATE[0], WorldGenerator.EAST_GATE[1],
                PLAZA_CX + PLAZA_HALF + 1, PLAZA_CZ);                                 // восток → площадь
        count += pavePath(WorldGenerator.WEST_GATE[0], WorldGenerator.WEST_GATE[1],
                0, 0);                                                                // запад → центр
        // Центральный перекрёсток → площадь
        count += pavePath(0, 0, PLAZA_CX - PLAZA_HALF - 1, PLAZA_CZ);
        return count;
    }

    private long pavePath(int x1, int z1, int x2, int z2) {
        long count = 0;
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        int cx = x1, cz = z1;
        int step = 0;
        while (true) {
            for (int ox = -STREET_HALF_WIDTH; ox <= STREET_HALF_WIDTH; ox++) {
                for (int oz = -STREET_HALF_WIDTH; oz <= STREET_HALF_WIDTH; oz++) {
                    int x = cx + ox, z = cz + oz;
                    if (!WorldGenerator.isInsideCityPolygon(x, z)) continue;
                    if (insideCathedralZone(x, z)) continue;
                    int aoxoz = Math.max(Math.abs(ox), Math.abs(oz));
                    Material mat;
                    if (aoxoz == STREET_HALF_WIDTH) {
                        mat = Material.DEEPSLATE_TILES; // обочина
                    } else if (((x + z) & 1) == 0) {
                        mat = Material.POLISHED_BLACKSTONE;
                    } else {
                        mat = Material.COBBLED_DEEPSLATE;
                    }
                    painter.place(x, Y_BASE, z, mat);
                    streetCells.add(packCoord(x, z));
                    count++;
                }
            }
            // Отметить точку для размещения уличных фонарей
            if (step % 14 == 0) {
                streetPoints.add(new int[]{cx, cz});
            }
            step++;
            if (cx == x2 && cz == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 < dx)  { err += dx; cz += sz; }
        }
        return count;
    }

    // =========================================================================
    // ПЛОЩАДЬ — мощёный квадрат перед собором с фонтаном и фонарями
    // =========================================================================

    private long buildPlaza() {
        long count = 0;
        // Мощение
        for (int dx = -PLAZA_HALF; dx <= PLAZA_HALF; dx++) {
            for (int dz = -PLAZA_HALF; dz <= PLAZA_HALF; dz++) {
                int x = PLAZA_CX + dx, z = PLAZA_CZ + dz;
                if (!WorldGenerator.isInsideCityPolygon(x, z)) continue;
                if (insideCathedralZone(x, z)) continue;
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                Material mat;
                if (cheb == PLAZA_HALF) mat = Material.DEEPSLATE_TILES;
                else if (cheb >= 8) mat = Material.POLISHED_BLACKSTONE;
                else if (cheb >= 4) mat = Material.POLISHED_BLACKSTONE_BRICKS;
                else mat = ((dx + dz) & 1) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS
                                               : Material.GILDED_BLACKSTONE;
                painter.place(x, Y_BASE, z, mat);
                count++;
            }
        }
        // Фонтан 5×5 в центре
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = PLAZA_CX + dx, z = PLAZA_CZ + dz;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    painter.place(x, Y_BASE + 1, z, Material.QUARTZ_BLOCK);
                    count++;
                }
            }
        }
        // Вода 3×3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(PLAZA_CX + dx, Y_BASE + 1, PLAZA_CZ + dz, Material.WATER);
                count++;
            }
        }
        // Центральная колонна с морским фонарём
        painter.place(PLAZA_CX, Y_BASE + 2, PLAZA_CZ, Material.QUARTZ_PILLAR);
        painter.place(PLAZA_CX, Y_BASE + 3, PLAZA_CZ, Material.QUARTZ_PILLAR);
        painter.place(PLAZA_CX, Y_BASE + 4, PLAZA_CZ, Material.SEA_LANTERN);
        painter.place(PLAZA_CX, Y_BASE + 5, PLAZA_CZ, Material.GOLD_BLOCK);
        count += 4;
        // 4 фонарных столба по углам
        for (int signX : new int[]{-1, +1}) {
            for (int signZ : new int[]{-1, +1}) {
                int lx = PLAZA_CX + signX * (PLAZA_HALF - 2);
                int lz = PLAZA_CZ + signZ * (PLAZA_HALF - 2);
                count += buildLamppost(lx, lz, true);
            }
        }
        return count;
    }

    // =========================================================================
    // ФОНАРНЫЕ СТОЛБЫ — на улицах
    // =========================================================================

    private long buildStreetLamps() {
        long count = 0;
        for (int[] pt : streetPoints) {
            int x = pt[0], z = pt[1];
            // Сместить фонарь на обочину (вдоль перпендикуляра)
            int lx = x + STREET_HALF_WIDTH + 1;
            int lz = z;
            if (canPlaceLamppost(lx, lz)) {
                count += buildLamppost(lx, lz, false);
            }
            int lx2 = x - STREET_HALF_WIDTH - 1;
            if (canPlaceLamppost(lx2, lz)) {
                count += buildLamppost(lx2, lz, false);
            }
        }
        return count;
    }

    private boolean canPlaceLamppost(int x, int z) {
        if (!WorldGenerator.isInsideCityPolygon(x, z)) return false;
        if (insideCathedralZone(x, z)) return false;
        if (insidePlazaZone(x, z)) return false;
        // не на улице
        for (int[] hb : placedHouses) {
            if (x >= hb[0] - 1 && x <= hb[2] + 1 && z >= hb[1] - 1 && z <= hb[3] + 1) return false;
        }
        return true;
    }

    private long buildLamppost(int x, int z, boolean tall) {
        int height = tall ? 5 : 4;
        for (int dy = 1; dy <= height; dy++) {
            painter.place(x, Y_BASE + dy, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        painter.place(x, Y_BASE + height + 1, z, Material.LANTERN);
        // Декоративный кронштейн
        painter.place(x, Y_BASE + height, z, Material.POLISHED_BLACKSTONE_WALL);
        return height + 2;
    }

    // =========================================================================
    // ДОМА — рандомно по полигону, избегая собора/площади/улиц/стены
    // =========================================================================

    private long buildHouses() {
        long count = 0;
        int placed = 0;
        int attempts = 0;
        while (placed < TARGET_HOUSES && attempts < MAX_HOUSE_ATTEMPTS) {
            attempts++;
            int cx = -130 + rng.nextInt(260);
            int cz = -135 + rng.nextInt(255);
            int w = 8 + rng.nextInt(4); // 8..11
            int d = 8 + rng.nextInt(4); // 8..11
            int xMin = cx - w / 2, xMax = cx + w / 2;
            int zMin = cz - d / 2, zMax = cz + d / 2;
            // Buffer 4 + check полигон/собор/площадь
            if (!isFreeFootprint(xMin - 4, zMin - 4, xMax + 4, zMax + 4)) continue;
            int seed = rng.nextInt();
            count += buildGothicHouse(cx, cz, w, d, seed);
            placedHouses.add(new int[]{xMin, zMin, xMax, zMax});
            placed++;
        }
        plugin.getLogger().info("ElikiumCity: размещено " + placed + "/" + TARGET_HOUSES
                + " домов (попыток: " + attempts + ").");
        return count;
    }

    private boolean isFreeFootprint(int x1, int z1, int x2, int z2) {
        // Все 4 угла + центр должны быть внутри полигона
        if (!WorldGenerator.isInsideCityPolygon(x1, z1)) return false;
        if (!WorldGenerator.isInsideCityPolygon(x2, z1)) return false;
        if (!WorldGenerator.isInsideCityPolygon(x1, z2)) return false;
        if (!WorldGenerator.isInsideCityPolygon(x2, z2)) return false;
        // Не пересекать собор
        if (boxesOverlap(x1, z1, x2, z2,
                CATHEDRAL_X_MIN, CATHEDRAL_Z_MIN, CATHEDRAL_X_MAX, CATHEDRAL_Z_MAX)) return false;
        // Не пересекать площадь
        if (boxesOverlap(x1, z1, x2, z2,
                PLAZA_CX - PLAZA_HALF - 2, PLAZA_CZ - PLAZA_HALF - 2,
                PLAZA_CX + PLAZA_HALF + 2, PLAZA_CZ + PLAZA_HALF + 2)) return false;
        // Не пересекать другие дома
        for (int[] hb : placedHouses) {
            if (boxesOverlap(x1, z1, x2, z2, hb[0] - 4, hb[1] - 4, hb[2] + 4, hb[3] + 4)) {
                return false;
            }
        }
        // Не пересекать улицы (хотя бы 1 блок дома + буфер уже на улице)
        for (int x = x1; x <= x2; x += 2) {
            for (int z = z1; z <= z2; z += 2) {
                if (streetCells.contains(packCoord(x, z))) return false;
            }
        }
        return true;
    }

    private boolean boxesOverlap(int x1a, int z1a, int x2a, int z2a,
                                  int x1b, int z1b, int x2b, int z2b) {
        return x1a <= x2b && x2a >= x1b && z1a <= z2b && z2a >= z1b;
    }

    private boolean insideCathedralZone(int x, int z) {
        return x >= CATHEDRAL_X_MIN && x <= CATHEDRAL_X_MAX
            && z >= CATHEDRAL_Z_MIN && z <= CATHEDRAL_Z_MAX;
    }

    private boolean insidePlazaZone(int x, int z) {
        return Math.abs(x - PLAZA_CX) <= PLAZA_HALF + 1
            && Math.abs(z - PLAZA_CZ) <= PLAZA_HALF + 1;
    }

    /**
     * Готический дом 8-11×8-11, 2-3 этажа, с двускатной крышей, дверью на юг,
     * 4-6 окнами, угловыми колоннами, опционально дымоходом и козырьком.
     */
    private long buildGothicHouse(int cx, int cz, int w, int d, int seed) {
        long count = 0;
        Random hr = new Random(seed);
        int floors = 2 + hr.nextInt(2); // 2..3
        int floorH = 4;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;

        // Фундамент-цоколь (1 блок выше пола, COBBLED_DEEPSLATE)
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // Стены по этажам
        for (int floor = 0; floor < floors; floor++) {
            int yBase = Y_BASE + 2 + floor * floorH;
            Material wallMat = (floor == 0)
                    ? Material.DEEPSLATE_BRICKS
                    : Material.DARK_OAK_PLANKS;
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                    if (!perim) continue;
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, z, wallMat);
                        count++;
                    }
                }
            }
            // Угловые колонны DARK_OAK_LOG (фахверк)
            for (int signX : new int[]{-1, +1}) {
                for (int signZ : new int[]{-1, +1}) {
                    int x = (signX < 0) ? xMin : xMax;
                    int z = (signZ < 0) ? zMin : zMax;
                    for (int dy = 0; dy < floorH; dy++) {
                        painter.place(x, yBase + dy, z, Material.DARK_OAK_LOG);
                        count++;
                    }
                }
            }
            // Окна на этаже (PURPLE_STAINED_GLASS)
            int windowY = yBase + 1;
            placeWindowRow(xMin + 1, xMax - 1, zMin, windowY, true);
            placeWindowRow(xMin + 1, xMax - 1, zMax, windowY, true);
            placeWindowCol(zMin + 1, zMax - 1, xMin, windowY, false);
            placeWindowCol(zMin + 1, zMax - 1, xMax, windowY, false);
        }

        // Двускатная крыша вдоль оси Z (длинная сторона)
        int roofBaseY = Y_BASE + 2 + floors * floorH;
        boolean roofAlongZ = (d >= w);
        int span = roofAlongZ ? w : d;
        int roofH = (span + 1) / 2;
        for (int rise = 0; rise <= roofH; rise++) {
            int y = roofBaseY + rise;
            if (roofAlongZ) {
                int dx = (span - 1) / 2 - rise;
                if (dx < 0) break;
                int xL = cx - dx, xR = cx + dx;
                for (int z = zMin; z <= zMax; z++) {
                    painter.place(xL, y, z, Material.DARK_OAK_LOG);
                    if (xR != xL) painter.place(xR, y, z, Material.DARK_OAK_LOG);
                    count += (xR != xL) ? 2 : 1;
                }
            } else {
                int dz = (span - 1) / 2 - rise;
                if (dz < 0) break;
                int zN = cz - dz, zS = cz + dz;
                for (int x = xMin; x <= xMax; x++) {
                    painter.place(x, y, zN, Material.DARK_OAK_LOG);
                    if (zS != zN) painter.place(x, y, zS, Material.DARK_OAK_LOG);
                    count += (zS != zN) ? 2 : 1;
                }
            }
        }
        // Каменные торцы крыши (фронтоны)
        for (int rise = 0; rise <= roofH; rise++) {
            int y = roofBaseY + rise;
            if (roofAlongZ) {
                int dx = (span - 1) / 2 - rise;
                if (dx < 0) break;
                for (int x = cx - dx; x <= cx + dx; x++) {
                    painter.place(x, y, zMin, Material.DEEPSLATE_BRICKS);
                    painter.place(x, y, zMax, Material.DEEPSLATE_BRICKS);
                    count += 2;
                }
            } else {
                int dz = (span - 1) / 2 - rise;
                if (dz < 0) break;
                for (int z = cz - dz; z <= cz + dz; z++) {
                    painter.place(xMin, y, z, Material.DEEPSLATE_BRICKS);
                    painter.place(xMax, y, z, Material.DEEPSLATE_BRICKS);
                    count += 2;
                }
            }
        }

        // Дверь — по центру южной стены
        int doorX = cx, doorZ = zMax;
        painter.place(doorX, Y_BASE + 2, doorZ, Material.AIR);
        painter.place(doorX, Y_BASE + 3, doorZ, Material.AIR);
        BlockData doorBottom = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=north,hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=north,hinge=left]");
        painter.placeData(doorX, Y_BASE + 2, doorZ, doorBottom);
        painter.placeData(doorX, Y_BASE + 3, doorZ, doorTop);
        count += 2;
        // Над дверью — фонарь
        painter.place(doorX, Y_BASE + 5, doorZ, Material.LANTERN);
        count++;

        // Дымоход (50% шанс)
        if (hr.nextDouble() < 0.7) {
            int chOffsetX = (hr.nextBoolean() ? -1 : +1) * (w / 2 - 2);
            int chX = cx + chOffsetX;
            int chZ = cz - 1;
            int chTop = roofBaseY + roofH + 2;
            for (int y = roofBaseY; y <= chTop; y++) {
                painter.place(chX, y, chZ, Material.COBBLED_DEEPSLATE);
                count++;
            }
            painter.place(chX, chTop + 1, chZ, Material.CAMPFIRE);
            count++;
        }

        // Внутреннее освещение (LANTERN на потолке первого этажа)
        painter.place(cx, Y_BASE + 2 + floorH - 1, cz, Material.LANTERN);
        count++;

        return count;
    }

    private void placeWindowRow(int xMin, int xMax, int z, int y, boolean horizontal) {
        for (int x = xMin + 1; x <= xMax - 1; x += 3) {
            painter.place(x, y, z, Material.PURPLE_STAINED_GLASS);
            painter.place(x, y + 1, z, Material.PURPLE_STAINED_GLASS);
        }
    }

    private void placeWindowCol(int zMin, int zMax, int x, int y, boolean horizontal) {
        for (int z = zMin + 1; z <= zMax - 1; z += 3) {
            painter.place(x, y, z, Material.PURPLE_STAINED_GLASS);
            painter.place(x, y + 1, z, Material.PURPLE_STAINED_GLASS);
        }
    }
}
