package ru.eclipsia.items.rarity;

import org.bukkit.ChatColor;

/**
 * Редкость предмета.
 *
 * <p>Связь редкость → состав аффиксов вынесена сюда, чтобы и
 * {@code AffixManager} (фильтр пула), и {@code ItemGenerator}
 * (бюджет на ролл) брали правила из одного места.
 */
public enum ItemRarity {

    NORMAL("Обычный",     ChatColor.WHITE,  0,  0, 0, 0),
    MAGIC ("Магический",  ChatColor.BLUE,   1,  4, 1, 1),
    RARE  ("Редкий",      ChatColor.YELLOW, 3, 14, 3, 3),
    UNIQUE("Уникальный",  ChatColor.GOLD,   99, 0, 6, 6);

    private final String displayName;
    private final ChatColor color;
    private final int maxAffixTier;
    private final int affixBudget;
    private final int maxPrefixes;
    private final int maxSuffixes;

    ItemRarity(String displayName, ChatColor color,
               int maxAffixTier, int affixBudget,
               int maxPrefixes, int maxSuffixes) {
        this.displayName = displayName;
        this.color = color;
        this.maxAffixTier = maxAffixTier;
        this.affixBudget = affixBudget;
        this.maxPrefixes = maxPrefixes;
        this.maxSuffixes = maxSuffixes;
    }

    public String getDisplayName() { return displayName; }
    public ChatColor getColor()    { return color; }

    /** Максимальный тир аффикса, который может выпасть на этой редкости. */
    public int getMaxAffixTier() { return maxAffixTier; }

    /**
     * Очки бюджета на ролл аффиксов. T1 стоит 1 очко, T2 — 2, T3 — 4, T4 — 7
     * (см. {@link #affixCost(int)}). Ролл прекращается, когда бюджет
     * исчерпан или достигнуты лимиты префиксов/суффиксов.
     */
    public int getAffixBudget() { return affixBudget; }

    public int getMaxPrefixes() { return maxPrefixes; }
    public int getMaxSuffixes() { return maxSuffixes; }

    /** Стоимость аффикса для бюджета редкости. */
    public static int affixCost(int tier) {
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 7;
            default -> Math.max(1, tier);
        };
    }

    public String getColoredName() {
        return color + displayName;
    }

    public static ItemRarity fromString(String name) {
        for (ItemRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return NORMAL;
    }
}
