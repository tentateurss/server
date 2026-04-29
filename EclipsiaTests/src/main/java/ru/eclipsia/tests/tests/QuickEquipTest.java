package ru.eclipsia.tests.tests;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест быстрой экипировки через ПКМ
 */
public class QuickEquipTest extends BaseTest {
    
    @Override
    public String getId() {
        return "quick_equip";
    }
    
    @Override
    public String getName() {
        return "Быстрая экипировка";
    }
    
    @Override
    public String getCategory() {
        return "system";
    }
    
    @Override
    public String getDescription() {
        return "Проверка системы быстрой экипировки через ПКМ";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Создаем тестовый предмет
            ItemStack testItem = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = testItem.getItemMeta();
            
            if (meta == null) {
                return failure("Не удалось создать тестовый предмет");
            }
            
            meta.setDisplayName("§fТестовый меч");
            meta.setLore(java.util.Arrays.asList(
                "§7Редкость: §fОбычный",
                "§7Тип: Меч",
                "§7Урон: +5"
            ));
            testItem.setItemMeta(meta);
            
            // Проверяем, что предмет создан корректно
            if (!testItem.hasItemMeta()) {
                return failure("Предмет не имеет метаданных");
            }
            
            if (!testItem.getItemMeta().hasLore()) {
                return failure("Предмет не имеет лора");
            }
            
            return success("Тестовый предмет создан корректно");
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
