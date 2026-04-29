package ru.eclipsia.items.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.generator.ItemGenerator;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.core.api.EclipsiaAPI;

/**
 * Команда управления предметами
 */
public class ItemCommand implements CommandExecutor {
    
    private final EclipsiaItems plugin;
    private final ItemGenerator generator;
    
    public ItemCommand(EclipsiaItems plugin, ItemGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
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
            case "generate":
                return handleGenerate(sender, args);
            
            case "give":
                return handleGive(sender, args);
            
            case "info":
                return handleInfo(sender);
            
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // /item generate [уровень] [класс]
        int itemLevel = 1;
        String playerClass = null;
        
        if (args.length >= 2) {
            try {
                itemLevel = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cНеверный уровень предмета!");
                return true;
            }
        }
        
        if (args.length >= 3) {
            playerClass = args[2];
        }
        
        // Генерируем предмет
        ItemStack item;
        if (playerClass != null) {
            item = generator.generateItemForClass(playerClass, itemLevel);
        } else {
            item = generator.generateItem(itemLevel);
        }
        
        if (item == null) {
            sender.sendMessage("§cНе удалось сгенерировать предмет!");
            return true;
        }
        
        // Выдаем игроку
        player.getInventory().addItem(item);
        sender.sendMessage("§aПредмет сгенерирован и выдан!");
        
        return true;
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        // /item give <игрок> <id_предмета> [уровень]
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /item give <игрок> <id_предмета> [уровень]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден!");
            return true;
        }
        
        String itemId = args[2];
        int itemLevel = 1;
        
        if (args.length >= 4) {
            try {
                itemLevel = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cНеверный уровень предмета!");
                return true;
            }
        }
        
        // Генерируем предмет
        ItemStack item = generator.generateItemById(itemId, itemLevel);
        
        if (item == null) {
            sender.sendMessage("§cПредмет не найден: " + itemId);
            return true;
        }
        
        // Выдаем игроку
        target.getInventory().addItem(item);
        sender.sendMessage("§aПредмет выдан игроку " + target.getName());
        target.sendMessage("§aВы получили предмет!");
        
        return true;
    }
    
    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage("§6=== EclipsiaItems ===");
        sender.sendMessage("§7Версия: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Базовых предметов: §f" + plugin.getItemManager().getItemCount());
        sender.sendMessage("§7Аффиксов: §f" + plugin.getAffixManager().getAffixCount());
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Команды EclipsiaItems ===");
        sender.sendMessage("§e/item generate [уровень] [класс] §7- Сгенерировать предмет");
        sender.sendMessage("§e/item give <игрок> <id> [уровень] §7- Выдать предмет");
        sender.sendMessage("§e/item info §7- Информация о плагине");
    }
}
