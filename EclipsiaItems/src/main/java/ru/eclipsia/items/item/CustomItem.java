package ru.eclipsia.items.item;

import ru.eclipsia.items.affix.Affix;
import ru.eclipsia.items.rarity.ItemRarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Кастомный предмет с аффиксами
 */
public class CustomItem {
    
    private final BaseItem baseItem;
    private final ItemRarity rarity;
    private final int itemLevel;
    private final Map<Affix, Integer> affixes; // Аффикс -> значение
    
    public CustomItem(BaseItem baseItem, ItemRarity rarity, int itemLevel) {
        this.baseItem = baseItem;
        this.rarity = rarity;
        this.itemLevel = itemLevel;
        this.affixes = new HashMap<>();
    }
    
    /**
     * Добавить аффикс с значением
     */
    public void addAffix(Affix affix, int value) {
        affixes.put(affix, value);
    }
    
    public BaseItem getBaseItem() {
        return baseItem;
    }
    
    public ItemRarity getRarity() {
        return rarity;
    }
    
    public int getItemLevel() {
        return itemLevel;
    }
    
    public Map<Affix, Integer> getAffixes() {
        return new HashMap<>(affixes);
    }
    
    /**
     * Получить полное название предмета
     */
    public String getFullName() {
        if (rarity == ItemRarity.NORMAL) {
            return rarity.getColor() + baseItem.getName();
        }
        
        // Для магических и редких предметов добавляем префикс/суффикс в название
        StringBuilder name = new StringBuilder();
        name.append(rarity.getColor());
        
        // Добавляем первый префикс в название
        for (Affix affix : affixes.keySet()) {
            if (affix.getType().name().equals("PREFIX")) {
                name.append(affix.getName()).append(" ");
                break;
            }
        }
        
        name.append(baseItem.getName());
        
        // Добавляем первый суффикс в название
        for (Affix affix : affixes.keySet()) {
            if (affix.getType().name().equals("SUFFIX")) {
                name.append(" ").append(affix.getName());
                break;
            }
        }
        
        return name.toString();
    }
    
    /**
     * Получить общий урон (базовый + бонусы)
     */
    public int getTotalDamage() {
        int total = baseItem.getBaseDamage();
        
        for (Map.Entry<Affix, Integer> entry : affixes.entrySet()) {
            if (entry.getKey().getId().contains("damage")) {
                total += entry.getValue();
            }
        }
        
        return total;
    }
    
    /**
     * Получить общую броню (базовая + бонусы)
     */
    public int getTotalArmor() {
        int total = baseItem.getBaseArmor();
        
        for (Map.Entry<Affix, Integer> entry : affixes.entrySet()) {
            if (entry.getKey().getId().contains("armor")) {
                total += entry.getValue();
            }
        }
        
        return total;
    }
    
    /**
     * Получить бонус здоровья
     */
    public int getHealthBonus() {
        int total = 0;
        
        for (Map.Entry<Affix, Integer> entry : affixes.entrySet()) {
            if (entry.getKey().getId().contains("health")) {
                total += entry.getValue();
            }
        }
        
        return total;
    }
    
    /**
     * Получить бонус критического урона
     */
    public int getCritBonus() {
        int total = 0;
        
        for (Map.Entry<Affix, Integer> entry : affixes.entrySet()) {
            if (entry.getKey().getId().contains("crit")) {
                total += entry.getValue();
            }
        }
        
        return total;
    }
    
    /**
     * Получить бонус скорости атаки
     */
    public int getSpeedBonus() {
        int total = 0;
        
        for (Map.Entry<Affix, Integer> entry : affixes.entrySet()) {
            if (entry.getKey().getId().contains("speed")) {
                total += entry.getValue();
            }
        }
        
        return total;
    }
}
