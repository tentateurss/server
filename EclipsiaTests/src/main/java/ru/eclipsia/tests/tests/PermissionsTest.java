package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест системы прав
 */
public class PermissionsTest extends BaseTest {
    
    @Override
    public String getId() {
        return "permissions";
    }
    
    @Override
    public String getName() {
        return "Система прав";
    }
    
    @Override
    public String getCategory() {
        return "integration";
    }
    
    @Override
    public String getDescription() {
        return "Проверка системы прав и доступа к командам";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Проверяем базовые права
            boolean hasAdminPerm = player.hasPermission("eclipsia.admin");
            boolean isOp = player.isOp();
            
            if (hasAdminPerm || isOp) {
                return success("Права администратора: ✓");
            } else {
                return success("Обычный игрок (нет прав администратора)");
            }
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
