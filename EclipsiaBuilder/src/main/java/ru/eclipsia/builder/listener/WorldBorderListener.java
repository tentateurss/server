package ru.eclipsia.builder.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.eclipsia.builder.EclipsiaBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Простая реализация прямоугольных границ для миров без зависимости
 * от WorldGuard. Если игрок пытается выйти за зарегистрированный
 * прямоугольник в указанном мире — телепортируем его обратно к точке
 * From и показываем сообщение.
 *
 * <p>Границы регистрируются вызовом {@link #registerBorder(String,
 * int, int, int, int, String)} на этапе построения структур.
 */
public class WorldBorderListener implements Listener {

    private final EclipsiaBuilder plugin;
    /** Список границ в каждом мире (по имени). */
    private final Map<String, List<Border>> bordersByWorld = new HashMap<>();

    public WorldBorderListener(EclipsiaBuilder plugin) {
        this.plugin = plugin;
    }

    /**
     * Зарегистрировать прямоугольную границу. {@code denyMessage} показывается
     * игроку при попытке выйти.
     */
    public void registerBorder(String worldName, int xMin, int zMin,
                               int xMax, int zMax, String denyMessage) {
        bordersByWorld
                .computeIfAbsent(worldName, k -> new ArrayList<>())
                .add(new Border(xMin, zMin, xMax, zMax, denyMessage));
        plugin.getLogger().info("Граница зарегистрирована в '" + worldName
                + "': X[" + xMin + ".." + xMax + "] Z[" + zMin + ".." + zMax + "]");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        // Только если реально перемещение (не поворот головы).
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (checkBorders(event.getPlayer(), from, to)) {
            event.setTo(from);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        // Игнорируем системные/админские телепорты (Multiverse и т.д.).
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN
                && event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }
        if (checkBorders(event.getPlayer(), event.getFrom(), to)) {
            event.setCancelled(true);
        }
    }

    /** Возвращает true, если попытка пересечения границы найдена. */
    private boolean checkBorders(Player player, Location from, Location to) {
        if (player.hasPermission("eclipsia.admin")) return false;
        if (!from.getWorld().equals(to.getWorld())) return false;

        List<Border> borders = bordersByWorld.get(to.getWorld().getName());
        if (borders == null || borders.isEmpty()) return false;

        for (Border b : borders) {
            boolean fromInside = b.contains(from.getX(), from.getZ());
            boolean toInside = b.contains(to.getX(), to.getZ());
            // Был внутри, пытается выйти — блокируем.
            if (fromInside && !toInside) {
                player.sendMessage(b.denyMessage);
                return true;
            }
        }
        return false;
    }

    /** Прямоугольная граница в плоскости XZ. */
    private static final class Border {
        final int xMin, zMin, xMax, zMax;
        final String denyMessage;

        Border(int xMin, int zMin, int xMax, int zMax, String denyMessage) {
            this.xMin = Math.min(xMin, xMax);
            this.xMax = Math.max(xMin, xMax);
            this.zMin = Math.min(zMin, zMax);
            this.zMax = Math.max(zMin, zMax);
            this.denyMessage = denyMessage;
        }

        boolean contains(double x, double z) {
            return x >= xMin && x <= xMax && z >= zMin && z <= zMax;
        }
    }
}
