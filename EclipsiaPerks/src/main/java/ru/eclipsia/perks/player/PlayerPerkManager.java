package ru.eclipsia.perks.player;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.api.EclipsiaAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер данных перков игроков
 * ОБНОВЛЕНО: Использует централизованное хранилище через EclipsiaAPI
 */
public class PlayerPerkManager {
    
    private final Plugin plugin;
    private final Map<UUID, PlayerPerkData> playerData;
    private final EclipsiaAPI coreAPI;
    
    public PlayerPerkManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerData = new HashMap<>();
        this.coreAPI = EclipsiaAPI.getInstance();
    }
    
    /**
     * Получить данные перков игрока
     */
    public PlayerPerkData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }
    
    /**
     * Получить данные по UUID
     */
    public PlayerPerkData getPlayerData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, id -> {
            PlayerPerkData data = new PlayerPerkData(id);
            // Загружаем из централизованного хранилища
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player != null) {
                loadPlayerDataFromCore(player, data);
            }
            return data;
        });
    }
    
    /**
     * ОБНОВЛЕНО: Загрузить данные из централизованного хранилища
     */
    private void loadPlayerDataFromCore(Player player, PlayerPerkData data) {
        try {
            String json = coreAPI.getPerkData(player);
            
            if (json != null && !json.isEmpty()) {
                data.fromJson(json);
                plugin.getLogger().fine("Данные перков игрока " + player.getName() + " загружены из централизованного хранилища");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки данных перков игрока " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Загрузить данные игрока
     */
    public void loadPlayerData(UUID uuid) {
        if (!playerData.containsKey(uuid)) {
            PlayerPerkData data = new PlayerPerkData(uuid);
            
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                loadPlayerDataFromCore(player, data);
            }
            
            playerData.put(uuid, data);
        }
    }
    
    /**
     * ОБНОВЛЕНО: Сохранить данные в централизованное хранилище
     */
    public void savePlayerData(UUID uuid) {
        PlayerPerkData data = playerData.get(uuid);
        if (data != null) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                try {
                    String json = data.toJson();
                    coreAPI.savePerkData(player, json);
                    plugin.getLogger().fine("Данные перков игрока " + player.getName() + " сохранены в централизованное хранилище");
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка сохранения данных перков игрока " + player.getName() + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Попытка сохранить данные перков оффлайн игрока " + uuid);
            }
        }
    }
    
    /**
     * Выгрузить данные игрока
     */
    public void unloadPlayerData(UUID uuid) {
        savePlayerData(uuid);
        playerData.remove(uuid);
    }
    
    /**
     * Рассчитать доступные очки перков для уровня
     */
    public int calculateAvailablePoints(int level) {
        if (level < 2) {
            return 0;
        }
        // 1 очко за уровень начиная со 2-го
        return level - 1;
    }
    
    /**
     * Обновить очки перков при повышении уровня
     */
    public void updatePointsForLevel(UUID uuid, int level) {
        PlayerPerkData data = getPlayerData(uuid);
        int totalPoints = calculateAvailablePoints(level);
        int usedPoints = data.getAllocatedCount();
        data.setAvailablePoints(totalPoints - usedPoints);
    }
}
