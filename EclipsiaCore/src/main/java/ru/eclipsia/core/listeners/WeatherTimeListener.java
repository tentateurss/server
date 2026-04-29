package ru.eclipsia.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

/**
 * Слушатель для отключения дождя и фиксации времени на день
 */
public class WeatherTimeListener implements Listener {
    
    private final Plugin plugin;
    private static final long DAY_TIME = 1000L;       // Утро
    /** Имя dark-fantasy мира «Берег»: вечер + постоянный дождь. */
    private static final String BEACH_WORLD = "beach";
    /** Сумерки/вечер для атмосферы Берега. */
    private static final long BEACH_TIME = 13500L;

    public WeatherTimeListener(Plugin plugin) {
        this.plugin = plugin;

        // Таймер фиксации погоды/времени.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().equals(BEACH_WORLD)) {
                    // Берег: вечер, постоянный дождь без грозы.
                    world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                    world.setTime(BEACH_TIME);
                    if (!world.hasStorm()) {
                        world.setStorm(true);
                    }
                    world.setWeatherDuration(Integer.MAX_VALUE);
                    if (world.isThundering()) {
                        world.setThundering(false);
                    }
                    continue;
                }

                // Остальные миры: всегда день, без дождя.
                world.setTime(DAY_TIME);
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                if (world.hasStorm()) {
                    world.setStorm(false);
                    world.setWeatherDuration(0);
                }
                if (world.isThundering()) {
                    world.setThundering(false);
                    world.setThunderDuration(0);
                }
            }
        }, 0L, 20L);

        plugin.getLogger().info("✓ Время зафиксировано на день, дождь отключен"
                + " (исключение: мир '" + BEACH_WORLD + "' — вечер+дождь)");
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        String worldName = event.getWorld().getName();
        // Берег: разрешаем включение дождя, запрещаем его выключение.
        if (worldName.equals(BEACH_WORLD)) {
            if (!event.toWeatherState()) {
                event.setCancelled(true);
            }
            return;
        }
        // Остальные миры: запрещаем включение дождя.
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }
}
