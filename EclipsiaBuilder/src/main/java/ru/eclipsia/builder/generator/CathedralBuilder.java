package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Готический собор Эликий — главное здание города. Тёмная палитра
 * (DEEPSLATE_BRICKS / POLISHED_BLACKSTONE_BRICKS), фиолетовые витражи
 * со внутренней подсветкой, крестообразный план (cruciform), семь
 * остроконечных башен и парящий «Глаз Эликия» над центральным шпилем.
 *
 * <p><b>Геометрия (PR 3.5/3.6)</b>:
 * <ul>
 *   <li>Центр: ({@link WorldGenerator#CATHEDRAL_X}, {@link WorldGenerator#CATHEDRAL_Z})
 *       = (45, -15).</li>
 *   <li>Cruciform: неф ±15 × ±42 (вдоль Z), трансепт ±30 × ±7 (вдоль X).</li>
 *   <li>Стены y=70..101 (высота 32), толщина 1 блок, вогнутые углы
 *       cruciform ​дополнительно уплотнены (PR 3.6) — закрывают «дыры».</li>
 *   <li>Двускатная крыша до y=119, гребень y=120 POLISHED_BLACKSTONE.</li>
 *   <li>Башни: 1 центральная (11×11, шпиль до y=186), 2 фасадные
 *       южные (7×7, шпили до y=143), 4 пинакля по концам креста
 *       (5×5, шпили до y=130).</li>
 *   <li>Парящий «Глаз Эликия» — целиком из частиц на y≈201 (PR 3.6:
 *       блочный куб удалён, оставлен только {@link Material#LIGHT}-куб для
 *       подсветки воздуха; реальный глаз рисуется в
 *       {@link SpireParticles}).</li>
 *   <li>Декор интерьера: 6 колонн вдоль нефа, 5 свисающих канделябров,
 *       4 ряда скамей DARK_OAK_SLAB, пюпитр на пересечении, алтарь
 *       в северной апсиде, гаргульи на центральной башне (PR 3.6).</li>
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
        // PR 3.6 — декор
        ops += buildPortalStatues();
        ops += buildInteriorColumns();
        ops += buildPews();
        ops += buildPulpit();
        ops += buildBell();
        ops += buildGargoyles();
        ops += buildVaultRibs();
        // PR 3.7 — двери в портале
        ops += buildSouthDoors();

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

    /**
     * Точка лежит на внешнем периметре footprint (есть сосед-снаружи).
     * <p>В PR 3.6 проверка дополнена диагональными соседями — это закрывает
     * «дыры» в вогнутых углах cruciform (там, где неф встречается с
     * трансептом и две стены подходят диагонально).
     */
    private boolean isPerimeter(int dx, int dz) {
        if (!inFootprint(dx, dz)) return false;
        // Cardinal-соседи (стандартный периметр).
        if (!inFootprint(dx - 1, dz) || !inFootprint(dx + 1, dz)
            || !inFootprint(dx, dz - 1) || !inFootprint(dx, dz + 1)) {
            return true;
        }
        // Диагональные соседи — обработка вогнутых углов cruciform.
        return !inFootprint(dx - 1, dz - 1) || !inFootprint(dx + 1, dz - 1)
            || !inFootprint(dx - 1, dz + 1) || !inFootprint(dx + 1, dz + 1);
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

    /**
     * PR 3.7: переделанная крыша. Прежняя имела:
     * <ul>
     *   <li><b>Разрыв 3 блока</b> между скатом (max y=117) и гребнем
     *       (y=120): {@code ROOF_PEAK_DY=18}, но скат шёл только до
     *       {@code rise=15}, после чего {@code dxAt<0} ломал цикл.</li>
     *   <li><b>Дыры на пересечении нефа и трансепта</b>: skip-условие
     *       пропускало нефовый скат над всем трансептом, а трансептовый
     *       скат не покрывал внутри нефа.</li>
     *   <li>Цвет DEEPSLATE_TILES (серая черепица) — на референсе крыша
     *       тёмно-коричневая, как DARK_OAK_LOG.</li>
     * </ul>
     * <p>Теперь:
     * <ul>
     *   <li>Скат идёт {@code rise=0..HALF_NAVE_W=15} → пик слипается с
     *       гребнем (y=117 для скатов, y=118 для гребня — соприкасаются).</li>
     *   <li>Скат нефа покрывает весь {@code z=-42..42} (skip убран).</li>
     *   <li>Трансептовый скат покрывает только {@code |x|>HALF_NAVE_W}.</li>
     *   <li>Материал — DARK_OAK_LOG (тёмно-коричневый).</li>
     * </ul>
     */
    private long buildRoof() {
        long count = 0;
        Material roofMat = Material.DARK_OAK_LOG;
        Material ridgeMat = Material.POLISHED_BLACKSTONE;

        // ===== Крыша нефа: вдоль Z, гребень при x=CX =====
        // rise=0..15 (HALF_NAVE_W). dxAt=15..0. Пик слипается с гребнем.
        for (int rise = 0; rise <= HALF_NAVE_W; rise++) {
            int y = WALL_TOP_Y + rise;
            int dxAt = HALF_NAVE_W - rise;
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                painter.place(CX - dxAt, y, CZ + dz, roofMat);
                painter.place(CX + dxAt, y, CZ + dz, roofMat);
                count += 2;
            }
        }
        // ===== Жирность нефовой крыши: ещё один слой "ниже" под скатом =====
        // Это даёт визуальную толщину 2 блока, как в готике.
        for (int rise = 1; rise <= HALF_NAVE_W; rise++) {
            int y = WALL_TOP_Y + rise;
            int dxAt = HALF_NAVE_W - rise + 1;
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                painter.place(CX - dxAt, y, CZ + dz, roofMat);
                painter.place(CX + dxAt, y, CZ + dz, roofMat);
                count += 2;
            }
        }
        // ===== Гребень нефа на y=WALL_TOP_Y+HALF_NAVE_W+1=118 =====
        int naveRidgeY = WALL_TOP_Y + HALF_NAVE_W + 1;
        for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
            // Не дублировать там, где центральная башня — она пробьёт крышу.
            if (Math.abs(dz) <= CT_HALF) continue;
            painter.place(CX, naveRidgeY, CZ + dz, ridgeMat);
            count++;
        }

        // ===== Крыша трансепта: вдоль X, гребень при z=CZ =====
        // rise=0..7 (HALF_TRANSEPT_L). Покрывает только |x|>HALF_NAVE_W
        // (внутри нефа крыша нефа покрывает с большей высотой).
        for (int rise = 0; rise <= HALF_TRANSEPT_L; rise++) {
            int y = WALL_TOP_Y + rise;
            int dzAt = HALF_TRANSEPT_L - rise;
            for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
                if (Math.abs(dx) <= HALF_NAVE_W) continue;
                painter.place(CX + dx, y, CZ - dzAt, roofMat);
                painter.place(CX + dx, y, CZ + dzAt, roofMat);
                count += 2;
            }
        }
        // ===== Жирность трансепта =====
        for (int rise = 1; rise <= HALF_TRANSEPT_L; rise++) {
            int y = WALL_TOP_Y + rise;
            int dzAt = HALF_TRANSEPT_L - rise + 1;
            for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
                if (Math.abs(dx) <= HALF_NAVE_W) continue;
                painter.place(CX + dx, y, CZ - dzAt, roofMat);
                painter.place(CX + dx, y, CZ + dzAt, roofMat);
                count += 2;
            }
        }
        // ===== Гребень трансепта на y=WALL_TOP_Y+HALF_TRANSEPT_L+1=110 =====
        int transeptRidgeY = WALL_TOP_Y + HALF_TRANSEPT_L + 1;
        for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
            if (Math.abs(dx) <= HALF_NAVE_W) continue;
            painter.place(CX + dx, transeptRidgeY, CZ, ridgeMat);
            count++;
        }

        // ===== Декоративные SHROOMLIGHT-«окошки» на скатах =====
        // Каждые 14 блоков по нефу — мансардные окна.
        for (int dz : new int[] { -32, -18, 18, 32 }) {
            for (int side : new int[] { -1, +1 }) {
                int rise = 6;
                int y = WALL_TOP_Y + rise;
                int x = CX + side * (HALF_NAVE_W - rise);
                painter.place(x, y, CZ + dz, Material.SHROOMLIGHT);
                count++;
            }
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
            int beaconY = WALL_TOP_Y + CP_BODY_DY + CP_SPIRE_DY + 1;
            painter.place(tx, beaconY, tz, Material.END_ROD);
            count++;
            // PR 3.7: флаг на каждом пинакле (вешаем на восточную сторону).
            count += buildFlagPole(tx, beaconY, tz);
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 10: ПАРЯЩИЙ «ГЛАЗ ЭЛИКИЯ»
    // =========================================================================

    /**
     * PR 3.6: «Глаз Эликия» теперь полностью на частицах. Сюда
     * заходим только чтобы расставить невидимые {@link Material#LIGHT}
     * блоки для атмосферного свечения воздуха и стереть возможные
     * остатки старого блочного куба.
     */
    private long buildFloatingEye() {
        long count = 0;
        // Очищаем 5×5×5 область вокруг Глаза (на случай старого мира v11).
        for (int ox = -2; ox <= 2; ox++) {
            for (int oy = -2; oy <= 2; oy++) {
                for (int oz = -2; oz <= 2; oz++) {
                    painter.place(CX + ox, EYE_Y + oy, CZ + oz, Material.AIR);
                    count++;
                }
            }
        }
        // Стираем «копья» по 4 сторонам и нижние END_ROD (старый блочный Глаз).
        for (int step = 0; step <= 10; step++) {
            painter.place(CX + step, EYE_Y, CZ, Material.AIR);
            painter.place(CX - step, EYE_Y, CZ, Material.AIR);
            painter.place(CX, EYE_Y, CZ + step, Material.AIR);
            painter.place(CX, EYE_Y, CZ - step, Material.AIR);
            count += 4;
        }
        for (int dy = 1; dy <= 5; dy++) {
            painter.place(CX, EYE_Y - dy, CZ, Material.AIR);
            count++;
        }
        // Невидимые LIGHT-блоки в центре + 4 вокруг — даёт fluctuating
        // свечение в воздухе вокруг Глаза без видимого каркаса.
        BlockData light = Material.LIGHT.createBlockData("[level=15]");
        painter.placeData(CX, EYE_Y, CZ, light);
        painter.placeData(CX + 1, EYE_Y, CZ, light);
        painter.placeData(CX - 1, EYE_Y, CZ, light);
        painter.placeData(CX, EYE_Y + 1, CZ, light);
        painter.placeData(CX, EYE_Y - 1, CZ, light);
        count += 5;
        return count;
    }

    // =========================================================================
    // ФАЗА 11: ВНУТРЕННЕЕ ОСВЕЩЕНИЕ
    // =========================================================================

    // =========================================================================
    // ФАЗА 12 (PR 3.6): СТАТУИ-АТЛАНТЫ У ЮЖНОГО ПОРТАЛА
    // =========================================================================

    /**
     * PR 3.7: переделаны на массивные пьедесталы 5×5 с жаровней SOUL_FIRE
     * наверху (как фиолетовые огни на референсе у входа). Прежняя версия
     * 3.6 (колонна 1×1×6 + череп) визуально была фаллической.
     */
    private long buildPortalStatues() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L + 4; // на 1 блок южнее последней ступени
        for (int side : new int[] { -1, +1 }) {
            int sx = CX + side * 11;
            // ===== Уровень 1 (y=Y_BASE+1): база 5×5 POLISHED_BLACKSTONE =====
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    painter.place(sx + dx, Y_BASE + 1, absZ + dz, Material.POLISHED_BLACKSTONE);
                    count++;
                }
            }
            // ===== Уровень 2 (y=Y_BASE+2): 5×5 POLISHED_BLACKSTONE_BRICKS =====
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    painter.place(sx + dx, Y_BASE + 2, absZ + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    count++;
                }
            }
            // ===== Уровень 3 (y=Y_BASE+3): 3×3 DEEPSLATE_BRICKS (карниз) =====
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    painter.place(sx + dx, Y_BASE + 3, absZ + dz, Material.DEEPSLATE_BRICKS);
                    count++;
                }
            }
            // ===== Уровень 4 (y=Y_BASE+4): 3×3 DEEPSLATE_BRICKS (тело) =====
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    painter.place(sx + dx, Y_BASE + 4, absZ + dz, Material.DEEPSLATE_BRICKS);
                    count++;
                }
            }
            // ===== Уровень 5 (y=Y_BASE+5): 3×3 CHISELED_DEEPSLATE (капитель) =====
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    painter.place(sx + dx, Y_BASE + 5, absZ + dz, Material.CHISELED_DEEPSLATE);
                    count++;
                }
            }
            // ===== Жаровня (y=Y_BASE+6): SOUL_FIRE на NETHERITE, по углам DARK_OAK_FENCE =====
            painter.place(sx, Y_BASE + 6, absZ, Material.NETHERITE_BLOCK);
            count++;
            for (int[] off : new int[][] { {-1, -1}, {-1, 1}, {1, -1}, {1, 1} }) {
                painter.place(sx + off[0], Y_BASE + 6, absZ + off[1], Material.DARK_OAK_FENCE);
                count++;
            }
            // Само пламя — SOUL_FIRE на y+7.
            painter.place(sx, Y_BASE + 7, absZ, Material.SOUL_FIRE);
            count++;
            // Концы FENCE на y+7 — END_ROD на каждом столбе для доп. подсветки.
            for (int[] off : new int[][] { {-1, -1}, {-1, 1}, {1, -1}, {1, 1} }) {
                painter.place(sx + off[0], Y_BASE + 7, absZ + off[1], Material.DARK_OAK_FENCE);
                painter.place(sx + off[0], Y_BASE + 8, absZ + off[1], Material.END_ROD);
                count += 2;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 12.5 (PR 3.7): ДВЕРИ ЮЖНОГО ПОРТАЛА
    // =========================================================================

    /**
     * Двойная DARK_OAK_DOOR в южном портале. Проём 9×16, ставим 2 двери
     * (dx=-1 и dx=+1, перед ними AIR-проход). Двери смотрят на юг
     * (наружу), {@link Door#setHinge}: левая=LEFT, правая=RIGHT.
     */
    private long buildSouthDoors() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L; // z южной стены
        int doorY = Y_BASE + 1;
        for (int side : new int[] { -1, +1 }) {
            int dx = side; // -1 (левая), +1 (правая)
            // Нижняя половина.
            BlockData lower = Material.DARK_OAK_DOOR.createBlockData();
            if (lower instanceof Door) {
                Door dd = (Door) lower;
                dd.setHalf(Bisected.Half.BOTTOM);
                dd.setHinge(side == -1 ? Door.Hinge.LEFT : Door.Hinge.RIGHT);
                dd.setFacing(BlockFace.SOUTH);
            }
            painter.placeData(CX + dx, doorY, absZ, lower);
            // Верхняя половина.
            BlockData upper = Material.DARK_OAK_DOOR.createBlockData();
            if (upper instanceof Door) {
                Door dd = (Door) upper;
                dd.setHalf(Bisected.Half.TOP);
                dd.setHinge(side == -1 ? Door.Hinge.LEFT : Door.Hinge.RIGHT);
                dd.setFacing(BlockFace.SOUTH);
            }
            painter.placeData(CX + dx, doorY + 1, absZ, upper);
            count += 2;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 13 (PR 3.6): КОЛОННАДА ВНУТРИ НЕФА
    // =========================================================================

    /**
     * 6 колонн (3 пары) вдоль нефа — поддерживают «своды» (визуальные
     * ребра от стен к гребню крыши).
     */
    private long buildInteriorColumns() {
        long count = 0;
        int[] zs = { -28, -10, 8, 28 };
        for (int dz : zs) {
            for (int side : new int[] { -1, +1 }) {
                int cx = CX + side * (HALF_NAVE_W - 4); // x=±11
                int cz = CZ + dz;
                // Базовая часть 1×1, h=1: COBBLED_DEEPSLATE.
                painter.place(cx, Y_BASE + 1, cz, Material.COBBLED_DEEPSLATE);
                count++;
                // Тело колонны y=72..91 PURPUR_PILLAR.
                for (int dy = 2; dy <= 21; dy++) {
                    BlockData pillar = Material.PURPUR_PILLAR.createBlockData();
                    painter.placeData(cx, Y_BASE + dy, cz, pillar);
                    count++;
                }
                // Капитель y=92..93 CHISELED_DEEPSLATE.
                painter.place(cx, Y_BASE + 22, cz, Material.CHISELED_DEEPSLATE);
                painter.place(cx, Y_BASE + 23, cz, Material.POLISHED_BLACKSTONE);
                count += 2;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 14 (PR 3.6): СКАМЬИ
    // =========================================================================

    private long buildPews() {
        long count = 0;
        // 8 рядов скамей по обе стороны от центрального прохода (dx=±2).
        for (int dz : new int[] { -34, -30, -22, -18, 18, 22, 30, 34 }) {
            for (int side : new int[] { -1, +1 }) {
                // Сидение DARK_OAK_SLAB y=Y_BASE+1, длина 5 блоков.
                for (int dx = side == -1 ? -7 : 3; dx <= (side == -1 ? -3 : 7); dx++) {
                    Slab slab = (Slab) Material.DARK_OAK_SLAB.createBlockData();
                    slab.setType(Slab.Type.BOTTOM);
                    painter.placeData(CX + dx, Y_BASE + 1, CZ + dz, slab);
                    count++;
                }
                // Спинка DARK_OAK_FENCE y=Y_BASE+2 на дальнем конце скамьи.
                int backDx = side == -1 ? -7 : 7;
                for (int dx = backDx - (side == -1 ? -4 : 4);
                     dx <= backDx;
                     dx++) {
                    if (dx == backDx || (side == -1 && dx == backDx + 4)
                        || (side == 1 && dx == backDx - 4)) {
                        painter.place(CX + dx, Y_BASE + 2, CZ + dz, Material.DARK_OAK_FENCE);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 15 (PR 3.6): ПЮПИТР НА ПЕРЕСЕЧЕНИИ
    // =========================================================================

    private long buildPulpit() {
        long count = 0;
        // Круглая платформа r=2 в (CX, Y_BASE+2, CZ), 2 ступени.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist > 3) continue;
                Material mat;
                if (dist == 0) mat = Material.AMETHYST_BLOCK;
                else if (dist == 1) mat = Material.PURPUR_PILLAR;
                else mat = Material.PURPUR_BLOCK;
                painter.place(CX + dx, Y_BASE + 1, CZ + dz, mat);
                count++;
            }
        }
        // Над пюпитром — END_ROD «лектор», подсвечивающий проповедника.
        painter.place(CX, Y_BASE + 2, CZ, Material.END_ROD);
        count++;
        // Дополнительные SHROOMLIGHT по 4 углам у крестовой башни.
        for (int sx : new int[] { -3, 3 }) {
            for (int sz : new int[] { -3, 3 }) {
                painter.place(CX + sx, Y_BASE + 2, CZ + sz, Material.SHROOMLIGHT);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 16 (PR 3.6): КОЛОКОЛ НА ЗАПАДНОЙ ФАСАДНОЙ БАШНЕ
    // =========================================================================

    private long buildBell() {
        long count = 0;
        // Колокол ставим в окне западной башни (запад = -X).
        int tx = CX - WT_X_OFFSET;
        int tz = CZ + WT_Z;
        int by = WALL_TOP_Y + WT_BODY_DY - 4;
        Bell bell = (Bell) Material.BELL.createBlockData();
        bell.setAttachment(Bell.Attachment.CEILING);
        bell.setFacing(BlockFace.SOUTH);
        painter.placeData(tx, by, tz, bell);
        count++;
        // Цепь над колоколом 2 блока.
        painter.place(tx, by + 1, tz, Material.CHAIN);
        painter.place(tx, by + 2, tz, Material.CHAIN);
        count += 2;
        return count;
    }

    // =========================================================================
    // ФАЗА 17 (PR 3.6): ГАРГУЛЬИ НА УГЛАХ ЦЕНТРАЛЬНОЙ БАШНИ
    // =========================================================================

    private long buildGargoyles() {
        long count = 0;
        int gy = CT_BODY_TOP_Y + 1;
        int half = CT_HALF + 2;
        int[][] corners = {
                { -half, -half }, { -half, half },
                {  half, -half }, {  half, half },
        };
        BlockFace[] outwardFaces = {
                BlockFace.WEST, BlockFace.WEST,
                BlockFace.EAST, BlockFace.EAST,
        };
        for (int i = 0; i < 4; i++) {
            int gx = CX + corners[i][0];
            int gz = CZ + corners[i][1];
            // Выносная балка-кронштейн POLISHED_BLACKSTONE_STAIRS.
            Stairs bracket = (Stairs) Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
            bracket.setFacing(outwardFaces[i].getOppositeFace());
            bracket.setHalf(Bisected.Half.TOP);
            painter.placeData(gx, gy - 1, gz, bracket);
            count++;
            // Череп смотрит наружу.
            BlockData skull = Material.WITHER_SKELETON_SKULL.createBlockData();
            if (skull instanceof Rotatable) {
                ((Rotatable) skull).setRotation(outwardFaces[i]);
            }
            painter.placeData(gx, gy, gz, skull);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 18 (PR 3.6): СВОДЫ-РЕБРА
    // =========================================================================

    /**
     * Декоративные DARK_OAK_FENCE-ребра от стен нефа к коньку крыши,
     * имитирующие готические нервюры. Не структурные, чисто визуально.
     */
    private long buildVaultRibs() {
        long count = 0;
        int[] ribZs = { -36, -22, -10, 0, 10, 22, 36 };
        for (int dz : ribZs) {
            // Не рисуем над трансептом и центральной башней.
            if (Math.abs(dz) <= HALF_TRANSEPT_L) continue;
            int z = CZ + dz;
            // От пары «капителей» (y=Y_BASE+24) на (CX±11, z) к коньку (CX, ROOF_PEAK_Y-1, z).
            for (int side : new int[] { -1, +1 }) {
                int x0 = CX + side * (HALF_NAVE_W - 4);
                int x1 = CX;
                int y0 = Y_BASE + 24;
                int y1 = ROOF_PEAK_Y - 1;
                int steps = Math.abs(x1 - x0);
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    int x = (int) Math.round(x0 + (x1 - x0) * t);
                    int y = (int) Math.round(y0 + (y1 - y0) * t);
                    painter.place(x, y, z, Material.DARK_OAK_FENCE);
                    count++;
                }
            }
        }
        return count;
    }

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
