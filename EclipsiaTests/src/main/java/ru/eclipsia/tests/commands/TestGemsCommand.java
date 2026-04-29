package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import ru.eclipsia.skills.eclipse.EclipseItem;

import java.util.List;

/**
 * Dev-команда выдачи skill/support gems для тестов.
 *
 * <ul>
 *   <li>{@code /testgems all} — все skill- и support-камни;</li>
 *   <li>{@code /testgems skills} — только skill (melee/arrow/fireball);</li>
 *   <li>{@code /testgems supports} — только supports (AOE/multi/explosion);</li>
 *   <li>{@code /testgems <id>} — конкретный по EclipseItem.fromId(id).</li>
 * </ul>
 *
 * Ids перечислены вручную в {@link #SKILL_IDS}/{@link #SUPPORT_IDS}, потому
 * что в реестре {@link EclipseItem#fromId(String)} нет сводного списка.
 * При добавлении нового камня в {@code fromId} — добавь его id и сюда.
 */
public class TestGemsCommand implements CommandExecutor {

    private static final List<String> SKILL_IDS = List.of(
            "melee_strike_1",
            "arrow_shot_1",
            "fireball_1"
    );

    private static final List<String> SUPPORT_IDS = List.of(
            "aoe_radius_1",
            "multi_shot_1",
            "explosion_1"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда только для игроков.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "all";

        switch (sub) {
            case "all" -> {
                int n = giveAll(player, SKILL_IDS);
                n += giveAll(player, SUPPORT_IDS);
                player.sendMessage("§a[testgems] Выдано §f" + n + " §aкамней (skill + support).");
            }
            case "skills" -> {
                int n = giveAll(player, SKILL_IDS);
                player.sendMessage("§a[testgems] Выдано §f" + n + " §askill-камней.");
            }
            case "supports" -> {
                int n = giveAll(player, SUPPORT_IDS);
                player.sendMessage("§a[testgems] Выдано §f" + n + " §asupport-камней.");
            }
            default -> {
                EclipseItem item = EclipseItem.fromId(sub);
                if (item == null) {
                    player.sendMessage("§cНеизвестный id: §f" + sub);
                    player.sendMessage("§7Доступно: §fall, skills, supports§7, либо id из:");
                    player.sendMessage("§7  skills: §f" + String.join(", ", SKILL_IDS));
                    player.sendMessage("§7  supports: §f" + String.join(", ", SUPPORT_IDS));
                    return true;
                }
                give(player, item);
                player.sendMessage("§a[testgems] Выдан §f" + item.getName() + " §a(" + sub + ").");
            }
        }
        return true;
    }

    private int giveAll(Player player, List<String> ids) {
        int count = 0;
        for (String id : ids) {
            EclipseItem item = EclipseItem.fromId(id);
            if (item == null) continue;
            give(player, item);
            count++;
        }
        return count;
    }

    private void give(Player player, EclipseItem item) {
        ItemStack stack = item.toItemStack();
        if (stack == null) return;
        var leftover = player.getInventory().addItem(stack);
        // Не влезло в инвентарь — кидаем под ноги.
        leftover.values().forEach(it ->
                player.getWorld().dropItem(player.getLocation(), it));
    }
}
