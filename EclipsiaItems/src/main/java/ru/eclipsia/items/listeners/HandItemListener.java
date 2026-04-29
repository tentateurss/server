package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.equipment.EquipmentBonusApplier;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;

/**
 * Обработчик смены предмета в руке
 */
public class HandItemListener implements Listener {
    
    private final EquipmentManager equipmentManager;
    
    public HandItemListener(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        
        // Обновляем бонусы при смене предмета в руке
        updateBonuses(player);
    }
    
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        
        // Обновляем бонусы при смене рук
        updateBonuses(player);
    }
    
    private void updateBonuses(Player player) {
        // Применяем бонусы от экипировки + предмет в руке
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        EquipmentBonusApplier.applyBonuses(player, equipment);
        
        // Дополнительно применяем бонусы от предмета в руке
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.hasItemMeta()) {
            // Бонусы от оружия в руке уже учитываются через атрибуты Minecraft
            // Но мы можем добавить дополнительную логику если нужно
        }
    }
}
