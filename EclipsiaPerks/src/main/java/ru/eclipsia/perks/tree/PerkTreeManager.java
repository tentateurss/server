package ru.eclipsia.perks.tree;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.perks.node.NodeType;
import ru.eclipsia.perks.node.PerkNode;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Менеджер дерева перков
 */
public class PerkTreeManager {
    
    private final Plugin plugin;
    private final Map<String, PerkNode> nodes;
    private final Map<String, String> classStartNodes;

    // Глобальные настройки дерева из секции settings: в perks.yml.
    // Дефолты выбраны так, чтобы игрок 1 уровня сразу мог взять одинижный
    // соседний узел после автовыдачи стартового (иначе дерево выглядит
    // сломанным — старт светится, но «следующие узлы не загружаются»).
    private int startLevel = 1;
    private int pointsPerLevel = 1;
    private int maxPoints = Integer.MAX_VALUE;

    public PerkTreeManager(Plugin plugin) {
        this.plugin = plugin;
        this.nodes = new HashMap<>();
        this.classStartNodes = new HashMap<>();
    }

    public int getStartLevel() { return startLevel; }
    public int getPointsPerLevel() { return pointsPerLevel; }
    public int getMaxPoints() { return maxPoints; }
    
    /**
     * Загрузить дерево перков из конфига
     */
    public void loadPerkTree() {
        nodes.clear();
        classStartNodes.clear();
        
        File perksFile = new File(plugin.getDataFolder(), "perks.yml");
        if (!perksFile.exists()) {
            plugin.getLogger().severe("Файл perks.yml не найден!");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(perksFile);

        // settings: (points-per-level / start-level / max-points)
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            this.pointsPerLevel = settings.getInt("points-per-level", 1);
            this.startLevel     = settings.getInt("start-level", 1);
            this.maxPoints      = settings.getInt("max-points", Integer.MAX_VALUE);
            if (this.startLevel < 1) this.startLevel = 1;
            if (this.pointsPerLevel < 0) this.pointsPerLevel = 0;
            if (this.maxPoints <= 0) this.maxPoints = Integer.MAX_VALUE;
        }

        // Загружаем стартовые позиции классов
        ConfigurationSection startingPositions = config.getConfigurationSection("starting-positions");
        if (startingPositions != null) {
            for (String className : startingPositions.getKeys(false)) {
                String nodeId = startingPositions.getString(className + ".node");
                classStartNodes.put(className, nodeId);
            }
        }
        
        // Загружаем узлы
        ConfigurationSection nodesSection = config.getConfigurationSection("nodes");
        if (nodesSection == null) {
            plugin.getLogger().severe("Секция 'nodes' не найдена в perks.yml!");
            return;
        }
        
        for (String nodeId : nodesSection.getKeys(false)) {
            try {
                ConfigurationSection nodeSection = nodesSection.getConfigurationSection(nodeId);
                PerkNode node = loadNode(nodeId, nodeSection);
                nodes.put(nodeId, node);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки узла: " + nodeId, e);
            }
        }
        
        plugin.getLogger().info("Загружено узлов перков: " + nodes.size());
    }
    
    /**
     * Загрузить узел из конфига
     */
    private PerkNode loadNode(String id, ConfigurationSection section) {
        NodeType type = NodeType.valueOf(section.getString("type", "SMALL"));
        String name = section.getString("name", id);
        int x = section.getInt("x", 0);
        int y = section.getInt("y", 0);
        List<String> connections = section.getStringList("connections");
        
        // Загружаем статы
        Map<String, Integer> stats = new HashMap<>();
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String stat : statsSection.getKeys(false)) {
                stats.put(stat, statsSection.getInt(stat));
            }
        }
        
        return new PerkNode(id, type, name, x, y, connections, stats);
    }
    
    /**
     * Получить узел по ID
     */
    public PerkNode getNode(String id) {
        return nodes.get(id);
    }
    
    /**
     * Получить все узлы
     */
    public Collection<PerkNode> getAllNodes() {
        return nodes.values();
    }
    
    /**
     * Получить стартовый узел для класса
     */
    public String getStartNodeForClass(String className) {
        return classStartNodes.get(className.toLowerCase());
    }
    
    /**
     * Проверить можно ли взять узел
     */
    public boolean canAllocateNode(String nodeId, Set<String> allocatedNodes) {
        PerkNode node = getNode(nodeId);
        if (node == null) {
            return false;
        }
        
        // Если узел уже взят
        if (allocatedNodes.contains(nodeId)) {
            return false;
        }

        // Проверяем связь в ПРЯМОМ направлении: этот узел ссылается на
        // уже взятый узел. Этого хватает, если perks.yml держит связи
        // двунаправленными.
        for (String connection : node.getConnections()) {
            if (allocatedNodes.contains(connection)) {
                return true;
            }
        }

        // Связь в ОБРАТНОМ направлении: какой-то из взятых узлов ссылается
        // на этот узел. В perks.yml встречаются однонаправленные связи
        // (например archer_med_0 указывает на archer_in_*, но archer_in_*
        // на archer_med_0 не указывает). Без этого фоллбэка пользователь
        // получал «Узел не примыкает к изученным» при попытке прокачать
        // соседний с START узел, хотя визуально ребро есть.
        for (String allocId : allocatedNodes) {
            PerkNode alloc = getNode(allocId);
            if (alloc == null) continue;
            if (alloc.getConnections().contains(nodeId)) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Получить количество узлов
     */
    public int getNodeCount() {
        return nodes.size();
    }
}
