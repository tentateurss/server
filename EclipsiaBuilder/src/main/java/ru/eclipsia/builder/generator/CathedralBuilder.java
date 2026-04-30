package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Готический собор Эликий — главное здание города. Тёмная палитра
 * (DEEPSLATE_BRICKS / POLISHED_BLACKSTONE_BRICKS), фиолетовые витражи
 * со внутренней подсветкой, крестообразный план (cruciform), семь
 * остроконечных башен и парящий «Глаз Эликия» над центральным шпилем.
 *
 * <p><b>Геометрия (PR 3.5)</b>:
 * <ul>
 *   <li>Центр: ({@link WorldGenerator#CATHEDRAL_X}, {@link WorldGenerator#CATHEDRAL_Z})
 *       = (45, -15).</li>
 *   <li>Cruciform: неф ±15 × ±42 (вдоль Z), трансепт ±30 × ±7 (вдоль X).</li>
 *   <li>Стены y=70..101 (высота 32), толщина 2 блока.</li>
 *   <li>Двускатная крыша до y=119, гребень y=120 POLISHED_BLACKSTONE.</li>
 *   <li>Башни: 1 центральная (11×11, шпиль до y=186), 2 фасадные
 *       южные (7×7, шпили до y=143), 4 пинакля по концам креста
 *       (5×5, шпили до y=130).</li>
 *   <li>Парящий «Глаз Эликия» — на y=200 (15 блоков над центральным
 *       шпилем), AMETHYST_BLOCK 3×3×3 + 3 кольца END_ROD.</li>
 * </ul>
 *
 * <p>Все блоки кладутся через {@link RegionPainter} — асинхронно. После
 * {@code build()} обновляется {@link WorldGenerator#spireCenterX/Y/Z}, и
 * {@link SpireParticles} начинает крутить частицы вокруг Глаза.
 */
public final class CathedralBuilder {

    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70
    private static final int CX = WorldGenerator.CATHEDRAL_X;      // 45
    private static final int CZ = WorldGenerator.CATHEDRAL_Z;      // -15

    /** Cruciform footprint. */
    private static final int HALF_NAVE_W     = 15; // x ∈ [CX-15..CX+15] = 31 wide
    private static final int HALF_NAVE_L     = 42; // z ∈ [CZ-42..CZ+42] = 85 long
    private static final int HALF_TRANSEPT_W = 30; // x ∈ [CX-30..CX+30] = 61 wide
    private static final int HALF_TRANSEPT_L = 7;  // z ∈ [CZ-7..CZ+7]  = 15 deep

    private static final int WALL_HEIGHT = 32;                                  // y=70..101
    private static final int WALL_TOP_Y  = Y_BASE + WALL_HEIGHT;                // y=102
    private static final int ROOF_PEAK_DY = 18;
    private static final int ROOF_PEAK_Y = WALL_TOP_Y + ROOF_PEAK_DY;           // y=120

    /** Центральная башня над пересечением (cross-tower). */
    private static final int CT_HALF       = 5;                                 // 11×11
    private static final int CT_BODY_DY    = 35;                                // y=102..136
    private static final int CT_BODY_TOP_Y = WALL_TOP_Y + CT_BODY_DY;           // y=137
    private static final int CT_SPIRE_DY   = 50;
    private static final int CT_SPIRE_TOP_Y = CT_BODY_TOP_Y + CT_SPIRE_DY;      // y=187

    /** Парящий Глаз над центральной башней. */
    private static final int EYE_Y_OFFSET  = 14;
    private static final int EYE_Y         = CT_SPIRE_TOP_Y + EYE_Y_OFFSET;     // y=201

    /** Две западные (южные) башни-близнецы у фасада. */
    private static final int WT_HALF        = 3;                                // 7×7
    private static final int WT_X_OFFSET    = 12;                               // x=CX±12
    private static final int WT_Z           = HALF_NAVE_L - 5;                  // z=CZ+37
    private static final int WT_BODY_DY     = 18;                               // y=102..119
    private static final int WT_SPIRE_DY    = 24;
    private static final int WT_SPIRE_TOP_Y = WALL_TOP_Y + WT_BODY_DY + WT_SPIRE_DY; // y=144

    /** 4 пинакля по концам креста. */
    private static final int CP_HALF        = 2;                                // 5×5
    private static final int CP_BODY_DY     = 12;
    private static final int CP_SPIRE_DY    = 16;
    private static final int CP_TOP_Y       = WALL_TOP_Y + CP_BODY_DY + CP_SPIRE_DY; // y=130

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;

    public CathedralBuilder(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
    }

    public void build() {
        plugin.getLogger().info(
                "CathedralBuilder: строю готический собор (45,-15) — cruciform, "
                + "7 башен, Глаз на y=" + EYE_Y + "…");

        long ops = 0;
        ops += buildFloor();
        ops += buildOuterWalls();
        ops += buildButtresses();
        ops += buildWindows();
        ops += buildSouthPortal();
        ops += buildRoof();
        ops += buildCentralTower();
        ops += buildWestTowers();
        ops += buildCornerPinnacles();
        ops += buildFloatingEye();
        ops += buildInteriorLight();

        plugin.getLogger().info(
                "CathedralBuilder: ~" + ops + " блок-операций готовы (стены, "
                + "башни, шпиль, парящий Глаз).");

        // Перевешиваем частицы на Глаз.
        WorldGenerator.spireCenterX = CX + 0.5;
        WorldGenerator.spireCenterY = EYE_Y + 0.5;
        WorldGenerator.spireCenterZ = CZ + 0.5;
    }

    // =========================================================================
    // FOOTPRINT HELPERS (cruciform)
    // =========================================================================

    /** Точка (CX+dx, CZ+dz) лежит внутри крестообразного следа собора. */
    private boolean inFootprint(int dx, int dz) {
        boolean inNave =
                Math.abs(dx) <= HALF_NAVE_W && Math.abs(dz) <= HALF_NAVE_L;
        boolean inTransept =
                Math.abs(dx) <= HALF_TRANSEPT_W && Math.abs(dz) <= HALF_TRANSEPT_L;
        return inNave || inTransept;
    }

    /** Точка лежит на внешнем периметре footprint (есть сосед-снаружи). */
    private boolean isPerimeter(int dx, int dz) {
        if (!inFootprint(dx, dz)) return false;
        return !inFootprint(dx - 1, dz) || !inFootprint(dx + 1, dz)
            || !inFootprint(dx, dz - 1) || !inFootprint(dx, dz + 1);
    }

    /** Точка является внешним углом периметра (2 пустых соседа подряд). */
    private boolean isOuterCorner(int dx, int dz) {
        if (!inFootprint(dx, dz)) return false;
        boolean L = !inFootprint(dx - 1, dz);
        boolean R = !inFootprint(dx + 1, dz);
        boolean N = !inFootprint(dx, dz - 1);
        boolean S = !inFootprint(dx, dz + 1);
        return (L && N) || (L && S) || (R && N) || (R && S);
    }

    // =========================================================================
    // ФАЗА 1: ПОЛ
    // =========================================================================

    private long buildFloor() {
        long count = 0;
        // Пол всего footprint — POLISHED_BLACKSTONE_BRICKS + DEEPSLATE_BRICKS-крест.
        for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                if (!inFootprint(dx, dz)) continue;
                Material floor;
                boolean onNaveAxis     = Math.abs(dx) <= 2;
                boolean onTranseptAxis = Math.abs(dz) <= 2;
                if (onNaveAxis || onTranseptAxis) {
                    floor = Material.POLISHED_BLACKSTONE;
                } else if (((dx + dz) & 1) == 0) {
                    floor = Material.DEEPSLATE_BRICKS;
                } else {
                    floor = Material.POLISHED_BLACKSTONE_BRICKS;
                }
                painter.place(CX + dx, Y_BASE, CZ + dz, floor);
                count++;
            }
        }
        // Центральная мозаика 7×7 на пересечении трансепта и нефа.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int adx = Math.abs(dx), adz = Math.abs(dz);
                int dist = Math.max(adx, adz);
                Material mat = (dist == 0) ? Material.AMETHYST_BLOCK
                        : (dist == 1) ? Material.PURPUR_BLOCK
                        : (dist == 2) ? Material.POLISHED_BLACKSTONE
                        : Material.DEEPSLATE_TILES;
                painter.place(CX + dx, Y_BASE, CZ + dz, mat);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 2: ВНЕШНИЕ СТЕНЫ
    // =========================================================================

    private long buildOuterWalls() {
        long count = 0;
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            int y = Y_BASE + dy;
            for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
                for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                    if (!isPerimeter(dx, dz)) continue;
                    Material mat = pickWallMaterial(dx, dy, dz);
                    painter.place(CX + dx, y, CZ + dz, mat);
                    count++;
                }
            }
        }
        // Карниз +1 над стеной, по всему периметру (POLISHED_BLACKSTONE_WALL).
        for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                if (!isPerimeter(dx, dz)) continue;
                painter.place(CX + dx, WALL_TOP_Y, CZ + dz,
                        Material.POLISHED_BLACKSTONE_BRICK_WALL);
                count++;
            }
        }
        return count;
    }

    private Material pickWallMaterial(int dx, int dy, int dz) {
        if (isOuterCorner(dx, dz)) {
            return Material.POLISHED_BLACKSTONE;
        }
        // Цоколь 3 блока — COBBLED_DEEPSLATE.
        if (dy <= 3) return Material.COBBLED_DEEPSLATE;
        // Декоративные пояса каждые 8 блоков — CHISELED_DEEPSLATE.
        if (dy == 12 || dy == 22 || dy == WALL_HEIGHT - 1) {
            return Material.CHISELED_DEEPSLATE;
        }
        // «Бордюр» снизу карниза — POLISHED_BLACKSTONE_BRICKS.
        if (dy == WALL_HEIGHT) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        // Чередование DEEPSLATE_BRICKS / POLISHED_BLACKSTONE_BRICKS по высоте.
        boolean even = ((dx + dz + dy) & 1) == 0;
        return even ? Material.DEEPSLATE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
    }

    // =========================================================================
    // ФАЗА 3: КОНТРФОРСЫ С ПИНАКЛЯМИ
    // =========================================================================

    private long buildButtresses() {
        long count = 0;
        // Ставим контрфорсы на длинных сторонах нефа, 4 пары.
        int[] zs = { -32, -18, 18, 32 };
        for (int dz : zs) {
            for (int side : new int[] { -1, +1 }) {
                int bx = CX + side * (HALF_NAVE_W + 1);
                int bz = CZ + dz;
                count += buildOneButtress(bx, bz);
            }
        }
        // На длинных сторонах трансепта (вдоль X), 2 пары.
        int[] xs = { -22, 22 };
        for (int dx : xs) {
            for (int side : new int[] { -1, +1 }) {
                int bx = CX + dx;
                int bz = CZ + side * (HALF_TRANSEPT_L + 1);
                count += buildOneButtress(bx, bz);
            }
        }
        return count;
    }

    /** Контрфорс 1×1 + пинакль (мини-шпиль 6 блоков). */
    private long buildOneButtress(int bx, int bz) {
        long count = 0;
        // Нижняя часть — POLISHED_BLACKSTONE 1×1, до y=WALL_TOP_Y-2.
        for (int dy = 0; dy <= WALL_HEIGHT - 2; dy++) {
            int y = Y_BASE + dy;
            Material mat;
            if (dy == 0) mat = Material.POLISHED_BLACKSTONE;
            else if (dy <= 2) mat = Material.COBBLED_DEEPSLATE;
            else if (dy % 6 == 0) mat = Material.CHISELED_DEEPSLATE;
            else mat = Material.POLISHED_BLACKSTONE_BRICKS;
            painter.place(bx, y, bz, mat);
            count++;
        }
        // Пинакль (мини-шпиль) над контрфорсом — 6 блоков, остроконечный.
        int pinTop = Y_BASE + WALL_HEIGHT - 2;
        for (int dy = 1; dy <= 5; dy++) {
            int y = pinTop + dy;
            // Узкая «башенка» 1×1 + END_ROD на конце.
            Material mat = (dy == 5) ? Material.END_ROD
                    : (dy == 4) ? Material.LIGHTNING_ROD
                    : Material.CHISELED_DEEPSLATE;
            painter.place(bx, y, bz, mat);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 4: ВЫСОКИЕ ВИТРАЖИ
    // =========================================================================

    private long buildWindows() {
        long count = 0;
        // На длинных боках нефа — 4 высоких витража с каждой стороны
        // (между контрфорсами на z=±32, ±18). centerZ — абсолютная z.
        int[] naveCenterZs = { -36, -25, 11, 25 };
        for (int side : new int[] { -1, +1 }) {
            int x = CX + side * HALF_NAVE_W;
            for (int cz : naveCenterZs) {
                count += buildTallGothicWindow(x, side, CZ + cz, true);
            }
        }
        // На торцах трансепта (E/W) — большая роза.
        for (int side : new int[] { -1, +1 }) {
            int x = CX + side * HALF_TRANSEPT_W;
            count += buildRoseWindow(x, side, CZ);
        }
        // Северная апсида — высокий витраж по центру (на стене, не вдоль оси Z).
        count += buildTallGothicWindow(CX, -1, CZ - HALF_NAVE_L, false);
        // Южный фасад: витражи по бокам от портала.
        for (int dx : new int[] { -10, 10 }) {
            count += buildTallGothicWindow(CX + dx, 1, CZ + HALF_NAVE_L, false);
        }
        return count;
    }

    /**
     * Высокое узкое готическое окно: ширина 5, высота 17, остроконечный
     * арочный верх. Стены собора однотолщинные, поэтому ставим только
     * один слой стекла; внутреннюю подсветку добавляем рядом.
     */
    private long buildTallGothicWindow(int wallX, int outwardSide, int centerZ, boolean alongZ) {
        long count = 0;
        int yBot = Y_BASE + 6;
        int yTop = Y_BASE + 23;
        for (int oz = -2; oz <= 2; oz++) {
            for (int y = yBot; y <= yTop; y++) {
                Material mat;
                int aoz = Math.abs(oz);
                int dyTop = yTop - y;
                // Стрельчатая арка: ширина 1 на самом верху, +2 каждые 1-2 блока.
                if (dyTop <= 0 && aoz > 0) continue;
                if (dyTop <= 1 && aoz > 1) continue;
                if (dyTop <= 2 && aoz > 2) continue;

                boolean isFrame = (aoz == 2) || (y == yBot)
                        || (dyTop == 0)
                        || (dyTop == 1 && aoz == 1)
                        || (dyTop == 2 && aoz == 2);
                if (isFrame) {
                    mat = Material.POLISHED_BLACKSTONE;
                } else if (aoz == 1) {
                    mat = Material.MAGENTA_STAINED_GLASS;
                } else {
                    mat = Material.PURPLE_STAINED_GLASS;
                }
                int wz = (alongZ ? centerZ + oz : centerZ);
                int wx = (alongZ ? wallX : wallX + oz);
                painter.place(wx, y, wz, mat);
                count++;
            }
        }
        // Подсветка изнутри: AMETHYST на уровне подоконника + SHROOMLIGHT снизу.
        if (outwardSide != 0) {
            int innerOffset = -outwardSide;
            int innerX = (alongZ ? wallX + innerOffset : wallX);
            int innerZ = (alongZ ? centerZ           : centerZ + innerOffset);
            painter.place(innerX, Y_BASE + 5, innerZ, Material.AMETHYST_BLOCK);
            painter.place(innerX, Y_BASE + 4, innerZ, Material.SHROOMLIGHT);
            count += 2;
        }
        return count;
    }

    /** Розетка-«роза» 9×9 на торце трансепта (один слой). */
    private long buildRoseWindow(int wallX, int outwardSide, int centerZ) {
        long count = 0;
        int yMid = Y_BASE + 18;
        for (int oz = -4; oz <= 4; oz++) {
            for (int dy = -4; dy <= 4; dy++) {
                int dist = Math.max(Math.abs(oz), Math.abs(dy));
                if (dist > 4) continue;
                Material mat;
                if (dist == 4) mat = Material.POLISHED_BLACKSTONE;
                else if (dist == 3) mat = Material.MAGENTA_STAINED_GLASS;
                else if (dist == 2) mat = Material.PURPLE_STAINED_GLASS;
                else if (dist == 1) mat = Material.MAGENTA_STAINED_GLASS;
                else mat = Material.AMETHYST_BLOCK;
                painter.place(wallX, yMid + dy, centerZ + oz, mat);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 5: ЮЖНЫЙ ПОРТАЛ
    // =========================================================================

    private long buildSouthPortal() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L; // z=27, южная стена нефа (1 блок толщины).
        // Проём 9×16, центр dx=0.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 16; dy++) {
                painter.place(CX + dx, Y_BASE + dy, absZ, Material.AIR);
                count++;
            }
        }
        // Стрельчатая арка над проёмом (готика — пик в центре).
        for (int dx = -5; dx <= 5; dx++) {
            int adx = Math.abs(dx);
            int dy;
            if (adx == 5)      dy = 17;
            else if (adx == 4) dy = 18;
            else if (adx == 3) dy = 19;
            else if (adx == 2) dy = 20;
            else if (adx == 1) dy = 22;
            else               dy = 24;
            painter.place(CX + dx, Y_BASE + dy, absZ, Material.CHISELED_DEEPSLATE);
            count++;
        }
        // Заполнить «пробелы» в арке стандартным камнем (между уступами).
        for (int dx = -5; dx <= 5; dx++) {
            int adx = Math.abs(dx);
            int peakDy = (adx == 0) ? 24 : (adx == 1) ? 22 : (adx == 2) ? 20
                       : (adx == 3) ? 19 : (adx == 4) ? 18 : 17;
            for (int dy = peakDy + 1; dy <= 24; dy++) {
                painter.place(CX + dx, Y_BASE + dy, absZ, Material.POLISHED_BLACKSTONE_BRICKS);
                count++;
            }
        }
        // Замковый камень — AMETHYST_BLOCK на пике.
        painter.place(CX, Y_BASE + 24, absZ, Material.AMETHYST_BLOCK);
        count++;
        // Крыльцо: 3 ступени.
        for (int step = 1; step <= 3; step++) {
            int sz = absZ + step;
            for (int dx = -5; dx <= 5; dx++) {
                Material mat = (step == 1) ? Material.POLISHED_BLACKSTONE
                        : Material.DEEPSLATE_BRICKS;
                painter.place(CX + dx, Y_BASE, sz, mat);
                count++;
            }
        }
        // Светильники по бокам портала — SOUL_LANTERN на цепях.
        for (int side : new int[] { -1, +1 }) {
            int lx = CX + side * 6;
            painter.place(lx, Y_BASE + 8, absZ + 1, Material.CHAIN);
            painter.place(lx, Y_BASE + 7, absZ + 1, Material.CHAIN);
            painter.place(lx, Y_BASE + 6, absZ + 1, Material.SOUL_LANTERN);
            count += 3;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 6: ДВУСКАТНАЯ КРЫША
    // =========================================================================

    private long buildRoof() {
        long count = 0;
        // Крыша нефа: вдоль Z, гребень при x=CX.
        for (int rise = 0; rise <= ROOF_PEAK_DY; rise++) {
            int y = WALL_TOP_Y + rise;
            int dxAt = HALF_NAVE_W - rise;
            if (dxAt < 0) break;
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                if (!inFootprint(0, dz) || Math.abs(0) > HALF_NAVE_W) {} // tiny safety
                // Скаты только над нефом — не над трансептом.
                if (Math.abs(dz) > HALF_NAVE_L) continue;
                if (Math.abs(dz) <= HALF_TRANSEPT_L && dxAt < HALF_TRANSEPT_W) {
                    // Над трансептом нужна отдельная крыша; пропускаем здесь.
                    continue;
                }
                painter.place(CX - dxAt, y, CZ + dz, Material.DEEPSLATE_TILES);
                painter.place(CX + dxAt, y, CZ + dz, Material.DEEPSLATE_TILES);
                count += 2;
            }
        }
        // Крыша трансепта: вдоль X, гребень при z=CZ.
        for (int rise = 0; rise <= 13; rise++) {
            int y = WALL_TOP_Y + rise;
            int dzAt = HALF_TRANSEPT_L - rise;
            if (dzAt < 0) break;
            for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
                if (Math.abs(dx) > HALF_TRANSEPT_W) continue;
                // Только над трансептом, вне нефа.
                if (Math.abs(dx) <= HALF_NAVE_W) continue;
                painter.place(CX + dx, y, CZ - dzAt, Material.DEEPSLATE_TILES);
                painter.place(CX + dx, y, CZ + dzAt, Material.DEEPSLATE_TILES);
                count += 2;
            }
        }
        // Гребень нефа.
        for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
            // Не дублировать там, где будет крест трансепта (закроет это центральная башня).
            if (Math.abs(dz) <= CT_HALF) continue;
            painter.place(CX, ROOF_PEAK_Y, CZ + dz, Material.POLISHED_BLACKSTONE);
            count++;
        }
        // Гребень трансепта.
        for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
            if (Math.abs(dx) <= CT_HALF) continue;
            if (Math.abs(dx) <= HALF_NAVE_W) continue;
            painter.place(CX + dx, WALL_TOP_Y + 13, CZ, Material.POLISHED_BLACKSTONE);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 7: ЦЕНТРАЛЬНАЯ БАШНЯ + ШПИЛЬ
    // =========================================================================

    private long buildCentralTower() {
        long count = 0;
        // Тело башни 11×11 от пола (y=71) до CT_BODY_TOP_Y (y=137).
        for (int y = Y_BASE + 1; y <= CT_BODY_TOP_Y; y++) {
            for (int ox = -CT_HALF; ox <= CT_HALF; ox++) {
                for (int oz = -CT_HALF; oz <= CT_HALF; oz++) {
                    int adx = Math.abs(ox), adz = Math.abs(oz);
                    int outer = Math.max(adx, adz);
                    if (outer < CT_HALF - 1) continue;     // полая внутри (с одной стенкой толщины)
                    Material mat;
                    boolean isCorner = adx == CT_HALF && adz == CT_HALF;
                    int yLocal = y - Y_BASE;
                    if (isCorner) {
                        mat = Material.POLISHED_BLACKSTONE;
                    } else if (yLocal % 8 == 0 && yLocal > 0) {
                        mat = Material.CHISELED_DEEPSLATE;
                    } else if (outer == CT_HALF && (adx + adz) % 2 == 0) {
                        mat = Material.DEEPSLATE_BRICKS;
                    } else {
                        mat = Material.POLISHED_BLACKSTONE_BRICKS;
                    }
                    painter.place(CX + ox, y, CZ + oz, mat);
                    count++;
                }
            }
        }
        // Высокие готические окна на 4 сторонах башни — пара длинных слотов.
        for (int side = 0; side < 4; side++) {
            int faceX = (side == 0) ? CT_HALF : (side == 2) ? -CT_HALF : 0;
            int faceZ = (side == 1) ? CT_HALF : (side == 3) ? -CT_HALF : 0;
            boolean alongZ = side == 0 || side == 2;
            for (int slot : new int[] { -2, 2 }) {
                int yBot = Y_BASE + 18, yTop = Y_BASE + 30;
                for (int y = yBot; y <= yTop; y++) {
                    int wx = CX + faceX + (alongZ ? 0 : slot);
                    int wz = CZ + faceZ + (alongZ ? slot : 0);
                    Material mat = (y == yTop) ? Material.AMETHYST_BLOCK
                            : Material.PURPLE_STAINED_GLASS;
                    painter.place(wx, y, wz, mat);
                    count++;
                }
            }
        }
        // Корона: парапет с зубцами на CT_BODY_TOP_Y+1.
        int parapetY = CT_BODY_TOP_Y + 1;
        for (int ox = -CT_HALF - 1; ox <= CT_HALF + 1; ox++) {
            for (int oz = -CT_HALF - 1; oz <= CT_HALF + 1; oz++) {
                int adx = Math.abs(ox), adz = Math.abs(oz);
                if (Math.max(adx, adz) != CT_HALF + 1) continue;
                Material mat = ((ox + oz) & 1) == 0 ? Material.POLISHED_BLACKSTONE
                        : Material.CHISELED_DEEPSLATE;
                painter.place(CX + ox, parapetY, CZ + oz, mat);
                count++;
            }
        }
        // 4 угловых пинакля на короне.
        for (int sx : new int[] { -CT_HALF - 1, CT_HALF + 1 }) {
            for (int sz : new int[] { -CT_HALF - 1, CT_HALF + 1 }) {
                for (int dy = 1; dy <= 6; dy++) {
                    Material mat = (dy == 6) ? Material.END_ROD
                            : (dy == 5) ? Material.LIGHTNING_ROD
                            : Material.CHISELED_DEEPSLATE;
                    painter.place(CX + sx, parapetY + dy, CZ + sz, mat);
                    count++;
                }
            }
        }
        // Шпиль 50 высотой: квадратный конус от CT_HALF до 0.
        for (int dy = 0; dy < CT_SPIRE_DY; dy++) {
            int y = CT_BODY_TOP_Y + 1 + dy;
            double t = (double) dy / (CT_SPIRE_DY - 1);
            int r = (int) Math.round((CT_HALF - 1) * (1.0 - t));
            if (r < 0) r = 0;
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    int outer = Math.max(Math.abs(ox), Math.abs(oz));
                    if (outer != r) continue; // только оболочка
                    Material mat;
                    boolean isCorner = Math.abs(ox) == r && Math.abs(oz) == r;
                    boolean isBand = (dy % 6 == 0);
                    if (isCorner) {
                        mat = Material.POLISHED_BLACKSTONE;
                    } else if (isBand) {
                        mat = Material.CHISELED_DEEPSLATE;
                    } else {
                        mat = Material.DEEPSLATE_BRICKS;
                    }
                    painter.place(CX + ox, y, CZ + oz, mat);
                    count++;
                }
            }
            if (r == 0) {
                painter.place(CX, y, CZ, Material.POLISHED_BLACKSTONE);
                count++;
            }
            // Маяки на рёбрах через каждые 10 блоков.
            if (dy > 4 && dy % 10 == 0 && r >= 2) {
                painter.place(CX + r, y, CZ + r, Material.END_ROD);
                painter.place(CX - r, y, CZ + r, Material.END_ROD);
                painter.place(CX + r, y, CZ - r, Material.END_ROD);
                painter.place(CX - r, y, CZ - r, Material.END_ROD);
                count += 4;
            }
        }
        // Венчающий LIGHTNING_ROD + END_ROD выше шпиля.
        painter.place(CX, CT_SPIRE_TOP_Y, CZ, Material.LIGHTNING_ROD);
        painter.place(CX, CT_SPIRE_TOP_Y + 1, CZ, Material.END_ROD);
        count += 2;
        return count;
    }

    // =========================================================================
    // ФАЗА 8: ДВЕ ЗАПАДНЫЕ (ЮЖНЫЕ ФАСАДНЫЕ) БАШНИ
    // =========================================================================

    private long buildWestTowers() {
        long count = 0;
        for (int side : new int[] { -1, +1 }) {
            int tx = CX + side * WT_X_OFFSET;
            int tz = CZ + WT_Z;
            count += buildOneSideTower(tx, tz, WT_HALF, WT_BODY_DY, WT_SPIRE_DY);
            // Флаг на пике.
            count += buildFlagPole(tx, WT_SPIRE_TOP_Y, tz);
        }
        return count;
    }

    /** Универсальная башня: квадрат 2*half+1, тело bodyDy, шпиль spireDy. */
    private long buildOneSideTower(int tx, int tz, int half, int bodyDy, int spireDy) {
        long count = 0;
        // Тело y=Y_BASE+1..WALL_TOP_Y+bodyDy.
        int bodyTopY = WALL_TOP_Y + bodyDy;
        for (int y = Y_BASE + 1; y <= bodyTopY; y++) {
            for (int ox = -half; ox <= half; ox++) {
                for (int oz = -half; oz <= half; oz++) {
                    int outer = Math.max(Math.abs(ox), Math.abs(oz));
                    if (outer < half) continue; // только оболочка
                    Material mat;
                    boolean isCorner = Math.abs(ox) == half && Math.abs(oz) == half;
                    int yLocal = y - Y_BASE;
                    if (isCorner) mat = Material.POLISHED_BLACKSTONE;
                    else if (yLocal % 7 == 0) mat = Material.CHISELED_DEEPSLATE;
                    else mat = ((ox + oz + yLocal) & 1) == 0
                            ? Material.DEEPSLATE_BRICKS
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    painter.place(tx + ox, y, tz + oz, mat);
                    count++;
                }
            }
        }
        // Узкие бойницы вверху.
        int slitY = bodyTopY - 6;
        for (int side = 0; side < 4; side++) {
            int dx = (side == 0) ? half : (side == 2) ? -half : 0;
            int dz = (side == 1) ? half : (side == 3) ? -half : 0;
            painter.place(tx + dx, slitY,     tz + dz, Material.PURPLE_STAINED_GLASS);
            painter.place(tx + dx, slitY + 1, tz + dz, Material.PURPLE_STAINED_GLASS);
            painter.place(tx + dx, slitY + 2, tz + dz, Material.AMETHYST_BLOCK);
            count += 3;
        }
        // Шпиль конусом.
        for (int dy = 0; dy < spireDy; dy++) {
            int y = bodyTopY + 1 + dy;
            double t = (double) dy / (spireDy - 1);
            int r = (int) Math.round(half * (1.0 - t));
            if (r < 0) r = 0;
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    int outer = Math.max(Math.abs(ox), Math.abs(oz));
                    if (outer != r) continue;
                    Material mat;
                    boolean isCorner = Math.abs(ox) == r && Math.abs(oz) == r;
                    if (isCorner) mat = Material.POLISHED_BLACKSTONE;
                    else if (dy % 4 == 0) mat = Material.CHISELED_DEEPSLATE;
                    else mat = Material.DEEPSLATE_BRICKS;
                    painter.place(tx + ox, y, tz + oz, mat);
                    count++;
                }
            }
            if (r == 0) {
                painter.place(tx, y, tz, Material.POLISHED_BLACKSTONE);
                count++;
            }
        }
        return count;
    }

    /** Полотно флага PURPLE_WOOL+треугольный хвост над пиком башни. */
    private long buildFlagPole(int tx, int topY, int tz) {
        long count = 0;
        // Флагшток — END_ROD ×3 над верхом шпиля.
        for (int dy = 1; dy <= 3; dy++) {
            painter.place(tx, topY + dy, tz, Material.END_ROD);
            count++;
        }
        // Полотно флага: 4 высокий, 5 широкий, на южной стороне шпиля.
        int flagY = topY - 4;
        for (int dy = 0; dy < 5; dy++) {
            int y = flagY + dy;
            int width = (dy == 0) ? 1 : (dy == 1) ? 3 : 5; // расширение книзу
            for (int oz = -width / 2; oz <= width / 2; oz++) {
                Material mat = (oz == 0) ? Material.MAGENTA_WOOL : Material.PURPLE_WOOL;
                painter.place(tx + 1, y, tz + oz, mat); // прижато к шпилю с восточной стороны
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 9: 4 УГЛОВЫХ ПИНАКЛЯ ПО КОНЦАМ КРЕСТА
    // =========================================================================

    private long buildCornerPinnacles() {
        long count = 0;
        // Точки: концы трансепта (E/W), концы нефа (N), плюс между ними при необходимости.
        int[][] coords = {
                { -HALF_TRANSEPT_W + 1,                   -HALF_TRANSEPT_L + 1 }, // SW transept
                { -HALF_TRANSEPT_W + 1,                    HALF_TRANSEPT_L - 1 }, // NW transept
                {  HALF_TRANSEPT_W - 1,                   -HALF_TRANSEPT_L + 1 }, // SE transept
                {  HALF_TRANSEPT_W - 1,                    HALF_TRANSEPT_L - 1 }, // NE transept
                { -HALF_NAVE_W + 1,                       -HALF_NAVE_L + 1 },     // NW apse
                {  HALF_NAVE_W - 1,                       -HALF_NAVE_L + 1 },     // NE apse
        };
        for (int[] c : coords) {
            int tx = CX + c[0];
            int tz = CZ + c[1];
            count += buildOneSideTower(tx, tz, CP_HALF, CP_BODY_DY, CP_SPIRE_DY);
            // Маяк сверху.
            painter.place(tx, WALL_TOP_Y + CP_BODY_DY + CP_SPIRE_DY + 1, tz,
                    Material.END_ROD);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 10: ПАРЯЩИЙ «ГЛАЗ ЭЛИКИЯ»
    // =========================================================================

    private long buildFloatingEye() {
        long count = 0;
        // Ядро: 3×3×3 куб AMETHYST_BLOCK с центром в (CX, EYE_Y, CZ),
        // углы — PURPUR_BLOCK для контраста.
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    int adx = Math.abs(ox), ady = Math.abs(oy), adz = Math.abs(oz);
                    int dist = adx + ady + adz;
                    Material mat;
                    if (dist == 0) {
                        mat = Material.BEACON; // ядро светится столпом вверх
                    } else if (dist == 3) {
                        mat = Material.PURPUR_BLOCK;
                    } else {
                        mat = Material.AMETHYST_BLOCK;
                    }
                    painter.place(CX + ox, EYE_Y + oy, CZ + oz, mat);
                    count++;
                }
            }
        }
        // 3 горизонтальных кольца END_ROD радиуса 4, 5, 4 на y-1, y, y+1.
        int[][] rings = {
                { EYE_Y - 1, 4 },
                { EYE_Y,     6 },
                { EYE_Y + 1, 4 },
        };
        for (int[] r : rings) {
            int ry = r[0];
            int rad = r[1];
            count += buildRing(CX, ry, CZ, rad);
        }
        // 4 длинных END_ROD-«копья» наружу от Глаза по 4 направлениям.
        BlockFace[] faces = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH };
        int[][] dirs = { {1, 0}, {-1, 0}, {0, -1}, {0, 1} };
        for (int i = 0; i < 4; i++) {
            for (int step = 7; step <= 10; step++) {
                BlockData rod = Material.END_ROD.createBlockData();
                if (rod instanceof Directional) {
                    ((Directional) rod).setFacing(faces[i]);
                }
                painter.placeData(CX + dirs[i][0] * step, EYE_Y, CZ + dirs[i][1] * step, rod);
                count++;
            }
        }
        // Подвешенная вертикальная свечная подсветка ниже Глаза (3 END_ROD вниз).
        for (int dy = 1; dy <= 3; dy++) {
            BlockData rod = Material.END_ROD.createBlockData();
            if (rod instanceof Directional) {
                ((Directional) rod).setFacing(BlockFace.DOWN);
            }
            painter.placeData(CX, EYE_Y - 1 - dy, CZ, rod);
            count++;
        }
        return count;
    }

    /** Кольцо из END_ROD на горизонтальной плоскости y. */
    private long buildRing(int cx, int y, int cz, int radius) {
        long count = 0;
        // Аппроксимация окружности на 32 шага.
        int steps = Math.max(16, radius * 6);
        boolean[][] placed = new boolean[radius * 2 + 3][radius * 2 + 3];
        for (int s = 0; s < steps; s++) {
            double a = 2.0 * Math.PI * s / steps;
            int dx = (int) Math.round(radius * Math.cos(a));
            int dz = (int) Math.round(radius * Math.sin(a));
            if (placed[dx + radius + 1][dz + radius + 1]) continue;
            placed[dx + radius + 1][dz + radius + 1] = true;
            painter.place(cx + dx, y, cz + dz, Material.END_ROD);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 11: ВНУТРЕННЕЕ ОСВЕЩЕНИЕ
    // =========================================================================

    private long buildInteriorLight() {
        long count = 0;
        // SHROOMLIGHT-«канделябры» по 6 точкам в нефе.
        int[] zs = { -32, -16, 0, 16, 32 };
        for (int dz : zs) {
            // Подвесная цепь от потолка нефа (y=119) до канделябра (y=Y_BASE+18).
            int hangX = CX, hangZ = CZ + dz;
            for (int y = Y_BASE + 19; y < ROOF_PEAK_Y; y++) {
                painter.place(hangX, y, hangZ, Material.CHAIN);
                count++;
            }
            painter.place(hangX, Y_BASE + 18, hangZ, Material.SHROOMLIGHT);
            count++;
        }
        // Алтарь в северной апсиде: 5×3 платформа PURPUR_BLOCK + AMETHYST в центре.
        int altZ = CZ - HALF_NAVE_L + 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material mat = (dx == 0 && dz == 0) ? Material.AMETHYST_BLOCK
                        : Material.PURPUR_BLOCK;
                painter.place(CX + dx, Y_BASE + 1, altZ + dz, mat);
                count++;
            }
        }
        // На алтарном AMETHYST — END_ROD (вертикальный).
        painter.place(CX, Y_BASE + 2, altZ, Material.END_ROD);
        painter.place(CX, Y_BASE + 3, altZ, Material.END_ROD);
        count += 2;
        return count;
    }
}
