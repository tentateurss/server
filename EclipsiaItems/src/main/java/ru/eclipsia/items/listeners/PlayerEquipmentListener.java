package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.equipment.EquipmentBonusApplier;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;

/**
 * Обработчик загрузки/выгрузки экипировки
 */
public class PlayerEquipmentListener implements Listener {
    
    private final EquipmentManager equipmentManager;
    
    public PlayerEquipmentListener(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Загружаем экипировку
        equipmentManager.loadEquipment(player.getUniqueId());
        
        // Применяем бонусы
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        EquipmentBonusApplier.applyBonuses(player, equipment);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Удаляем бонусы
        EquipmentBonusApplier.removeAllBonuses(player);
        
        // Удаляем HUD
        EclipsiaItems.getInstance().getHudManager().removeHUD(player);
        
        // Выгружаем экипировку
        equipmentManager.unloadEquipment(player.getUniqueId());
    }
}
