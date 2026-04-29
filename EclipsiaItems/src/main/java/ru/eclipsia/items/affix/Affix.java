package ru.eclipsia.items.affix;

import org.bukkit.Material;

import java.util.List;

/**
 * Аффикс предмета (префикс или суффикс)
 */
public class Affix {
    
    private final String id;
    private final String name;
    private final AffixType type;
    private final int tier;
    private final int minValue;
    private final int maxValue;
    private final List<String> itemTypes;
    private final int minItemLevel;
    
    public Affix(String id, String name, AffixType type, int tier, int minValue, int maxValue, 
                 List<String> itemTypes, int minItemLevel) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.tier = tier;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.itemTypes = itemTypes;
        this.minItemLevel = minItemLevel;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public AffixType getType() {
        return type;
    }
    
    public int getTier() {
        return tier;
    }
    
    public int getMinValue() {
        return minValue;
    }
    
    public int getMaxValue() {
        return maxValue;
    }
    
    public List<String> getItemTypes() {
        return itemTypes;
    }
    
    public int getMinItemLevel() {
        return minItemLevel;
    }
    
    /**
     * Проверить может ли аффикс быть применен к типу предмета
     */
    public boolean canApplyTo(String itemType) {
        return itemTypes.contains(itemType);
    }
    
    /**
     * Проверить подходит ли аффикс для уровня предмета
     */
    public boolean isValidForLevel(int itemLevel) {
        return itemLevel >= minItemLevel;
    }
    
    /**
     * Получить случайное значение в диапазоне
     */
    public int rollValue() {
        return minValue + (int) (Math.random() * (maxValue - minValue + 1));
    }
    
    /**
     * Получить описание аффикса с значением
     */
    public String getDescription(int value) {
        String statName = getStatName();
        return "§7" + name + " (" + statName + ": §f+" + value + "§7)";
    }
    
    /**
     * Получить название характеристики
     */
    private String getStatName() {
        String idLower = id.toLowerCase();
        if (idLower.contains("damage")) return "Урон";
        if (idLower.contains("health")) return "Здоровье";
        if (idLower.contains("armor")) return "Броня";
        if (idLower.contains("crit")) return "Крит. урон";
        if (idLower.contains("speed")) return "Скорость атаки";
        if (idLower.contains("resistance")) return "Сопротивление";
        if (idLower.contains("regen")) return "Регенерация";
        return "Характеристика";
    }
}
