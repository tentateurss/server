package ru.eclipsia.mobs.spawn;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import ru.eclipsia.mobs.EclipsiaMobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер зон спавна мобов в структурах
 */
public class StructureSpawnManager {
    
    private static StructureSpawnManager instance;
    private final EclipsiaMobs plugin;
    private final Map<String, SpawnZone> spawnZones;
    
    private StructureSpawnManager(EclipsiaMobs plugin) {
        this.plugin = plugin;
        this.spawnZones = new HashMap<>();
    }
    
    public static void initialize(EclipsiaMobs plugin) {
        if (instance == null) {
            instance = new StructureSpawnManager(plugin);
        }
    }
    
    public static StructureSpawnManager getInstance() {
        return instance;
    }
    
    /**
     * Зарегистрировать зону спавна
     */
    public void registerSpawnZone(String structureId, Location center, int radius, int level, List<EntityType> mobTypes) {
        SpawnZone zone = new SpawnZone(structureId, center, radius, level, mobTypes);
        spawnZones.put(structureId, zone);
        
        plugin.getLogger().info("Зарегистрирована зона спавна: " + structureId + 
                               " (радиус: " + radius + ", уровень: " + level + ")");
    }
    
    /**
     * Получить зону спавна по ID структуры
     */
    public SpawnZone getSpawnZone(String structureId) {
        return spawnZones.get(structureId);
    }
    
    /**
     * Получить все зоны спавна
     */
    public Map<String, SpawnZone> getAllSpawnZones() {
        return new HashMap<>(spawnZones);
    }
    
    /**
     * Класс зоны спавна
     */
    public static class SpawnZone {
        private final String structureId;
        private final Location center;
        private final int radius;
        private final int level;
        private final List<EntityType> mobTypes;
        
        public SpawnZone(String structureId, Location center, int radius, int level, List<EntityType> mobTypes) {
            this.structureId = structureId;
            this.center = center;
            this.radius = radius;
            this.level = level;
            this.mobTypes = mobTypes;
        }
        
        public String getStructureId() {
            return structureId;
        }
        
        public Location getCenter() {
            return center;
        }
        
        public int getRadius() {
            return radius;
        }
        
        public int getLevel() {
            return level;
        }
        
        public List<EntityType> getMobTypes() {
            return mobTypes;
        }
        
        /**
         * Проверить находится ли локация в зоне
         */
        public boolean isInZone(Location loc) {
            if (!loc.getWorld().equals(center.getWorld())) {
                return false;
            }
            return loc.distance(center) <= radius;
        }
    }
}
