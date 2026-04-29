package ru.eclipsia.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.core.resourcepack.ResourcePackManager;

/**
 * Команда /resourcepack - управление ресурс-паком
 */
public class ResourcePackCommand implements CommandExecutor {
    
    private final ResourcePackManager resourcePackManager;
    
    public ResourcePackCommand(ResourcePackManager resourcePackManager) {
        this.resourcePackManager = resourcePackManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Без аргументов - установить себе
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cЭта команда доступна только игрокам!");
                return true;
            }
            
            if (!resourcePackManager.isEnabled()) {
                player.sendMessage("§cРесурс-пак отключен на сервере.");
                return true;
            }
            
            if (resourcePackManager.hasResourcePack(player)) {
                player.sendMessage("§aУ вас уже установлен ресурс-пак!");
                player.sendMessage("§7Используйте §f/resourcepack reload §7для переустановки.");
            } else {
                resourcePackManager.offerResourcePack(player);
            }
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
            case "reinstall":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cЭта команда доступна только игрокам!");
                    return true;
                }
                
                player.sendMessage("§eПереустановка ресурс-пака...");
                resourcePackManager.forceResourcePack(player);
                break;
                
            case "status":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cЭта команда доступна только игрокам!");
                    return true;
                }
                
                boolean hasRP = resourcePackManager.hasResourcePack(player);
                player.sendMessage("§8§m                                        ");
                player.sendMessage("§6§lСтатус ресурс-пака");
                player.sendMessage("");
                player.sendMessage("§7Установлен: " + (hasRP ? "§a✓ Да" : "§c✗ Нет"));
                
                if (!hasRP) {
                    player.sendMessage("");
                    player.sendMessage("§7Используйте §f/resourcepack §7для установки");
                }
                
                player.sendMessage("§8§m                                        ");
                break;
                
            case "info":
                sender.sendMessage("§8§m                                        ");
                sender.sendMessage("§6§lИнформация о ресурс-паке");
                sender.sendMessage("");
                sender.sendMessage("§7Включен: " + (resourcePackManager.isEnabled() ? "§aДа" : "§cНет"));
                
                if (resourcePackManager.isEnabled()) {
                    int playersWithRP = resourcePackManager.getPlayersWithResourcePack();
                    int totalPlayers = Bukkit.getOnlinePlayers().size();
                    sender.sendMessage("§7Игроков с ресурс-паком: §e" + playersWithRP + "§7/§e" + totalPlayers);
                }
                
                sender.sendMessage("§8§m                                        ");
                break;
                
            case "send":
                if (!sender.hasPermission("eclipsia.admin")) {
                    sender.sendMessage("§cУ вас нет прав на использование этой команды!");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /resourcepack send <игрок>");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок не найден!");
                    return true;
                }
                
                resourcePackManager.forceResourcePack(target);
                sender.sendMessage("§aРесурс-пак отправлен игроку " + target.getName());
                break;
                
            case "help":
            default:
                sender.sendMessage("§8§m                                        ");
                sender.sendMessage("§6§lКоманды ресурс-пака");
                sender.sendMessage("");
                sender.sendMessage("§e/resourcepack §7- Установить ресурс-пак");
                sender.sendMessage("§e/resourcepack reload §7- Переустановить");
                sender.sendMessage("§e/resourcepack status §7- Проверить статус");
                sender.sendMessage("§e/resourcepack info §7- Информация");
                
                if (sender.hasPermission("eclipsia.admin")) {
                    sender.sendMessage("");
                    sender.sendMessage("§cАдмин команды:");
                    sender.sendMessage("§e/resourcepack send <игрок> §7- Отправить игроку");
                }
                
                sender.sendMessage("§8§m                                        ");
                break;
        }
        
        return true;
    }
}
