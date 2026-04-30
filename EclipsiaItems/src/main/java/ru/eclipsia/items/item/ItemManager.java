package ru.eclipsia.items.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Менеджер базовых предметов
 */
public class ItemManager {
    
    private final Plugin plugin;
    private final Map<String, BaseItem> items;
    
    public ItemManager(Plugin plugin) {
        this.plugin = plugin;
        this.items = new HashMap<>();
    }
    
    /**
     * Загрузить предметы из конфига
     */
    public void loadItems() {
        items.clear();
        
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.getLogger().severe("Файл items.yml не найден!");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        
        // Загрузка оружия
        loadItemCategory(config, "weapons");
        
        // Загрузка брони
        loadItemCategory(config, "armor");
        
        // Загрузка аксессуаров
        loadItemCategory(config, "accessories");
        
        plugin.getLogger().info("Загружено базовых предметов: " + items.size());
    }
    
    private void loadItemCategory(FileConfiguration config, String category) {
        ConfigurationSection section = config.getConfigurationSection(category);
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection itemSection = section.getConfigurationSection(key);
                BaseItem item = loadItem(key, itemSection);
                items.put(key, item);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки предмета: " + key, e);
            }
        }
    }
    
    private BaseItem loadItem(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        Material material = Material.valueOf(section.getString("material", "STONE"));
        ItemSlot slot = ItemSlot.fromString(section.getString("slot", "HAND"));
        int baseDamage = section.getInt("base-damage", 0);
        int baseArmor = section.getInt("base-armor", 0);
        // Базовые блок-параметры. Имеют смысл только для щитов; для прочих
        // предметов остаются 0 и в лоре не отображаются.
        int baseBlockChance = section.getInt("base-block-chance", 0);
        int baseBlockAmount = section.getInt("base-block-amount", 0);
        String requiredClass = section.getString("required-class", "");
        int minLevel = section.getInt("min-level", 1);

        return new BaseItem(id, name, material, slot,
                baseDamage, baseArmor, baseBlockChance, baseBlockAmount,
                requiredClass, minLevel);
    }
    
    /**
     * Получить предмет по ID
     */
    public BaseItem getItem(String id) {
        return items.get(id);
    }
    
    /**
     * Получить все предметы
     */
    public Collection<BaseItem> getAllItems() {
        return items.values();
    }
    
    /**
     * Получить случайный предмет для уровня
     */
    public BaseItem getRandomItem(int itemLevel) {
        List<BaseItem> available = new ArrayList<>();
        
        for (BaseItem item : items.values()) {
            if (item.getMinLevel() <= itemLevel) {
                available.add(item);
            }
        }
        
        if (available.isEmpty()) {
            return null;
        }
        
        return available.get(new Random().nextInt(available.size()));
    }
    
    /**
     * Получить случайный предмет для класса и уровня
     */
    public BaseItem getRandomItemForClass(String playerClass, int itemLevel) {
        List<BaseItem> available = new ArrayList<>();
        
        for (BaseItem item : items.values()) {
            if (item.getMinLevel() <= itemLevel) {
                // Если у предмета нет требования к классу или класс совпадает
                if (item.getRequiredClass().isEmpty() || 
                    item.getRequiredClass().equalsIgnoreCase(playerClass)) {
                    available.add(item);
                }
            }
        }
        
        if (available.isEmpty()) {
            return null;
        }
        
        return available.get(new Random().nextInt(available.size()));
    }
    
    /**
     * Получить количество предметов
     */
    public int getItemCount() {
        return items.size();
    }
}
