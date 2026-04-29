package ru.eclipsia.items.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.core.api.EclipsiaAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI для управления валютой (орбами)
 */
public class CurrencyGUI {
    
    private static final String TITLE = "§6§lВалюта";
    private static final int SIZE = 27; // 3 ряда
    
    /**
     * Открыть GUI валюты для игрока
     */
    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        int orbs = api.getPlayerOrbs(player);
        
        // Центральный предмет - информация об орбах
        inv.setItem(13, createOrbInfo(orbs));
        
        // Информационные предметы
        inv.setItem(10, createInfoItem(
            Material.ZOMBIE_HEAD,
            "§6Как получить орбы?",
            "§7Убивайте кастомных мобов",
            "§7Количество зависит от уровня моба",
            "",
            "§8• §7Зомби-воин: §65-15 орбов",
            "§8• §7Скелет-лучник: §67-20 орбов",
            "§8• §7Элитный моб: §620-50 орбов"
        ));
        
        inv.setItem(12, createInfoItem(
            Material.EMERALD,
            "§6Для чего нужны орбы?",
            "§7Основная валюта сервера",
            "",
            "§8• §7Покупка предметов (скоро)",
            "§8• §7Улучшение экипировки (скоро)",
            "§8• §7Торговля с игроками (скоро)"
        ));
        
        inv.setItem(14, createInfoItem(
            Material.CHEST,
            "§6Хранение орбов",
            "§7Орбы хранятся в профиле",
            "",
            "§8• §7Не занимают место в инвентаре",
            "§8• §7Не теряются при смерти",
            "§8• §7Автоматически сохраняются"
        ));
        
        inv.setItem(16, createInfoItem(
            Material.DIAMOND,
            "§6Редкие орбы",
            "§7Особая валюта (скоро)",
            "",
            "§8• §7Дроп с боссов",
            "§8• §7Награды за достижения",
            "§8• §7Уникальные покупки"
        ));
        
        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private static void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
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
     * Создать информацию об орбах
     */
    private static ItemStack createOrbInfo(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lВаши орбы");
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Текущий баланс:");
            lore.add("§6§l" + amount + " орбов");
            lore.add("");
            lore.add("§7Орбы - основная валюта");
            lore.add("§7на сервере Eclipsia");
            lore.add("");
            lore.add("§8Получайте орбы за убийство мобов!");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать информационный предмет
     */
    private static ItemStack createInfoItem(Material material, String name, String... lore) {
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
}
