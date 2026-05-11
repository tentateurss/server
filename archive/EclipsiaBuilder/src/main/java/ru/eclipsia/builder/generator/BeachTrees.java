package ru.eclipsia.builder.generator;

import org.bukkit.Material;

import java.util.Random;

/**
 * Набор «настоящих» алгоритмов деревьев для генератора Берега.
 * В отличие от старых столбов-«облезлых кошек» (1×1 ствол без листвы),
 * здесь используются полноценные модели:
 *
 * <ul>
 *   <li>{@link #bigDarkOak} — массивный мрачный дуб 3×3 со штакетной кроной;</li>
 *   <li>{@link #twistedDead} — кривое мёртвое дерево с настоящими ветвями;</li>
 *   <li>{@link #ancientPine} — высокая еловая башня;</li>
 *   <li>{@link #cherryBlossom} — небесная сакура для контрастных «оазисов»;</li>
 *   <li>{@link #willowTree} — плакучая ива с висящими лианами.</li>
 * </ul>
 *
 * Все методы рисуют дерево от уровня земли {@code gy+1} вверх.
 * Ствол всегда стоит на {@code gy+1}, корни уходят на {@code gy} и {@code gy-1}.
 * Используется {@link RegionPainter} для отложенной асинхронной заливки.
 */
public final class BeachTrees {
    private BeachTrees() {}

    // =====================================================================
    // BIG DARK OAK — мрачный исполин 3×3 со штакетной кроной
    // =====================================================================

    /**
     * Большой тёмный дуб с толстым стволом 3×3, ветвями и плотной кроной.
     * Высота 14..22 блоков, видимый ориентир в лесу.
     */
    public static void bigDarkOak(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 14 + rng.nextInt(9);   // 14..22
        Material log = Material.DARK_OAK_LOG;
        Material leaves = Material.DARK_OAK_LEAVES;

        // Корни (4 направления, по 2..3 блока)
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}}) {
            int len = 1 + rng.nextInt(2);
            for (int k = 0; k <= len; k++) {
                p.place(x + d[0] * k, gy - k / 2, z + d[1] * k, log);
            }
        }

        // Толстый ствол 3×3 (квадрат) со скруглёнными углами на верхнем куске.
        for (int dy = 0; dy < trunkH; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // углы убираем выше середины
                    if (dy > trunkH * 2 / 3 && Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                    p.place(x + dx, gy + 1 + dy, z + dz, log);
                }
            }
        }

        // 4..6 крупных боковых ветвей в верхней трети.
        int branchCount = 4 + rng.nextInt(3);
        for (int b = 0; b < branchCount; b++) {
            int branchY = gy + 1 + (trunkH * 2 / 3) + rng.nextInt(trunkH / 3);
            double angle = b * (2 * Math.PI / branchCount) + rng.nextDouble() * 0.5;
            int branchLen = 4 + rng.nextInt(3);
            growBranch(p, x, branchY, z, angle, branchLen, log);
            int tx = x + (int) Math.round(Math.cos(angle) * branchLen);
            int tz = z + (int) Math.round(Math.sin(angle) * branchLen);
            leafBlob(p, rng, tx, branchY + 1, tz, 3 + rng.nextInt(2), leaves);
        }

        // Главная крона — большой шар на вершине ствола.
        int top = gy + 1 + trunkH;
        leafBlob(p, rng, x, top, z, 5, leaves);
        leafBlob(p, rng, x, top - 2, z, 6, leaves);
        leafBlob(p, rng, x, top + 1, z, 4, leaves);

        // 1-2 «ягодных» куска для деталей
        if (rng.nextBoolean()) {
            p.place(x + (rng.nextBoolean() ? 3 : -3), top - 1, z, leaves);
        }
    }

    // =====================================================================
    // TWISTED DEAD — кривое мёртвое дерево с настоящими ветвями
    // =====================================================================

    /**
     * Кривое мёртвое дерево с recursive-ветвлением. Без листвы, но
     * с разветвлёнными «когтистыми» сучьями + редкая паутина.
     */
    public static void twistedDead(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 9 + rng.nextInt(6); // 9..14
        Material log = Material.DARK_OAK_LOG;

        // Толстый кривой ствол (1-2 блока шириной).
        int curX = x, curZ = z;
        for (int dy = 0; dy < trunkH; dy++) {
            p.place(curX, gy + 1 + dy, curZ, log);
            // широкий ствол снизу (2x1)
            if (dy < trunkH / 2 && rng.nextInt(3) == 0) {
                p.place(curX + 1, gy + 1 + dy, curZ, log);
            }
            if (dy == trunkH / 4) curX += rng.nextInt(3) - 1;
            if (dy == trunkH / 2) curZ += rng.nextInt(3) - 1;
            if (dy == 3 * trunkH / 4) curX += rng.nextInt(3) - 1;
        }

        // 3..5 ветвей с recursive-расщеплением
        int branchCount = 3 + rng.nextInt(3);
        for (int b = 0; b < branchCount; b++) {
            double angle = b * (2 * Math.PI / branchCount) + rng.nextDouble() * 0.7;
            int by = gy + 1 + trunkH / 2 + rng.nextInt(trunkH / 2);
            growGnarledBranch(p, rng, curX, by, curZ, angle, 4 + rng.nextInt(3), 0, log);
        }

        // Несколько ветвей у самой вершины — «когти к небу»
        for (int i = 0; i < 3; i++) {
            int dx = rng.nextInt(3) - 1, dz = rng.nextInt(3) - 1;
            p.place(curX + dx, gy + trunkH + 1, curZ + dz, log);
            if (rng.nextBoolean()) {
                p.place(curX + dx * 2, gy + trunkH + 2, curZ + dz * 2, log);
            }
        }

        // Паутина в развилках (1-3 шт.)
        for (int i = 0; i < 2 + rng.nextInt(2); i++) {
            int dx = rng.nextInt(5) - 2, dz = rng.nextInt(5) - 2;
            p.place(curX + dx, gy + 2 + rng.nextInt(trunkH - 2), curZ + dz, Material.COBWEB);
        }
    }

    private static void growGnarledBranch(RegionPainter p, Random rng,
                                          int x, int y, int z,
                                          double angle, int len, int depth, Material log) {
        if (depth > 2 || len <= 0) return;
        int curX = x, curY = y, curZ = z;
        double a = angle;
        for (int k = 0; k < len; k++) {
            curX += (int) Math.round(Math.cos(a));
            curZ += (int) Math.round(Math.sin(a));
            // ветка слегка задирается вверх
            if (k % 2 == 0) curY += 1;
            p.place(curX, curY, curZ, log);
            a += (rng.nextDouble() - 0.5) * 0.6;
        }
        if (depth < 2 && rng.nextDouble() < 0.7) {
            growGnarledBranch(p, rng, curX, curY, curZ, angle + 0.6, len - 2, depth + 1, log);
        }
        if (depth < 2 && rng.nextDouble() < 0.7) {
            growGnarledBranch(p, rng, curX, curY, curZ, angle - 0.6, len - 2, depth + 1, log);
        }
    }

    // =====================================================================
    // ANCIENT PINE — высокая ель / сосна
    // =====================================================================

    /** Мрачная высокая ель с конической кроной. */
    public static void ancientPine(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 14 + rng.nextInt(8); // 14..21
        Material log = Material.SPRUCE_LOG;
        Material leaves = Material.SPRUCE_LEAVES;

        // Толстый ствол 1×1 (или 2×1 у основания)
        for (int dy = 0; dy < trunkH; dy++) {
            p.place(x, gy + 1 + dy, z, log);
            if (dy < 3) {
                p.place(x + 1, gy + 1 + dy, z, log);
                p.place(x, gy + 1 + dy, z + 1, log);
            }
        }
        // Корни
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            p.place(x + d[0] * 2, gy, z + d[1] * 2, log);
        }

        // Коническая крона: 3..4 яруса, каждый шире снизу.
        int layers = 4 + rng.nextInt(2);
        for (int layer = 0; layer < layers; layer++) {
            int layerY = gy + 1 + trunkH - 1 - layer * 3;
            int radius = 1 + layer * 2;
            // диск листвы
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int dist2 = dx * dx + dz * dz;
                    if (dist2 > radius * radius) continue;
                    if (dist2 == radius * radius && rng.nextInt(3) == 0) continue;
                    p.place(x + dx, layerY, z + dz, leaves);
                    p.place(x + dx, layerY - 1, z + dz, leaves);
                }
            }
        }
        // Шапка
        p.place(x, gy + 1 + trunkH, z, leaves);
        p.place(x, gy + 2 + trunkH, z, leaves);
    }

    // =====================================================================
    // CHERRY BLOSSOM — сакура
    // =====================================================================

    /**
     * Сакура «зонтиком» по референсам — узкий ствол с раскидистой,
     * широкой и плоской розовой кроной (как зонтик-гриб).
     */
    public static void cherryBlossom(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 9 + rng.nextInt(4); // 9..12
        Material log = Material.CHERRY_LOG;
        Material leaves = Material.CHERRY_LEAVES;

        // Ствол с лёгким изгибом
        int curX = x, curZ = z;
        for (int dy = 0; dy < trunkH; dy++) {
            p.place(curX, gy + 1 + dy, curZ, log);
            if (dy == trunkH / 3 || dy == 2 * trunkH / 3) {
                curX += rng.nextInt(3) - 1;
                curZ += rng.nextInt(3) - 1;
            }
        }
        int top = gy + 1 + trunkH;
        // 6..8 раскидистых ветвей образуют «зонтик»
        int branchCount = 6 + rng.nextInt(3);
        for (int b = 0; b < branchCount; b++) {
            double angle = b * (2 * Math.PI / branchCount) + rng.nextDouble() * 0.4;
            int branchLen = 4 + rng.nextInt(3);
            int bx = curX, bz = curZ;
            for (int k = 1; k <= branchLen; k++) {
                bx = curX + (int) Math.round(Math.cos(angle) * k);
                bz = curZ + (int) Math.round(Math.sin(angle) * k);
                int by = top + k / 4 - 1; // ветви слегка под наклоном вверх
                p.place(bx, by, bz, log);
            }
            // На конце ветви — крупный розовый ком
            leafBlob(p, rng, bx, top + 1, bz, 3 + rng.nextInt(2), leaves);
            // Свисающая нить розовых лепестков
            for (int k = 1; k <= 2 + rng.nextInt(2); k++) {
                p.place(bx, top - k, bz, leaves);
            }
        }
        // Большая центральная крона — почти плоский диск
        int crownR = 5;
        for (int dx = -crownR; dx <= crownR; dx++) {
            for (int dz = -crownR; dz <= crownR; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > crownR * crownR) continue;
                if (d2 > (crownR - 1) * (crownR - 1) && rng.nextInt(3) == 0) continue;
                p.place(curX + dx, top + 1, curZ + dz, leaves);
                if (d2 <= (crownR - 1) * (crownR - 1)) {
                    p.place(curX + dx, top, curZ + dz, leaves);
                }
                if (d2 <= 9) {
                    p.place(curX + dx, top + 2, curZ + dz, leaves);
                }
            }
        }
        // Лепестки на земле
        for (int i = 0; i < 16; i++) {
            int dx = rng.nextInt(11) - 5, dz = rng.nextInt(11) - 5;
            if (dx * dx + dz * dz <= 25) {
                p.place(x + dx, gy + 1, z + dz, Material.PINK_PETALS);
            }
        }
    }

    // =====================================================================
    // WILLOW — плакучая ива с висящими лианами
    // =====================================================================

    /**
     * Большая плакучая ива по референсам — мощный 2×2 ствол, огромная
     * куполообразная крона и густые свисающие нити-лианы со всех сторон.
     */
    public static void willowTree(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 10 + rng.nextInt(4);   // 10..13
        Material log = Material.OAK_LOG;
        Material leaves = Material.OAK_LEAVES;

        // Корни (4 направления)
        for (int[] d : new int[][]{{2,0},{-2,0},{0,2},{0,-2},{1,1},{-1,-1},{1,-1},{-1,1}}) {
            p.place(x + d[0], gy, z + d[1], log);
            if (rng.nextBoolean()) {
                p.place(x + d[0], gy - 1, z + d[1], log);
            }
        }
        // 2×2 ствол с расширением у основания (до 3×3 на 2 нижних блока)
        for (int dy = 0; dy < trunkH; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    p.place(x + dx, gy + 1 + dy, z + dz, log);
                }
            }
            if (dy <= 1) {
                p.place(x - 1, gy + 1 + dy, z, log);
                p.place(x + 2, gy + 1 + dy, z + 1, log);
                p.place(x, gy + 1 + dy, z - 1, log);
                p.place(x + 1, gy + 1 + dy, z + 2, log);
            }
        }

        int top = gy + trunkH + 1;
        // Купольная крона радиусом 8: 3 слоя
        int crownR = 8;
        for (int dy = -1; dy <= 2; dy++) {
            int rl = crownR - Math.abs(dy);
            for (int dx = -rl; dx <= rl; dx++) {
                for (int dz = -rl; dz <= rl; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rl * rl) continue;
                    if (d2 > (rl - 1) * (rl - 1) && rng.nextInt(4) == 0) continue;
                    p.place(x + dx, top + dy, z + dz, leaves);
                }
            }
        }
        // ОЧЕНЬ много свисающих нитей: 32 шт по 5-10 блоков длиной
        int strands = 32 + rng.nextInt(8);
        for (int i = 0; i < strands; i++) {
            double a = i * (2 * Math.PI / strands) + rng.nextDouble() * 0.1;
            // ставим нити на разных радиусах для объёма
            double rr = 4 + rng.nextDouble() * 4;
            int sx = x + (int) Math.round(Math.cos(a) * rr);
            int sz = z + (int) Math.round(Math.sin(a) * rr);
            int len = 4 + rng.nextInt(7);
            for (int k = 0; k < len; k++) {
                p.place(sx, top - 1 - k, sz, leaves);
            }
        }
        // Несколько боковых ветвей
        for (int b = 0; b < 4; b++) {
            double a = b * (Math.PI / 2) + 0.3;
            int branchLen = 3 + rng.nextInt(2);
            for (int k = 1; k <= branchLen; k++) {
                int bx = x + (int) Math.round(Math.cos(a) * k);
                int bz = z + (int) Math.round(Math.sin(a) * k);
                p.place(bx, top - 2, bz, log);
            }
        }
    }

    // =====================================================================
    // GIANT PINE — высокая ель для пояса по периметру
    // =====================================================================

    /**
     * Высокая раскидистая ель 18-28 блоков. Используется для пояса
     * по периметру локации (закрывает горизонт).
     */
    public static void giantPine(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 18 + rng.nextInt(11); // 18..28
        Material log = Material.SPRUCE_LOG;
        Material leaves = Material.SPRUCE_LEAVES;

        // Корни
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            p.place(x + d[0], gy, z + d[1], log);
        }
        // Ствол 1×1 (с утолщением 2×2 у основания)
        for (int dy = 0; dy < trunkH; dy++) {
            p.place(x, gy + 1 + dy, z, log);
            if (dy < 3) {
                p.place(x + 1, gy + 1 + dy, z, log);
                p.place(x, gy + 1 + dy, z + 1, log);
            }
        }
        // Конус крон (в стиле большой ели): чем выше, тем уже
        int crownStart = trunkH / 4;
        int crownH = trunkH - crownStart;
        for (int level = 0; level < crownH; level++) {
            int y = gy + 1 + crownStart + level;
            // радиус уменьшается ступеньками
            int r = Math.max(0, (crownH - level) / 2);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > r * r) continue;
                    // прореженные края
                    if (d2 == r * r && rng.nextInt(2) == 0) continue;
                    p.place(x + dx, y, z + dz, leaves);
                }
            }
        }
        // Шпиль наверху
        p.place(x, gy + 1 + trunkH, z, leaves);
        p.place(x, gy + 2 + trunkH, z, leaves);
    }

    // =====================================================================
    // SACRED OAK — огромный многоствольный дуб (по референсам)
    // =====================================================================

    /**
     * «Священный дуб» — огромное многоствольное дерево с куполовидной
     * кроной, как на референсах. Размером с небольшое здание.
     */
    public static void sacredOak(RegionPainter p, Random rng, int x, int z, int gy) {
        int trunkH = 9 + rng.nextInt(4);  // 9..12
        Material log = Material.OAK_LOG;
        Material leaves = Material.OAK_LEAVES;

        // Расходящиеся корни-укоренения
        for (int[] d : new int[][]{{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2},{2,-2},{-2,2},{3,0},{0,3},{-3,0},{0,-3}}) {
            int len = 1 + rng.nextInt(2);
            for (int k = 0; k <= len; k++) {
                p.place(x + d[0] * (k == 0 ? 1 : 1), gy - k / 2, z + d[1] * (k == 0 ? 1 : 1), log);
            }
            p.place(x + d[0], gy, z + d[1], log);
        }

        // Толстый ствол 5×5 в центре, сужается к верху (3×3 потом 1×1)
        for (int dy = 0; dy < trunkH; dy++) {
            int rad = (dy < trunkH / 3) ? 2 : (dy < 2 * trunkH / 3) ? 1 : 0;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // углы
                    p.place(x + dx, gy + 1 + dy, z + dz, log);
                }
            }
        }

        int top = gy + trunkH + 1;
        // Большое разветвление наверху: 5-7 толстых ветвей 6-8 блоков в стороны и вверх
        int branchCount = 5 + rng.nextInt(3);
        for (int b = 0; b < branchCount; b++) {
            double angle = b * (2 * Math.PI / branchCount) + rng.nextDouble() * 0.3;
            int branchLen = 6 + rng.nextInt(3);
            int bx = x, bz = z, by = top;
            for (int k = 1; k <= branchLen; k++) {
                bx = x + (int) Math.round(Math.cos(angle) * k);
                bz = z + (int) Math.round(Math.sin(angle) * k);
                by = top + k / 2;
                p.place(bx, by, bz, log);
                // утолщённая ветвь у основания
                if (k <= 2) {
                    p.place(bx, by + 1, bz, log);
                }
            }
            // Шар листвы на конце
            leafBlob(p, rng, bx, by + 1, bz, 4 + rng.nextInt(2), leaves);
            leafBlob(p, rng, bx, by + 2, bz, 3, leaves);
        }
        // Огромная центральная купольная крона
        int crownR = 7;
        for (int dy = 0; dy <= 4; dy++) {
            int rl = crownR - dy;
            for (int dx = -rl; dx <= rl; dx++) {
                for (int dz = -rl; dz <= rl; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > rl * rl) continue;
                    if (d2 > (rl - 1) * (rl - 1) && rng.nextInt(4) == 0) continue;
                    p.place(x + dx, top + 2 + dy, z + dz, leaves);
                }
            }
        }
        // Несколько свисающих лиан
        for (int i = 0; i < 6; i++) {
            double a = rng.nextDouble() * 2 * Math.PI;
            int sx = x + (int) Math.round(Math.cos(a) * 5);
            int sz = z + (int) Math.round(Math.sin(a) * 5);
            for (int k = 0; k < 3 + rng.nextInt(3); k++) {
                p.place(sx, top + 1 - k, sz, Material.VINE);
            }
        }
    }

    // =====================================================================
    // Маленькие живые деревья (для дороги к городу)
    // =====================================================================

    /** Берёза средней высоты с округлой кроной. */
    public static void roadsideBirch(RegionPainter p, Random rng, int x, int z, int gy) {
        int h = 5 + rng.nextInt(3);
        for (int dy = 0; dy < h; dy++) {
            p.place(x, gy + 1 + dy, z, Material.BIRCH_LOG);
        }
        leafBlob(p, rng, x, gy + h, z, 2 + rng.nextInt(2), Material.BIRCH_LEAVES);
        leafBlob(p, rng, x, gy + h + 1, z, 2, Material.BIRCH_LEAVES);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void growBranch(RegionPainter p, int x, int y, int z,
                                   double angle, int len, Material log) {
        for (int k = 1; k <= len; k++) {
            int bx = x + (int) Math.round(Math.cos(angle) * k);
            int bz = z + (int) Math.round(Math.sin(angle) * k);
            int by = y + k / 3;  // ветви слегка идут вверх
            p.place(bx, by, bz, log);
        }
    }

    /** Шарообразная масса листвы вокруг (cx,cy,cz) радиусом r с шумом. */
    private static void leafBlob(RegionPainter p, Random rng,
                                 int cx, int cy, int cz, int r, Material leaves) {
        int r2 = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) continue;
                    // на самом краю прореживаем
                    if (d2 > (r - 1) * (r - 1) && rng.nextInt(3) == 0) continue;
                    p.place(cx + dx, cy + dy, cz + dz, leaves);
                }
            }
        }
    }
}
