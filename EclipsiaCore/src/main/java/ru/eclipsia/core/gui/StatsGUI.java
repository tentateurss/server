package ru.eclipsia.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.classes.PlayerClass;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI для просмотра и распределения характеристик игрока
 */
public class StatsGUI {
    
    private static final String TITLE = "§6Характеристики";
    private static final int SIZE = 54; // 6 рядов
    
    // Слоты для статов
    private static final int SLOT_STRENGTH = 20;
    private static final int SLOT_DEXTERITY = 22;
    private static final int SLOT_INTELLIGENCE = 24;
    
    // Слоты для информации
    private static final int SLOT_PLAYER_INFO = 4;
    private static final int SLOT_CLASS_INFO = 13;
    
    // Слоты для кнопок распределения
    private static final int SLOT_STR_PLUS = 11;
    private static final int SLOT_DEX_PLUS = 13;
    private static final int SLOT_INT_PLUS = 15;
    
    private static final int SLOT_FREE_POINTS = 49;
    
    /**
     * Открыть GUI для игрока
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
        
        // Добавляем информацию об игроке
        inv.setItem(SLOT_PLAYER_INFO, createPlayerInfoItem(player, data));
        
        // Добавляем информацию о классе
        PlayerClass playerClass = ClassManager.getInstance().getClass(data.getClassName());
        if (playerClass != null) {
            inv.setItem(SLOT_CLASS_INFO, createClassInfoItem(playerClass, data));
        }
        
        // Добавляем статы
        inv.setItem(SLOT_STRENGTH, createStatItem("strength", data));
        inv.setItem(SLOT_DEXTERITY, createStatItem("dexterity", data));
        inv.setItem(SLOT_INTELLIGENCE, createStatItem("intelligence", data));
        
        // Добавляем информацию о свободных очках
        inv.setItem(SLOT_FREE_POINTS, createFreePointsItem(data));
        
        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private static void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
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
     * Создать предмет с информацией об игроке
     */
    private static ItemStack createPlayerInfoItem(Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§l" + player.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Информация о персонаже");
            lore.add("");
            lore.add("§7Уровень: §e" + data.getLevel());
            lore.add("§7Опыт: §e" + data.getExperience());
            lore.add("§7Здоровье: §c" + String.format("%.1f", player.getHealth()) + "/" + String.format("%.1f", player.getMaxHealth()));
            lore.add("");
            lore.add("§7Свободных очков: §a" + data.getFreeStatPoints());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать предмет с информацией о классе
     */
    private static ItemStack createClassInfoItem(PlayerClass playerClass, PlayerData data) {
        Material material = Material.getMaterial(playerClass.getIcon());
        if (material == null) material = Material.STONE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(playerClass.getDisplayName());
            
            List<String> lore = new ArrayList<>(playerClass.getDescription());
            lore.add("");
            lore.add("§7Базовые характеристики класса:");
            
            playerClass.getBaseStats().forEach((stat, value) -> {
                String statName = getStatDisplayName(stat);
                int currentValue = data.getStat(stat);
                lore.add("§8• §f" + statName + ": §a" + currentValue);
            });
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать предмет для стата
     */
    private static ItemStack createStatItem(String statId, PlayerData data) {
        Material material;
        String displayName;
        String color;
        List<String> description = new ArrayList<>();
        
        switch (statId) {
            case "strength":
                material = Material.IRON_SWORD;
                displayName = "Сила";
                color = "§c";
                description.add("§7Увеличивает физический урон");
                description.add("§7и максимальное здоровье");
                break;
            case "dexterity":
                material = Material.BOW;
                displayName = "Ловкость";
                color = "§a";
                description.add("§7Увеличивает шанс критического");
                description.add("§7удара и уклонение");
                break;
            case "intelligence":
                material = Material.ENCHANTED_BOOK;
                displayName = "Интеллект";
                color = "§9";
                description.add("§7Увеличивает магический урон");
                description.add("§7и максимальную ману");
                break;
            default:
                material = Material.PAPER;
                displayName = statId;
                color = "§7";
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(color + "§l" + displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Характеристика");
            lore.add("");
            lore.addAll(description);
            lore.add("");
            lore.add("§7Текущее значение: " + color + data.getStat(statId));
            lore.add("");
            lore.add("§7Прокачка через дерево перков");
            lore.add("§e/perks §7- открыть дерево перков");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать предмет со свободными очками
     */
    private static ItemStack createFreePointsItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lСвободные очки");
            
            List<String> lore = new ArrayList<>();
            lore.add("§8Очки перков");
            lore.add("");
            lore.add("§7Доступно очков: §a" + data.getFreeStatPoints());
            lore.add("");
            lore.add("§7Получайте очки за повышение уровня");
            lore.add("§7Используйте их в дереве перков");
            lore.add("");
            lore.add("§e/perks §7- открыть дерево перков");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Получить отображаемое имя стата
     */
    private static String getStatDisplayName(String statId) {
        return switch (statId) {
            case "strength" -> "Сила";
            case "dexterity" -> "Ловкость";
            case "intelligence" -> "Интеллект";
            default -> statId;
        };
    }
    
    /**
     * Получить ID стата по слоту
     */
    public static String getStatBySlot(int slot) {
        return switch (slot) {
            case SLOT_STRENGTH -> "strength";
            case SLOT_DEXTERITY -> "dexterity";
            case SLOT_INTELLIGENCE -> "intelligence";
            default -> null;
        };
    }
    
    /**
     * Проверить является ли слот статом
     */
    public static boolean isStatSlot(int slot) {
        return getStatBySlot(slot) != null;
    }
}
