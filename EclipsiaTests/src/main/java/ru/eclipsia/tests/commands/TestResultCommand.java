package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestManager;
import ru.eclipsia.tests.manager.TestResult;

import java.util.List;

/**
 * Команда /testresult - показать результаты тестов
 */
public class TestResultCommand implements CommandExecutor {
    
    private final TestManager testManager;
    
    public TestResultCommand(TestManager testManager) {
        this.testManager = testManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        List<TestResult> results = testManager.getLastResults();
        
        if (results.isEmpty()) {
            player.sendMessage("§cНет результатов тестов. Запустите §e/test all");
            return true;
        }
        
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§lРЕЗУЛЬТАТЫ ТЕСТОВ");
        player.sendMessage("");
        
        int passed = 0;
        int failed = 0;
        
        for (TestResult result : results) {
            player.sendMessage(result.toString());
            
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        
        player.sendMessage("");
        player.sendMessage("§7Итого: §a" + passed + " passed §7| §c" + failed + " failed");
        player.sendMessage("§8§m                                        ");
        
        return true;
    }
}
