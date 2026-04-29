package ru.eclipsia.mobs.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.mobs.mob.CustomMob;
import ru.eclipsia.mobs.mob.MobManager;

/**
 * Обработчик отображения HP мобов над головой
 */
public class MobHealthBarListener implements Listener {
    
    private final Plugin plugin;
    
    public MobHealthBarListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        
        // Проверяем что это кастомный моб
        if (!MobManager.getInstance().isCustomMob(mob)) return;
        
        // Обновляем HP в имени моба после получения урона
        // Используем MONITOR priority чтобы обновлять только если урон реально применился
        // ignoreCancelled = true - не обновляем если событие отменено
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Проверяем что моб еще существует и жив
            if (mob.isValid() && !mob.isDead()) {
                CustomMob customMob = MobManager.getInstance().getCustomMobFromEntity(mob);
                if (customMob != null) {
                    customMob.updateHealthDisplay(mob);
                }
            }
        }, 1L);
    }
}
