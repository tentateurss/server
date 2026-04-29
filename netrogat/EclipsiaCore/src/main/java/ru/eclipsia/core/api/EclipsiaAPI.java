package ru.eclipsia.core.api;

import org.bukkit.entity.Player;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.classes.PlayerClass;
import ru.eclipsia.core.permissions.PermissionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Публичный API для взаимодействия других плагинов с EclipsiaCore
 * 
 * Использование в других плагинах:
 * EclipsiaAPI api = EclipsiaAPI.getInstance();
 */
public class EclipsiaAPI {
    
    private static EclipsiaAPI instance;
    
    private EclipsiaAPI() {}
    
    public static EclipsiaAPI getInstance() {
        if (instance == null) {
            instance = new EclipsiaAPI();
        }
        return instance;
    }
    
    // ==================== ДАННЫЕ ИГРОКА ====================
    
    /**
     * Получить данные игрока (синхронно из кэша)
     * @param uuid UUID игрока
     * @return PlayerData или null если не загружено
     */
    public PlayerData getPlayerData(UUID uuid) {
        return DataManager.getInstance().getCachedPlayer(uuid);
    }
    
    /**
     * Получить данные игрока (синхронно из кэша)
     * @param player Игрок
     * @return PlayerData или null если не загружено
     */
    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }
    
    /**
     * Загрузить данные игрока асинхронно
     * @param uuid UUID игрока
     * @return CompletableFuture с данными
     */
    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return DataManager.getInstance().loadPlayer(uuid);
    }
    
    /**
     * Сохранить данные игрока асинхронно
     * @param data Данные для сохранения
     * @return CompletableFuture завершается когда сохранение выполнено
     */
    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return DataManager.getInstance().savePlayer(data);
    }
    
    // ==================== КЛАССЫ ====================
    
    /**
     * Получить класс игрока
     * @param player Игрок
     * @return Название класса или null если не выбран
     */
    public String getPlayerClassName(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getClassName() : null;
    }
    
    /**
     * Получить объект класса игрока
     * @param player Игрок
     * @return PlayerClass или null
     */
    public PlayerClass getPlayerClass(Player player) {
        String className = getPlayerClassName(player);
        return className != null ? ClassManager.getInstance().getClass(className) : null;
    }
    
    /**
     * Проверить выбран ли у игрока класс
     * @param player Игрок
     * @return true если класс выбран
     */
    public boolean hasClass(Player player) {
        return getPlayerClassName(player) != null;
    }
    
    /**
     * Получить класс по ID
     * @param classId ID класса (warrior, archer, mage)
     * @return PlayerClass или null
     */
    public PlayerClass getClassById(String classId) {
        return ClassManager.getInstance().getClass(classId);
    }
    
    // ==================== ХАРАКТЕРИСТИКИ ====================
    
    /**
     * Получить уровень игрока
     * @param player Игрок
     * @return Уровень или 1 если данные не загружены
     */
    public int getPlayerLevel(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getLevel() : 1;
    }
    
    /**
     * Получить значение характеристики игрока
     * @param player Игрок
     * @param statName Название стата (strength, dexterity, intelligence)
     * @return Значение стата или 0
     */
    public int getPlayerStat(Player player, String statName) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getStat(statName) : 0;
    }
    
    /**
     * Получить опыт игрока
     * @param player Игрок
     * @return Количество опыта
     */
    public int getPlayerExperience(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getExperience() : 0;
    }
    
    /**
     * Добавить опыт игроку
     * @param player Игрок
     * @param amount Количество опыта
     */
    public void addExperience(Player player, int amount) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .experience(data.getExperience() + amount)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    /**
     * Установить уровень игроку
     * @param player Игрок
     * @param level Новый уровень
     */
    public void setPlayerLevel(Player player, int level) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .level(level)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    /**
     * Добавить свободные очки характеристик
     * @param player Игрок
     * @param points Количество очков
     */
    public void addStatPoints(Player player, int points) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .freeStatPoints(data.getFreeStatPoints() + points)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    // ==================== ВАЛЮТА (ОРБЫ) ====================
    
    /**
     * Получить количество орбов игрока
     * @param player Игрок
     * @return Количество орбов
     */
    public int getPlayerOrbs(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getOrbs() : 0;
    }
    
    /**
     * Добавить орбы игроку
     * @param player Игрок
     * @param amount Количество орбов
     */
    public void addOrbs(Player player, int amount) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .orbs(data.getOrbs() + amount)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    /**
     * Забрать орбы у игрока
     * @param player Игрок
     * @param amount Количество орбов
     * @return true если хватило орбов
     */
    public boolean removeOrbs(Player player, int amount) {
        PlayerData data = getPlayerData(player);
        if (data == null) return false;
        
        if (data.getOrbs() < amount) {
            return false;
        }
        
        PlayerData updated = data.toBuilder()
                .orbs(data.getOrbs() - amount)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
        return true;
    }
    
    /**
     * Установить количество орбов
     * @param player Игрок
     * @param amount Количество орбов
     */
    public void setOrbs(Player player, int amount) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .orbs(Math.max(0, amount))
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    // ==================== ПРАВА ====================
    
    /**
     * Проверить является ли игрок администратором
     * @param player Игрок
     * @return true если админ
     */
    public boolean isAdmin(Player player) {
        return PermissionManager.getInstance().isAdmin(player);
    }
    
    // ==================== ЦЕНТРАЛИЗОВАННОЕ ХРАНЕНИЕ (НОВОЕ) ====================
    
    /**
     * Получить JSON данные экипировки игрока из централизованного хранилища
     * @param player Игрок
     * @return JSON строка с данными экипировки или null
     */
    public String getEquipmentData(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getEquipmentData() : null;
    }
    
    /**
     * Сохранить JSON данные экипировки игрока в централизованное хранилище
     * @param player Игрок
     * @param equipmentJson JSON строка с данными экипировки
     */
    public void saveEquipmentData(Player player, String equipmentJson) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .equipmentData(equipmentJson)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    /**
     * Получить JSON данные перков игрока из централизованного хранилища
     * @param player Игрок
     * @return JSON строка с данными перков или null
     */
    public String getPerkData(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getPerkData() : null;
    }
    
    /**
     * Сохранить JSON данные перков игрока в централизованное хранилище
     * @param player Игрок
     * @param perkJson JSON строка с данными перков
     */
    public void savePerkData(Player player, String perkJson) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .perkData(perkJson)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
    
    // ==================== СОБЫТИЯ ====================
    
    /**
     * Зарегистрировать слушатель событий Eclipsia
     * Используйте EclipsiaPlayerLevelUpEvent, EclipsiaPlayerClassChangeEvent и т.д.
     */
    // TODO: Добавить кастомные события на Этапе 1
    
    // ==================== СИСТЕМА ПРОФИЛЕЙ ====================
    
    /**
     * Получить активный профиль игрока
     * @param player Игрок
     * @return PlayerProfile или null если не выбран
     */
    public PlayerProfile getActiveProfile(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getActiveProfile() : null;
    }
    
    /**
     * Получить профиль игрока по слоту
     * @param player Игрок
     * @param slot Номер слота (0, 1, 2)
     * @return PlayerProfile или null
     */
    public PlayerProfile getProfile(Player player, int slot) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getProfile(slot) : null;
    }
    
    /**
     * Получить все профили игрока
     * @param player Игрок
     * @return Список профилей (может содержать null)
     */
    public List<PlayerProfile> getProfiles(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null ? data.getProfiles() : new ArrayList<>();
    }
    
    /**
     * Создать новый профиль для игрока
     * @param player Игрок
     * @param className Класс персонажа (warrior, archer, mage)
     * @return true если профиль создан успешно
     */
    public boolean createProfile(Player player, String className) {
        PlayerData data = getPlayerData(player);
        if (data == null) return false;
        
        int freeSlot = data.getFreeSlot();
        if (freeSlot == -1) {
            return false; // Нет свободных слотов
        }
        
        PlayerProfile newProfile = PlayerProfile.createNew(freeSlot, className);
        
        PlayerData updated = data.toBuilder()
                .setProfile(freeSlot, newProfile)
                .activeSlot(freeSlot)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
        return true;
    }
    
    /**
     * Удалить профиль игрока
     * @param player Игрок
     * @param slot Номер слота (0, 1, 2)
     * @return true если профиль удален успешно
     */
    public boolean deleteProfile(Player player, int slot) {
        PlayerData data = getPlayerData(player);
        if (data == null) return false;
        
        if (data.getProfile(slot) == null) {
            return false; // Слот уже пуст
        }
        
        PlayerData.Builder builder = data.toBuilder().setProfile(slot, null);
        
        // Если удаляем активный профиль, сбрасываем activeSlot
        if (data.getActiveSlot() == slot) {
            builder.activeSlot(-1);
        }
        
        DataManager.getInstance().savePlayer(builder.build());
        return true;
    }
    
    /**
     * Переключиться на другой профиль
     * @param player Игрок
     * @param slot Номер слота (0, 1, 2)
     * @return true если переключение успешно
     */
    public boolean switchProfile(Player player, int slot) {
        PlayerData data = getPlayerData(player);
        if (data == null) return false;
        
        if (data.getProfile(slot) == null) {
            return false; // Слот пуст
        }
        
        PlayerData updated = data.toBuilder()
                .activeSlot(slot)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
        return true;
    }
    
    /**
     * Проверить есть ли у игрока свободный слот для профиля
     * @param player Игрок
     * @return true если есть свободный слот
     */
    public boolean hasFreeSlot(Player player) {
        PlayerData data = getPlayerData(player);
        return data != null && data.hasFreeSlot();
    }
    
    /**
     * Обновить активный профиль игрока
     * @param player Игрок
     * @param profile Обновленный профиль
     */
    public void updateProfile(Player player, PlayerProfile profile) {
        PlayerData data = getPlayerData(player);
        if (data == null) return;
        
        PlayerData updated = data.toBuilder()
                .setProfile(profile.getSlot(), profile)
                .build();
        
        DataManager.getInstance().savePlayer(updated);
    }
}
