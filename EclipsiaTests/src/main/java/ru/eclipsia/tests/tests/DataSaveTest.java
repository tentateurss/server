package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест сохранения данных
 */
public class DataSaveTest extends BaseTest {
    
    @Override
    public String getId() {
        return "data_save";
    }
    
    @Override
    public String getName() {
        return "Сохранение данных";
    }
    
    @Override
    public String getCategory() {
        return "system";
    }
    
    @Override
    public String getDescription() {
        return "Проверка системы сохранения данных игрока";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            EclipsiaAPI api = EclipsiaAPI.getInstance();
            
            // Проверяем, что данные игрока загружены
            String className = api.getPlayerClassName(player);
            int level = api.getPlayerLevel(player);
            
            if (className == null) {
                return failure("Данные игрока не загружены");
            }
            
            // Проверяем базовые данные
            if (level < 1) {
                return failure("Некорректный уровень: " + level);
            }
            
            return success("Данные загружены: " + className + " Lv." + level);
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
