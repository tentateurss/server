package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Городская стена Эликия: трассирует {@link WorldGenerator#CITY_POLYGON} (22
 * вершины) толстой каменной кладкой высотой 9 блоков, с зубцами наверху,
 * круглыми башнями через ~20 блоков и арками 4 ворот.
 *
 * <p><b>Геометрия стены</b>:
 * <ul>
 *   <li>Полигон обходится по часовой стрелке (Bresenham line по каждому ребру).</li>
 *   <li>Толщина: 3 блока. На каждой клетке полигональной линии ставятся
 *       3 блока перпендикулярно направлению ребра — один на самой линии,
 *       один наружу, один внутрь. Это даёт визуально массивную стену
 *       без щелей на углах.</li>
 *   <li>Высота: 9 блоков (y=70..78). Фундамент y=70 — COBBLED_DEEPSLATE,
 *       тело y=71..77 — DEEPSLATE_BRICKS, верх y=78 —
 *       DEEPSLATE_TILES (карниз).</li>
 *   <li>Зубцы (crenellations): чередующиеся блоки
 *       DEEPSLATE_BRICK_WALL на y=79 (через клетку — щель воздуха),
 *       только на наружной и центральной линии. Внутренний ряд y=79
 *       сплошной — чтобы по стене можно было ходить.</li>
 * </ul>
 *
 * <p><b>Башни</b>:
 * <ul>
 *   <li>Расставляются по периметру через 18-22 блока (с небольшим
 *       рандомом). Принцип «бегущей дистанции»: пока идём по полигону,
 *       копим путь; как набралось ≥nextSpacing — ставим башню и сбрасываем.</li>
 *   <li>Каждая башня — круглая, диаметр 5 (радиус 2), высота 14
 *       (y=70..83). Внешние стенки COBBLED_DEEPSLATE + полоса
 *       DEEPSLATE_TILES на y=83. Внутренний полый объём.</li>
 *   <li>Крыша: 2 уровня DEEPSLATE_BRICK_STAIRS, образующие конус.
 *       На вершине FLOWER_POT (декоративный) или END_ROD как мини-маяк.</li>
 *   <li>На уровне y=76 в каждой башне — 4 бойницы (снять блок стены).</li>
 * </ul>
 *
 * <p><b>Ворота</b>: 4 штуки в координатах
 * {@link WorldGenerator#SOUTH_GATE}/{@link WorldGenerator#NORTH_GATE}/
 * {@link WorldGenerator#EAST_GATE}/{@link WorldGenerator#WEST_GATE}.
 * Около каждой точки в радиусе 2 (Manhattan) клетки полигональной линии
 * пропускаются — это создаёт проём 5×7 в стене. Над проёмом надстраивается
 * каменная арка DEEPSLATE_TILES высотой y=77..78. По бокам ворот
 * — массивные пилоны 3×3×11 (DEEPSLATE_BRICKS), с факелами на y=78.
 *
 * <p><b>Что НЕ делает этот класс</b>:
 * <ul>
 *   <li>Собор (PR 3), здания (PR 4), улицы (PR 4), декорации/FloatingText
 *       (PR 5), внешние биомы (PR 5).</li>
 *   <li>Каркас полигона уже замощен POLISHED_DEEPSLATE на y=70 в Phase 1
 *       {@link WorldGenerator}; этот класс только надстраивает над ним
 *       стену + башни.</li>
 * </ul>
 */
public final class ElikiumWall {

    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70
    private static final int WALL_HEIGHT = 27;       // y=70..96
    private static final int WALL_TOP_Y = Y_BASE + WALL_HEIGHT - 1; // 96
    private static final int CRENEL_Y   = Y_BASE + WALL_HEIGHT;     // 97
    /** Полутолщина стены: общая толщина = 2*HALF + 1 = 9. */
    private static final int WALL_HALF_THICKNESS = 4;

    private static final int TOWER_HEIGHT = 42; // y=70..111
    private static final int TOWER_TOP_Y  = Y_BASE + TOWER_HEIGHT - 1; // 111
    private static final int TOWER_RADIUS = 7;
    /** Внутренняя пустота башни. */
    private static final int TOWER_INNER_R = TOWER_RADIUS - 1; // 6

    private static final int GATE_HALF_WIDTH = 5;  // проём 11 = 2*5+1
    private static final int GATE_HEIGHT     = 18; // y=70..87 — открыто

    private static final int TOWER_SPACING_MIN = 45;
    private static final int TOWER_SPACING_MAX = 60;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;

    public ElikiumWall(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
    }

    /**
     * Построить стену + ворота + башни. Вызывается из
     * {@link WorldGenerator#phase6Structures(RegionPainter, Random)}.
     */
    public void build() {
        plugin.getLogger().info("ElikiumWall: трассирую периметр и расставляю башни…");

        int[][] poly = WorldGenerator.CITY_POLYGON;
        int[][] gates = {
                WorldGenerator.SOUTH_GATE,
                WorldGenerator.NORTH_GATE,
                WorldGenerator.EAST_GATE,
                WorldGenerator.WEST_GATE,
        };

        // Бегущий счётчик для расстановки башен. Стартуем с 0 — первая
        // башня встанет где-то в районе 18-22 блока от первой вершины.
        int distSinceLastTower = 0;
        int nextTowerSpacing  = TOWER_SPACING_MIN
                + rng.nextInt(TOWER_SPACING_MAX - TOWER_SPACING_MIN + 1);
        int wallSegments = 0;
        int towersBuilt = 0;
        int gatesBuilt = 0;

        for (int i = 0; i < poly.length - 1; i++) {
            int x1 = poly[i][0],     z1 = poly[i][1];
            int x2 = poly[i + 1][0], z2 = poly[i + 1][1];

            // Идём по ребру алгоритмом Брезенхэма, считая пройденные блоки.
            int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
            int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
            int err = dx - dz;
            int cx = x1, cz = z1;

            // Перпендикуляр к ребру, направленный «внутрь» полигона.
            // Для стены важно поставить 3 блока поперёк линии так, чтобы
            // стена была толстая. Внутренний/внешний ряд получаются
            // автоматически — нам просто нужно знать ось.
            // Если ребро идёт вправо/влево (большой dx) — перпендикуляр
            // вдоль Z; иначе — вдоль X.
            int perpAxis; // 0 = X, 1 = Z
            if (dx >= dz) {
                perpAxis = 1;
            } else {
                perpAxis = 0;
            }

            while (true) {
                wallSegments++;

                // Проверяем — не находится ли текущая клетка в зоне ворот.
                int gateIdx = isNearGate(cx, cz, gates);
                if (gateIdx < 0) {
                    placeWallSlice(cx, cz, perpAxis);
                } else {
                    // Просвет под воротами: оставляем «дыру» y=70..76,
                    // только одна верхняя плита кладки + арка над ней.
                    placeGateSlice(cx, cz, perpAxis);
                }

                // Башни: ставим только на углах полигона ИЛИ когда
                // накопилась дистанция и не попали в зону ворот.
                distSinceLastTower++;
                boolean atVertex = (cx == x2 && cz == z2)
                        || (cx == x1 && cz == z1);
                if (gateIdx < 0
                        && (atVertex || distSinceLastTower >= nextTowerSpacing)) {
                    buildTower(cx, cz);
                    towersBuilt++;
                    distSinceLastTower = 0;
                    nextTowerSpacing = TOWER_SPACING_MIN
                            + rng.nextInt(TOWER_SPACING_MAX
                                          - TOWER_SPACING_MIN + 1);
                }

                if (cx == x2 && cz == z2) break;
                int e2 = 2 * err;
                if (e2 > -dz) { err -= dz; cx += sx; }
                if (e2 < dx)  { err += dx; cz += sz; }
            }
        }

        // Над каждыми воротами строим готический гейтхаус с башнями,
        // стрельчатой аркой и надписью «ELIKIUM» (южные ворота — главный
        // вход, для них особо плотный декор).
        ElikiumGateHouse gh = new ElikiumGateHouse(plugin, painter);
        // SOUTH — главный вход; через него игрок приходит с Берега.
        gh.build(WorldGenerator.SOUTH_GATE[0], WorldGenerator.SOUTH_GATE[1],
                /*horizontal=*/ true,
                /*signTitle=*/   "ELIKIUM",
                /*hoverTitle=*/  "§5§l~ Эликий ~",
                /*hoverSubtitle=*/ "§7Город под Всевидящим Оком",
                /*mainEntry=*/   true);
        gatesBuilt++;
        gh.build(WorldGenerator.NORTH_GATE[0], WorldGenerator.NORTH_GATE[1],
                true, "NORD", "§5§lСеверные ворота", "§7Эликий", false);
        gatesBuilt++;
        gh.build(WorldGenerator.EAST_GATE[0], WorldGenerator.EAST_GATE[1],
                false, "EST", "§5§lВосточные ворота", "§7Эликий", false);
        gatesBuilt++;
        gh.build(WorldGenerator.WEST_GATE[0], WorldGenerator.WEST_GATE[1],
                false, "WEST", "§5§lЗападные ворота", "§7Эликий", false);
        gatesBuilt++;

        plugin.getLogger().info("ElikiumWall: периметр (" + wallSegments
                + " клеток) + " + towersBuilt + " башен + "
                + gatesBuilt + " ворот построены.");
    }

    // =========================================================================
    // СТЕНА
    // =========================================================================

    /**
     * 3 блока стены поперёк направления ребра, высотой
     * {@link #WALL_HEIGHT} + зубцы.
     */
    private void placeWallSlice(int cx, int cz, int perpAxis) {
        for (int off = -WALL_HALF_THICKNESS; off <= WALL_HALF_THICKNESS; off++) {
            int x = cx + (perpAxis == 0 ? off : 0);
            int z = cz + (perpAxis == 1 ? off : 0);

            // Фундамент (y=70..72) — COBBLED_DEEPSLATE (три слоя «пятки»).
            painter.place(x, Y_BASE,     z, Material.COBBLED_DEEPSLATE);
            painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
            painter.place(x, Y_BASE + 2, z, Material.COBBLED_DEEPSLATE);
            // Тело стены — DEEPSLATE_BRICKS с поясом TILES каждые 6 блоков.
            for (int dy = 3; dy <= WALL_HEIGHT - 2; dy++) {
                Material mat = (dy % 6 == 0)
                        ? Material.DEEPSLATE_TILES
                        : Material.DEEPSLATE_BRICKS;
                painter.place(x, Y_BASE + dy, z, mat);
            }
            // Карниз — DEEPSLATE_TILES, под боевой ход.
            painter.place(x, WALL_TOP_Y, z, Material.DEEPSLATE_TILES);
        }

        // Зубцы (y=88):
        // - средняя полоса (off=-1..1): сплошной CHISELED_DEEPSLATE — настил
        //   боевого хода (по нему можно ходить);
        // - наружный (off=-2) и внутренний (off=+2): зубцы через клетку
        //   DEEPSLATE_BRICK_WALL.
        // «Через клетку» — простая чётность (cx+cz)%2.
        boolean even = ((cx + cz) & 1) == 0;
        // Средний настил 5 блоков шириной (off=-2..+2) — боевой ход.
        for (int off = -2; off <= 2; off++) {
            int x = cx + (perpAxis == 0 ? off : 0);
            int z = cz + (perpAxis == 1 ? off : 0);
            painter.place(x, CRENEL_Y, z, Material.CHISELED_DEEPSLATE);
        }

        if (even) {
            int xOut = cx + (perpAxis == 0 ? -WALL_HALF_THICKNESS : 0);
            int zOut = cz + (perpAxis == 1 ? -WALL_HALF_THICKNESS : 0);
            int xIn  = cx + (perpAxis == 0 ?  WALL_HALF_THICKNESS : 0);
            int zIn  = cz + (perpAxis == 1 ?  WALL_HALF_THICKNESS : 0);
            painter.place(xOut, CRENEL_Y, zOut, Material.DEEPSLATE_BRICK_WALL);
            painter.place(xIn,  CRENEL_Y, zIn,  Material.DEEPSLATE_BRICK_WALL);
            // Двухрядный зубец — +1 блок выше для «элитного» вида.
            painter.place(xOut, CRENEL_Y + 1, zOut, Material.DEEPSLATE_BRICK_WALL);
            painter.place(xIn,  CRENEL_Y + 1, zIn,  Material.DEEPSLATE_BRICK_WALL);
        }
    }

    /**
     * «Срез под воротами» — оставляем сквозной проём y=70..76 и кладём
     * только одну плиту DEEPSLATE_TILES на y=77..78
     * (под арку, которую достроим в {@link #buildGateArch}).
     */
    private void placeGateSlice(int cx, int cz, int perpAxis) {
        for (int off = -WALL_HALF_THICKNESS; off <= WALL_HALF_THICKNESS; off++) {
            int x = cx + (perpAxis == 0 ? off : 0);
            int z = cz + (perpAxis == 1 ? off : 0);

            // y=70..81 — пусто (под ноги; проём высотой 12).
            for (int dy = 0; dy <= GATE_HEIGHT - 1; dy++) {
                painter.place(x, Y_BASE + dy, z, Material.AIR);
            }
            // y=82..86 — нижний край арки (5 рядов).
            for (int dy = GATE_HEIGHT; dy <= WALL_HEIGHT - 2; dy++) {
                painter.place(x, Y_BASE + dy, z, Material.DEEPSLATE_TILES);
            }
            // y=87 — карниз.
            painter.place(x, WALL_TOP_Y, z, Material.DEEPSLATE_TILES);
            // y=88 — сплошный настил (зубец нет).
            painter.place(x, CRENEL_Y, z, Material.CHISELED_DEEPSLATE);
        }
    }

    /**
     * Найти, к какому из 4 ворот принадлежит клетка {@code (cx, cz)}.
     * Возвращает индекс ворот в массиве или -1.
     */
    private int isNearGate(int cx, int cz, int[][] gates) {
        for (int i = 0; i < gates.length; i++) {
            int gx = gates[i][0], gz = gates[i][1];
            if (Math.abs(cx - gx) <= GATE_HALF_WIDTH
                    && Math.abs(cz - gz) <= GATE_HALF_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // ВОРОТА
    // =========================================================================

    /**
     * Декоративная арка + пилоны над проёмом ворот. Пилоны — 3×3×11
     * (выше стены на 3 блока), факелы на вершинах.
     */
    private void buildGateArch(int gx, int gz) {
        // Пилоны: по обе стороны от центра ворот, в направлении вдоль стены.
        // Без точной информации о направлении ребра в этой точке мы не
        // знаем, какая ось «вдоль» — попробуем угадать по координате:
        // SOUTH_GATE/NORTH_GATE стоят на горизонтальной стене (вдоль X),
        // EAST_GATE/WEST_GATE — на вертикальной (вдоль Z).
        boolean horizontalGate = Math.abs(gz) > Math.abs(gx);
        // Для южных/северных ворот пилон уходит вдоль X, для запад/восток — вдоль Z.

        int pillarOffsetA, pillarOffsetB;
        if (horizontalGate) {
            pillarOffsetA = -GATE_HALF_WIDTH - 1; // -3
            pillarOffsetB =  GATE_HALF_WIDTH + 1; // +3
        } else {
            pillarOffsetA = -GATE_HALF_WIDTH - 1;
            pillarOffsetB =  GATE_HALF_WIDTH + 1;
        }

        // Пилоны 5×5 по бокам (выше стены на 6 блоков).
        int pillarHeight = WALL_HEIGHT + 6;
        for (int side : new int[]{pillarOffsetA, pillarOffsetB}) {
            for (int t = -2; t <= 2; t++) { // толщина 5
                int px = gx + (horizontalGate ? side : t);
                int pz = gz + (horizontalGate ? t    : side);
                for (int dy = 0; dy < pillarHeight; dy++) {
                    Material mat;
                    if (dy <= 2) {
                        mat = Material.COBBLED_DEEPSLATE;
                    } else if (dy >= WALL_HEIGHT - 1) {
                        mat = Material.DEEPSLATE_TILES;
                    } else if (dy % 6 == 0) {
                        mat = Material.DEEPSLATE_TILES;
                    } else {
                        mat = Material.DEEPSLATE_BRICKS;
                    }
                    painter.place(px, Y_BASE + dy, pz, mat);
                }
                // Декоративная корона у вершины пилона.
                painter.place(px, Y_BASE + pillarHeight, pz,
                        Material.CHISELED_DEEPSLATE);
            }
        }

        // Факелы на вершине пилонов (со стороны проёма).
        for (int side : new int[]{pillarOffsetA, pillarOffsetB}) {
            int tx = gx + (horizontalGate ? side : 0);
            int tz = gz + (horizontalGate ? 0    : side);
            painter.place(tx, Y_BASE + pillarHeight + 1, tz,
                    Material.SOUL_LANTERN);
        }

        // Полукруглая арка сверху проёма уже в placeGateSlice. Добавим
        // декоративный «замковый камень» по центру y=88.
        painter.place(gx, CRENEL_Y, gz, Material.DEEPSLATE_TILES);
    }

    // =========================================================================
    // БАШНИ
    // =========================================================================

    /**
     * Башня в точке {@code (cx, cz)} — круглая, диаметр 5, высота 14.
     * Верх — двухуровневый конус из DEEPSLATE_BRICK_STAIRS + END_ROD на пике.
     */
    private void buildTower(int cx, int cz) {
        int rOuter = TOWER_RADIUS;        // 4
        int rInner = TOWER_INNER_R;       // 3

        // Стены: кольцо толщиной 1 блок, высота TOWER_HEIGHT-2.
        for (int dy = 0; dy < TOWER_HEIGHT - 2; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                for (int dz = -rOuter; dz <= rOuter; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rOuter * rOuter) continue;
                    if (d2 < rInner * rInner && dy > 0) continue; // полая
                    Material mat;
                    if (dy <= 1) {
                        mat = Material.COBBLED_DEEPSLATE;
                    } else if (dy % 6 == 0) {
                        mat = Material.DEEPSLATE_TILES;
                    } else {
                        mat = Material.DEEPSLATE_BRICKS;
                    }
                    painter.place(cx + dx, Y_BASE + dy, cz + dz, mat);
                }
            }
        }

        // Бойницы на трёх ярусах — 4 стороны × 2 слоя по высоте.
        for (int slitY : new int[]{Y_BASE + 12, Y_BASE + 24, Y_BASE + 34}) {
            for (int dy = 0; dy <= 1; dy++) {
                painter.place(cx + rOuter, slitY + dy, cz, Material.AIR);
                painter.place(cx - rOuter, slitY + dy, cz, Material.AIR);
                painter.place(cx, slitY + dy, cz + rOuter, Material.AIR);
                painter.place(cx, slitY + dy, cz - rOuter, Material.AIR);
            }
        }

        // Карниз (y=Y_BASE+TOWER_HEIGHT-2): кольцо DEEPSLATE_TILES.
        int carnY = Y_BASE + TOWER_HEIGHT - 2;
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 >= (rOuter - 1) * (rOuter - 1) && d2 <= rOuter * rOuter) {
                    painter.place(cx + dx, carnY, cz + dz,
                            Material.DEEPSLATE_TILES);
                }
            }
        }

        // Конус крыши: 7 уровней, радиус убывает 7 → 1.
        int roofBaseY = Y_BASE + TOWER_HEIGHT - 1;
        for (int level = 0; level < 7; level++) {
            int rAt = rOuter - level;
            if (rAt < 1) rAt = 1;
            int r2 = rAt * rAt;
            for (int dx = -rAt; dx <= rAt; dx++) {
                for (int dz = -rAt; dz <= rAt; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= r2 && d2 >= (rAt - 1) * (rAt - 1)) {
                        painter.place(cx + dx, roofBaseY + level, cz + dz,
                                Material.DEEPSLATE_BRICKS);
                    }
                }
            }
        }

        // Маяк на пике: LANTERN + 3×END_ROD (больше высоты под ×3-масштаб).
        int peakY = roofBaseY + 7;
        painter.place(cx, peakY,     cz, Material.LANTERN);
        painter.place(cx, peakY + 1, cz, Material.END_ROD);
        painter.place(cx, peakY + 2, cz, Material.END_ROD);
        painter.place(cx, peakY + 3, cz, Material.END_ROD);
    }
}
