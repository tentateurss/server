package ru.eclipsia.items.generator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.items.affix.Affix;
import ru.eclipsia.items.affix.AffixManager;
import ru.eclipsia.items.item.*;
import ru.eclipsia.items.rarity.ItemRarity;
import ru.eclipsia.items.rarity.RarityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Генератор кастомных предметов
 */
public class ItemGenerator {
    
    private final Plugin plugin;
    private final AffixManager affixManager;
    private final ItemManager itemManager;
    private final RarityManager rarityManager;
    
    public ItemGenerator(Plugin plugin, AffixManager affixManager, 
                        ItemManager itemManager, RarityManager rarityManager) {
        this.plugin = plugin;
        this.affixManager = affixManager;
        this.itemManager = itemManager;
        this.rarityManager = rarityManager;
    }
    
    /**
     * Сгенерировать случайный предмет для уровня
     */
    public ItemStack generateItem(int itemLevel) {
        // Получаем случайный базовый предмет
        BaseItem baseItem = itemManager.getRandomItem(itemLevel);
        if (baseItem == null) {
            plugin.getLogger().warning("Не удалось найти базовый предмет для уровня " + itemLevel);
            return null;
        }
        
        return generateItem(baseItem, itemLevel, null);
    }
    
    /**
     * Сгенерировать случайный предмет для класса и уровня
     */
    public ItemStack generateItemForClass(String playerClass, int itemLevel) {
        // Получаем случайный базовый предмет для класса
        BaseItem baseItem = itemManager.getRandomItemForClass(playerClass, itemLevel);
        if (baseItem == null) {
            plugin.getLogger().warning("Не удалось найти базовый предмет для класса " + playerClass + " и уровня " + itemLevel);
            return null;
        }
        
        return generateItem(baseItem, itemLevel, playerClass);
    }
    
    /**
     * Сгенерировать предмет из базового
     */
    public ItemStack generateItem(BaseItem baseItem, int itemLevel, String playerClass) {
        // Определяем редкость
        ItemRarity rarity = rarityManager.getRandomRarity();
        
        // Создаем кастомный предмет
        CustomItem customItem = new CustomItem(baseItem, rarity, itemLevel);
        
        // Добавляем аффиксы (если не обычный)
        if (rarity != ItemRarity.NORMAL) {
            int affixCount = rarityManager.getAffixCount(rarity);
            List<Affix> affixes = affixManager.getRandomAffixes(
                baseItem.getItemType(), 
                itemLevel, 
                affixCount
            );
            
            for (Affix affix : affixes) {
                int value = affix.rollValue();
                customItem.addAffix(affix, value);
            }
        }
        
        // Конвертируем в ItemStack
        return toItemStack(customItem);
    }
    
    /**
     * Конвертировать CustomItem в ItemStack
     */
    private ItemStack toItemStack(CustomItem customItem) {
        BaseItem baseItem = customItem.getBaseItem();
        
        // Создаем ItemStack
        ItemStack item = new ItemStack(baseItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) {
            return item;
        }
        
        // Устанавливаем название
        meta.setDisplayName(customItem.getFullName());
        
        // Создаем лор
        List<String> lore = new ArrayList<>();
        
        // Редкость
        lore.add(customItem.getRarity().getColoredName());
        lore.add("§7Уровень предмета: §f" + customItem.getItemLevel());
        lore.add("§7Тип: §f" + getSlotDisplayName(baseItem.getSlot()));
        lore.add("");
        
        // Базовые характеристики
        if (baseItem.isWeapon()) {
            int totalDamage = customItem.getTotalDamage();
            lore.add("§7Урон: §f" + totalDamage);
        }
        
        if (baseItem.isArmor()) {
            int totalArmor = customItem.getTotalArmor();
            lore.add("§7Броня: §f" + totalArmor);
        }

        // Базовый блок щита. Парсер EquipmentBonusApplier ловит «Шанс блока:»
        // и «Сила блока:», поэтому достаточно вывести их в лор — стат сам
        // подцепится в DamageCalculator при равных слотах.
        if (baseItem.isShield()) {
            int bbc = baseItem.getBaseBlockChance();
            int bba = baseItem.getBaseBlockAmount();
            if (bbc > 0) {
                lore.add("§7Шанс блока: §f" + bbc + "%");
            }
            if (bba > 0) {
                lore.add("§7Сила блока: §f" + bba + "%");
            }
        }
        
        // Бонусы от аффиксов
        int healthBonus = customItem.getHealthBonus();
        if (healthBonus > 0) {
            lore.add("§7Здоровье: §f+" + healthBonus);
        }
        
        int critBonus = customItem.getCritBonus();
        if (critBonus > 0) {
            lore.add("§7Крит. урон: §f+" + critBonus + "%");
        }
        
        int speedBonus = customItem.getSpeedBonus();
        if (speedBonus > 0) {
            lore.add("§7Скорость атаки: §f+" + speedBonus + "%");
        }
        
        // Аффиксы
        if (!customItem.getAffixes().isEmpty()) {
            lore.add("");
            lore.add("§6Аффиксы:");
            for (Map.Entry<Affix, Integer> entry : customItem.getAffixes().entrySet()) {
                lore.add(entry.getKey().getDescription(entry.getValue()));
            }
        }
        
        // Требования
        lore.add("");
        // УБРАНО: Требование по классу - как в PoE, все могут носить всё
        lore.add("§cТребуется уровень: §f" + baseItem.getMinLevel());
        
        meta.setLore(lore);
        
        // CustomModelData для ресурс-пака
        if (baseItem.getSlot() == ru.eclipsia.items.item.ItemSlot.AMULET) {
            if (baseItem.getId().equals("simple_amulet")) {
                meta.setCustomModelData(100);
            } else if (baseItem.getId().equals("mystic_amulet")) {
                meta.setCustomModelData(101);
            }
        }
        
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Получить отображаемое название класса
     */
    private String getClassDisplayName(String className) {
        switch (className.toLowerCase()) {
            case "warrior": return "Воин";
            case "archer": return "Лучник";
            case "mage": return "Маг";
            default: return className;
        }
    }
    
    /**
     * Получить отображаемое название слота
     */
    private String getSlotDisplayName(ru.eclipsia.items.item.ItemSlot slot) {
        return switch (slot) {
            case HEAD -> "Шлем";
            case CHEST -> "Нагрудник";
            case LEGS -> "Штаны";
            case FEET -> "Ботинки";
            case HAND -> "Оружие";
            case OFFHAND -> "Доп. оружие";
            case AMULET -> "Амулет";
            case RING_1, RING_2 -> "Кольцо";
            case BELT -> "Пояс";
        };
    }
    
    /**
     * Сгенерировать предмет по ID базового предмета
     */
    public ItemStack generateItemById(String itemId, int itemLevel) {
        BaseItem baseItem = itemManager.getItem(itemId);
        if (baseItem == null) {
            plugin.getLogger().warning("Базовый предмет не найден: " + itemId);
            return null;
        }
        
        return generateItem(baseItem, itemLevel, null);
    }
}
