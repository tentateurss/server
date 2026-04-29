package ru.eclipsia.mobs.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Менеджер кастомных мобов
 */
public class MobManager {
    
    private static MobManager instance;
    
    private final Plugin plugin;
    private final Map<String, CustomMob> mobs;
    
    private MobManager(Plugin plugin) {
        this.plugin = plugin;
        this.mobs = new HashMap<>();
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("MobManager уже инициализирован!");
        }
        instance = new MobManager(plugin);
        instance.loadMobs();
    }
    
    public static MobManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MobManager не инициализирован!");
        }
        return instance;
    }
    
    private void loadMobs() {
        File mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
        
        if (!mobsFile.exists()) {
            plugin.saveResource("mobs.yml", false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(mobsFile);
        
        ConfigurationSection mobsSection = config.getConfigurationSection("mobs");
        if (mobsSection == null) {
            plugin.getLogger().severe("Секция 'mobs' не найдена в mobs.yml!");
            return;
        }
        
        for (String mobId : mobsSection.getKeys(false)) {
            try {
                ConfigurationSection mobConfig = mobsSection.getConfigurationSection(mobId);
                CustomMob mob = new CustomMob(mobId, mobConfig);
                mobs.put(mobId, mob);
                
                plugin.getLogger().info("Загружен моб: " + mob.getDisplayName() + " (Ур. " + mob.getLevel() + ")");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки моба: " + mobId, e);
            }
        }
        
        plugin.getLogger().info("Загружено мобов: " + mobs.size());
    }
    
    /**
     * Получить моба по ID
     */
    public CustomMob getMob(String id) {
        return mobs.get(id);
    }
    
    /**
     * Получить всех мобов
     */
    public Map<String, CustomMob> getAllMobs() {
        return new HashMap<>(mobs);
    }
    
    /**
     * Проверить является ли сущность кастомным мобом
     */
    public boolean isCustomMob(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(plugin, "custom_mob_id"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
    }
    
    /**
     * Получить ID кастомного моба из сущности
     */
    public String getCustomMobId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "custom_mob_id"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
    }
    
    /**
     * Получить кастомного моба из сущности
     */
    public CustomMob getCustomMobFromEntity(LivingEntity entity) {
        String mobId = getCustomMobId(entity);
        return mobId != null ? getMob(mobId) : null;
    }
    
    /**
     * Заспавнить кастомного моба
     */
    public LivingEntity spawnCustomMob(String mobId, org.bukkit.Location location) {
        CustomMob mob = getMob(mobId);
        if (mob == null) {
            plugin.getLogger().warning("Попытка заспавнить несуществующего моба: " + mobId);
            return null;
        }
        
        // Спавним сущность
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, mob.getEntityType());
        
        // Применяем характеристики
        mob.applyToEntity(entity);
        
        // Сохраняем ID моба в PDC
        entity.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "custom_mob_id"),
            org.bukkit.persistence.PersistentDataType.STRING,
            mobId
        );
        
        return entity;
    }
    
    /**
     * Получить количество загруженных мобов
     */
    public int getMobCount() {
        return mobs.size();
    }
    
    /**
     * Перезагрузить конфигурацию мобов
     */
    public void reload() {
        mobs.clear();
        loadMobs();
    }
}
