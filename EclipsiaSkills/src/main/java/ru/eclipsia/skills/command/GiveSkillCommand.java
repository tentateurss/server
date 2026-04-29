package ru.eclipsia.skills.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.eclipse.EclipseItem;

/**
 * Команда для тестирования и получения навыков
 */
public class GiveSkillCommand implements CommandExecutor {
    
    private final EclipsiaSkills plugin;
    
    public GiveSkillCommand(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eclipsia.admin")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§6Использование:");
            sender.sendMessage("§e/giveskill <slot> <тип>");
            sender.sendMessage("§7Типы навыков: melee, arrow, fireball");
            sender.sendMessage("§7Типы поддержек: aoe, multishot, explosion");
            sender.sendMessage("§7Слоты: 0-4 для навыков");
            return true;
        }
        
        int slot;
        try {
            slot = Integer.parseInt(args[0]);
            if (slot < 0 || slot > 4) {
                player.sendMessage("§cСлот должен быть от 0 до 4!");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверный формат слота!");
            return true;
        }
        
        String type = args[1].toLowerCase();
        EclipseItem eclipse = null;
        
        switch (type) {
            case "melee":
                eclipse = new EclipseItem.Builder("melee_strike_test", "Тестовый удар", EclipseItem.EclipseType.SKILL_GEM)
                        .skillClass(EclipseItem.SkillClass.MELEE_STRIKE)
                        .level(1)
                        .manaCost(10)
                        .cooldownTicks(40)
                        .baseDamage(20.0)
                        .description("Тестовый навык ближнего боя")
                        .build();
                break;
                
            case "arrow":
                eclipse = new EclipseItem.Builder("arrow_shot_test", "Тестовый выстрел", EclipseItem.EclipseType.SKILL_GEM)
                        .skillClass(EclipseItem.SkillClass.ARROW_SHOT)
                        .level(1)
                        .manaCost(8)
                        .cooldownTicks(20)
                        .baseDamage(15.0)
                        .description("Тестовый навык стрельбы")
                        .build();
                break;
                
            case "fireball":
                eclipse = new EclipseItem.Builder("fireball_test", "Тестовый огненный шар", EclipseItem.EclipseType.SKILL_GEM)
                        .skillClass(EclipseItem.SkillClass.FIREBALL)
                        .level(1)
                        .manaCost(15)
                        .cooldownTicks(60)
                        .baseDamage(25.0)
                        .description("Тестовый огненный навык")
                        .build();
                break;
                
            case "aoe":
                eclipse = new EclipseItem.Builder("aoe_test", "Тестовое AOE", EclipseItem.EclipseType.SUPPORT_GEM)
                        .supportClass(EclipseItem.SupportClass.AOE_RADIUS)
                        .level(1)
                        .description("Увеличивает радиус действия")
                        .build();
                break;
                
            case "multishot":
                eclipse = new EclipseItem.Builder("multishot_test", "Тестовый мультивыстрел", EclipseItem.EclipseType.SUPPORT_GEM)
                        .supportClass(EclipseItem.SupportClass.MULTI_SHOT)
                        .level(1)
                        .description("Выпускает несколько снарядов")
                        .build();
                break;
                
            case "explosion":
                eclipse = new EclipseItem.Builder("explosion_test", "Тестовый взрыв", EclipseItem.EclipseType.SUPPORT_GEM)
                        .supportClass(EclipseItem.SupportClass.EXPLOSION)
                        .level(1)
                        .description("Добавляет взрыв при попадании")
                        .build();
                break;
                
            default:
                player.sendMessage("§cНеизвестный тип: " + type);
                return true;
        }
        
        if (eclipse.getType() == EclipseItem.EclipseType.SKILL_GEM) {
            boolean success = plugin.getSkillManager().insertSkill(player, slot, eclipse);
            if (success) {
                player.sendMessage("§aНавык §6" + eclipse.getName() + " §aвставлен в слот " + slot);
            }
        } else {
            player.sendMessage("§aПоддержка §d" + eclipse.getName() + " §aполучена!");
            player.sendMessage("§7Откройте §e/skills §7чтобы вставить её к навыку");
            player.getInventory().addItem(eclipse.toItemStack());
        }
        
        return true;
    }
}
