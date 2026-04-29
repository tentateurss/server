package ru.eclipsia.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Слушатель для скрытия ванильного голода
 * ВАЖНО: Сердечки и баффы скрываются через ресурс-пак!
 */
public class HideVanillaHUDListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Устанавливаем сытость на максимум
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("EclipsiaCore"),
            () -> {
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
                player.setExhaustion(0.0f);
            },
            1L
        );
    }
    
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        // Блокируем изменение голода
        event.setCancelled(true);
        
        if (event.getEntity() instanceof Player player) {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }
}
