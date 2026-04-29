package ru.eclipsia.items.affix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Менеджер аффиксов
 */
public class AffixManager {
    
    private final Plugin plugin;
    private final Map<String, Affix> prefixes;
    private final Map<String, Affix> suffixes;
    
    public AffixManager(Plugin plugin) {
        this.plugin = plugin;
        this.prefixes = new HashMap<>();
        this.suffixes = new HashMap<>();
    }
    
    /**
     * Загрузить аффиксы из конфига
     */
    public void loadAffixes() {
        prefixes.clear();
        suffixes.clear();
        
        File affixesFile = new File(plugin.getDataFolder(), "affixes.yml");
        if (!affixesFile.exists()) {
            plugin.getLogger().severe("Файл affixes.yml не найден!");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(affixesFile);
        
        // Загрузка префиксов
        ConfigurationSection prefixSection = config.getConfigurationSection("prefixes");
        if (prefixSection != null) {
            for (String key : prefixSection.getKeys(false)) {
                try {
                    Affix affix = loadAffix(key, prefixSection.getConfigurationSection(key), AffixType.PREFIX);
                    prefixes.put(key, affix);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки префикса: " + key, e);
                }
            }
        }
        
        // Загрузка суффиксов
        ConfigurationSection suffixSection = config.getConfigurationSection("suffixes");
        if (suffixSection != null) {
            for (String key : suffixSection.getKeys(false)) {
                try {
                    Affix affix = loadAffix(key, suffixSection.getConfigurationSection(key), AffixType.SUFFIX);
                    suffixes.put(key, affix);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки суффикса: " + key, e);
                }
            }
        }
        
        plugin.getLogger().info("Загружено префиксов: " + prefixes.size());
        plugin.getLogger().info("Загружено суффиксов: " + suffixes.size());
    }
    
    private Affix loadAffix(String id, ConfigurationSection section, AffixType type) {
        String name = section.getString("name", id);
        int tier = section.getInt("tier", 1);
        int minValue = section.getInt("min-value", 1);
        int maxValue = section.getInt("max-value", 10);
        List<String> itemTypes = section.getStringList("item-types");
        int minItemLevel = section.getInt("min-item-level", 1);
        
        return new Affix(id, name, type, tier, minValue, maxValue, itemTypes, minItemLevel);
    }
    
    /**
     * Получить префикс по ID
     */
    public Affix getPrefix(String id) {
        return prefixes.get(id);
    }
    
    /**
     * Получить суффикс по ID
     */
    public Affix getSuffix(String id) {
        return suffixes.get(id);
    }
    
    /**
     * Получить все префиксы
     */
    public Collection<Affix> getAllPrefixes() {
        return prefixes.values();
    }
    
    /**
     * Получить все суффиксы
     */
    public Collection<Affix> getAllSuffixes() {
        return suffixes.values();
    }
    
    /**
     * Получить случайные аффиксы для предмета
     */
    public List<Affix> getRandomAffixes(String itemType, int itemLevel, int count) {
        List<Affix> result = new ArrayList<>();
        
        // Получаем подходящие префиксы и суффиксы
        List<Affix> availablePrefixes = prefixes.values().stream()
            .filter(a -> a.canApplyTo(itemType) && a.isValidForLevel(itemLevel))
            .collect(Collectors.toList());
        
        List<Affix> availableSuffixes = suffixes.values().stream()
            .filter(a -> a.canApplyTo(itemType) && a.isValidForLevel(itemLevel))
            .collect(Collectors.toList());
        
        // Распределяем аффиксы (примерно 50/50 префиксы/суффиксы)
        int prefixCount = count / 2;
        int suffixCount = count - prefixCount;
        
        // Добавляем случайные префиксы
        Collections.shuffle(availablePrefixes);
        for (int i = 0; i < Math.min(prefixCount, availablePrefixes.size()); i++) {
            result.add(availablePrefixes.get(i));
        }
        
        // Добавляем случайные суффиксы
        Collections.shuffle(availableSuffixes);
        for (int i = 0; i < Math.min(suffixCount, availableSuffixes.size()); i++) {
            result.add(availableSuffixes.get(i));
        }
        
        return result;
    }
    
    /**
     * Получить общее количество аффиксов
     */
    public int getAffixCount() {
        return prefixes.size() + suffixes.size();
    }
}
