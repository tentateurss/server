package ru.eclipsia.items.equipment;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.api.EclipsiaAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер экипировки игроков
 * ОБНОВЛЕНО: Использует централизованное хранилище через EclipsiaAPI
 */
public class EquipmentManager {
    
    private final Plugin plugin;
    private final Map<UUID, PlayerEquipment> equipmentCache;
    private final EclipsiaAPI coreAPI;
    
    public EquipmentManager(Plugin plugin) {
        this.plugin = plugin;
        this.equipmentCache = new HashMap<>();
        this.coreAPI = EclipsiaAPI.getInstance();
    }
    
    /**
     * Получить экипировку игрока
     */
    public PlayerEquipment getEquipment(Player player) {
        return getEquipment(player.getUniqueId());
    }
    
    /**
     * Получить экипировку по UUID
     */
    public PlayerEquipment getEquipment(UUID uuid) {
        return equipmentCache.computeIfAbsent(uuid, id -> {
            PlayerEquipment equipment = new PlayerEquipment(id);
            // Загружаем из централизованного хранилища
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player != null) {
                loadEquipmentFromCore(player, equipment);
            }
            return equipment;
        });
    }
    
    /**
     * ОБНОВЛЕНО: Загрузить экипировку из централизованного хранилища
     */
    private void loadEquipmentFromCore(Player player, PlayerEquipment equipment) {
        try {
            String json = coreAPI.getEquipmentData(player);
            
            if (json != null && !json.isEmpty()) {
                equipment.fromJson(json);
                plugin.getLogger().fine("Экипировка игрока " + player.getName() + " загружена из централизованного хранилища");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки экипировки игрока " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Загрузить экипировку игрока
     */
    public void loadEquipment(UUID uuid) {
        if (!equipmentCache.containsKey(uuid)) {
            PlayerEquipment equipment = new PlayerEquipment(uuid);
            
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                loadEquipmentFromCore(player, equipment);
            }
            
            equipmentCache.put(uuid, equipment);
        }
    }
    
    /**
     * ОБНОВЛЕНО: Сохранить экипировку в централизованное хранилище
     */
    public void saveEquipment(UUID uuid) {
        PlayerEquipment equipment = equipmentCache.get(uuid);
        if (equipment != null) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                try {
                    String json = equipment.toJson();
                    coreAPI.saveEquipmentData(player, json);
                    plugin.getLogger().fine("Экипировка игрока " + player.getName() + " сохранена в централизованное хранилище");
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка сохранения экипировки игрока " + player.getName() + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Попытка сохранить экипировку оффлайн игрока " + uuid);
            }
        }
    }
    
    /**
     * Выгрузить экипировку игрока из кэша
     */
    public void unloadEquipment(UUID uuid) {
        saveEquipment(uuid);
        equipmentCache.remove(uuid);
    }

    /**
     * Полная очистка экипировки игрока (баг 2 — /admin resetplayer).
     *
     * <p>Делает четыре вещи в строгом порядке:
     * <ol>
     *   <li>Снимает все наши AttributeModifier'ы (иначе бонусы старой
     *       экипировки прилипнут к новому персонажу);</li>
     *   <li>Опустошает PlayerEquipment в кэше;</li>
     *   <li>Сохраняет ПУСТОЙ equipmentData в централизованное хранилище
     *       (Core), чтобы при следующей загрузке тоже было пусто;</li>
     *   <li>Удаляет UUID из кэша целиком — пусть при следующем доступе
     *       загрузка пройдёт заново и без артефактов.</li>
     * </ol>
     */
    public void clearEquipment(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        // 1) Снять все модификаторы атрибутов от старой экипировки.
        EquipmentBonusApplier.removeAllBonuses(player);

        // 2) Очистить in-memory PlayerEquipment.
        PlayerEquipment cached = equipmentCache.get(uuid);
        if (cached != null) {
            cached.clear();
        }

        // 3) Сохранить пустую экипировку в Core (перезатереть JSON).
        try {
            coreAPI.saveEquipmentData(player, new PlayerEquipment(uuid).toJson());
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Не удалось сохранить пустую экипировку игрока " + player.getName()
                            + ": " + e.getMessage());
        }

        // 4) Снять с кэша — следующий getEquipment(...) загрузит заново
        //    из Core (там уже пусто).
        equipmentCache.remove(uuid);
    }
    
    /**
     * Получить количество загруженных экипировок
     */
    public int getCachedCount() {
        return equipmentCache.size();
    }
}
