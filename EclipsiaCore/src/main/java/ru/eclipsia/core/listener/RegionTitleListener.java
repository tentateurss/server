package ru.eclipsia.core.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.eclipsia.core.EclipsiaCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель для отображения названий регионов при входе
 */
public class RegionTitleListener implements Listener {
    
    private final EclipsiaCore plugin;
    private final Map<UUID, String> lastRegion = new HashMap<>();
    
    public RegionTitleListener(EclipsiaCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем только если игрок пересек границу блока
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        String currentRegion = getRegionName(player);
        String previousRegion = lastRegion.get(player.getUniqueId());
        
        // Если регион изменился - показываем title
        if (currentRegion != null && !currentRegion.equals(previousRegion)) {
            showRegionTitle(player, currentRegion);
            lastRegion.put(player.getUniqueId(), currentRegion);
        }
    }
    
    /**
     * Получить название региона по координатам игрока
     */
    private String getRegionName(Player player) {
        String worldName = player.getWorld().getName();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        
        // Берег
        if (worldName.equals("beach")) {
            if (x >= -50 && x <= 100 && z >= -50 && z <= 100) {
                return "§6Берег";
            }
        }
        
        // Основной мир
        if (worldName.equals("world")) {
            // Город Эликиум
            if (x >= -100 && x <= 100 && z >= -100 && z <= 100) {
                return "§bГород Эликиум";
            }
            
            // Лагеря (примерные координаты)
            if (x >= 200 && x <= 250 && z >= 200 && z <= 250) {
                return "§aЛагерь Северный";
            }
            if (x >= -250 && x <= -200 && z >= 200 && z <= 250) {
                return "§aЛагерь Западный";
            }
            if (x >= 200 && x <= 250 && z >= -250 && z <= -200) {
                return "§aЛагерь Восточный";
            }
            if (x >= -250 && x <= -200 && z >= -250 && z <= -200) {
                return "§aЛагерь Южный";
            }
            
            // Арена Хранителя Врат
            if (x >= 40 && x <= 70 && z >= 40 && z <= 70) {
                return "§cАрена Хранителя Врат";
            }
            
            // Дикие земли (по умолчанию)
            return "§7Дикие земли";
        }
        
        return null;
    }
    
    /**
     * Показать title с названием региона
     */
    private void showRegionTitle(Player player, String regionName) {
        player.sendTitle(
            regionName,
            "",
            10, // fadeIn
            40, // stay
            10  // fadeOut
        );
    }
}
