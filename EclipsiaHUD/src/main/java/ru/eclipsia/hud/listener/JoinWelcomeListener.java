package ru.eclipsia.hud.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.hud.title.TitleCinematicService;

/**
 * При логине показываем welcome-title — задержка 1 секунду, чтобы клиент
 * успел получить ресурс-пак / открыть мир (Title во время белого экрана
 * не виден).
 */
public final class JoinWelcomeListener implements Listener {

    private final Plugin plugin;
    private final TitleCinematicService titles;

    public JoinWelcomeListener(Plugin plugin, TitleCinematicService titles) {
        this.plugin = plugin;
        this.titles = titles;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> titles.showWelcome(event.getPlayer()),
                20L);
    }
}
