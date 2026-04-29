package ru.eclipsia.items.rarity;

import org.bukkit.ChatColor;

/**
 * Редкость предмета
 */
public enum ItemRarity {
    
    NORMAL("Обычный", ChatColor.WHITE, 0),
    MAGIC("Магический", ChatColor.BLUE, 2),
    RARE("Редкий", ChatColor.YELLOW, 6),
    UNIQUE("Уникальный", ChatColor.GOLD, -1);
    
    private final String displayName;
    private final ChatColor color;
    private final int maxAffixes;
    
    ItemRarity(String displayName, ChatColor color, int maxAffixes) {
        this.displayName = displayName;
        this.color = color;
        this.maxAffixes = maxAffixes;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public ChatColor getColor() {
        return color;
    }
    
    public int getMaxAffixes() {
        return maxAffixes;
    }
    
    /**
     * Получить цветное название
     */
    public String getColoredName() {
        return color + displayName;
    }
    
    /**
     * Получить редкость по названию
     */
    public static ItemRarity fromString(String name) {
        for (ItemRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return NORMAL;
    }
}
