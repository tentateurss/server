package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Парящий «Глаз Эликия» + аура вокруг собора — всё на ЧАСТИЦАХ.
 *
 * <p>PR 3.9 (v15): Глаз СТАЛ ЖИРНЕЕ:
 * <ul>
 *   <li>LID_STEPS 96→144 и SCLERA_STEPS 72→108 — больше частиц на оборот;</li>
 *   <li>Контур LID — 5 параллельных проходов (вместо 3) с spread ±0.20;</li>
 *   <li>Склера — 3 параллельных прохода (вместо 1) с spread ±0.15;</li>
 *   <li>8 радиальных лучей (вместо 4): N/S/E/W + 4 диагонали NE/NW/SE/SW.</li>
 * </ul>
 *
 * <p>PR 3.9 (v15): АУРА ВОКРУГ СОБОРА — новый блок {@code spawnAura()}
 * рассыпает PORTAL+SOUL_FIRE_FLAME+ENCHANTMENT_TABLE в радиусе ~50
 * блоков от центра собора (CATHEDRAL_X, CATHEDRAL_Z), на высотах
 * y=70..150 — собор выглядит «в фиолетовом тумане» из города.
 *
 * <p>PR 3.8 (v14): все 18 вызовов {@code spawnParticle} переведены на
 * overload {@code (..., Object data, boolean force)} с {@code data=null,
 * force=true} — без force клиент видит частицы только в радиусе ~32
 * блоков, и Глаз был не виден из дальних углов города (±150). Теперь
 * пакеты идут всем игрокам в view-distance чанков. Миндаль увеличен
 * ×1.6 (LID_A 7.5→12, LID_B 3.0→4.8 и т.д.) — Глаз стал заметным с
 * расстояния 100-150 блоков.
 *
 * <p>PR 3.7: переработан под референс — большой «миндаль» с
 * <i>вертикальным</i> slit-зрачком (как у дракона), вращающимся
 * руническим кольцом и парами вертикальных световых лучей сверху и снизу,
 * плюс diamond-glow в 4 направлениях (СВЗВ).
 *
 * <p>Слои (рисуются в порядке Z-buffer от заднего плана к переднему):
 * <ul>
 *   <li><b>Внешняя аура</b> — {@link Particle#PORTAL} (×60, радиус ≈10 блоков)
 *       тёмно-фиолетовый туман.</li>
 *   <li><b>Diamond-glow</b> — 4 «звёздных» луча DRAGON_BREATH в стороны
 *       (С/Ю/В/З), длина 9 блоков с убывающей плотностью.</li>
 *   <li><b>Руническое кольцо</b> (вращается) —
 *       {@link Particle#ENCHANTMENT_TABLE}, эллипс 9×5, фаза вращения
 *       8 сек/оборот.</li>
 *   <li><b>Бровь</b> (горизонтальная дуга над веком) — DRAGON_BREATH ×16.</li>
 *   <li><b>Внешнее веко</b> (миндаль) — DRAGON_BREATH, эллипс 7.5×3.0
 *       (вертикальная плоскость XY). Контур рисуется тройным проходом для
 *       толщины.</li>
 *   <li><b>Склера</b> (внутренний контур, белый) — {@link Particle#FIREWORKS_SPARK}
 *       эллипс 6.0×2.4, идёт по внутреннему краю миндаля.</li>
 *   <li><b>Внешняя радужка</b> — {@link Particle#SPELL_WITCH} круг r=2.2,
 *       медленное вращение (период 12 сек).</li>
 *   <li><b>Внутренняя радужка</b> — SPELL_WITCH круг r=1.3,
 *       обратное вращение (период 7 сек, быстрее, противоположное направление).</li>
 *   <li><b>Slit-зрачок</b> (вертикальная щель) — {@link Particle#SQUID_INK}
 *       вертикальная линия высотой 2.4 блока, ширина 0.3.</li>
 *   <li><b>Hightlight зрачка</b> — END_ROD ×3 в верхней половине зрачка.</li>
 *   <li><b>Слёзы</b> (две вертикальные дорожки END_ROD) — стекают
 *       вниз от внешних углов миндаля на 6 блоков, медленно перемещаются.</li>
 *   <li><b>Световые лучи</b> — END_ROD вертикальные колонки сверху и снизу
 *       Глаза, по 8 блоков с каждой стороны (2 верхних + 2 нижних).</li>
 *   <li><b>Падающие руны</b> — ENCHANTMENT_TABLE ×30 стекают сверху
 *       на 6 блоков радиусом 3.</li>
 * </ul>
 *
 * <p>Тик: 3 тика (≈6.6 раз/сек).
 */
public final class SpireParticles {

    // Геометрия миндаля. PR 3.8 (v14): ×1.6 от исходных значений 3.7,
    // чтобы Глаз был отчётливо виден с дистанции 100-150 блоков.
    private static final double LID_A   = 12.0; // полуось эллипса по X (3.7: 7.5)
    private static final double LID_B   = 4.8;  // полуось эллипса по Y (3.7: 3.0)
    private static final int LID_STEPS  = 144;  // PR 3.9: 96→144 (жирнее контур)

    // Склера (внутренний контур).
    private static final double SCLERA_A = 9.6;  // 3.7: 6.0
    private static final double SCLERA_B = 3.84; // 3.7: 2.4
    private static final int SCLERA_STEPS = 108; // PR 3.9: 72→108

    // Радужка.
    private static final double IRIS_OUT_R = 3.5; // 3.7: 2.2
    private static final double IRIS_IN_R  = 2.1; // 3.7: 1.3
    private static final int IRIS_STEPS    = 48;

    // Бровь.
    private static final double BROW_A = 8.8;   // 3.7: 5.5
    private static final double BROW_B = 0.96;  // 3.7: 0.6
    private static final int BROW_STEPS = 28;

    // Руническое кольцо (вращается).
    private static final double RUNE_A = 14.4; // 3.7: 9.0
    private static final double RUNE_B = 8.0;  // 3.7: 5.0
    private static final int RUNE_STEPS = 54;

    private final Plugin plugin;
    private BukkitTask task;

    public SpireParticles(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Стартует периодический выпуск частиц (если ещё не запущен). */
    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld("world");
                if (world == null) return;
                if (world.getPlayers().isEmpty()) return; // экономим, если никого нет

                double cx = WorldGenerator.spireCenterX;
                double cy = WorldGenerator.spireCenterY;
                double cz = WorldGenerator.spireCenterZ;
                if (Double.isNaN(cx) || Double.isNaN(cy) || Double.isNaN(cz)) return;

                Location center = new Location(world, cx, cy, cz);
                long ms = System.currentTimeMillis();

                // ===== 1. ВНЕШНЯЯ АУРА (PORTAL туман) =====
                world.spawnParticle(Particle.PORTAL, center, 100, 12.0, 5.0, 3.5, 0.05,
                        null, true);

                // ===== 2. DIAMOND-GLOW (8 лучей наружу) =====
                // PR 3.9: 4→8 лучей — добавлены 4 диагонали (NE/NW/SE/SW),
                // плюс SOUL_FIRE_FLAME для разнообразия цветов (белый-синий пламень).
                double[][] rayDirs = {
                        { +1.0, 0.0 }, { -1.0, 0.0 }, { 0.0, +1.0 }, { 0.0, -1.0 },
                        { +0.707, +0.707 }, { -0.707, +0.707 },
                        { +0.707, -0.707 }, { -0.707, -0.707 },
                };
                for (int len = 1; len <= 14; len++) {
                    double f = 1.0 - (len - 1) / 14.0; // плотность затухает
                    int parts = (int) Math.round(3 * f);
                    if (parts < 1) parts = 1;
                    for (int d = 0; d < rayDirs.length; d++) {
                        double[] dir = rayDirs[d];
                        double rx = cx + dir[0] * (LID_A + len);
                        double ry = cy + dir[1] * (LID_B + len);
                        // Кардинальные — DRAGON_BREATH (фиолетовые), диагонали —
                        // SOUL_FIRE_FLAME (бело-синие языки пламени) — эффектнее.
                        Particle pType = (d < 4) ? Particle.DRAGON_BREATH
                                : Particle.SOUL_FIRE_FLAME;
                        world.spawnParticle(pType,
                                new Location(world, rx, ry, cz), parts,
                                0.05, 0.05, 0.05, 0.0, null, true);
                    }
                }

                // ===== 3. РУНИЧЕСКОЕ КОЛЬЦО (вращается) =====
                double runePhase = (ms % 8000L) / 8000.0 * Math.PI * 2.0;
                for (int i = 0; i < RUNE_STEPS; i++) {
                    double t = (double) i / RUNE_STEPS * Math.PI * 2.0 + runePhase;
                    double dx = RUNE_A * Math.cos(t);
                    double dy = RUNE_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.ENCHANTMENT_TABLE, p, 1, 0.0, 0.0, 0.0, 0.0,
                            null, true);
                }

                // ===== 4. БРОВЬ (DRAGON_BREATH дуга над веком) =====
                for (int i = 0; i < BROW_STEPS; i++) {
                    double t = (double) i / (BROW_STEPS - 1) * Math.PI; // полуокружность вверх
                    double dx = BROW_A * Math.cos(t);
                    double dy = LID_B + 0.9 + BROW_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0,
                            null, true);
                }

                // ===== 5. ВЕКО — внешний миндаль (DRAGON_BREATH) =====
                // PR 3.9: 5 параллельных проходов (вместо 3) с spread ±0.10 × 0.20 —
                // контур жирный, виден издалека.
                for (int pass = 0; pass < 5; pass++) {
                    double offset = (pass - 2) * 0.20; // -0.40, -0.20, 0, +0.20, +0.40
                    double aMul = 1.0 + offset / LID_A;
                    double bMul = 1.0 + offset / LID_B;
                    for (int i = 0; i < LID_STEPS; i++) {
                        double t = (double) i / LID_STEPS * Math.PI * 2.0;
                        double dx = LID_A * aMul * Math.cos(t);
                        double dy = LID_B * bMul * Math.sin(t);
                        Location p = new Location(world, cx + dx, cy + dy, cz);
                        world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0,
                                null, true);
                    }
                }

                // ===== 6. СКЛЕРА (FIREWORKS_SPARK белый внутренний контур) =====
                // PR 3.9: 3 параллельных прохода (вместо 1) с spread ±0.15.
                for (int pass = 0; pass < 3; pass++) {
                    double offset = (pass - 1) * 0.15;
                    double aMul = 1.0 + offset / SCLERA_A;
                    double bMul = 1.0 + offset / SCLERA_B;
                    for (int i = 0; i < SCLERA_STEPS; i++) {
                        double t = (double) i / SCLERA_STEPS * Math.PI * 2.0;
                        double dx = SCLERA_A * aMul * Math.cos(t);
                        double dy = SCLERA_B * bMul * Math.sin(t);
                        Location p = new Location(world, cx + dx, cy + dy, cz);
                        world.spawnParticle(Particle.FIREWORKS_SPARK, p, 1, 0.0, 0.0, 0.0, 0.0,
                                null, true);
                    }
                }

                // ===== 7. ВНЕШНЯЯ РАДУЖКА (SPELL_WITCH, по часовой) =====
                double irisOutPhase = (ms % 12000L) / 12000.0 * Math.PI * 2.0;
                for (int i = 0; i < IRIS_STEPS; i++) {
                    double t = (double) i / IRIS_STEPS * Math.PI * 2.0 + irisOutPhase;
                    double dx = IRIS_OUT_R * Math.cos(t);
                    double dy = IRIS_OUT_R * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.SPELL_WITCH, p, 1, 0.0, 0.0, 0.0, 0.0,
                            null, true);
                }

                // ===== 8. ВНУТРЕННЯЯ РАДУЖКА (SPELL_WITCH, против ч.с.) =====
                double irisInPhase = -(ms % 7000L) / 7000.0 * Math.PI * 2.0;
                for (int i = 0; i < IRIS_STEPS; i++) {
                    double t = (double) i / IRIS_STEPS * Math.PI * 2.0 + irisInPhase;
                    double dx = IRIS_IN_R * Math.cos(t);
                    double dy = IRIS_IN_R * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.SPELL_WITCH, p, 1, 0.0, 0.0, 0.0, 0.0,
                            null, true);
                }

                // ===== 9. SLIT-ЗРАЧОК (вертикальная щель SQUID_INK) =====
                // PR 3.8: щель растянута до ±2.0 (раньше ±1.2) под увеличенный миндаль.
                for (double dy = -2.0; dy <= 2.0; dy += 0.18) {
                    Location p = new Location(world, cx, cy + dy, cz);
                    world.spawnParticle(Particle.SQUID_INK, p, 1, 0.05, 0.0, 0.05, 0.0,
                            null, true);
                }

                // ===== 10. HIGHLIGHT ЗРАЧКА (END_ROD ×4 в верхней половине) =====
                for (int i = 0; i < 4; i++) {
                    Location p = new Location(world, cx + 0.2, cy + 0.6 + i * 0.2, cz);
                    world.spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0,
                            null, true);
                }

                // ===== 11. СЛЁЗЫ (END_ROD от внешних углов вниз) =====
                // Анимация: tearOffset движется от 0 до 9 за 4 сек.
                double tearOffset = (ms % 4000L) / 4000.0 * 9.0;
                for (int side : new int[] { -1, +1 }) {
                    for (int i = 0; i < 8; i++) {
                        double dy = -tearOffset - i * 0.5;
                        if (dy < -12.0) break;
                        Location p = new Location(world, cx + side * (LID_A * 0.7), cy + dy, cz);
                        world.spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0,
                                null, true);
                    }
                }

                // ===== 12. СВЕТОВЫЕ ЛУЧИ ВВЕРХ И ВНИЗ =====
                // 2 верхних столба END_ROD (на x=±LID_A*0.4) от cy+LID_B+1 до +14.
                // 2 нижних — симметрично от cy-LID_B-1 до -14. PR 3.8: длина 8→14.
                for (int side : new int[] { -1, +1 }) {
                    for (int up = 1; up <= 14; up++) {
                        Location pUp = new Location(world,
                                cx + side * LID_A * 0.4, cy + LID_B + up, cz);
                        Location pDown = new Location(world,
                                cx + side * LID_A * 0.4, cy - LID_B - up, cz);
                        world.spawnParticle(Particle.END_ROD, pUp, 1, 0.0, 0.0, 0.0, 0.0,
                                null, true);
                        world.spawnParticle(Particle.END_ROD, pDown, 1, 0.0, 0.0, 0.0, 0.0,
                                null, true);
                    }
                }

                // ===== 13. ПАДАЮЩИЕ РУНЫ (ENCHANTMENT_TABLE сверху) =====
                Location above = new Location(world, cx, cy + 11.0, cz);
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, above, 50, 5.0, 0.8, 2.5, 0.5,
                        null, true);

                // ===== 14. ВСАСЫВАЮЩИЕ ИСКРЫ =====
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 32, 6.0, 5.0, 3.0, 0.05,
                        null, true);

                // ===== 15. АУРА ВОКРУГ СОБОРА (PR 3.10: пресет 5BCD) =====
                // - Магия: PORTAL + WITCH с шпилей наверх
                // - Святой огонь: 4 SOUL_FIRE_FLAME-столба на углах
                // - Текущая пассивная (PORTAL+SOUL+ENCHANT кольцо вокруг собора)
                spawnAura(world, ms);

                // ===== 16. ЧАСТИЦЫ ВНУТРИ СОБОРА (PR 3.10: пресет 4E - все 4) =====
                spawnInteriorParticles(world, ms);
            }
        }.runTaskTimer(plugin, 60L, 3L); // первый запуск через 3 сек, потом каждые 3 тика
    }

    /** Остановить эффект (вызывается на onDisable). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * PR 3.9 (v15): пассивная фиолетовая аура частиц вокруг ВСЕГО собора.
     *
     * <p>Рассыпает 3 типа частиц (PORTAL, SOUL_FIRE_FLAME, ENCHANTMENT_TABLE)
     * детерминистически по времени в кольцевой зоне вокруг
     * {@code (CATHEDRAL_X, CATHEDRAL_Z)} на высотах y=70..150. Все частицы
     * с {@code force=true}, поэтому видны игрокам в любой точке города
     * независимо от particle distance клиента.
     *
     * <p>Алгоритм: за каждый тик берём ~24 случайных точек в кольце
     * радиуса 18..50 от центра собора. Псевдо-«случайность» через
     * детерминированный hash от {@code ms}, чтобы не плодить аллокаций.
     */
    private static void spawnAura(World world, long ms) {
        final double cathX = WorldGenerator.CATHEDRAL_X + 0.5;
        final double cathZ = WorldGenerator.CATHEDRAL_Z + 0.5;
        final int    samples = 24;
        final double rMin = 18.0;
        final double rMax = 50.0;
        final double yMin = 72.0;
        final double yMax = 150.0;

        // Псевдо-случайный seed от ms (без аллокаций Random на каждый тик).
        long seed = ms * 6364136223846793005L + 1442695040888963407L;

        for (int i = 0; i < samples; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double a = ((seed >>> 16) & 0xFFFF) / 65535.0; // [0..1)
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double b = ((seed >>> 16) & 0xFFFF) / 65535.0;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double c = ((seed >>> 16) & 0xFFFF) / 65535.0;

            // Угол в кольце.
            double theta = a * Math.PI * 2.0;
            // Радиус с распределением sqrt(uniform), чтобы плотность была равномерной.
            double r = Math.sqrt(b) * (rMax - rMin) + rMin;
            double px = cathX + r * Math.cos(theta);
            double pz = cathZ + r * Math.sin(theta);
            double py = yMin + c * (yMax - yMin);

            Location loc = new Location(world, px, py, pz);

            // 3 разных частицы, выбираем по индексу.
            int kind = i % 3;
            switch (kind) {
                case 0:
                    world.spawnParticle(Particle.PORTAL, loc, 4,
                            0.6, 0.6, 0.6, 0.05, null, true);
                    break;
                case 1:
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1,
                            0.1, 0.1, 0.1, 0.005, null, true);
                    break;
                default:
                    world.spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 2,
                            0.4, 0.4, 0.4, 0.02, null, true);
                    break;
            }
        }

        // Колонки END_ROD, поднимающиеся вверх над 4 фасадными башнями
        // (декоративные «фонари» по углам собора). Координаты CT-офсета +/- 30
        // относительно центра собора.
        final int[][] towerOffsets = {
                { +30, +30 }, { +30, -30 }, { -30, +30 }, { -30, -30 },
        };
        double anim = (ms % 3000L) / 3000.0; // 0..1
        for (int[] off : towerOffsets) {
            double tx = cathX + off[0];
            double tz = cathZ + off[1];
            for (int up = 0; up < 30; up += 3) {
                double y = 100.0 + up + anim * 3.0;
                world.spawnParticle(Particle.END_ROD,
                        new Location(world, tx, y, tz), 1,
                        0.0, 0.0, 0.0, 0.0, null, true);
            }
        }

        // PR 3.10 (5B - магия): PORTAL + WITCH потоки снизу вверх со шпилей
        // 7 башен (центральная + 2 фасадные + 4 пинакля).
        final int[][] spireOffsets = {
                { 0, 0 },               // центральная
                { +20, +35 }, { -20, +35 },                                // 2 фасадные южные
                { +30, +0 }, { -30, +0 }, { +0, +35 }, { +0, -35 },        // 4 пинакля по концам креста
        };
        double witchAnim = (ms % 4000L) / 4000.0;
        for (int[] off : spireOffsets) {
            double sx = cathX + off[0];
            double sz = cathZ + off[1];
            // Поток снизу (y=110) вверх (y=170), с лёгким wobble.
            for (int dy = 0; dy < 15; dy++) {
                double y = 110.0 + dy * 4.0 + witchAnim * 4.0;
                double wobble = Math.sin((ms / 200.0) + dy) * 0.7;
                world.spawnParticle(Particle.SPELL_WITCH,
                        new Location(world, sx + wobble, y, sz), 1,
                        0.05, 0.05, 0.05, 0.0, null, true);
            }
        }

        // PR 3.10 (5C - святой огонь): 4 SOUL_FIRE_FLAME-столба на углах собора (80 блоков высотой).
        final int[][] holyColumns = {
                { +30, +42 }, { +30, -42 }, { -30, +42 }, { -30, -42 },
        };
        for (int[] off : holyColumns) {
            double cx = cathX + off[0];
            double cz = cathZ + off[1];
            // Столб от y=72 до y=152 шагом 4.
            for (int up = 0; up < 80; up += 4) {
                double y = 72.0 + up;
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        new Location(world, cx, y, cz), 1,
                        0.1, 0.1, 0.1, 0.005, null, true);
            }
        }
    }

    /**
     * PR 3.10 (4E): частицы ВНУТРИ собора — все 4 типа сразу.
     *
     * <ul>
     *   <li>HAPPY_VILLAGER над алтарём — золотая искра «благословения».</li>
     *   <li>DRAGON_BREATH над пересечением нефа+трансепта — фиолетовый туман.</li>
     *   <li>END_ROD по 6 колоннам — восходящий свет от капителей.</li>
     *   <li>ENCHANTMENT_TABLE в апсиде — магические руны вокруг алтаря.</li>
     * </ul>
     *
     * <p>Все с force=true чтобы было видно гостям из любого расстояния.
     */
    private static void spawnInteriorParticles(World world, long ms) {
        // Алтарь: HAPPY_VILLAGER, золотая искра.
        // Координаты алтаря: (CX, y=74, CZ-38) ≈ (45, 74, -53)
        final double altX = WorldGenerator.CATHEDRAL_X + 0.5;
        final double altZ = WorldGenerator.CATHEDRAL_Z - 38 + 0.5;
        for (int i = 0; i < 6; i++) {
            double a = ms * 0.03 + i * Math.PI / 3.0;
            double rx = altX + Math.cos(a) * 1.5;
            double rz = altZ + Math.sin(a) * 1.5;
            double ry = 75.0 + Math.sin(ms * 0.005 + i) * 0.5;
            world.spawnParticle(Particle.VILLAGER_HAPPY,
                    new Location(world, rx, ry, rz), 2,
                    0.1, 0.1, 0.1, 0.0, null, true);
        }
        // Спираль VILLAGER_HAPPY от пола до Глаза-навеса над алтарём.
        for (int up = 0; up < 12; up++) {
            double t = ms * 0.005 + up * 0.6;
            double rx = altX + Math.cos(t) * 0.8;
            double rz = altZ + Math.sin(t) * 0.8;
            world.spawnParticle(Particle.VILLAGER_HAPPY,
                    new Location(world, rx, 76.0 + up * 0.6, rz), 1,
                    0.0, 0.0, 0.0, 0.0, null, true);
        }

        // Пересечение нефа+трансепта: DRAGON_BREATH туман.
        // Координаты: (CX, y=82, CZ) = (45, 82, -15).
        final double crossX = WorldGenerator.CATHEDRAL_X + 0.5;
        final double crossZ = WorldGenerator.CATHEDRAL_Z + 0.5;
        for (int i = 0; i < 8; i++) {
            double a = (ms * 0.001) + i * Math.PI / 4.0;
            double rx = crossX + Math.cos(a) * 4.0;
            double rz = crossZ + Math.sin(a) * 4.0;
            world.spawnParticle(Particle.DRAGON_BREATH,
                    new Location(world, rx, 82.0, rz), 1,
                    0.5, 0.2, 0.5, 0.005, null, true);
        }

        // 6 колонн нефа: END_ROD-струи вверх от капителей (y=78..92).
        // Координаты: (CX±11, y=78..92, CZ+dz) для dz ∈ {-28, -10, 8, 28}.
        final int[] columnZs = { -28, -10, 8, 28 };
        for (int dz : columnZs) {
            for (int side : new int[] { -1, +1 }) {
                double colX = WorldGenerator.CATHEDRAL_X + side * 11 + 0.5;
                double colZ = WorldGenerator.CATHEDRAL_Z + dz + 0.5;
                // Анимированная струя 14 блоков высотой.
                for (int up = 0; up < 14; up += 2) {
                    double y = 79.0 + up + ((ms / 100L) % 2 == 0 ? 0.5 : 0.0);
                    world.spawnParticle(Particle.END_ROD,
                            new Location(world, colX, y, colZ), 1,
                            0.0, 0.0, 0.0, 0.0, null, true);
                }
            }
        }

        // Апсида вокруг алтаря: ENCHANTMENT_TABLE-руны.
        // Координаты: вокруг алтаря (CX, y=72..78, CZ-38).
        for (int i = 0; i < 12; i++) {
            double a = (ms * 0.002) + i * Math.PI / 6.0;
            double rx = altX + Math.cos(a) * 3.5;
            double rz = altZ + Math.sin(a) * 3.5;
            double ry = 73.0 + ((i * 7) % 6) * 0.7;
            world.spawnParticle(Particle.ENCHANTMENT_TABLE,
                    new Location(world, rx, ry, rz), 1,
                    0.3, 0.3, 0.3, 0.05, null, true);
        }
    }
}
