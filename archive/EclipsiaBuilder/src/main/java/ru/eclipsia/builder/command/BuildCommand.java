package ru.eclipsia.builder.command;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
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
            player.sendMessage("§e/build regen-beach §7- §lпересоздать Берег§r §7(force)");
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

            case "regen-beach", "regen", "regenerate" -> {
                player.sendMessage("§eПринудительная перегенерация Берега…");
                if (plugin.forceRegenerateBeach()) {
                    player.sendMessage("§aСтарт фоновой генерации. Подожди ~1-2 минуты, следи за консолью.");
                } else {
                    player.sendMessage("§cМир 'beach' не загружен.");
                }
            }

            case "clearmannequins", "cleardummies", "clearmanikens" -> {
                // v10: ручная чистка остатков голограмм манекенов из beach-мира.
                org.bukkit.World beach = Bukkit.getWorld("beach");
                if (beach == null) {
                    player.sendMessage("§cМир 'beach' не загружен.");
                    return true;
                }
                int removed = 0;
                String[] markers = {
                        "Манекен", "Чемпион Берега", "Мишень для лучника",
                        "Щит-стенка", "Лучник", "Воин", "Берсерк", "Маг",
                        "Рыцарь", "Некромант", "Витязь", "Бей мечом",
                        "Бей по ним мечом", "Тренировочный манекен",
                        "Бронированный манекен"
                };
                for (org.bukkit.entity.Entity e : beach.getEntities()) {
                    if (!(e instanceof org.bukkit.entity.TextDisplay td)) continue;
                    String t = td.getText();
                    if (t == null) continue;
                    for (String m : markers) {
                        if (t.contains(m)) {
                            e.remove();
                            removed++;
                            break;
                        }
                    }
                }
                // Заодно удалить блоки манекенов в районе лагеря (если остались)
                int cx = 0, cz = -55;
                int feetY = 5;
                int blocksRemoved = 0;
                for (int dx = -10; dx <= 10; dx++) {
                    for (int dz = -10; dz <= 10; dz++) {
                        for (int dy = 0; dy <= 4; dy++) {
                            org.bukkit.block.Block b = beach.getBlockAt(cx + dx, feetY + dy, cz + dz);
                            org.bukkit.Material m = b.getType();
                            if (m == org.bukkit.Material.HAY_BLOCK
                                    || m == org.bukkit.Material.CARVED_PUMPKIN
                                    || m == org.bukkit.Material.SKELETON_SKULL
                                    || m == org.bukkit.Material.WITHER_SKELETON_SKULL
                                    || m == org.bukkit.Material.ZOMBIE_HEAD
                                    || m == org.bukkit.Material.PLAYER_HEAD
                                    || m == org.bukkit.Material.RED_WOOL
                                    || m == org.bukkit.Material.WHITE_WOOL
                                    || m == org.bukkit.Material.IRON_BARS
                                    || m == org.bukkit.Material.OAK_FENCE
                                    || m == org.bukkit.Material.DARK_OAK_FENCE
                                    || m == org.bukkit.Material.POLISHED_BLACKSTONE) {
                                b.setType(org.bukkit.Material.AIR);
                                blocksRemoved++;
                            }
                        }
                    }
                }
                player.sendMessage("§aУдалено голограмм: §e" + removed
                        + "§a, блоков манекенов: §e" + blocksRemoved);
            }

            case "resetboss", "bossreset" -> {
                // v8: сброс PDC-флага «убил Хранителя» для целевого игрока (или себя).
                Player target = (args.length >= 2)
                        ? Bukkit.getPlayerExact(args[1]) : player;
                if (target == null) {
                    player.sendMessage("§cИгрок не найден или offline.");
                    return true;
                }
                Plugin mobs = Bukkit.getPluginManager().getPlugin("EclipsiaMobs");
                if (mobs == null) {
                    player.sendMessage("§cEclipsiaMobs не загружен.");
                    return true;
                }
                NamespacedKey key = new NamespacedKey(mobs, "eclipsia_gatekeeper_defeated");
                target.getPersistentDataContainer().remove(key);
                player.sendMessage("§aУ игрока §e" + target.getName()
                        + " §aсброшен флаг победы над Хранителем.");
                target.sendMessage("§eТвой прогресс по Хранителю Врат сброшен. Можешь снова идти на арену.");
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
            completions.add("regen-beach");
            completions.addAll(plugin.getStructureManager().getStructureIds());
        }
        
        return completions;
    }
}
