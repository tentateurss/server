package ru.eclipsia.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.classes.PlayerClass;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI профиля игрока - общая информация о персонаже
 */
public class ProfileGUI {
    
    private static final String TITLE = "§6Профиль персонажа";
    private static final int SIZE = 54; // 6 рядов
    
    // Слоты для разделов
    private static final int SLOT_PLAYER_HEAD = 4;
    private static final int SLOT_CLASS_INFO = 13;
    private static final int SLOT_STATS = 20;
    private static final int SLOT_PERKS = 22;
    private static final int SLOT_EQUIPMENT = 24;
    private static final int SLOT_ACHIEVEMENTS = 31;
    
    // Кнопки навигации
    private static final int SLOT_OPEN_STATS = 38;
    private static final int SLOT_OPEN_PERKS = 40;
    private static final int SLOT_OPEN_EQUIPMENT = 42;
    
    /**
     * Открыть GUI профиля для игрока
     */
    public static void open(Player player) {
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        
        if (data == null) {
            player.sendMessage("§cОшибка загрузки данных. Попробуйте перезайти.");
            return;
        }
        
        if (data.getClassName() == null) {
            player.sendMessage("§cСначала выберите класс: /class");
            return;
        }
        
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Добавляем голову игрока
        inv.setItem(SLOT_PLAYER_HEAD, createPlayerHead(player, data));
        
        // Добавляем информацию о классе
        PlayerClass playerClass = ClassManager.getInstance().getClass(data.getClassName());
        if (playerClass != null) {
            inv.setItem(SLOT_CLASS_INFO, createClassInfo(playerClass, data));
        }
        
        // Добавляем разделы
        inv.setItem(SLOT_STATS, createStatsSection(data));
        inv.setItem(SLOT_PERKS, createPerksSection(data));
        inv.setItem(SLOT_EQUIPMENT, createEquipmentSection(player));
        inv.setItem(SLOT_ACHIEVEMENTS, createAchievementsSection());
        
        // Добавляем кнопки навигации
        inv.setItem(SLOT_OPEN_STATS, createNavigationButton(Material.BOOK, "§6Открыть характеристики", "§7Нажмите для просмотра и", "§7распределения статов"));
        inv.setItem(SLOT_OPEN_PERKS, createNavigationButton(Material.ENCHANTED_BOOK, "§6Открыть дерево перков", "§7Нажмите для прокачки", "§7узлов дерева"));
        inv.setItem(SLOT_OPEN_EQUIPMENT, createNavigationButton(Material.DIAMOND_CHESTPLATE, "§6Открыть экипировку", "§7Нажмите для управления", "§7снаряжением"));
        
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
     * Создать голову игрока
     */
    private static ItemStack createPlayerHead(Player player, PlayerData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§6§l" + player.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Профиль персонажа");
            lore.add("");
            lore.add("§7Уровень: §e" + data.getLevel());
            lore.add("§7Опыт: §e" + data.getExperience());
            lore.add("");
            lore.add("§7Здоровье: §c" + String.format("%.1f", player.getHealth()) + "§7/§c" + String.format("%.1f", player.getMaxHealth()));
            lore.add("§7Голод: §6" + player.getFoodLevel() + "§7/§620");
            lore.add("");
            lore.add("§7Время игры: §e" + formatPlayTime(player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)));
            
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        
        return head;
    }
    
    /**
     * Создать информацию о классе
     */
    private static ItemStack createClassInfo(PlayerClass playerClass, PlayerData data) {
        Material material = Material.getMaterial(playerClass.getIcon());
        if (material == null) material = Material.STONE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(playerClass.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Ваш класс");
            lore.add("");
            lore.addAll(playerClass.getDescription());
            lore.add("");
            lore.add("§7Базовое здоровье: §c" + String.format("%.1f", playerClass.calculateHealth(data.getLevel())));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать раздел статов
     */
    private static ItemStack createStatsSection(PlayerData data) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lХарактеристики");
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Основные статы");
            lore.add("");
            lore.add("§cСила: §f" + data.getStat("strength"));
            lore.add("§aЛовкость: §f" + data.getStat("dexterity"));
            lore.add("§9Интеллект: §f" + data.getStat("intelligence"));
            lore.add("");
            lore.add("§7Свободных очков: §a" + data.getFreeStatPoints());
            lore.add("");
            lore.add("§eНажмите для подробностей");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать раздел перков
     */
    private static ItemStack createPerksSection(PlayerData data) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lДерево перков");
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Пассивные улучшения");
            lore.add("");
            lore.add("§7Система прокачки как в Path of Exile");
            lore.add("§7Единое дерево для всех классов");
            lore.add("");
            lore.add("§7Получайте очки за уровень");
            lore.add("§7и прокачивайте узлы дерева");
            lore.add("");
            lore.add("§eНажмите для открытия");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать раздел экипировки
     */
    private static ItemStack createEquipmentSection(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lЭкипировка");
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Снаряжение персонажа");
            lore.add("");
            lore.add("§7Слоты экипировки:");
            lore.add("§8• §7Голова, Грудь, Ноги, Ботинки");
            lore.add("§8• §7Амулет, 2 Кольца, Пояс");
            lore.add("");
            lore.add("§7Оружие в руке применяется автоматически");
            lore.add("");
            lore.add("§eНажмите для открытия");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать раздел достижений
     */
    private static ItemStack createAchievementsSection() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lДостижения");
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Скоро будет доступно");
            lore.add("");
            lore.add("§7Система достижений и наград");
            lore.add("§7будет добавлена в будущих обновлениях");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать кнопку навигации
     */
    private static ItemStack createNavigationButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Форматировать время игры
     */
    private static String formatPlayTime(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "ч " + (minutes % 60) + "м";
        } else if (minutes > 0) {
            return minutes + "м " + (seconds % 60) + "с";
        } else {
            return seconds + "с";
        }
    }
    
    /**
     * Получить тип действия по слоту
     */
    public static String getActionBySlot(int slot) {
        return switch (slot) {
            case SLOT_STATS, SLOT_OPEN_STATS -> "stats";
            case SLOT_PERKS, SLOT_OPEN_PERKS -> "perks";
            case SLOT_EQUIPMENT, SLOT_OPEN_EQUIPMENT -> "equipment";
            default -> null;
        };
    }
}
