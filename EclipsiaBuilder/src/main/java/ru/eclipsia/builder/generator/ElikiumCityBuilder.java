package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Геометрия и заливка города Эликий: стены, угловые башни, ворота и
 * центральный собор. Создаётся одноразово {@link WorldGenerator}'ом во
 * второй фазе генерации; ничего не делает в рантайме (за это отвечает
 * отдельный {@link SpireParticles}).
 *
 * <p>Класс намеренно вынесен из {@link WorldGenerator}, чтобы тот не
 * разрастался в монолит. Все координаты берутся из публичных констант
 * {@link WorldGenerator} — менять размеры стен или высоту плато нужно
 * там, а не здесь.
 *
 * <p>Цветовая палитра города:
 * <ul>
 *   <li>основа — {@link Material#STONE_BRICKS} с {@link Material#DEEPSLATE_TILES}
 *       вставками;</li>
 *   <li>ворота — {@link Material#POLISHED_BLACKSTONE} +
 *       {@link Material#GOLD_BLOCK} + {@link Material#IRON_BARS} +
 *       {@link Material#AMETHYST_BLOCK} над аркой;</li>
 *   <li>собор — {@link Material#POLISHED_BLACKSTONE} +
 *       {@link Material#DEEPSLATE_TILES} + золото и фиолетовое стекло
 *       в окнах;</li>
 *   <li>шпиль — {@link Material#OBSIDIAN}, {@link Material#END_ROD},
 *       {@link Material#AMETHYST_BLOCK}, {@link Material#SOUL_FIRE} в центре.</li>
 * </ul>
 */
public final class ElikiumCityBuilder {

    // Из {@link WorldGenerator}.
    private static final int CX = WorldGenerator.CITY_X;
    private static final int CZ = WorldGenerator.CITY_Z;
    private static final int HALF = WorldGenerator.CITY_HALF;
    private static final int FLOOR_Y = WorldGenerator.CITY_FLOOR_Y;
    private static final int WALL_H = WorldGenerator.CITY_WALL_HEIGHT;
    private static final int CATHEDRAL_HALF = WorldGenerator.CATHEDRAL_HALF;
    private static final int CATHEDRAL_H = WorldGenerator.CATHEDRAL_HEIGHT;

    /** Полуширина проёма ворот: проём = 2*GATE_HALF_W + 1 = 5. */
    private static final int GATE_HALF_W = 2;
    /** Высота проёма ворот (от пола до арки). */
    private static final int GATE_H = 8;

    /** Высота угловых башен (надстройка над стеной). */
    private static final int TOWER_EXTRA = 5;
    /** Радиус угловых башен. */
    private static final int TOWER_R = 2;

    /** Толщина стены (1 — тонкая; 2 — двойная с DEEPSLATE-вставкой). */
    private static final int WALL_THICK = 2;

    /** Высота главного зала собора (свод). */
    private static final int CATHEDRAL_NAVE_H = 20;

    private final Plugin plugin;
    private final World world;
    private final BlockData stairsNorth;
    private final BlockData stairsSouth;
    private final BlockData stairsEast;
    private final BlockData stairsWest;
    private final BlockData blackstoneStairsNorth;
    private final BlockData blackstoneStairsSouth;
    private final BlockData blackstoneStairsEast;
    private final BlockData blackstoneStairsWest;

    public ElikiumCityBuilder(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.stairsNorth = orientStairs(Material.DARK_OAK_STAIRS, Stairs.Shape.STRAIGHT, "north");
        this.stairsSouth = orientStairs(Material.DARK_OAK_STAIRS, Stairs.Shape.STRAIGHT, "south");
        this.stairsEast  = orientStairs(Material.DARK_OAK_STAIRS, Stairs.Shape.STRAIGHT, "east");
        this.stairsWest  = orientStairs(Material.DARK_OAK_STAIRS, Stairs.Shape.STRAIGHT, "west");
        this.blackstoneStairsNorth = orientStairs(Material.POLISHED_BLACKSTONE_STAIRS, Stairs.Shape.STRAIGHT, "north");
        this.blackstoneStairsSouth = orientStairs(Material.POLISHED_BLACKSTONE_STAIRS, Stairs.Shape.STRAIGHT, "south");
        this.blackstoneStairsEast  = orientStairs(Material.POLISHED_BLACKSTONE_STAIRS, Stairs.Shape.STRAIGHT, "east");
        this.blackstoneStairsWest  = orientStairs(Material.POLISHED_BLACKSTONE_STAIRS, Stairs.Shape.STRAIGHT, "west");
    }

    private BlockData orientStairs(Material mat, Stairs.Shape shape, String facing) {
        BlockData bd = mat.createBlockData();
        if (bd instanceof Stairs s) {
            switch (facing) {
                case "north" -> s.setFacing(org.bukkit.block.BlockFace.NORTH);
                case "south" -> s.setFacing(org.bukkit.block.BlockFace.SOUTH);
                case "east"  -> s.setFacing(org.bukkit.block.BlockFace.EAST);
                case "west"  -> s.setFacing(org.bukkit.block.BlockFace.WEST);
                default -> {}
            }
            s.setShape(shape);
            return s;
        }
        return bd;
    }

    /**
     * Точка входа фазы 2. Заполняет {@code p} операциями строительства
     * (стены, башни, ворота, собор, шпиль). Вызывать один раз внутри
     * {@link WorldGenerator#generate(Runnable)}.
     */
    public void buildAll(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: фаза 2 — строю Эликий…");

        // 0) Очищаем воздух над плато: WorldGenerator (фаза 1) уже это
        //    делает, но если перегенерируем поверх старой постройки —
        //    нужно сбросить лишние блоки внутри 100×100.
        clearCityAirspace(p);

        // 1) Стены и угловые башни.
        buildWalls(p);
        buildCornerTowers(p);

        // 2) Четверо ворот (проёмы пробиваются ПОВЕРХ стены).
        buildGate(p, GateSide.NORTH);
        buildGate(p, GateSide.SOUTH);
        buildGate(p, GateSide.EAST);
        buildGate(p, GateSide.WEST);

        // 3) Площадь вокруг собора (мостовая + столбы).
        buildCathedralPlaza(p);

        // 4) Собор (крест + неф + трансепт + крыша).
        buildCathedral(p, rng);

        // 5) Финальные FloatingText (имена ворот) — это не строительство,
        //    мы складываем их в очередь, но фактически TextDisplay-сущности
        //    спавнит {@link WorldGenerator#spawnFloatingTexts()}/onFinish;
        //    здесь тоже можно — main thread в moменте вызова.
        spawnGateLabels();
    }

    // =========================================================================
    // СТЕНЫ
    // =========================================================================

    private void clearCityAirspace(RegionPainter p) {
        int yTop = FLOOR_Y + CATHEDRAL_H + 8;
        // Чистим только колонну над плато (внутри 80×80) — снаружи плато
        // ландшафт уже почищен фазой 1.
        for (int x = CX - HALF; x <= CX + HALF; x++) {
            for (int z = CZ - HALF; z <= CZ + HALF; z++) {
                for (int y = FLOOR_Y + 1; y <= yTop; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }
    }

    /**
     * Строит периметр стен 80×80 высотой {@link WorldGenerator#CITY_WALL_HEIGHT}.
     *
     * <p>Внешний слой — STONE_BRICKS, внутренний — DEEPSLATE_TILES для
     * визуального шва. Каждые 5 блоков по периметру вертикальная вставка
     * из DEEPSLATE до самой вершины. Поверх стены — STONE_BRICK_WALL
     * зубцы (через один блок).
     */
    private void buildWalls(RegionPainter p) {
        int xMin = CX - HALF, xMax = CX + HALF;
        int zMin = CZ - HALF, zMax = CZ + HALF;

        for (int side = 0; side < 4; side++) {
            // Каждая из 4 сторон: список (x, z) идущих по периметру.
            for (int t = 0; t < HALF * 2; t++) {
                int x, z;
                switch (side) {
                    case 0 -> { x = xMin + t; z = zMin; }   // север
                    case 1 -> { x = xMax;     z = zMin + t; } // восток
                    case 2 -> { x = xMax - t; z = zMax; }   // юг
                    default -> { x = xMin;    z = zMax - t; } // запад
                }
                buildWallColumn(p, x, z, t);
            }
        }
        // Закрываем углы (на стыках циклов оставались дыры в 1 столб).
        for (int[] c : new int[][] { {xMin, zMin}, {xMax, zMin}, {xMax, zMax}, {xMin, zMax} }) {
            buildWallColumn(p, c[0], c[1], 0);
        }
    }

    private void buildWallColumn(RegionPainter p, int x, int z, int t) {
        // Двухслойная стена: внешний (тот столб, что на периметре) +
        // внутренний (на 1 блок внутрь).
        int dxIn = (x < CX) ? 1 : (x > CX ? -1 : 0);
        int dzIn = (z < CZ) ? 1 : (z > CZ ? -1 : 0);
        int x2 = x + (dxIn != 0 ? dxIn : 0);
        int z2 = z + (dzIn != 0 ? dzIn : 0);

        for (int dy = 1; dy <= WALL_H; dy++) {
            // Внешний слой: STONE_BRICKS; на каждой 5-й клетке —
            // вертикальная вставка DEEPSLATE_TILES.
            Material outer = (t % 5 == 0) ? Material.DEEPSLATE_TILES : Material.STONE_BRICKS;
            p.place(x, FLOOR_Y + dy, z, outer);
            // Внутренний слой (если есть направление «внутрь») — STONE_BRICKS.
            if (WALL_THICK >= 2 && (dxIn != 0 || dzIn != 0)) {
                p.place(x2, FLOOR_Y + dy, z2, Material.STONE_BRICKS);
            }
        }
        // Зубцы: STONE_BRICK_WALL через один блок (на чётных t).
        if (t % 2 == 0) {
            p.place(x, FLOOR_Y + WALL_H + 1, z, Material.STONE_BRICK_WALL);
        }
    }

    // =========================================================================
    // УГЛОВЫЕ БАШНИ
    // =========================================================================

    private void buildCornerTowers(RegionPainter p) {
        int[][] corners = {
                { CX - HALF, CZ - HALF }, // СЗ
                { CX + HALF, CZ - HALF }, // СВ
                { CX - HALF, CZ + HALF }, // ЮЗ
                { CX + HALF, CZ + HALF }, // ЮВ
        };
        for (int[] c : corners) {
            buildCornerTower(p, c[0], c[1]);
        }
    }

    private void buildCornerTower(RegionPainter p, int cx, int cz) {
        // Сместим центр башни на 1 блок внутрь, чтобы она стояла своим
        // массивом на стене, а не свисала в обрыв.
        int dx = (cx < CX) ? 1 : -1;
        int dz = (cz < CZ) ? 1 : -1;
        int towerCx = cx + dx;
        int towerCz = cz + dz;
        int totalH = WALL_H + TOWER_EXTRA;

        // Башня: круг радиуса TOWER_R, заполненный по периметру STONE_BRICKS,
        // внутри — пустота. Между башней и стеной DEEPSLATE_TILES «шов».
        int r2 = TOWER_R * TOWER_R;
        int rOuter2 = (TOWER_R + 1) * (TOWER_R + 1);
        for (int ox = -TOWER_R - 1; ox <= TOWER_R + 1; ox++) {
            for (int oz = -TOWER_R - 1; oz <= TOWER_R + 1; oz++) {
                int d = ox * ox + oz * oz;
                if (d > rOuter2) continue;
                boolean ring = d > r2;
                Material mat = ring ? Material.DEEPSLATE_TILES : Material.STONE_BRICKS;
                int xx = towerCx + ox;
                int zz = towerCz + oz;
                for (int dy = 1; dy <= totalH; dy++) {
                    // Только периметр (внешнее кольцо или внутренний радиус) —
                    // внутреннее пространство пустое (пол ставим один раз).
                    if (ring || d == r2) {
                        p.place(xx, FLOOR_Y + dy, zz, mat);
                    }
                }
                // Пол башни на верхней площадке — POLISHED_BLACKSTONE.
                if (d <= r2) {
                    p.place(xx, FLOOR_Y + totalH, zz, Material.POLISHED_BLACKSTONE);
                }
            }
        }
        // Зубцы на крыше башни (4 точки по компасу).
        int top = FLOOR_Y + totalH + 1;
        for (int[] off : new int[][] { {TOWER_R, 0}, {-TOWER_R, 0}, {0, TOWER_R}, {0, -TOWER_R} }) {
            p.place(towerCx + off[0], top, towerCz + off[1], Material.STONE_BRICK_WALL);
        }
        // На самой макушке — золотой огонёк (END_ROD), чтобы башни читались издали.
        p.place(towerCx, top + 1, towerCz, Material.END_ROD);
    }

    // =========================================================================
    // ВОРОТА
    // =========================================================================

    private enum GateSide { NORTH, SOUTH, EAST, WEST }

    private void buildGate(RegionPainter p, GateSide side) {
        // Координаты центра ворот на стене.
        int gx = CX, gz = CZ;
        switch (side) {
            case NORTH -> gz = CZ - HALF;
            case SOUTH -> gz = CZ + HALF;
            case EAST  -> gx = CX + HALF;
            case WEST  -> gx = CX - HALF;
        }
        // Перпендикуляр стене (направление вдоль фасада).
        int tdx = (side == GateSide.NORTH || side == GateSide.SOUTH) ? 1 : 0;
        int tdz = (side == GateSide.EAST  || side == GateSide.WEST)  ? 1 : 0;

        // 1) ВЫБИВАЕМ проём в стене: GATE_HALF_W*2+1 шириной, GATE_H высотой.
        for (int t = -GATE_HALF_W; t <= GATE_HALF_W; t++) {
            int xx = gx + t * tdx;
            int zz = gz + t * tdz;
            for (int dy = 1; dy <= GATE_H; dy++) {
                p.place(xx, FLOOR_Y + dy, zz, Material.AIR);
                // Внутренний слой стены тоже выбиваем (если есть).
                int inDx = (xx < CX) ? 1 : (xx > CX ? -1 : 0);
                int inDz = (zz < CZ) ? 1 : (zz > CZ ? -1 : 0);
                if (inDx != 0 || inDz != 0) {
                    p.place(xx + inDx, FLOOR_Y + dy, zz + inDz, Material.AIR);
                }
            }
        }

        // 2) Рамка ворот: POLISHED_BLACKSTONE по краям проёма (низ-верх-стороны).
        for (int t = -GATE_HALF_W - 1; t <= GATE_HALF_W + 1; t++) {
            int xx = gx + t * tdx;
            int zz = gz + t * tdz;
            // Горизонтальная балка-арка на высоте GATE_H+1.
            p.place(xx, FLOOR_Y + GATE_H + 1, zz, Material.POLISHED_BLACKSTONE);
            // Золотая полоса под аркой.
            if (t == 0) {
                p.place(xx, FLOOR_Y + GATE_H + 1, zz, Material.GOLD_BLOCK);
            }
        }
        // Боковые столбы рамки — на ±(GATE_HALF_W+1) от центра.
        for (int sign : new int[] { -1, 1 }) {
            int tx = gx + sign * (GATE_HALF_W + 1) * tdx;
            int tz = gz + sign * (GATE_HALF_W + 1) * tdz;
            for (int dy = 1; dy <= GATE_H + 1; dy++) {
                p.place(tx, FLOOR_Y + dy, tz, Material.POLISHED_BLACKSTONE);
            }
            // Золотая верхушка столба.
            p.place(tx, FLOOR_Y + GATE_H + 2, tz, Material.GOLD_BLOCK);
        }

        // 3) IRON_BARS «решётка» — поднята: висит сверху проёма (3 блока),
        //    оставляя 5 блоков прохода снизу. Игрок проходит сквозь решётку,
        //    что атмосферно: «ворота открыты».
        for (int t = -GATE_HALF_W; t <= GATE_HALF_W; t++) {
            int xx = gx + t * tdx;
            int zz = gz + t * tdz;
            for (int dy = GATE_H - 2; dy <= GATE_H; dy++) {
                p.place(xx, FLOOR_Y + dy, zz, Material.IRON_BARS);
            }
        }

        // 4) AMETHYST «купол» над аркой — 1 блок в центре + 2 чуть ниже.
        p.place(gx, FLOOR_Y + GATE_H + 3, gz, Material.AMETHYST_BLOCK);
        // По бокам от купола — фиолетовое стекло, чтобы кристалл «светился».
        for (int sign : new int[] { -1, 1 }) {
            int tx = gx + sign * tdx;
            int tz = gz + sign * tdz;
            p.place(tx, FLOOR_Y + GATE_H + 2, tz, Material.PURPLE_STAINED_GLASS);
        }
    }

    private void spawnGateLabels() {
        // Имена ворот — над каждой аркой. y = FLOOR_Y + GATE_H + 4 = 70+8+4 = 82.
        int yLabel = FLOOR_Y + GATE_H + 4;
        FloatingText.createSign(plugin, world,
                CX + 0.5, yLabel, CZ - HALF + 0.5, "§6Врата Полей");
        FloatingText.createSign(plugin, world,
                CX + 0.5, yLabel, CZ + HALF + 0.5, "§6Врата Озера");
        FloatingText.createSign(plugin, world,
                CX + HALF + 0.5, yLabel, CZ + 0.5, "§6Врата Леса");
        FloatingText.createSign(plugin, world,
                CX - HALF + 0.5, yLabel, CZ + 0.5, "§6Врата Гор");
    }

    // =========================================================================
    // СОБОР
    // =========================================================================

    /**
     * Строит главный собор Эликия в форме креста (центральный неф 10×20,
     * трансепт 20×10), с готическими окнами, контрфорсами и шпилем.
     *
     * <p>Координатная сетка: центр собора совпадает с центром города (0,0).
     * Длинная ось нефа идёт по Z (север↔юг), трансепт — по X.
     */
    private void buildCathedral(RegionPainter p, Random rng) {
        // Параметры креста: nave 10×20, transept 20×10.
        int navHalfX = 5, navHalfZ = 10;
        int trnHalfX = 10, trnHalfZ = 5;
        int yBase = FLOOR_Y;
        int yWallTop = yBase + CATHEDRAL_NAVE_H;

        // ---------- Пол (POLISHED_DEEPSLATE с золотым крестом в центре) ----------
        for (int x = CX - trnHalfX; x <= CX + trnHalfX; x++) {
            for (int z = CZ - navHalfZ; z <= CZ + navHalfZ; z++) {
                if (!isInsideCross(x, z, navHalfX, navHalfZ, trnHalfX, trnHalfZ)) continue;
                p.place(x, yBase, z, Material.POLISHED_DEEPSLATE);
            }
        }
        // Золотой крест на полу (1×1 в центре + 4 «руки» по 3 блока).
        p.place(CX, yBase, CZ, Material.GOLD_BLOCK);
        for (int d = 1; d <= 3; d++) {
            p.place(CX + d, yBase, CZ, Material.GOLD_BLOCK);
            p.place(CX - d, yBase, CZ, Material.GOLD_BLOCK);
            p.place(CX, yBase, CZ + d, Material.GOLD_BLOCK);
            p.place(CX, yBase, CZ - d, Material.GOLD_BLOCK);
        }

        // ---------- Стены ----------
        for (int dy = 1; dy <= CATHEDRAL_NAVE_H; dy++) {
            // Длинные стены нефа (запад/восток).
            for (int z = CZ - navHalfZ; z <= CZ + navHalfZ; z++) {
                Material m = wallMaterial(dy);
                p.place(CX - navHalfX, yBase + dy, z, m);
                p.place(CX + navHalfX, yBase + dy, z, m);
            }
            // Короткие стены нефа (север/юг) — но они частично «съедены» трансептом.
            for (int x = CX - navHalfX; x <= CX + navHalfX; x++) {
                if (Math.abs(x - CX) > trnHalfX) continue; // ничего, всё в пределах
                Material m = wallMaterial(dy);
                if (Math.abs(x - CX) > 0 && Math.abs(x - CX) < trnHalfX - 4) {
                    // оставим вход в трансепт чистым (см. дальше)
                }
                p.place(x, yBase + dy, CZ - navHalfZ, m);
                p.place(x, yBase + dy, CZ + navHalfZ, m);
            }
            // Длинные стены трансепта (север/юг).
            for (int x = CX - trnHalfX; x <= CX + trnHalfX; x++) {
                Material m = wallMaterial(dy);
                p.place(x, yBase + dy, CZ - trnHalfZ, m);
                p.place(x, yBase + dy, CZ + trnHalfZ, m);
            }
            // Короткие стены трансепта (запад/восток).
            for (int z = CZ - trnHalfZ; z <= CZ + trnHalfZ; z++) {
                Material m = wallMaterial(dy);
                p.place(CX - trnHalfX, yBase + dy, z, m);
                p.place(CX + trnHalfX, yBase + dy, z, m);
            }
        }

        // ---------- Окна (фиолетовое стекло + железные решётки) ----------
        // На длинных стенах нефа — 4 высоких арочных окна (по 2 на каждой).
        int windowYBottom = yBase + 6;
        int windowYTop = yBase + 14;
        int[] windowZs = { CZ - 6, CZ - 2, CZ + 2, CZ + 6 };
        for (int wz : windowZs) {
            for (int dy = windowYBottom; dy <= windowYTop; dy++) {
                p.place(CX - navHalfX, dy, wz, dy >= windowYTop - 1 ? Material.IRON_BARS : Material.PURPLE_STAINED_GLASS);
                p.place(CX + navHalfX, dy, wz, dy >= windowYTop - 1 ? Material.IRON_BARS : Material.PURPLE_STAINED_GLASS);
            }
        }
        // На длинных стенах трансепта — по 2 окна.
        int[] windowXs = { CX - 6, CX - 2, CX + 2, CX + 6 };
        for (int wx : windowXs) {
            for (int dy = windowYBottom; dy <= windowYTop; dy++) {
                p.place(wx, dy, CZ - trnHalfZ, dy >= windowYTop - 1 ? Material.IRON_BARS : Material.PURPLE_STAINED_GLASS);
                p.place(wx, dy, CZ + trnHalfZ, dy >= windowYTop - 1 ? Material.IRON_BARS : Material.PURPLE_STAINED_GLASS);
            }
        }

        // ---------- Контрфорсы (выступы каждые 5 блоков снаружи) ----------
        for (int z = CZ - navHalfZ + 4; z <= CZ + navHalfZ - 4; z += 5) {
            buildButtress(p, CX - navHalfX - 1, yBase, z, true);
            buildButtress(p, CX + navHalfX + 1, yBase, z, false);
        }
        for (int x = CX - trnHalfX + 4; x <= CX + trnHalfX - 4; x += 5) {
            // Только на коротких сторонах трансепта (восток/запад) — нефа уже не хватит.
            if (Math.abs(x - CX) <= navHalfX) continue;
            buildButtress(p, x, yBase, CZ - trnHalfZ - 1, true);
            buildButtress(p, x, yBase, CZ + trnHalfZ + 1, false);
        }

        // ---------- Двери (главный вход с севера; не запертые) ----------
        // Проём 3×4 в северной стене трансепта/нефа (cx ±1, под потолком 5).
        for (int x = CX - 1; x <= CX + 1; x++) {
            for (int dy = 1; dy <= 4; dy++) {
                p.place(x, yBase + dy, CZ - navHalfZ, Material.AIR);
            }
        }
        // Декоративная арка-перемычка из золота над дверью.
        for (int x = CX - 2; x <= CX + 2; x++) {
            p.place(x, yBase + 5, CZ - navHalfZ, Material.GOLD_BLOCK);
        }

        // ---------- Крыша (двускатная) ----------
        buildCathedralRoof(p, navHalfX, navHalfZ, trnHalfX, trnHalfZ, yWallTop);

        // ---------- Шпиль и платформа на вершине ----------
        buildSpire(p, yWallTop);

        // ---------- Интерьер ----------
        buildCathedralInterior(p, navHalfX, navHalfZ, trnHalfX, trnHalfZ, yBase);
    }

    private boolean isInsideCross(int x, int z, int navHalfX, int navHalfZ, int trnHalfX, int trnHalfZ) {
        boolean inNave = Math.abs(x - CX) <= navHalfX && Math.abs(z - CZ) <= navHalfZ;
        boolean inTrn  = Math.abs(x - CX) <= trnHalfX && Math.abs(z - CZ) <= trnHalfZ;
        return inNave || inTrn;
    }

    /** Материал стены собора с лёгкой «текстурной» сменой по высоте. */
    private Material wallMaterial(int dy) {
        if (dy == 1) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (dy % 6 == 0) return Material.GOLD_BLOCK;             // золотой пояс
        if (dy >= 16) return Material.DEEPSLATE_TILES;            // верх — тёмный
        return (dy % 2 == 0) ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_TILES;
    }

    private void buildButtress(RegionPainter p, int x, int yBase, int z, boolean westSide) {
        // Выступ из 1 блока POLISHED_BLACKSTONE_BRICKS на всю высоту,
        // плюс «контрфорс»-ступенька на середине.
        int top = yBase + CATHEDRAL_NAVE_H;
        for (int dy = 1; dy <= CATHEDRAL_NAVE_H; dy++) {
            p.place(x, yBase + dy, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        // Маленькая капитель сверху.
        p.place(x, top + 1, z, Material.GOLD_BLOCK);
    }

    private void buildCathedralRoof(RegionPainter p, int navHalfX, int navHalfZ,
                                     int trnHalfX, int trnHalfZ, int yWallTop) {
        // Двускатная крыша нефа (вдоль Z): пик по линии x=CX,
        // скаты опускаются на ±navHalfX. Высота пика: navHalfX блоков
        // над стеной (т.е. 5 блоков).
        int navPeakOffset = navHalfX;
        for (int z = CZ - navHalfZ; z <= CZ + navHalfZ; z++) {
            for (int dx = -navHalfX; dx <= navHalfX; dx++) {
                int dy = navPeakOffset - Math.abs(dx);
                if (dy <= 0) continue;
                int yy = yWallTop + dy;
                Material mat = (dy % 2 == 0) ? Material.DARK_OAK_PLANKS : Material.BLACKSTONE;
                p.place(CX + dx, yy, z, mat);
            }
        }
        // Двускатная крыша трансепта (вдоль X), такая же.
        int trnPeakOffset = trnHalfZ;
        for (int x = CX - trnHalfX; x <= CX + trnHalfX; x++) {
            // Пропускаем зону пересечения с нефом — там уже стоят блоки нефа.
            if (Math.abs(x - CX) <= navHalfX) continue;
            for (int dz = -trnHalfZ; dz <= trnHalfZ; dz++) {
                int dy = trnPeakOffset - Math.abs(dz);
                if (dy <= 0) continue;
                int yy = yWallTop + dy;
                Material mat = (dy % 2 == 0) ? Material.DARK_OAK_PLANKS : Material.BLACKSTONE;
                p.place(x, yy, CZ + dz, mat);
            }
        }
    }

    /**
     * Шпиль 10 блоков высотой над крышей с платформой 5×5 OBSIDIAN на вершине,
     * 4 END_ROD по углам, AMETHYST-кольцо радиуса 2, SOUL_FIRE по центру и
     * вертикальный «глаз» из END_ROD + PURPLE_STAINED_GLASS.
     */
    private void buildSpire(RegionPainter p, int yWallTop) {
        int peak = yWallTop + 5;        // высота пика крыши нефа
        int spireBase = peak + 1;       // основание шпиля
        int spireH = 10;                // высота самого шпиля

        // Сама колонна шпиля: BLACKSTONE_WALL вертикально 1×1.
        for (int dy = 0; dy < spireH; dy++) {
            p.place(CX, spireBase + dy, CZ, Material.BLACKSTONE_WALL);
        }
        // Платформа 5×5 OBSIDIAN на вершине.
        int platY = spireBase + spireH;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.place(CX + dx, platY, CZ + dz, Material.OBSIDIAN);
            }
        }
        // 4 END_ROD по углам платформы.
        int rodY = platY + 1;
        for (int[] off : new int[][] { {2, 2}, {-2, 2}, {2, -2}, {-2, -2} }) {
            p.place(CX + off[0], rodY, CZ + off[1], Material.END_ROD);
        }
        // AMETHYST-кольцо радиуса 2 на платформе (8 блоков).
        for (int[] off : new int[][] {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 1}, {-2, 1}, {2, -1}, {-2, -1},
                {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
        }) {
            p.place(CX + off[0], rodY, CZ + off[1], Material.AMETHYST_BLOCK);
        }
        // Центральная «жертвенная чаша»: NETHER_BRICK_FENCE + SOUL_FIRE.
        p.place(CX, rodY, CZ, Material.NETHER_BRICK_FENCE);
        p.place(CX, rodY + 1, CZ, Material.SOUL_FIRE);

        // Вертикальный «глаз»: END_ROD-каркас + PURPLE_STAINED_GLASS внутри.
        int eyeY = rodY + 3;
        // Каркас (вытянутый ромб):
        p.place(CX, eyeY,     CZ, Material.PURPLE_STAINED_GLASS);
        p.place(CX, eyeY + 1, CZ, Material.PURPLE_STAINED_GLASS);
        p.place(CX, eyeY + 2, CZ, Material.PURPLE_STAINED_GLASS);
        p.place(CX, eyeY - 1, CZ, Material.END_ROD);
        p.place(CX, eyeY + 3, CZ, Material.END_ROD);
        p.place(CX + 1, eyeY + 1, CZ, Material.END_ROD);
        p.place(CX - 1, eyeY + 1, CZ, Material.END_ROD);
        p.place(CX, eyeY + 1, CZ + 1, Material.END_ROD);
        p.place(CX, eyeY + 1, CZ - 1, Material.END_ROD);
        // «Зрачок» — кристалл аметиста по центру.
        // (Пишем ПОСЛЕ glass, чтобы перекрыл вершину.)
        // оставляем PURPLE_STAINED_GLASS — он и есть зрачок.

        // Сохраним координаты вершины шпиля, чтобы SpireParticles знал, где крутить.
        WorldGenerator.spireCenterX = CX + 0.5;
        WorldGenerator.spireCenterY = rodY + 1.5; // чуть выше soul_fire
        WorldGenerator.spireCenterZ = CZ + 0.5;
    }

    // =========================================================================
    // ИНТЕРЬЕР СОБОРА
    // =========================================================================

    private void buildCathedralInterior(RegionPainter p, int navHalfX, int navHalfZ,
                                         int trnHalfX, int trnHalfZ, int yBase) {
        // 4 ряда колонн по 3 в нефе. По длине Z: -8, -4, 4, 8 (избегаем центра).
        int[] colZs = { CZ - 8, CZ - 4, CZ + 4, CZ + 8 };
        for (int cz : colZs) {
            for (int dx : new int[] { -3, 3 }) {
                int xx = CX + dx;
                for (int dy = 1; dy <= CATHEDRAL_NAVE_H - 6; dy++) {
                    p.place(xx, yBase + dy, cz, Material.POLISHED_BLACKSTONE);
                }
                // Навершие колонны — золотая капитель.
                p.place(xx, yBase + CATHEDRAL_NAVE_H - 5, cz, Material.GOLD_BLOCK);
            }
        }

        // Алтарь в центре трансепта: 3×3 GOLD_BLOCK на полу + SOUL_LANTERN.
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                p.place(CX + ox, yBase + 1, CZ + oz, Material.GOLD_BLOCK);
            }
        }
        p.place(CX, yBase + 2, CZ, Material.SOUL_LANTERN);

        // Лавки (DARK_OAK_STAIRS) по 4 ряда в нефе.
        int[] benchZs = { CZ - 7, CZ - 5, CZ + 5, CZ + 7 };
        for (int bz : benchZs) {
            for (int bx = CX - 4; bx <= CX + 4; bx++) {
                if (bx == CX || Math.abs(bx - CX) >= 3) continue; // оставляем проход и место под колонны
                p.placeData(bx, yBase + 1, bz,
                        bz < CZ ? stairsSouth : stairsNorth);
            }
        }

        // Люстры: END_ROD на потолке + SOUL_LANTERN ниже.
        int chandelierY = yBase + CATHEDRAL_NAVE_H - 1;
        int[][] chandeliers = {
                { CX, CZ - 6 }, { CX, CZ + 6 },
                { CX - 6, CZ }, { CX + 6, CZ }
        };
        for (int[] c : chandeliers) {
            p.place(c[0], chandelierY, c[1], Material.END_ROD);
            p.place(c[0], chandelierY - 1, c[1], Material.SOUL_LANTERN);
        }
    }

    // =========================================================================
    // ПЛОЩАДЬ ВОКРУГ СОБОРА
    // =========================================================================

    private void buildCathedralPlaza(RegionPainter p) {
        int half = 15;
        int yBase = FLOOR_Y;
        for (int x = CX - half; x <= CX + half; x++) {
            for (int z = CZ - half; z <= CZ + half; z++) {
                // Не трогаем фундамент собора (он шире плазы — ок).
                p.place(x, yBase, z, Material.POLISHED_DEEPSLATE);
            }
        }
        // 4 фонарных столба по углам плазы.
        int[][] lamps = {
                { CX - half + 2, CZ - half + 2 },
                { CX + half - 2, CZ - half + 2 },
                { CX - half + 2, CZ + half - 2 },
                { CX + half - 2, CZ + half - 2 },
        };
        for (int[] l : lamps) {
            for (int dy = 1; dy <= 4; dy++) {
                p.place(l[0], yBase + dy, l[1], Material.OAK_FENCE);
            }
            p.place(l[0], yBase + 5, l[1], Material.SOUL_LANTERN);
        }
        // Несколько клумб с фиолетовыми цветами по краю плазы.
        Material[] purpleFlowers = { Material.ALLIUM, Material.LILAC };
        Random rng = new Random((long) (CX * 31L + CZ));
        int placed = 0;
        for (int x = CX - half; x <= CX + half && placed < 24; x++) {
            for (int z = CZ - half; z <= CZ + half && placed < 24; z++) {
                int dx = Math.abs(x - CX), dz = Math.abs(z - CZ);
                if (dx < half - 2 && dz < half - 2) continue; // только по краю
                if (rng.nextDouble() < 0.10) {
                    Material flower = purpleFlowers[rng.nextInt(purpleFlowers.length)];
                    p.place(x, yBase + 1, z, flower);
                    placed++;
                }
            }
        }
    }
}
