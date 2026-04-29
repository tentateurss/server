package ru.eclipsia.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.permissions.PermissionManager;

/**
 * Команда /data - управление данными (только для администраторов)
 */
public class DataCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.getInstance().checkAdmin(sender)) {
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§6=== Команды управления данными ===");
            sender.sendMessage("§e/data migrate §7- Миграция из PDC в SQLite");
            sender.sendMessage("§e/data info §7- Информация о хранилище");
            sender.sendMessage("§e/data stats §7- Статистика кэша");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "migrate" -> {
                return handleMigrate(sender);
            }
            case "info" -> {
                return handleInfo(sender);
            }
            case "stats" -> {
                return handleStats(sender);
            }
            default -> {
                sender.sendMessage("§cНеизвестная подкоманда: " + args[0]);
                return true;
            }
        }
    }
    
    private boolean handleMigrate(CommandSender sender) {
        sender.sendMessage("§eЗапуск миграции данных из PDC в SQLite...");
        sender.sendMessage("§7Это может занять некоторое время...");
        
        DataManager.getInstance().migrateFromPDC().thenAccept(count -> {
            if (count > 0) {
                sender.sendMessage("§a✓ Миграция завершена успешно!");
                sender.sendMessage("§7Мигрировано игроков: §e" + count);
            } else {
                sender.sendMessage("§eМиграция не требуется или данные отсутствуют.");
            }
        }).exceptionally(ex -> {
            sender.sendMessage("§c✗ Ошибка миграции: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
        
        return true;
    }
    
    private boolean handleInfo(CommandSender sender) {
        DataManager manager = DataManager.getInstance();
        
        sender.sendMessage("§6=== Информация о хранилище ===");
        sender.sendMessage("§7Тип хранилища: §e" + manager.getStorage().getStorageType());
        
        if (manager.getStorage() instanceof ru.eclipsia.core.data.storage.SQLiteDataStorage sqlite) {
            sender.sendMessage("§7Подключение: §e" + (sqlite.isConnected() ? "Активно" : "Неактивно"));
            sender.sendMessage("§7Записей в БД: §e" + sqlite.getPlayerCount());
        }
        
        return true;
    }
    
    private boolean handleStats(CommandSender sender) {
        DataManager manager = DataManager.getInstance();
        DataManager.CacheStats stats = manager.getCacheStats();
        
        sender.sendMessage("§6=== Статистика кэша ===");
        sender.sendMessage("§7Всего в кэше: §e" + stats.totalCached());
        sender.sendMessage("§7Онлайн игроков: §e" + stats.onlineCached());
        
        return true;
    }
}
