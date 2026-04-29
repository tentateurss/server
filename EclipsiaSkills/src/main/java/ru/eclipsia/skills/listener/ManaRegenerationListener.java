package ru.eclipsia.skills.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.skills.EclipsiaSkills;

/**
 * Слушатель для регенерации маны
 */
public class ManaRegenerationListener implements Listener {
    
    private final EclipsiaSkills plugin;
    
    public ManaRegenerationListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Запускаем регенерацию маны для игрока
        startManaRegeneration(player);
    }
    
    /**
     * Запустить регенерацию маны для игрока
     */
    private void startManaRegeneration(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Проверяем что игрок онлайн
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Получаем профиль
                PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
                if (profile == null) {
                    return;
                }
                
                // Если мана не полная - регенерируем
                if (profile.getCurrentMana() < profile.getMaxMana()) {
                    // Регенерация: 2% от максимальной маны каждую секунду
                    int regenAmount = Math.max(1, (int) (profile.getMaxMana() * 0.02));
                    int newMana = Math.min(profile.getMaxMana(), profile.getCurrentMana() + regenAmount);
                    
                    PlayerProfile updated = profile.toBuilder()
                            .currentMana(newMana)
                            .build();
                    
                    plugin.getAPI().updateProfile(player, updated);
                    
                    // Action bar больше не нужен, мана отображается в boss bar
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Каждую секунду (20 тиков)
    }
}
