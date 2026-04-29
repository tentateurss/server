package ru.eclipsia.skills.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.gui.SkillsGUI;

/**
 * Команда /skills для открытия GUI навыков
 */
public class SkillsCommand implements CommandExecutor {
    
    private final EclipsiaSkills plugin;
    
    public SkillsCommand(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        // Открываем GUI навыков
        new SkillsGUI(plugin.getSkillManager()).open(player);
        
        return true;
    }
}
