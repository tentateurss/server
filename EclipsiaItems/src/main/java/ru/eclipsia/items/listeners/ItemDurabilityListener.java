package ru.eclipsia.items.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Отключение поломки предметов
 */
public class ItemDurabilityListener implements Listener {
    
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        // Отменяем поломку всех предметов
        event.setCancelled(true);
    }
}
