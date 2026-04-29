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
        player.sendMessage("");
        player.sendMessage("§6§l⟡ Дерево перков (web) ⟡");
        player.sendMessage("§7Открой ссылку в браузере и войди по нику + коду:");
        player.sendMessage("§b§n" + url);
        player.sendMessage("§7Ник: §f" + player.getName());
        player.sendMessage("§7Код: §a§l" + PerkAuthCodes.format(code));
        player.sendMessage("§8(Код одноразовый на сессию. /perkscode выдаст новый.)");
        player.sendMessage("");
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
