package ru.eclipsia.core.data.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.data.PlayerData;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Реализация хранилища через SQLite.
 * Используется начиная с Этапа 2 для масштабирования.
 * Данные хранятся в JSON формате для гибкости.
 */
public class SQLiteDataStorage implements IPlayerDataStorage {
    
    private final Plugin plugin;
    private final String databasePath;
    private Connection connection;
    
    public SQLiteDataStorage(Plugin plugin, String databaseFile) {
        this.plugin = plugin;
        this.databasePath = new File(plugin.getDataFolder(), databaseFile).getAbsolutePath();
    }
    
    /**
     * Инициализация базы данных
     */
    public void initialize() throws SQLException {
        // Создаем директорию если не существует
        File dbFile = new File(databasePath);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        
        // Подключаемся к базе данных
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        
        // Создаем таблицу если не существует
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT PRIMARY KEY, " +
                "data TEXT NOT NULL, " +
                "last_save INTEGER NOT NULL" +
                ")"
            );
            
            // Создаем индекс для быстрого поиска
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_last_save ON player_data(last_save)");
        }
        
        plugin.getLogger().info("SQLite база данных инициализирована: " + databasePath);
    }
    
    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT data FROM player_data WHERE uuid = ?";
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String json = rs.getString("data");
                            return PlayerData.fromJson(json);
                        }
                    }
                }
                
                // Данных нет - создаем нового игрока
                return PlayerData.createNew(uuid);
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки данных игрока " + uuid, e);
                return PlayerData.createNew(uuid);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Обновляем timestamp перед сохранением
                PlayerData updatedData = data.toBuilder()
                        .lastSave(System.currentTimeMillis())
                        .build();
                
                String sql = "INSERT OR REPLACE INTO player_data (uuid, data, last_save) VALUES (?, ?, ?)";
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, updatedData.getUuid().toString());
                    stmt.setString(2, updatedData.toJson());
                    stmt.setLong(3, updatedData.getLastSave());
                    stmt.executeUpdate();
                }
                
                plugin.getLogger().fine("Данные игрока " + data.getUuid() + " сохранены в SQLite");
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения данных игрока " + data.getUuid(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT 1 FROM player_data WHERE uuid = ? LIMIT 1";
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка проверки данных игрока " + uuid, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> deletePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "DELETE FROM player_data WHERE uuid = ?";
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    int deleted = stmt.executeUpdate();
                    
                    if (deleted > 0) {
                        plugin.getLogger().info("Данные игрока " + uuid + " удалены из SQLite");
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка удаления данных игрока " + uuid, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> migrateAllFromPDC() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PDCDataStorage pdcStorage = new PDCDataStorage(plugin);
                int migratedCount = 0;
                
                plugin.getLogger().info("═══════════════════════════════════════════════════");
                plugin.getLogger().info("НАЧАЛО МИГРАЦИИ: PDC → SQLite");
                plugin.getLogger().info("═══════════════════════════════════════════════════");
                
                // Получаем всех онлайн игроков
                List<UUID> onlinePlayers = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> onlinePlayers.add(p.getUniqueId()));
                
                plugin.getLogger().info("Найдено онлайн игроков: " + onlinePlayers.size());
                
                for (UUID uuid : onlinePlayers) {
                    try {
                        // Проверяем есть ли данные в PDC
                        if (pdcStorage.hasPlayer(uuid).join()) {
                            // Загружаем из PDC (включая equipmentData и perkData)
                            PlayerData data = pdcStorage.loadPlayer(uuid).join();
                            
                            plugin.getLogger().info("  → Миграция игрока: " + uuid);
                            plugin.getLogger().info("    - Класс: " + data.getClassName());
                            plugin.getLogger().info("    - Уровень: " + data.getLevel());
                            plugin.getLogger().info("    - Экипировка: " + (data.getEquipmentData() != null ? "ДА" : "НЕТ"));
                            plugin.getLogger().info("    - Перки: " + (data.getPerkData() != null ? "ДА" : "НЕТ"));
                            
                            // Сохраняем в SQLite (все данные включая equipment и perks)
                            savePlayer(data).join();
                            
                            migratedCount++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Ошибка миграции игрока " + uuid, e);
                    }
                }
                
                plugin.getLogger().info("═══════════════════════════════════════════════════");
                plugin.getLogger().info("МИГРАЦИЯ ЗАВЕРШЕНА");
                plugin.getLogger().info("Успешно мигрировано: " + migratedCount + " игроков");
                plugin.getLogger().info("═══════════════════════════════════════════════════");
                
                return migratedCount;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Критическая ошибка миграции", e);
                return 0;
            }
        });
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite соединение закрыто");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка закрытия SQLite соединения", e);
        }
    }
    
    @Override
    public String getStorageType() {
        return "SQLite";
    }
    
    /**
     * Получить количество записей в базе
     */
    public int getPlayerCount() {
        try {
            String sql = "SELECT COUNT(*) FROM player_data";
            
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка подсчета игроков", e);
        }
        
        return 0;
    }
    
    /**
     * Проверить работоспособность соединения
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
