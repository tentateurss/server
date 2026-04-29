package ru.eclipsia.items.item;

import org.bukkit.Material;

/**
 * Базовый тип предмета
 */
public class BaseItem {
    
    private final String id;
    private final String name;
    private final Material material;
    private final ItemSlot slot;
    private final int baseDamage;
    private final int baseArmor;
    private final String requiredClass;
    private final int minLevel;
    
    public BaseItem(String id, String name, Material material, ItemSlot slot, 
                    int baseDamage, int baseArmor, String requiredClass, int minLevel) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.slot = slot;
        this.baseDamage = baseDamage;
        this.baseArmor = baseArmor;
        this.requiredClass = requiredClass;
        this.minLevel = minLevel;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public Material getMaterial() {
        return material;
    }
    
    public ItemSlot getSlot() {
        return slot;
    }
    
    public int getBaseDamage() {
        return baseDamage;
    }
    
    public int getBaseArmor() {
        return baseArmor;
    }
    
    public String getRequiredClass() {
        return requiredClass;
    }
    
    public int getMinLevel() {
        return minLevel;
    }
    
    /**
     * Получить тип предмета для аффиксов
     */
    public String getItemType() {
        if (slot == ItemSlot.HAND) {
            if (material.name().contains("SWORD") || material.name().contains("AXE")) {
                return "SWORD";
            } else if (material.name().contains("BOW")) {
                return "BOW";
            } else {
                return "STAFF";
            }
        } else if (slot == ItemSlot.HEAD) {
            return "HELMET";
        } else if (slot == ItemSlot.CHEST) {
            return "CHESTPLATE";
        } else if (slot == ItemSlot.LEGS) {
            return "LEGGINGS";
        } else if (slot == ItemSlot.FEET) {
            return "BOOTS";
        } else if (slot == ItemSlot.RING_1 || slot == ItemSlot.RING_2) {
            return "RING";
        } else if (slot == ItemSlot.AMULET) {
            return "AMULET";
        } else if (slot == ItemSlot.BELT) {
            return "BELT";
        }
        return "UNKNOWN";
    }
    
    /**
     * Является ли предмет оружием
     */
    public boolean isWeapon() {
        return baseDamage > 0;
    }
    
    /**
     * Является ли предмет броней
     */
    public boolean isArmor() {
        return baseArmor > 0;
    }
}
