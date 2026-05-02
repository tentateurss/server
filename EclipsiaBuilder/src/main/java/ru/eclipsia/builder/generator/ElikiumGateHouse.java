package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

/**
 * Готический «гейтхаус» — массивные ворота с парой высоких башен,
 * стрельчатой аркой, фиолетовыми витражами, золотыми вставками и
 * парящей вывеской «ЭЛИКИЙ»/«ELIKIUM», нарисованной из светящихся
 * блоков. По стилю — точная копия референса (городские ворота на
 * последней пользовательской фотке).
 *
 * <p>{@link ElikiumWall} вызывает этот класс четырежды — по одному разу
 * на каждый из {@link WorldGenerator#SOUTH_GATE},
 * {@link WorldGenerator#NORTH_GATE}, {@link WorldGenerator#EAST_GATE},
 * {@link WorldGenerator#WEST_GATE}. Для южных ворот (через которые
 * приходит игрок после Хранителя Врат) используется специальный
 * расширенный режим — большая надпись «ELIKIUM», статуи стражей,
 * парящие частицы и фонари по всему пути.
 *
 * <p><b>Геометрия гейтхауса</b> (для горизонтальных ворот, vertical=false):
 * <ul>
 *   <li>Проём 11×16 (ширина×высота) — на 2 блока выше ванильного wall-проёма.</li>
 *   <li>Две фланкирующие башни 9×9, высота 38 блоков, скат-конус на крыше
 *       со шпилем END_ROD/LIGHTNING_ROD высотой 7 блоков.</li>
 *   <li>Над аркой — 2-этажный «фронтон» с большой надписью «ELIKIUM»
 *       3×7 на южных воротах, 3-блочные витражи на остальных.</li>
 *   <li>По бокам арки — узкие лансет-витражи PURPLE_STAINED_GLASS,
 *       подсвеченные SHROOMLIGHT/AMETHYST.</li>
 *   <li>SOUL_LANTERN-фонари на цепях с обеих сторон арки + END_ROD по
 *       контуру арки (фиолетовое свечение как на референсе).</li>
 * </ul>
 *
 * <p><b>Материалы</b>: DEEPSLATE_BRICKS (тело стены), POLISHED_BLACKSTONE
 * (полированные пилоны), GILDED_BLACKSTONE (золотая инкрустация),
 * GOLD_BLOCK (буквы «ELIKIUM»), PURPLE_STAINED_GLASS (витражи),
 * AMETHYST_BLOCK (магические вставки), SHROOMLIGHT (тёплый свет за
 * витражами), SOUL_LANTERN/END_ROD (фиолетовый свет снаружи).
 */
public final class ElikiumGateHouse {

    /** Базовый y. */
    static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70

    /** Высота главных флангирующих башен (от земли). */
    private static final int TOWER_HEIGHT = 38;
    /** Полу-сторона квадратной башни (полная сторона = 2*HALF+1 = 9). */
    private static final int TOWER_HALF = 4;

    /** Полу-ширина проёма (полная ширина = 2*HALF+1 = 11). */
    private static final int OPENING_HALF = 5;
    /** Высота проёма (от пола до низа арки). */
    private static final int OPENING_HEIGHT = 14;

    /** Высота арки над проёмом (стрельчатая). */
    private static final int ARCH_HEIGHT = 9;

    private final Plugin plugin;
    private final World world;
    private final RegionPainter painter;

    public ElikiumGateHouse(Plugin plugin, RegionPainter painter) {
        this.plugin = plugin;
        this.painter = painter;
        this.world = painter.world();
    }

    /**
     * Построить гейтхаус в точке {@code (gx, gz)} с указанным направлением
     * стены. Если {@code horizontal=true}, стена тянется вдоль оси X
     * (южные/северные ворота); если false — вдоль оси Z (восточные/западные).
     *
     * @param signTitle    короткая надпись над аркой (рисуется буквами,
     *                     видна за километр) — для южных ворот «ELIKIUM».
     * @param hoverTitle   текст FloatingText прямо над аркой (читается
     *                     с близи).
     * @param hoverSubtitle подзаголовок для FloatingText.
     * @param mainEntry    {@code true} для южных ворот — добавляет
     *                     дополнительную атмосферу (статуи, фонари
     *                     на каньоне, парящий «Глаз»).
     */
    public void build(int gx, int gz, boolean horizontal,
                      String signTitle, String hoverTitle,
                      String hoverSubtitle, boolean mainEntry) {
        plugin.getLogger().info("ElikiumGateHouse: гейтхаус в (" + gx + ", " + gz
                + ") horizontal=" + horizontal + " mainEntry=" + mainEntry);

        // 1) Очистить пространство арки (на случай старых блоков).
        clearOpeningSpace(gx, gz, horizontal);

        // 2) Две фланкирующие башни.
        int towerOffset = OPENING_HALF + TOWER_HALF + 1; // 5+4+1 = 10
        if (horizontal) {
            buildTower(gx - towerOffset, gz);
            buildTower(gx + towerOffset, gz);
        } else {
            buildTower(gx, gz - towerOffset);
            buildTower(gx, gz + towerOffset);
        }

        // 3) Стрельчатая арка над проёмом.
        buildPointedArch(gx, gz, horizontal);

        // 4) Фронтон + большая надпись над аркой.
        buildPedimentSign(gx, gz, horizontal, signTitle);

        // 5) Декор: лансет-витражи, фонари, золотая инкрустация.
        buildDecoration(gx, gz, horizontal);

        // 6) Парящий FloatingText прямо над аркой (для близкого чтения).
        spawnFloatingTitle(gx, gz, horizontal, hoverTitle, hoverSubtitle);

        // 7) Для южных ворот — путь к городу: статуи стражей и фонари
        //    вдоль каньона, парящий «Глаз» над аркой.
        if (mainEntry) {
            buildSouthernApproach(gx, gz);
        }
    }

    // =========================================================================
    // ШАГ 1 — ОЧИСТКА ПРОЁМА
    // =========================================================================

    private void clearOpeningSpace(int gx, int gz, boolean horizontal) {
        int yMax = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT + 8;
        // Всё пространство башен + арки + поглощение «сноса» от
        // wall trace (Bresenham рассеивает блоки на ±2 z от линии,
        // а толщина стены = 9 → может бить до z±6 от оси). Чистим
        // dz=-8..8, чтобы гарантированно перекрыть вообще всё.
        for (int dx = -OPENING_HALF - TOWER_HALF * 2 - 2;
             dx <= OPENING_HALF + TOWER_HALF * 2 + 2; dx++) {
            for (int dz = -TOWER_HALF - 4; dz <= TOWER_HALF + 4; dz++) {
                int x = horizontal ? gx + dx : gx + dz;
                int z = horizontal ? gz + dz : gz + dx;
                for (int y = Y_BASE + 1; y <= yMax; y++) {
                    painter.place(x, y, z, Material.AIR);
                }
            }
        }
    }

    // =========================================================================
    // ШАГ 2 — ФЛАНКИРУЮЩАЯ БАШНЯ
    // =========================================================================

    private void buildTower(int cx, int cz) {
        int rOuter = TOWER_HALF;        // 4 → 9×9 outer
        int rInner = TOWER_HALF - 1;    // 3 → полая внутри

        // Фундамент — массивный COBBLED_DEEPSLATE.
        for (int dx = -rOuter - 1; dx <= rOuter + 1; dx++) {
            for (int dz = -rOuter - 1; dz <= rOuter + 1; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) > rOuter + 1) continue;
                painter.place(cx + dx, Y_BASE - 1, cz + dz, Material.COBBLED_DEEPSLATE);
                painter.place(cx + dx, Y_BASE,     cz + dz, Material.COBBLED_DEEPSLATE);
            }
        }

        // Стены башни (квадратное сечение).
        for (int dy = 1; dy <= TOWER_HEIGHT; dy++) {
            int y = Y_BASE + dy;
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                for (int dz = -rOuter; dz <= rOuter; dz++) {
                    int chess = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean wall = (chess == rOuter);
                    if (!wall) continue;

                    Material mat = pickTowerMaterial(dx, dy, dz);
                    painter.place(cx + dx, y, cz + dz, mat);
                }
            }
        }

        // Карнизы из стейров на y = TOWER_HEIGHT - 6 (антаблемент) и y = TOWER_HEIGHT.
        placeCornice(cx, cz, Y_BASE + TOWER_HEIGHT - 6, rOuter);
        placeCornice(cx, cz, Y_BASE + TOWER_HEIGHT,     rOuter);

        // Стрельчатые лансет-окна на 3 уровнях.
        for (int slitY : new int[]{Y_BASE + 14, Y_BASE + 22, Y_BASE + 30}) {
            placeLancetWindow(cx + rOuter, slitY, cz, "+x");
            placeLancetWindow(cx - rOuter, slitY, cz, "-x");
            placeLancetWindow(cx, slitY, cz + rOuter, "+z");
            placeLancetWindow(cx, slitY, cz - rOuter, "-z");
        }

        // Боевая площадка на крыше.
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                int chess = Math.max(Math.abs(dx), Math.abs(dz));
                if (chess > rInner) continue;
                painter.place(cx + dx, Y_BASE + TOWER_HEIGHT + 1, cz + dz,
                        Material.POLISHED_BLACKSTONE);
            }
        }
        // Зубцы по периметру крыши.
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                int chess = Math.max(Math.abs(dx), Math.abs(dz));
                if (chess != rOuter) continue;
                if (((dx + dz) & 1) == 0) {
                    painter.place(cx + dx, Y_BASE + TOWER_HEIGHT + 2, cz + dz,
                            Material.DEEPSLATE_BRICK_WALL);
                }
            }
        }

        // Готическая остроконечная крыша из стейров (пирамидка).
        int roofBaseY = Y_BASE + TOWER_HEIGHT + 2;
        for (int level = 0; level < 6; level++) {
            int rAt = rOuter - level;
            if (rAt < 1) {
                // Шпиль.
                for (int sy = 0; sy < 7; sy++) {
                    Material spire = sy == 6
                            ? Material.LIGHTNING_ROD
                            : Material.END_ROD;
                    painter.place(cx, roofBaseY + level + sy, cz, spire);
                }
                break;
            }
            for (int dx = -rAt; dx <= rAt; dx++) {
                for (int dz = -rAt; dz <= rAt; dz++) {
                    int chess = Math.max(Math.abs(dx), Math.abs(dz));
                    if (chess == rAt) {
                        painter.place(cx + dx, roofBaseY + level, cz + dz,
                                Material.POLISHED_BLACKSTONE);
                    } else if (chess < rAt) {
                        // Внутренняя начинка крыши.
                        painter.place(cx + dx, roofBaseY + level, cz + dz,
                                Material.POLISHED_BLACKSTONE);
                    }
                }
            }
        }

        // Угловые SOUL_LANTERN на y = TOWER_HEIGHT (4 фонаря по углам).
        int lanternY = Y_BASE + TOWER_HEIGHT + 1;
        for (int sx : new int[]{-rOuter + 1, rOuter - 1}) {
            for (int sz : new int[]{-rOuter + 1, rOuter - 1}) {
                painter.place(cx + sx, lanternY, cz + sz, Material.SOUL_LANTERN);
            }
        }

        // Знамя на верхушке башни (PURPLE_BANNER).
        painter.place(cx, lanternY + 1, cz, Material.PURPLE_BANNER);
    }

    private Material pickTowerMaterial(int dx, int dy, int dz) {
        // Полированные углы.
        int chess = Math.max(Math.abs(dx), Math.abs(dz));
        if (chess == TOWER_HALF && Math.abs(dx) == TOWER_HALF && Math.abs(dz) == TOWER_HALF) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        // Декоративные пояса каждые 6 блоков.
        if (dy % 6 == 0) return Material.POLISHED_BLACKSTONE;
        if (dy == 1 || dy == 2) return Material.COBBLED_DEEPSLATE; // фундамент
        // Случайные «золотые» вставки.
        int hash = (dx * 73856093) ^ (dy * 19349663) ^ (dz * 83492791);
        if (Math.floorMod(hash, 80) == 0) return Material.GILDED_BLACKSTONE;
        return Material.DEEPSLATE_BRICKS;
    }

    private void placeCornice(int cx, int cz, int y, int rOuter) {
        int r = rOuter + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int chess = Math.max(Math.abs(dx), Math.abs(dz));
                if (chess != r) continue;
                painter.place(cx + dx, y, cz + dz,
                        Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            }
        }
    }

    private void placeLancetWindow(int x, int y, int z, String facing) {
        // Окно 1×3: AMETHYST_BLOCK (нижний), PURPLE_STAINED_GLASS (центр),
        // AMETHYST_BLOCK (верхний). За окном — SHROOMLIGHT для свечения.
        painter.place(x, y, z, Material.PURPLE_STAINED_GLASS);
        painter.place(x, y + 1, z, Material.PURPLE_STAINED_GLASS);
        painter.place(x, y + 2, z, Material.AMETHYST_BLOCK);

        // Источник света за окном (внутри башни).
        int lx = x, lz = z;
        switch (facing) {
            case "+x" -> lx -= 1;
            case "-x" -> lx += 1;
            case "+z" -> lz -= 1;
            case "-z" -> lz += 1;
            default -> {
            }
        }
        painter.place(lx, y, lz, Material.SHROOMLIGHT);
    }

    // =========================================================================
    // ШАГ 3 — СТРЕЛЬЧАТАЯ АРКА
    // =========================================================================

    private void buildPointedArch(int gx, int gz, boolean horizontal) {
        // Перпендикуляр к стене — толщина «тоннеля» арки = 9 блоков
        // (равно толщине стены, чтобы по бокам арки не оставалось дыр
        // между башнями и гейтхаусом).
        int thickness = 9;
        int halfThickness = thickness / 2; // 4

        // 3.1 СНАЧАЛА строим полностью сплошную стену гейтхауса:
        //     dx∈[-OPENING_HALF-1..OPENING_HALF+1] (одна полоса до башен),
        //     перпендикуляр t∈[-4..4] (вся толщина),
        //     y∈[Y_BASE..Y_BASE+OPENING_HEIGHT+ARCH_HEIGHT].
        //     Это дает массивный «параллелепипед» стены, в который мы
        //     потом ВЫРЕЖЕМ стрельчатую арку.
        int wallTop = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT;
        for (int dx = -OPENING_HALF - 1; dx <= OPENING_HALF + 1; dx++) {
            for (int t = -halfThickness; t <= halfThickness; t++) {
                int x = horizontal ? gx + dx : gx + t;
                int z = horizontal ? gz + t  : gz + dx;
                for (int y = Y_BASE; y <= wallTop; y++) {
                    Material mat = pickArchWallMaterial(dx, y - Y_BASE, t);
                    painter.place(x, y, z, mat);
                }
            }
        }

        // 3.2 ВЫРЕЗАЕМ стрельчатую арку через всю толщину стены.
        //     На каждом уровне dy ширина «дыры» сужается по профилю
        //     (1-t)^1.7 — настоящая готическая стрельчатая форма,
        //     не полукруг.
        for (int dy = 0; dy <= ARCH_HEIGHT - 1; dy++) {
            int y = Y_BASE + OPENING_HEIGHT + dy;
            double t = (double) dy / ARCH_HEIGHT;
            double profile = Math.pow(1.0 - t, 1.7);
            int halfW = (int) Math.round(OPENING_HALF * profile);
            for (int t2 = -halfThickness; t2 <= halfThickness; t2++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    int x = horizontal ? gx + dx : gx + t2;
                    int z = horizontal ? gz + t2 : gz + dx;
                    painter.place(x, y, z, Material.AIR);
                }
            }
        }
        // Вырезаем основной проём (без арки) — y=Y_BASE..Y_BASE+OPENING_HEIGHT-1.
        for (int dy = 0; dy < OPENING_HEIGHT; dy++) {
            int y = Y_BASE + dy;
            for (int t2 = -halfThickness; t2 <= halfThickness; t2++) {
                for (int dx = -OPENING_HALF; dx <= OPENING_HALF; dx++) {
                    int x = horizontal ? gx + dx : gx + t2;
                    int z = horizontal ? gz + t2 : gz + dx;
                    painter.place(x, y, z, Material.AIR);
                }
            }
        }

        // 3.3 Замочный камень (keystone) на пике дуги — GOLD_BLOCK
        //     по центру.
        for (int t2 = -halfThickness; t2 <= halfThickness; t2 += halfThickness) {
            int y = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT;
            int x = horizontal ? gx : gx + t2;
            int z = horizontal ? gz + t2 : gz;
            painter.place(x, y, z, Material.GOLD_BLOCK);
        }

        // 3.4 Подсветка контура арки END_ROD-ами (внутренний обвод
        //     по передней и задней плоскости стены).
        for (int dy = 0; dy <= ARCH_HEIGHT - 1; dy++) {
            int y = Y_BASE + OPENING_HEIGHT + dy;
            double t = (double) dy / ARCH_HEIGHT;
            int halfW = (int) Math.round(OPENING_HALF * Math.pow(1.0 - t, 1.7));
            for (int t2 : new int[]{-halfThickness, halfThickness}) {
                for (int sign : new int[]{-1, 1}) {
                    int dx = sign * halfW;
                    int x = horizontal ? gx + dx : gx + t2;
                    int z = horizontal ? gz + t2 : gz + dx;
                    painter.place(x, y, z, Material.END_ROD);
                }
            }
        }
    }

    /** Материал для стенки гейтхауса вокруг арки. Полированные углы
     *  (dy<=1, jambs), пояса GILDED_BLACKSTONE каждые 4 блока, остальное
     *  POLISHED_BLACKSTONE_BRICKS — массивная готика. */
    private Material pickArchWallMaterial(int dx, int dy, int t) {
        if (dy <= 1) return Material.POLISHED_BLACKSTONE; // фундамент
        if (Math.abs(dx) == OPENING_HALF + 1) {
            // боковые «лопатки» — полированный камень с золотыми полосами
            if (dy % 4 == 0) return Material.GILDED_BLACKSTONE;
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (dy % 6 == 0) return Material.POLISHED_BLACKSTONE; // карнизы
        return Material.DEEPSLATE_BRICKS;
    }

    // =========================================================================
    // ШАГ 4 — ФРОНТОН + НАДПИСЬ ELIKIUM
    // =========================================================================

    private void buildPedimentSign(int gx, int gz, boolean horizontal, String signTitle) {
        // Высота фронтона над аркой.
        int pediYTop = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT + 5;
        int pediYBot = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT + 1;
        int halfW = OPENING_HALF + 1;

        // 4.1 Бэкпанель фронтона.
        for (int dy = pediYBot; dy <= pediYTop; dy++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                int x = horizontal ? gx + dx : gx;
                int z = horizontal ? gz : gz + dx;
                Material mat = (dy == pediYTop || dy == pediYBot
                                || Math.abs(dx) == halfW)
                        ? Material.GILDED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_BRICKS;
                painter.place(x, dy, z, mat);
            }
        }

        // 4.2 Надпись ELIKIUM (или другая) — буквы из GOLD_BLOCK на
        //     центральной полосе фронтона. Используем встроенную карту букв.
        int letterRowY = (pediYBot + pediYTop) / 2 - 2; // нижний ряд букв
        drawSign(signTitle, gx, gz, horizontal, letterRowY);

        // 4.3 Подсветка надписи: SHROOMLIGHT за буквами.
        int frontLightDz = horizontal ? -1 : 0;
        int frontLightDx = horizontal ? 0 : -1;
        for (int row = 0; row < 5; row++) {
            for (int col = -halfW + 1; col <= halfW - 1; col++) {
                int x = horizontal ? gx + col : gx + frontLightDx;
                int z = horizontal ? gz + frontLightDz : gz + col;
                int y = letterRowY - 1 + row;
                painter.place(x, y, z, Material.SHROOMLIGHT);
            }
        }
    }

    /**
     * Очень упрощённый «пиксельный» шрифт: каждая буква 3 широкими×5 высокими
     * пикселями, плюс 1 пиксель промежутка. Размер надписи подобран так,
     * чтобы влезать в фронтон шириной 11 блоков (3 буквы), а более длинные
     * слова обрезаются. Для южных ворот вызов — «ELIKIUM», и слово
     * нарисуется в 2 ряда: «ELI / KIUM», но мы его упростим до главных
     * символов «ELI» и оставим красивый FloatingText «ЭЛИКИЙ» рядом.
     */
    private void drawSign(String text, int gx, int gz, boolean horizontal, int yBot) {
        if (text == null || text.isEmpty()) return;
        text = text.toUpperCase();

        int LETTER_W = 3, LETTER_H = 5, SPACING = 1;
        int totalW = text.length() * (LETTER_W + SPACING) - SPACING;
        // Если влезает прямо — рисуем по центру; если нет — обрезаем до
        // максимально подходящей ширины.
        int maxW = (OPENING_HALF * 2 - 1); // 9
        int startCol;
        String renderText;
        if (totalW <= maxW) {
            renderText = text;
            startCol = -totalW / 2;
        } else {
            int maxLetters = (maxW + SPACING) / (LETTER_W + SPACING);
            renderText = text.substring(0, maxLetters);
            int rw = renderText.length() * (LETTER_W + SPACING) - SPACING;
            startCol = -rw / 2;
        }

        int col = startCol;
        for (char c : renderText.toCharArray()) {
            int[][] glyph = PixelFont.glyph(c);
            if (glyph == null) {
                col += LETTER_W + SPACING;
                continue;
            }
            for (int row = 0; row < LETTER_H; row++) {
                for (int g = 0; g < LETTER_W; g++) {
                    int bit = glyph[row][g];
                    if (bit == 0) continue;
                    int x = horizontal ? gx + col + g : gx;
                    int z = horizontal ? gz : gz + col + g;
                    int y = yBot + (LETTER_H - 1 - row); // снизу-вверх
                    painter.place(x, y, z, Material.GOLD_BLOCK);
                }
            }
            col += LETTER_W + SPACING;
        }
    }

    // =========================================================================
    // ШАГ 5 — ДЕКОРАЦИИ (фонари, лансеты, цепи, золото)
    // =========================================================================

    private void buildDecoration(int gx, int gz, boolean horizontal) {
        // 5.1 SOUL_LANTERN на цепях у обеих сторон арки.
        for (int side : new int[]{-OPENING_HALF, OPENING_HALF}) {
            int x = horizontal ? gx + side : gx + 2;
            int z = horizontal ? gz + 2    : gz + side;
            // Цепь и фонарь.
            for (int cy = OPENING_HEIGHT - 4; cy <= OPENING_HEIGHT - 1; cy++) {
                painter.place(x, Y_BASE + cy, z, Material.CHAIN);
            }
            painter.place(x, Y_BASE + OPENING_HEIGHT - 5, z, Material.SOUL_LANTERN);

            int x2 = horizontal ? gx + side : gx - 2;
            int z2 = horizontal ? gz - 2    : gz + side;
            for (int cy = OPENING_HEIGHT - 4; cy <= OPENING_HEIGHT - 1; cy++) {
                painter.place(x2, Y_BASE + cy, z2, Material.CHAIN);
            }
            painter.place(x2, Y_BASE + OPENING_HEIGHT - 5, z2, Material.SOUL_LANTERN);
        }

        // 5.2 Аметистовые «свечи» по бокам арки (низ).
        for (int side : new int[]{-OPENING_HALF + 1, OPENING_HALF - 1}) {
            int x = horizontal ? gx + side : gx;
            int z = horizontal ? gz : gz + side;
            painter.place(x, Y_BASE + 1, z + (horizontal ? 2 : 0),
                    Material.AMETHYST_BLOCK);
            painter.place(x, Y_BASE + 2, z + (horizontal ? 2 : 0),
                    Material.AMETHYST_CLUSTER);
        }

        // 5.3 Декоративная стрельчатая арка над дугой — внешний обвод
        //     POLISHED_BLACKSTONE_BRICK_STAIRS.
        try {
            for (int dy = 0; dy <= ARCH_HEIGHT; dy++) {
                int y = Y_BASE + OPENING_HEIGHT + dy + 1;
                double t = (double) dy / ARCH_HEIGHT;
                int halfW = (int) Math.round((OPENING_HALF + 1) * Math.pow(1.0 - t, 1.6));
                if (halfW < 1) halfW = 1;
                placeStair(horizontal ? gx - halfW : gx, y, horizontal ? gz : gz - halfW,
                        horizontal ? "east" : "south");
                placeStair(horizontal ? gx + halfW : gx, y, horizontal ? gz : gz + halfW,
                        horizontal ? "west" : "north");
            }
        } catch (Throwable t) {
            // Если по какой-то причине не вышло поставить лестницу с
            // правильным facing — не страшно, основная арка уже стоит.
            plugin.getLogger().warning("ElikiumGateHouse: stairs render skipped: " + t);
        }
    }

    /** Поставить stairs-блок с указанным facing'ом ("north"/"south"/"east"/"west"). */
    private void placeStair(int x, int y, int z, String facing) {
        BlockData data = world.getBlockData(0, 0, 0); // плейсхолдер
        try {
            data = Material.POLISHED_BLACKSTONE_BRICK_STAIRS.createBlockData();
            if (data instanceof Stairs s) {
                s.setFacing(org.bukkit.block.BlockFace.valueOf(facing.toUpperCase()));
                s.setHalf(Bisected.Half.BOTTOM);
            }
        } catch (Throwable ignored) {
            // fallback на обычный POLISHED_BLACKSTONE_BRICKS
            painter.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
            return;
        }
        painter.placeData(x, y, z, data);
    }

    // =========================================================================
    // ШАГ 6 — FLOATINGTEXT
    // =========================================================================

    private void spawnFloatingTitle(int gx, int gz, boolean horizontal,
                                    String title, String subtitle) {
        // Парящая надпись прямо над аркой, чуть впереди стены (на 2 блока)
        // — чтобы её было видно при подходе.
        double y = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT - 1.0;
        double dx = 0, dz = 0;
        // Выдвигаем надпись наружу города (-z для южных, +z для северных и т.д.)
        if (horizontal) {
            dz = (gz > 0 ? +2.5 : -2.5); // SOUTH gz>0 → +z (наружу); NORTH gz<0 → -z (наружу)
        } else {
            dx = (gx > 0 ? +2.5 : -2.5);
        }
        try {
            FloatingText.createLocationTitle(plugin, world,
                    gx + 0.5 + dx, y, gz + 0.5 + dz, title, subtitle);
        } catch (Throwable t) {
            plugin.getLogger().warning("ElikiumGateHouse: FloatingText fail: " + t);
        }
    }

    // =========================================================================
    // ШАГ 7 — ЮЖНЫЙ ПОДХОД: статуи, фонари вдоль каньона
    // =========================================================================

    private void buildSouthernApproach(int gx, int gz) {
        // gx, gz = 0, 120 (SOUTH_GATE на полигоне после v29).
        // Игрок появляется на (0, 75, 130) — 10 блоков южнее ворот.
        // Декорируем путь z=121..200 в стиле «приближение к крепости»:
        // статуи стражей, фонари, ковровая дорожка, парящий «Глаз».

        // 7.1 Статуи стражей по обе стороны арки (на расстоянии 7 от центра,
        //     z=125 — за башнями, перед игроком).
        buildGuardianStatue(gx - 7, gz + 5);
        buildGuardianStatue(gx + 7, gz + 5);

        // 7.2 Фонарные столбы парами вдоль каньона каждые 6 блоков.
        for (int z = gz + 8; z <= gz + 80; z += 6) {
            buildLanternPost(gx - 9, z);
            buildLanternPost(gx + 9, z);
        }

        // 7.3 «Декоративная» центральная мостовая — 3 блока шириной
        //     с акцентами GILDED_BLACKSTONE каждые 4 блока (как на
        //     референсе у трона/алтаря).
        for (int dz = 1; dz <= 14; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Material floor = (dz % 4 == 0)
                        ? Material.GILDED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE;
                painter.place(gx + dx, Y_BASE, gz + dz, floor);
            }
        }

        // 7.4 Парящий «Всевидящий Глаз» над аркой (отсылка к собору).
        int orbX = gx, orbZ = gz;
        int orbY = Y_BASE + OPENING_HEIGHT + ARCH_HEIGHT + 14;
        painter.place(orbX, orbY, orbZ, Material.AMETHYST_BLOCK);
        painter.place(orbX, orbY + 1, orbZ, Material.LIGHTNING_ROD);
        // Корона из END_ROD-ов.
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                if (sx == 0 && sz == 0) continue;
                if (Math.abs(sx) + Math.abs(sz) != 1) continue;
                painter.place(orbX + sx, orbY, orbZ + sz, Material.END_ROD);
            }
        }

        // 7.5 Декоративные «огонь души» костры по бокам входа.
        painter.place(gx - 4, Y_BASE + 1, gz + 6, Material.SOUL_CAMPFIRE);
        painter.place(gx + 4, Y_BASE + 1, gz + 6, Material.SOUL_CAMPFIRE);

        // 7.6 Цветочные горшки с ALLIUM на пьедесталах вдоль ковра.
        for (int dz = 4; dz <= 12; dz += 4) {
            painter.place(gx - 3, Y_BASE + 1, gz + dz, Material.POTTED_ALLIUM);
            painter.place(gx + 3, Y_BASE + 1, gz + dz, Material.POTTED_ALLIUM);
        }
    }

    private void buildGuardianStatue(int x, int z) {
        // Статуя — простой пьедестал + торс + голова.
        // Пьедестал 3×3 из POLISHED_BLACKSTONE.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                painter.place(x + dx, Y_BASE, z + dz, Material.POLISHED_BLACKSTONE);
                painter.place(x + dx, Y_BASE + 1, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Торс — 1×1×3.
        painter.place(x, Y_BASE + 2, z, Material.DEEPSLATE_BRICKS);
        painter.place(x, Y_BASE + 3, z, Material.DEEPSLATE_BRICKS);
        painter.place(x, Y_BASE + 4, z, Material.CHISELED_DEEPSLATE);
        // Голова — череп пилгрина (отсылка к собору v16).
        painter.place(x, Y_BASE + 5, z, Material.SKELETON_SKULL);
        // Нимб END_ROD над головой.
        painter.place(x, Y_BASE + 6, z, Material.END_ROD);
        // Постамент с факелом.
        painter.place(x, Y_BASE + 1, z + (z > 113 ? -1 : 1), Material.SOUL_LANTERN);
    }

    private void buildLanternPost(int x, int z) {
        for (int dy = 0; dy < 4; dy++) {
            painter.place(x, Y_BASE + dy, z, Material.OAK_FENCE);
        }
        painter.place(x, Y_BASE + 4, z, Material.SOUL_LANTERN);
    }

    // =========================================================================
    // PIXEL FONT — простая 3×5-сетка для надписи ELIKIUM на воротах
    // =========================================================================

    private static final class PixelFont {
        private static int[][] glyph(char c) {
            return switch (c) {
                case 'A' -> a();
                case 'B' -> b();
                case 'C' -> c0();
                case 'D' -> d();
                case 'E' -> e();
                case 'F' -> f();
                case 'G' -> g();
                case 'H' -> h();
                case 'I' -> i();
                case 'J' -> j();
                case 'K' -> k();
                case 'L' -> l();
                case 'M' -> m();
                case 'N' -> n();
                case 'O' -> o();
                case 'P' -> p();
                case 'R' -> r();
                case 'S' -> s();
                case 'T' -> t();
                case 'U' -> u();
                case 'V' -> v();
                case 'W' -> w();
                case 'X' -> x();
                case 'Y' -> y();
                case 'Z' -> z();
                case ' ' -> blank();
                default -> blank();
            };
        }

        private static int[][] a() { return new int[][]{
                {0,1,0},{1,0,1},{1,1,1},{1,0,1},{1,0,1}}; }
        private static int[][] b() { return new int[][]{
                {1,1,0},{1,0,1},{1,1,0},{1,0,1},{1,1,0}}; }
        private static int[][] c0(){ return new int[][]{
                {1,1,1},{1,0,0},{1,0,0},{1,0,0},{1,1,1}}; }
        private static int[][] d() { return new int[][]{
                {1,1,0},{1,0,1},{1,0,1},{1,0,1},{1,1,0}}; }
        private static int[][] e() { return new int[][]{
                {1,1,1},{1,0,0},{1,1,0},{1,0,0},{1,1,1}}; }
        private static int[][] f() { return new int[][]{
                {1,1,1},{1,0,0},{1,1,0},{1,0,0},{1,0,0}}; }
        private static int[][] g() { return new int[][]{
                {1,1,1},{1,0,0},{1,0,1},{1,0,1},{1,1,1}}; }
        private static int[][] h() { return new int[][]{
                {1,0,1},{1,0,1},{1,1,1},{1,0,1},{1,0,1}}; }
        private static int[][] i() { return new int[][]{
                {1,1,1},{0,1,0},{0,1,0},{0,1,0},{1,1,1}}; }
        private static int[][] j() { return new int[][]{
                {0,0,1},{0,0,1},{0,0,1},{1,0,1},{1,1,1}}; }
        private static int[][] k() { return new int[][]{
                {1,0,1},{1,1,0},{1,0,0},{1,1,0},{1,0,1}}; }
        private static int[][] l() { return new int[][]{
                {1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,1,1}}; }
        private static int[][] m() { return new int[][]{
                {1,0,1},{1,1,1},{1,0,1},{1,0,1},{1,0,1}}; }
        private static int[][] n() { return new int[][]{
                {1,0,1},{1,1,1},{1,1,1},{1,0,1},{1,0,1}}; }
        private static int[][] o() { return new int[][]{
                {1,1,1},{1,0,1},{1,0,1},{1,0,1},{1,1,1}}; }
        private static int[][] p() { return new int[][]{
                {1,1,0},{1,0,1},{1,1,0},{1,0,0},{1,0,0}}; }
        private static int[][] r() { return new int[][]{
                {1,1,0},{1,0,1},{1,1,0},{1,0,1},{1,0,1}}; }
        private static int[][] s() { return new int[][]{
                {1,1,1},{1,0,0},{1,1,1},{0,0,1},{1,1,1}}; }
        private static int[][] t() { return new int[][]{
                {1,1,1},{0,1,0},{0,1,0},{0,1,0},{0,1,0}}; }
        private static int[][] u() { return new int[][]{
                {1,0,1},{1,0,1},{1,0,1},{1,0,1},{1,1,1}}; }
        private static int[][] v() { return new int[][]{
                {1,0,1},{1,0,1},{1,0,1},{1,0,1},{0,1,0}}; }
        private static int[][] w() { return new int[][]{
                {1,0,1},{1,0,1},{1,0,1},{1,1,1},{1,0,1}}; }
        private static int[][] x() { return new int[][]{
                {1,0,1},{1,0,1},{0,1,0},{1,0,1},{1,0,1}}; }
        private static int[][] y() { return new int[][]{
                {1,0,1},{1,0,1},{0,1,0},{0,1,0},{0,1,0}}; }
        private static int[][] z() { return new int[][]{
                {1,1,1},{0,0,1},{0,1,0},{1,0,0},{1,1,1}}; }
        private static int[][] blank() { return new int[][]{
                {0,0,0},{0,0,0},{0,0,0},{0,0,0},{0,0,0}}; }
    }
}
