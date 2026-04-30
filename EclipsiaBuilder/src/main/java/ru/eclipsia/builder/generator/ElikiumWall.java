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
    private static final int WALL_HEIGHT = 9;       // y=70..78
    private static final int WALL_TOP_Y = Y_BASE + WALL_HEIGHT - 1; // 78
    private static final int CRENEL_Y   = Y_BASE + WALL_HEIGHT;     // 79

    private static final int TOWER_HEIGHT = 14; // y=70..83
    private static final int TOWER_TOP_Y  = Y_BASE + TOWER_HEIGHT - 1; // 83
    private static final int TOWER_RADIUS = 2;

    private static final int GATE_HALF_WIDTH = 2; // проём 5 = 2*2+1
    private static final int GATE_HEIGHT     = 7; // y=70..76 — открыто

    private static final int TOWER_SPACING_MIN = 18;
    private static final int TOWER_SPACING_MAX = 22;

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

        // Над каждыми воротами строим арку и пилоны (в одном проходе по
        // всем 4 точкам — проще, чем пытаться поймать момент в Бресенхэме).
        for (int[] g : gates) {
            buildGateArch(g[0], g[1]);
            gatesBuilt++;
        }

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
        for (int off = -1; off <= 1; off++) {
            int x = cx + (perpAxis == 0 ? off : 0);
            int z = cz + (perpAxis == 1 ? off : 0);

            // Фундамент (y=70) — COBBLED_DEEPSLATE.
            painter.place(x, Y_BASE, z, Material.COBBLED_DEEPSLATE);
            // Тело стены (y=71..77) — DEEPSLATE_BRICKS.
            for (int dy = 1; dy <= WALL_HEIGHT - 2; dy++) {
                painter.place(x, Y_BASE + dy, z, Material.DEEPSLATE_BRICKS);
            }
            // Карниз (y=78) — DEEPSLATE_TILES, под боевой ход.
            painter.place(x, WALL_TOP_Y, z, Material.DEEPSLATE_TILES);
        }

        // Зубцы (y=79):
        // - средний ряд (off=0): сплошной CHISELED_DEEPSLATE для прохода;
        // - наружный (off=-1) и внутренний (off=+1): зубцы через клетку
        //   DEEPSLATE_BRICK_WALL.
        // «Через клетку» — простая чётность (cx+cz)%2 — на сложных углах
        // даёт нерегулярный, но визуально приятный паттерн.
        boolean even = ((cx + cz) & 1) == 0;
        int xMid = cx, zMid = cz;
        painter.place(xMid, CRENEL_Y, zMid, Material.CHISELED_DEEPSLATE);

        if (even) {
            int xOut = cx + (perpAxis == 0 ? -1 : 0);
            int zOut = cz + (perpAxis == 1 ? -1 : 0);
            int xIn  = cx + (perpAxis == 0 ?  1 : 0);
            int zIn  = cz + (perpAxis == 1 ?  1 : 0);
            painter.place(xOut, CRENEL_Y, zOut,
                    Material.DEEPSLATE_BRICK_WALL);
            painter.place(xIn,  CRENEL_Y, zIn,
                    Material.DEEPSLATE_BRICK_WALL);
        }
    }

    /**
     * «Срез под воротами» — оставляем сквозной проём y=70..76 и кладём
     * только одну плиту DEEPSLATE_TILES на y=77..78
     * (под арку, которую достроим в {@link #buildGateArch}).
     */
    private void placeGateSlice(int cx, int cz, int perpAxis) {
        for (int off = -1; off <= 1; off++) {
            int x = cx + (perpAxis == 0 ? off : 0);
            int z = cz + (perpAxis == 1 ? off : 0);

            // y=70..76 — пусто (под ноги).
            for (int dy = 0; dy <= GATE_HEIGHT - 1; dy++) {
                painter.place(x, Y_BASE + dy, z, Material.AIR);
            }
            // y=77..78 — нижний край арки.
            painter.place(x, Y_BASE + GATE_HEIGHT,     z,
                    Material.DEEPSLATE_TILES);
            painter.place(x, Y_BASE + GATE_HEIGHT + 1, z,
                    Material.DEEPSLATE_TILES);
            // y=79 — карниз (зубец нет).
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

        // Пилоны 2×2×11 по бокам. На углу — 1 блок.
        for (int side : new int[]{pillarOffsetA, pillarOffsetB}) {
            for (int t = -1; t <= 0; t++) { // толщина 2
                int px = gx + (horizontalGate ? side : t);
                int pz = gz + (horizontalGate ? t    : side);
                for (int dy = 0; dy < WALL_HEIGHT + 3; dy++) {
                    Material mat = (dy < WALL_HEIGHT - 1)
                            ? Material.DEEPSLATE_BRICKS
                            : Material.DEEPSLATE_TILES;
                    painter.place(px, Y_BASE + dy, pz, mat);
                }
                // Декоративная корона у вершины пилона.
                painter.place(px, Y_BASE + WALL_HEIGHT + 3, pz,
                        Material.CHISELED_DEEPSLATE);
            }
        }

        // Факелы на вершине пилонов (со стороны проёма).
        for (int side : new int[]{pillarOffsetA, pillarOffsetB}) {
            int tx = gx + (horizontalGate ? side : 0);
            int tz = gz + (horizontalGate ? 0    : side);
            painter.place(tx, Y_BASE + WALL_HEIGHT + 4, tz,
                    Material.SOUL_LANTERN);
        }

        // Полукруглая арка сверху проёма (y=77..78 уже плита из placeGateSlice).
        // Добавим декоративный «замковый камень» по центру y=79.
        painter.place(gx, CRENEL_Y, gz,
                Material.DEEPSLATE_TILES);
    }

    // =========================================================================
    // БАШНИ
    // =========================================================================

    /**
     * Башня в точке {@code (cx, cz)} — круглая, диаметр 5, высота 14.
     * Верх — двухуровневый конус из DEEPSLATE_BRICK_STAIRS + END_ROD на пике.
     */
    private void buildTower(int cx, int cz) {
        int rOuter = TOWER_RADIUS;     // 2
        int rInner = TOWER_RADIUS - 1; // 1 (для пустоты внутри)

        // Стены: кольцо толщиной 1 блок, высота 12.
        for (int dy = 0; dy < TOWER_HEIGHT - 2; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                for (int dz = -rOuter; dz <= rOuter; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rOuter * rOuter) continue;
                    if (d2 < rInner * rInner && dy > 0) continue; // полая
                    Material mat;
                    if (dy == 0) {
                        mat = Material.COBBLED_DEEPSLATE;
                    } else if ((dy + 1) % 4 == 0) {
                        mat = Material.DEEPSLATE_TILES;
                    } else {
                        mat = Material.DEEPSLATE_BRICKS;
                    }
                    painter.place(cx + dx, Y_BASE + dy, cz + dz, mat);
                }
            }
        }

        // Бойницы на y=76 (середина башни).
        int slitY = Y_BASE + 6;
        painter.place(cx + rOuter,    slitY, cz, Material.AIR);
        painter.place(cx - rOuter,    slitY, cz, Material.AIR);
        painter.place(cx, slitY, cz + rOuter,    Material.AIR);
        painter.place(cx, slitY, cz - rOuter,    Material.AIR);

        // Карниз (y=82): кольцо DEEPSLATE_TILES толщиной 1.
        int carnY = Y_BASE + TOWER_HEIGHT - 2;
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 == rOuter * rOuter
                        || d2 == rOuter * rOuter - 1
                        || d2 == rOuter * rOuter + 1) {
                    painter.place(cx + dx, carnY, cz + dz,
                            Material.DEEPSLATE_TILES);
                }
            }
        }

        // Конус крыши: 2 уровня DEEPSLATE_BRICK_STAIRS (упрощённо — слоями).
        int roofY1 = Y_BASE + TOWER_HEIGHT - 1;
        int roofY2 = Y_BASE + TOWER_HEIGHT;
        // Уровень 1 — диск радиуса 2.
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                if (dx * dx + dz * dz <= rOuter * rOuter
                        && (Math.abs(dx) == rOuter || Math.abs(dz) == rOuter
                            || dx * dx + dz * dz >= rInner * rInner)) {
                    painter.place(cx + dx, roofY1, cz + dz,
                            Material.DEEPSLATE_BRICK_STAIRS);
                }
            }
        }
        // Уровень 2 — диск радиуса 1.
        for (int dx = -rInner; dx <= rInner; dx++) {
            for (int dz = -rInner; dz <= rInner; dz++) {
                if (dx * dx + dz * dz <= rInner * rInner) {
                    painter.place(cx + dx, roofY2, cz + dz,
                            Material.DEEPSLATE_BRICKS);
                }
            }
        }

        // Маяк на пике: END_ROD + дозорный фонарь.
        painter.place(cx, roofY2 + 1, cz, Material.LANTERN);
        painter.place(cx, roofY2 + 2, cz, Material.END_ROD);

        // Внутри башни — лестница вверх (упрощённо: один столб блоков).
        // На y=70..81 — ступеньки DEEPSLATE_BRICK_STAIRS.
        // Опускаем ради простоты (PR 4 — здания: можно добавить позже).
    }
}
