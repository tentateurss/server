package ru.eclipsia.core.data.storage;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.data.PlayerData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Реализация хранилища через Persistent Data Container (PDC).
 * Используется для быстрого прототипирования на Этапе 0.
 * Данные хранятся в JSON формате для легкой миграции.
 */
public class PDCDataStorage implements IPlayerDataStorage {
    
    private final Plugin plugin;
    private final NamespacedKey dataKey;
    
    public PDCDataStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "player_data");
    }
    
    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ИСПРАВЛЕНО: Используем синхронный метод для получения игрока
                Player player = Bukkit.getPlayer(uuid);
                
                if (player != null) {
                    // Игрок онлайн - читаем из PDC
                    PersistentDataContainer pdc = player.getPersistentDataContainer();
                    String json = pdc.get(dataKey, PersistentDataType.STRING);
                    
                    if (json != null && !json.isEmpty()) {
                        return PlayerData.fromJson(json);
                    }
                }
                
                // ВАЖНО: PDC не поддерживает оффлайн игроков
                // Данных нет или игрок оффлайн - создаем нового игрока
                return PlayerData.createNew(uuid);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки данных игрока " + uuid, e);
                return PlayerData.createNew(uuid);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ИСПРАВЛЕНО: Используем синхронный метод для получения игрока
                Player player = Bukkit.getPlayer(data.getUuid());
                
                if (player != null) {
                    // Обновляем timestamp перед сохранением
                    PlayerData updatedData = data.toBuilder()
                            .lastSave(System.currentTimeMillis())
                            .build();
                    
                    PersistentDataContainer pdc = player.getPersistentDataContainer();
                    pdc.set(dataKey, PersistentDataType.STRING, updatedData.toJson());
                    
                    plugin.getLogger().fine("Данные игрока " + data.getUuid() + " сохранены в PDC");
                } else {
                    // ВАЖНО: PDC не поддерживает оффлайн игроков
                    plugin.getLogger().warning("Попытка сохранить данные оффлайн игрока " + data.getUuid() + " (PDC не поддерживает оффлайн сохранение)");
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения данных игрока " + data.getUuid(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ИСПРАВЛЕНО: Используем синхронный метод для получения игрока
                Player player = Bukkit.getPlayer(uuid);
                
                if (player != null) {
                    PersistentDataContainer pdc = player.getPersistentDataContainer();
                    return pdc.has(dataKey, PersistentDataType.STRING);
                }
                
                // ВАЖНО: PDC не поддерживает оффлайн игроков
                return false;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка проверки данных игрока " + uuid, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> deletePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ИСПРАВЛЕНО: Используем синхронный метод для получения игрока
                Player player = Bukkit.getPlayer(uuid);
                
                if (player != null) {
                    PersistentDataContainer pdc = player.getPersistentDataContainer();
                    pdc.remove(dataKey);
                    plugin.getLogger().info("Данные игрока " + uuid + " удалены из PDC");
                } else {
                    plugin.getLogger().warning("Попытка удалить данные оффлайн игрока " + uuid + " (PDC не поддерживает оффлайн операции)");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка удаления данных игрока " + uuid, e);
            }
        });
    }
    
    @Override
    public void close() {
        // PDC не требует закрытия соединений
    }
    
    @Override
    public String getStorageType() {
        return "PDC";
    }
    
    /**
     * Получение сырых JSON данных из PDC (для миграции)
     */
    public String getRawJson(UUID uuid) {
        // ИСПРАВЛЕНО: Используем синхронный метод для получения игрока
        Player player = Bukkit.getPlayer(uuid);
        
        if (player != null) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            return pdc.get(dataKey, PersistentDataType.STRING);
        }
        
        // ВАЖНО: PDC не поддерживает оффлайн игроков
        return null;
    }
}
