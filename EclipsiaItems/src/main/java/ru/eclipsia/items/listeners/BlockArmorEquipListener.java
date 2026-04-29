package ru.eclipsia.items.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Блокировка экипировки брони через ПКМ (для всей брони, включая ванильную)
 */
public class BlockArmorEquipListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmorEquip(PlayerInteractEvent event) {
        // Проверяем только ПКМ
        if (event.getAction() != Action.RIGHT_CLICK_AIR && 
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        
        // Проверяем является ли предмет броней
        if (!isArmorPiece(item.getType())) {
            return;
        }
        
        // Если это кастомный предмет (с лором) - пропускаем, его обработает ArmorSyncListener
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            return;
        }
        
        // Блокируем ВСЮ ванильную броню без лора
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage("§cВанильная броня отключена! Используйте §e/item generate §cдля получения кастомной брони.");
    }
    
    /**
     * Проверить является ли предмет броней
     */
    private boolean isArmorPiece(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || 
               name.endsWith("_CHESTPLATE") || 
               name.endsWith("_LEGGINGS") || 
               name.endsWith("_BOOTS");
    }
}
