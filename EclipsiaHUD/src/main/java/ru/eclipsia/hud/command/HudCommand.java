package ru.eclipsia.hud.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.eclipsia.core.combat.DamageType;
import ru.eclipsia.hud.EclipsiaHUD;
import ru.eclipsia.hud.api.EclipsiaHUDAPI;
import ru.eclipsia.hud.theme.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /hud …} — управление UI Eclipsia.
 *
 * <p>Поддерживает: {@code sidebar}, {@code tablist}, {@code reload},
 * {@code test &lt;title|damage|label&gt;}, {@code labels clear}.
 *
 * <p>Команды {@code reload} и {@code test} требуют {@code eclipsia.hud.admin}.
 */
public final class HudCommand implements CommandExecutor, TabCompleter {

    private final EclipsiaHUD plugin;

    public HudCommand(EclipsiaHUD plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        EclipsiaHUDAPI api = EclipsiaHUDAPI.getInstance();

        switch (sub) {
            case "sidebar" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Только в игре.");
                    return true;
                }
                if (api == null) {
                    p.sendMessage("HUD API недоступен.");
                    return true;
                }
                boolean newState = !api.isSidebarVisible(p);
                api.setSidebarVisible(p, newState);
                p.sendMessage(Theme.mm("<gray>sidebar: <white>"
                        + (newState ? "on" : "off") + "</white></gray>"));
                return true;
            }
            case "tablist" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Только в игре.");
                    return true;
                }
                if (api == null) return true;
                // нет «toggle» state-tracking для tablist — переключаем
                // через скрытие путём отправки пустых компонентов
                api.setTabListVisible(p, true);
                p.sendMessage(Theme.mm("<gray>tablist обновлён</gray>"));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("eclipsia.hud.admin")) {
                    sender.sendMessage("Нет прав.");
                    return true;
                }
                plugin.reloadEverything();
                sender.sendMessage(Theme.mm("<green>EclipsiaHUD: config перечитан.</green>"));
                return true;
            }
            case "test" -> {
                if (!sender.hasPermission("eclipsia.hud.admin")) {
                    sender.sendMessage("Нет прав.");
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Только в игре.");
                    return true;
                }
                if (api == null) return true;
                String which = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "title";
                switch (which) {
                    case "title" -> api.showLevelUp(p, 99);
                    case "welcome" -> api.showWelcome(p);
                    case "boss" -> api.showBossSpawn(p, "Хранитель Врат");
                    case "region" -> api.showRegionEnter(p,
                            Theme.mm("<gradient:#ffd166:#fff>✦ Test Zone</gradient>"));
                    case "damage" -> api.showDamage(p, 142.5, DamageType.CRIT);
                    case "label" -> {
                        api.spawnLabel(p.getLocation().add(0, 1.5, 0),
                                Theme.mm("<gradient:#ffd166:#ef476f>✦ <player></gradient>",
                                        java.util.Map.of("player", p.getName())),
                                100);
                    }
                    default -> p.sendMessage("Доступно: title|welcome|boss|region|damage|label");
                }
                return true;
            }
            case "labels" -> {
                if (!sender.hasPermission("eclipsia.hud.admin")) {
                    sender.sendMessage("Нет прав.");
                    return true;
                }
                if (api == null) return true;
                if (args.length > 1 && "clear".equalsIgnoreCase(args[1])) {
                    int n = api.clearAllLabels();
                    sender.sendMessage(Theme.mm("<gray>удалено меток: <white>" + n + "</white></gray>"));
                } else {
                    sender.sendMessage("Использование: /hud labels clear");
                }
                return true;
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Eclipsia HUD"));
        sender.sendMessage("  /hud sidebar          — переключить sidebar");
        sender.sendMessage("  /hud tablist          — обновить TAB header/footer");
        sender.sendMessage("  /hud reload           — перечитать config");
        sender.sendMessage("  /hud test <type>      — title|welcome|boss|region|damage|label");
        sender.sendMessage("  /hud labels clear     — снять все плавающие метки");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("sidebar", "tablist", "reload", "test", "labels"), args[0]);
        }
        if (args.length == 2 && "test".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("title", "welcome", "boss", "region", "damage", "label"), args[1]);
        }
        if (args.length == 2 && "labels".equalsIgnoreCase(args[0])) {
            return filter(List.of("clear"), args[1]);
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(options.size());
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }
}
