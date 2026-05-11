package ru.eclipsia.hud.title;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.eclipsia.hud.theme.Theme;

import java.time.Duration;
import java.util.Map;

/**
 * Унифицированные «кинематик»-титры: welcome, region-enter, level-up, boss-spawn.
 *
 * <p>Раньше эти эффекты были рассыпаны:
 * <ul>
 *   <li>welcome — не было вообще;</li>
 *   <li>region — {@code EclipsiaCore/listener/RegionTitleListener} с
 *       deprecated {@code player.sendTitle(...)} и хардкодом миров
 *       {@code beach}/{@code world}, которых уже нет;</li>
 *   <li>level-up — {@code EclipsiaMobs/ExperienceManager#levelUp} с тем же
 *       deprecated API;</li>
 *   <li>boss-spawn — не было.</li>
 * </ul>
 *
 * <p>Параметры берутся из {@code titles.*} в config.yml — фразы/тайминги
 * можно править без перекомпиляции.
 */
public final class TitleCinematicService {

    private final ConfigurationSection cfg;

    public TitleCinematicService(ConfigurationSection cfg) {
        this.cfg = cfg;
    }

    /** Title при подключении. */
    public void showWelcome(Player player) {
        ConfigurationSection s = cfg.getConfigurationSection("welcome");
        if (s == null || !s.getBoolean("enabled", true)) return;

        Title title = build(
                Theme.mm(s.getString("title", ""), Map.of("player", player.getName())),
                Theme.mm(s.getString("subtitle", ""), Map.of("player", player.getName())),
                s.getInt("fade-in-ticks", 20),
                s.getInt("stay-ticks", 60),
                s.getInt("fade-out-ticks", 20)
        );
        player.showTitle(title);
    }

    /** Title при повышении уровня. */
    public void showLevelUp(Player player, int newLevel) {
        ConfigurationSection s = cfg.getConfigurationSection("level-up");
        if (s == null || !s.getBoolean("enabled", true)) return;

        Title title = build(
                Theme.mm(s.getString("title", ""), Map.of("level", String.valueOf(newLevel))),
                Theme.mm(s.getString("subtitle", ""), Map.of("level", String.valueOf(newLevel))),
                s.getInt("fade-in-ticks", 10),
                s.getInt("stay-ticks", 40),
                s.getInt("fade-out-ticks", 10)
        );
        player.showTitle(title);
        // звук уровня — оставляем как был в ExperienceManager
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /** Title при появлении босса. */
    public void showBossSpawn(Player player, String bossName) {
        ConfigurationSection s = cfg.getConfigurationSection("boss-spawn");
        if (s == null || !s.getBoolean("enabled", true)) return;

        String safe = bossName == null ? "???" : bossName;
        Title title = build(
                Theme.mm(s.getString("title", ""), Map.of("boss", safe)),
                Theme.mm(s.getString("subtitle", ""), Map.of("boss", safe)),
                s.getInt("fade-in-ticks", 5),
                s.getInt("stay-ticks", 50),
                s.getInt("fade-out-ticks", 10)
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.0f);
    }

    /** Title при входе в регион — название передаётся уже готовым Component'ом. */
    public void showRegionEnter(Player player, Component regionName) {
        ConfigurationSection s = cfg.getConfigurationSection("region-enter");
        if (s == null || !s.getBoolean("enabled", true)) return;

        Title title = build(
                regionName == null ? Component.empty() : regionName,
                Component.empty(),
                s.getInt("fade-in-ticks", 10),
                s.getInt("stay-ticks", 40),
                s.getInt("fade-out-ticks", 10)
        );
        player.showTitle(title);
    }

    private Title build(Component title, Component sub, int fadeIn, int stay, int fadeOut) {
        // Title.Times принимает Duration; tick = 50ms.
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        return Title.title(title, sub, times);
    }
}
