package ru.eclipsia.builder.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.eclipsia.builder.EclipsiaBuilder;
import ru.eclipsia.builder.generator.BeachGenerator;

/**
 * v8: при смерти в beach игрок возрождается в лагере и НИЧЕГО не теряет.
 */
public final class CampRespawnListener implements Listener {

    public static final String BEACH_WORLD = "beach";

    private final EclipsiaBuilder plugin;

    public CampRespawnListener(EclipsiaBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent e) {
        World world = e.getEntity().getWorld();
        if (!BEACH_WORLD.equals(world.getName())) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        World world = e.getPlayer().getWorld();
        if (!BEACH_WORLD.equals(world.getName())) return;
        World beach = Bukkit.getWorld(BEACH_WORLD);
        if (beach == null) return;
        Location campSpawn = new Location(beach,
                BeachGenerator.CAMP_X + 0.5,
                BeachGenerator.GROUND_Y + 2,
                BeachGenerator.CAMP_Z + 0.5);
        e.setRespawnLocation(campSpawn);
    }
}
