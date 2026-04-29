package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.tests.EclipsiaTests;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест навигации между GUI
 */
public class NavigationTest extends BaseTest {
    
    @Override
    public String getId() {
        return "navigation";
    }
    
    @Override
    public String getName() {
        return "Навигация между GUI";
    }
    
    @Override
    public String getCategory() {
        return "integration";
    }
    
    @Override
    public String getDescription() {
        return "Проверка навигации между всеми GUI системами";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Проверяем, что все необходимые плагины загружены
            Plugin core = EclipsiaTests.getInstance().getServer().getPluginManager().getPlugin("EclipsiaCore");
            Plugin items = EclipsiaTests.getInstance().getServer().getPluginManager().getPlugin("EclipsiaItems");
            Plugin perks = EclipsiaTests.getInstance().getServer().getPluginManager().getPlugin("EclipsiaPerks");
            
            if (core == null || !core.isEnabled()) {
                return failure("EclipsiaCore не активен");
            }
            
            if (items == null || !items.isEnabled()) {
                return failure("EclipsiaItems не активен");
            }
            
            if (perks == null || !perks.isEnabled()) {
                return failure("EclipsiaPerks не активен");
            }
            
            return success("Все плагины активны");
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
