package ru.eclipsia.core.classes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс персонажа с характеристиками из конфига
 */
public class PlayerClass {
    
    private final String id;
    private final String displayName;
    private final List<String> description;
    private final String icon;
    private final Map<String, Integer> baseStats;
    private final Map<String, Integer> statPerLevel;
    private final double startingHealth;
    private final double healthPerLevel;
    
    public PlayerClass(String id, ConfigurationSection config) {
        this.id = id;
        this.displayName = config.getString("display-name", id);
        this.description = config.getStringList("description");
        this.icon = config.getString("icon", "STONE");
        
        this.baseStats = new HashMap<>();
        ConfigurationSection baseStatsSection = config.getConfigurationSection("base-stats");
        if (baseStatsSection != null) {
            for (String key : baseStatsSection.getKeys(false)) {
                baseStats.put(key, baseStatsSection.getInt(key));
            }
        }
        
        this.statPerLevel = new HashMap<>();
        ConfigurationSection statPerLevelSection = config.getConfigurationSection("stat-per-level");
        if (statPerLevelSection != null) {
            for (String key : statPerLevelSection.getKeys(false)) {
                statPerLevel.put(key, statPerLevelSection.getInt(key));
            }
        }
        
        this.startingHealth = config.getDouble("starting-health", 20.0);
        this.healthPerLevel = config.getDouble("health-per-level", 1.0);
    }
    
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<String> getDescription() { return description; }
    public String getIcon() { return icon; }
    public Map<String, Integer> getBaseStats() { return new HashMap<>(baseStats); }
    public Map<String, Integer> getStatPerLevel() { return new HashMap<>(statPerLevel); }
    public double getStartingHealth() { return startingHealth; }
    public double getHealthPerLevel() { return healthPerLevel; }
    
    /**
     * Получить базовое значение стата
     */
    public int getBaseStat(String statName) {
        return baseStats.getOrDefault(statName, 0);
    }
    
    /**
     * Получить прирост стата за уровень
     */
    public int getStatGainPerLevel(String statName) {
        return statPerLevel.getOrDefault(statName, 0);
    }
    
    /**
     * Рассчитать здоровье для уровня
     */
    public double calculateHealth(int level) {
        return startingHealth + (healthPerLevel * (level - 1));
    }
    
    /**
     * Рассчитать стат для уровня (базовый + прирост за уровни)
     */
    public int calculateStat(String statName, int level) {
        int base = getBaseStat(statName);
        int perLevel = getStatGainPerLevel(statName);
        return base + (perLevel * (level - 1));
    }
}
