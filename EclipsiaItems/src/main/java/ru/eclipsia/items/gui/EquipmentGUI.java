package ru.eclipsia.items.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.item.ItemSlot;

import java.util.Arrays;

/**
 * GUI экипировки игрока
 */
public class EquipmentGUI {
    
    private static final String TITLE = "§6Экипировка";
    private static final int SIZE = 54; // 6 рядов
    
    // Слоты в GUI. Два оружейных слота — HAND (основное) и OFFHAND (доп.).
    // Бонусы с обоих суммируются в EquipmentBonusApplier (идёт по всем ItemSlot.values()).
    private static final int SLOT_HEAD    = 10;
    private static final int SLOT_CHEST   = 19;
    private static final int SLOT_LEGS    = 28;
    private static final int SLOT_FEET    = 37;
    private static final int SLOT_AMULET  = 14;
    private static final int SLOT_RING_1  = 23;
    private static final int SLOT_RING_2  = 32;
    private static final int SLOT_BELT    = 41;
    private static final int SLOT_WEAPON  = 12; // Основное оружие (HAND)
    private static final int SLOT_OFFHAND = 21; // Доп. оружие (OFFHAND)
    
    private final EquipmentManager equipmentManager;
    
    public EquipmentGUI(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    /**
     * Открыть GUI для игрока
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Добавляем слоты экипировки
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        
        // Броня
        ItemStack head = equipment.getItem(ItemSlot.HEAD);
        inv.setItem(SLOT_HEAD, head != null ? head : createSlotPlaceholder(ItemSlot.HEAD));
        
        ItemStack chest = equipment.getItem(ItemSlot.CHEST);
        inv.setItem(SLOT_CHEST, chest != null ? chest : createSlotPlaceholder(ItemSlot.CHEST));
        
        ItemStack legs = equipment.getItem(ItemSlot.LEGS);
        inv.setItem(SLOT_LEGS, legs != null ? legs : createSlotPlaceholder(ItemSlot.LEGS));
        
        ItemStack feet = equipment.getItem(ItemSlot.FEET);
        inv.setItem(SLOT_FEET, feet != null ? feet : createSlotPlaceholder(ItemSlot.FEET));
        
        // Аксессуары
        ItemStack amulet = equipment.getItem(ItemSlot.AMULET);
        inv.setItem(SLOT_AMULET, amulet != null ? amulet : createSlotPlaceholder(ItemSlot.AMULET));
        
        ItemStack ring1 = equipment.getItem(ItemSlot.RING_1);
        inv.setItem(SLOT_RING_1, ring1 != null ? ring1 : createSlotPlaceholder(ItemSlot.RING_1));
        
        ItemStack ring2 = equipment.getItem(ItemSlot.RING_2);
        inv.setItem(SLOT_RING_2, ring2 != null ? ring2 : createSlotPlaceholder(ItemSlot.RING_2));
        
        ItemStack belt = equipment.getItem(ItemSlot.BELT);
        inv.setItem(SLOT_BELT, belt != null ? belt : createSlotPlaceholder(ItemSlot.BELT));

        // Оружие
        ItemStack weapon = equipment.getItem(ItemSlot.HAND);
        inv.setItem(SLOT_WEAPON, weapon != null ? weapon : createSlotPlaceholder(ItemSlot.HAND));

        ItemStack offhand = equipment.getItem(ItemSlot.OFFHAND);
        inv.setItem(SLOT_OFFHAND, offhand != null ? offhand : createSlotPlaceholder(ItemSlot.OFFHAND));

        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < SIZE; i++) {
            // Пропускаем слоты экипировки
            if (isEquipmentSlot(i)) {
                continue;
            }
            inv.setItem(i, glass);
        }
    }
    
    /**
     * Создать заглушку для пустого слота
     */
    private ItemStack createSlotPlaceholder(ItemSlot slot) {
        Material material;
        
        switch (slot) {
            case HEAD:
                material = Material.LEATHER_HELMET;
                break;
            case CHEST:
                material = Material.LEATHER_CHESTPLATE;
                break;
            case LEGS:
                material = Material.LEATHER_LEGGINGS;
                break;
            case FEET:
                material = Material.LEATHER_BOOTS;
                break;
            case HAND:
                material = Material.WOODEN_SWORD;
                break;
            case OFFHAND:
                material = Material.SHIELD;
                break;
            case AMULET:
                material = Material.PURPLE_STAINED_GLASS_PANE;
                break;
            case RING_1:
            case RING_2:
                material = Material.YELLOW_STAINED_GLASS_PANE;
                break;
            case BELT:
                material = Material.BROWN_STAINED_GLASS_PANE;
                break;
            default:
                material = Material.BARRIER;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7" + slot.getDisplayName());
            meta.setLore(Arrays.asList("§8Пусто", "§7Перетащите предмет сюда"));
            // Пустые плейсхолдеры из лэзер-брони показывать ванильный
            // "When on Head: +1 Armor" не должны — это визуальный мусор
            // на фоне русского лора.
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_DYE,
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_PLACED_ON,
                    ItemFlag.HIDE_POTION_EFFECTS
            );
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Получить слот экипировки по индексу в GUI
     */
    public static ItemSlot getSlotByIndex(int index) {
        switch (index) {
            case SLOT_HEAD: return ItemSlot.HEAD;
            case SLOT_CHEST: return ItemSlot.CHEST;
            case SLOT_LEGS: return ItemSlot.LEGS;
            case SLOT_FEET: return ItemSlot.FEET;
            case SLOT_AMULET: return ItemSlot.AMULET;
            case SLOT_RING_1: return ItemSlot.RING_1;
            case SLOT_RING_2: return ItemSlot.RING_2;
            case SLOT_BELT: return ItemSlot.BELT;
            case SLOT_WEAPON: return ItemSlot.HAND;
            case SLOT_OFFHAND: return ItemSlot.OFFHAND;
            default: return null;
        }
    }
    
    /**
     * Проверить является ли индекс слотом экипировки
     */
    public static boolean isEquipmentSlot(int index) {
        return getSlotByIndex(index) != null;
    }
    
}
