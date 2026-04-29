package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestManager;

/**
 * Команда /test - запуск тестов
 */
public class TestCommand implements CommandExecutor {
    
    private final TestManager testManager;
    
    public TestCommand(TestManager testManager) {
        this.testManager = testManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String testType = args[0].toLowerCase();
        
        switch (testType) {
            case "all":
                testManager.runAllTests(player);
                break;
                
            case "gui":
                testManager.runTestsByCategory(player, "gui");
                break;
                
            case "system":
                testManager.runTestsByCategory(player, "system");
                break;
                
            case "integration":
                testManager.runTestsByCategory(player, "integration");
                break;
                
            case "list":
                showTestList(player);
                break;
                
            default:
                player.sendMessage("§cНеизвестная категория: " + testType);
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lСИСТЕМА ТЕСТИРОВАНИЯ");
        player.sendMessage("");
        player.sendMessage("§e/test all §7- Запустить все тесты");
        player.sendMessage("§e/test gui §7- Тесты GUI систем");
        player.sendMessage("§e/test system §7- Тесты игровых систем");
        player.sendMessage("§e/test integration §7- Интеграционные тесты");
        player.sendMessage("§e/test list §7- Список всех тестов");
        player.sendMessage("");
        player.sendMessage("§e/testresult §7- Результаты последних тестов");
        player.sendMessage("§8§m                                        ");
    }
    
    private void showTestList(Player player) {
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lСПИСОК ТЕСТОВ");
        player.sendMessage("");
        
        var categories = testManager.getCategories();
        for (String category : categories) {
            player.sendMessage("§e" + category.toUpperCase() + ":");
            
            for (var test : testManager.getAllTests()) {
                if (test.getCategory().equalsIgnoreCase(category)) {
                    player.sendMessage("  §7- " + test.getName());
                    player.sendMessage("    §8" + test.getDescription());
                }
            }
            player.sendMessage("");
        }
        
        player.sendMessage("§7Всего тестов: §e" + testManager.getTestCount());
        player.sendMessage("§8§m                                        ");
    }
}
