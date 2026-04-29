package ru.eclipsia.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.gui.StatsGUI;
import ru.eclipsia.core.stats.StatsBonusApplier;

/**
 * Команда /stats - просмотр и распределение характеристик
 */
public class StatsCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        // ЛОГИРОВАНИЕ
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Игрок: " + player.getName() + ", Аргументы: " + String.join(" ", args));
        
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        
        if (data == null) {
            player.sendMessage("§cОшибка загрузки данных. Попробуйте перезайти.");
            return true;
        }
        
        if (data.getClassName() == null) {
            player.sendMessage("§cСначала выберите класс: /class");
            return true;
        }
        
        // Если есть аргументы - обрабатываем команду добавления статов
        if (args.length > 0 && args[0].equalsIgnoreCase("add")) {
            org.bukkit.Bukkit.getLogger().info("[StatsCommand] Вызов handleAddStat");
            handleAddStat(player, data, args);
            return true;
        }
        
        // Открываем GUI статов
        StatsGUI.open(player);
        return true;
    }
    
    private void handleAddStat(Player player, PlayerData data, String[] args) {
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] handleAddStat вызван, args.length=" + args.length);
        
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /stats add <сила|ловкость|интеллект> [количество]");
            return;
        }
        
        // Проверяем свободные очки
        if (data.getFreeStatPoints() <= 0) {
            player.sendMessage("§cУ вас нет свободных очков характеристик!");
            return;
        }
        
        // Определяем стат
        String statName = args[1].toLowerCase();
        String statId;
        String displayName;
        
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Определение стата: " + statName);
        
        if (statName.contains("сил") || statName.equals("strength") || statName.equals("str")) {
            statId = "strength";
            displayName = "§cСила";
        } else if (statName.contains("ловк") || statName.equals("dexterity") || statName.equals("dex")) {
            statId = "dexterity";
            displayName = "§aЛовкость";
        } else if (statName.contains("инт") || statName.equals("intelligence") || statName.equals("int")) {
            statId = "intelligence";
            displayName = "§9Интеллект";
        } else {
            player.sendMessage("§cНеизвестная характеристика: " + args[1]);
            player.sendMessage("§7Доступные: §fсила, ловкость, интеллект");
            return;
        }
        
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Стат определен: " + statId);
        
        // Определяем количество очков
        int points = 1;
        if (args.length >= 3) {
            try {
                points = Integer.parseInt(args[2]);
                if (points < 1) {
                    player.sendMessage("§cКоличество должно быть больше 0");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверное количество: " + args[2]);
                return;
            }
        }
        
        // Проверяем достаточно ли очков
        if (points > data.getFreeStatPoints()) {
            player.sendMessage("§cНедостаточно свободных очков! Доступно: §e" + data.getFreeStatPoints());
            return;
        }
        
        // Добавляем стат
        int currentValue = data.getStat(statId);
        int newValue = currentValue + points;
        
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Добавление стата: " + statId + " " + currentValue + " -> " + newValue);
        
        PlayerData updatedData = data.toBuilder()
                .stat(statId, newValue)
                .freeStatPoints(data.getFreeStatPoints() - points)
                .build();
        
        // Сохраняем
        DataManager.getInstance().savePlayer(updatedData);
        
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Данные сохранены, применяем бонусы");
        
        // КРИТИЧНО: Применяем бонусы от статов
        StatsBonusApplier.applyAllBonuses(player);
        
        org.bukkit.Bukkit.getLogger().info("[StatsCommand] Бонусы применены");
        
        // Сообщение игроку
        player.sendMessage("§a✓ Характеристика повышена!");
        player.sendMessage(displayName + " §7: §f" + currentValue + " §7→ §a" + newValue + " §7(+" + points + ")");
        player.sendMessage("§7Свободных очков: §e" + updatedData.getFreeStatPoints());
    }
}
