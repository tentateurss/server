package ru.eclipsia.items.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Постоянная стрела для лучников
 */
public class PermanentArrow {
    
    private static final String ARROW_NAME = "§6§lБесконечная стрела";
    
    /**
     * Создать постоянную стрелу
     */
    public static ItemStack createPermanentArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ARROW_NAME);
            meta.setLore(Arrays.asList(
                "§7Магическая стрела для стрельбы",
                "§7из кастомных луков",
                "",
                "§aНе расходуется при стрельбе",
                "",
                "§c§lНельзя выбросить!"
            ));
            arrow.setItemMeta(meta);
        }
        
        return arrow;
    }
    
    /**
     * Проверить является ли предмет постоянной стрелой
     */
    public static boolean isPermanentArrow(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) {
            return false;
        }
        
        if (!item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && ARROW_NAME.equals(meta.getDisplayName());
    }
}
