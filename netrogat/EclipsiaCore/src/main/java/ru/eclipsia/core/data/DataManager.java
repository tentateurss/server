package ru.eclipsia.core.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.data.storage.IPlayerDataStorage;
import ru.eclipsia.core.data.storage.PDCDataStorage;
import ru.eclipsia.core.data.storage.SQLiteDataStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Центральный менеджер для управления данными игроков.
 * Обеспечивает кэширование в памяти и абстракцию над хранилищем.
 */
public class DataManager {
    
    private static DataManager instance;
    
    private final Plugin plugin;
    private final IPlayerDataStorage storage;
    private final Map<UUID, PlayerData> cache;
    private final boolean autoSaveEnabled;
    private final int autoSaveInterval;
    
    private DataManager(Plugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        
        // Читаем конфигурацию
        String storageType = plugin.getConfig().getString("storage-type", "PDC");
        this.autoSaveEnabled = plugin.getConfig().getBoolean("auto-save.enabled", true);
        this.autoSaveInterval = plugin.getConfig().getInt("auto-save.interval-minutes", 5);
        
        // Выбираем хранилище
        this.storage = createStorage(storageType);
        
        plugin.getLogger().info("DataManager инициализирован с хранилищем: " + storage.getStorageType());
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("DataManager уже инициализирован!");
        }
        instance = new DataManager(plugin);
        instance.onEnable();
    }
    
    public static DataManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DataManager не инициализирован!");
        }
        return instance;
    }
    
    private IPlayerDataStorage createStorage(String type) {
        return switch (type.toUpperCase()) {
            case "SQLITE" -> {
                String dbFile = plugin.getConfig().getString("sqlite.database-file", "eclipsia.db");
                yield new SQLiteDataStorage(plugin, dbFile);
            }
            case "PDC" -> new PDCDataStorage(plugin);
            default -> {
                plugin.getLogger().warning("Неизвестный тип хранилища: " + type + ". Используется PDC.");
                yield new PDCDataStorage(plugin);
            }
        };
    }
    
    private void onEnable() {
        // Инициализируем хранилище если это SQLite
        if (storage instanceof SQLiteDataStorage) {
            try {
                ((SQLiteDataStorage) storage).initialize();
                plugin.getLogger().info("Хранилище " + storage.getStorageType() + " готово к работе");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка инициализации хранилища", e);
            }
        } else {
            plugin.getLogger().info("Хранилище " + storage.getStorageType() + " готово к работе");
        }
        
        // Запускаем автосохранение если включено
        if (autoSaveEnabled) {
            startAutoSave();
        }
    }
    
    public void onDisable() {
        plugin.getLogger().info("Сохранение всех данных игроков...");
        
        // Сохраняем всех игроков из кэша
        CompletableFuture<?>[] futures = cache.values().stream()
                .map(storage::savePlayer)
                .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        // Закрываем хранилище
        storage.close();
        
        cache.clear();
        plugin.getLogger().info("DataManager выключен");
    }
    
    /**
     * Загрузка данных игрока (из кэша или хранилища)
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        // Проверяем кэш
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }
        
        // Загружаем из хранилища
        return storage.loadPlayer(uuid).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }
    
    /**
     * Сохранение данных игрока (в кэш и хранилище)
     */
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        // Обновляем кэш
        cache.put(data.getUuid(), data);
        
        // Сохраняем в хранилище
        return storage.savePlayer(data);
    }
    
    /**
     * Получение данных из кэша (синхронно)
     */
    public PlayerData getCachedPlayer(UUID uuid) {
        return cache.get(uuid);
    }
    
    /**
     * Обновление данных в кэше без сохранения
     */
    public void updateCache(PlayerData data) {
        cache.put(data.getUuid(), data);
    }
    
    /**
     * Выгрузка игрока из кэша с сохранением
     */
    public CompletableFuture<Void> unloadPlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        
        if (data != null) {
            return storage.savePlayer(data);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Миграция данных из PDC в текущее хранилище
     */
    public CompletableFuture<Integer> migrateFromPDC() {
        if (storage instanceof PDCDataStorage) {
            plugin.getLogger().warning("Миграция невозможна: текущее хранилище уже PDC");
            return CompletableFuture.completedFuture(0);
        }
        
        return storage.migrateAllFromPDC().thenApply(count -> {
            plugin.getLogger().info("Миграция завершена: " + count + " игроков");
            
            // Очищаем кэш чтобы перезагрузить данные
            cache.clear();
            
            return count;
        });
    }
    
    /**
     * Автосохранение всех онлайн игроков
     */
    private void startAutoSave() {
        long intervalTicks = autoSaveInterval * 60 * 20L; // минуты в тики
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int savedCount = 0;
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = cache.get(player.getUniqueId());
                
                if (data != null) {
                    storage.savePlayer(data).join();
                    savedCount++;
                }
            }
            
            if (savedCount > 0) {
                plugin.getLogger().info("Автосохранение: сохранено " + savedCount + " игроков");
            }
            
        }, intervalTicks, intervalTicks);
        
        plugin.getLogger().info("Автосохранение запущено (интервал: " + autoSaveInterval + " мин)");
    }
    
    /**
     * Получение статистики кэша
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            cache.size(),
            (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> cache.containsKey(p.getUniqueId()))
                .count()
        );
    }
    
    public IPlayerDataStorage getStorage() {
        return storage;
    }
    
    public record CacheStats(int totalCached, int onlineCached) {}
}
