package ru.eclipsia.mobs.experience;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerData;

/**
 * Менеджер системы опыта и уровней
 */
public class ExperienceManager {
    
    private static ExperienceManager instance;
    
    private final Plugin plugin;
    private final EclipsiaAPI coreAPI;
    
    private int expPerLevelBase;
    private double expMultiplier;
    private int maxLevel;
    private int statPointsPerLevel;
    private boolean showExpMessages;
    private boolean showExpActionBar;
    
    private ExperienceManager(Plugin plugin) {
        this.plugin = plugin;
        this.coreAPI = EclipsiaAPI.getInstance();
        loadConfig();
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("ExperienceManager уже инициализирован!");
        }
        instance = new ExperienceManager(plugin);
    }
    
    public static ExperienceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ExperienceManager не инициализирован!");
        }
        return instance;
    }
    
    private void loadConfig() {
        expPerLevelBase = plugin.getConfig().getInt("experience.exp-per-level-base", 100);
        expMultiplier = plugin.getConfig().getDouble("experience.exp-multiplier", 1.5);
        maxLevel = plugin.getConfig().getInt("experience.max-level", 100);
        statPointsPerLevel = plugin.getConfig().getInt("experience.stat-points-per-level", 1);
        showExpMessages = plugin.getConfig().getBoolean("experience.show-exp-messages", true);
        // По умолчанию false — единый ActionBar пишет HUDActionBarListener
        // (HP/Эгида/Мана + статы). Прогресс опыта виден в BossBar (PlayerHUDManager).
        showExpActionBar = plugin.getConfig().getBoolean("experience.show-exp-actionbar", false);
    }
    
    /**
     * Добавить опыт игроку
     */
    public void addExperience(Player player, int amount) {
        PlayerData data = coreAPI.getPlayerData(player);
        if (data == null) return;
        
        int currentLevel = data.getLevel();
        int currentExp = data.getExperience();
        int newExp = currentExp + amount;
        
        // Показываем сообщение
        if (showExpMessages) {
            player.sendMessage("§a+ " + amount + " опыта");
        }
        
        // Проверяем повышение уровня
        int expNeeded = getExpForLevel(currentLevel + 1);
        
        if (newExp >= expNeeded && currentLevel < maxLevel) {
            // Повышаем уровень
            levelUp(player, data, currentLevel + 1, newExp - expNeeded);
        } else {
            // Просто добавляем опыт
            PlayerData updated = data.toBuilder()
                    .experience(newExp)
                    .build();
            coreAPI.savePlayerData(updated);
            
            // Показываем прогресс в action bar
            if (showExpActionBar) {
                showExpProgress(player, newExp, expNeeded);
            }
        }
    }
    
    /**
     * Повышение уровня
     */
    private void levelUp(Player player, PlayerData data, int newLevel, int remainingExp) {
        // Обновляем данные
        PlayerData updated = data.toBuilder()
                .level(newLevel)
                .experience(remainingExp)
                .freeStatPoints(data.getFreeStatPoints() + statPointsPerLevel)
                .build();
        
        coreAPI.savePlayerData(updated);
        
        // ЭТАП: +1 очко перков КАЖДЫЙ уровень (стиль PoE)
        givePerkPoint(player);
        
        // Эффекты
        player.sendTitle("§6§lПОВЫШЕНИЕ УРОВНЯ!", "§eУровень " + newLevel, 10, 40, 10);
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l                    ПОВЫШЕНИЕ УРОВНЯ!");
        player.sendMessage("");
        player.sendMessage("§7                    Новый уровень: §e" + newLevel);
        player.sendMessage("§7                    Свободных очков: §a+" + statPointsPerLevel);
        
        // Сообщение о получении очка перков (теперь каждый уровень)
        player.sendMessage("§7                    Очков перков: §d+1");
        player.sendMessage("");
        player.sendMessage("§7                    Используйте §f/perks §7для прокачки навыков");
        
        player.sendMessage("");
        player.sendMessage("§7                    Используйте §f/stats §7для распределения");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        // Проверяем следующий уровень
        int nextExpNeeded = getExpForLevel(newLevel + 1);
        if (remainingExp >= nextExpNeeded && newLevel < maxLevel) {
            // Рекурсивно повышаем уровень если хватает опыта
            levelUp(player, updated, newLevel + 1, remainingExp - nextExpNeeded);
        }
    }
    
    /**
     * Показать прогресс опыта в action bar
     */
    private void showExpProgress(Player player, int currentExp, int expNeeded) {
        double progress = (double) currentExp / expNeeded;
        int bars = 20;
        int filled = (int) (progress * bars);
        
        StringBuilder progressBar = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                progressBar.append("§a■");
            } else {
                progressBar.append("§8■");
            }
        }
        progressBar.append("§7] §e").append(currentExp).append("§7/§e").append(expNeeded);
        
        player.sendActionBar(progressBar.toString());
    }
    
    /**
     * Рассчитать опыт необходимый для уровня
     */
    public int getExpForLevel(int level) {
        if (level <= 1) return 0;
        return (int) (expPerLevelBase * Math.pow(expMultiplier, level - 2));
    }
    
    /**
     * Получить текущий прогресс до следующего уровня (0.0 - 1.0)
     */
    public double getLevelProgress(Player player) {
        PlayerData data = coreAPI.getPlayerData(player);
        if (data == null) return 0.0;
        
        int currentExp = data.getExperience();
        int expNeeded = getExpForLevel(data.getLevel() + 1);
        
        return (double) currentExp / expNeeded;
    }
    
    /**
     * Установить уровень игроку
     */
    public void setLevel(Player player, int level) {
        if (level < 1 || level > maxLevel) return;
        
        PlayerData data = coreAPI.getPlayerData(player);
        if (data == null) return;
        
        int levelDiff = level - data.getLevel();
        int statPoints = data.getFreeStatPoints() + (levelDiff * statPointsPerLevel);
        
        PlayerData updated = data.toBuilder()
                .level(level)
                .experience(0)
                .freeStatPoints(Math.max(0, statPoints))
                .build();
        
        coreAPI.savePlayerData(updated);
        
        player.sendMessage("§aВаш уровень установлен на " + level);
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Выдать +1 очко перков игроку (через рефлексию, чтобы не было прямой
     * зависимости от EclipsiaPerks). Вызывается при каждом повышении уровня.
     */
    private void givePerkPoint(Player player) {
        Plugin perksPlugin = plugin.getServer().getPluginManager().getPlugin("EclipsiaPerks");
        if (perksPlugin == null || !perksPlugin.isEnabled()) return;

        try {
            Class<?> perksClass = Class.forName("ru.eclipsia.perks.EclipsiaPerks");
            Object perksInstance = perksClass.getMethod("getInstance").invoke(null);
            Object playerManager = perksClass.getMethod("getPlayerManager").invoke(perksInstance);

            // Берём (или создаём) PlayerPerkData
            Object perkData = playerManager.getClass()
                    .getMethod("getPlayerData", java.util.UUID.class)
                    .invoke(playerManager, player.getUniqueId());

            // addPoints(+1) — добавляет, не пересчитывает
            perkData.getClass().getMethod("addPoints", int.class)
                    .invoke(perkData, 1);

            // Сохраняем
            playerManager.getClass()
                    .getMethod("savePlayerData", java.util.UUID.class)
                    .invoke(playerManager, player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось выдать очко перков игроку "
                    + player.getName() + ": " + e.getMessage());
        }
    }
}
