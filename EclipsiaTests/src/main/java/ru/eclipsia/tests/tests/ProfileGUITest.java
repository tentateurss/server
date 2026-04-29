package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест GUI профиля
 */
public class ProfileGUITest extends BaseTest {
    
    @Override
    public String getId() {
        return "profile_gui";
    }
    
    @Override
    public String getName() {
        return "GUI профиля";
    }
    
    @Override
    public String getCategory() {
        return "gui";
    }
    
    @Override
    public String getDescription() {
        return "Проверка работы GUI профиля персонажа";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            // Проверяем базовые данные игрока
            if (player.getLevel() < 0) {
                return failure("Некорректный уровень игрока");
            }
            
            if (player.getMaxHealth() <= 0) {
                return failure("Некорректное здоровье игрока");
            }
            
            return success("Уровень: " + player.getLevel() + ", HP: " + player.getHealth() + "/" + player.getMaxHealth());
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
