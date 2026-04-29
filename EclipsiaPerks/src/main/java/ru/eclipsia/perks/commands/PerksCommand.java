package ru.eclipsia.perks.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.perks.gui.PerkTreeGUI;
import ru.eclipsia.perks.tree.PerkTreeManager;
import ru.eclipsia.core.api.EclipsiaAPI;

/**
 * Команда /perks - открыть дерево перков
 */
public class PerksCommand implements CommandExecutor {
    
    private final PerkTreeGUI gui;
    private final PerkTreeManager treeManager;
    
    public PerksCommand(PerkTreeGUI gui, PerkTreeManager treeManager) {
        this.gui = gui;
        this.treeManager = treeManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Получаем класс игрока
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        String playerClass = api.getPlayerClassName(player);
        
        if (playerClass == null || playerClass.isEmpty()) {
            player.sendMessage("§cСначала выберите класс: /class");
            return true;
        }
        
        // Получаем стартовый узел для класса
        String startNode = treeManager.getStartNodeForClass(playerClass);
        
        if (startNode == null) {
            player.sendMessage("§cОшибка: стартовый узел не найден для класса " + playerClass);
            return true;
        }
        
        // Открываем GUI
        gui.open(player, startNode);
        
        return true;
    }
}
