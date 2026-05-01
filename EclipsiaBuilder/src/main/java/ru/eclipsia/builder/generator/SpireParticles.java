package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Парящий «Глаз Эликия» — рисуется ЧАСТИЦАМИ над собором.
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

    // Геометрия миндаля.
    private static final double LID_A   = 7.5; // полуось эллипса по X
    private static final double LID_B   = 3.0; // полуось эллипса по Y
    private static final int LID_STEPS  = 64;

    // Склера (внутренний контур).
    private static final double SCLERA_A = 6.0;
    private static final double SCLERA_B = 2.4;
    private static final int SCLERA_STEPS = 48;

    // Радужка.
    private static final double IRIS_OUT_R = 2.2;
    private static final double IRIS_IN_R  = 1.3;
    private static final int IRIS_STEPS    = 32;

    // Бровь.
    private static final double BROW_A = 5.5;
    private static final double BROW_B = 0.6;
    private static final int BROW_STEPS = 18;

    // Руническое кольцо (вращается).
    private static final double RUNE_A = 9.0;
    private static final double RUNE_B = 5.0;
    private static final int RUNE_STEPS = 36;

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
                world.spawnParticle(Particle.PORTAL, center, 60, 8.0, 3.0, 2.0, 0.05);

                // ===== 2. DIAMOND-GLOW (4 луча наружу) =====
                for (int len = 1; len <= 9; len++) {
                    double f = 1.0 - (len - 1) / 9.0; // плотность затухает
                    int parts = (int) Math.round(2 * f);
                    if (parts < 1) parts = 1;
                    // Восток
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            new Location(world, cx + LID_A + len, cy, cz), parts,
                            0.05, 0.05, 0.05, 0.0);
                    // Запад
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            new Location(world, cx - LID_A - len, cy, cz), parts,
                            0.05, 0.05, 0.05, 0.0);
                    // Север (вверх)
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            new Location(world, cx, cy + LID_B + len, cz), parts,
                            0.05, 0.05, 0.05, 0.0);
                    // Юг (вниз)
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            new Location(world, cx, cy - LID_B - len, cz), parts,
                            0.05, 0.05, 0.05, 0.0);
                }

                // ===== 3. РУНИЧЕСКОЕ КОЛЬЦО (вращается) =====
                double runePhase = (ms % 8000L) / 8000.0 * Math.PI * 2.0;
                for (int i = 0; i < RUNE_STEPS; i++) {
                    double t = (double) i / RUNE_STEPS * Math.PI * 2.0 + runePhase;
                    double dx = RUNE_A * Math.cos(t);
                    double dy = RUNE_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.ENCHANTMENT_TABLE, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 4. БРОВЬ (DRAGON_BREATH дуга над веком) =====
                for (int i = 0; i < BROW_STEPS; i++) {
                    double t = (double) i / (BROW_STEPS - 1) * Math.PI; // полуокружность вверх
                    double dx = BROW_A * Math.cos(t);
                    double dy = LID_B + 0.6 + BROW_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 5. ВЕКО — внешний миндаль (DRAGON_BREATH) =====
                // Тройной проход: ровный + чуть меньше + чуть больше = жирность.
                for (int pass = 0; pass < 3; pass++) {
                    double aMul = 1.0 + (pass - 1) * 0.05;
                    double bMul = 1.0 + (pass - 1) * 0.05;
                    for (int i = 0; i < LID_STEPS; i++) {
                        double t = (double) i / LID_STEPS * Math.PI * 2.0;
                        double dx = LID_A * aMul * Math.cos(t);
                        double dy = LID_B * bMul * Math.sin(t);
                        Location p = new Location(world, cx + dx, cy + dy, cz);
                        world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                // ===== 6. СКЛЕРА (FIREWORKS_SPARK белый внутренний контур) =====
                for (int i = 0; i < SCLERA_STEPS; i++) {
                    double t = (double) i / SCLERA_STEPS * Math.PI * 2.0;
                    double dx = SCLERA_A * Math.cos(t);
                    double dy = SCLERA_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.FIREWORKS_SPARK, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 7. ВНЕШНЯЯ РАДУЖКА (SPELL_WITCH r=2.2, по часовой) =====
                double irisOutPhase = (ms % 12000L) / 12000.0 * Math.PI * 2.0;
                for (int i = 0; i < IRIS_STEPS; i++) {
                    double t = (double) i / IRIS_STEPS * Math.PI * 2.0 + irisOutPhase;
                    double dx = IRIS_OUT_R * Math.cos(t);
                    double dy = IRIS_OUT_R * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.SPELL_WITCH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 8. ВНУТРЕННЯЯ РАДУЖКА (SPELL_WITCH r=1.3, против ч.с.) =====
                double irisInPhase = -(ms % 7000L) / 7000.0 * Math.PI * 2.0;
                for (int i = 0; i < IRIS_STEPS; i++) {
                    double t = (double) i / IRIS_STEPS * Math.PI * 2.0 + irisInPhase;
                    double dx = IRIS_IN_R * Math.cos(t);
                    double dy = IRIS_IN_R * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.SPELL_WITCH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 9. SLIT-ЗРАЧОК (вертикальная щель SQUID_INK) =====
                for (double dy = -1.2; dy <= 1.2; dy += 0.15) {
                    Location p = new Location(world, cx, cy + dy, cz);
                    world.spawnParticle(Particle.SQUID_INK, p, 1, 0.05, 0.0, 0.05, 0.0);
                }

                // ===== 10. HIGHLIGHT ЗРАЧКА (END_ROD ×3 в верхней половине) =====
                for (int i = 0; i < 3; i++) {
                    Location p = new Location(world, cx + 0.15, cy + 0.5 + i * 0.15, cz);
                    world.spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // ===== 11. СЛЁЗЫ (END_ROD от внешних углов вниз) =====
                // Анимация: tearOffset движется от 0 до 6 за 4 сек.
                double tearOffset = (ms % 4000L) / 4000.0 * 6.0;
                for (int side : new int[] { -1, +1 }) {
                    for (int i = 0; i < 6; i++) {
                        double dy = -tearOffset - i * 0.4;
                        if (dy < -8.0) break;
                        Location p = new Location(world, cx + side * (LID_A * 0.7), cy + dy, cz);
                        world.spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                // ===== 12. СВЕТОВЫЕ ЛУЧИ ВВЕРХ И ВНИЗ =====
                // 2 верхних столба END_ROD (на x=±LID_A*0.4) от cy+LID_B+1 до +9
                // 2 нижних — симметрично от cy-LID_B-1 до -9.
                for (int side : new int[] { -1, +1 }) {
                    for (int up = 1; up <= 8; up++) {
                        Location pUp = new Location(world,
                                cx + side * LID_A * 0.4, cy + LID_B + up, cz);
                        Location pDown = new Location(world,
                                cx + side * LID_A * 0.4, cy - LID_B - up, cz);
                        world.spawnParticle(Particle.END_ROD, pUp, 1, 0.0, 0.0, 0.0, 0.0);
                        world.spawnParticle(Particle.END_ROD, pDown, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                // ===== 13. ПАДАЮЩИЕ РУНЫ (ENCHANTMENT_TABLE сверху) =====
                Location above = new Location(world, cx, cy + 7.0, cz);
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, above, 30, 3.0, 0.5, 1.5, 0.5);

                // ===== 14. ВСАСЫВАЮЩИЕ ИСКРЫ =====
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 20, 4.0, 3.0, 2.0, 0.05);
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
}
