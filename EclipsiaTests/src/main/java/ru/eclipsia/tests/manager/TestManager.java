package ru.eclipsia.tests.manager;

import org.bukkit.entity.Player;
import ru.eclipsia.tests.EclipsiaTests;
import ru.eclipsia.tests.tests.*;

import java.util.*;

/**
 * Менеджер тестов
 */
public class TestManager {
    
    private final EclipsiaTests plugin;
    private final Map<String, BaseTest> tests;
    private final List<TestResult> lastResults;
    
    public TestManager(EclipsiaTests plugin) {
        this.plugin = plugin;
        this.tests = new HashMap<>();
        this.lastResults = new ArrayList<>();
        
        // Регистрация всех тестов
        registerTests();
    }
    
    /**
     * Регистрация всех тестов
     */
    private void registerTests() {
        // GUI тесты
        registerTest(new ClassGUITest());
        registerTest(new StatsGUITest());
        registerTest(new ProfileGUITest());
        registerTest(new MenuBookTest());
        
        // Система тесты
        registerTest(new DataSaveTest());
        registerTest(new EquipmentTest());
        registerTest(new QuickEquipTest());
        
        // Интеграционные тесты
        registerTest(new NavigationTest());
        registerTest(new PermissionsTest());
    }
    
    /**
     * Зарегистрировать тест
     */
    private void registerTest(BaseTest test) {
        tests.put(test.getId(), test);
    }
    
    /**
     * Запустить все тесты
     */
    public List<TestResult> runAllTests(Player player) {
        lastResults.clear();
        
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lЗАПУСК ВСЕХ ТЕСТОВ");
        player.sendMessage("");
        
        int passed = 0;
        int failed = 0;
        
        for (BaseTest test : tests.values()) {
            player.sendMessage("§7Тест: §e" + test.getName() + "§7...");
            
            TestResult result = test.run(player);
            lastResults.add(result);
            
            if (result.isPassed()) {
                player.sendMessage("  §a✓ PASSED §7- " + result.getMessage());
                passed++;
            } else {
                player.sendMessage("  §c✗ FAILED §7- " + result.getMessage());
                failed++;
            }
        }
        
        player.sendMessage("");
        player.sendMessage("§7Результаты: §a" + passed + " passed §7| §c" + failed + " failed");
        player.sendMessage("§8§m                                        ");
        
        return new ArrayList<>(lastResults);
    }
    
    /**
     * Запустить тест по категории
     */
    public List<TestResult> runTestsByCategory(Player player, String category) {
        lastResults.clear();
        
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lЗАПУСК ТЕСТОВ: §e" + category.toUpperCase());
        player.sendMessage("");
        
        int passed = 0;
        int failed = 0;
        
        for (BaseTest test : tests.values()) {
            if (!test.getCategory().equalsIgnoreCase(category)) {
                continue;
            }
            
            player.sendMessage("§7Тест: §e" + test.getName() + "§7...");
            
            TestResult result = test.run(player);
            lastResults.add(result);
            
            if (result.isPassed()) {
                player.sendMessage("  §a✓ PASSED §7- " + result.getMessage());
                passed++;
            } else {
                player.sendMessage("  §c✗ FAILED §7- " + result.getMessage());
                failed++;
            }
        }
        
        player.sendMessage("");
        player.sendMessage("§7Результаты: §a" + passed + " passed §7| §c" + failed + " failed");
        player.sendMessage("§8§m                                        ");
        
        return new ArrayList<>(lastResults);
    }
    
    /**
     * Запустить конкретный тест
     */
    public TestResult runTest(Player player, String testId) {
        BaseTest test = tests.get(testId);
        
        if (test == null) {
            return new TestResult(testId, "Unknown Test", false, "Тест не найден");
        }
        
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lЗАПУСК ТЕСТА: §e" + test.getName());
        player.sendMessage("");
        
        TestResult result = test.run(player);
        lastResults.clear();
        lastResults.add(result);
        
        if (result.isPassed()) {
            player.sendMessage("§a✓ PASSED §7- " + result.getMessage());
        } else {
            player.sendMessage("§c✗ FAILED §7- " + result.getMessage());
        }
        
        player.sendMessage("§8§m                                        ");
        
        return result;
    }
    
    /**
     * Получить последние результаты
     */
    public List<TestResult> getLastResults() {
        return new ArrayList<>(lastResults);
    }
    
    /**
     * Получить количество тестов
     */
    public int getTestCount() {
        return tests.size();
    }
    
    /**
     * Получить список категорий
     */
    public Set<String> getCategories() {
        Set<String> categories = new HashSet<>();
        for (BaseTest test : tests.values()) {
            categories.add(test.getCategory());
        }
        return categories;
    }
    
    /**
     * Получить все тесты
     */
    public Collection<BaseTest> getAllTests() {
        return tests.values();
    }
}
