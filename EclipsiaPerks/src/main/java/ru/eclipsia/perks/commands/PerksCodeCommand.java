package ru.eclipsia.perks.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.perks.EclipsiaPerks;
import ru.eclipsia.perks.web.PerkAuthCodes;

/**
 * Команда {@code /perkscode} — выдаёт игроку URL и код для входа в web-дерево
 * перков. Также вызывается из MainMenuGUI/MenuBookListener при клике по
 * иконке перков.
 */
public class PerksCodeCommand implements CommandExecutor {

    private final EclipsiaPerks plugin;

    public PerksCodeCommand(EclipsiaPerks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        send(player, plugin);
        return true;
    }

    /** Сформатировать и отправить игроку URL+код в чат. */
    public static void send(Player player, EclipsiaPerks plugin) {
        int code = PerkAuthCodes.getOrCreate(player.getUniqueId());
        String url = buildUrl(plugin);
        String codeStr = PerkAuthCodes.format(code);

        // Adventure-компоненты с click-actions: URL открывается в браузере,
        // ник и код копируются в буфер обмена. Hover-подсказки на всё.
        player.sendMessage(net.kyori.adventure.text.Component.empty());
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "⟡ Дерево перков (web) ⟡",
                net.kyori.adventure.text.format.NamedTextColor.GOLD,
                net.kyori.adventure.text.format.TextDecoration.BOLD));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Открой ссылку в браузере и войди по нику + коду:",
                net.kyori.adventure.text.format.NamedTextColor.GRAY));

        net.kyori.adventure.text.Component urlComp = net.kyori.adventure.text.Component
                .text(url, net.kyori.adventure.text.format.NamedTextColor.AQUA,
                        net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text("Кликни — откроет в браузере",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
        player.sendMessage(urlComp);

        net.kyori.adventure.text.Component nickComp = net.kyori.adventure.text.Component
                .text("Ник: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(player.getName(),
                                net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(player.getName()))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Кликни — скопирует ник",
                                        net.kyori.adventure.text.format.NamedTextColor.YELLOW))))
                .append(net.kyori.adventure.text.Component.text("  [копировать]",
                                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(player.getName())));
        player.sendMessage(nickComp);

        net.kyori.adventure.text.Component codeComp = net.kyori.adventure.text.Component
                .text("Код: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(codeStr,
                                net.kyori.adventure.text.format.NamedTextColor.GREEN,
                                net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(codeStr))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Кликни — скопирует код",
                                        net.kyori.adventure.text.format.NamedTextColor.YELLOW))))
                .append(net.kyori.adventure.text.Component.text("  [копировать]",
                                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(codeStr)));
        player.sendMessage(codeComp);

        player.sendMessage(net.kyori.adventure.text.Component.text(
                "(Код одноразовый на сессию. /perkscode выдаст новый.)",
                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        player.sendMessage(net.kyori.adventure.text.Component.empty());
    }

    private static String buildUrl(EclipsiaPerks plugin) {
        String host = plugin.getConfig().getString("web.public-host", "");
        int port = plugin.getConfig().getInt("web.port", 8080);
        if (host == null || host.isEmpty()) {
            // Fallback на IP сервера (через server.properties: server-ip)
            host = plugin.getServer().getIp();
            if (host == null || host.isEmpty()) host = "localhost";
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            // Хост уже полный URL — добавить порт только если его нет.
            if (!host.contains(":") || host.lastIndexOf(':') < 6) {
                return host + ":" + port + "/";
            }
            return host + "/";
        }
        return "http://" + host + ":" + port + "/";
    }
}
