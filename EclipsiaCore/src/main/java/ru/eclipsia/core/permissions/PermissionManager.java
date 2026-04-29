package ru.eclipsia.core.permissions;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Менеджер прав доступа для игроков и админов
 */
public class PermissionManager {
    
    private static PermissionManager instance;
    
    private final Plugin plugin;
    private List<String> admins;
    
    private PermissionManager(Plugin plugin) {
        this.plugin = plugin;
        loadAdmins();
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("PermissionManager уже инициализирован!");
        }
        instance = new PermissionManager(plugin);
    }
    
    public static PermissionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PermissionManager не инициализирован!");
        }
        return instance;
    }
    
    private void loadAdmins() {
        admins = plugin.getConfig().getStringList("admins");
        plugin.getLogger().info("Загружено администраторов: " + admins.size());
        admins.forEach(admin -> plugin.getLogger().info("  - " + admin));
    }
    
    /**
     * Проверка является ли игрок администратором
     */
    public boolean isAdmin(Player player) {
        return admins.contains(player.getName()) || player.isOp();
    }
    
    /**
     * Проверка является ли отправитель команды администратором
     */
    public boolean isAdmin(CommandSender sender) {
        if (sender instanceof Player player) {
            return isAdmin(player);
        }
        return true; // Консоль всегда админ
    }
    
    /**
     * Проверка прав с отправкой сообщения об ошибке
     */
    public boolean checkAdmin(CommandSender sender) {
        if (!isAdmin(sender)) {
            sender.sendMessage("§c✗ У вас нет прав на использование этой команды!");
            sender.sendMessage("§7Только администраторы могут использовать эту команду.");
            return false;
        }
        return true;
    }
    
    /**
     * Перезагрузка списка админов из конфига
     */
    public void reload() {
        loadAdmins();
    }
    
    /**
     * Получить список всех админов
     */
    public List<String> getAdmins() {
        return List.copyOf(admins);
    }
    
    /**
     * Добавить админа (только в памяти, не сохраняется в конфиг)
     */
    public void addAdmin(String playerName) {
        if (!admins.contains(playerName)) {
            admins.add(playerName);
            plugin.getLogger().info("Добавлен администратор: " + playerName);
        }
    }
    
    /**
     * Удалить админа (только в памяти, не сохраняется в конфиг)
     */
    public void removeAdmin(String playerName) {
        if (admins.remove(playerName)) {
            plugin.getLogger().info("Удален администратор: " + playerName);
        }
    }
}
