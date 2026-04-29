package ru.eclipsia.items.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.items.gui.EquipmentGUI;

/**
 * Команда для открытия экипировки
 */
public class EquipmentCommand implements CommandExecutor {
    
    private final EquipmentGUI equipmentGUI;
    
    public EquipmentCommand(EquipmentGUI equipmentGUI) {
        this.equipmentGUI = equipmentGUI;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        Player player = (Player) sender;
        equipmentGUI.open(player);
        
        return true;
    }
}
