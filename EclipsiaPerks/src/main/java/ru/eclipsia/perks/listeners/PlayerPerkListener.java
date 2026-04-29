package ru.eclipsia.perks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.core.api.EclipsiaAPI;

/**
 * Обработчик загрузки/выгрузки данных перков
 */
public class PlayerPerkListener implements Listener {
    
    private final PlayerPerkManager playerManager;
    
    public PlayerPerkListener(PlayerPerkManager playerManager) {
        this.playerManager = playerManager;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Загружаем данные перков
        playerManager.loadPlayerData(player.getUniqueId());
        
        // Обновляем очки на основе текущего уровня
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        int level = api.getPlayerLevel(player);
        playerManager.updatePointsForLevel(player.getUniqueId(), level);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Выгружаем данные перков
        playerManager.unloadPlayerData(player.getUniqueId());
    }
}
