package ru.eclipsia.mobs.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.mobs.mob.CustomMob;
import ru.eclipsia.mobs.mob.MobManager;
import ru.eclipsia.mobs.spawn.SpawnManager;

/**
 * Команда /mob - управление мобами
 */
public class MobCommand implements CommandExecutor {
    
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
            case "spawn" -> handleSpawn(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== MOB КОМАНДЫ ===");
        sender.sendMessage("§e/mob spawn <id> §7- Заспавнить моба");
        sender.sendMessage("§e/mob list §7- Список всех мобов");
        sender.sendMessage("§e/mob info <id> §7- Информация о мобе");
    }
    
    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /mob spawn <id>");
            return;
        }
        
        String mobId = args[1];
        CustomMob mob = MobManager.getInstance().getMob(mobId);
        
        if (mob == null) {
            sender.sendMessage("§cМоб не найден: " + mobId);
            return;
        }
        
        SpawnManager.getInstance().spawnMob(mobId, player.getLocation());
        sender.sendMessage("§aЗаспавнен моб: " + mob.getDisplayName());
    }
    
    private void handleList(CommandSender sender) {
        var mobs = MobManager.getInstance().getAllMobs();
        
        sender.sendMessage("§6§l=== СПИСОК МОБОВ ===");
        sender.sendMessage("§7Всего: §e" + mobs.size());
        sender.sendMessage("");
        
        mobs.values().forEach(mob -> {
            sender.sendMessage("§e" + mob.getId() + " §7- " + mob.getDisplayName() + 
                " §7(Ур. " + mob.getLevel() + ")");
        });
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /mob info <id>");
            return;
        }
        
        String mobId = args[1];
        CustomMob mob = MobManager.getInstance().getMob(mobId);
        
        if (mob == null) {
            sender.sendMessage("§cМоб не найден: " + mobId);
            return;
        }
        
        sender.sendMessage("§6§l=== " + mob.getDisplayName() + " ===");
        sender.sendMessage("§7ID: §e" + mob.getId());
        sender.sendMessage("§7Тип: §e" + mob.getEntityType());
        sender.sendMessage("§7Уровень: §e" + mob.getLevel());
        sender.sendMessage("§7Здоровье: §c" + mob.getHealth());
        sender.sendMessage("§7Урон: §c" + mob.getDamage());
        sender.sendMessage("§7Броня: §7" + mob.getArmor());
        sender.sendMessage("§7Опыт: §a" + mob.getExperience());
        sender.sendMessage("§7Орбы: §6" + mob.getDrops().getMinOrbs() + "-" + mob.getDrops().getMaxOrbs());
        sender.sendMessage("§7Зоны спавна: §e" + String.join(", ", mob.getSpawnZones()));
    }
}
