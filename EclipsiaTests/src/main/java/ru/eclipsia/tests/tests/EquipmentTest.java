package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.tests.EclipsiaTests;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест системы экипировки
 */
public class EquipmentTest extends BaseTest {
    
    @Override
    public String getId() {
        return "equipment";
    }
    
    @Override
    public String getName() {
        return "Система экипировки";
    }
    
    @Override
    public String getCategory() {
        return "system";
    }
    
    @Override
    public String getDescription() {
        return "Проверка работы системы экипировки";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Проверяем, что плагин EclipsiaItems загружен
            Plugin itemsPlugin = EclipsiaTests.getInstance().getServer().getPluginManager().getPlugin("EclipsiaItems");
            
            if (itemsPlugin == null) {
                return failure("Плагин EclipsiaItems не загружен");
            }
            
            if (!itemsPlugin.isEnabled()) {
                return failure("Плагин EclipsiaItems отключен");
            }
            
            return success("EclipsiaItems активен");
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
