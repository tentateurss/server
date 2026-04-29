package ru.eclipsia.items.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.eclipsia.items.EclipsiaItems;

/**
 * Админские команды для предметов
 */
public class ItemAdminCommand implements CommandExecutor {
    
    private final EclipsiaItems plugin;
    
    public ItemAdminCommand(EclipsiaItems plugin) {
        this.plugin = plugin;
    }
    
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
            case "reload":
                plugin.reloadConfiguration();
                sender.sendMessage("§aКонфигурация перезагружена!");
                return true;
            
            case "debug":
                boolean debug = !plugin.getConfig().getBoolean("debug", false);
                plugin.getConfig().set("debug", debug);
                plugin.saveConfig();
                sender.sendMessage("§aРежим отладки: " + (debug ? "§aВКЛ" : "§cВЫКЛ"));
                return true;
            
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Админские команды EclipsiaItems ===");
        sender.sendMessage("§e/itemadmin reload §7- Перезагрузить конфигурацию");
        sender.sendMessage("§e/itemadmin debug §7- Переключить режим отладки");
    }
}
