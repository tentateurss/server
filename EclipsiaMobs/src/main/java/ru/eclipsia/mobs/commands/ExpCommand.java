package ru.eclipsia.mobs.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.mobs.experience.ExperienceManager;

/**
 * Команда /exp - управление опытом
 */
public class ExpCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eclipsia.admin")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(sender, args);
            case "set" -> handleSet(sender, args);
            case "show" -> handleShow(sender, args);
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== EXP КОМАНДЫ ===");
        sender.sendMessage("§e/exp add <игрок> <количество> §7- Добавить опыт");
        sender.sendMessage("§e/exp set <игрок> <уровень> §7- Установить уровень");
        sender.sendMessage("§e/exp show <игрок> §7- Показать опыт игрока");
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /exp add <игрок> <количество>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            ExperienceManager.getInstance().addExperience(target, amount);
            sender.sendMessage("§aДобавлено §e" + amount + " §aопыта игроку " + target.getName());
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверное количество: " + args[2]);
        }
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /exp set <игрок> <уровень>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        try {
            int level = Integer.parseInt(args[2]);
            ExperienceManager.getInstance().setLevel(target, level);
            sender.sendMessage("§aУровень игрока " + target.getName() + " установлен на " + level);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный уровень: " + args[2]);
        }
    }
    
    private void handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /exp show <игрок>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        var coreAPI = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
        var data = coreAPI.getPlayerData(target);
        
        if (data == null) {
            sender.sendMessage("§cДанные игрока не загружены");
            return;
        }
        
        int expNeeded = ExperienceManager.getInstance().getExpForLevel(data.getLevel() + 1);
        double progress = ExperienceManager.getInstance().getLevelProgress(target);
        
        sender.sendMessage("§6§l=== ОПЫТ: " + target.getName() + " ===");
        sender.sendMessage("§7Уровень: §e" + data.getLevel());
        sender.sendMessage("§7Опыт: §e" + data.getExperience() + " §7/ §e" + expNeeded);
        sender.sendMessage("§7Прогресс: §e" + String.format("%.1f", progress * 100) + "%");
    }
}
