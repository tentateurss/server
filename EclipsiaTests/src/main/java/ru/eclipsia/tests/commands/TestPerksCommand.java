package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import ru.eclipsia.perks.EclipsiaPerks;
import ru.eclipsia.perks.node.PerkNode;
import ru.eclipsia.perks.player.PlayerPerkData;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

/**
 * Dev-команда выдачи перков для тестирования веток дерева.
 *
 * <ul>
 *   <li>{@code /testperks all} — добавить очков и взять ВСЕ узлы дерева;</li>
 *   <li>{@code /testperks points <n>} — выдать N свободных очков;</li>
 *   <li>{@code /testperks reset} — сбросить все взятые узлы.</li>
 * </ul>
 *
 * Команда доступна только под пермой {@code eclipsia.admin}, чтобы её
 * нельзя было использовать в обычном гэймплее.
 */
public class TestPerksCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда только для игроков.");
            return true;
        }

        EclipsiaPerks perksPlugin = EclipsiaPerks.getInstance();
        if (perksPlugin == null) {
            player.sendMessage("§cEclipsiaPerks не загружен.");
            return true;
        }

        PerkTreeManager tree = perksPlugin.getTreeManager();
        PlayerPerkManager perkMgr = perksPlugin.getPlayerManager();
        PlayerPerkData data = perkMgr.getPlayerData(player);

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "all" -> {
                int total = 0;
                int alreadyHad = 0;
                int allocated = 0;
                // Запас очков с потолком: бесплатно «купим» все узлы.
                data.setAvailablePoints(99999);
                for (PerkNode node : tree.getAllNodes()) {
                    total++;
                    if (data.hasNode(node.getId())) {
                        alreadyHad++;
                        continue;
                    }
                    int cost = Math.max(0, node.getCost());
                    if (data.allocateNode(node.getId(), cost)) {
                        allocated++;
                    }
                }
                perkMgr.savePlayerData(player.getUniqueId());
                player.sendMessage("§a[testperks] Выдано все " + total + " узлов: §f"
                        + allocated + " §aвзято, §f" + alreadyHad + " §aуже было. "
                        + "§7Очков осталось: §f" + data.getAvailablePoints());
            }
            case "points" -> {
                int n = 100;
                if (args.length >= 2) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cНе число: " + args[1]);
                        return true;
                    }
                }
                data.addPoints(n);
                perkMgr.savePlayerData(player.getUniqueId());
                player.sendMessage("§a[testperks] Выдано §f" + n + " §aочков. "
                        + "§7Сейчас: §f" + data.getAvailablePoints());
            }
            case "reset" -> {
                int had = data.getAllocatedCount();
                data.resetAll();
                perkMgr.savePlayerData(player.getUniqueId());
                player.sendMessage("§a[testperks] Сброшено §f" + had + " §aузлов.");
            }
            default -> {
                player.sendMessage("§e/testperks all §7— взять все узлы");
                player.sendMessage("§e/testperks points <n> §7— выдать очки");
                player.sendMessage("§e/testperks reset §7— сбросить все узлы");
            }
        }
        return true;
    }
}
