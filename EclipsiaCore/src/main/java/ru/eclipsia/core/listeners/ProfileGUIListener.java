package ru.eclipsia.core.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.eclipsia.core.gui.ProfileGUI;
import ru.eclipsia.core.gui.StatsGUI;

/**
 * Обработчик кликов в GUI профиля
 */
public class ProfileGUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (!event.getView().getTitle().equals("§6Профиль персонажа")) return;
        
        event.setCancelled(true);
        
        if (event.getCurrentItem() == null) return;
        
        int slot = event.getRawSlot();
        String action = ProfileGUI.getActionBySlot(slot);
        
        if (action == null) return;
        
        player.closeInventory();
        
        switch (action) {
            case "stats":
                StatsGUI.open(player);
                break;
            case "perks":
                player.performCommand("perks");
                break;
            case "equipment":
                player.performCommand("equipment");
                break;
        }
    }
}
