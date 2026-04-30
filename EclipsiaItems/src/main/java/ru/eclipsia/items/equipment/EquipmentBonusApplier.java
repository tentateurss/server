package ru.eclipsia.items.equipment;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.items.item.ItemSlot;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Применение бонусов от экипировки.
 *
 * <p>Бонусы парсятся из лора в двух категориях:
 * <ul>
 *   <li><b>Vanilla-атрибуты</b> ({@code Урон/Броня/Здоровье/Скорость атаки}) —
 *       применяются как Bukkit AttributeModifier;</li>
 *   <li><b>RPG-статы</b> ({@code Сила/Ловкость/Интеллект}) — НЕ применяются
 *       к Bukkit-атрибутам (их там нет). Запрашиваются на лету через
 *       {@link #getStatBonus(Player, String)} другими плагинами для
 *       расчёта урона навыков, регенерации маны и т.д.</li>
 * </ul>
 *
 * <p>Подход «pull» (а не push): мы не дублируем статы в PlayerProfile,
 * чтобы перк-дерево и экипировка оставались независимыми источниками,
 * не перезаписывающими друг друга при изменениях.
 */
public class EquipmentBonusApplier {
    
    private static final String MODIFIER_NAME = "eclipsia_equipment";
    
    /**
     * Применить все бонусы от экипировки игрока
     */
    public static void applyBonuses(Player player, PlayerEquipment equipment) {
        // Сначала удаляем все старые модификаторы
        removeAllBonuses(player);
        
        // Собираем все бонусы
        Map<String, Integer> totalBonuses = calculateTotalBonuses(equipment);
        
        // Применяем бонусы
        applyHealthBonus(player, totalBonuses.getOrDefault("health", 0));
        applyArmorBonus(player, totalBonuses.getOrDefault("armor", 0));
        applyDamageBonus(player, totalBonuses.getOrDefault("damage", 0)
                + totalBonuses.getOrDefault("physical_damage", 0));
        applySpeedBonus(player, totalBonuses.getOrDefault("speed", 0));

        // Пересчёт maxAegis (учитывает aegis с экипировки + перки + intelligence).
        try {
            ru.eclipsia.core.stats.AegisManager.recompute(player);
        } catch (Throwable ignored) {
            // EclipsiaCore может быть не загружен на момент init — это ок.
        }

        // Клампим currentMana ≤ newMaxMana — если игрок снял предмет с +mana
        // и его текущая мана оказалась выше нового максимума, нужно её срезать.
        // Сам максимум вычисляется на лету через ManaManager#getMaxMana — в
        // профиле обновлять не надо, чтобы не терять базовую ману класса.
        try {
            ru.eclipsia.core.stats.ManaManager.recompute(player);
        } catch (Throwable ignored) {
            // EclipsiaCore может быть не загружен на момент init — это ок.
        }
    }
    
    /**
     * Рассчитать суммарные бонусы от всей экипировки
     */
    private static Map<String, Integer> calculateTotalBonuses(PlayerEquipment equipment) {
        Map<String, Integer> bonuses = new HashMap<>();
        
        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack item = equipment.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasLore()) continue;
            
            // Парсим бонусы из лора
            for (String line : meta.getLore()) {
                parseBonusLine(line, bonuses);
            }
        }
        
        return bonuses;
    }
    
    /**
     * Парсить строку лора для извлечения бонусов.
     *
     * <p>Поддерживаются ВСЕ статы, которые умеют выдавать новые аффиксы и
     * базовые предметы: vanilla-атрибуты (Урон/Броня/Здоровье/Крит/Скорость),
     * базовые статы класса (Сила/Ловкость/Интеллект), стихийный урон и
     * резисты, уклонение/блок, эгида, реген, скорость бега, мана.
     *
     * <p>Парсер тупой: ищет подстроку «лейбл:» и берёт первое число справа.
     * Для процентных статов (резисты, шанс крита, скорость атаки и т.д.)
     * процент в значение не превращается — это нужно учитывать в
     * DamageCalculator (он и так берёт процент через {@link Math#min}).
     */
    private static void parseBonusLine(String line, Map<String, Integer> bonuses) {
        String cleaned = line.replaceAll("§.", "");
        String l = cleaned.toLowerCase();

        // ---- Vanilla-атрибуты (применяются ниже как AttributeModifier) ----
        if (l.contains("физ. урон:") || l.contains("физический урон:")) {
            bonuses.merge("physical_damage", extractValue(cleaned), Integer::sum);
        } else if (l.contains("урон огнём:") || l.contains("урон огнем:")) {
            bonuses.merge("fire_damage", extractValue(cleaned), Integer::sum);
        } else if (l.contains("урон холодом:")) {
            bonuses.merge("cold_damage", extractValue(cleaned), Integer::sum);
        } else if (l.contains("урон молнией:")) {
            bonuses.merge("lightning_damage", extractValue(cleaned), Integer::sum);
        } else if (l.contains("урон:")) {
            bonuses.merge("damage", extractValue(cleaned), Integer::sum);
        } else if (l.contains("броня:")) {
            int v = extractValue(cleaned);
            bonuses.merge("armor", v, Integer::sum);   // vanilla
            bonuses.merge("armour", v, Integer::sum);  // RPG-stat синоним
        } else if (l.contains("здоровье:")) {
            bonuses.merge("health", extractValue(cleaned), Integer::sum);
        } else if (l.contains("крит. урон:") || l.contains("крит урон:")) {
            int v = extractValue(cleaned);
            bonuses.merge("crit", v, Integer::sum);
            bonuses.merge("crit_damage", v, Integer::sum);
        } else if (l.contains("шанс крита:")) {
            bonuses.merge("crit_chance", extractValue(cleaned), Integer::sum);
        } else if (l.contains("скор. атаки:") || l.contains("скорость атаки:")) {
            int v = extractValue(cleaned);
            bonuses.merge("speed", v, Integer::sum);
            bonuses.merge("attack_speed", v, Integer::sum);
        } else if (l.contains("скор. бега:") || l.contains("скорость бега:")) {
            bonuses.merge("move_speed", extractValue(cleaned), Integer::sum);
        }
        // ---- Защиты ----
        else if (l.contains("сопр. огню:") || l.contains("сопротивление огню:")) {
            bonuses.merge("fire_resist", extractValue(cleaned), Integer::sum);
        } else if (l.contains("сопр. холоду:") || l.contains("сопротивление холоду:")) {
            bonuses.merge("cold_resist", extractValue(cleaned), Integer::sum);
        } else if (l.contains("сопр. молнии:") || l.contains("сопротивление молнии:")) {
            bonuses.merge("lightning_resist", extractValue(cleaned), Integer::sum);
        } else if (l.contains("уклонение:")) {
            bonuses.merge("evasion", extractValue(cleaned), Integer::sum);
            bonuses.merge("dodge",   extractValue(cleaned), Integer::sum);
        } else if (l.contains("шанс блока:")) {
            bonuses.merge("block_chance", extractValue(cleaned), Integer::sum);
        } else if (l.contains("сила блока:")) {
            bonuses.merge("block_amount", extractValue(cleaned), Integer::sum);
        }
        // ---- Эгида / реген / мана ----
        else if (l.contains("реген эгиды:")) {
            bonuses.merge("aegis_regen", extractValue(cleaned), Integer::sum);
        } else if (l.contains("эгида:")) {
            bonuses.merge("aegis", extractValue(cleaned), Integer::sum);
        } else if (l.contains("реген здоровья:")) {
            bonuses.merge("health_regen", extractValue(cleaned), Integer::sum);
        } else if (l.contains("реген маны:")) {
            bonuses.merge("mana_regen", extractValue(cleaned), Integer::sum);
        } else if (l.contains("мана:")) {
            bonuses.merge("mana", extractValue(cleaned), Integer::sum);
        }
        // ---- Базовые RPG-статы ----
        else if (l.contains("сила:") || l.contains("strength:")) {
            bonuses.merge("strength", extractValue(cleaned), Integer::sum);
        } else if (l.contains("ловкость:") || l.contains("dexterity:")) {
            bonuses.merge("dexterity", extractValue(cleaned), Integer::sum);
        } else if (l.contains("интеллект:") || l.contains("intelligence:")) {
            bonuses.merge("intelligence", extractValue(cleaned), Integer::sum);
        }
    }
    
    /**
     * Извлечь числовое значение из строки
     */
    private static int extractValue(String line) {
        try {
            // Ищем паттерн "+X" или "X"
            String[] parts = line.split(":");
            if (parts.length < 2) return 0;
            
            String valuePart = parts[1].trim().replaceAll("[^0-9]", "");
            return Integer.parseInt(valuePart);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Применить бонус здоровья
     */
    private static void applyHealthBonus(Player player, int bonus) {
        if (bonus <= 0) return;
        
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME + "_health").getBytes());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            MODIFIER_NAME + "_health",
            bonus,
            AttributeModifier.Operation.ADD_NUMBER
        );
        
        attr.addModifier(modifier);
        
        // Восстанавливаем здоровье если нужно
        if (player.getHealth() > attr.getValue()) {
            player.setHealth(attr.getValue());
        }
    }
    
    /**
     * Применить бонус брони
     */
    private static void applyArmorBonus(Player player, int bonus) {
        if (bonus <= 0) return;
        
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ARMOR);
        if (attr == null) return;
        
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME + "_armor").getBytes());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            MODIFIER_NAME + "_armor",
            bonus,
            AttributeModifier.Operation.ADD_NUMBER
        );
        
        attr.addModifier(modifier);
    }
    
    /**
     * Применить бонус урона
     */
    private static void applyDamageBonus(Player player, int bonus) {
        if (bonus <= 0) return;
        
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr == null) return;
        
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME + "_damage").getBytes());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            MODIFIER_NAME + "_damage",
            bonus,
            AttributeModifier.Operation.ADD_NUMBER
        );
        
        attr.addModifier(modifier);
    }
    
    /**
     * Применить бонус скорости атаки
     */
    private static void applySpeedBonus(Player player, int bonus) {
        if (bonus <= 0) return;
        
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) return;
        
        // Конвертируем процент в значение (1% = 0.01)
        double speedValue = bonus / 100.0;
        
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME + "_speed").getBytes());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            MODIFIER_NAME + "_speed",
            speedValue,
            AttributeModifier.Operation.ADD_NUMBER
        );
        
        attr.addModifier(modifier);
    }
    
    /**
     * Удалить все бонусы от экипировки
     */
    public static void removeAllBonuses(Player player) {
        removeAttributeModifiers(player, Attribute.GENERIC_MAX_HEALTH);
        removeAttributeModifiers(player, Attribute.GENERIC_ARMOR);
        removeAttributeModifiers(player, Attribute.GENERIC_ATTACK_DAMAGE);
        removeAttributeModifiers(player, Attribute.GENERIC_ATTACK_SPEED);
    }
    
    /**
     * Удалить модификаторы атрибута
     */
    private static void removeAttributeModifiers(Player player, Attribute attribute) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        
        // Удаляем все модификаторы с нашим именем
        attr.getModifiers().stream()
            .filter(mod -> mod.getName().startsWith(MODIFIER_NAME))
            .forEach(attr::removeModifier);
    }

    // =========================================================================
    // PUBLIC API — pull-доступ к RPG-статам с экипировки
    // =========================================================================

    /**
     * Кэш ссылок на EquipmentManager — резолвится один раз через рефлексию,
     * чтобы не делать reflection-call на каждый удар.
     */
    private static volatile Object equipmentManagerCache;
    private static volatile Method getEquipmentMethod;

    /**
     * Сумма бонусов одного RPG-стата с экипировки игрока.
     * Поддерживаемые ключи: {@code strength}, {@code dexterity}, {@code intelligence},
     * {@code health}, {@code armor}, {@code damage}, {@code crit}, {@code speed}.
     *
     * <p>Безопасно вызывать из других плагинов — если EclipsiaItems не загружен
     * или экипировка пустая, возвращает 0.
     */
    public static int getStatBonus(Player player, String statName) {
        if (player == null || statName == null) return 0;
        PlayerEquipment eq = resolveEquipment(player);
        if (eq == null) return 0;
        return getStatBonus(eq, statName);
    }

    /** То же что выше, но из готового PlayerEquipment (для внутренних вызовов). */
    public static int getStatBonus(PlayerEquipment equipment, String statName) {
        if (equipment == null || statName == null) return 0;
        Map<String, Integer> bonuses = calculateTotalBonuses(equipment);
        return bonuses.getOrDefault(statName.toLowerCase(), 0);
    }

    /** Все бонусы с экипировки игрока (read-only snapshot). */
    public static Map<String, Integer> getAllBonuses(Player player) {
        PlayerEquipment eq = resolveEquipment(player);
        if (eq == null) return new HashMap<>();
        return calculateTotalBonuses(eq);
    }

    private static PlayerEquipment resolveEquipment(Player player) {
        try {
            if (equipmentManagerCache == null) {
                Plugin items = Bukkit.getPluginManager().getPlugin("EclipsiaItems");
                if (items == null) return null;
                Method getMgr = items.getClass().getMethod("getEquipmentManager");
                Object mgr = getMgr.invoke(items);
                if (mgr == null) return null;
                getEquipmentMethod = mgr.getClass().getMethod("getEquipment", Player.class);
                equipmentManagerCache = mgr;
            }
            return (PlayerEquipment) getEquipmentMethod.invoke(equipmentManagerCache, player);
        } catch (Exception e) {
            return null;
        }
    }

    /** Сбросить кэш (вызывается при /reload). */
    public static void invalidateCache() {
        equipmentManagerCache = null;
        getEquipmentMethod = null;
    }
}
