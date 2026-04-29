package ru.eclipsia.mobs.listeners;

import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.eclipsia.mobs.EclipsiaMobs;
import ru.eclipsia.mobs.boss.BossManager;
import ru.eclipsia.mobs.boss.GatekeeperArena;
import ru.eclipsia.mobs.boss.GatekeeperBoss;

/**
 * Слушатель смерти боссов
 */
public class BossDeathListener implements Listener {
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        
        // Проверяем что это босс
        if (!golem.hasMetadata("eclipsia_boss")) return;
        
        String bossType = golem.getMetadata("eclipsia_boss").get(0).asString();
        
        if ("gatekeeper".equals(bossType)) {
            // Хранитель Врат
            GatekeeperBoss boss = BossManager.getInstance().getGatekeeper();
            
            Player killer = golem.getKiller();
            boss.onDeath(killer);
            
            // Очищаем дропы (выдаем награды вручную)
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Арена Хранителя: ставим флаг «побеждён», чистим небо,
            // активируем портал в арке.
            GatekeeperArena arena = EclipsiaMobs.getInstance().getGatekeeperArena();
            if (arena != null) {
                arena.onBossDefeated(golem.getWorld(), killer);
            }
        }
    }
}
