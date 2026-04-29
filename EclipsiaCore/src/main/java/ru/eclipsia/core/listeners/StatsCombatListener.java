package ru.eclipsia.core.listeners;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.eclipsia.core.stats.StatsBonusApplier;

import java.util.Random;

/**
 * Слушатель для применения бонусов от статов в бою
 */
public class StatsCombatListener implements Listener {
    
    private static final Random RANDOM = new Random();
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Обработка уклонения (защитник)
        if (event.getEntity() instanceof Player defender) {
            double evasionChance = StatsBonusApplier.getEvasionChance(defender);
            
            if (evasionChance > 0 && RANDOM.nextDouble() < evasionChance) {
                event.setCancelled(true);
                defender.sendMessage("§a⚡ Уклонение!");
                return;
            }
        }
        
        // Обработка урона от луков (атакующий)
        if (event.getDamager() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player attacker) {
                double bowDamageBonus = StatsBonusApplier.getBowDamageBonus(attacker);
                
                if (bowDamageBonus > 0) {
                    double originalDamage = event.getDamage();
                    double newDamage = originalDamage + bowDamageBonus; // Прямое добавление урона
                    event.setDamage(newDamage);
                }
            }
        }
        
        // Урон ближнего боя уже применяется через Attribute.GENERIC_ATTACK_DAMAGE
        // Магический урон будет применяться когда добавим магию
    }
}
