package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест GUI выбора класса
 */
public class ClassGUITest extends BaseTest {
    
    @Override
    public String getId() {
        return "class_gui";
    }
    
    @Override
    public String getName() {
        return "GUI выбора класса";
    }
    
    @Override
    public String getCategory() {
        return "gui";
    }
    
    @Override
    public String getDescription() {
        return "Проверка работы GUI выбора класса";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            EclipsiaAPI api = EclipsiaAPI.getInstance();
            
            // Проверяем, что API доступен
            if (api == null) {
                return failure("EclipsiaAPI недоступен");
            }
            
            // Проверяем, что у игрока есть класс
            String className = api.getPlayerClassName(player);
            if (className == null || className.isEmpty()) {
                return failure("У игрока не выбран класс. Используйте /class");
            }
            
            return success("Класс игрока: " + className);
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
