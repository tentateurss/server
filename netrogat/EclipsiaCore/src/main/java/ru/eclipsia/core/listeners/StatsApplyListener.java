package ru.eclipsia.core.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.core.stats.StatsBonusApplier;

/**
 * Слушатель для применения бонусов статов при входе/выходе
 */
public class StatsApplyListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Применяем бонусы от статов через 1 тик (после загрузки данных)
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaCore"),
            () -> StatsBonusApplier.applyAllBonuses(player),
            1L
        );
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Удаляем бонусы при выходе
        StatsBonusApplier.removeAllBonuses(player);
    }
}
