package ru.eclipsia.core.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Слушатель для отключения голода и естественной регенерации
 */
public class NoHungerListener implements Listener {
    
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            // Отменяем изменение уровня голода
            event.setCancelled(true);
            
            // Устанавливаем голод на максимум
            ((Player) event.getEntity()).setFoodLevel(20);
            ((Player) event.getEntity()).setSaturation(5.0f);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Устанавливаем голод на максимум при входе
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
    }
    
    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        // Отключаем ВСЮ естественную регенерацию здоровья
        if (event.getEntity() instanceof Player) {
            // Блокируем все типы естественной регенерации
            EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN ||
                reason == EntityRegainHealthEvent.RegainReason.EATING ||
                reason == EntityRegainHealthEvent.RegainReason.MAGIC_REGEN) {
                event.setCancelled(true);
            }
        }
    }
}
