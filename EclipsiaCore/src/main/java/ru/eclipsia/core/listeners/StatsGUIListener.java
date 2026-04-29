package ru.eclipsia.core.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Обработчик кликов в GUI характеристик (только просмотр)
 */
public class StatsGUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (!event.getView().getTitle().equals("§6Характеристики")) return;
        
        // Блокируем все клики - GUI только для просмотра
        event.setCancelled(true);
    }
}
