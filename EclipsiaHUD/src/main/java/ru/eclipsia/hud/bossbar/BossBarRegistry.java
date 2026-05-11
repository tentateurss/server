package ru.eclipsia.hud.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр {@link BossBar}-ов по ключу на игрока.
 *
 * <p>В существующем коде уже есть {@code PlayerHUDManager} (EclipsiaItems),
 * который держит ОДИН зелёный bossbar опыта. Этот реестр НЕ конкурирует с
 * ним — мы регистрируем под уникальными ключами (например {@code "mana"},
 * {@code "cooldown"}, {@code "boss-target"}), а XP-bar остаётся в Items
 * и не трогается.
 *
 * <p>Клиент может показывать одновременно до 7 bossbar-ов комфортно
 * (выше — наплывают друг на друга), поэтому ограничивай число активных
 * ключей разумно.
 *
 * <p>Очистка при выходе игрока сделана через {@link PlayerQuitEvent} —
 * иначе ссылки на BossBar утекали бы до перезагрузки сервера.
 */
public final class BossBarRegistry implements Listener {

    private final Map<UUID, Map<String, BossBar>> bars = new ConcurrentHashMap<>();

    /**
     * Показать (или обновить, если ключ уже существует) bossbar для игрока.
     *
     * <p>Если по ключу уже был другой бар — старый снимается, новый ставится.
     * Если переданный {@code bar} — это уже существующий по этому ключу
     * объект, метод просто гарантирует, что он показан.
     */
    public void show(Player player, String key, BossBar bar) {
        if (player == null || key == null || bar == null) return;

        Map<String, BossBar> playerBars = bars.computeIfAbsent(
                player.getUniqueId(), k -> new HashMap<>());

        BossBar previous = playerBars.put(key, bar);
        if (previous != null && previous != bar) {
            player.hideBossBar(previous);
        }
        player.showBossBar(bar);
    }

    /** Получить bossbar по ключу (или {@code null}). */
    public BossBar get(Player player, String key) {
        if (player == null || key == null) return null;
        Map<String, BossBar> playerBars = bars.get(player.getUniqueId());
        return playerBars == null ? null : playerBars.get(key);
    }

    /** Скрыть bossbar по ключу. */
    public void hide(Player player, String key) {
        if (player == null || key == null) return;
        Map<String, BossBar> playerBars = bars.get(player.getUniqueId());
        if (playerBars == null) return;
        BossBar bar = playerBars.remove(key);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Скрыть все bossbar-ы, зарегистрированные этим реестром. */
    public void hideAll(Player player) {
        if (player == null) return;
        Map<String, BossBar> playerBars = bars.remove(player.getUniqueId());
        if (playerBars == null) return;
        for (BossBar bar : playerBars.values()) {
            player.hideBossBar(bar);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideAll(event.getPlayer());
    }

    /** Снять и очистить вообще всё (вызов из onDisable). */
    public void shutdown() {
        for (Map.Entry<UUID, Map<String, BossBar>> e : bars.entrySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(e.getKey());
            if (p != null) {
                for (BossBar bar : e.getValue().values()) {
                    p.hideBossBar(bar);
                }
            }
        }
        bars.clear();
    }
}
