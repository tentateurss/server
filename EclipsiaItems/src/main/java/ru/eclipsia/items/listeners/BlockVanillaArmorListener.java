package ru.eclipsia.items.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Блокировка стандартной экипировки брони
 */
public class BlockVanillaArmorListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Блокируем только слоты брони (не весь инвентарь)
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }
        
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        
        // Если игрок пытается положить предмет в слот брони
        if (cursor != null && cursor.getType() != Material.AIR) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§cИспользуйте §e/equipment §cдля экипировки!");
            return;
        }
        
        // Если игрок пытается взять предмет из слота брони
        if (current != null && current.getType() != Material.AIR) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§cИспользуйте §e/equipment §cдля управления экипировкой!");
        }
    }
}
