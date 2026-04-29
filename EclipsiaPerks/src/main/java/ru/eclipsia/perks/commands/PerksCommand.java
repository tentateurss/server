package ru.eclipsia.perks.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.perks.EclipsiaPerks;

/**
 * Команда {@code /perks} — теперь открывает не in-game GUI, а web-дерево.
 * Игроку выводятся URL и 6-значный код для входа.
 */
public class PerksCommand implements CommandExecutor {

    private final EclipsiaPerks plugin;

    public PerksCommand(EclipsiaPerks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        PerksCodeCommand.send(player, plugin);
        return true;
    }
}
