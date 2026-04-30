package ru.eclipsia.skills.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.eclipse.EclipseItem;

/**
 * Тестовые команды для быстрой выдачи всех эклипсов и стартовых навыков.
 *
 * <p>Регистрирует:
 * <ul>
 *   <li>{@code /giveall} — выдаёт по одному из каждого активного навыка
 *       и каждого эклипса поддержки в инвентарь, чтобы тестер мог
 *       руками комбинировать и проверять связи навык↔поддержка
 *       (без правки слотов).</li>
 *   <li>{@code /givestarter <warrior|archer|mage>} — выдаёт стартовую
 *       книгу навыка нужного класса (как лобби после выбора).
 *       Без аргумента — берёт класс игрока из его профиля.</li>
 * </ul>
 *
 * <p>Обе команды требуют permission {@code eclipsia.admin}.
 */
public final class GiveAllCommand implements CommandExecutor {

    private static final String[] SKILL_IDS    = {"melee_strike_1", "arrow_shot_1", "fireball_1"};
    private static final String[] SUPPORT_IDS  = {"aoe_radius_1", "multi_shot_1", "explosion_1"};

    private final EclipsiaSkills plugin;
    private final boolean starterMode;

    public GiveAllCommand(EclipsiaSkills plugin, boolean starterMode) {
        this.plugin = plugin;
        this.starterMode = starterMode;
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

        if (starterMode) {
            return handleStarter(player, args);
        }
        return handleAll(player);
    }

    private boolean handleAll(Player player) {
        int given = 0;
        for (String id : SKILL_IDS) {
            EclipseItem item = EclipseItem.fromId(id);
            if (item == null) continue;
            player.getInventory().addItem(item.toItemStack());
            given++;
        }
        for (String id : SUPPORT_IDS) {
            EclipseItem item = EclipseItem.fromId(id);
            if (item == null) continue;
            player.getInventory().addItem(item.toItemStack());
            given++;
        }
        player.sendMessage("§aВыдано эклипсов в инвентарь: §6" + given
                + " §7(3 навыка + 3 поддержки). Открой §e/skills §7чтобы вставлять.");
        return true;
    }

    private boolean handleStarter(Player player, String[] args) {
        String className;
        if (args.length >= 1) {
            className = args[0].toLowerCase();
        } else {
            try {
                ru.eclipsia.core.data.PlayerData data =
                        ru.eclipsia.core.api.EclipsiaAPI.getInstance().getPlayerData(player);
                className = (data != null) ? data.getClassName() : null;
            } catch (Throwable t) {
                className = null;
            }
            if (className == null) {
                player.sendMessage("§cНе удалось определить класс. Использование: §e/givestarter <warrior|archer|mage>");
                return true;
            }
        }

        EclipseItem starter = EclipseItem.createStarterSkill(className);
        if (starter == null) {
            player.sendMessage("§cНеизвестный класс: §e" + className + " §c(допустимо: warrior|archer|mage)");
            return true;
        }

        player.getInventory().addItem(starter.toItemStack());
        player.sendMessage("§aВыдан стартовый навык класса §6" + className + "§a: §e" + starter.getName());
        return true;
    }
}
