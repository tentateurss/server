package ru.eclipsia.perks.node;

import org.bukkit.ChatColor;

/**
 * Тип узла в дереве перков
 */
public enum NodeType {
    START("Стартовый", ChatColor.WHITE, 0),
    SMALL("Малый", ChatColor.GRAY, 1),
    MEDIUM("Средний", ChatColor.GREEN, 1),
    NOTABLE("Крупный", ChatColor.GOLD, 1),
    KEYSTONE("Ключевой", ChatColor.RED, 1);
    
    private final String displayName;
    private final ChatColor color;
    private final int cost;
    
    NodeType(String displayName, ChatColor color, int cost) {
        this.displayName = displayName;
        this.color = color;
        this.cost = cost;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public ChatColor getColor() {
        return color;
    }
    
    public int getCost() {
        return cost;
    }
}
