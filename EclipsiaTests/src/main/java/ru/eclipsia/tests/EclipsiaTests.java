package ru.eclipsia.tests;

import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.tests.commands.TestCommand;
import ru.eclipsia.tests.commands.TestResultCommand;
import ru.eclipsia.tests.commands.AutoTestCommand;
import ru.eclipsia.tests.manager.TestManager;

/**
 * Главный класс плагина EclipsiaTests
 */
public class EclipsiaTests extends JavaPlugin {
    
    private static EclipsiaTests instance;
    private TestManager testManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("=================================");
        getLogger().info("  EclipsiaTests v" + getDescription().getVersion());
        getLogger().info("  Загрузка плагина тестирования...");
        getLogger().info("=================================");
        
        // Проверка зависимостей
        if (getServer().getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден! Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Инициализация менеджера тестов
        testManager = new TestManager(this);
        
        // Регистрация команд
        registerCommands();
        
        getLogger().info("=================================");
        getLogger().info("  EclipsiaTests успешно загружен!");
        getLogger().info("  Доступные тесты: " + testManager.getTestCount());
        getLogger().info("=================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("EclipsiaTests отключен.");
    }
    
    private void registerCommands() {
        var testCmd = getCommand("test");
        var testResultCmd = getCommand("testresult");
        var autoTestCmd = getCommand("autotest");
        
        if (testCmd != null) {
            testCmd.setExecutor(new TestCommand(testManager));
            getLogger().info("✓ Команда /test зарегистрирована");
        }
        
        if (testResultCmd != null) {
            testResultCmd.setExecutor(new TestResultCommand(testManager));
            getLogger().info("✓ Команда /testresult зарегистрирована");
        }
        
        if (autoTestCmd != null) {
            autoTestCmd.setExecutor(new AutoTestCommand(testManager));
            getLogger().info("✓ Команда /autotest зарегистрирована");
        }
    }
    
    public static EclipsiaTests getInstance() {
        return instance;
    }
    
    public TestManager getTestManager() {
        return testManager;
    }
}
