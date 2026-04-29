package ru.eclipsia.builder.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.eclipsia.builder.EclipsiaBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Команда /build для управления структурами
 */
public class BuildCommand implements CommandExecutor, TabCompleter {
    
    private final EclipsiaBuilder plugin;
    
    public BuildCommand(EclipsiaBuilder plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        
        if (!player.hasPermission("eclipsia.admin")) {
            player.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage("§6=== EclipsiaBuilder ===");
            player.sendMessage("§e/build <structure_id> §7- построить структуру");
            player.sendMessage("§e/build all §7- построить все структуры");
            player.sendMessage("§e/build list §7- список структур");
            player.sendMessage("§e/build reload §7- перезагрузить конфиг");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "all" -> {
                player.sendMessage("§aНачинаю генерацию всех структур...");
                int count = plugin.getStructureManager().buildAll();
                player.sendMessage("§aПостроено структур: §e" + count);
            }
            
            case "list" -> {
                player.sendMessage("§6=== Доступные структуры ===");
                plugin.getStructureManager().getStructureIds().forEach(id -> 
                    player.sendMessage("§e- " + id)
                );
            }
            
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getStructureManager().loadStructures();
                player.sendMessage("§aКонфигурация перезагружена!");
            }
            
            default -> {
                // Попытка построить конкретную структуру
                if (plugin.getStructureManager().buildStructure(args[0])) {
                    player.sendMessage("§aСтруктура §e" + args[0] + " §aпостроена!");
                } else {
                    player.sendMessage("§cСтруктура §e" + args[0] + " §cне найдена!");
                }
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("all");
            completions.add("list");
            completions.add("reload");
            completions.addAll(plugin.getStructureManager().getStructureIds());
        }
        
        return completions;
    }
}
