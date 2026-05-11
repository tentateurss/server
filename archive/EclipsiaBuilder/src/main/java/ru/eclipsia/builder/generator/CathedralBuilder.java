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

    /** Парящий Глаз над центральной башней. PR 3.14 (v20): по просьбе
     *  пользователя опущен на 18 блоков (24→6) — Глаз сидит почти на пике
     *  шпиля, y=193 вместо y=211. Так Глаз гораздо «приземлённее» и
     *  читабельнее как корона собора. */
    private static final int EYE_Y_OFFSET  = 6;
    private static final int EYE_Y         = CT_SPIRE_TOP_Y + EYE_Y_OFFSET;     // y=193

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
        // PR 3.7 — двери в портале (УБРАНЫ в PR 3.10: открытая арка, ковёр виден с площади).
        // ops += buildSouthDoors();  // отключено
        // PR 3.8 — фиксы по фото-референсу:
        ops += buildGableCaps();           // закрыть дыры в торцах нефа/трансепта
        ops += buildSouthFacadeDecor();    // окно-роза + часы на южном фронтоне
        ops += buildEyeColumns();          // 4 END_ROD-колонны под Глазом (видны издалека)
        ops += buildCentralStandard();     // 4 баннера-штандарта ниже Глаза
        ops += buildExteriorSconces();     // SOUL_LANTERN-сконсы на фасаде
        ops += buildAmethystVeins();       // аметистовые жилы на контрфорсах

        // PR 3.9 — полировка по фидбэку (крыша-дыра, Глаз жирнее, интерьер, наружный декор):
        // buildRoofValleys() ОТКЛЮЧЁН в PR 3.10 — выпирающие диагонали выглядели плохо.
        // Замена: buildRoofHipMerge() сшивает скаты ровно, без выпуклостей.
        ops += buildRoofHipMerge();        // вшить трансептовый скат в нефовый
        ops += buildAltar();               // 3-ступенчатый алтарь в апсиде с большим золотым крестом (PR 3.10: переделан)
        // PR 3.12: ОТКЛЮЧЁН — старые SHROOMLIGHT-канделябры (y=88) накладывались
        // визуально на новые большие 5×5 (y=98), создавая «люстру из люстры».
        // ops += buildChandeliers();         // 5 люстр-кандел SHROOMLIGHT над нефом
        ops += buildCarpet();              // RED_CARPET от южного портала к алтарю
        ops += buildChoir();               // хорные скамьи у апсиды
        ops += buildVaultRibsTop();        // ребристые своды из потолка к колоннам
        ops += buildFlyingButtresses();    // диагональные арки от пинаклей к нефу
        ops += buildSpireCrockets();       // декоративные крокеты END_ROD на шпиле
        ops += buildCornices();            // карнизы POLISHED_BLACKSTONE_BRICK_STAIRS
        ops += buildTrifoliumArcade();     // 3 узких арочки на торцах трансепта

        // PR 3.10 — фидбэк v15: убрать «pencil-реликварий», добавить выпирающие детали,
        // обогатить интерьер, расширить сад. Двери убраны (см. выше).
        ops += buildPortalCarpetExtension(); // RED_CARPET наружу из арки, на крыльце
        ops += buildExteriorGargoyles();     // 8 каменных гаргулий на углах нефа (выпирают на 2 блока)
        ops += buildPortalSaintStatues();    // 2 высокие статуи святых у портала
        ops += buildSouthBalcony();          // балкон-пюпитр на южном фасаде между этажами
        ops += buildExteriorReliquaries();   // 8 реликвариев-витрин на стенах нефа (PURPLE_GLASS+GOLD)
        ops += buildBishopThrones();         // 2 трона за алтарём (епископ+бискуп)
        ops += buildDripstoneCandles();      // подсвечники-сталагмиты вдоль нефа
        ops += buildHangingBanners();        // подвесные знамёна между колоннами
        ops += buildStainedGlassArches();    // витражные стенки между нефом и трансептом
        ops += buildFullPerimeterGarden();   // тёмный готический сад по всему периметру (заменяет buildBaseGardens)

        // PR 3.11 — полировка v16: красивая арка, выпирающие окна, полигональная апсида,
        // большие люстры, 2-й этаж с балконом на крест, центральная башня с колоколами.
        ops += buildPortalGothicArch();      // готическая остроконечная арка на южном портале
        ops += buildBayWindows();            // выпирающие bay-window'ы на длинных стенах нефа
        ops += buildPolygonalApse();         // полигональная апсида (5 граней) на северной стене
        ops += buildBigChandeliers();        // 5 больших готических люстр 5×5 (заменяют SHROOMLIGHT)
        ops += buildTriforiumGallery();      // 2-й этаж 2 блока шириной + балкон в апсиде с видом на крест
        ops += buildCeilingLighting();       // SHROOMLIGHT-решётка + GLOWSTONE между балками
        ops += buildCentralTowerBells();     // 4 открытые арки + 4 BELL на y=120 + viewing platform y=130

        // PR 3.12 — серьёзная полировка по фидбэку v17: внешний крест на апсиде,
        // fleche на верху апсиды, козырёк над входом, ниши со статуями святых на
        // южном фасаде, крокеты на коньках.
        ops += buildExteriorApseCross();     // GOLD_BLOCK крест 5×7 снаружи на северной стене (виден с улицы)
        // PR 3.14: buildApseFleche() ОТКЛЮЧЁН — пользователь попросил удалить «лишний шпиль».
        // ops += buildApseFleche();
        ops += buildPorchOverhang();         // выступающая крыша-козырёк над входом (3 блока наружу)
        ops += buildSaintNiches();           // 4 ниши со статуями святых на южном фасаде между порталом и розой
        ops += buildRoofCrockets();          // декоративные крокеты END_ROD по конькам всех крыш

        // PR 3.13 — фидбэк v18: «внутри в центральной балке нет прохода, нет декора».
        // Открываем 4 БОЛЬШИЕ готические арки на крестовине (вместо игольного
        // ушка 3×7), декорируем лантерну изнутри, рисуем мозаику-звезду на полу
        // пересечения, добавляем крестовый ковёр восток-запад через трансепт.
        ops += buildCrossingArches();        // 4 grand pointed arches 7×13 + archivolt + keystone
        ops += buildLanternInterior();       // hanging chandelier + vault ribs + AMETHYST relief inside lantern
        ops += buildCrossingFloorStar();     // 9×9 mosaic star at crossing centre (replaces removed pulpit END_ROD)
        ops += buildCrossCarpet();           // east-west red carpet across transept (cross of carpets at crossing)
        // PR 3.14: buildCrossingHangingLanterns ОТКЛЮЧЁН — пользователь сказал
        // что эти подвесы выглядят как «висюльки» и не нужны.
        // ops += buildCrossingHangingLanterns();

        // PR 3.14 (v20) — фиксы по фидбэку v19 + больше декора:
        ops += buildEavesCornice();          // элегантный карниз-козырёк по периметру нефа/трансепта
        ops += buildTriforiumStairs();       // 4 угловые лестницы пол→галерея (заменяют «лестницу в никуда»)
        ops += buildPewsExtended();          // больше скамей в нефе + скамьи в трансепте
        ops += buildWallCandles();           // CANDLE-сконсы вдоль внутренних стен нефа
        ops += buildApseOrgan();             // декоративный орган в апсиде за алтарём

        // PR 3.15 — фикс «дырявой крыши»: закрыть треугольный клин между нефом
        // и трансептовым гребнем, который зиял на стыке крестовины.
        ops += buildCrossingRoofFill();

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
        // PR 3.14: END_ROD/LIGHTNING_ROD заменены на каменные капы (без внешнего света).
        int pinTop = Y_BASE + WALL_HEIGHT - 2;
        for (int dy = 1; dy <= 5; dy++) {
            int y = pinTop + dy;
            Material mat = (dy == 5) ? Material.POLISHED_BLACKSTONE_BRICK_WALL
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

        // PR 3.14: SHROOMLIGHT-«окошки» на скатах удалены — всё освещение
        // теперь только внутри собора. Снаружи крыша сплошная деревянная.
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
        // 4 угловых пинакля на короне (PR 3.14: тёмные каменные шпили,
        // без END_ROD/LIGHTNING_ROD — никаких внешних ламп).
        for (int sx : new int[] { -CT_HALF - 1, CT_HALF + 1 }) {
            for (int sz : new int[] { -CT_HALF - 1, CT_HALF + 1 }) {
                for (int dy = 1; dy <= 6; dy++) {
                    Material mat = (dy == 6) ? Material.POLISHED_BLACKSTONE_BRICK_WALL
                            : (dy >= 4) ? Material.POLISHED_BLACKSTONE
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
            // PR 3.14: END_ROD-маяк удалён, оставляем только каменный шпилет.
            int beaconY = WALL_TOP_Y + CP_BODY_DY + CP_SPIRE_DY + 1;
            painter.place(tx, beaconY, tz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            count++;
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
        // PR 3.13: пюпитр перенесён из центра крестовины (где он мешал ходьбе
        // насквозь и закрывал нижнюю часть звезды-мозаики) — теперь
        // {@link #buildCrossingFloorStar()} рисует на полу пересечения парадную
        // мозаику, а самой «кафедры проповедника» (раньше был END_ROD на y=72)
        // больше нет: проход по нефу свободен. SHROOMLIGHT-углы оставлены —
        // они стоят на y=72 в углах квадрата 7×7, сидят за колоннами и
        // подсвечивают звезду снизу.
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
        // PR 3.14: LIGHTNING_ROD/END_ROD сверху удалены — тёмные каменные
        // флюгера без светящих ламп снаружи.
        for (int dy = 1; dy <= 4; dy++) {
            painter.place(tx, topY + dy, tz, Material.POLISHED_BLACKSTONE_WALL);
            count++;
        }
        painter.place(tx, topY + 5, tz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        count++;
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
    // ФАЗА 27 (PR 3.10): КРЫШЕВОЕ HIP-СЛИЯНИЕ ТРАНСЕПТА И НЕФА
    // =========================================================================

    /**
     * PR 3.10 — REPLACES buildRoofValleys (которая выпирала диагоналями наружу).
     *
     * <p>Гладкое слияние трансептовой и нефовой крыш на 4 внутренних L-углах.
     * Каждый угол получает 45°-«вальмовый ребро» (HIP RAMP), идущее от
     * точки L-угла на eave (x=±16, y=102, z=±7) ВВЕРХ и ВНУТРЬ к точке,
     * где трансептовый гребень должен встретиться с нефовым скатом
     * (x=±9, y=109, z=0).
     *
     * <p>По пути этого 45° ребра дополнительно закрывает «дыры» по бокам
     * (отстающий блок на x±1 и z±1), чтобы не было просветов сбоку.
     * Без этой полировки возникал треугольный пробел на y=103..109 в
     * вертикальной полосе x=±15..±16 (что игрок и видел на скрине).
     */
    private long buildRoofHipMerge() {
        long count = 0;
        Material roofMat = Material.DARK_OAK_LOG;
        // PR 3.12 — переделано: пирамидная заливка v17 давала ступенчатый
        // визуальный «пиздец» на крыше (диагональный шрам POLISHED_BLACKSTONE
        // + сама пирамида). Заменено на ТОНКОЕ диагональное hip-ребро,
        // совпадающее по цвету с нефовым/трансептовым скатом + минимальная
        // боковая толщина, чтобы заполнить видимые щели на L-уголе.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                // Диагональ 45° от eave-угла (x=±16, y=102, z=±8) inward
                // к (x=±9, y=109, z=±1) — единый thin hip ridge.
                for (int k = 0; k <= HALF_TRANSEPT_L; k++) {
                    int xOff = HALF_NAVE_W + 1 - k;       // 16, 15, ..., 9
                    int zOff = HALF_TRANSEPT_L + 1 - k;   // 8, 7, ..., 1
                    int x = CX + signX * xOff;
                    int z = CZ + signZ * zOff;
                    int y = WALL_TOP_Y + k;
                    painter.place(x, y, z, roofMat);
                    count++;
                    // Малая бок. толщина (1 блок внутрь по обоим осям) —
                    // визуально замыкает hip-ребро и заполняет щели.
                    painter.place(x - signX, y, z, roofMat);
                    painter.place(x, y, z - signZ, roofMat);
                    count += 2;
                }
                // Доп. фикс: вертикальная колонна в L-углу на y=103..109
                // (где пирамида создавала «штырь»): только ОДНА колонна
                // в самом «конце» eave (x=±16, z=±8).
                for (int y = WALL_TOP_Y + 1; y <= WALL_TOP_Y + HALF_TRANSEPT_L; y++) {
                    painter.place(CX + signX * (HALF_NAVE_W + 1),
                            y,
                            CZ + signZ * (HALF_TRANSEPT_L + 1),
                            roofMat);
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
        // PR 3.10: убран «pencil-реликварий» (3-блочная стопка GOLD/QUARTZ/GOLD —
        // выглядел фаллически). Заменён на низкий алтарный камень + чашу.
        // На центре алтаря: 1 блок CHISELED_QUARTZ (низкий камень).
        painter.place(altX, Y_BASE + 4, altZ, Material.CHISELED_QUARTZ_BLOCK);
        count++;
        // Перед камнем — золотая чаша (DECORATED_POT с цветочным узором + GOLD_BLOCK ободок).
        painter.place(altX - 1, Y_BASE + 4, altZ, Material.GOLD_BLOCK);
        painter.place(altX + 1, Y_BASE + 4, altZ, Material.GOLD_BLOCK);
        count += 2;
        // 4 свечи END_ROD на ступени 2 по углам.
        int[][] candles = {
                { -2, -1 }, { +2, -1 }, { -2, +1 }, { +2, +1 },
        };
        for (int[] c : candles) {
            painter.place(altX + c[0], Y_BASE + 3, altZ + c[1], Material.END_ROD);
            count++;
        }
        // PR 3.10 (1A): БОЛЬШОЙ ЗОЛОТОЙ КРЕСТ 5×7 на стене апсиды (z=CZ-42).
        // Вертикаль: 7 GOLD_BLOCK подряд (y=Y_BASE+8..14 = y=78..84).
        // Перекладина: 5 GOLD_BLOCK по горизонтали на y=Y_BASE+11 (y=81).
        // По 4 концам — END_ROD-«сияние».
        int crossZ = CZ - HALF_NAVE_L + 1; // z=-41 (внутренняя сторона стены апсиды)
        // Вертикаль креста.
        for (int dy = 0; dy <= 6; dy++) {
            painter.place(altX, Y_BASE + 8 + dy, crossZ, Material.GOLD_BLOCK);
            count++;
        }
        // Перекладина креста (5 блоков, центр на y=Y_BASE+11 = y=81).
        for (int dx = -2; dx <= 2; dx++) {
            if (dx != 0) {
                painter.place(altX + dx, Y_BASE + 11, crossZ, Material.GOLD_BLOCK);
                count++;
            }
        }
        // END_ROD-сияние на 4 концах креста (выпирает на 1 блок наружу из стены).
        painter.place(altX, Y_BASE + 15, crossZ, Material.END_ROD);          // верх
        painter.place(altX, Y_BASE + 7, crossZ, Material.END_ROD);           // низ
        painter.place(altX - 3, Y_BASE + 11, crossZ, Material.END_ROD);      // левый
        painter.place(altX + 3, Y_BASE + 11, crossZ, Material.END_ROD);      // правый
        count += 4;
        // SOUL_LANTERN-«сияния» по углам перекладины.
        painter.place(altX - 2, Y_BASE + 12, crossZ, Material.SOUL_LANTERN);
        painter.place(altX + 2, Y_BASE + 12, crossZ, Material.SOUL_LANTERN);
        painter.place(altX - 2, Y_BASE + 10, crossZ, Material.SOUL_LANTERN);
        painter.place(altX + 2, Y_BASE + 10, crossZ, Material.SOUL_LANTERN);
        count += 4;
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
        // PR 3.14: END_ROD заменены на POLISHED_BLACKSTONE_BRICK_WALL —
        // каменные крокеты-шипы на рёбрах шпиля без внешнего света.
        // Шпиль конический от CT_BODY_TOP_Y=137 до CT_SPIRE_TOP_Y=187.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                for (int yOff = 0; yOff <= CT_SPIRE_DY; yOff += 3) {
                    int y = CT_BODY_TOP_Y + yOff;
                    double t = (double) yOff / CT_SPIRE_DY;
                    int r = (int) Math.max(0, Math.round(CT_HALF * (1.0 - t) - 0.5));
                    if (r < 1) continue;
                    int x = CX + signX * r;
                    int z = CZ + signZ * r;
                    painter.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
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

    // =========================================================================
    // ФАЗА 37 (PR 3.10): RED_CARPET НА КРЫЛЬЦЕ И ВЫХОДИТ ИЗ АРКИ
    // =========================================================================

    /**
     * PR 3.10: продлевает красный ковёр от южного портала НАРУЖУ через
     * крыльцо (3 ступени) и ещё на 4 блока на площадь. Так аркой видно
     * красный ковёр сразу с площади перед собором.
     */
    private long buildPortalCarpetExtension() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L; // z=27 (южная стена)
        // Крыльцо z=28..30 + площадь z=31..34. Шириной 3 блока (CX-1..CX+1).
        for (int dz = 1; dz <= 7; dz++) {
            int z = absZ + dz;
            int yLevel = Y_BASE + 1; // на уровне крыльца
            for (int ox = -1; ox <= 1; ox++) {
                painter.place(CX + ox, yLevel, z, Material.RED_CARPET);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 38 (PR 3.10) — 2A: ГАРГУЛЬИ НА УГЛАХ НЕФА И ТРАНСЕПТА
    // =========================================================================

    /**
     * PR 3.10 (2A): 8 каменных гаргулий, выпирающих на 2 блока из стен
     * на верхних углах cruciform. Каждая — 2-блочный «выступ» из
     * COBBLED_DEEPSLATE_WALL + COBBLED_DEEPSLATE_STAIRS, с END_ROD-«языком»
     * вперёд и SOUL_LANTERN снизу как «огненная пасть».
     */
    private long buildExteriorGargoyles() {
        long count = 0;
        int gargY = WALL_TOP_Y - 4; // y=98
        // 4 угла нефа (южная и северная стены, по краям).
        int[][] corners = {
                { CX - HALF_NAVE_W, CZ + HALF_NAVE_L, -1, +1 },  // SW нефа, наружу = -X, +Z
                { CX + HALF_NAVE_W, CZ + HALF_NAVE_L, +1, +1 },  // SE нефа
                { CX - HALF_NAVE_W, CZ - HALF_NAVE_L, -1, -1 },  // NW нефа (апсида)
                { CX + HALF_NAVE_W, CZ - HALF_NAVE_L, +1, -1 },  // NE нефа
                // 4 края трансепта (восточный и западный торцы).
                { CX + HALF_TRANSEPT_W, CZ - HALF_TRANSEPT_L, +1, -1 }, // E-N
                { CX + HALF_TRANSEPT_W, CZ + HALF_TRANSEPT_L, +1, +1 }, // E-S
                { CX - HALF_TRANSEPT_W, CZ - HALF_TRANSEPT_L, -1, -1 }, // W-N
                { CX - HALF_TRANSEPT_W, CZ + HALF_TRANSEPT_L, -1, +1 }, // W-S
        };
        for (int[] c : corners) {
            int wx = c[0], wz = c[1], signX = c[2], signZ = c[3];
            // Платформа (1 блок наружу): COBBLED_DEEPSLATE_WALL.
            painter.place(wx + signX, gargY, wz + signZ, Material.COBBLED_DEEPSLATE_WALL);
            // Туловище (2 блока наружу): CHISELED_DEEPSLATE.
            painter.place(wx + signX * 2, gargY, wz + signZ * 2, Material.CHISELED_DEEPSLATE);
            // Голова сверху: COBBLED_DEEPSLATE.
            painter.place(wx + signX * 2, gargY + 1, wz + signZ * 2, Material.COBBLED_DEEPSLATE);
            // END_ROD-«язык» торчит вперёд.
            painter.place(wx + signX * 3, gargY, wz + signZ * 3, Material.END_ROD);
            // SOUL_LANTERN под пастью.
            painter.place(wx + signX * 2, gargY - 1, wz + signZ * 2, Material.SOUL_LANTERN);
            count += 5;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 39 (PR 3.10) — 2B: СТАТУИ СВЯТЫХ У ПОРТАЛА
    // =========================================================================

    /**
     * PR 3.10 (2B): 2 высокие каменные статуи у южного портала
     * (по обе стороны крыльца). Постамент 1×1×2, тело 1×1×4,
     * голова — PIGLIN_HEAD. SOUL_LANTERN у ног, END_ROD-нимб над головой.
     */
    private long buildPortalSaintStatues() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L + 4; // перед крыльцом (z=31)
        for (int signX : new int[] { -1, +1 }) {
            int sx = CX + signX * 8; // x=±8 от центра портала
            // Постамент: y=Y_BASE+1..2 — POLISHED_BLACKSTONE_BRICKS.
            painter.place(sx, Y_BASE + 1, absZ, Material.POLISHED_BLACKSTONE_BRICKS);
            painter.place(sx, Y_BASE + 2, absZ, Material.POLISHED_BLACKSTONE_BRICKS);
            // Тело: y=Y_BASE+3..6 — DEEPSLATE_BRICKS.
            for (int dy = 3; dy <= 6; dy++) {
                painter.place(sx, Y_BASE + dy, absZ, Material.DEEPSLATE_BRICKS);
                count++;
            }
            // Плечи (расширение на 1 блок по X): y=Y_BASE+5, x=±9.
            painter.place(sx + signX, Y_BASE + 5, absZ, Material.CHISELED_DEEPSLATE);
            painter.place(sx - signX, Y_BASE + 5, absZ, Material.CHISELED_DEEPSLATE);
            // Голова: PIGLIN_HEAD на y=Y_BASE+7.
            BlockData head = Material.PIGLIN_HEAD.createBlockData();
            if (head instanceof Rotatable) {
                ((Rotatable) head).setRotation(BlockFace.SOUTH);
            }
            painter.placeData(sx, Y_BASE + 7, absZ, head);
            // Нимб: END_ROD на y=Y_BASE+8.
            painter.place(sx, Y_BASE + 8, absZ, Material.END_ROD);
            // 4 свечи END_ROD по углам постамента.
            painter.place(sx + 1, Y_BASE + 1, absZ + 1, Material.SOUL_LANTERN);
            painter.place(sx - 1, Y_BASE + 1, absZ - 1, Material.SOUL_LANTERN);
            count += 9;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 40 (PR 3.10) — 2C: БАЛКОН-ПЮПИТР НА ЮЖНОМ ФАСАДЕ
    // =========================================================================

    /**
     * PR 3.10 (2C): балкон 5×3 на южном фасаде, выступает на 2 блока
     * над крыльцом. Платформа DEEPSLATE_BRICK_SLAB, ограждение
     * COBBLED_DEEPSLATE_WALL, 2 SOUL_LANTERN на углах. Под балконом —
     * 2 опорных консоли POLISHED_BLACKSTONE_BRICK_STAIRS.
     */
    private long buildSouthBalcony() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L; // z=27 (стена)
        int balY = WALL_TOP_Y - 12;  // y=90 (между этажами фасада)
        // Платформа 5 блоков (x=CX-2..CX+2), на z=absZ+1 и absZ+2 (выступ 2 блока).
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(CX + dx, balY, absZ + 1, Material.DEEPSLATE_BRICKS);
            painter.place(CX + dx, balY, absZ + 2, Material.DEEPSLATE_BRICKS);
            count += 2;
        }
        // Ограждение по фронту и бокам (y=balY+1).
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(CX + dx, balY + 1, absZ + 2, Material.COBBLED_DEEPSLATE_WALL);
            count++;
        }
        for (int side : new int[] { -2, +2 }) {
            painter.place(CX + side, balY + 1, absZ + 1, Material.COBBLED_DEEPSLATE_WALL);
            count++;
        }
        // 2 фонаря на углах.
        painter.place(CX - 2, balY + 2, absZ + 2, Material.SOUL_LANTERN);
        painter.place(CX + 2, balY + 2, absZ + 2, Material.SOUL_LANTERN);
        // Опорные консоли (под балконом, y=balY-1).
        BlockData supLeft = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
        if (supLeft instanceof Stairs) {
            ((Stairs) supLeft).setFacing(BlockFace.SOUTH);
            ((Stairs) supLeft).setHalf(Bisected.Half.TOP);
        }
        painter.placeData(CX - 2, balY - 1, absZ + 1, supLeft);
        painter.placeData(CX + 2, balY - 1, absZ + 1, supLeft);
        count += 4;
        // Пюпитр (LECTERN) в центре балкона.
        BlockData lectern = Material.LECTERN.createBlockData();
        if (lectern instanceof Directional) {
            ((Directional) lectern).setFacing(BlockFace.SOUTH);
        }
        painter.placeData(CX, balY + 1, absZ + 1, lectern);
        count++;
        return count;
    }

    // =========================================================================
    // ФАЗА 41 (PR 3.10) — 2E: РЕЛИКВАРИИ-ВИТРИНЫ НА СТЕНАХ НЕФА
    // =========================================================================

    /**
     * PR 3.10 (2E): 8 реликвариев-витрин на стенах нефа (по 4 на каждую
     * сторону). Каждый — выступ на 1 блок наружу: PURPLE_STAINED_GLASS-куб
     * 1×1×1, GOLD_BLOCK снизу, END_ROD сверху, SOUL_LANTERN снизу.
     */
    private long buildExteriorReliquaries() {
        long count = 0;
        int relY = WALL_TOP_Y - 14; // y=88 (между декорами фасада)
        int[] zs = { -32, -16, 0, 16 }; // 4 точки по нефу
        for (int signX : new int[] { -1, +1 }) {
            for (int dz : zs) {
                int wx = CX + signX * HALF_NAVE_W;
                int wz = CZ + dz;
                int rx = wx + signX; // 1 блок наружу
                // GOLD_BLOCK основание.
                painter.place(rx, relY, wz, Material.GOLD_BLOCK);
                // PURPLE_STAINED_GLASS-куб с подсветкой.
                painter.place(rx, relY + 1, wz, Material.PURPLE_STAINED_GLASS);
                // END_ROD навершие.
                painter.place(rx, relY + 2, wz, Material.END_ROD);
                // SOUL_LANTERN снизу (свешен под витриной).
                painter.place(rx, relY - 1, wz, Material.SOUL_LANTERN);
                count += 4;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 42 (PR 3.10) — 3A: ТРОНЫ ЕПИСКОПА И БИСКУПА ЗА АЛТАРЁМ
    // =========================================================================

    /**
     * PR 3.10 (3A): 2 трона в апсиде ЗА алтарём (z=CZ-40). Каждый —
     * сиденье PURPLE_GLAZED_TERRACOTTA, спинка GOLD_BLOCK + END_ROD,
     * ножки CHISELED_DEEPSLATE, по бокам SOUL_LANTERN.
     */
    private long buildBishopThrones() {
        long count = 0;
        int thrZ = CZ - HALF_NAVE_L + 2; // z=-40
        for (int side : new int[] { -1, +1 }) {
            int sx = CX + side * 4; // x=CX±4
            // Постамент: 2 блока POLISHED_BLACKSTONE_BRICKS (y=71-72).
            painter.place(sx, Y_BASE + 1, thrZ, Material.POLISHED_BLACKSTONE_BRICKS);
            painter.place(sx, Y_BASE + 2, thrZ, Material.POLISHED_BLACKSTONE_BRICKS);
            // Сиденье: PURPLE_GLAZED_TERRACOTTA на y=Y_BASE+3 (y=73).
            painter.place(sx, Y_BASE + 3, thrZ, Material.PURPLE_GLAZED_TERRACOTTA);
            // Спинка: GOLD_BLOCK на y=Y_BASE+4..6 (y=74..76).
            for (int dy = 4; dy <= 6; dy++) {
                painter.place(sx, Y_BASE + dy, thrZ - 1, Material.GOLD_BLOCK);
                count++;
            }
            // END_ROD-навершие.
            painter.place(sx, Y_BASE + 7, thrZ - 1, Material.END_ROD);
            // 2 фонаря по бокам.
            painter.place(sx - 1, Y_BASE + 3, thrZ, Material.SOUL_LANTERN);
            painter.place(sx + 1, Y_BASE + 3, thrZ, Material.SOUL_LANTERN);
            count += 6;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 43 (PR 3.10) — 3B: ПОДСВЕЧНИКИ-СТАЛАГМИТЫ ВДОЛЬ НЕФА
    // =========================================================================

    /**
     * PR 3.10 (3B): подсвечники из POINTED_DRIPSTONE+END_ROD+SOUL_LANTERN
     * каждые 8 блоков вдоль обеих внутренних стен нефа.
     */
    private long buildDripstoneCandles() {
        long count = 0;
        int candleY = Y_BASE + 14; // y=84 (низкий настенный)
        int[] zs = { -34, -22, -10, 2, 14, 26 };
        for (int signX : new int[] { -1, +1 }) {
            for (int dz : zs) {
                int sx = CX + signX * (HALF_NAVE_W - 2); // x=±13 (на 2 блока внутрь от стены)
                int sz = CZ + dz;
                // База: DRIPSTONE_BLOCK.
                painter.place(sx, candleY, sz, Material.DRIPSTONE_BLOCK);
                // Сталагмит: POINTED_DRIPSTONE (вверх).
                BlockData stalag = Material.POINTED_DRIPSTONE.createBlockData();
                if (stalag instanceof org.bukkit.block.data.type.PointedDripstone) {
                    org.bukkit.block.data.type.PointedDripstone pd =
                            (org.bukkit.block.data.type.PointedDripstone) stalag;
                    pd.setVerticalDirection(BlockFace.UP);
                    pd.setThickness(org.bukkit.block.data.type.PointedDripstone.Thickness.MIDDLE);
                }
                painter.placeData(sx, candleY + 1, sz, stalag);
                // END_ROD огонёк.
                painter.place(sx, candleY + 2, sz, Material.END_ROD);
                // SOUL_LANTERN свешен под базой.
                painter.place(sx, candleY - 1, sz, Material.SOUL_LANTERN);
                count += 4;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 44 (PR 3.10) — 3C: ПОДВЕСНЫЕ ЗНАМЁНА МЕЖДУ КОЛОННАМИ
    // =========================================================================

    /**
     * PR 3.10 (3C): подвесные PURPLE_BANNER между парами внутренних колонн
     * нефа. 4 пары × 1 знамя = 4 знамени. Подвешены на CHAIN с потолка.
     */
    private long buildHangingBanners() {
        long count = 0;
        int[] zs = { -28, -10, 8, 28 }; // те же z, что у колонн
        for (int dz : zs) {
            int sx = CX;
            int sz = CZ + dz;
            // CHAIN-подвеска от потолка y=WALL_TOP_Y-1 до y=WALL_TOP_Y-7.
            for (int y = WALL_TOP_Y - 7; y < WALL_TOP_Y; y++) {
                painter.place(sx, y, sz, Material.CHAIN);
                count++;
            }
            // Знамя PURPLE_BANNER на верхнем конце.
            BlockData bann = Material.PURPLE_BANNER.createBlockData();
            if (bann instanceof Rotatable) {
                ((Rotatable) bann).setRotation(BlockFace.SOUTH);
            }
            painter.placeData(sx, WALL_TOP_Y - 8, sz, bann);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 45 (PR 3.10) — 3D: ВИТРАЖНЫЕ АРКИ МЕЖДУ НЕФОМ И ТРАНСЕПТОМ
    // =========================================================================

    /**
     * PR 3.10 (3D): 2 витражных «стенки» между нефом и трансептом —
     * на границе z=CZ±HALF_TRANSEPT_L=±7 в обе стороны от центра.
     * Каждая — 5×3 PURPLE_STAINED_GLASS с верхней аркой POLISHED_BLACKSTONE.
     */
    private long buildStainedGlassArches() {
        long count = 0;
        for (int signZ : new int[] { -1, +1 }) {
            int sz = CZ + signZ * HALF_TRANSEPT_L; // z=±7 граница
            // 2 стенки: слева (x=CX-13..CX-9) и справа (x=CX+9..CX+13).
            for (int signX : new int[] { -1, +1 }) {
                for (int dx = 9; dx <= 13; dx++) {
                    int sx = CX + signX * dx;
                    for (int dy = 14; dy <= 17; dy++) {
                        painter.place(sx, Y_BASE + dy, sz, Material.PURPLE_STAINED_GLASS);
                        count++;
                    }
                }
                // Свод аркой (1 блок выше).
                for (int dx = 9; dx <= 13; dx++) {
                    int sx = CX + signX * dx;
                    painter.place(sx, Y_BASE + 18, sz, Material.POLISHED_BLACKSTONE);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 46 (PR 3.10) — 6A: ТЁМНЫЙ ГОТИЧЕСКИЙ САД ПО ВСЕМУ ПЕРИМЕТРУ
    // =========================================================================

    /**
     * PR 3.10 (6A): тёмный готический сад по ВСЕМУ периметру собора —
     * заменяет точечные клумбы из {@link #buildBaseGardens()}. Кольцо
     * шириной 4 блока вокруг footprint собора, с PODZOL/COARSE_DIRT базой,
     * AZALEA-кустами, ROSE_BUSH/LILAC высокими цветами, отдельными
     * DARK_OAK_SAPLING. Внутри — каменные дорожки COBBLED_DEEPSLATE.
     */
    private long buildFullPerimeterGarden() {
        long count = 0;
        Random rng = new Random(0xCAFEBABEL);
        // Footprint cruciform: x in [CX-30..CX+30], z in [CZ-42..CZ+42]
        // (трансепт +30 шире, чем неф, поэтому используем максимальный).
        // Сад снаружи: радиус 4..8 от внешнего контура.
        for (int x = CX - 38; x <= CX + 38; x++) {
            for (int z = CZ - 50; z <= CZ + 50; z++) {
                // Расстояние до ближайшей точки footprint.
                int dxN = Math.max(0, Math.max(CX - HALF_NAVE_W - x, x - (CX + HALF_NAVE_W)));
                int dzN = Math.max(0, Math.max(CZ - HALF_NAVE_L - z, z - (CZ + HALF_NAVE_L)));
                int dxT = Math.max(0, Math.max(CX - HALF_TRANSEPT_W - x, x - (CX + HALF_TRANSEPT_W)));
                int dzT = Math.max(0, Math.max(CZ - HALF_TRANSEPT_L - z, z - (CZ + HALF_TRANSEPT_L)));
                int distNave = Math.max(dxN, dzN);
                int distTrans = Math.max(dxT, dzT);
                int dist = Math.min(distNave, distTrans);
                // Сад только в кольце 1..7 от собора.
                if (dist < 1 || dist > 7) continue;
                // Не залезаем на крыльцо портала.
                if (z > CZ + HALF_NAVE_L && z <= CZ + HALF_NAVE_L + 7
                        && x >= CX - 5 && x <= CX + 5) continue;
                int yFloor = Y_BASE; // пол города (POLISHED_DEEPSLATE)
                // База: 60% PODZOL, 30% COARSE_DIRT, 10% MOSS_BLOCK.
                double r = rng.nextDouble();
                Material base;
                if (r < 0.60) base = Material.PODZOL;
                else if (r < 0.90) base = Material.COARSE_DIRT;
                else base = Material.MOSS_BLOCK;
                painter.place(x, yFloor, z, base);
                count++;
                // 30% — ничего сверху (трава и так отрисуется).
                // 25% — короткий куст AZALEA.
                // 20% — ROSE_BUSH (2 блока).
                // 15% — LILAC (2 блока).
                // 5% — DARK_OAK_SAPLING.
                // 5% — высокая трава GRASS+TALL_GRASS.
                double r2 = rng.nextDouble();
                if (r2 < 0.30) {
                    // Просто трава (1.20+ переименован GRASS → SHORT_GRASS).
                    painter.place(x, yFloor + 1, z, Material.SHORT_GRASS);
                    count++;
                } else if (r2 < 0.55) {
                    painter.place(x, yFloor + 1, z, Material.AZALEA);
                    count++;
                } else if (r2 < 0.75) {
                    BlockData rb = Material.ROSE_BUSH.createBlockData();
                    if (rb instanceof Bisected) ((Bisected) rb).setHalf(Bisected.Half.BOTTOM);
                    BlockData rt = Material.ROSE_BUSH.createBlockData();
                    if (rt instanceof Bisected) ((Bisected) rt).setHalf(Bisected.Half.TOP);
                    painter.placeData(x, yFloor + 1, z, rb);
                    painter.placeData(x, yFloor + 2, z, rt);
                    count += 2;
                } else if (r2 < 0.90) {
                    BlockData lb = Material.LILAC.createBlockData();
                    if (lb instanceof Bisected) ((Bisected) lb).setHalf(Bisected.Half.BOTTOM);
                    BlockData lt = Material.LILAC.createBlockData();
                    if (lt instanceof Bisected) ((Bisected) lt).setHalf(Bisected.Half.TOP);
                    painter.placeData(x, yFloor + 1, z, lb);
                    painter.placeData(x, yFloor + 2, z, lt);
                    count += 2;
                } else if (r2 < 0.95) {
                    painter.place(x, yFloor + 1, z, Material.DARK_OAK_SAPLING);
                    count++;
                } else {
                    BlockData tb = Material.TALL_GRASS.createBlockData();
                    if (tb instanceof Bisected) ((Bisected) tb).setHalf(Bisected.Half.BOTTOM);
                    BlockData tt = Material.TALL_GRASS.createBlockData();
                    if (tt instanceof Bisected) ((Bisected) tt).setHalf(Bisected.Half.TOP);
                    painter.placeData(x, yFloor + 1, z, tb);
                    painter.placeData(x, yFloor + 2, z, tt);
                    count += 2;
                }
            }
        }
        // Декоративные SOUL_LANTERN-фонари на DARK_OAK_FENCE-столбиках по 8 точкам периметра.
        int[][] lanterns = {
                { CX + 35, CZ + 35 }, { CX + 35, CZ - 35 },
                { CX - 35, CZ + 35 }, { CX - 35, CZ - 35 },
                { CX, CZ + 47 }, { CX, CZ - 47 },
                { CX + 35, CZ }, { CX - 35, CZ },
        };
        for (int[] l : lanterns) {
            int lx = l[0], lz = l[1];
            painter.place(lx, Y_BASE + 1, lz, Material.DARK_OAK_FENCE);
            painter.place(lx, Y_BASE + 2, lz, Material.DARK_OAK_FENCE);
            painter.place(lx, Y_BASE + 3, lz, Material.DARK_OAK_FENCE);
            painter.place(lx, Y_BASE + 4, lz, Material.SOUL_LANTERN);
            count += 4;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 47 (PR 3.11) — ГОТИЧЕСКАЯ ОСТРОКОНЕЧНАЯ АРКА НА ЮЖНОМ ПОРТАЛЕ
    // =========================================================================

    /**
     * PR 3.11: украшает существующую арку южного портала готическими
     * STAIRS-jambs, END_ROD-сиянием по контуру и 2 лансет-витражами
     * по бокам входа.
     */
    private long buildPortalGothicArch() {
        long count = 0;
        int absZ = CZ + HALF_NAVE_L; // z=27 (стена)
        int outZ = absZ + 1;          // выступ на 1 блок наружу (визуальный jamb)

        // 0. ГОТИЧЕСКОЕ КАРВИНГ ОТВЕРСТИЯ: убираем «балку» на y=Y_BASE+17,
        // вырезаем форму арки в стене (была только прямоугольная дыра 9×16).
        // dx=0: проём до y=23; dx=±1: до y=21; dx=±2: до y=19; dx=±3: до y=18;
        // dx=±4: до y=17. (Контур арки на dy=17..24 не трогаем — это сама дуга.)
        // index = dx+5 → dx ∈ {-5..+5}: { -1, 17, 18, 19, 21, 23, 21, 19, 18, 17, -1 }
        int[] archInside = { -1, 17, 18, 19, 21, 23, 21, 19, 18, 17, -1 };
        for (int dx = -4; dx <= 4; dx++) {
            int top = archInside[dx + 5];
            if (top < 0) continue;
            for (int dy = 17; dy <= top; dy++) {
                painter.place(CX + dx, Y_BASE + dy, absZ, Material.AIR);
                count++;
            }
        }

        // 1. Архивольт (3-блочная рамка) вокруг существующей арки.
        // Контур арки идёт по точкам (dx, dy):
        // dx=±5: dy=17, dx=±4: dy=18, dx=±3: dy=19, dx=±2: dy=20, dx=±1: dy=22, dx=0: dy=24.
        int[][] archPts = {
                { -5, 17 }, { -4, 18 }, { -3, 19 }, { -2, 20 }, { -1, 22 }, { 0, 24 },
                { +1, 22 }, { +2, 20 }, { +3, 19 }, { +4, 18 }, { +5, 17 },
        };
        for (int[] pt : archPts) {
            int dx = pt[0], dy = pt[1];
            // Архивольт-рамка: блок наружу от арки.
            painter.place(CX + dx, Y_BASE + dy + 1, outZ, Material.CHISELED_DEEPSLATE);
            painter.place(CX + dx, Y_BASE + dy + 1, absZ, Material.CHISELED_DEEPSLATE);
            count += 2;
            // END_ROD-сияние снаружи на каждой 2-й точке арки.
            if (dx % 2 == 0) {
                painter.place(CX + dx, Y_BASE + dy + 2, outZ, Material.END_ROD);
                count++;
            }
        }
        // Замковый камень: ЗОЛОТОЙ + AMETHYST на пике.
        painter.place(CX, Y_BASE + 25, outZ, Material.GOLD_BLOCK);
        painter.place(CX, Y_BASE + 26, outZ, Material.AMETHYST_BLOCK);
        painter.place(CX, Y_BASE + 27, outZ, Material.END_ROD);
        count += 3;

        // 2. Боковые косяки (jambs) — POLISHED_BLACKSTONE_BRICK_STAIRS.
        // Слева dx=-5..-6 (выступают на 1 блок наружу), справа dx=+5..+6.
        for (int side : new int[] { -1, +1 }) {
            for (int dy = 1; dy <= 16; dy++) {
                // Верхний jamb (1 блок наружу от стены).
                BlockData stairs = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
                if (stairs instanceof Stairs) {
                    ((Stairs) stairs).setFacing(side > 0 ? BlockFace.WEST : BlockFace.EAST);
                }
                painter.placeData(CX + side * 6, Y_BASE + dy, outZ, stairs);
                painter.place(CX + side * 6, Y_BASE + dy, absZ, Material.POLISHED_BLACKSTONE_BRICKS);
                count += 2;
            }
            // Капитель (на y=Y_BASE+17, переход к арке).
            painter.place(CX + side * 6, Y_BASE + 17, absZ, Material.CHISELED_DEEPSLATE);
            painter.place(CX + side * 6, Y_BASE + 17, outZ, Material.CHISELED_DEEPSLATE);
            count += 2;
        }

        // 3. Лансет-витражи по бокам входа (PURPLE_GLASS).
        // Два узких окна 1×6 в стене на dx=±8, dy=4..9.
        for (int side : new int[] { -1, +1 }) {
            int wx = CX + side * 8;
            for (int dy = 4; dy <= 9; dy++) {
                painter.place(wx, Y_BASE + dy, absZ, Material.PURPLE_STAINED_GLASS);
                count++;
            }
            // Острый конец витража: dy=10.
            painter.place(wx, Y_BASE + 10, absZ, Material.AMETHYST_BLOCK);
            // Архивольт над витражом.
            painter.place(wx, Y_BASE + 11, absZ, Material.CHISELED_DEEPSLATE);
            count += 2;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 48 (PR 3.11) — BAY WINDOWS НА ДЛИННЫХ СТЕНАХ НЕФА
    // =========================================================================

    /**
     * PR 3.11: 6 выпирающих bay-window'ов (3 на восточной стене x=+15,
     * 3 на западной x=-15 нефа). Каждый = 3 блока шириной (по z),
     * выступает на 2 блока наружу. PURPLE_GLASS центральная панель,
     * GOLD_BLOCK рамка, END_ROD по углам, SOUL_LANTERN снизу-сверху.
     */
    private long buildBayWindows() {
        long count = 0;
        int[] zs = { -28, -10, 16 }; // 3 точки по нефу (избегаем трансепт z=±7)
        for (int signX : new int[] { -1, +1 }) {
            int wx = CX + signX * HALF_NAVE_W; // ±15 (стена)
            int outX = wx + signX;             // ±16 (1 блок наружу)
            int outX2 = wx + signX * 2;        // ±17 (2 блока наружу)
            for (int dz : zs) {
                int wz = CZ + dz;
                // База (y=84): GOLD_BLOCK по контуру 3×1 + 2×2 выступа.
                int baseY = Y_BASE + 14; // y=84
                int topY = Y_BASE + 19;  // y=89
                // Боковые "стены" bay-window'a (на z=wz-1 и wz+1, x=outX и outX2).
                for (int side : new int[] { -1, +1 }) {
                    for (int dy = baseY; dy <= topY; dy++) {
                        painter.place(outX, dy, wz + side, Material.DEEPSLATE_BRICKS);
                        painter.place(outX2, dy, wz + side, Material.DEEPSLATE_BRICKS);
                        count += 2;
                    }
                }
                // Передняя стенка bay (на x=outX2, z=wz): PURPLE_GLASS центр + GOLD рамка.
                painter.place(outX2, baseY, wz, Material.GOLD_BLOCK);
                painter.place(outX2, topY, wz, Material.GOLD_BLOCK);
                for (int dy = baseY + 1; dy < topY; dy++) {
                    painter.place(outX2, dy, wz, Material.PURPLE_STAINED_GLASS);
                    count++;
                }
                count += 2;
                // Боковые стенки bay (на x=outX2, z=wz±1) — PURPLE_GLASS.
                for (int side : new int[] { -1, +1 }) {
                    for (int dy = baseY + 2; dy < topY - 1; dy++) {
                        painter.place(outX2, dy, wz + side, Material.PURPLE_STAINED_GLASS);
                        count++;
                    }
                }
                // Верхняя крышка bay-window'a (на y=topY+1).
                BlockData topStairs = Material.DEEPSLATE_BRICK_STAIRS.createBlockData();
                if (topStairs instanceof Stairs) {
                    ((Stairs) topStairs).setFacing(signX > 0 ? BlockFace.EAST : BlockFace.WEST);
                    ((Stairs) topStairs).setHalf(Bisected.Half.TOP);
                }
                for (int dz2 = -1; dz2 <= 1; dz2++) {
                    painter.placeData(outX, topY + 1, wz + dz2, topStairs);
                    painter.placeData(outX2, topY + 1, wz + dz2, topStairs);
                    count += 2;
                }
                // Нижняя плита bay-window'a (на y=baseY-1, x=outX..outX2).
                for (int dz2 = -1; dz2 <= 1; dz2++) {
                    painter.place(outX, baseY - 1, wz + dz2, Material.DEEPSLATE_BRICKS);
                    painter.place(outX2, baseY - 1, wz + dz2, Material.DEEPSLATE_BRICKS);
                    count += 2;
                }
                // Удаляем СТЕНУ нефа в окне (чтобы окно было ОТКРЫТЫМ).
                for (int dy = baseY + 1; dy < topY; dy++) {
                    painter.place(wx, dy, wz, Material.AIR);
                    count++;
                }
                // END_ROD по 4 углам bay-window'a.
                painter.place(outX2, baseY, wz - 1, Material.END_ROD);
                painter.place(outX2, baseY, wz + 1, Material.END_ROD);
                painter.place(outX2, topY, wz - 1, Material.END_ROD);
                painter.place(outX2, topY, wz + 1, Material.END_ROD);
                count += 4;
                // SOUL_LANTERN под bay-window'ом (на y=baseY-2).
                painter.place(outX2, baseY - 2, wz, Material.SOUL_LANTERN);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 49 (PR 3.11) — ПОЛИГОНАЛЬНАЯ АПСИДА (5 ВЫСТУПАЮЩИХ КОНТРФОРСОВ)
    // =========================================================================

    /**
     * PR 3.11: 5 контрфорсов-аркаплет, выступающих наружу из северной
     * стены апсиды (z=CZ-HALF_NAVE_L=-57). Каждый — 3-блочный выступ
     * с лансет-витражом PURPLE_GLASS + GOLD_BLOCK рамка + крокеты END_ROD.
     */
    private long buildPolygonalApse() {
        long count = 0;
        int absZ = CZ - HALF_NAVE_L; // z=-57 (северная стена)
        // 5 точек: x ∈ {-12, -6, 0, +6, +12}.
        int[] xOffsets = { -12, -6, 0, +6, +12 };
        for (int dx : xOffsets) {
            int wx = CX + dx;
            int outZ = absZ - 1;  // 1 блок наружу
            int outZ2 = absZ - 2; // 2 блока наружу
            int outZ3 = absZ - 3; // 3 блока наружу для центрального выступа
            int baseY = Y_BASE + 1;
            int topY = Y_BASE + 25; // высокий контрфорс
            // Вертикальный «пилон» 1×1 на outZ2.
            for (int dy = baseY; dy <= topY; dy++) {
                painter.place(wx, dy, outZ2, Material.DEEPSLATE_BRICKS);
                count++;
            }
            // Стенки пилона (выступ 1 блок).
            for (int dy = baseY; dy <= topY - 4; dy++) {
                painter.place(wx, dy, outZ, Material.DEEPSLATE_BRICKS);
                count++;
            }
            // Центральный (dx=0) — выступает дальше.
            if (dx == 0) {
                for (int dy = baseY; dy <= topY; dy++) {
                    painter.place(wx, dy, outZ3, Material.DEEPSLATE_BRICKS);
                    count++;
                }
            }
            // Лансет-витраж на пилоне (вертикальный 1×6).
            for (int dy = Y_BASE + 10; dy <= Y_BASE + 15; dy++) {
                painter.place(wx, dy, outZ2, Material.PURPLE_STAINED_GLASS);
                count++;
            }
            // Острый верх витража (AMETHYST + GOLD рамка).
            painter.place(wx, Y_BASE + 16, outZ2, Material.AMETHYST_BLOCK);
            painter.place(wx, Y_BASE + 17, outZ2, Material.GOLD_BLOCK);
            // Крокеты END_ROD на верху пилона.
            painter.place(wx, topY + 1, outZ2, Material.END_ROD);
            count += 3;
            // Аркада-перемычка между пилонами (POLISHED_BLACKSTONE_BRICK_STAIRS на y=Y_BASE+18).
            if (dx < 12) {
                BlockData arch = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
                if (arch instanceof Stairs) {
                    ((Stairs) arch).setFacing(BlockFace.NORTH);
                    ((Stairs) arch).setHalf(Bisected.Half.TOP);
                }
                for (int xLink = wx + 1; xLink < CX + dx + 6; xLink++) {
                    painter.placeData(xLink, Y_BASE + 18, outZ2, arch);
                    count++;
                }
            }
        }
        // SOUL_LANTERN-светильники между пилонами на y=Y_BASE+8.
        for (int xLink : new int[] { CX - 9, CX - 3, CX + 3, CX + 9 }) {
            painter.place(xLink, Y_BASE + 8, absZ - 2, Material.SOUL_LANTERN);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 50 (PR 3.11) — БОЛЬШИЕ ГОТИЧЕСКИЕ ЛЮСТРЫ-КАНДЕЛЯБРЫ 5×5
    // =========================================================================

    /**
     * PR 3.11: 5 больших готических люстр 5×5 (заменяют SHROOMLIGHT-кластеры).
     * Каждая = крест GOLD_BLOCK 5×5 + GLOWSTONE центр + END_ROD по 4 концам
     * + 8 SOUL_LANTERN снизу + 4 длинные CHAIN-подвесы.
     */
    private long buildBigChandeliers() {
        long count = 0;
        int[] zs = { -34, -18, 0, 18, 34 }; // 5 люстр вдоль нефа
        int chandY = WALL_TOP_Y - 4; // y=98 (видна с пола и галереи y=88)
        int chainTopY = ROOF_PEAK_Y - 6; // y=114 (под скатом крыши, не задевает)
        for (int dz : zs) {
            int hx = CX, hz = CZ + dz;
            // CHAIN-подвесы 4 шт от потолка до chandY.
            for (int side : new int[] { -1, +1 }) {
                for (int axis = 0; axis < 2; axis++) {
                    int cx = (axis == 0) ? hx + side : hx;
                    int cz = (axis == 0) ? hz : hz + side;
                    for (int y = chandY + 1; y <= chainTopY; y++) {
                        painter.place(cx, y, cz, Material.CHAIN);
                        count++;
                    }
                }
            }
            // Крест GOLD_BLOCK 5×5 на y=chandY (только края креста).
            for (int dx2 = -2; dx2 <= 2; dx2++) {
                painter.place(hx + dx2, chandY, hz, Material.GOLD_BLOCK);
                count++;
            }
            for (int dz2 = -2; dz2 <= 2; dz2++) {
                if (dz2 == 0) continue; // не дублируем центр
                painter.place(hx, chandY, hz + dz2, Material.GOLD_BLOCK);
                count++;
            }
            // GLOWSTONE центр (на y=chandY).
            painter.place(hx, chandY, hz, Material.GLOWSTONE);
            count++;
            // END_ROD-рожки по 4 концам креста (вверх).
            painter.place(hx + 2, chandY + 1, hz, Material.END_ROD);
            painter.place(hx - 2, chandY + 1, hz, Material.END_ROD);
            painter.place(hx, chandY + 1, hz + 2, Material.END_ROD);
            painter.place(hx, chandY + 1, hz - 2, Material.END_ROD);
            count += 4;
            // SOUL_LANTERN-свечи под крестом (y=chandY-1) на 4 углах.
            painter.place(hx + 2, chandY - 1, hz + 2, Material.SOUL_LANTERN);
            painter.place(hx - 2, chandY - 1, hz - 2, Material.SOUL_LANTERN);
            painter.place(hx + 2, chandY - 1, hz - 2, Material.SOUL_LANTERN);
            painter.place(hx - 2, chandY - 1, hz + 2, Material.SOUL_LANTERN);
            // 4 SHROOMLIGHT по сторонам креста для дополнительного света.
            painter.place(hx + 1, chandY - 1, hz, Material.SHROOMLIGHT);
            painter.place(hx - 1, chandY - 1, hz, Material.SHROOMLIGHT);
            painter.place(hx, chandY - 1, hz + 1, Material.SHROOMLIGHT);
            painter.place(hx, chandY - 1, hz - 1, Material.SHROOMLIGHT);
            count += 8;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 51 (PR 3.11) — TRIFORIUM GALLERY (2-Й ЭТАЖ + БАЛКОН НА КРЕСТ)
    // =========================================================================

    /**
     * PR 3.11: triforium-галерея 2-го этажа на y=88 вдоль обеих стен нефа,
     * 2 блока шириной. Балкон в АПСИДЕ (z=CZ-40) на y=88, прямо НАПРОТИВ
     * Большого Золотого Креста (y=78..84). С балкона — лучший вид на крест.
     * Лестницы наверх — по углам пересечения нефа+трансепта.
     */
    private long buildTriforiumGallery() {
        long count = 0;
        int galleryY = Y_BASE + 18; // y=88

        // 1. Галерея вдоль обеих стен нефа (x=±13..±14, z=-40..+40, y=88).
        // Пропускаем зону пересечения нефа+трансепта (z=-7..+7), там
        // галерея переходит в висячий мостик через зал.
        for (int signX : new int[] { -1, +1 }) {
            for (int dz = -40; dz <= 40; dz++) {
                if (Math.abs(dz) <= HALF_TRANSEPT_L) continue; // не нависаем над пересечением
                int gx1 = CX + signX * (HALF_NAVE_W - 1); // ±14 (у стены)
                int gx2 = CX + signX * (HALF_NAVE_W - 2); // ±13
                int z = CZ + dz;
                painter.place(gx1, galleryY, z, Material.DARK_OAK_PLANKS);
                painter.place(gx2, galleryY, z, Material.DARK_OAK_PLANKS);
                count += 2;
                int gxRail = CX + signX * (HALF_NAVE_W - 3); // ±12
                painter.place(gxRail, galleryY + 1, z, Material.DARK_OAK_FENCE);
                count++;
            }
            painter.place(CX + signX * (HALF_NAVE_W - 1), galleryY + 1, CZ - 40, Material.SOUL_LANTERN);
            painter.place(CX + signX * (HALF_NAVE_W - 1), galleryY + 1, CZ + 40, Material.SOUL_LANTERN);
            count += 2;
        }

        // 2. PR 3.12 — ШИРОКИЙ БАЛКОН в апсиде (15 wide × 4 deep).
        // y=88, x=-7..+7 (15 блоков, всю ширину нефа), z=-39..-36 (4 deep).
        int balconyZFront = CZ - HALF_NAVE_L + 3; // z=-39 (передний край, ближе к кресту)
        int balconyZBack = balconyZFront + 3;     // z=-36 (задний край у трона)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = balconyZFront; dz <= balconyZBack; dz++) {
                painter.place(CX + dx, galleryY, dz, Material.DARK_OAK_PLANKS);
                count++;
            }
        }
        // Перила DARK_OAK_FENCE по 3 краям балкона (передний + 2 боковых).
        for (int dx = -7; dx <= 7; dx++) {
            painter.place(CX + dx, galleryY + 1, balconyZFront, Material.DARK_OAK_FENCE);
            count++;
        }
        for (int dz = balconyZFront; dz <= balconyZBack; dz++) {
            painter.place(CX - 7, galleryY + 1, dz, Material.DARK_OAK_FENCE);
            painter.place(CX + 7, galleryY + 1, dz, Material.DARK_OAK_FENCE);
            count += 2;
        }
        // 4 SOUL_LANTERN по углам балкона + 1 в центре переднего перила (на CHAIN).
        painter.place(CX - 7, galleryY + 2, balconyZFront, Material.SOUL_LANTERN);
        painter.place(CX + 7, galleryY + 2, balconyZFront, Material.SOUL_LANTERN);
        painter.place(CX - 7, galleryY + 2, balconyZBack, Material.SOUL_LANTERN);
        painter.place(CX + 7, galleryY + 2, balconyZBack, Material.SOUL_LANTERN);
        count += 4;
        // 2 трона епископа на y=89 у заднего края балкона, лицом к кресту.
        painter.place(CX - 2, galleryY + 1, balconyZBack, Material.PURPLE_GLAZED_TERRACOTTA);
        painter.place(CX + 2, galleryY + 1, balconyZBack, Material.PURPLE_GLAZED_TERRACOTTA);
        // GOLD_BLOCK-спинки за тронами.
        painter.place(CX - 2, galleryY + 2, balconyZBack, Material.GOLD_BLOCK);
        painter.place(CX + 2, galleryY + 2, balconyZBack, Material.GOLD_BLOCK);
        // LECTERN в центре, лицом на крест (z=-39 → крест на z=-41+).
        BlockData lect = Material.LECTERN.createBlockData();
        if (lect instanceof org.bukkit.block.data.Directional) {
            ((org.bukkit.block.data.Directional) lect).setFacing(BlockFace.NORTH);
        }
        painter.placeData(CX, galleryY + 1, balconyZFront + 1, lect);
        count += 5;

        // PR 3.14: 2 ПАРАДНЫЕ ЛЕСТНИЦЫ из PR 3.12 удалены — они заканчивались
        // в крестовинной зоне (z=0..2), где галереи НЕТ, и выглядели как
        // «лестница в никуда». Замена — buildTriforiumStairs() с реальным
        // соединением пола нефа (y=71) с галереей (y=88) в местах, где
        // галерея ДЕЙСТВИТЕЛЬНО есть.

        return count;
    }

    // =========================================================================
    // ФАЗА 63 (PR 3.14) — ЛЕСТНИЦЫ НА ТРИФОРИЙ-ГАЛЕРЕЮ
    // =========================================================================

    /**
     * 4 угловые лестницы 1×16 ступеней, соединяющие пол нефа (y=71) с
     * trifor-галереей (y=88). По 2 лестницы в каждом конце нефа (юг/север),
     * по обе стороны от центральной оси, прижатые к боковым стенам нефа.
     *
     * <p>Геометрия: каждая лестница идёт параллельно стене (вдоль оси Z),
     * поднимаясь по 1 блоку на шаг. На юге начинается у входа (z=+38),
     * заканчивается на z=+22 (внутри зоны галереи |dz|>7). На севере —
     * аналогично у апсиды (z=-38..-22), приходит на апсидный балкон.
     *
     * <p>Вместе с заполнением под ступенями и перилами по внешнему краю —
     * визуально полноценный мраморный лестничный марш.
     */
    private long buildTriforiumStairs() {
        long count = 0;
        // 4 лестницы: (signX, signZ) = south-west, south-east, north-west, north-east.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                int sx = CX + signX * (HALF_NAVE_W - 2); // x=±13 (1 блок от внутренней стены)
                int startZ = CZ + signZ * 38;            // z=±38 (у торца нефа)
                BlockFace facing = (signZ > 0) ? BlockFace.NORTH : BlockFace.SOUTH;
                for (int step = 0; step < 17; step++) {
                    int y = Y_BASE + 1 + step; // y=72..88
                    int z = startZ - signZ * step;
                    // Поддерживающий стенной блок — POLISHED_BLACKSTONE_BRICKS под ступенью.
                    if (step > 0) {
                        painter.place(sx, y - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        count++;
                    }
                    // Сама ступень.
                    BlockData stair = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
                    if (stair instanceof Stairs) {
                        ((Stairs) stair).setFacing(facing);
                    }
                    painter.placeData(sx, y, z, stair);
                    count++;
                    // Перила DARK_OAK_FENCE на внутренней стороне (где обрыв в неф).
                    int railX = sx - signX; // 1 блок ближе к центру нефа
                    if (step > 0 && step < 16) {
                        painter.place(railX, y + 1, z, Material.DARK_OAK_FENCE);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 52 (PR 3.11) — ПОТОЛОЧНОЕ ОСВЕЩЕНИЕ + GLOWSTONE-ВКРАПЛЕНИЯ
    // =========================================================================

    /**
     * PR 3.11: SHROOMLIGHT-решётка между балками потолка нефа +
     * GLOWSTONE между поперечными арками. Делает интерьер существенно ярче.
     */
    private long buildCeilingLighting() {
        long count = 0;
        int ceilingY = ROOF_PEAK_Y - 5; // y=115 (под потолком)
        // 5 пар SHROOMLIGHT по нефу (на парных z к люстрам).
        int[] zs = { -36, -22, -8, 8, 22, 36 };
        for (int dz : zs) {
            for (int signX : new int[] { -1, +1 }) {
                int gx = CX + signX * 6;
                int gz = CZ + dz;
                painter.place(gx, ceilingY, gz, Material.SHROOMLIGHT);
                count++;
            }
        }
        // GLOWSTONE-вкрапления между поперечными арками (на y=ceilingY+1).
        for (int dz : new int[] { -28, -14, 0, 14, 28 }) {
            painter.place(CX, ceilingY + 1, CZ + dz, Material.GLOWSTONE);
            count++;
        }
        // 2 SOUL_LANTERN над каждой парой колонн (всего 12 шт).
        int[] colZs = { -28, -10, 8, 28 };
        for (int dz : colZs) {
            for (int signX : new int[] { -1, +1 }) {
                int gx = CX + signX * 11;
                int gz = CZ + dz;
                painter.place(gx, ceilingY - 2, gz, Material.SOUL_LANTERN);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 53 (PR 3.11) — ЦЕНТРАЛЬНАЯ БАШНЯ: ОТКРЫТЫЕ АРКИ + 4 BELL + БАЛКОН
    // =========================================================================

    /**
     * PR 3.11 (вариант B): переделка центральной башни из «глухой» в живую.
     * <ul>
     *   <li>Open arches: 4 стрельчатые арки 3×7 у основания (y=72..78) на 4 сторонах.</li>
     *   <li>4 BELL на y=120 (по центрам 4 стен башни).</li>
     *   <li>Viewing platform: пол из POLISHED_BLACKSTONE_BRICK_SLAB на y=130
     *       со скамьями DARK_OAK_STAIRS и витражным окном по сторонам.</li>
     * </ul>
     */
    private long buildCentralTowerBells() {
        long count = 0;
        // 1. Open arches на 4 сторонах башни — ОТКЛЮЧЕНО в PR 3.13.
        //    Старая резка арок 3×7 имела два дефекта: (a) узкое игольное ушко
        //    вместо парадного крестового прохода, (b) AIR на y=71 затирал
        //    RED_CARPET, положенный buildCarpet(). Заменено на
        //    {@link #buildCrossingArches()} — большие 7×13 готические арки с
        //    архивольтом и замковыми камнями, не трогающие пол/ковёр (y≥72).
        // 2. 4 BELL на y=120 (внутри башни, на стенах).
        // Высота 120 = WALL_TOP_Y + HALF_NAVE_W + 2 = 102+15+3=120 (под крышей нефа).
        int bellY = WALL_TOP_Y + 18; // y=120
        for (int side = 0; side < 4; side++) {
            int bx = CX, bz = CZ;
            BlockFace facing = BlockFace.NORTH;
            switch (side) {
                case 0: bz = CZ - 4; facing = BlockFace.NORTH; break;
                case 1: bx = CX + 4; facing = BlockFace.EAST; break;
                case 2: bz = CZ + 4; facing = BlockFace.SOUTH; break;
                case 3: bx = CX - 4; facing = BlockFace.WEST; break;
            }
            BlockData bell = Material.BELL.createBlockData();
            if (bell instanceof Bell) {
                ((Bell) bell).setFacing(facing);
                ((Bell) bell).setAttachment(Bell.Attachment.SINGLE_WALL);
            }
            painter.placeData(bx, bellY, bz, bell);
            count++;
            // CHAIN-подвес над колоколом.
            painter.place(bx, bellY + 1, bz, Material.CHAIN);
            painter.place(bx, bellY + 2, bz, Material.CHAIN);
            count += 2;
        }
        // 3. Viewing platform: пол на y=130.
        int platY = WALL_TOP_Y + 28; // y=130
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockData slab = Material.POLISHED_BLACKSTONE_BRICK_SLAB.createBlockData();
                if (slab instanceof Slab) {
                    ((Slab) slab).setType(Slab.Type.TOP);
                }
                painter.placeData(CX + dx, platY, CZ + dz, slab);
                count++;
            }
        }
        // Скамьи DARK_OAK_STAIRS вдоль 4 стен платформы.
        for (int side = 0; side < 4; side++) {
            for (int i = -3; i <= 3; i++) {
                int sx = CX, sz = CZ;
                BlockFace facing = BlockFace.NORTH;
                switch (side) {
                    case 0: sx = CX + i; sz = CZ - 4; facing = BlockFace.SOUTH; break;
                    case 1: sx = CX + 4; sz = CZ + i; facing = BlockFace.WEST; break;
                    case 2: sx = CX + i; sz = CZ + 4; facing = BlockFace.NORTH; break;
                    case 3: sx = CX - 4; sz = CZ + i; facing = BlockFace.EAST; break;
                }
                BlockData stair = Material.DARK_OAK_STAIRS.createBlockData();
                if (stair instanceof Stairs) {
                    ((Stairs) stair).setFacing(facing);
                }
                painter.placeData(sx, platY + 1, sz, stair);
                count++;
            }
        }
        // 4 SOUL_LANTERN на углах платформы.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                painter.place(CX + signX * 4, platY + 2, CZ + signZ * 4, Material.SOUL_LANTERN);
                count++;
            }
        }
        // 4 PURPLE_GLASS-окна на стенах башни на уровне платформы (y=131..134).
        for (int side = 0; side < 4; side++) {
            int wallX = 0, wallZ = 0;
            if (side == 0) wallZ = -5;
            else if (side == 1) wallX = +5;
            else if (side == 2) wallZ = +5;
            else wallX = -5;
            for (int i = -1; i <= 1; i++) {
                int wx = (side == 1 || side == 3) ? CX + wallX : CX + i;
                int wz = (side == 0 || side == 2) ? CZ + wallZ : CZ + i;
                for (int dy = 1; dy <= 4; dy++) {
                    painter.place(wx, platY + dy, wz, Material.PURPLE_STAINED_GLASS);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 55 (PR 3.12) — ВНЕШНИЙ КРЕСТ НА АПСИДЕ (виден с улицы)
    // =========================================================================

    /**
     * PR 3.12: Большой золотой крест 5×7 на ВНЕШНЕЙ северной стене апсиды
     * (z=-43, выступает на 1 блок наружу). Дополняет внутренний крест на
     * z=-41 (стена апсиды). Виден от северной площади и сверху.
     */
    private long buildExteriorApseCross() {
        long count = 0;
        // Нaружная стена апсиды: z=CZ-HALF_NAVE_L=-42, выступ z=-43.
        int outZ = CZ - HALF_NAVE_L - 1; // z=-43
        // Вертикальная балка креста (7 блоков, y=80..86 центр на y=83).
        int crossCenterY = Y_BASE + 13; // y=83
        for (int dy = -3; dy <= 3; dy++) {
            painter.place(CX, crossCenterY + dy, outZ, Material.GOLD_BLOCK);
            count++;
        }
        // Горизонтальная перекладина (5 блоков, y=84 чуть выше центра).
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(CX + dx, crossCenterY + 1, outZ, Material.GOLD_BLOCK);
            count++;
        }
        // 4 END_ROD-сияния на концах креста.
        painter.place(CX, crossCenterY + 4, outZ, Material.END_ROD);
        painter.place(CX, crossCenterY - 4, outZ, Material.END_ROD);
        painter.place(CX - 3, crossCenterY + 1, outZ, Material.END_ROD);
        painter.place(CX + 3, crossCenterY + 1, outZ, Material.END_ROD);
        count += 4;
        // 4 SOUL_LANTERN по углам внешнего креста на стене (y=82, z=-42).
        int lanternZ = CZ - HALF_NAVE_L; // z=-42 (на самой стене)
        painter.place(CX - 4, crossCenterY, lanternZ, Material.SOUL_LANTERN);
        painter.place(CX + 4, crossCenterY, lanternZ, Material.SOUL_LANTERN);
        painter.place(CX - 4, crossCenterY + 3, lanternZ, Material.SOUL_LANTERN);
        painter.place(CX + 4, crossCenterY + 3, lanternZ, Material.SOUL_LANTERN);
        count += 4;
        return count;
    }

    // =========================================================================
    // ФАЗА 56 (PR 3.12) — FLECHE: МАЛЕНЬКИЙ ШПИЛЬ НА ВЕРХУ АПСИДЫ
    // =========================================================================

    /**
     * PR 3.12: Декоративный fleche (маленький готический шпиль) на верху
     * апсиды (северный конец нефа, z=CZ-39, на коньке нефовой крыши y=119).
     * Решает фидбэк "сверху пустовато сзади". Высота 12 блоков.
     */
    private long buildApseFleche() {
        long count = 0;
        int flecheBaseY = ROOF_PEAK_Y; // y=120 (на коньке нефовой крыши)
        int flecheZ = CZ - HALF_NAVE_L + 3; // z=-39 (над апсидой)
        // База 3×3 POLISHED_BLACKSTONE на коньке.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(CX + dx, flecheBaseY, flecheZ + dz, Material.POLISHED_BLACKSTONE);
                count++;
            }
        }
        // Тело fleche: 8 блоков POLISHED_BLACKSTONE_BRICKS, сужающиеся.
        for (int dy = 1; dy <= 7; dy++) {
            int y = flecheBaseY + dy;
            // 3×3 → 2×2 → 1×1 (сужение).
            int half = (dy <= 3) ? 1 : 0;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    Material mat = (dy % 3 == 0) ? Material.CHISELED_DEEPSLATE
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    painter.place(CX + dx, y, flecheZ + dz, mat);
                    count++;
                }
            }
        }
        // Верхушка: 5 блоков END_ROD-стержня + AMETHYST на пике.
        for (int dy = 8; dy <= 12; dy++) {
            painter.place(CX, flecheBaseY + dy, flecheZ, Material.END_ROD);
            count++;
        }
        painter.place(CX, flecheBaseY + 13, flecheZ, Material.AMETHYST_BLOCK);
        count++;
        // 4 GOLD_BLOCK у основания fleche (декор).
        painter.place(CX - 1, flecheBaseY + 1, flecheZ - 1, Material.GOLD_BLOCK);
        painter.place(CX + 1, flecheBaseY + 1, flecheZ - 1, Material.GOLD_BLOCK);
        painter.place(CX - 1, flecheBaseY + 1, flecheZ + 1, Material.GOLD_BLOCK);
        painter.place(CX + 1, flecheBaseY + 1, flecheZ + 1, Material.GOLD_BLOCK);
        count += 4;
        return count;
    }

    // =========================================================================
    // ФАЗА 57 (PR 3.12) — КОЗЫРЁК (PORCH OVERHANG) НАД ВХОДОМ
    // =========================================================================

    /**
     * PR 3.12: Выступающая крыша-козырёк над южным входом, как в реальных
     * готических соборах. POLISHED_BLACKSTONE_BRICK_STAIRS наклонены вниз
     * к улице, поддерживающие колонны DEEPSLATE_BRICKS у стен.
     */
    private long buildPorchOverhang() {
        long count = 0;
        int porchY = Y_BASE + 25; // y=95 (над аркой портала)
        int southWallZ = CZ + HALF_NAVE_L; // z=27
        // Козырёк: 11×3 STAIRS, наклонены вниз на юг (BlockFace.NORTH = опора с севера).
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                int z = southWallZ + dz;
                BlockData stair = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
                if (stair instanceof Stairs) {
                    ((Stairs) stair).setFacing(BlockFace.NORTH);
                    ((Stairs) stair).setHalf(Bisected.Half.TOP);
                }
                painter.placeData(CX + dx, porchY, z, stair);
                count++;
            }
        }
        // Сплошной "козырёк" блок POLISHED_BLACKSTONE_BRICKS у стены (для массы).
        for (int dx = -5; dx <= 5; dx++) {
            painter.place(CX + dx, porchY, southWallZ, Material.POLISHED_BLACKSTONE_BRICKS);
            count++;
        }
        // 2 поддерживающих колонны (по бокам входа, y=72..94, x=±6, z=28).
        for (int signX : new int[] { -1, +1 }) {
            int cx = CX + signX * 6;
            int cz = southWallZ + 1;
            for (int y = Y_BASE + 1; y <= porchY - 1; y++) {
                Material mat = (y % 4 == 0) ? Material.CHISELED_DEEPSLATE
                        : Material.POLISHED_BLACKSTONE_BRICKS;
                painter.place(cx, y, cz, mat);
                count++;
            }
            // Капители у верха колонн.
            BlockData top = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
            if (top instanceof Stairs) {
                ((Stairs) top).setFacing(signX > 0 ? BlockFace.WEST : BlockFace.EAST);
                ((Stairs) top).setHalf(Bisected.Half.TOP);
            }
            painter.placeData(cx, porchY - 1, cz, top);
            count++;
            // SOUL_LANTERN на CHAIN под козырьком.
            painter.place(cx, porchY - 2, cz, Material.CHAIN);
            painter.place(cx, porchY - 3, cz, Material.SOUL_LANTERN);
            count += 2;
        }
        // Декор: GOLD_BLOCK + END_ROD по центру козырька.
        painter.place(CX, porchY + 1, southWallZ + 1, Material.GOLD_BLOCK);
        painter.place(CX - 1, porchY + 1, southWallZ + 1, Material.END_ROD);
        painter.place(CX + 1, porchY + 1, southWallZ + 1, Material.END_ROD);
        count += 3;
        return count;
    }

    // =========================================================================
    // ФАЗА 58 (PR 3.12) — НИШИ СО СТАТУЯМИ СВЯТЫХ НА ЮЖНОМ ФАСАДЕ
    // =========================================================================

    /**
     * PR 3.12: 4 декоративные ниши на южном фасаде (между порталом и розой,
     * x=±9 и ±13, y=98..103) с маленькими статуями святых внутри
     * (POLISHED_BLACKSTONE-телo + PIGLIN_HEAD-голова + END_ROD-нимб).
     * Решает фидбэк "передний вид пустовато".
     */
    private long buildSaintNiches() {
        long count = 0;
        int southWallZ = CZ + HALF_NAVE_L; // z=27
        // 4 ниши: x=±9 и x=±13, y=98 (середина между порталом y=95 и розой y=113).
        int nicheY = Y_BASE + 28; // y=98
        int[] nicheXs = { -13, -9, 9, 13 };
        for (int nx : nicheXs) {
            int absX = CX + nx;
            // 1. Вырезаем нишу 1×1×3 в стене (z=27 → z=27, но на 1 блок внутрь
            // нет смысла — стена 1 блок толщины. Вместо этого выступ наружу
            // на 1 блок: статуи стоят НА выступе у стены).
            int outZ = southWallZ + 1;
            // Подножие ниши: POLISHED_DEEPSLATE.
            painter.place(absX, nicheY, outZ, Material.POLISHED_DEEPSLATE);
            count++;
            // Тело статуи: POLISHED_BLACKSTONE 2 блока.
            painter.place(absX, nicheY + 1, outZ, Material.POLISHED_BLACKSTONE);
            painter.place(absX, nicheY + 2, outZ, Material.POLISHED_BLACKSTONE);
            count += 2;
            // Голова: PIGLIN_HEAD (декоративная)
            painter.place(absX, nicheY + 3, outZ, Material.PIGLIN_HEAD);
            count++;
            // Нимб END_ROD сверху.
            painter.place(absX, nicheY + 4, outZ, Material.END_ROD);
            count++;
            // Балдахин-аркада: 3 STAIRS POLISHED_BLACKSTONE_BRICK над нишей.
            BlockData topL = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
            BlockData topR = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
            if (topL instanceof Stairs) {
                ((Stairs) topL).setFacing(BlockFace.EAST);
                ((Stairs) topL).setHalf(Bisected.Half.TOP);
            }
            if (topR instanceof Stairs) {
                ((Stairs) topR).setFacing(BlockFace.WEST);
                ((Stairs) topR).setHalf(Bisected.Half.TOP);
            }
            painter.placeData(absX - 1, nicheY + 5, outZ, topL);
            painter.placeData(absX + 1, nicheY + 5, outZ, topR);
            painter.place(absX, nicheY + 5, outZ, Material.CHISELED_DEEPSLATE);
            count += 3;
            // 2 SOUL_LANTERN по бокам ниши на стене.
            painter.place(absX - 1, nicheY + 1, southWallZ, Material.SOUL_LANTERN);
            painter.place(absX + 1, nicheY + 1, southWallZ, Material.SOUL_LANTERN);
            count += 2;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 59 (PR 3.12) — КРОКЕТЫ ПО КОНЬКАМ КРЫШИ
    // =========================================================================

    /**
     * PR 3.12: Декоративные крокеты END_ROD на коньках всех крыш (нефа +
     * трансепта + апсиды). Имитирует кованые шипы на готической крыше.
     * Каждые 5 блоков по коньку.
     */
    private long buildRoofCrockets() {
        long count = 0;
        // PR 3.14: END_ROD заменены на POLISHED_BLACKSTONE_BRICK_WALL —
        // тёмные каменные крокеты вместо светящихся стержней.
        // Нефовый конёк: y=118, x=CX, z=-40..+40 каждые 5.
        int naveRidgeY = WALL_TOP_Y + HALF_NAVE_W + 1; // y=118
        for (int dz = -40; dz <= 40; dz += 5) {
            if (Math.abs(dz) <= CT_HALF) continue; // не наезжать на центр. башню
            painter.place(CX, naveRidgeY + 1, CZ + dz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            count++;
        }
        // Трансептовый конёк: y=110, z=CZ, x=±30..±18 каждые 5.
        int transRidgeY = WALL_TOP_Y + HALF_TRANSEPT_L + 1; // y=110
        for (int signX : new int[] { -1, +1 }) {
            for (int dx = HALF_NAVE_W + 3; dx <= HALF_TRANSEPT_W; dx += 5) {
                painter.place(CX + signX * dx, transRidgeY + 1, CZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 60 (PR 3.13) — БОЛЬШИЕ ГОТИЧЕСКИЕ АРКИ НА КРЕСТОВИНЕ
    // =========================================================================

    /**
     * 4 grand pointed gothic arches at the crossing — one in each side of the
     * 11×11 central tower's lower body. Replaces the previous tiny 3×7 cuts
     * (see disabled section in {@link #buildCentralTowerBells()} which also
     * destroyed the red carpet at y=71).
     *
     * <p>Each arch:
     * <ul>
     *   <li>Width 7 (dx ∈ [-3..+3]) at the wall axis.</li>
     *   <li>Straight portion y=72..81 (10 rows clear).</li>
     *   <li>Stepped pointed top: y=82 width 5, y=83 width 3, y=84 width 1.</li>
     *   <li>Total clear height 13 blocks; floor (y=70) and carpet (y=71) NOT
     *       touched — pre-existing red carpet stays intact through the opening.</li>
     * </ul>
     *
     * <p>Decoration around each arch (placed on the wall blocks that frame the
     * carved opening):
     * <ul>
     *   <li>Step-corners at y=82..84 — {@link Material#CHISELED_DEEPSLATE} on
     *       the wall side that abuts the carved arch (matches the south-portal
     *       arch trim style).</li>
     *   <li>{@link Material#GOLD_BLOCK} keystone at the apex (y=85, dx=0)
     *       with {@link Material#AMETHYST_BLOCK} crown one row higher.</li>
     *   <li>{@link Material#CHISELED_DEEPSLATE} capitals at y=82, dx=±4
     *       (springers — where the straight jamb meets the pointed arch).</li>
     *   <li>{@link Material#SOUL_LANTERN} sconces on {@link Material#CHAIN}-
     *       hung from y=82..78 on dx=±5 (jamb sides, hanging into the crossing).</li>
     * </ul>
     */
    private long buildCrossingArches() {
        long count = 0;
        // 4 sides of the central tower:
        //   side 0 = NORTH (z=CZ-CT_HALF=-20, opens crossing to apse/altar)
        //   side 1 = EAST  (x=CX+CT_HALF=+50, opens crossing to east transept)
        //   side 2 = SOUTH (z=CZ+CT_HALF=-10, opens nave-south to crossing)
        //   side 3 = WEST  (x=CX-CT_HALF=+40, opens crossing to west transept)
        // IMPORTANT: central tower walls are TWO blocks thick (placed at
        //   outer ∈ {CT_HALF-1, CT_HALF} = {4, 5}) — see {@link #buildCentralTower()}
        //   line ~665. We must carve the arch through BOTH layers.
        for (int side = 0; side < 4; side++) {
            boolean alongX = (side == 0 || side == 2); // arch span along X
            int wallSignX = (side == 1) ? +1 : (side == 3) ? -1 : 0;
            int wallSignZ = (side == 0) ? -1 : (side == 2) ? +1 : 0;
            // Two wall layers: outer (at ±CT_HALF) and inner (at ±(CT_HALF-1)).
            int outerOffsetX = wallSignX * CT_HALF;
            int outerOffsetZ = wallSignZ * CT_HALF;
            int innerOffsetX = wallSignX * (CT_HALF - 1);
            int innerOffsetZ = wallSignZ * (CT_HALF - 1);

            // 1. Carve the arch opening through BOTH layers. Straight rectangle 7×10.
            for (int dy = 2; dy <= 11; dy++) { // y=72..81 — NOT y=71 (preserve carpet/floor)
                for (int across = -3; across <= 3; across++) {
                    if (alongX) {
                        painter.place(CX + across, Y_BASE + dy, CZ + outerOffsetZ, Material.AIR);
                        painter.place(CX + across, Y_BASE + dy, CZ + innerOffsetZ, Material.AIR);
                    } else {
                        painter.place(CX + outerOffsetX, Y_BASE + dy, CZ + across, Material.AIR);
                        painter.place(CX + innerOffsetX, Y_BASE + dy, CZ + across, Material.AIR);
                    }
                    count += 2;
                }
            }
            // Stepped pointed top — both layers.
            int[] topWidths = { 5, 3, 1 }; // y=82, 83, 84
            for (int step = 0; step < 3; step++) {
                int dy = 12 + step; // y=82, 83, 84
                int half = (topWidths[step] - 1) / 2;
                for (int across = -half; across <= half; across++) {
                    if (alongX) {
                        painter.place(CX + across, Y_BASE + dy, CZ + outerOffsetZ, Material.AIR);
                        painter.place(CX + across, Y_BASE + dy, CZ + innerOffsetZ, Material.AIR);
                    } else {
                        painter.place(CX + outerOffsetX, Y_BASE + dy, CZ + across, Material.AIR);
                        painter.place(CX + innerOffsetX, Y_BASE + dy, CZ + across, Material.AIR);
                    }
                    count += 2;
                }
            }

            // PR 3.14: archivolt-trim удалён (создавал визуальную «балку в
            // проходе» на y=82..84). Стенки арки остаются чистыми — по краям
            // карведа уже стоят wall-блоки от buildCentralTower; лишний
            // декор только мешал визуальному прочтению прохода.

            // 3. Capitals — springers where straight jamb meets pointed top
            //    (y=82, dx=±4 on BOTH layers).
            for (int signA : new int[] { -1, +1 }) {
                int aa = signA * 4;
                if (alongX) {
                    painter.place(CX + aa, Y_BASE + 12, CZ + outerOffsetZ, Material.CHISELED_DEEPSLATE);
                    painter.place(CX + aa, Y_BASE + 12, CZ + innerOffsetZ, Material.CHISELED_DEEPSLATE);
                } else {
                    painter.place(CX + outerOffsetX, Y_BASE + 12, CZ + aa, Material.CHISELED_DEEPSLATE);
                    painter.place(CX + innerOffsetX, Y_BASE + 12, CZ + aa, Material.CHISELED_DEEPSLATE);
                }
                count += 2;
            }

            // 4. Keystone — GOLD apex on the OUTER face above the pointed top.
            // PR 3.14: END_ROD halo удалён (внешний свет). Сверху AMETHYST как
            // последний акцент.
            int kx = CX + (alongX ? 0 : outerOffsetX);
            int kz = CZ + (alongX ? outerOffsetZ : 0);
            painter.place(kx, Y_BASE + 15, kz, Material.GOLD_BLOCK);
            painter.place(kx, Y_BASE + 16, kz, Material.AMETHYST_BLOCK);
            count += 2;
            // Inner-face mirror keystone — single GOLD_BLOCK on inner layer
            // visible from the crossing.
            int kxi = CX + (alongX ? 0 : innerOffsetX);
            int kzi = CZ + (alongX ? innerOffsetZ : 0);
            painter.place(kxi, Y_BASE + 15, kzi, Material.GOLD_BLOCK);
            count++;

            // 5. SOUL_LANTERN sconces on CHAIN at the jamb corners, hanging
            //    INTO the crossing from y=82 down to y=78 (head height).
            //    Anchor 1 block inside the inner wall layer (toward crossing
            //    centre). For south arch (wallSignZ=+1, alongX): sconce at
            //    z = CZ + innerOffsetZ - wallSignZ = CZ + 4 - 1 = CZ+3 (inside
            //    the central tower's hollow), on dx=±5 (the original outer
            //    corner — but that's a CORNER block, place 1 step inside =
            //    dx=±4 instead).
            for (int signA : new int[] { -1, +1 }) {
                int aa = signA * 4; // sit just inside the corner, on the jamb
                int sx, sz;
                if (alongX) {
                    sx = CX + aa;
                    sz = CZ + innerOffsetZ - wallSignZ; // 1 step into the crossing from inner wall
                } else {
                    sx = CX + innerOffsetX - wallSignX;
                    sz = CZ + aa;
                }
                // Chain ladder y=82..80, lantern at y=79.
                painter.place(sx, Y_BASE + 12, sz, Material.CHAIN);
                painter.place(sx, Y_BASE + 11, sz, Material.CHAIN);
                painter.place(sx, Y_BASE + 10, sz, Material.CHAIN);
                painter.place(sx, Y_BASE + 9, sz, Material.SOUL_LANTERN);
                count += 4;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 61 (PR 3.13) — ИНТЕРЬЕР ЛАНТЕРНОЙ БАШНИ (видно с пола крестовины)
    // =========================================================================

    /**
     * Decorates the inside of the central tower (cross-tower) so that when a
     * player stands at the crossing and looks up they see a striking gothic
     * lantern: a hanging crown chandelier, vault ribs converging at the
     * lantern's spring point, and amethyst relief on the inner walls.
     *
     * <p>The lantern body is a 7×7 hollow column from y=72 (above floor) up to
     * y=136 (below the body top y=137 / spire start y=138). Without decoration
     * this is just empty air — adding the elements below makes the upward view
     * the centerpiece of the cathedral.
     *
     * <ul>
     *   <li><b>Hanging crown chandelier</b> — 4 CHAIN strands from the lantern
     *       ceiling at y=130 down to a GOLD_BLOCK ring at y=110, with a
     *       SHROOMLIGHT centre + 8 SOUL_LANTERN around the ring + 4 END_ROD
     *       upspikes. Reads as a parade crown floating ~40 blocks above the
     *       crossing floor.</li>
     *   <li><b>Vault ribs at y=92</b> — 4 diagonal POLISHED_BLACKSTONE_BRICK_WALL
     *       ribs from each inner corner of the lantern (CX±3, CZ±3) toward
     *       the centre, meeting at a CHISELED_DEEPSLATE keystone at (CX, 92, CZ).
     *       Visually the spring point of the lantern's inner vault.</li>
     *   <li><b>Inner-wall purple lancets</b> — 4 narrow 1×6 PURPLE_STAINED_GLASS
     *       lancets on the inner faces of the lantern walls (y=86..91) with
     *       AMETHYST_BLOCK crowns. Layered in front of the existing lantern
     *       windows at y=88..100 (which sit on the OUTER face). Light passes
     *       through both.</li>
     *   <li><b>Amethyst relief</b> — 12 AMETHYST_CLUSTER blocks on the inner
     *       walls at y=80..86 (sparkly purple speckle catching torchlight).</li>
     *   <li><b>Inner-corner pilasters</b> — 4 thin END_ROD vertical accents
     *       running from y=82 to y=92 at (CX±3, CZ±3). Light shafts up the
     *       lantern.</li>
     * </ul>
     */
    private long buildLanternInterior() {
        long count = 0;
        // 1. Hanging crown chandelier.
        int crownY = 110;       // ring height
        int chainTop = 136;     // PR 3.14: extended to y=136 (1 below ceiling y=137) to fix the visible "hole".
        // 4 chain strands from chainTop down to crownY.
        int[][] chainXZ = { { -2, 0 }, { +2, 0 }, { 0, -2 }, { 0, +2 } };
        for (int[] cxcz : chainXZ) {
            for (int y = crownY + 1; y <= chainTop; y++) {
                painter.place(CX + cxcz[0], y, CZ + cxcz[1], Material.CHAIN);
                count++;
            }
        }
        // PR 3.14: ceiling rosette/medallion at y=137 — anchors the chains
        // visually so they no longer dangle into a "hole" in the ceiling.
        // 5×5 plate centred on (CX, 137, CZ): GOLD_BLOCK ring + CHISELED_DEEPSLATE
        // corners + central AMETHYST pip.
        int ceilY = 137;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int adx = Math.abs(dx), adz = Math.abs(dz);
                int outer = Math.max(adx, adz);
                Material mat;
                if (outer == 0) {
                    mat = Material.AMETHYST_BLOCK;
                } else if (outer == 1) {
                    mat = Material.GOLD_BLOCK;
                } else if (adx == 2 && adz == 2) {
                    mat = Material.CHISELED_DEEPSLATE;
                } else {
                    mat = Material.POLISHED_BLACKSTONE_BRICKS;
                }
                painter.place(CX + dx, ceilY, CZ + dz, mat);
                count++;
            }
        }
        // GOLD_BLOCK ring at crownY — 8 blocks forming a ring of radius 2.
        int[][] ringXZ = {
                { -2, -1 }, { -2, 0 }, { -2, +1 },
                { +2, -1 }, { +2, 0 }, { +2, +1 },
                { -1, -2 }, {  0, -2 }, { +1, -2 },
                { -1, +2 }, {  0, +2 }, { +1, +2 },
        };
        for (int[] r : ringXZ) {
            painter.place(CX + r[0], crownY, CZ + r[1], Material.GOLD_BLOCK);
            count++;
        }
        // SHROOMLIGHT centre at crownY (visible glow disk).
        painter.place(CX, crownY, CZ, Material.SHROOMLIGHT);
        count++;
        // 8 SOUL_LANTERN one row below ring (hanging like pendants).
        int[][] lanternXZ = {
                { -2, -1 }, { -2, +1 }, { +2, -1 }, { +2, +1 },
                { -1, -2 }, { +1, -2 }, { -1, +2 }, { +1, +2 },
        };
        for (int[] l : lanternXZ) {
            painter.place(CX + l[0], crownY - 1, CZ + l[1], Material.SOUL_LANTERN);
            count++;
        }
        // 4 END_ROD upspikes from the ring corners (y=crownY+1).
        int[][] spikes = { { -2, 0 }, { +2, 0 }, { 0, -2 }, { 0, +2 } };
        // (those are the chain points — END_ROD goes one block above ring on
        //  the cardinal corners of the ring, which are NOT under chains: use
        //  the diagonal positions of the ring.)
        int[][] spikePositions = { { -2, -1 }, { +2, +1 }, { +2, -1 }, { -2, +1 } };
        for (int[] s : spikePositions) {
            painter.place(CX + s[0], crownY + 1, CZ + s[1], Material.END_ROD);
            count++;
        }
        // (silence unused — keep "spikes" var for documentation)
        if (spikes.length == 0) { /* no-op */ }

        // 2. Vault ribs at y=92 — 4 diagonal walls converging at centre.
        //    From inner corner (CX±3, CZ±3) along diagonal to (CX, CZ).
        int ribY = Y_BASE + 22; // y=92
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                for (int t = 1; t <= 3; t++) {
                    int rx = CX + signX * t;
                    int rz = CZ + signZ * t;
                    painter.place(rx, ribY, rz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    count++;
                }
            }
        }
        // Centre keystone at (CX, ribY, CZ) — CHISELED_DEEPSLATE.
        painter.place(CX, ribY, CZ, Material.CHISELED_DEEPSLATE);
        count++;
        // 4 cardinal ribs from inner wall (CX±3 at axis 0, CZ±3 at axis 0)
        // converging to the keystone — short 3-block walls along the cardinal
        // axes at ribY.
        for (int signA : new int[] { -1, +1 }) {
            for (int t = 1; t <= 3; t++) {
                painter.place(CX + signA * t, ribY, CZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                painter.place(CX, ribY, CZ + signA * t, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                count += 2;
            }
        }

        // 3. Inner-wall purple lancets — 4 sides, each 1×6 PURPLE_GLASS at
        //    y=86..91 (ABOVE the new crossing arch keystones at y=85..87,
        //    BELOW the existing outer lantern windows at y=88..100).
        for (int side = 0; side < 4; side++) {
            boolean alongX = (side == 0 || side == 2);
            int wallX = (side == 1) ? +(CT_HALF - 1) : (side == 3) ? -(CT_HALF - 1) : 0;
            int wallZ = (side == 0) ? -(CT_HALF - 1) : (side == 2) ? +(CT_HALF - 1) : 0;
            // 2 lancets per side, on dx=±1 (inner-wall offset 1 block from corner).
            for (int slot : new int[] { -1, +1 }) {
                int wx = CX + (alongX ? slot : wallX);
                int wz = CZ + (alongX ? wallZ : slot);
                for (int dy = 16; dy <= 21; dy++) { // y=86..91
                    painter.place(wx, Y_BASE + dy, wz, Material.PURPLE_STAINED_GLASS);
                    count++;
                }
                // AMETHYST crown above lancet (y=92 — sits on the rib ring).
                // Skip if it would overwrite the cardinal rib already placed.
                if (slot != 0) {
                    painter.place(wx, Y_BASE + 22, wz, Material.AMETHYST_BLOCK);
                    count++;
                }
            }
        }

        // 4. Amethyst relief — replace 12 inner-wall-layer blocks with
        //    AMETHYST_BLOCK at varying heights y∈{80, 83, 86}. Inner wall is
        //    at outer=CT_HALF-1=4; positions across the wall span [-3..+3].
        //    3 reliefs per side × 4 sides = 12 amethyst pips on the lantern
        //    interior — visible from below as purple chevrons in the masonry.
        int[] reliefAcross = { -3, 0, +3 };
        int[] reliefY = { 80, 83, 86 };
        for (int side = 0; side < 4; side++) {
            boolean alongX = (side == 0 || side == 2);
            int wsX = (side == 1) ? +1 : (side == 3) ? -1 : 0;
            int wsZ = (side == 0) ? -1 : (side == 2) ? +1 : 0;
            int innerX = wsX * (CT_HALF - 1);
            int innerZ = wsZ * (CT_HALF - 1);
            for (int i = 0; i < 3; i++) {
                int across = reliefAcross[i];
                int rx = CX + (alongX ? across : innerX);
                int rz = CZ + (alongX ? innerZ : across);
                painter.place(rx, reliefY[i], rz, Material.AMETHYST_BLOCK);
                count++;
            }
        }

        // 5. Inner-corner pilasters — END_ROD light shafts at the 4 inner
        //    corners of the lantern (CX±3, CZ±3) from y=82 up to y=91.
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                for (int dy = 12; dy <= 21; dy += 3) { // y=82, 85, 88, 91
                    painter.place(CX + signX * 3, Y_BASE + dy, CZ + signZ * 3, Material.END_ROD);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 62 (PR 3.13) — МОЗАИКА-ЗВЕЗДА НА ПОЛУ ПЕРЕСЕЧЕНИЯ
    // =========================================================================

    /**
     * 9×9 mosaic star at the crossing centre (CX, Y_BASE, CZ) — replaces the
     * original {@link #buildPulpit()} central platform's amethyst+purpur
     * geometry with a wider, more readable rosette. The rosette is rendered
     * AT FLOOR LEVEL (y=Y_BASE) so the player walks ON it (no height
     * obstruction); the small platform at y=Y_BASE+1 from {@code buildPulpit}
     * is no longer placed (we removed the END_ROD obstacle in PR 3.13).
     *
     * <p>Layout (concentric, distance = Chebyshev max(|dx|, |dz|)):
     * <ul>
     *   <li>d=0 (centre 1×1): {@link Material#AMETHYST_BLOCK}.</li>
     *   <li>d=1 cardinal (4 blocks): {@link Material#PURPUR_PILLAR}.</li>
     *   <li>d=1 diagonal (4 blocks): {@link Material#GOLD_BLOCK} (sparkles
     *       under chandelier light).</li>
     *   <li>d=2 cardinal: {@link Material#PURPUR_BLOCK}.</li>
     *   <li>d=2 diagonal: {@link Material#DEEPSLATE_BRICKS}.</li>
     *   <li>d=3 (frame): {@link Material#POLISHED_BLACKSTONE_BRICKS}
     *       cardinals + {@link Material#CHISELED_DEEPSLATE} corners.</li>
     *   <li>d=4 (corners only): END_ROD-style points → just frame stays
     *       deepslate; ring d=4 not drawn (size limit 9×9 covered by central
     *       7×7 already from {@code buildFloor}'s mosaic).</li>
     * </ul>
     */
    private long buildCrossingFloorStar() {
        long count = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int adx = Math.abs(dx), adz = Math.abs(dz);
                int dist = Math.max(adx, adz);
                boolean cardinal = (dx == 0) ^ (dz == 0); // exactly one axis is zero
                boolean centre = (dx == 0 && dz == 0);
                Material mat;
                if (centre) {
                    mat = Material.AMETHYST_BLOCK;
                } else if (dist == 1 && cardinal) {
                    mat = Material.PURPUR_PILLAR;
                } else if (dist == 1) { // diagonal d=1
                    mat = Material.GOLD_BLOCK;
                } else if (dist == 2 && cardinal) {
                    mat = Material.PURPUR_BLOCK;
                } else if (dist == 2) {
                    mat = Material.DEEPSLATE_BRICKS;
                } else if (dist == 3 && cardinal) {
                    mat = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (dist == 3 && adx == 3 && adz == 3) {
                    mat = Material.CHISELED_DEEPSLATE;
                } else {
                    // edge of the 7×7 outer ring (d=3, non-corner non-cardinal)
                    mat = Material.DEEPSLATE_BRICKS;
                }
                painter.place(CX + dx, Y_BASE, CZ + dz, mat);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 63 (PR 3.13) — КРЕСТОВЫЙ КОВЁР: ВОСТОК-ЗАПАД ЧЕРЕЗ ТРАНСЕПТ
    // =========================================================================

    /**
     * Adds a 3-wide RED_CARPET strip running east-west across the transept,
     * from x=CX-(HALF_TRANSEPT_W-2) to x=CX+(HALF_TRANSEPT_W-2) at z=CZ-1..+1.
     * Combined with the existing north-south carpet from {@link #buildCarpet()}
     * this forms a CROSS of carpets centered on the crossing — visually
     * reinforcing the cruciform plan and tying the 4 new arches together.
     *
     * <p>The rosette from {@link #buildCrossingFloorStar()} sits at y=Y_BASE
     * (floor level); the carpet sits at y=Y_BASE+1 (on top of the floor).
     * Both can coexist — the rosette's centre AMETHYST is visually covered by
     * the carpet at the crossing, but at the corners of the rosette (dx=±2/3
     * with dz=0 outside the 3-wide carpet strip) the mosaic remains visible.
     *
     * <p>Range avoids the altar approach and the south portal (those are
     * already covered by the north-south carpet).
     */
    private long buildCrossCarpet() {
        long count = 0;
        int xMin = -(HALF_TRANSEPT_W - 2); // x=-28 (just inside west transept end)
        int xMax = +(HALF_TRANSEPT_W - 2); // x=+28
        for (int dx = xMin; dx <= xMax; dx++) {
            for (int dz = -1; dz <= +1; dz++) {
                painter.place(CX + dx, Y_BASE + 1, CZ + dz, Material.RED_CARPET);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 64 (PR 3.13) — ВИСЯЧИЕ ФОНАРИ В УГЛАХ КРЕСТОВИНЫ
    // =========================================================================

    /**
     * Adds 4 long CHAIN+SOUL_LANTERN pendants in the 4 inner corners of the
     * crossing (just inside the central tower's lower body, in the transept
     * wings). They hang from y=85 (above the crossing arch keystones) down to
     * y=78 (head height for a player on the crossing floor) — bright purple
     * fire glow at eye level frames the crossing nicely without blocking
     * the new wide arches.
     *
     * <p>Anchor positions: (CX±(CT_HALF-1), CZ±(CT_HALF-1)) = (CX±4, CZ±4) —
     * inner corners of the lantern body, just inside the 7×7 hollow.
     */
    private long buildCrossingHangingLanterns() {
        long count = 0;
        for (int signX : new int[] { -1, +1 }) {
            for (int signZ : new int[] { -1, +1 }) {
                int hx = CX + signX * (CT_HALF - 1); // ±4
                int hz = CZ + signZ * (CT_HALF - 1);
                // Chain from y=85 down to y=79.
                for (int y = Y_BASE + 9; y <= Y_BASE + 15; y++) {
                    painter.place(hx, y, hz, Material.CHAIN);
                    count++;
                }
                // SOUL_LANTERN at y=78 (1 block below chain).
                painter.place(hx, Y_BASE + 8, hz, Material.SOUL_LANTERN);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 65 (PR 3.14) — КАРНИЗ-КОЗЫРЁК ПО ПЕРИМЕТРУ (Issue #1)
    // =========================================================================

    /**
     * Элегантный готический карниз по верхней кромке стен нефа и трансепта
     * (y=WALL_TOP_Y, y=WALL_TOP_Y+1). Создаёт видимый «вынос» крыши за
     * стену — то самое «красивый козырёк», о котором просил пользователь
     * вместо отдельного шпиля-флешь.
     *
     * <p>Рисуется одной строкой POLISHED_BLACKSTONE_BRICK_STAIRS (вверх-ногами,
     * лицом наружу) на y=WALL_TOP_Y и DARK_OAK_SLAB (top type) на
     * y=WALL_TOP_Y+1, в положении 1 блок ОТ стены наружу — так стропила
     * деревянной крыши визуально нависают над каменной стеной с тёмной
     * каменной отделкой снизу.
     */
    private long buildEavesCornice() {
        long count = 0;
        int corniceY = WALL_TOP_Y;       // y=102
        int trimY    = WALL_TOP_Y + 1;   // y=103
        // Контрфорсы нефа (skip-set по dz): x=±16 на dz=-32,-18,+18,+32.
        java.util.Set<Integer> naveButtressDz = new java.util.HashSet<>();
        for (int v : new int[] { -32, -18, 18, 32 }) naveButtressDz.add(v);
        // Контрфорсы трансепта (skip-set по dx на front-стене z=±8): dx=±22.
        java.util.Set<Integer> transeptButtressDx = new java.util.HashSet<>();
        for (int v : new int[] { -22, 22 }) transeptButtressDx.add(v);

        // Карниз по длинным стенам нефа (восток/запад), кроме крыльев трансепта.
        for (int signX : new int[] { -1, +1 }) {
            int outX = CX + signX * (HALF_NAVE_W + 1); // x=±16
            BlockFace face = (signX > 0) ? BlockFace.WEST : BlockFace.EAST; // лицом ВНУТРЬ собора
            for (int dz = -HALF_NAVE_L; dz <= HALF_NAVE_L; dz++) {
                if (Math.abs(dz) <= HALF_TRANSEPT_L) continue; // у крыла трансепта своя стена
                if (naveButtressDz.contains(dz)) continue;     // не накрывать пинакли контрфорсов
                int z = CZ + dz;
                placeCornice(outX, corniceY, z, face);
                placeWoodEave(outX, trimY, z);
                count += 2;
            }
        }
        // Карниз по торцевым стенам трансепта (восток/запад).
        for (int signX : new int[] { -1, +1 }) {
            int outX = CX + signX * (HALF_TRANSEPT_W + 1); // x=±31
            BlockFace face = (signX > 0) ? BlockFace.WEST : BlockFace.EAST;
            for (int dz = -HALF_TRANSEPT_L; dz <= HALF_TRANSEPT_L; dz++) {
                int z = CZ + dz;
                placeCornice(outX, corniceY, z, face);
                placeWoodEave(outX, trimY, z);
                count += 2;
            }
        }
        // Карниз по передней (южной) стене трансепта между нефом и торцом.
        for (int signZ : new int[] { -1, +1 }) {
            int outZ = CZ + signZ * (HALF_TRANSEPT_L + 1); // z=±8
            BlockFace face = (signZ > 0) ? BlockFace.NORTH : BlockFace.SOUTH;
            for (int signX : new int[] { -1, +1 }) {
                for (int dx = HALF_NAVE_W + 1; dx <= HALF_TRANSEPT_W; dx++) {
                    int signedDx = signX * dx;
                    if (transeptButtressDx.contains(signedDx)) continue;
                    int x = CX + signedDx;
                    placeCornice(x, corniceY, outZ, face);
                    placeWoodEave(x, trimY, outZ);
                    count += 2;
                }
            }
        }
        return count;
    }

    private void placeCornice(int x, int y, int z, BlockFace facing) {
        Stairs s = (Stairs) Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
        s.setFacing(facing);
        s.setHalf(Bisected.Half.TOP); // upside-down — slope ВНИЗ-НАРУЖУ
        painter.placeData(x, y, z, s);
    }

    private void placeWoodEave(int x, int y, int z) {
        Slab top = (Slab) Material.DARK_OAK_SLAB.createBlockData();
        top.setType(Slab.Type.TOP);
        painter.placeData(x, y, z, top);
    }

    // =========================================================================
    // ФАЗА 66 (PR 3.14) — РАСШИРЕННЫЕ СКАМЬИ (Issue #9)
    // =========================================================================

    /**
     * Дополняет существующие скамьи buildPews() более плотным ритмом по нефу
     * + симметричные ряды в боковых рукавах трансепта. Каждый ряд скамьи —
     * 5-блоковое сидение DARK_OAK_SLAB с торцевой спинкой DARK_OAK_FENCE.
     */
    private long buildPewsExtended() {
        long count = 0;
        // 4 дополнительных ряда скамей в нефе. Существующие — на
        // z ∈ {-34,-30,-22,-18,18,22,30,34}. Хор — z=-32..-25, алтарь —
        // z=-40..-36. Безопасные промежутки: z=-14, -10, 10, 14.
        int[] newNaveZs = { -14, -10, 10, 14 };
        for (int dz : newNaveZs) {
            for (int side : new int[] { -1, +1 }) {
                int dxStart = (side == -1) ? -7 : 3;
                int dxEnd   = (side == -1) ? -3 : 7;
                for (int dx = dxStart; dx <= dxEnd; dx++) {
                    Slab slab = (Slab) Material.DARK_OAK_SLAB.createBlockData();
                    slab.setType(Slab.Type.BOTTOM);
                    painter.placeData(CX + dx, Y_BASE + 1, CZ + dz, slab);
                    count++;
                }
                int backDx = (side == -1) ? -7 : 7;
                painter.place(CX + backDx, Y_BASE + 2, CZ + dz, Material.DARK_OAK_FENCE);
                count++;
            }
        }
        // Скамьи в боковых рукавах трансепта.
        // 4 одиночные скамьи в каждом крыле (запад/восток): по 2 ряда севернее
        // и южнее крестового ковра (ковёр на z ∈ {-1,0,+1}).
        // Скамья — короткая (2 блока по z), на фиксированном x, ось z=-3..-2
        // (севернее ковра) и z=+2..+3 (южнее ковра).
        for (int signX : new int[] { -1, +1 }) {
            // 4 X-позиции в каждом крыле: dx0 = ±9, ±13, ±17, ±21
            for (int idx = 0; idx < 4; idx++) {
                int dx0 = signX * (9 + idx * 4);
                if (Math.abs(dx0) > HALF_TRANSEPT_W - 3) continue;
                int x = CX + dx0;
                for (int sign : new int[] { -1, +1 }) {
                    int dz1 = sign * 2;
                    int dz2 = sign * 3;
                    Slab slab = (Slab) Material.DARK_OAK_SLAB.createBlockData();
                    slab.setType(Slab.Type.BOTTOM);
                    painter.placeData(x, Y_BASE + 1, CZ + dz1, slab);
                    painter.placeData(x, Y_BASE + 1, CZ + dz2, slab);
                    count += 2;
                    // Спинка на самом внешнем z.
                    painter.place(x, Y_BASE + 2, CZ + dz2, Material.DARK_OAK_FENCE);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 67 (PR 3.14) — СВЕЧИ-СКОНСЫ НА ВНУТРЕННИХ СТЕНАХ (Issue #9)
    // =========================================================================

    /**
     * Свечи (CANDLE) на каменных кронштейнах вдоль внутренней поверхности
     * боковых стен нефа на высоте ~y=78. Каждые 6 блоков по нефу — пара
     * сконсов лево/право. Создаёт интимный «литургический» свет на скамьях.
     */
    private long buildWallCandles() {
        long count = 0;
        int sconceY = Y_BASE + 8;  // y=78
        for (int dz = -36; dz <= 36; dz += 6) {
            if (Math.abs(dz) <= HALF_TRANSEPT_L + 1) continue; // не накладывать на крестовинную зону
            for (int signX : new int[] { -1, +1 }) {
                int wx = CX + signX * (HALF_NAVE_W - 1); // x=±14, 1 блок внутри стены
                int wz = CZ + dz;
                // Кронштейн — POLISHED_BLACKSTONE_BRICK_WALL.
                painter.place(wx, sconceY, wz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                // 3 свечи (CANDLE с lit=true).
                BlockData candle = Material.CANDLE.createBlockData("[candles=3,lit=true]");
                painter.placeData(wx, sconceY + 1, wz, candle);
                count += 2;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 68 (PR 3.14) — ОРГАН В АПСИДЕ (Issue #9)
    // =========================================================================

    /**
     * Декоративный готический орган за алтарём в апсиде. 7 «труб» из
     * COPPER_BLOCK / EXPOSED_COPPER / WAXED_COPPER разной высоты, обрамлённые
     * DARK_OAK_PLANKS и POLISHED_BLACKSTONE_BRICKS — характерный силуэт,
     * который читается с южного входа.
     *
     * <p>Расположен ПОД триформ-балконом (балкон на y=88, орган высотой до
     * y=84) на z=-37..-36 (вплотную к северной стене), x=-3..+3.
     */
    private long buildApseOrgan() {
        long count = 0;
        // Орган стоит за тронами епископа (z=-40), у северной стены апсиды.
        // Верхушка не должна заходить в зону трифориум-балкона (y=88, z=-39..-36).
        int organZ = CZ - HALF_NAVE_L + 1; // z=-41 (вплотную к северной стене)
        int baseY  = Y_BASE + 1;           // y=71 (на полу)
        // База органа — DARK_OAK_PLANKS пьедестал 7 широкий × 1 глубокий.
        for (int dx = -3; dx <= 3; dx++) {
            painter.place(CX + dx, baseY, organZ, Material.DARK_OAK_PLANKS);
            count++;
        }
        // POLISHED_BLACKSTONE_BRICKS-кант базы (на 1 выше).
        for (int dx = -3; dx <= 3; dx++) {
            painter.place(CX + dx, baseY + 1, organZ, Material.POLISHED_BLACKSTONE_BRICKS);
            count++;
        }
        // 7 «труб» из меди разной патины, высоты 14,12,10,8,10,12,14 (симметрично).
        int[] pipeHeights = { 14, 12, 10, 8, 10, 12, 14 };
        Material[] pipeMats = {
                Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER,
                Material.WAXED_OXIDIZED_COPPER,
                Material.WEATHERED_COPPER, Material.EXPOSED_COPPER, Material.COPPER_BLOCK
        };
        for (int i = 0; i < 7; i++) {
            int dx = i - 3;
            for (int dy = 0; dy < pipeHeights[i]; dy++) {
                painter.place(CX + dx, baseY + 2 + dy, organZ, pipeMats[i]);
                count++;
            }
            // Капитель трубы — CHISELED_DEEPSLATE.
            painter.place(CX + dx, baseY + 2 + pipeHeights[i], organZ, Material.CHISELED_DEEPSLATE);
            count++;
        }
        // 5 декоративных NOTE_BLOCK перед трубами (визуальная клавиатура).
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(CX + dx, baseY + 1, organZ + 1, Material.NOTE_BLOCK);
            count++;
        }
        // 2 SOUL_LANTERN на цепях по бокам органа (на y=85..89).
        for (int signX : new int[] { -1, +1 }) {
            int sx = CX + signX * 4;
            for (int dy = 0; dy < 4; dy++) {
                painter.place(sx, baseY + 14 + dy, organZ, Material.CHAIN);
                count++;
            }
            painter.place(sx, baseY + 13, organZ, Material.SOUL_LANTERN);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 69 (PR 3.15) — ЗАПОЛНЕНИЕ КЛИНА КРЕСТОВИНЫ (фикс «дырявой крыши»)
    // =========================================================================

    /**
     * Закрывает «клин» воздуха между нефовым eave (y=102..103 на x=±15) и
     * трансептовым гребнем (y=109..110 на x=±16..±30, z=0). Эти зоны после
     * {@link #buildRoof()} оставались разорваны — нефовый скат не дотягивался
     * до трансептового хребта, между ними зияла треугольная щель видимая
     * как изнутри (виден интерьер сквозь крышу), так и снаружи.
     *
     * <p>Алгоритм: для каждой клетки (dx,dz) в зоне крестовины
     * (|dx|≤HALF_NAVE_W, |dz|≤HALF_TRANSEPT_L), кроме башенного 11×11,
     * вычисляется унифицированная высота — максимум нефового и трансептового
     * скатов. Если унифицированная выше существующего fat-слоя нефа, столб
     * добивается каменной готической стенкой вверх до этого уровня.
     *
     * <p>Архитектурно — это «фронтон трансептового рукава со стороны
     * крестовины»: каменная стена закрывает торец трансептового конька.
     */
    private long buildCrossingRoofFill() {
        long count = 0;
        for (int dx = -HALF_NAVE_W; dx <= HALF_NAVE_W; dx++) {
            for (int dz = -HALF_TRANSEPT_L; dz <= HALF_TRANSEPT_L; dz++) {
                int adx = Math.abs(dx), adz = Math.abs(dz);
                // Зона центральной башни — её тело покрывает крышу собственной
                // массивной стеной, заполнение там не нужно.
                if (adx <= CT_HALF && adz <= CT_HALF) continue;
                int naveH = HALF_NAVE_W - adx;       // 0..15
                int transH = HALF_TRANSEPT_L - adz;  // 0..7
                int unifiedH = Math.max(naveH, transH);
                // Существующая макушка нефа в этой колонке — main + fat
                // (fat есть везде, кроме самой вершины dx=0).
                int existingTopH = (naveH == HALF_NAVE_W) ? naveH : naveH + 1;
                if (unifiedH <= existingTopH) continue; // нефовый fat уже выше унифицированной — пропускаем
                for (int h = existingTopH + 1; h <= unifiedH; h++) {
                    int y = WALL_TOP_Y + h;
                    Material mat = ((dx + dz + h) & 1) == 0
                            ? Material.DEEPSLATE_BRICKS
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                    painter.place(CX + dx, y, CZ + dz, mat);
                    count++;
                }
            }
        }
        return count;
    }
}
