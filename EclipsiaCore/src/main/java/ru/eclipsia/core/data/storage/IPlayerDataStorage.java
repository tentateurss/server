package ru.eclipsia.core.data.storage;

import ru.eclipsia.core.data.PlayerData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Интерфейс для хранения данных игроков.
 * Позволяет легко переключаться между PDC, SQLite и MySQL.
 */
public interface IPlayerDataStorage {
    
    /**
     * Загрузить данные игрока асинхронно
     * @param uuid UUID игрока
     * @return CompletableFuture с данными игрока или null если не найдено
     */
    CompletableFuture<PlayerData> loadPlayer(UUID uuid);
    
    /**
     * Сохранить данные игрока асинхронно
     * @param data Данные игрока
     * @return CompletableFuture<Void>
     */
    CompletableFuture<Void> savePlayer(PlayerData data);
    
    /**
     * Проверить существование данных игрока
     * @param uuid UUID игрока
     * @return true если данные существуют
     */
    CompletableFuture<Boolean> hasPlayer(UUID uuid);
    
    /**
     * Удалить данные игрока
     * @param uuid UUID игрока
     * @return CompletableFuture<Void>
     */
    CompletableFuture<Void> deletePlayer(UUID uuid);
    
    /**
     * Мигрировать все данные из PDC в текущее хранилище
     * @return Количество мигрированных игроков
     */
    default CompletableFuture<Integer> migrateAllFromPDC() {
        return CompletableFuture.completedFuture(0);
    }
    
    /**
     * Закрыть соединение с хранилищем
     */
    void close();
    
    /**
     * Получить тип хранилища
     */
    String getStorageType();
}
