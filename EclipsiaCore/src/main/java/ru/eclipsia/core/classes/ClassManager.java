package ru.eclipsia.core.classes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Менеджер классов персонажей
 */
public class ClassManager {
    
    private static ClassManager instance;
    
    private final Plugin plugin;
    private final Map<String, PlayerClass> classes;
    private int respecCost;
    
    private ClassManager(Plugin plugin) {
        this.plugin = plugin;
        this.classes = new HashMap<>();
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("ClassManager уже инициализирован!");
        }
        instance = new ClassManager(plugin);
        instance.loadClasses();
    }
    
    public static ClassManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ClassManager не инициализирован!");
        }
        return instance;
    }
    
    private void loadClasses() {
        File classesFile = new File(plugin.getDataFolder(), "classes.yml");
        
        if (!classesFile.exists()) {
            plugin.saveResource("classes.yml", false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(classesFile);
        
        ConfigurationSection classesSection = config.getConfigurationSection("classes");
        if (classesSection == null) {
            plugin.getLogger().severe("Секция 'classes' не найдена в classes.yml!");
            return;
        }
        
        for (String classId : classesSection.getKeys(false)) {
            try {
                ConfigurationSection classConfig = classesSection.getConfigurationSection(classId);
                PlayerClass playerClass = new PlayerClass(classId, classConfig);
                classes.put(classId, playerClass);
                
                plugin.getLogger().info("Загружен класс: " + playerClass.getDisplayName());
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки класса: " + classId, e);
            }
        }
        
        respecCost = config.getInt("respec-cost", 1000);
        
        plugin.getLogger().info("Загружено классов: " + classes.size());
    }
    
    public PlayerClass getClass(String id) {
        return classes.get(id);
    }
    
    public Set<String> getClassIds() {
        return classes.keySet();
    }
    
    public Map<String, PlayerClass> getAllClasses() {
        return new HashMap<>(classes);
    }
    
    public boolean classExists(String id) {
        return classes.containsKey(id);
    }
    
    public int getRespecCost() {
        return respecCost;
    }
    
    public void reload() {
        classes.clear();
        loadClasses();
    }
}
