package ru.eclipsia.mobs.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.mobs.boss.BossManager;
import ru.eclipsia.mobs.boss.GatekeeperBoss;

/**
 * Команда для управления боссами
 */
public class BossCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eclipsia.admin")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§6Использование:");
            sender.sendMessage("§e/boss spawn gatekeeper §7- Заспавнить Хранителя Врат");
            sender.sendMessage("§e/boss status §7- Статус боссов");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                if (args.length < 2) {
                    sender.sendMessage("§cУкажите тип босса: gatekeeper");
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("gatekeeper")) {
                    spawnGatekeeper(sender);
                } else {
                    sender.sendMessage("§cНеизвестный тип босса: " + args[1]);
                }
                break;
                
            case "status":
                showStatus(sender);
                break;
                
            default:
                sender.sendMessage("§cНеизвестная подкоманда: " + args[0]);
        }
        
        return true;
    }
    
    private void spawnGatekeeper(CommandSender sender) {
        if (BossManager.getInstance().isGatekeeperActive()) {
            sender.sendMessage("§cХранитель Врат уже активен!");
            return;
        }
        
        Location spawnLoc;
        
        if (sender instanceof Player player) {
            spawnLoc = player.getLocation();
        } else {
            // Спавним на арене Хранителя Врат
            World beach = Bukkit.getWorld("beach");
            if (beach == null) {
                sender.sendMessage("§cМир 'beach' не найден!");
                return;
            }
            spawnLoc = new Location(beach, 55, 70, 55);
        }
        
        GatekeeperBoss boss = BossManager.getInstance().getGatekeeper();
        boss.spawn(spawnLoc);
        
        sender.sendMessage("§aХранитель Врат заспавнен!");
    }
    
    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6=== Статус боссов ===");
        
        boolean gatekeeperActive = BossManager.getInstance().isGatekeeperActive();
        sender.sendMessage("§eХранитель Врат: " + (gatekeeperActive ? "§aАктивен" : "§7Неактивен"));
    }
}
