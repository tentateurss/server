package ru.eclipsia.lobby.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.lobby.EclipsiaLobby;
import ru.eclipsia.lobby.gui.CharacterCreationGUI;
import ru.eclipsia.lobby.gui.CharacterSelectionGUI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Команда /character для управления персонажами
 */
public class CharacterCommand implements CommandExecutor, TabCompleter {
    
    private final EclipsiaLobby plugin;
    private final CharacterSelectionGUI selectionGUI;
    
    public CharacterCommand(EclipsiaLobby plugin) {
        this.plugin = plugin;
        this.selectionGUI = new CharacterSelectionGUI(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        
        // /character - открыть GUI выбора
        if (args.length == 0) {
            selectionGUI.open(player);
            return true;
        }
        
        // /character create <warrior|archer|mage>
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                player.sendMessage("§cИспользование: /character create <warrior|archer|mage>");
                return true;
            }
            
            String className = args[1].toLowerCase();
            if (!className.equals("warrior") && !className.equals("archer") && !className.equals("mage")) {
                player.sendMessage("§cНеизвестный класс! Доступные: warrior, archer, mage");
                return true;
            }
            
            PlayerData data = plugin.getAPI().getPlayerData(player);
            if (data == null) {
                player.sendMessage("§cОшибка загрузки данных!");
                return true;
            }
            
            if (!data.hasFreeSlot()) {
                player.sendMessage("§cНет свободных слотов для персонажа!");
                return true;
            }
            
            if (plugin.getAPI().createProfile(player, className)) {
                player.sendMessage("§aПерсонаж создан! Класс: §6" + getClassDisplayName(className));
            } else {
                player.sendMessage("§cОшибка создания персонажа!");
            }
            
            return true;
        }
        
        // /character delete <0|1|2>
        if (args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                player.sendMessage("§cИспользование: /character delete <0|1|2>");
                return true;
            }
            
            int slot;
            try {
                slot = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный номер слота! Используйте 0, 1 или 2");
                return true;
            }
            
            if (slot < 0 || slot > 2) {
                player.sendMessage("§cНеверный номер слота! Используйте 0, 1 или 2");
                return true;
            }
            
            PlayerProfile profile = plugin.getAPI().getProfile(player, slot);
            if (profile == null) {
                player.sendMessage("§cСлот " + slot + " уже пуст!");
                return true;
            }
            
            if (plugin.getAPI().deleteProfile(player, slot)) {
                player.sendMessage("§aПерсонаж в слоте " + slot + " удален!");
            } else {
                player.sendMessage("§cОшибка удаления персонажа!");
            }
            
            return true;
        }
        
        // /character select <0|1|2>
        if (args[0].equalsIgnoreCase("select")) {
            if (args.length < 2) {
                player.sendMessage("§cИспользование: /character select <0|1|2>");
                return true;
            }
            
            int slot;
            try {
                slot = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный номер слота! Используйте 0, 1 или 2");
                return true;
            }
            
            if (slot < 0 || slot > 2) {
                player.sendMessage("§cНеверный номер слота! Используйте 0, 1 или 2");
                return true;
            }
            
            PlayerProfile profile = plugin.getAPI().getProfile(player, slot);
            if (profile == null) {
                player.sendMessage("§cСлот " + slot + " пуст!");
                return true;
            }
            
            if (plugin.getAPI().switchProfile(player, slot)) {
                player.sendMessage("§aВы переключились на персонажа §6" + getClassDisplayName(profile.getClassName()));
            } else {
                player.sendMessage("§cОшибка переключения персонажа!");
            }
            
            return true;
        }
        
        player.sendMessage("§cНеизвестная подкоманда! Используйте: create, delete, select");
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "delete", "select"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                completions.addAll(Arrays.asList("warrior", "archer", "mage"));
            } else if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("select")) {
                completions.addAll(Arrays.asList("0", "1", "2"));
            }
        }
        
        return completions;
    }
    
    /**
     * Получить отображаемое имя класса
     */
    private String getClassDisplayName(String className) {
        return switch (className.toLowerCase()) {
            case "warrior" -> "Воин";
            case "archer" -> "Лучник";
            case "mage" -> "Маг";
            default -> className;
        };
    }
}
