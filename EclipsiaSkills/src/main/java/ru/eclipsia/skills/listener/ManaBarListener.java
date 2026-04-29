package ru.eclipsia.skills.listener;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.skills.EclipsiaSkills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель для отображения маны через Boss Bar
 */
public class ManaBarListener implements Listener {
    
    private final EclipsiaSkills plugin;
    private final Map<UUID, BossBar> manaBars;
    private BukkitRunnable updateTask;
    
    public ManaBarListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
        this.manaBars = new HashMap<>();
        startUpdateTask();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        createManaBar(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeManaBar(player);
    }
    
    /**
     * Создать boss bar для маны
     */
    private void createManaBar(Player player) {
        BossBar bar = Bukkit.createBossBar(
            "§9Мана",
            BarColor.BLUE,
            BarStyle.SEGMENTED_10
        );
        
        bar.addPlayer(player);
        bar.setVisible(true);
        bar.setProgress(1.0);
        
        manaBars.put(player.getUniqueId(), bar);
    }
    
    /**
     * Удалить boss bar
     */
    private void removeManaBar(Player player) {
        BossBar bar = manaBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
    
    /**
     * Запустить задачу обновления
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateManaBar(player);
                }
            }
        };
        
        updateTask.runTaskTimer(plugin, 0L, 10L); // Каждые 0.5 секунды
    }
    
    /**
     * Обновить boss bar игрока
     */
    private void updateManaBar(Player player) {
        BossBar bar = manaBars.get(player.getUniqueId());
        if (bar == null) return;
        
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        if (profile == null) {
            bar.setVisible(false);
            return;
        }
        
        int currentMana = profile.getCurrentMana();
        int maxMana = profile.getMaxMana();
        
        if (maxMana <= 0) {
            bar.setVisible(false);
            return;
        }
        
        double progress = Math.max(0.0, Math.min(1.0, (double) currentMana / maxMana));
        bar.setProgress(progress);
        
        // Обновляем цвет в зависимости от процента
        BarColor color;
        if (progress >= 0.75) {
            color = BarColor.BLUE;
        } else if (progress >= 0.5) {
            color = BarColor.GREEN;
        } else if (progress >= 0.25) {
            color = BarColor.YELLOW;
        } else {
            color = BarColor.RED;
        }
        
        bar.setColor(color);
        bar.setTitle(String.format("§9Мана: §f%d§7/§f%d", currentMana, maxMana));
        bar.setVisible(true);
    }
    
    /**
     * Остановить задачу обновления
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        for (BossBar bar : manaBars.values()) {
            bar.removeAll();
        }
        manaBars.clear();
    }
}
