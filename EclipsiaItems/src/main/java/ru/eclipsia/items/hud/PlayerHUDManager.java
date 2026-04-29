package ru.eclipsia.items.hud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер HUD игрока
 */
public class PlayerHUDManager {
    
    private final Plugin plugin;
    private final Map<UUID, BossBar> playerBossBars;
    private int taskId = -1;
    
    public PlayerHUDManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerBossBars = new HashMap<>();
    }
    
    /**
     * Запустить обновление HUD
     */
    public void start() {
        if (taskId != -1) {
            return;
        }
        
        // Обновляем HUD каждые 20 тиков (1 секунда)
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateHUD(player);
            }
        }, 0L, 20L);
    }
    
    /**
     * Остановить обновление HUD
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        
        // Удаляем все BossBar
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerBossBars.clear();
    }
    
    /**
     * Обновить HUD игрока
     */
    private void updateHUD(Player player) {
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerData data = api.getPlayerData(player);
        
        if (data == null) {
            return;
        }

        // ActionBar теперь пишет ТОЛЬКО HUDActionBarListener из EclipsiaSkills
        // (единый формат с HP/Эгида/Мана/реген + базовые статы). Старая
        // строчка отсюда вызывала мерцание из-за двух источников 1× и 0.5×/тик.

        // Обновляем BossBar (полоса опыта)
        updateBossBar(player, data);
    }
    
    /**
     * Обновить ActionBar
     */
    private void updateActionBar(Player player, PlayerData data) {
        String className = getClassDisplayName(data.getClassName());
        int level = data.getLevel();
        
        int strength = data.getStats().getOrDefault("strength", 0);
        int dexterity = data.getStats().getOrDefault("dexterity", 0);
        int intelligence = data.getStats().getOrDefault("intelligence", 0);
        
        double health = player.getHealth();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        
        // Формируем текст
        Component actionBar = Component.text()
            .append(Component.text("❤ ", NamedTextColor.RED))
            .append(Component.text((int)health + "/" + (int)maxHealth, NamedTextColor.WHITE))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(className + " ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("Ур." + level, NamedTextColor.YELLOW))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚔ ", NamedTextColor.RED))
            .append(Component.text(strength, NamedTextColor.WHITE))
            .append(Component.text(" ➹ ", NamedTextColor.GREEN))
            .append(Component.text(dexterity, NamedTextColor.WHITE))
            .append(Component.text(" ✦ ", NamedTextColor.AQUA))
            .append(Component.text(intelligence, NamedTextColor.WHITE))
            .build();
        
        player.sendActionBar(actionBar);
    }
    
    /**
     * Обновить BossBar (полоса опыта)
     */
    private void updateBossBar(Player player, PlayerData data) {
        BossBar bossBar = playerBossBars.get(player.getUniqueId());
        
        if (bossBar == null) {
            bossBar = BossBar.bossBar(
                Component.text("Опыт"),
                0.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
            );
            playerBossBars.put(player.getUniqueId(), bossBar);
            player.showBossBar(bossBar);
        }
        
        // Рассчитываем прогресс опыта
        int currentExp = data.getExperience();
        int requiredExp = calculateRequiredExp(data.getLevel());
        float progress = Math.min(1.0f, (float) currentExp / requiredExp);
        
        // Обновляем BossBar
        Component title = Component.text()
            .append(Component.text("Опыт: ", NamedTextColor.GRAY))
            .append(Component.text(currentExp + " / " + requiredExp, NamedTextColor.WHITE))
            .append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(Component.text((int)(progress * 100) + "%", NamedTextColor.YELLOW))
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .build();
        
        bossBar.name(title);
        bossBar.progress(progress);
        
        // Меняем цвет в зависимости от прогресса
        if (progress < 0.33f) {
            bossBar.color(BossBar.Color.RED);
        } else if (progress < 0.66f) {
            bossBar.color(BossBar.Color.YELLOW);
        } else {
            bossBar.color(BossBar.Color.GREEN);
        }
    }
    
    /**
     * Удалить HUD игрока
     */
    public void removeHUD(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }
    
    /**
     * Получить отображаемое название класса
     */
    private String getClassDisplayName(String className) {
        if (className == null || className.isEmpty()) {
            return "Без класса";
        }
        
        switch (className.toLowerCase()) {
            case "warrior": return "Воин";
            case "archer": return "Лучник";
            case "mage": return "Маг";
            default: return className;
        }
    }
    
    /**
     * Рассчитать требуемый опыт для уровня
     */
    private int calculateRequiredExp(int level) {
        // Формула: 100 * 1.5^(level-1)
        return (int) (100 * Math.pow(1.5, level - 1));
    }
}
