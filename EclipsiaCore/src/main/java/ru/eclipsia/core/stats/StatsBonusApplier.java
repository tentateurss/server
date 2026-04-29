package ru.eclipsia.core.stats;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;

import java.util.UUID;

/**
 * Применение бонусов от характеристик игрока
 * Баланс вдохновлен Path of Exile
 */
public class StatsBonusApplier {
    
    private static final String MODIFIER_NAME = "eclipsia_stats";
    
    // Баланс статов (как в PoE)
    // Сила: 1 сила = 0.5 урона, 0.5 здоровья
    private static final double STRENGTH_TO_MELEE_DAMAGE = 0.5; // 0.5 урона за 1 силу (увеличено с 0.1)
    private static final double STRENGTH_TO_HEALTH = 0.5; // 0.5 HP за 1 силу
    
    // Ловкость: 1 ловкость = 0.3 урона от луков, 0.02% шанс уклонения
    private static final double DEXTERITY_TO_BOW_DAMAGE = 0.3; // 0.3 урона за 1 ловкость (увеличено с 0.08)
    private static final double DEXTERITY_TO_EVASION = 0.0002; // 0.02% за 1 ловкость (макс 75%)
    
    // Интеллект: 1 интеллект = 0.5 магического урона, 2 маны
    private static final double INTELLIGENCE_TO_SPELL_DAMAGE = 0.5; // 0.5 урона за 1 интеллект (увеличено с 0.12)
    private static final double INTELLIGENCE_TO_MANA = 2.0; // 2 маны за 1 интеллект
    
    /**
     * Применить все бонусы от статов игрока
     */
    public static void applyAllBonuses(Player player) {
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        if (data == null) return;
        
        // Удаляем старые модификаторы
        removeAllBonuses(player);
        
        // Получаем статы
        int strength = data.getStat("strength");
        int dexterity = data.getStat("dexterity");
        int intelligence = data.getStat("intelligence");
        
        // Применяем бонусы от силы
        if (strength > 0) {
            applyStrengthBonuses(player, strength);
        }
        
        // Применяем бонусы от ловкости
        if (dexterity > 0) {
            applyDexterityBonuses(player, dexterity);
        }
        
        // Применяем бонусы от интеллекта
        if (intelligence > 0) {
            applyIntelligenceBonuses(player, intelligence);
        }

        // Пересчитываем максимум Эгиды (intelligence × 2 + экипировка + перки).
        AegisManager.recompute(player);
    }
    
    /**
     * Применить бонусы от силы
     */
    private static void applyStrengthBonuses(Player player, int strength) {
        // Здоровье от силы
        double healthBonus = strength * STRENGTH_TO_HEALTH;
        applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, "health", healthBonus, AttributeModifier.Operation.ADD_NUMBER);
        
        // Урон ближнего боя (прямое добавление урона)
        double meleeDamageBonus = strength * STRENGTH_TO_MELEE_DAMAGE;
        applyAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, "melee_damage", meleeDamageBonus, AttributeModifier.Operation.ADD_NUMBER);
        
        // Восстанавливаем здоровье если нужно
        AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null && player.getHealth() > healthAttr.getValue()) {
            player.setHealth(healthAttr.getValue());
        }
    }
    
    /**
     * Применить бонусы от ловкости
     */
    private static void applyDexterityBonuses(Player player, int dexterity) {
        // Урон от луков будет применяться в слушателе EntityDamageByEntityEvent
        // Здесь только сохраняем значение для использования
        
        // Шанс уклонения сохраняем в PDC для использования в слушателе
        double evasionChance = Math.min(dexterity * DEXTERITY_TO_EVASION, 0.75); // Макс 75%
        player.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaCore"), "evasion_chance"),
            org.bukkit.persistence.PersistentDataType.DOUBLE,
            evasionChance
        );
    }
    
    /**
     * Применить бонусы от интеллекта
     */
    private static void applyIntelligenceBonuses(Player player, int intelligence) {
        // Магический урон будет применяться в слушателе EntityDamageByEntityEvent
        // Мана - пока не реализована, оставляем для будущего
        
        // Сохраняем бонус магического урона в PDC
        double spellDamageBonus = intelligence * INTELLIGENCE_TO_SPELL_DAMAGE;
        player.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaCore"), "spell_damage_bonus"),
            org.bukkit.persistence.PersistentDataType.DOUBLE,
            spellDamageBonus
        );
    }
    
    /**
     * Применить модификатор атрибута
     */
    private static void applyAttributeModifier(Player player, Attribute attribute, String suffix, double value, AttributeModifier.Operation operation) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME + "_" + suffix).getBytes());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            MODIFIER_NAME + "_" + suffix,
            value,
            operation
        );
        
        attr.addModifier(modifier);
    }
    
    /**
     * Удалить все бонусы от статов
     */
    public static void removeAllBonuses(Player player) {
        removeAttributeModifiers(player, Attribute.GENERIC_MAX_HEALTH);
        removeAttributeModifiers(player, Attribute.GENERIC_ATTACK_DAMAGE);
        removeAttributeModifiers(player, Attribute.GENERIC_ARMOR);
    }
    
    /**
     * Удалить модификаторы атрибута
     */
    private static void removeAttributeModifiers(Player player, Attribute attribute) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        
        attr.getModifiers().stream()
            .filter(mod -> mod.getName().startsWith(MODIFIER_NAME))
            .forEach(attr::removeModifier);
    }
    
    /**
     * Получить бонус урона от луков для игрока
     */
    public static double getBowDamageBonus(Player player) {
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        if (data == null) return 0.0;
        
        int dexterity = data.getStat("dexterity");
        return dexterity * DEXTERITY_TO_BOW_DAMAGE;
    }
    
    /**
     * Получить шанс уклонения для игрока
     */
    public static double getEvasionChance(Player player) {
        try {
            Double evasion = player.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaCore"), "evasion_chance"),
                org.bukkit.persistence.PersistentDataType.DOUBLE
            );
            return evasion != null ? evasion : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Получить бонус магического урона для игрока
     */
    public static double getSpellDamageBonus(Player player) {
        try {
            Double bonus = player.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaCore"), "spell_damage_bonus"),
                org.bukkit.persistence.PersistentDataType.DOUBLE
            );
            return bonus != null ? bonus : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
