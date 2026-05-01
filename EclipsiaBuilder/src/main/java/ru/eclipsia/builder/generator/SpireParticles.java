package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Атмосферные частицы вокруг парящего «Глаза Эликия» над собором.
 *
 * <p>После переделки в PR 3.5 Глаз парит на y≈201 над центральным
 * шпилем; вокруг него крутятся фиолетовые частицы, имитируя
 * магическую ауру:
 * <ul>
 *   <li>{@link Particle#DRAGON_BREATH} (≈40 шт., радиус 5) — основа
 *       фиолетового облака.</li>
 *   <li>{@link Particle#PORTAL} (≈80 шт., радиус 4) — мерцающие
 *       тёмно-фиолетовые точки.</li>
 *   <li>{@link Particle#WITCH} (≈30 шт., радиус 3) — магенто-розовые
 *       искры (зельеварные руны).</li>
 *   <li>{@link Particle#ENCHANTMENT_TABLE} (≈40 шт.) — магические
 *       руны, стекающие к Глазу.</li>
 *   <li>{@link Particle#END_ROD} (≈10 шт., тонкий ореол) — белые
 *       искры по верху Глаза.</li>
 * </ul>
 *
 * <p>Запускается раз в 5 тиков (4 раза в секунду). Подписывается на
 * координаты Глаза через статические поля
 * {@link WorldGenerator#spireCenterX}/{@link WorldGenerator#spireCenterY}/
 * {@link WorldGenerator#spireCenterZ} — после {@code CathedralBuilder.build()}
 * они перезаписываются на фактическую точку.
 */
public final class SpireParticles {

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
                // Координаты предынициализированы константами в
                // WorldGenerator, поэтому Double.NaN тут не ожидается.
                if (Double.isNaN(cx) || Double.isNaN(cy) || Double.isNaN(cz)) return;

                Location center = new Location(world, cx, cy, cz);

                // DRAGON_BREATH (40, радиус 5) — основа фиолетового облака.
                world.spawnParticle(Particle.DRAGON_BREATH, center, 40, 5.0, 2.5, 5.0, 0.01);
                // PORTAL (80, радиус 4) — мерцающие тёмно-фиолетовые точки.
                world.spawnParticle(Particle.PORTAL, center, 80, 4.0, 3.0, 4.0, 0.05);
                // SPELL_WITCH (30, радиус 3) — магенто-розовые зельеварные руны.
                world.spawnParticle(Particle.SPELL_WITCH, center, 30, 3.0, 2.0, 3.0, 0.0);
                // REVERSE_PORTAL (20) — частицы, стекающие В Глаз сверху.
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 20, 2.0, 4.0, 2.0, 0.05);
                // ENCHANTMENT_TABLE (40) — стекающие руны.
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, center, 40, 3.0, 2.0, 3.0, 0.5);
                // END_ROD (10, тонкий ореол) — белые искры по верху.
                world.spawnParticle(Particle.END_ROD, center, 10, 1.5, 0.5, 1.5, 0.01);
            }
        }.runTaskTimer(plugin, 60L, 5L); // первый запуск через 3 сек, потом каждые 5 тиков
    }

    /** Остановить эффект (вызывается на onDisable). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
