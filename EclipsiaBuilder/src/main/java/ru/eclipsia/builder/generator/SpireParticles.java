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
 * <p>В PR 3.6 блочный куб AMETHYST/PURPUR/END_ROD удалён —
 * Глаз теперь существует только как параметрически нарисованный
 * частицами визуальный символ:
 * <ul>
 *   <li><b>Веко</b> (внешний эллипс) — {@link Particle#DRAGON_BREATH},
 *       полуоси a=4.5 (X) × b=2.0 (Y). Плоскость <i>вертикальная</i>
 *       XY (смотрит на юг, откуда подходит игрок).</li>
 *   <li><b>Радужка</b> (вращается медленно) — {@link Particle#SPELL_WITCH}
 *       по окружности r=1.6, фаза кратна {@link System#currentTimeMillis()}.
 *       Магенто-розовая цветовая «корона».</li>
 *   <li><b>Зрачок</b> (центр) — {@link Particle#END_ROD} (×4), пульсирует.</li>
 *   <li><b>Аура</b> вокруг — {@link Particle#PORTAL} (×30, радиус 5),
 *       тёмно-фиолетовый туман.</li>
 *   <li><b>Руны сверху</b> — {@link Particle#ENCHANTMENT_TABLE} (×20),
 *       стекают в Глаз с высоты 5 блоков.</li>
 *   <li><b>Стекающие искры</b> — {@link Particle#REVERSE_PORTAL} (×15)
 *       сверху в центр, имитируют «всасывание».</li>
 * </ul>
 *
 * <p>Запускается каждые 3 тика (≈6.6 раз/сек) — достаточно для
 * иллюзии непрерывной анимации без значимой нагрузки. Подписывается
 * на координаты Глаза через статические поля
 * {@link WorldGenerator#spireCenterX}/{@link WorldGenerator#spireCenterY}/
 * {@link WorldGenerator#spireCenterZ}.
 */
public final class SpireParticles {

    private static final int LID_STEPS  = 36;
    private static final int IRIS_STEPS = 24;
    private static final double LID_A   = 4.5; // полуось эллипса по X
    private static final double LID_B   = 2.0; // полуось эллипса по Y
    private static final double IRIS_R  = 1.6; // радиус радужки

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

                // === ВЕКО (внешний эллипс, вертикальная плоскость XY) ===
                for (int i = 0; i < LID_STEPS; i++) {
                    double t = (double) i / LID_STEPS * Math.PI * 2.0;
                    double dx = LID_A * Math.cos(t);
                    double dy = LID_B * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // === РАДУЖКА (вращается, плоскость XY) ===
                double phase = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2.0;
                for (int i = 0; i < IRIS_STEPS; i++) {
                    double t = (double) i / IRIS_STEPS * Math.PI * 2.0 + phase;
                    double dx = IRIS_R * Math.cos(t);
                    double dy = IRIS_R * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.SPELL_WITCH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }

                // === ЗРАЧОК (центр, мерцает) ===
                world.spawnParticle(Particle.END_ROD, center, 4, 0.18, 0.18, 0.18, 0.0);

                // === АУРА (тёмно-фиолетовый туман вокруг Глаза) ===
                world.spawnParticle(Particle.PORTAL, center, 30, 5.0, 1.5, 1.5, 0.05);

                // === РУНЫ (стекают сверху в Глаз) ===
                Location above = new Location(world, cx, cy + 5.0, cz);
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, above, 20, 1.5, 0.5, 1.5, 0.5);

                // === ВСАСЫВАЮЩИЕ ИСКРЫ (REVERSE_PORTAL) ===
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 15, 2.0, 3.0, 2.0, 0.05);

                // === ВНЕШНИЙ ОБОДОК (DRAGON_BREATH повторно, шире) ===
                // Для жирности контура веко рисуем дважды — второй раз с offset 0.2.
                for (int i = 0; i < LID_STEPS; i += 2) {
                    double t = (double) i / LID_STEPS * Math.PI * 2.0;
                    double dx = (LID_A + 0.4) * Math.cos(t);
                    double dy = (LID_B + 0.2) * Math.sin(t);
                    Location p = new Location(world, cx + dx, cy + dy, cz);
                    world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0.05, 0.05, 0.05, 0.0);
                }
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
