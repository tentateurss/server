package ru.eclipsia.items.rarity;

import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Менеджер редкости предметов
 */
public class RarityManager {
    
    private final Plugin plugin;
    private final Random random;
    
    public RarityManager(Plugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }
    
    /**
     * Получить случайную редкость на основе шансов из конфига
     */
    public ItemRarity getRandomRarity() {
        int normalChance = plugin.getConfig().getInt("generation.rarity-chances.normal", 50);
        int magicChance = plugin.getConfig().getInt("generation.rarity-chances.magic", 30);
        int rareChance = plugin.getConfig().getInt("generation.rarity-chances.rare", 18);
        int uniqueChance = plugin.getConfig().getInt("generation.rarity-chances.unique", 2);
        
        int total = normalChance + magicChance + rareChance + uniqueChance;
        int roll = random.nextInt(total);
        
        if (roll < normalChance) {
            return ItemRarity.NORMAL;
        } else if (roll < normalChance + magicChance) {
            return ItemRarity.MAGIC;
        } else if (roll < normalChance + magicChance + rareChance) {
            return ItemRarity.RARE;
        } else {
            return ItemRarity.UNIQUE;
        }
    }
    
    /**
     * Получить количество аффиксов для редкости
     */
    public int getAffixCount(ItemRarity rarity) {
        if (rarity == ItemRarity.NORMAL) {
            return 0;
        } else if (rarity == ItemRarity.MAGIC) {
            int min = plugin.getConfig().getInt("generation.affixes.magic.min", 1);
            int max = plugin.getConfig().getInt("generation.affixes.magic.max", 2);
            return min + random.nextInt(max - min + 1);
        } else if (rarity == ItemRarity.RARE) {
            int min = plugin.getConfig().getInt("generation.affixes.rare.min", 3);
            int max = plugin.getConfig().getInt("generation.affixes.rare.max", 6);
            return min + random.nextInt(max - min + 1);
        }
        return 0; // Уникальные предметы имеют фиксированные аффиксы
    }
}
