package ru.eclipsia.items.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI главного меню игрока
 */
public class MainMenuGUI {
    
    private static final String TITLE = "§6§lМеню персонажа";
    private static final int SIZE = 27; // 3 ряда
    
    // Слоты для разделов
    private static final int SLOT_PROFILE = 10;
    private static final int SLOT_STATS = 11;
    private static final int SLOT_SKILLS = 12;
    private static final int SLOT_EQUIPMENT = 13;
    private static final int SLOT_CURRENCY = 15;
    private static final int SLOT_PERKS = 16;
    
    /**
     * Открыть главное меню для игрока
     */
    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Добавляем разделы меню
        inv.setItem(SLOT_PROFILE, createMenuItem(
            Material.PLAYER_HEAD,
            "§6§lПрофиль",
            "§7Просмотр информации о персонаже",
            "",
            "§8• §7Уровень и опыт",
            "§8• §7Класс и характеристики",
            "§8• §7Время игры",
            "",
            "§eНажмите для открытия"
        ));
        
        inv.setItem(SLOT_STATS, createMenuItem(
            Material.WRITABLE_BOOK,
            "§6§lХарактеристики",
            "§7Распределение статов",
            "",
            "§8• §cСила",
            "§8• §aЛовкость",
            "§8• §9Интеллект",
            "",
            "§eНажмите для открытия"
        ));
        
        inv.setItem(SLOT_SKILLS, createMenuItem(
            Material.EMERALD,
            "§6§lНавыки",
            "§7Управление эклипс-навыками",
            "",
            "§8• §75 слотов навыков",
            "§8• §710 слотов поддержек",
            "§8• §7Комбинируйте эффекты",
            "",
            "§eНажмите для открытия"
        ));
        
        inv.setItem(SLOT_EQUIPMENT, createMenuItem(
            Material.DIAMOND_CHESTPLATE,
            "§6§lЭкипировка",
            "§7Управление снаряжением",
            "",
            "§8• §7Броня (4 слота)",
            "§8• §7Аксессуары (4 слота)",
            "§8• §7Оружие в руке",
            "",
            "§eНажмите для открытия"
        ));
        
        inv.setItem(SLOT_CURRENCY, createMenuItem(
            Material.GOLD_INGOT,
            "§6§lВалюта",
            "§7Управление орбами",
            "",
            "§8• §6Орбы: §e" + ru.eclipsia.core.api.EclipsiaAPI.getInstance().getPlayerOrbs(player),
            "§8• §7Валюта для покупок",
            "§8• §7Дроп с мобов",
            "",
            "§eНажмите для открытия"
        ));
        
        inv.setItem(SLOT_PERKS, createMenuItem(
            Material.ENCHANTED_BOOK,
            "§6§lДерево перков",
            "§7Пассивные улучшения",
            "",
            "§8• §7Прокачка узлов",
            "§8• §7Бонусы к характеристикам",
            "§8• §7Уникальные эффекты",
            "",
            "§eНажмите для открытия"
        ));
        
        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private static void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }
    }
    
    /**
     * Создать элемент меню
     */
    private static ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Получить действие по слоту
     */
    public static String getActionBySlot(int slot) {
        return switch (slot) {
            case SLOT_PROFILE -> "profile";
            case SLOT_STATS -> "stats";
            case SLOT_SKILLS -> "skills";
            case SLOT_EQUIPMENT -> "equipment";
            case SLOT_CURRENCY -> "currency";
            case SLOT_PERKS -> "perks";
            default -> null;
        };
    }
}
