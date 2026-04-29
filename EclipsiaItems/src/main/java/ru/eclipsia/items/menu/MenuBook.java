package ru.eclipsia.items.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Книга меню игрока
 */
public class MenuBook {
    
    private static final String BOOK_NAME = "§6§lМеню";
    
    /**
     * Создать книгу меню
     */
    public static ItemStack createMenuBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(BOOK_NAME);
            meta.setLore(Arrays.asList(
                "§7Нажмите ПКМ для открытия меню",
                "",
                "§e▸ Профиль персонажа",
                "§e▸ Характеристики",
                "§e▸ Экипировка",
                "§e▸ Дерево перков",
                "",
                "§c§lНельзя выбросить!"
            ));
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * Проверить является ли предмет книгой меню
     */
    public static boolean isMenuBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        
        if (!item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && BOOK_NAME.equals(meta.getDisplayName());
    }
}
