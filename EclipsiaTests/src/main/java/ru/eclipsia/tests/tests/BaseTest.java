package ru.eclipsia.tests.tests;

import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestResult;

/**
 * Базовый класс для всех тестов
 */
public abstract class BaseTest {
    
    /**
     * Уникальный ID теста
     */
    public abstract String getId();
    
    /**
     * Название теста
     */
    public abstract String getName();
    
    /**
     * Категория теста (gui, system, integration)
     */
    public abstract String getCategory();
    
    /**
     * Описание теста
     */
    public abstract String getDescription();
    
    /**
     * Запустить тест
     */
    public abstract TestResult run(Player player);
    
    /**
     * Создать успешный результат
     */
    protected TestResult success(String message) {
        return new TestResult(getId(), getName(), true, message);
    }
    
    /**
     * Создать неудачный результат
     */
    protected TestResult failure(String message) {
        return new TestResult(getId(), getName(), false, message);
    }
}
