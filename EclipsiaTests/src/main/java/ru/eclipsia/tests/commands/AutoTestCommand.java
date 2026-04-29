package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.tests.manager.TestManager;

/**
 * Команда /autotest - автоматическое тестирование
 */
public class AutoTestCommand implements CommandExecutor {
    
    private final TestManager testManager;
    
    public AutoTestCommand(TestManager testManager) {
        this.testManager = testManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        player.sendMessage("§eАвтоматическое тестирование пока не реализовано.");
        player.sendMessage("§7Используйте §f/test all §7для запуска всех тестов.");
        
        return true;
    }
}
