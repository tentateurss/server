package ru.eclipsia.mobs.listeners;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Обработчик спавна мобов
 */
public class MobSpawnListener implements Listener {
    
    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        // Отменяем естественный спавн ванильных враждебных мобов
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            EntityType type = event.getEntityType();
            
            // Список враждебных мобов для отмены
            if (type == EntityType.ZOMBIE || 
                type == EntityType.SKELETON ||
                type == EntityType.CREEPER ||
                type == EntityType.SPIDER ||
                type == EntityType.ENDERMAN ||
                type == EntityType.WITCH ||
                type == EntityType.SLIME ||
                type == EntityType.PHANTOM ||
                type == EntityType.DROWNED ||
                type == EntityType.HUSK ||
                type == EntityType.STRAY ||
                type == EntityType.CAVE_SPIDER ||
                type == EntityType.SILVERFISH) {
                
                event.setCancelled(true);
            }
        }
    }
}
