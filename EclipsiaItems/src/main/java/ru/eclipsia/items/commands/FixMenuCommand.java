package ru.eclipsia.items.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.menu.MenuBook;

import java.util.Map;

/**
 * Команда /fixmenu — возвращает книгу меню в 9-й слот хотбара,
 * НЕ очищая остальной инвентарь (ранее команда затирала навыки).
 * Если слот 8 занят — предмет перемещается в первый свободный слот,
 * либо выбрасывается на землю при полном инвентаре.
 */
public class FixMenuCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;
        ItemStack slot8 = player.getInventory().getItem(8);

        // Если в слоте 8 уже книга — ничего не делаем.
        if (slot8 != null && MenuBook.isMenuBook(slot8)) {
            player.sendMessage("§aКнига меню уже в хотбаре.");
            return true;
        }

        // В слоте 8 что-то другое — переносим в свободный слот.
        if (slot8 != null && !slot8.getType().isAir()) {
            player.getInventory().setItem(8, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(slot8);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        player.getInventory().setItem(8, MenuBook.createMenuBook());
        player.sendMessage("§aКнига меню возвращена в хотбар.");
        return true;
    }
}
