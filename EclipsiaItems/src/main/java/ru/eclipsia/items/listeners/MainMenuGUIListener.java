package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.eclipsia.items.gui.CurrencyGUI;
import ru.eclipsia.items.gui.MainMenuGUI;

/**
 * Обработчик кликов в главном меню
 */
public class MainMenuGUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (!event.getView().getTitle().equals("§6§lМеню персонажа")) return;
        
        event.setCancelled(true);
        
        if (event.getCurrentItem() == null) return;
        
        int slot = event.getRawSlot();
        String action = MainMenuGUI.getActionBySlot(slot);
        
        if (action == null) return;
        
        player.closeInventory();
        
        // Выполняем команду в зависимости от выбранного раздела
        switch (action) {
            case "profile":
                player.performCommand("profile");
                break;
            case "stats":
                player.performCommand("stats");
                break;
            case "skills":
                player.performCommand("skills");
                break;
            case "equipment":
                player.performCommand("equipment");
                break;
            case "currency":
                CurrencyGUI.open(player);
                break;
            case "perks":
                player.performCommand("perks");
                break;
        }
    }
}
