package ru.eclipsia.tests.tests;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест книги меню
 */
public class MenuBookTest extends BaseTest {
    
    @Override
    public String getId() {
        return "menu_book";
    }
    
    @Override
    public String getName() {
        return "Книга меню";
    }
    
    @Override
    public String getCategory() {
        return "gui";
    }
    
    @Override
    public String getDescription() {
        return "Проверка наличия книги меню в слоте 8 хотбара";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Проверяем слот 8 (последний слот хотбара)
            ItemStack item = player.getInventory().getItem(8);
            
            if (item == null) {
                return failure("В слоте 8 нет предмета");
            }
            
            if (item.getType() != Material.ENCHANTED_BOOK) {
                return failure("В слоте 8 не книга: " + item.getType());
            }
            
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                return failure("У книги нет названия");
            }
            
            String name = item.getItemMeta().getDisplayName();
            if (!name.contains("Меню")) {
                return failure("Неправильное название книги: " + name);
            }
            
            return success("Книга меню найдена в слоте 8");
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
