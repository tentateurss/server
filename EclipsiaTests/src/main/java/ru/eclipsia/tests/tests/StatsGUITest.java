package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Тест GUI статов
 */
public class StatsGUITest extends BaseTest {
    
    @Override
    public String getId() {
        return "stats_gui";
    }
    
    @Override
    public String getName() {
        return "GUI характеристик";
    }
    
    @Override
    public String getCategory() {
        return "gui";
    }
    
    @Override
    public String getDescription() {
        return "Проверка работы GUI статов и распределения очков";
    }
    
    @Override
    public TestResult run(Player player) {
        try {
            EclipsiaAPI api = EclipsiaAPI.getInstance();
            
            // Проверяем статы игрока
            int strength = api.getPlayerStat(player, "strength");
            int dexterity = api.getPlayerStat(player, "dexterity");
            int intelligence = api.getPlayerStat(player, "intelligence");
            
            if (strength == 0 && dexterity == 0 && intelligence == 0) {
                return failure("Все статы равны 0. Выберите класс через /class");
            }
            
            return success("Статы: STR=" + strength + " DEX=" + dexterity + " INT=" + intelligence);
            
        } catch (Exception e) {
            return failure("Ошибка: " + e.getMessage());
        }
    }
}
