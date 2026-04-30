package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Атмосферные частицы вокруг «глаза» на шпиле собора Эликий.
 *
 * <p>Запускается раз в 5 тиков (4 раза в секунду): кольцо
 * {@link Particle#PORTAL} (≈100 шт.), магические руны
 * {@link Particle#ENCHANTMENT_TABLE} (≈50 шт.) и точечные искры
 * {@link Particle#SOUL_FIRE_FLAME} (≈20 шт.).
 *
 * <p>По смыслу аналогичен {@link BeachParticles}, но крутится только
 * в одной точке (вершина шпиля) и только в мире {@code world}.
 * Подписывается на координаты шпиля через статические поля
 * {@link WorldGenerator#spireCenterX}/{@link WorldGenerator#spireCenterY}/
 * {@link WorldGenerator#spireCenterZ} — до PR 3 указывают на место,
 * где собор будет построен; после PR 3 (фактическая постройка шпиля)
 * перезаписываются на точные координаты «глаза».
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

                // PORTAL (100 шт., радиус 4, высота 3) — фиолетовое свечение.
                world.spawnParticle(Particle.PORTAL, center, 100, 4.0, 3.0, 4.0, 0.05);
                // ENCHANTMENT_TABLE (50 шт.) — магические руны, поднимающиеся к глазу.
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, center, 50, 3.0, 2.0, 3.0, 0.5);
                // SOUL_FIRE_FLAME (20 шт.) — фиолетовый огонь в центре.
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 20, 0.6, 1.0, 0.6, 0.01);
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
