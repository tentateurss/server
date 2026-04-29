package ru.eclipsia.mobs.mob;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс кастомного моба
 */
public class CustomMob {
    
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final int level;
    private final double health;
    private final double damage;
    private final double armor;
    private final int experience;
    private final Map<String, ItemStack> equipment;
    private final DropConfig drops;
    private final List<String> spawnZones;
    
    public CustomMob(String id, ConfigurationSection config) {
        this.id = id;
        this.displayName = config.getString("display-name", id);
        this.entityType = EntityType.valueOf(config.getString("entity-type", "ZOMBIE"));
        this.level = config.getInt("level", 1);
        this.health = config.getDouble("health", 20.0);
        this.damage = config.getDouble("damage", 5.0);
        this.armor = config.getDouble("armor", 0.0);
        this.experience = config.getInt("experience", 10);
        this.equipment = loadEquipment(config.getConfigurationSection("equipment"));
        this.drops = new DropConfig(config.getConfigurationSection("drops"));
        this.spawnZones = config.getStringList("spawn-zones");
    }
    
    private Map<String, ItemStack> loadEquipment(ConfigurationSection section) {
        Map<String, ItemStack> eq = new HashMap<>();
        if (section == null) return eq;
        
        for (String slot : section.getKeys(false)) {
            String materialName = section.getString(slot);
            try {
                Material material = Material.valueOf(materialName);
                eq.put(slot, new ItemStack(material));
            } catch (IllegalArgumentException e) {
                // Игнорируем неверные материалы
            }
        }
        
        return eq;
    }
    
    /**
     * Применить характеристики к сущности
     */
    public void applyToEntity(LivingEntity entity) {
        // Устанавливаем здоровье
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        entity.setHealth(health);
        
        // Устанавливаем урон
        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        }
        
        // Устанавливаем броню
        if (entity.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            entity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(armor);
        }
        
        // Устанавливаем имя с HP
        updateHealthDisplay(entity);
        
        // Экипируем
        applyEquipment(entity);
        
        // Запрещаем деспавн
        entity.setRemoveWhenFarAway(false);
    }
    
    /**
     * Обновить отображение HP
     */
    public void updateHealthDisplay(LivingEntity entity) {
        double currentHealth = entity.getHealth();
        double maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        
        // Рассчитываем процент HP
        double percent = currentHealth / maxHealth;
        
        // Выбираем цвет в зависимости от HP
        String healthColor;
        if (percent > 0.75) {
            healthColor = "§a"; // Зеленый
        } else if (percent > 0.5) {
            healthColor = "§e"; // Желтый
        } else if (percent > 0.25) {
            healthColor = "§6"; // Оранжевый
        } else {
            healthColor = "§c"; // Красный
        }
        
        // Создаем HP бар из символов
        int totalBars = 20;
        int filledBars = (int) (percent * totalBars);
        
        StringBuilder hpBar = new StringBuilder("§8[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                hpBar.append(healthColor).append("|");
            } else {
                hpBar.append("§7|");
            }
        }
        hpBar.append("§8]");
        
        // Устанавливаем имя моба с HP
        String displayName = this.displayName + " §7[Ур. " + level + "] " +
                            hpBar + " " + healthColor + (int)currentHealth + "§8/§7" + (int)maxHealth;
        
        entity.setCustomName(displayName);
        entity.setCustomNameVisible(true);
    }
    
    private void applyEquipment(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        
        if (equipment.containsKey("helmet")) {
            ItemStack helmet = equipment.get("helmet").clone();
            makeUnbreakable(helmet);
            eq.setHelmet(helmet);
            eq.setHelmetDropChance(0.0f);
        }
        if (equipment.containsKey("chestplate")) {
            ItemStack chestplate = equipment.get("chestplate").clone();
            makeUnbreakable(chestplate);
            eq.setChestplate(chestplate);
            eq.setChestplateDropChance(0.0f);
        }
        if (equipment.containsKey("leggings")) {
            ItemStack leggings = equipment.get("leggings").clone();
            makeUnbreakable(leggings);
            eq.setLeggings(leggings);
            eq.setLeggingsDropChance(0.0f);
        }
        if (equipment.containsKey("boots")) {
            ItemStack boots = equipment.get("boots").clone();
            makeUnbreakable(boots);
            eq.setBoots(boots);
            eq.setBootsDropChance(0.0f);
        }
        if (equipment.containsKey("mainhand")) {
            ItemStack mainhand = equipment.get("mainhand").clone();
            makeUnbreakable(mainhand);
            eq.setItemInMainHand(mainhand);
            eq.setItemInMainHandDropChance(0.0f);
        }
        if (equipment.containsKey("offhand")) {
            ItemStack offhand = equipment.get("offhand").clone();
            makeUnbreakable(offhand);
            eq.setItemInOffHand(offhand);
            eq.setItemInOffHandDropChance(0.0f);
        }
    }
    
    /**
     * Сделать предмет неразрушимым
     */
    private void makeUnbreakable(ItemStack item) {
        if (item == null) return;
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public int getLevel() { return level; }
    public double getHealth() { return health; }
    public double getDamage() { return damage; }
    public double getArmor() { return armor; }
    public int getExperience() { return experience; }
    public DropConfig getDrops() { return drops; }
    public List<String> getSpawnZones() { return spawnZones; }
    
    /**
     * Конфигурация дропа
     */
    public static class DropConfig {
        private final int minOrbs;
        private final int maxOrbs;
        private final int orbChance;
        
        public DropConfig(ConfigurationSection section) {
            if (section != null && section.contains("orbs")) {
                ConfigurationSection orbs = section.getConfigurationSection("orbs");
                this.minOrbs = orbs.getInt("min", 1);
                this.maxOrbs = orbs.getInt("max", 5);
                this.orbChance = orbs.getInt("chance", 100);
            } else {
                this.minOrbs = 1;
                this.maxOrbs = 5;
                this.orbChance = 100;
            }
        }
        
        public int getMinOrbs() { return minOrbs; }
        public int getMaxOrbs() { return maxOrbs; }
        public int getOrbChance() { return orbChance; }
    }
}
