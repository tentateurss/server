package ru.eclipsia.builder.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Если игрок входит в воду на пляжном мире — отправить ему сообщение
 * «Здесь слишком опасно...» и телепортировать обратно к берегу.
 *
 * <p>Простая логика: смотрим ноги игрока и блок ниже. Если хоть один —
 * вода, телепортируем на ближайшую сушу (по направлению к лагерю).
 *
 * <p>Чтобы не спамить сообщениями каждый тик, держим cooldown 3с.
 */
public final class WaterGuardListener implements Listener {

    private static final String BEACH_WORLD = "beach";
    private static final long MESSAGE_COOLDOWN_MS = 3000L;
    private static final int SAFE_Y = 6; // выше уровня воды (5)

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public WaterGuardListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Location to = e.getTo();
        if (to == null) return;
        World world = to.getWorld();
        if (world == null) return;
        if (!BEACH_WORLD.equals(world.getName())) return;
        if (player.isOp() && player.getGameMode().name().equals("CREATIVE")) return;

        Block feet = to.getBlock();
        Block legs = world.getBlockAt(to.getBlockX(), to.getBlockY() + 1, to.getBlockZ());
        if (!isWaterLike(feet) && !isWaterLike(legs)) return;

        // Игрок в воде. Возвращаем на берег.
        Location safe = findSafeLocation(world, to);
        player.teleport(safe);
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(player.getUniqueId());
        if (last == null || now - last > MESSAGE_COOLDOWN_MS) {
            lastWarn.put(player.getUniqueId(), now);
            player.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "✦ "
                    + ChatColor.RESET + ChatColor.AQUA
                    + "Здесь ещё слишком опасно... " + ChatColor.GRAY
                    + "море таит в себе древние ужасы. Возвращайтесь к берегу.");
        }
    }

    private static boolean isWaterLike(Block b) {
        Material m = b.getType();
        return m == Material.WATER || m == Material.BUBBLE_COLUMN
                || m == Material.KELP_PLANT || m == Material.KELP;
    }

    /** Найти ближайшую безопасную точку на берегу. */
    private Location findSafeLocation(World world, Location from) {
        // Идём по прямой от игрока в сторону лагеря (z=-55 → y centerline)
        // и ищем первый сухой блок.
        double dx = -from.getX();
        double dz = -55 - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return new Location(world, 0, SAFE_Y, -55);
        }
        dx /= len;
        dz /= len;

        for (int step = 1; step <= 200; step++) {
            int x = (int) Math.round(from.getX() + dx * step);
            int z = (int) Math.round(from.getZ() + dz * step);
            // Ищем верхний твёрдый блок не-воды.
            for (int y = world.getMaxHeight() - 1; y > 0; y--) {
                Block b = world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m.isAir() || isWaterLike(b)) continue;
                // Нашли. Возвращаем точку выше.
                Block above = world.getBlockAt(x, y + 1, z);
                if (!isWaterLike(above)) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5,
                            from.getYaw(), from.getPitch());
                }
                break;
            }
        }
        // Fallback: спавн лагеря.
        return new Location(world, 0, SAFE_Y, -55);
    }
}
