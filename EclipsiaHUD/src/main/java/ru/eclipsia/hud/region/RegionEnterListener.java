package ru.eclipsia.hud.region;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.hud.api.EclipsiaHUDAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Замена сломанного {@code EclipsiaCore/listener/RegionTitleListener}.
 *
 * <p>Старый листенер ссылался на миры {@code world}/{@code beach}, которых
 * больше нет (удалены в PR #51). Этот — генерический: смотрит в
 * {@link RegionRegistry} (источник — config.yml), запоминает последний
 * регион игрока и при переходе зовёт {@link EclipsiaHUDAPI#showRegionEnter}.
 *
 * <p>Проверка проходит только при смене блока — иначе будем 20 раз в секунду
 * перебирать список регионов.
 */
public final class RegionEnterListener implements Listener {

    private final RegionRegistry registry;
    private final Map<UUID, String> lastRegionId = new HashMap<>();

    public RegionEnterListener(RegionRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Дёргаемся только при смене блока — getTo() может быть тот же.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        RegionRegistry.Region region = registry.resolve(event.getTo());

        String previous = lastRegionId.get(player.getUniqueId());
        String current = region == null ? null : region.id();

        if (!java.util.Objects.equals(previous, current)) {
            lastRegionId.put(player.getUniqueId(), current);
            if (region != null) {
                EclipsiaHUDAPI api = EclipsiaHUDAPI.getInstance();
                if (api != null) {
                    api.showRegionEnter(player, region.name());
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastRegionId.remove(event.getPlayer().getUniqueId());
    }
}
