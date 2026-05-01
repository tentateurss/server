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

    /** Парящий Глаз над центральной башней. PR 3.8 (v14): 14→24, Глаз
     *  на y=211 (был y=201), выше из-за большего ×1.6 миндаля. */
    private static final int EYE_Y_OFFSET  = 24;
    private static final int EYE_Y         = CT_SPIRE_TOP_Y + EYE_Y_OFFSET;     // y=211

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
        // PR 3.8 — фиксы по фото-референсу:
        ops += buildGableCaps();           // закрыть дыры в торцах нефа/трансепта
        ops += buildSouthFacadeDecor();    // окно-роза + часы на южном фронтоне
        ops += buildEyeColumns();          // 4 END_ROD-колонны под Глазом (видны издалека)
        ops += buildCentralStandard();     // 4 баннера-штандарта ниже Глаза
        ops += buildExteriorSconces();     // SOUL_LANTERN-сконсы на фасаде
        ops += buildAmethystVeins();       // аметистовые жилы на контрфорсах
        ops += buildBaseGardens();         // цветочные клумбы по периметру

        // PR 3.9 — полировка по фидбэку (крыша-дыра, Глаз жирнее, интерьер, наружный декор):
        ops += buildRoofValleys();         // долины на 4 внутренних углах cruciform
        ops += buildAltar();               // 3-ступенчатый алтарь в апсиде с реликварием
        ops += buildChandeliers();         // 5 люстр-кандел SHROOMLIGHT над нефом
        ops += buildCarpet();              // RED_CARPET от южного портала к алтарю
        ops += buildChoir();               // хорные скамьи у апсиды
        ops += buildVaultRibsTop();        // ребристые своды из потолка к колоннам
        ops += buildFlyingButtresses();    // диагональные арки от пинаклей к нефу
        ops += buildSpireCrockets();       // декоративные крокеты END_ROD на шпиле
        ops += buildCornices();            // карнизы POLISHED_BLACKSTONE_BRICK_STAIRS
        ops += buildTrifoliumArcade();     // 3 узких арочки на торцах трансепта

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
            // PR 3.8: вместо квадратного wool-полотна — настоящий геральдический
            // штандарт на BLACKSTONE_WALL-древке (черный фон + фиолетовый
            // крест + золотые окантовки).
            count += buildBigBanner(tx, WT_SPIRE_TOP_Y, tz);
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

    // PR 3.8: старый buildFlagPole удалён — вызываются buildBigBanner /
    // buildSpirelet (см. ниже).

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
            // PR 3.8: вместо квадратного wool-флага — компактный флюгер-спирелет.
            count += buildSpirelet(tx, beaconY, tz);
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
        // PR 3.8: область расширена до ±5 и высоты 8 (×1.6 миндаля).
        for (int ox = -5; ox <= 5; ox++) {
            for (int oy = -8; oy <= 8; oy++) {
                for (int oz = -5; oz <= 5; oz++) {
                    painter.place(CX + ox, EYE_Y + oy, CZ + oz, Material.AIR);
                    count++;
                }
            }
        }
        // Стираем «копья» по 4 сторонам и нижние END_ROD (старый блочный Глаз).
        for (int step = 0; step <= 14; step++) {
            painter.place(CX + step, EYE_Y, CZ, Material.AIR);
            painter.place(CX - step, EYE_Y, CZ, Material.AIR);
            painter.place(CX, EYE_Y, CZ + step, Material.AIR);
            painter.place(CX, EYE_Y, CZ - step, Material.AIR);
            count += 4;
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

    // =========================================================================
    // ФАЗА 19 (PR 3.8): ФРОНТОНЫ — ЗАКРЫВАЮТ ДЫРЫ В ТОРЦАХ НЕФА И ТРАНСЕПТА
    // =========================================================================

    /**
     * Старый {@link #buildRoof()} оставлял ТРЕУГОЛЬНЫЕ ДЫРЫ на 4 торцах
     * cruciform (юг/север нефа на z=±42, восток/запад трансепта на x=±30):
     * скаты сходятся к гребню над пустым воздухом, потому что между
     * перекрытием стены (y=102) и крышей внутри торца ничего не клалось.
     *
     * <p>Этот метод заполняет 4 фронтона (gable caps) как в готической
     * архитектуре — однотолщинная диафрагма из DEEPSLATE_BRICKS /
     * POLISHED_BLACKSTONE_BRICKS, повторяющая профиль ската.
     */
    private long buildGableCaps() {
        long count = 0;
        // ===== Южный и северный фронтоны нефа (z=±42) =====
        // Скат нефа: rise=0..15, dxAt=15-rise. Заполнить ПОЛНЫЙ профиль
        // (включая диагональ x=±dxAt) — без этого с торцов оставались
        // видны треугольники неба сбоку от фронтона (PR 3.8 → 3.9 фикс).
        for (int signZ : new int[] { -1, +1 }) {
            int gableZ = CZ + signZ * HALF_NAVE_L;
            for (int rise = 0; rise <= HALF_NAVE_W; rise++) {
                int y = WALL_TOP_Y + rise;
                int dxAt = HALF_NAVE_W - rise;
                // ox = -dxAt..+dxAt (полный профиль, включая края — перекроет крышу
                // в плоскости z=gableZ, что архитектурно правильно: фронтон каменный,
                // деревянный скат — внутри, за фронтоном).
                for (int ox = -dxAt; ox <= dxAt; ox++) {
                    Material mat = ((ox + rise) & 1) == 0
                            ? Material.DEEPSLATE_BRICKS
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    painter.place(CX + ox, y, gableZ, mat);
                    count++;
                }
            }
            // Доп. ряд y=WALL_TOP_Y+HALF_NAVE_W+1=118 над пиком фронтона —
            // 1 блок ridge-cap на коньке (DEEPSLATE_BRICK_WALL) на коньке.
            painter.place(CX, WALL_TOP_Y + HALF_NAVE_W, gableZ, Material.POLISHED_BLACKSTONE);
            count++;
        }
        // ===== Восточный и западный фронтоны трансепта (x=±30) =====
        // Скат трансепта: rise=0..7, dzAt=7-rise. Полный профиль включая края.
        for (int signX : new int[] { -1, +1 }) {
            int gableX = CX + signX * HALF_TRANSEPT_W;
            for (int rise = 0; rise <= HALF_TRANSEPT_L; rise++) {
                int y = WALL_TOP_Y + rise;
                int dzAt = HALF_TRANSEPT_L - rise;
                for (int oz = -dzAt; oz <= dzAt; oz++) {
                    Material mat = ((oz + rise) & 1) == 0
                            ? Material.DEEPSLATE_BRICKS
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    painter.place(gableX, y, CZ + oz, mat);
                    count++;
                }
            }
            // Ridge-cap на коньке трансептовой крыши.
            painter.place(gableX, WALL_TOP_Y + HALF_TRANSEPT_L, CZ, Material.POLISHED_BLACKSTONE);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 20 (PR 3.8): ДЕКОР ЮЖНОГО ФАСАДА — ОКНО-РОЗА + ЧАСЫ
    // =========================================================================

    /**
     * На южном фронтоне (z=CZ+HALF_NAVE_L=27) ставим:
     * <ul>
     *   <li>Окно-розу d=7 (PURPLE_STAINED_GLASS / MAGENTA_STAINED_GLASS /
     *       AMETHYST_BLOCK) на y=113 — выше часов, под коньком;</li>
     *   <li>Готические часы d=5 (GOLD_BLOCK + CHISELED_QUARTZ_BLOCK +
     *       POLISHED_BLACKSTONE-стрелки на 1:15) на y=107 — над портальной
     *       аркой, в центре фронтона.</li>
     * </ul>
     */
    private long buildSouthFacadeDecor() {
        long count = 0;
        int gableZ = CZ + HALF_NAVE_L;
        // ===== Окно-роза d=7, центр (CX, 113, gableZ) =====
        int roseY = WALL_TOP_Y + 11; // y=113
        for (int ox = -3; ox <= 3; ox++) {
            for (int dy = -3; dy <= 3; dy++) {
                int dist = Math.max(Math.abs(ox), Math.abs(dy));
                if (dist > 3) continue;
                Material mat;
                if (dist == 3) mat = Material.POLISHED_BLACKSTONE;
                else if (dist == 2) mat = Material.MAGENTA_STAINED_GLASS;
                else if (dist == 1) mat = Material.PURPLE_STAINED_GLASS;
                else mat = Material.AMETHYST_BLOCK;
                painter.place(CX + ox, roseY + dy, gableZ, mat);
                count++;
            }
        }
        // Подсветка изнутри розы: 2 SHROOMLIGHT за стеклом.
        painter.place(CX, roseY, gableZ - 1, Material.SHROOMLIGHT);
        painter.place(CX, roseY - 1, gableZ - 1, Material.SHROOMLIGHT);
        count += 2;

        // ===== Часы d=5, центр (CX, 107, gableZ) =====
        // Frame ring (Chebyshev=2): GOLD_BLOCK на 4 стороны (без углов).
        // Outer ring (Chebyshev=1): CHISELED_QUARTZ_BLOCK циферблат.
        // Hands: POLISHED_BLACKSTONE на 1:15 (часовая вверх, минутная вправо).
        int clockY = WALL_TOP_Y + 5; // y=107
        for (int ox = -2; ox <= 2; ox++) {
            for (int dy = -2; dy <= 2; dy++) {
                int adx = Math.abs(ox), ady = Math.abs(dy);
                int dist = Math.max(adx, ady);
                if (dist > 2) continue;
                Material mat;
                if (dist == 2) {
                    // Углы (adx=2, ady=2) — пропускаем (срез — придаёт круглость).
                    if (adx == 2 && ady == 2) continue;
                    mat = Material.GOLD_BLOCK;
                } else {
                    mat = Material.CHISELED_QUARTZ_BLOCK;
                }
                painter.place(CX + ox, clockY + dy, gableZ, mat);
                count++;
            }
        }
        // Стрелки (рисуем поверх циферблата).
        // Часовая стрелка вверх (12 → почти 1): (CX, clockY+1, gableZ).
        painter.place(CX, clockY + 1, gableZ, Material.POLISHED_BLACKSTONE);
        // Минутная стрелка вправо (15 минут): (CX+1, clockY, gableZ).
        painter.place(CX + 1, clockY, gableZ, Material.POLISHED_BLACKSTONE);
        // Центральная ось.
        painter.place(CX, clockY, gableZ, Material.POLISHED_BLACKSTONE);
        count += 3;
        return count;
    }

    // =========================================================================
    // ФАЗА 21 (PR 3.8): 4 БЛОЧНЫЕ END_ROD-КОЛОННЫ ПОД ГЛАЗОМ
    // =========================================================================

    /**
     * Чтобы Глаз был ВИДЕН ИЗ ЛЮБОЙ ТОЧКИ ГОРОДА (±150 блоков), без
     * жёсткого луча-маяка. END_ROD как блок виден на любом расстоянии
     * (это блок, не частица), поэтому 4 вертикальные колонны от вершины
     * центральной башни до Глаза дают надёжный визуальный «постамент»,
     * даже если игрок стоит за view-distance частиц.
     *
     * <p>Колонны от y=CT_SPIRE_TOP_Y+1 до EYE_Y-1 на (CX±2, CZ±2).
     */
    private long buildEyeColumns() {
        long count = 0;
        int yStart = CT_SPIRE_TOP_Y + 1;   // y=188
        int yEnd   = EYE_Y - 1;            // y=210
        int[][] cornerOffsets = { { -2, -2 }, { -2, 2 }, { 2, -2 }, { 2, 2 } };
        for (int[] off : cornerOffsets) {
            for (int y = yStart; y <= yEnd; y++) {
                painter.place(CX + off[0], y, CZ + off[1], Material.END_ROD);
                count++;
            }
        }
        // На вершине каждой колонны — LIGHTNING_ROD как «копьё», чуть ниже
        // плоскости Глаза — придаёт ощущение «алтаря-постамента».
        for (int[] off : cornerOffsets) {
            painter.place(CX + off[0], EYE_Y, CZ + off[1], Material.LIGHTNING_ROD);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 22 (PR 3.8): БОЛЬШОЙ ШТАНДАРТ — заменяет старый buildFlagPole
    // =========================================================================

    /**
     * Геральдический штандарт (4 ширины × 8 высоты) на BLACKSTONE_WALL-древке
     * с поперечиной над пиком башни. Цветовая схема — чёрный фон с
     * фиолетовым крестом и золотыми окантовками (тёмная готика).
     *
     * <p>Древко: 7 BLACKSTONE_WALL над пиком + END_ROD на верху + 5 поперечина.
     * Полотно: 4×8 wool-блоков (BLACK / PURPLE / GOLD), хвост-«ласточкин»:
     * 4 PURPLE_STAINED_GLASS_PANE по 2 на каждой кромке.
     */
    private long buildBigBanner(int tx, int topY, int tz) {
        long count = 0;
        // ===== ДРЕВКО (вертикальный шест над пиком) =====
        for (int dy = 1; dy <= 8; dy++) {
            painter.place(tx, topY + dy, tz, Material.POLISHED_BLACKSTONE_WALL);
            count++;
        }
        // Венчающий END_ROD над верхом древка.
        painter.place(tx, topY + 9, tz, Material.END_ROD);
        count++;

        // ===== ПОПЕРЕЧИНА (горизонтальная балка на восток) =====
        // 5 блоков POLISHED_BLACKSTONE_WALL на y=topY+8, z=tz+1..tz+5.
        int crossY = topY + 8;
        for (int oz = 1; oz <= 5; oz++) {
            painter.place(tx, crossY, tz + oz, Material.POLISHED_BLACKSTONE_WALL);
            count++;
        }

        // ===== ПОЛОТНО ШТАНДАРТА (4 широкое × 8 высокое) =====
        // Висит под поперечиной: y=crossY-1..crossY-8, z=tz+1..tz+4.
        // Цветовая раскладка (col 1..4 слева направо, row 1..8 сверху вниз):
        //   row 1 (top): GOLD GOLD GOLD GOLD       (золотая окантовка сверху)
        //   row 2:       BLACK PURPLE PURPLE BLACK
        //   row 3:       BLACK GOLD GOLD BLACK     (центральный крест начало)
        //   row 4:       PURPLE GOLD GOLD PURPLE
        //   row 5:       PURPLE GOLD GOLD PURPLE
        //   row 6:       BLACK GOLD GOLD BLACK     (центральный крест конец)
        //   row 7:       BLACK PURPLE PURPLE BLACK
        //   row 8 (bot): GOLD GOLD GOLD GOLD       (золотая окантовка снизу)
        Material[][] cloth = {
            { Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK   },
            { Material.BLACK_WOOL,   Material.PURPLE_WOOL,  Material.PURPLE_WOOL,  Material.BLACK_WOOL   },
            { Material.BLACK_WOOL,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.BLACK_WOOL   },
            { Material.PURPLE_WOOL,  Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.PURPLE_WOOL  },
            { Material.PURPLE_WOOL,  Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.PURPLE_WOOL  },
            { Material.BLACK_WOOL,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.BLACK_WOOL   },
            { Material.BLACK_WOOL,   Material.PURPLE_WOOL,  Material.PURPLE_WOOL,  Material.BLACK_WOOL   },
            { Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK,   Material.GOLD_BLOCK   },
        };
        for (int row = 0; row < 8; row++) {
            int y = crossY - 1 - row;
            for (int col = 0; col < 4; col++) {
                int z = tz + 1 + col;
                painter.place(tx, y, z, cloth[row][col]);
                count++;
            }
        }
        // Хвост-«ласточкин»: 2 PURPLE_STAINED_GLASS_PANE на двух
        // крайних колонках, 2 ряда вниз от полотна.
        for (int row = 0; row < 2; row++) {
            int y = crossY - 9 - row;
            painter.place(tx, y, tz + 1, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(tx, y, tz + 4, Material.PURPLE_STAINED_GLASS_PANE);
            count += 2;
        }
        return count;
    }

    /**
     * Компактный «флюгер» вместо большого штандарта — для угловых
     * пинаклей (CP), где места мало. POLISHED_BLACKSTONE_WALL ×3 +
     * LIGHTNING_ROD + END_ROD сверху.
     */
    private long buildSpirelet(int tx, int topY, int tz) {
        long count = 0;
        for (int dy = 1; dy <= 3; dy++) {
            painter.place(tx, topY + dy, tz, Material.POLISHED_BLACKSTONE_WALL);
            count++;
        }
        painter.place(tx, topY + 4, tz, Material.LIGHTNING_ROD);
        painter.place(tx, topY + 5, tz, Material.END_ROD);
        count += 2;
        return count;
    }

    // =========================================================================
    // ФАЗА 23 (PR 3.8): 4 ШТАНДАРТА НА ЦЕНТРАЛЬНОЙ БАШНЕ (НИЖЕ ГЛАЗА)
    // =========================================================================

    /**
     * На каждой из 4 сторон тела центральной башни — вертикальное
     * полотно-штандарт 3 широкое × 7 высокое. Цвета: BLACK_WOOL фон
     * с GOLD_BLOCK кругом-медальоном (символ Эликия) посередине.
     *
     * <p>Высота: y=Y_BASE+50..56 (y=120..126), на стенах CT_HALF=5.
     */
    private long buildCentralStandard() {
        long count = 0;
        int bandBottomY = Y_BASE + 50; // y=120
        int bandTopY    = Y_BASE + 56; // y=126

        // 4 стороны центральной башни: south (z=+CT_HALF), north (z=-CT_HALF),
        // east (x=+CT_HALF), west (x=-CT_HALF).
        for (int side = 0; side < 4; side++) {
            for (int y = bandBottomY; y <= bandTopY; y++) {
                int dyLocal = y - bandBottomY;            // 0..6
                for (int across = -1; across <= 1; across++) {
                    int wx, wz;
                    switch (side) {
                        case 0: // south
                            wx = CX + across; wz = CZ + CT_HALF; break;
                        case 1: // east
                            wx = CX + CT_HALF; wz = CZ + across; break;
                        case 2: // north
                            wx = CX + across; wz = CZ - CT_HALF; break;
                        default: // west
                            wx = CX - CT_HALF; wz = CZ + across; break;
                    }
                    Material mat;
                    boolean isCenterCross = (across == 0 && (dyLocal == 1 || dyLocal == 5))
                            || (Math.abs(across) == 0 && dyLocal == 3);
                    boolean isMedallionRing = (Math.abs(across) == 1 && dyLocal == 3)
                            || (across == 0 && (dyLocal == 2 || dyLocal == 4));
                    boolean isBorder = (dyLocal == 0 || dyLocal == 6);
                    if (isBorder) {
                        mat = Material.GOLD_BLOCK;
                    } else if (isCenterCross || isMedallionRing) {
                        mat = Material.GOLD_BLOCK;
                    } else {
                        mat = Material.BLACK_WOOL;
                    }
                    painter.place(wx, y, wz, mat);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 24 (PR 3.8): SOUL_LANTERN-СКОНСЫ НА ВНЕШНИХ СТЕНАХ
    // =========================================================================

    /**
     * Сконсы (фасадные светильники) на длинных боковых стенах нефа
     * между контрфорсами и на торцах трансепта. Каждый сконс — OAK_FENCE
     * на 1 блок наружу от стены + SOUL_LANTERN под ним.
     *
     * <p>Высота: y=Y_BASE+19 (≈грудь второго этажа, легко видно).
     */
    private long buildExteriorSconces() {
        long count = 0;
        int sconceY = Y_BASE + 19;

        // ===== Длинные боковые стены нефа (x=±HALF_NAVE_W=±15) =====
        // Контрфорсы стоят на z=±32, ±18. Сконсы между ними.
        int[] naveSconceZs = { -36, -25, -10, 10, 25, 36 };
        for (int dz : naveSconceZs) {
            for (int signX : new int[] { -1, +1 }) {
                int sx = CX + signX * (HALF_NAVE_W + 1); // 1 блок за стеной
                int sz = CZ + dz;
                painter.place(sx, sconceY, sz, Material.OAK_FENCE);
                painter.place(sx, sconceY - 1, sz, Material.SOUL_LANTERN);
                count += 2;
            }
        }

        // ===== Торцы трансепта (x=±HALF_TRANSEPT_W=±30) =====
        // По 2 сконса на каждый торец, выше/ниже центральной розы.
        for (int signX : new int[] { -1, +1 }) {
            int sx = CX + signX * (HALF_TRANSEPT_W + 1);
            for (int dz : new int[] { -3, 3 }) {
                painter.place(sx, sconceY, CZ + dz, Material.OAK_FENCE);
                painter.place(sx, sconceY - 1, CZ + dz, Material.SOUL_LANTERN);
                count += 2;
            }
        }

        // ===== Длинные боковые стены трансепта (z=±HALF_TRANSEPT_L=±7) =====
        // Контрфорсы стоят на x=±22. Сконсы между ними.
        int[] transeptSconceXs = { -26, -18, 18, 26 };
        for (int dx : transeptSconceXs) {
            for (int signZ : new int[] { -1, +1 }) {
                int sx = CX + dx;
                int sz = CZ + signZ * (HALF_TRANSEPT_L + 1);
                painter.place(sx, sconceY, sz, Material.OAK_FENCE);
                painter.place(sx, sconceY - 1, sz, Material.SOUL_LANTERN);
                count += 2;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 25 (PR 3.8): АМЕТИСТОВЫЕ ЖИЛЫ НА КОНТРФОРСАХ
    // =========================================================================

    /**
     * Заменяет POLISHED_BLACKSTONE_BRICKS на AMETHYST_BLOCK на 3 уровнях
     * каждого из 12 контрфорсов — даёт фиолетовые «жилы», как у фентезийных
     * соборов с кристаллами. Перезаписывает существующие блоки контрфорсов
     * (вызывается ПОСЛЕ buildButtresses).
     */
    private long buildAmethystVeins() {
        long count = 0;
        int[] veinDys = { 9, 18, 24 };
        // 4 пары контрфорсов на нефе (8 контрфорсов).
        int[] naveZs = { -32, -18, 18, 32 };
        for (int dz : naveZs) {
            for (int side : new int[] { -1, +1 }) {
                int bx = CX + side * (HALF_NAVE_W + 1);
                int bz = CZ + dz;
                for (int dy : veinDys) {
                    painter.place(bx, Y_BASE + dy, bz, Material.AMETHYST_BLOCK);
                    count++;
                }
            }
        }
        // 2 пары контрфорсов на трансепте (4 контрфорса).
        int[] transeptXs = { -22, 22 };
        for (int dx : transeptXs) {
            for (int side : new int[] { -1, +1 }) {
                int bx = CX + dx;
                int bz = CZ + side * (HALF_TRANSEPT_L + 1);
                for (int dy : veinDys) {
                    painter.place(bx, Y_BASE + dy, bz, Material.AMETHYST_BLOCK);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 26 (PR 3.8): ЦВЕТОЧНЫЕ КЛУМБЫ ПО УГЛАМ ЦОКОЛЯ
    // =========================================================================

    /**
     * 4 небольшие клумбы 3×3 у каждой стороны cruciform — между пинаклями
     * (на пустых углах cross-плана). Использует PODZOL как почву и
     * чередует ALLIUM, LILAC, PINK_PETALS, AZURE_BLUET, CORNFLOWER
     * как растения.
     *
     * <p>Подпол: y=Y_BASE PODZOL заменяет POLISHED_DEEPSLATE city floor;
     * растения ставятся на y=Y_BASE+1.
     */
    private long buildBaseGardens() {
        long count = 0;
        Material[] flowers = {
                Material.ALLIUM, Material.LILAC, Material.PINK_PETALS,
                Material.AZURE_BLUET, Material.CORNFLOWER, Material.OXEYE_DAISY,
        };
        // 4 угла cross-плана (вне footprint, не пересекаются с собором).
        int[][] gardenCenters = {
                { CX - HALF_NAVE_W - 4, CZ - HALF_TRANSEPT_L - 4 }, // SW
                { CX + HALF_NAVE_W + 4, CZ - HALF_TRANSEPT_L - 4 }, // SE
                { CX - HALF_NAVE_W - 4, CZ + HALF_TRANSEPT_L + 4 }, // NW
                { CX + HALF_NAVE_W + 4, CZ + HALF_TRANSEPT_L + 4 }, // NE
        };
        for (int[] c : gardenCenters) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    int x = c[0] + ox;
                    int z = c[1] + oz;
                    // Проверка: не залазим на footprint собора.
                    if (inFootprint(x - CX, z - CZ)) continue;
                    painter.place(x, Y_BASE, z, Material.PODZOL);
                    Material flower = flowers[(Math.abs(ox) + Math.abs(oz) * 2 + Math.abs(c[0])) % flowers.length];
                    painter.place(x, Y_BASE + 1, z, flower);
                    count += 2;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 27 (PR 3.9): КРЫШЕВЫЕ ДОЛИНЫ — ЗАКРЫТИЕ ВНУТРЕННИХ УГЛОВ CRUCIFORM
    // =========================================================================

    /**
     * 4 внутренних угла cruciform (где скат нефа встречается со скатом
     * трансепта на расстоянии diagonal) образуют L-образные углы снаружи
     * собора. Из города эти углы видны как «треугольники неба» — крыша
     * нефа и крыша трансепта НЕ сходятся в одной точке.
     *
     * <p>Этот метод добавляет диагональную «долину» (valley roof) DARK_OAK_LOG
     * + ridge POLISHED_BLACKSTONE на каждом из 4 углов: треугольник 8×8
     * блоков, ровно соединяющий два ската.
     */
    private long buildRoofValleys() {
        long count = 0;
        Material roofMat = Material.DARK_OAK_LOG;
        // 4 угла: NE/NW/SE/SW от пересечения нефа и трансепта.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                // Углы за пределами footprint, ближайшая угловая точка
                // - (CX+signX*16, CZ+signZ*8). Долина — треугольник наружу
                // от этой точки, размер 8×8.
                for (int dx = 1; dx <= 8; dx++) {
                    for (int dz = 1; dz <= 8; dz++) {
                        if (dx + dz > 9) continue;          // треугольный профиль
                        int rx = CX + signX * (HALF_NAVE_W + dx);
                        int rz = CZ + signZ * (HALF_TRANSEPT_L + dz);
                        // y растёт по min(dx, dz): чем ближе к диагонали, тем выше.
                        int rise = Math.min(dx, dz);
                        int y = WALL_TOP_Y + Math.min(rise, HALF_TRANSEPT_L);
                        painter.place(rx, y, rz, roofMat);
                        count++;
                        // Толщина: 1 блок ниже.
                        if (y > WALL_TOP_Y) {
                            painter.place(rx, y - 1, rz, roofMat);
                            count++;
                        }
                    }
                }
                // Гребень долины (диагональ dx=dz) — POLISHED_BLACKSTONE.
                for (int k = 1; k <= HALF_TRANSEPT_L; k++) {
                    int rx = CX + signX * (HALF_NAVE_W + k);
                    int rz = CZ + signZ * (HALF_TRANSEPT_L + k);
                    int y = WALL_TOP_Y + k + 1;
                    painter.place(rx, y, rz, Material.POLISHED_BLACKSTONE);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 28 (PR 3.9): АЛТАРЬ — 3-СТУПЕНЧАТАЯ ПЛАТФОРМА В АПСИДЕ
    // =========================================================================

    /**
     * Расширенный готический алтарь в северной апсиде на (CX, y, CZ-38).
     * Заменяет простую 5×3 платформу из {@link #buildInteriorLight()} на
     * 3-ступенчатый постамент с реликварием GOLD_BLOCK + AMETHYST_BLOCK,
     * 4 свечи END_ROD по углам, крест-распятие из END_ROD над алтарём.
     */
    private long buildAltar() {
        long count = 0;
        int altX = CX;
        int altZ = CZ - HALF_NAVE_L + 4; // z=-38

        // Ступень 1 (нижняя, 7×5) — DEEPSLATE_BRICKS.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                painter.place(altX + dx, Y_BASE + 1, altZ + dz,
                        Material.DEEPSLATE_BRICKS);
                count++;
            }
        }
        // Ступень 2 (5×3) — POLISHED_BLACKSTONE_BRICKS.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(altX + dx, Y_BASE + 2, altZ + dz,
                        Material.POLISHED_BLACKSTONE_BRICKS);
                count++;
            }
        }
        // Ступень 3 (3×1) — PURPUR_BLOCK + центральный AMETHYST.
        for (int dx = -1; dx <= 1; dx++) {
            Material mat = (dx == 0) ? Material.AMETHYST_BLOCK : Material.PURPUR_BLOCK;
            painter.place(altX + dx, Y_BASE + 3, altZ, mat);
            count++;
        }
        // Реликварий: GOLD_BLOCK + CHISELED_QUARTZ_BLOCK на центре алтаря.
        painter.place(altX, Y_BASE + 4, altZ, Material.GOLD_BLOCK);
        painter.place(altX, Y_BASE + 5, altZ, Material.CHISELED_QUARTZ_BLOCK);
        painter.place(altX, Y_BASE + 6, altZ, Material.GOLD_BLOCK);
        count += 3;
        // 4 свечи END_ROD на ступени 2 по углам.
        int[][] candles = {
                { -2, -1 }, { +2, -1 }, { -2, +1 }, { +2, +1 },
        };
        for (int[] c : candles) {
            painter.place(altX + c[0], Y_BASE + 3, altZ + c[1], Material.END_ROD);
            count++;
        }
        // Крест-распятие: END_ROD вертикальный + горизонтальный, повешен
        // НАД алтарём на y=Y_BASE+9..14 (y=79..84) на стене апсиды (z=CZ-42).
        int crossZ = CZ - HALF_NAVE_L; // z=-42 (стена апсиды)
        for (int dy = 0; dy <= 5; dy++) {
            painter.place(altX, Y_BASE + 9 + dy, crossZ + 1, Material.END_ROD);
            count++;
        }
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(altX + dx, Y_BASE + 12, crossZ + 1, Material.END_ROD);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 29 (PR 3.9): ЛЮСТРЫ-КАНДЕЛЯБРЫ — РАСШИРЕННЫЕ С END_ROD-РОЖКАМИ
    // =========================================================================

    /**
     * Расширяет 5 SHROOMLIGHT-канделябров из {@link #buildInteriorLight()}:
     * добавляет 4 END_ROD-«рожка» по сторонам и более длинную CHAIN-подвеску.
     * 3 дополнительных канделябра на пересечении трансепта (z=CZ, x=±20).
     */
    private long buildChandeliers() {
        long count = 0;
        // Расширение существующих 5 канделябров на нефовой оси.
        int[] zs = { -32, -16, 0, 16, 32 };
        for (int dz : zs) {
            int hx = CX, hz = CZ + dz;
            // 4 END_ROD-«рожка» по сторонам SHROOMLIGHT (y=Y_BASE+18=88).
            painter.place(hx + 1, Y_BASE + 18, hz, Material.END_ROD);
            painter.place(hx - 1, Y_BASE + 18, hz, Material.END_ROD);
            painter.place(hx, Y_BASE + 18, hz + 1, Material.END_ROD);
            painter.place(hx, Y_BASE + 18, hz - 1, Material.END_ROD);
            count += 4;
            // Дополнительный SOUL_LANTERN ниже (на CHAIN-подвеске).
            painter.place(hx, Y_BASE + 17, hz, Material.SOUL_LANTERN);
            count++;
        }
        // 2 новых люстры на трансепте (x=±20, z=CZ).
        for (int signX : new int[] { -1, +1 }) {
            int hx = CX + signX * 20, hz = CZ;
            for (int y = Y_BASE + 19; y < ROOF_PEAK_Y; y++) {
                painter.place(hx, y, hz, Material.CHAIN);
                count++;
            }
            painter.place(hx, Y_BASE + 18, hz, Material.SHROOMLIGHT);
            painter.place(hx + 1, Y_BASE + 18, hz, Material.END_ROD);
            painter.place(hx - 1, Y_BASE + 18, hz, Material.END_ROD);
            painter.place(hx, Y_BASE + 18, hz + 1, Material.END_ROD);
            painter.place(hx, Y_BASE + 18, hz - 1, Material.END_ROD);
            painter.place(hx, Y_BASE + 17, hz, Material.SOUL_LANTERN);
            count += 6;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 30 (PR 3.9): КРАСНЫЙ КОВЁР ОТ ПОРТАЛА К АЛТАРЮ
    // =========================================================================

    /**
     * Красная ковровая дорожка RED_CARPET шириной 3 блока от южных дверей
     * (z=CZ+42) через всё пересечение нефа и трансепта до подножия
     * алтаря (z=CZ-37). Кладётся НА пол собора.
     */
    private long buildCarpet() {
        long count = 0;
        int altarApproachZ = CZ - HALF_NAVE_L + 5; // z=-37 (перед нижней ступенькой алтаря)
        for (int dz = altarApproachZ; dz <= CZ + HALF_NAVE_L - 1; dz++) {
            for (int ox = -1; ox <= 1; ox++) {
                painter.place(CX + ox, Y_BASE + 1, dz, Material.RED_CARPET);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 31 (PR 3.9): ХОРНЫЕ СКАМЬИ У АПСИДЫ
    // =========================================================================

    /**
     * Хорные скамьи (хор) из DARK_OAK_STAIRS вдоль апсиды (северного нефа)
     * z=CZ-32..CZ-25 на сторонах x=±5..±13 (между колоннами и стенами).
     * 3 ряда лесенкой, с DARK_OAK_FENCE-разделителями.
     */
    private long buildChoir() {
        long count = 0;
        for (int signX : new int[] { -1, +1 }) {
            for (int row = 0; row < 3; row++) {
                int x = CX + signX * (5 + row);
                for (int dz = -32; dz <= -25; dz++) {
                    // Скамьи как DARK_OAK_STAIRS, повёрнутые наружу.
                    BlockData stairsData = Material.DARK_OAK_STAIRS.createBlockData();
                    if (stairsData instanceof Stairs) {
                        Stairs stairs = (Stairs) stairsData;
                        BlockFace facing = (signX == -1) ? BlockFace.EAST : BlockFace.WEST;
                        stairs.setFacing(facing);
                        painter.placeData(x, Y_BASE + 1, CZ + dz, stairs);
                    } else {
                        painter.place(x, Y_BASE + 1, CZ + dz, Material.DARK_OAK_STAIRS);
                    }
                    count++;
                }
                // Высокая спинка из DARK_OAK_FENCE на крайнем ряду.
                if (row == 2) {
                    for (int dz = -32; dz <= -25; dz += 2) {
                        painter.place(x, Y_BASE + 2, CZ + dz, Material.DARK_OAK_FENCE);
                        painter.place(x, Y_BASE + 3, CZ + dz, Material.DARK_OAK_FENCE);
                        count += 2;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 32 (PR 3.9): РЕБРИСТЫЕ СВОДЫ ПОТОЛКА (ПРОДОЛЖЕНИЕ buildVaultRibs)
    // =========================================================================

    /**
     * Дополняет {@link #buildVaultRibs()} (PR 3.6, нервюры от стен к коньку)
     * вторыми «рёбрами» из POLISHED_BLACKSTONE_BRICK_WALL, идущими от
     * капителей колонн (y=Y_BASE+24=94) поперёк нефа к противоположной
     * стене на той же высоте — формируют потолочные арки на y=104..110.
     * Это даёт ВИЗУАЛЬНЫЙ ребристый свод как в Notre-Dame.
     */
    private long buildVaultRibsTop() {
        long count = 0;
        // 6 поперечных арок вдоль нефа на z=±36, ±22, ±10 (между колоннами).
        int[] ribZs = { -36, -22, -10, 10, 22, 36 };
        for (int dz : ribZs) {
            if (Math.abs(dz) <= HALF_TRANSEPT_L) continue; // не над трансептом
            int z = CZ + dz;
            // Арка из BLACKSTONE_WALL: 2 вертикальные стойки + горизонталь.
            for (int side : new int[] { -1, +1 }) {
                int x = CX + side * (HALF_NAVE_W - 3); // x=±12
                // Вертикальная стойка от капители (y=94) до основания арки (y=104).
                for (int y = Y_BASE + 24; y <= Y_BASE + 34; y++) {
                    painter.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    count++;
                }
            }
            // Горизонтальная перемычка y=Y_BASE+34=104.
            for (int dx = -(HALF_NAVE_W - 4); dx <= HALF_NAVE_W - 4; dx++) {
                painter.place(CX + dx, Y_BASE + 34, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                count++;
            }
            // Бутон в центре арки (key-stone) — CHISELED_DEEPSLATE.
            painter.place(CX, Y_BASE + 34, z, Material.CHISELED_DEEPSLATE);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 33 (PR 3.9): ЛЕТАЮЩИЕ КОНТРФОРСЫ (FLYING BUTTRESSES)
    // =========================================================================

    /**
     * Аркбутаны (flying buttresses) — диагональные арки из
     * POLISHED_BLACKSTONE_BRICK_STAIRS, соединяющие верхушки контрфорсов
     * (y=Y_BASE+22=92) с нефовой стеной (y=Y_BASE+30=100). 8 контрфорсов
     * нефа (по 4 на западной и восточной стороне) → 8 аркбутанов.
     */
    private long buildFlyingButtresses() {
        long count = 0;
        // Контрфорсы нефа стоят на (CX±18, CZ±dz) для dz=−24, −10, 10, 24.
        // Hmm проверю реальные координаты в buildButtresses, но возьму
        // безопасные точки (CX±17, CZ±dz) — снаружи стены нефа.
        int[] zs = { -28, -14, 14, 28 };
        for (int dz : zs) {
            for (int signX : new int[] { -1, +1 }) {
                int outerX = CX + signX * 17; // верхушка контрфорса
                int innerX = CX + signX * (HALF_NAVE_W + 1); // y=100 на нефовой стене
                int z = CZ + dz;
                int yLow = Y_BASE + 22;
                int yHigh = Y_BASE + 30;
                int dxAbs = Math.abs(outerX - innerX);
                int yRange = yHigh - yLow;
                // Диагональная арка из STAIRS.
                int steps = Math.max(dxAbs, yRange);
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    int x = (int) Math.round(outerX + (innerX - outerX) * t);
                    int y = (int) Math.round(yLow + (yHigh - yLow) * t);
                    painter.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);
                    count++;
                    // Поддерживающий блок снизу (на каждом 2-м шаге).
                    if (s % 2 == 0 && y > Y_BASE + 22) {
                        painter.place(x, y - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 34 (PR 3.9): КРОКЕТЫ — ДЕКОРАТИВНЫЕ END_ROD НА ШПИЛЕ
    // =========================================================================

    /**
     * Декоративные крокеты (готические шипы) на 4 рёбрах центрального
     * шпиля — END_ROD каждые 3 блока, образуют стилизованный листовой
     * орнамент по диагональным гранями шпиля.
     */
    private long buildSpireCrockets() {
        long count = 0;
        // Шпиль конический от CT_BODY_TOP_Y=137 до CT_SPIRE_TOP_Y=187.
        // Крокеты на 4 рёбрах (NE/NW/SE/SW диагонали).
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                for (int yOff = 0; yOff <= CT_SPIRE_DY; yOff += 3) {
                    int y = CT_BODY_TOP_Y + yOff;
                    // Радиус шпиля сужается линейно: r = CT_HALF * (1 - yOff/CT_SPIRE_DY).
                    double t = (double) yOff / CT_SPIRE_DY;
                    int r = (int) Math.max(0, Math.round(CT_HALF * (1.0 - t) - 0.5));
                    if (r < 1) continue;
                    int x = CX + signX * r;
                    int z = CZ + signZ * r;
                    painter.place(x, y, z, Material.END_ROD);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 35 (PR 3.9): КАРНИЗЫ — ПОЯСА STAIRS НА СТЕНАХ
    // =========================================================================

    /**
     * Декоративные карнизы из POLISHED_BLACKSTONE_BRICK_STAIRS на y=82
     * (Y_BASE+12) и y=92 (Y_BASE+22) — выступающие наружу пояса по
     * периметру cruciform. Делают фасад менее «плоским».
     */
    private long buildCornices() {
        long count = 0;
        int[] corniceYs = { Y_BASE + 12, Y_BASE + 22 };
        for (int y : corniceYs) {
            // Обходим периметр footprint и кладём stairs «наружу».
            for (int dx = -HALF_TRANSEPT_W; dx <= HALF_TRANSEPT_W; dx++) {
                for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                    if (!isPerimeter(dx, dz)) continue;
                    // Определяем «наружное» направление по соседям.
                    BlockFace facing = null;
                    if (!inFootprint(dx + 1, dz)) facing = BlockFace.EAST;
                    else if (!inFootprint(dx - 1, dz)) facing = BlockFace.WEST;
                    else if (!inFootprint(dx, dz + 1)) facing = BlockFace.SOUTH;
                    else if (!inFootprint(dx, dz - 1)) facing = BlockFace.NORTH;
                    if (facing == null) continue;
                    BlockData data = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
                    if (data instanceof Stairs) {
                        Stairs stairs = (Stairs) data;
                        stairs.setFacing(facing);
                        stairs.setHalf(Bisected.Half.TOP);
                        painter.placeData(CX + dx, y, CZ + dz, stairs);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 36 (PR 3.9): TRIFOLIUM-АРКАДА НА ТОРЦАХ ТРАНСЕПТА
    // =========================================================================

    /**
     * 3 узких готических арочки PURPLE_STAINED_GLASS под существующей
     * розой на восточном (x=CX+30) и западном (x=CX-30) торцах трансепта.
     * y=Y_BASE+15..18 (4 блока высотой), x=CX±30.
     */
    private long buildTrifoliumArcade() {
        long count = 0;
        for (int signX : new int[] { -1, +1 }) {
            int gx = CX + signX * HALF_TRANSEPT_W;
            // 3 арочки на z=CZ-3, CZ, CZ+3.
            for (int dz : new int[] { -3, 0, +3 }) {
                int z = CZ + dz;
                // Низ арочки y=Y_BASE+15=85, верх y=Y_BASE+18=88, ширина 1 блок.
                for (int y = Y_BASE + 15; y <= Y_BASE + 18; y++) {
                    painter.place(gx, y, z, Material.PURPLE_STAINED_GLASS);
                    count++;
                }
                // Свод арки (1 блок выше) — POLISHED_BLACKSTONE.
                painter.place(gx, Y_BASE + 19, z, Material.POLISHED_BLACKSTONE);
                count++;
                // Подсветка изнутри.
                int innerX = gx - signX;
                painter.place(innerX, Y_BASE + 16, z, Material.SHROOMLIGHT);
                count++;
            }
        }
        return count;
    }
}
