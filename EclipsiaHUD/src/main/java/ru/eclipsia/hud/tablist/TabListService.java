package ru.eclipsia.hud.tablist;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.hud.theme.Theme;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player TAB header/footer. Шаблоны берутся из {@code tablist.header} и
 * {@code tablist.footer} (MiniMessage). Поддерживаемые плейсхолдеры:
 *
 * <ul>
 *   <li>{@code <player>}     — имя игрока;</li>
 *   <li>{@code <player_count>} — текущее число онлайн;</li>
 *   <li>{@code <level>}      — уровень активного профиля;</li>
 *   <li>{@code <class>}      — отображаемое имя класса;</li>
 *   <li>{@code <world>}      — имя мира.</li>
 * </ul>
 *
 * <p>Header/footer обновляются раз в {@code period-ticks}; этого достаточно
 * для отображения количества онлайн и времени. Жёсткий per-tick апдейт
 * здесь не нужен — Минекрафт-клиент перерисовывает TAB только при изменении.
 */
public final class TabListService implements Listener {

    private final Plugin plugin;
    private final ConfigurationSection cfg;
    private BukkitTask task;

    private final Set<UUID> disabled = new HashSet<>();

    public TabListService(Plugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void start() {
        if (task != null || !cfg.getBoolean("enabled", true)) return;
        long period = Math.max(20L, cfg.getLong("period-ticks", 40L));

        task = new BukkitRunnable() {
            @Override
            public void run() {
                int online = Bukkit.getOnlinePlayers().size();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (disabled.contains(p.getUniqueId())) continue;
                    try {
                        render(p, online);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("TabListService render error for "
                                + p.getName() + ": " + t.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    public void setVisible(Player player, boolean visible) {
        if (visible) {
            disabled.remove(player.getUniqueId());
            render(player, Bukkit.getOnlinePlayers().size());
        } else {
            disabled.add(player.getUniqueId());
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg.getBoolean("enabled", true)) return;
        render(event.getPlayer(), Bukkit.getOnlinePlayers().size());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disabled.remove(event.getPlayer().getUniqueId());
    }

    private void render(Player player, int online) {
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile profile = api == null ? null : api.getActiveProfile(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("player_count", String.valueOf(online));
        ph.put("world", player.getWorld().getName());
        ph.put("level", profile == null ? "—" : String.valueOf(profile.getLevel()));
        ph.put("class", profile == null ? "—" : Theme.classDisplayName(profile.getClassName()));

        Component header = Theme.mm(cfg.getString("header", ""), ph);
        Component footer = Theme.mm(cfg.getString("footer", ""), ph);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
