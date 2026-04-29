package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Обработчик кликов в GUI валюты
 */
public class CurrencyGUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        if (!event.getView().getTitle().equals("§6§lВалюта")) return;
        
        // Блокируем все клики в GUI валюты (только информационное окно)
        event.setCancelled(true);
    }
}
