package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Собор Эликий — главное здание города. Готическая архитектура: длинный
 * неф вдоль оси Z, фронтальный южный портал, контрфорсы, витражи,
 * двускатная крыша, центральный шпиль с «глазом» наверху и баннер.
 *
 * <p><b>Геометрия (×3-масштаб, PR 2.7+)</b>:
 * <ul>
 *   <li>Центр: ({@link WorldGenerator#CATHEDRAL_X}, {@link WorldGenerator#CATHEDRAL_Z})
 *       = (45, -15).</li>
 *   <li>Footprint: 60 (x) × 84 (z); короткая сторона — фасад, длинная — неф.</li>
 *   <li>Стены: y=70..99 (высота 30); толщина 2 блока, фундамент 2 блока вглубь.</li>
 *   <li>Крыша: двускатная, фронтоном на юг/север; пик y=119.</li>
 *   <li>Шпиль: центрированный над крышей; вершина y=189 (общая
 *       высота 120 от пола).</li>
 *   <li>Глаз Эликия: 11×11 розетка на южном фасаде, y=85..95.</li>
 *   <li>Контрфорсы: 6 пар по бокам (x=±30 от центра), z={-35,-21,-7,7,21,35}.</li>
 * </ul>
 *
 * <p>Все блоки кладутся через {@link RegionPainter} — асинхронно, без
 * фризов TPS. {@code build()} только описывает геометрию, физическая
 * заливка происходит в {@code RegionPainter.flush()} после фазы 8.
 */
public final class CathedralBuilder {

    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70
    private static final int CX = WorldGenerator.CATHEDRAL_X;      // 45
    private static final int CZ = WorldGenerator.CATHEDRAL_Z;      // -15

    /** Полу-ширина (x) и полу-длина (z) собора. */
    private static final int HALF_WIDTH  = 30; // x ∈ [CX-30..CX+30] = [15..75]
    private static final int HALF_LENGTH = 42; // z ∈ [CZ-42..CZ+42] = [-57..27]

    private static final int WALL_HEIGHT  = 30;                       // y=70..99
    private static final int ROOF_PEAK_DY = 20;                       // выше стены
    private static final int ROOF_PEAK_Y  = Y_BASE + WALL_HEIGHT + ROOF_PEAK_DY; // 120

    /** Высота центрального шпиля над крышей. */
    private static final int SPIRE_HEIGHT = 70;
    private static final int SPIRE_BASE_Y = ROOF_PEAK_Y;                       // 120
    private static final int SPIRE_TOP_Y  = SPIRE_BASE_Y + SPIRE_HEIGHT - 1;   // 189
    private static final int FLAG_TOP_Y   = SPIRE_TOP_Y + 5;                   // 194

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
                "CathedralBuilder: строю собор (45,-15) 60×84, h=30, шпиль до y="
                + SPIRE_TOP_Y + "…");

        long ops = 0;
        ops += buildFloor();
        ops += buildWalls();
        ops += buildButtresses();
        ops += buildWindows();
        ops += buildSouthPortal();
        ops += buildRoof();
        ops += buildSpire();
        ops += buildEyeOnFacade();
        ops += buildFlag();

        plugin.getLogger().info(
                "CathedralBuilder: ~" + ops + " блок-операций (стены, крыша, шпиль, глаз).");

        // Обновляем координаты «глаза» для SpireParticles, чтобы частицы
        // крутились над вершиной шпиля (а не на старой заглушке y=121).
        WorldGenerator.spireCenterX = CX + 0.5;
        WorldGenerator.spireCenterY = SPIRE_TOP_Y + 0.5;
        WorldGenerator.spireCenterZ = CZ + 0.5;
    }

    // =========================================================================
    // ФАЗА 1: ПОЛ И ФУНДАМЕНТ
    // =========================================================================

    private long buildFloor() {
        long count = 0;
        // Внутренний пол собора — POLISHED_DEEPSLATE с DEEPSLATE_TILES-крестом.
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dz = -HALF_LENGTH; dz <= HALF_LENGTH; dz++) {
                Material floor;
                // Центральный «трансепт» — крест из DEEPSLATE_TILES.
                boolean onCentralAisle = Math.abs(dx) <= 4;
                boolean onTransept     = Math.abs(dz) <= 4;
                if (onCentralAisle || onTransept) {
                    floor = Material.DEEPSLATE_TILES;
                } else {
                    floor = ((dx + dz) & 1) == 0
                            ? Material.POLISHED_DEEPSLATE
                            : Material.DEEPSLATE_BRICKS;
                }
                painter.place(CX + dx, Y_BASE, CZ + dz, floor);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 2: ВНЕШНИЕ СТЕНЫ
    // =========================================================================

    private long buildWalls() {
        long count = 0;
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            int y = Y_BASE + dy;
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
                for (int dz = -HALF_LENGTH; dz <= HALF_LENGTH; dz++) {
                    boolean onPerimeter =
                            Math.abs(dx) >= HALF_WIDTH - 1
                            || Math.abs(dz) >= HALF_LENGTH - 1;
                    if (!onPerimeter) continue;

                    Material mat = pickWallMaterial(dx, dy, dz);
                    painter.place(CX + dx, y, CZ + dz, mat);
                    count++;
                }
            }
        }
        return count;
    }

    /** Стилевой выбор материала для внешних стен. */
    private Material pickWallMaterial(int dx, int dy, int dz) {
        boolean isCorner = Math.abs(dx) >= HALF_WIDTH - 1
                && Math.abs(dz) >= HALF_LENGTH - 1;
        if (isCorner) {
            // Угловые столбы — SMOOTH_QUARTZ.
            return Material.SMOOTH_QUARTZ;
        }
        // Декоративные пояса каждые 6 блоков по высоте.
        if (dy == 1 || dy == 2) {
            return Material.POLISHED_DIORITE;
        }
        if (dy % 6 == 0) {
            return Material.CHISELED_QUARTZ_BLOCK;
        }
        if (dy == WALL_HEIGHT) {
            // Карниз верха стены.
            return Material.QUARTZ_BRICKS;
        }
        // Основное тело — QUARTZ_BLOCK.
        return Material.QUARTZ_BLOCK;
    }

    // =========================================================================
    // ФАЗА 3: КОНТРФОРСЫ (BUTTRESSES)
    // =========================================================================

    private long buildButtresses() {
        long count = 0;
        int[] zs = { -35, -21, -7, 7, 21, 35 };
        for (int dz : zs) {
            for (int side : new int[] { -1, +1 }) {
                int bx = CX + side * (HALF_WIDTH + 1); // на 1 блок вне стены
                int bz = CZ + dz;
                count += buildOneButtress(bx, bz, side);
            }
        }
        return count;
    }

    /**
     * Один контрфорс: 3×3 столб от земли до y=99, увенчанный
     * QUARTZ_PILLAR. Отделён от основной стены узким «арочным» проёмом.
     */
    private long buildOneButtress(int bx, int bz, int outwardSide) {
        long count = 0;
        for (int dy = 0; dy <= WALL_HEIGHT - 2; dy++) {
            int y = Y_BASE + dy;
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    Material mat;
                    if (dy == 0) {
                        mat = Material.POLISHED_DEEPSLATE;
                    } else if (dy <= 2) {
                        mat = Material.POLISHED_DIORITE;
                    } else if (dy == WALL_HEIGHT - 2) {
                        mat = Material.CHISELED_QUARTZ_BLOCK;
                    } else if (ox == 0 && oz == 0) {
                        // Сердечник — QUARTZ_PILLAR с осью Y.
                        BlockData pillar = Material.QUARTZ_PILLAR.createBlockData();
                        if (pillar instanceof Orientable) {
                            ((Orientable) pillar).setAxis(Axis.Y);
                        }
                        painter.placeData(bx + ox, y, bz + oz, pillar);
                        count++;
                        continue;
                    } else {
                        mat = Material.QUARTZ_BLOCK;
                    }
                    painter.place(bx + ox, y, bz + oz, mat);
                    count++;
                }
            }
        }
        // Кровлевая «корона» контрфорса +2 блока.
        for (int dy = WALL_HEIGHT - 1; dy <= WALL_HEIGHT; dy++) {
            painter.place(bx, Y_BASE + dy, bz, Material.QUARTZ_BRICKS);
            count++;
        }
        painter.place(bx, Y_BASE + WALL_HEIGHT + 1, bz, Material.END_ROD);
        count++;
        return count;
    }

    // =========================================================================
    // ФАЗА 4: ВИТРАЖИ (8 ПРОЛЁТОВ)
    // =========================================================================

    private long buildWindows() {
        long count = 0;
        // Окна на восточной/западной стенах — между контрфорсами.
        // 5 пролётов × 2 стены = 10 окон. По 4 шир × 8 выс.
        int[] windowZs = { -28, -14, 0, 14, 28 };
        for (int wz : windowZs) {
            for (int side : new int[] { -1, +1 }) {
                count += buildOneWindow(side, wz);
            }
        }
        return count;
    }

    private long buildOneWindow(int outwardSide, int dz) {
        long count = 0;
        int x = CX + outwardSide * (HALF_WIDTH - 1); // внутренний слой стены (×2 толщины)
        // Окно: dz-3..dz+3 по Z (ширина 7), y=82..93 (высота 12).
        for (int oz = -3; oz <= 3; oz++) {
            for (int dy = 12; dy <= 23; dy++) {
                int y = Y_BASE + dy;
                Material glass;
                // Рамка тёмная.
                boolean isFrame =
                        Math.abs(oz) == 3
                        || dy == 12 || dy == 23
                        || (dy == 17 || dy == 18);
                if (isFrame) {
                    glass = Material.DEEPSLATE_TILES;
                } else if (Math.abs(oz) <= 1) {
                    glass = Material.BLUE_STAINED_GLASS;
                } else {
                    glass = Material.LIGHT_BLUE_STAINED_GLASS;
                }
                // Кладём И на внешней, И на внутренней толщине стены.
                painter.place(x, y, CZ + dz + oz, glass);
                painter.place(x + outwardSide, y, CZ + dz + oz, glass);
                count += 2;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 5: ЮЖНЫЙ ПОРТАЛ (ВХОД)
    // =========================================================================

    private long buildSouthPortal() {
        long count = 0;
        int wz = HALF_LENGTH;          // z=42 от центра — южная стена
        int absZ = CZ + wz;
        // Проём 9 ширина × 16 высота, центрирован на dx=0.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 16; dy++) {
                // Кладём AIR на оба слоя толщины южной стены.
                painter.place(CX + dx, Y_BASE + dy, absZ,     Material.AIR);
                painter.place(CX + dx, Y_BASE + dy, absZ - 1, Material.AIR);
                count += 2;
            }
        }
        // Декоративная арка над проёмом (полукруг y=17..21).
        for (int dx = -5; dx <= 5; dx++) {
            int dy;
            int adx = Math.abs(dx);
            if (adx == 5) dy = 17;
            else if (adx == 4) dy = 18;
            else if (adx >= 2) dy = 19;
            else dy = 20;
            painter.place(CX + dx, Y_BASE + dy, absZ,     Material.CHISELED_QUARTZ_BLOCK);
            painter.place(CX + dx, Y_BASE + dy, absZ - 1, Material.CHISELED_QUARTZ_BLOCK);
            count += 2;
        }
        // «Замковый камень».
        painter.place(CX, Y_BASE + 21, absZ,     Material.GOLD_BLOCK);
        painter.place(CX, Y_BASE + 21, absZ - 1, Material.GOLD_BLOCK);
        count += 2;

        // Крыльцо — 3 ступени QUARTZ_STAIRS, выходят из проёма на юг.
        for (int step = 1; step <= 3; step++) {
            int sz = absZ + step;
            for (int dx = -5; dx <= 5; dx++) {
                Material stair = (step == 1) ? Material.QUARTZ_BLOCK : Material.SMOOTH_QUARTZ;
                painter.place(CX + dx, Y_BASE, sz, stair);
                count++;
            }
        }
        // Лантерны по бокам портала.
        painter.place(CX - 6, Y_BASE + 4, absZ + 1, Material.SOUL_LANTERN);
        painter.place(CX + 6, Y_BASE + 4, absZ + 1, Material.SOUL_LANTERN);
        count += 2;
        return count;
    }

    // =========================================================================
    // ФАЗА 6: ДВУСКАТНАЯ КРЫША
    // =========================================================================

    private long buildRoof() {
        long count = 0;
        // Гребень бежит вдоль Z (с юга на север), пик при x=CX.
        // Каждый уровень y увеличивается → скаты сужаются.
        for (int rise = 0; rise <= ROOF_PEAK_DY; rise++) {
            int y = Y_BASE + WALL_HEIGHT + rise;
            int dxAt = HALF_WIDTH - rise; // skat narrows with height
            if (dxAt < 0) break;
            for (int dz = -HALF_LENGTH; dz <= HALF_LENGTH; dz++) {
                // Левый скат и правый скат — два ряда блоков.
                painter.place(CX - dxAt, y, CZ + dz, Material.DEEPSLATE_TILES);
                painter.place(CX + dxAt, y, CZ + dz, Material.DEEPSLATE_TILES);
                count += 2;
                // Заполняем пространство «под крышей» CHISELED_DEEPSLATE
                // только по периметру (фронтоны), чтобы не блокировать
                // пространство — внутри крыша полая.
                if (Math.abs(dz) >= HALF_LENGTH - 1) {
                    for (int innerDx = -dxAt + 1; innerDx <= dxAt - 1; innerDx++) {
                        painter.place(CX + innerDx, y, CZ + dz, Material.CHISELED_DEEPSLATE);
                        count++;
                    }
                }
            }
        }
        // Гребень на самом верху.
        for (int dz = -HALF_LENGTH; dz <= HALF_LENGTH; dz++) {
            painter.place(CX, ROOF_PEAK_Y, CZ + dz, Material.POLISHED_BLACKSTONE);
            count++;
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 7: ЦЕНТРАЛЬНЫЙ ШПИЛЬ
    // =========================================================================

    private long buildSpire() {
        long count = 0;
        // Шпиль конусом от радиуса 7 (квадратный 15×15) у основания
        // до радиуса 0 на пике. Высота 70.
        for (int dy = 0; dy < SPIRE_HEIGHT; dy++) {
            int y = SPIRE_BASE_Y + dy;
            // Радиус убывает с высотой: r=7 в начале → r=0 на пике.
            double t = (double) dy / SPIRE_HEIGHT;
            int r = (int) Math.round(7 * (1.0 - t));
            if (r < 1) r = 1;
            // Кладём «оболочку» квадрата.
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    int axx = Math.max(Math.abs(ox), Math.abs(oz));
                    if (axx != r && axx != r - 1) continue; // только тонкая оболочка
                    Material mat;
                    // Рёбра — END_STONE_BRICKS, грани — CHISELED_QUARTZ_BLOCK,
                    // пояса каждые 8 — QUARTZ_PILLAR (ось Y).
                    boolean isCorner = Math.abs(ox) == r && Math.abs(oz) == r;
                    boolean isBand = (dy % 8 == 0);
                    if (isCorner) {
                        mat = Material.END_STONE_BRICKS;
                    } else if (isBand) {
                        mat = Material.CHISELED_QUARTZ_BLOCK;
                    } else if (axx == r) {
                        mat = Material.SMOOTH_QUARTZ;
                    } else {
                        mat = Material.QUARTZ_BRICKS;
                    }
                    painter.place(CX + ox, y, CZ + oz, mat);
                    count++;
                }
            }
            // На рёбрах через каждые 12 блоков — END_ROD маяк.
            if (dy > 5 && dy % 12 == 0 && r >= 2) {
                painter.place(CX + r, y, CZ + r, Material.END_ROD);
                painter.place(CX - r, y, CZ + r, Material.END_ROD);
                painter.place(CX + r, y, CZ - r, Material.END_ROD);
                painter.place(CX - r, y, CZ - r, Material.END_ROD);
                count += 4;
            }
        }
        // Корона на самом верху (y=189): NETHERITE_BLOCK 3×3, маяк END_ROD.
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                painter.place(CX + ox, SPIRE_TOP_Y, CZ + oz, Material.NETHERITE_BLOCK);
                count++;
            }
        }
        painter.place(CX, SPIRE_TOP_Y + 1, CZ, Material.LIGHTNING_ROD);
        painter.place(CX, SPIRE_TOP_Y + 2, CZ, Material.END_ROD);
        count += 2;
        return count;
    }

    // =========================================================================
    // ФАЗА 8: «ГЛАЗ ЭЛИКИЯ» НА ЮЖНОМ ФРОНТОНЕ
    // =========================================================================

    private long buildEyeOnFacade() {
        long count = 0;
        // Глаз на фронтоне над крышей, центр (CX, y=110, CZ + HALF_LENGTH).
        // Виден от южных ворот (z=113) сквозь арку.
        int eyeY = Y_BASE + 36;       // y=106
        int eyeZ = CZ + HALF_LENGTH;  // z=27 — внешняя плоскость южной стены/фронтона
        // Розетка 11×11.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                if (dist > 5) continue;
                Material mat;
                if (dist == 5) {
                    mat = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (dist >= 3) {
                    mat = Material.LAPIS_BLOCK;
                } else if (dist >= 1) {
                    mat = Material.EMERALD_BLOCK;
                } else {
                    // Центр — END_ROD (направлен на юг).
                    BlockData rod = Material.END_ROD.createBlockData();
                    if (rod instanceof Directional) {
                        ((Directional) rod).setFacing(BlockFace.SOUTH);
                    }
                    painter.placeData(CX + dx, eyeY + dy, eyeZ, rod);
                    count++;
                    continue;
                }
                painter.place(CX + dx, eyeY + dy, eyeZ, mat);
                count++;
            }
        }
        // Лучи света от глаза: 4 направления (E, W, U, D), по 3 блока.
        int[][] rays = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        for (int[] ray : rays) {
            BlockFace face;
            if (ray[0] > 0) face = BlockFace.EAST;
            else if (ray[0] < 0) face = BlockFace.WEST;
            else if (ray[1] > 0) face = BlockFace.UP;
            else face = BlockFace.DOWN;
            for (int step = 6; step <= 8; step++) {
                BlockData rod = Material.END_ROD.createBlockData();
                if (rod instanceof Directional) {
                    ((Directional) rod).setFacing(face);
                }
                painter.placeData(CX + ray[0] * step, eyeY + ray[1] * step, eyeZ, rod);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // ФАЗА 9: ФЛАГ НА ВЕРШИНЕ ШПИЛЯ
    // =========================================================================

    private long buildFlag() {
        long count = 0;
        // Флагшток (END_ROD) сидит на наконечнике шпиля, сам флаг — баннер.
        // На ванильном Minecraft 1.20: BLUE_BANNER (стоячий) — это floor banner.
        // Поскольку он не помещается на шпиль шириной 1 — ставим вокруг.
        // Простое решение: BLUE_WOOL «полотно» 3×4, висящее на шпиле.
        int poleY = SPIRE_TOP_Y + 3;
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Material mat = (dx == 0)
                        ? Material.BLUE_WOOL
                        : Material.LIGHT_BLUE_WOOL;
                painter.place(CX + dx, poleY + dy, CZ, mat);
                count++;
            }
        }
        // Хвост флага — 2 ряда вниз, треугольником.
        painter.place(CX - 1, poleY - 1, CZ, Material.BLUE_WOOL);
        painter.place(CX + 1, poleY - 1, CZ, Material.BLUE_WOOL);
        painter.place(CX,     poleY - 1, CZ, Material.LIGHT_BLUE_WOOL);
        count += 3;
        // Маяк выше флага.
        painter.place(CX, FLAG_TOP_Y,     CZ, Material.END_ROD);
        painter.place(CX, FLAG_TOP_Y + 1, CZ, Material.END_ROD);
        painter.place(CX, FLAG_TOP_Y + 2, CZ, Material.BEACON);
        count += 3;
        return count;
    }
}
