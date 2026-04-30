package ru.eclipsia.items.generator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
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
     * Сгенерировать случайный предмет для уровня (без скейлинга редкости).
     * Для дропа с моба используй {@link #generateItem(int, int, int)}.
     */
    public ItemStack generateItem(int itemLevel) {
        return generateItem(itemLevel, itemLevel, 0);
    }

    /** Перегрузка с уровнем моба и Magic Find — для лута. */
    public ItemStack generateItem(int itemLevel, int mobLevel, int magicFindPercent) {
        BaseItem baseItem = itemManager.getRandomItem(itemLevel);
        if (baseItem == null) {
            plugin.getLogger().warning("Не удалось найти базовый предмет для уровня " + itemLevel);
            return null;
        }
        return generateItem(baseItem, itemLevel, null, mobLevel, magicFindPercent);
    }

    /** Сгенерировать случайный предмет для класса и уровня. */
    public ItemStack generateItemForClass(String playerClass, int itemLevel) {
        return generateItemForClass(playerClass, itemLevel, itemLevel, 0);
    }

    public ItemStack generateItemForClass(String playerClass, int itemLevel,
                                          int mobLevel, int magicFindPercent) {
        BaseItem baseItem = itemManager.getRandomItemForClass(playerClass, itemLevel);
        if (baseItem == null) {
            plugin.getLogger().warning("Не удалось найти базовый предмет для класса "
                    + playerClass + " и уровня " + itemLevel);
            return null;
        }
        return generateItem(baseItem, itemLevel, playerClass, mobLevel, magicFindPercent);
    }
    
    /**
     * Сгенерировать предмет из базового. {@code mobLevel}/{@code magicFind}
     * управляют шансом редкости; для бэк-совместимости старая сигнатура
     * (без mobLevel) использует {@code itemLevel} как уровень моба.
     */
    public ItemStack generateItem(BaseItem baseItem, int itemLevel, String playerClass) {
        return generateItem(baseItem, itemLevel, playerClass, itemLevel, 0);
    }

    public ItemStack generateItem(BaseItem baseItem, int itemLevel, String playerClass,
                                  int mobLevel, int magicFindPercent) {
        ItemRarity rarity = rarityManager.getRandomRarity(mobLevel, magicFindPercent);
        CustomItem customItem = new CustomItem(baseItem, rarity, itemLevel);

        // Implicits — врождённые статы базы, всегда применяются.
        for (Affix imp : affixManager.getImplicitsFor(baseItem.getItemType())) {
            customItem.addAffix(imp, imp.rollValue());
        }

        // Префиксы/суффиксы — только для Magic/Rare. По бюджету и тиру.
        if (rarity == ItemRarity.MAGIC || rarity == ItemRarity.RARE) {
            List<Affix> affixes = affixManager.rollAffixes(
                    baseItem.getItemType(), itemLevel, rarity);
            for (Affix affix : affixes) {
                customItem.addAffix(affix, affix.rollValue());
            }
        }

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
        
        // Implicit-аффиксы (PoE-style: над разделителем, без шапки).
        Map<Affix, Integer> implicits = customItem.getImplicits();
        if (!implicits.isEmpty()) {
            for (Map.Entry<Affix, Integer> entry : implicits.entrySet()) {
                lore.add(entry.getKey().getDescription(entry.getValue()));
            }
            lore.add("§8" + "─".repeat(20));
        }

        // Explicit-аффиксы (префикс + суффикс).
        Map<Affix, Integer> explicit = new java.util.LinkedHashMap<>();
        explicit.putAll(customItem.getPrefixes());
        explicit.putAll(customItem.getSuffixes());
        if (!explicit.isEmpty()) {
            lore.add("§6Аффиксы:");
            for (Map.Entry<Affix, Integer> entry : explicit.entrySet()) {
                lore.add(entry.getKey().getDescription(entry.getValue()));
            }
        }
        
        // Требования
        lore.add("");
        // УБРАНО: Требование по классу - как в PoE, все могут носить всё
        lore.add("§cТребуется уровень: §f" + baseItem.getMinLevel());
        
        meta.setLore(lore);

        // Скрываем ванильные "When on Head: +N Armor" / "When in Main Hand:
        // +N Attack Damage" и прочую служебку — наш лор сам показывает
        // итоговые числа (Броня/Урон/Аффиксы), а ванильный блок ломал
        // оформление, накладывая английский текст поверх русского описания.
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_POTION_EFFECTS
        );

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
