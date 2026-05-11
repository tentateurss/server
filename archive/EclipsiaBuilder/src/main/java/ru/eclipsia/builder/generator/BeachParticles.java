package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import ru.eclipsia.builder.EclipsiaBuilder;

/**
 * Фоновые атмосферные частицы для мира «beach».
 * <p>
 * Каждые 10 тиков (≈0.5 сек) рассылает частицы по фиксированным «эмиттерам»
 * вокруг лагеря, арены, прохода и архипелага. Частицы видны только тем
 * игрокам, кто находится в радиусе ≈80 блоков от точки эмиссии — это
 * экономит сетевой трафик.
 *
 * <p>Все эмиттеры — постоянные, не зависят от ротации мира.
 */
public final class BeachParticles {

    private final EclipsiaBuilder plugin;
    private BukkitTask task;
    private int tick = 0;

    // Якоря
    private static final int CAMP_X  = 0,    CAMP_Z  = -55;
    private static final int ARENA_X = 0,    ARENA_Z = 95;
    private static final int CAVE_ENTRANCE_Z = 120;
    private static final int CAVE_EXIT_Z     = 165;
    private static final int ELIKIUM_ARCH_Z  = 240;
    private static final int Y0 = 5;

    public BeachParticles(EclipsiaBuilder plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld("beach");
                if (w == null) return;
                tick++;
                emit(w);
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void emit(World w) {
        // Угольки и дым у центрального костра в лагере
        spawn(w, Particle.LAVA, CAMP_X, Y0 + 1, CAMP_Z, 1, 0.5, 0.3, 0.5, 0);
        spawn(w, Particle.SMOKE_NORMAL, CAMP_X, Y0 + 2, CAMP_Z, 3, 0.6, 0.6, 0.6, 0.02);
        spawn(w, Particle.FLAME, CAMP_X, Y0 + 1, CAMP_Z, 4, 0.5, 0.2, 0.5, 0.01);
        // Soul fire у алтаря в лагере (на севере центра)
        spawn(w, Particle.SOUL_FIRE_FLAME, CAMP_X, Y0 + 2, CAMP_Z - 6, 2, 0.4, 0.4, 0.4, 0.01);
        // Ladrillos у боковых факелов
        for (int side = -1; side <= 1; side += 2) {
            spawn(w, Particle.SOUL_FIRE_FLAME,
                    CAMP_X + side * 14, Y0 + 1, CAMP_Z + 14, 1, 0.3, 0.3, 0.3, 0);
            spawn(w, Particle.SOUL_FIRE_FLAME,
                    CAMP_X + side * 14, Y0 + 1, CAMP_Z - 14, 1, 0.3, 0.3, 0.3, 0);
        }

        // Аметистовая магия у центрального алтаря арены
        spawn(w, Particle.SPELL_WITCH, ARENA_X, Y0 + 4, ARENA_Z, 4, 1.0, 0.5, 1.0, 0);
        spawn(w, Particle.ENCHANTMENT_TABLE, ARENA_X, Y0 + 4, ARENA_Z, 6, 1.5, 1.0, 1.5, 0.5);
        // Soul flames в 4-х жаровнях арены
        for (double angDeg : new double[]{45, 135, 225, 315}) {
            double a = Math.toRadians(angDeg);
            int sx = ARENA_X + (int) Math.round(Math.cos(a) * 5);
            int sz = ARENA_Z + (int) Math.round(Math.sin(a) * 5);
            spawn(w, Particle.SOUL_FIRE_FLAME, sx, Y0 + 2, sz, 2, 0.3, 0.3, 0.3, 0.01);
            spawn(w, Particle.SMOKE_NORMAL, sx, Y0 + 3, sz, 1, 0.3, 0.4, 0.3, 0.01);
        }
        // Фиолетовые портальные частицы у северных ворот босса
        int gateZ = ARENA_Z + 22 + 1;
        for (int dy = 1; dy <= 6; dy++) {
            spawn(w, Particle.PORTAL, ARENA_X, Y0 + dy, gateZ, 2, 1.5, 0.2, 0.1, 0.0);
        }
        // Один фиолетовый «душевный» firework на гейте (раз в 4 секунды)
        if (tick % 8 == 0) {
            spawn(w, Particle.DRAGON_BREATH, ARENA_X, Y0 + 4, gateZ, 3, 1.0, 0.5, 0.2, 0.0);
        }

        // Лампады прохода — мерцающий «огонь души»
        for (int z = CAVE_ENTRANCE_Z + 4; z <= CAVE_EXIT_Z - 4; z += 8) {
            spawn(w, Particle.SOUL_FIRE_FLAME, 0, Y0 + 12, z, 1, 0.3, 0.0, 0.3, 0);
            spawn(w, Particle.END_ROD, 0, Y0 + 12, z, 1, 0.2, 0.1, 0.2, 0);
        }
        // Аметистовый «звон» у статуй прохода
        for (int z = CAVE_ENTRANCE_Z + 6; z <= CAVE_EXIT_Z - 6; z += 12) {
            spawn(w, Particle.ENCHANTMENT_TABLE, -7, Y0 + 5, z, 2, 0.3, 0.5, 0.3, 0.1);
            spawn(w, Particle.ENCHANTMENT_TABLE, +7, Y0 + 5, z, 2, 0.3, 0.5, 0.3, 0.1);
        }

        // Эликий — арка в город — белые искрящиеся частицы
        spawn(w, Particle.SPELL_INSTANT, 0, Y0 + 9, ELIKIUM_ARCH_Z, 6, 1.5, 1.0, 0.5, 0);
        spawn(w, Particle.END_ROD, 0, Y0 + 11, ELIKIUM_ARCH_Z, 1, 0.5, 0.2, 0.2, 0);

        // Туман-дымка над морем (раз в секунду, по краям локации)
        if (tick % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                spawn(w, Particle.SMOKE_NORMAL,
                        side * 140, Y0 + 6, -100 + (tick * 7) % 200,
                        2, 6.0, 1.0, 6.0, 0.005);
                spawn(w, Particle.SPORE_BLOSSOM_AIR,
                        side * 140, Y0 + 4, 30 + ((tick * 13) % 200) - 100,
                        4, 8.0, 0.5, 8.0, 0);
            }
        }

        // === v7: ДОБАВЛЕНО — больше эффектов ===
        // Faerie-light: десятки glow-точек в лесу, медленно дрейфуют.
        if (tick % 3 == 0) {
            for (int i = 0; i < 12; i++) {
                double rx = (Math.random() - 0.5) * 200;
                double rz = -30 + Math.random() * 130; // forest зона
                spawn(w, Particle.END_ROD, rx, Y0 + 6 + Math.random() * 8, rz,
                        1, 0.1, 0.2, 0.1, 0.005);
            }
        }
        // Soul-flame у горных руин (z=140..170)
        if (tick % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double rx = -60 + Math.random() * 120;
                double rz = 140 + Math.random() * 30;
                spawn(w, Particle.SOUL_FIRE_FLAME, rx, Y0 + 4 + Math.random() * 6, rz,
                        2, 0.3, 0.3, 0.3, 0.01);
            }
        }
        // Магические искры у манекенов (5 точек попеременно мерцают)
        int[][] dummies = {{-5, -4}, {-3, -4}, {-1, -4}, {1, -4}, {3, -4},
                           {5, -4}, {-5, -1}, {5, -1}, {0, -5}};
        if (tick % 6 == 0) {
            for (int[] off : dummies) {
                if (Math.random() < 0.4) {
                    spawn(w, Particle.CRIT_MAGIC,
                            CAMP_X + off[0], Y0 + 3, CAMP_Z + off[1],
                            3, 0.4, 0.5, 0.4, 0.05);
                }
            }
        }
        // Капли воды-падают с облаков-дождь (каждые 2 такта по большому небу)
        if (tick % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                double rx = (Math.random() - 0.5) * 280;
                double rz = (Math.random() - 0.5) * 280;
                spawn(w, Particle.DRIP_WATER,
                        rx, Y0 + 70 + Math.random() * 20, rz, 1, 0.2, 0.2, 0.2, 0);
            }
        }
        // Падающие listья над лесом
        if (tick % 5 == 0) {
            for (int i = 0; i < 8; i++) {
                double rx = (Math.random() - 0.5) * 180;
                double rz = -20 + Math.random() * 120;
                spawn(w, Particle.FALLING_DUST,
                        rx, Y0 + 14 + Math.random() * 8, rz, 1, 0.5, 0.5, 0.5, 0);
            }
        }
        // Пылинки у границы острова — обозначают барьер
        if (tick % 8 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 4; i++) {
                    double rz = -100 + Math.random() * 280;
                    spawn(w, Particle.GLOW,
                            side * 165, Y0 + 8 + Math.random() * 14, rz,
                            1, 0.3, 4.0, 0.3, 0.0);
                }
            }
        }
    }

    private void spawn(World w, Particle particle, double x, double y, double z,
                       int count, double offX, double offY, double offZ, double speed) {
        // Частицы — серверные, отправляются всем игрокам в радиусе ~64 блоков
        // через стандартный World.spawnParticle.
        try {
            w.spawnParticle(particle, new Location(w, x + 0.5, y + 0.5, z + 0.5),
                    count, offX, offY, offZ, speed);
        } catch (Exception ignored) {
            // не валим сервер из-за частиц
        }
    }
}
