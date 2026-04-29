package ru.eclipsia.items.listeners;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Слушатель для бесконечных луков
 * ИСПРАВЛЕНО: Все кастомные луки стреляют БЕЗ стрел
 */
public class InfiniteBowListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        ItemStack bow = event.getBow();
        
        // Проверяем что это лук
        if (bow == null || bow.getType() != Material.BOW) {
            return;
        }
        
        // Проверяем что это кастомный лук (с лором)
        if (!bow.hasItemMeta() || !bow.getItemMeta().hasLore()) {
            return;
        }
        
        // ИСПРАВЛЕНО: Всегда разрешаем стрелять из кастомного лука без стрел
        // Если событие уже отменено (нет стрел), разблокируем его
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
        
        // Если у игрока нет стрел - создаем стрелу вручную
        if (!player.getInventory().contains(Material.ARROW)) {
            // Получаем силу натяжения (0.0 - 1.0)
            float force = event.getForce();
            
            // Запускаем стрелу вручную
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(player.getLocation().getDirection().multiply(force * 3.0));
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setShooter(player);
            
            // Критический удар с шансом
            if (Math.random() < 0.1) {
                arrow.setCritical(true);
            }
            
            // Отменяем оригинальное событие чтобы не было двойной стрелы
            event.setCancelled(true);
        }
        // Если стрелы есть - пусть стреляет как обычно, но не расходует их
        else {
            // Устанавливаем что стрелы не расходуются
            event.setConsumeItem(false);
        }
    }
}
