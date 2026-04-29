package ru.eclipsia.perks.node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Узел в дереве перков
 */
public class PerkNode {
    
    private final String id;
    private final NodeType type;
    private final String name;
    private final int x;
    private final int y;
    private final List<String> connections;
    private final Map<String, Integer> stats;
    
    public PerkNode(String id, NodeType type, String name, int x, int y, 
                    List<String> connections, Map<String, Integer> stats) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
        this.connections = connections;
        this.stats = stats != null ? stats : new HashMap<>();
    }
    
    public String getId() {
        return id;
    }
    
    public NodeType getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public List<String> getConnections() {
        return connections;
    }
    
    public Map<String, Integer> getStats() {
        return new HashMap<>(stats);
    }
    
    /**
     * Проверить связан ли узел с другим узлом
     */
    public boolean isConnectedTo(String nodeId) {
        return connections.contains(nodeId);
    }
    
    /**
     * Получить стоимость узла в очках
     */
    public int getCost() {
        return type.getCost();
    }
    
    /**
     * Получить описание бонусов узла
     */
    public String getStatsDescription() {
        if (stats.isEmpty()) {
            return "§7Нет бонусов";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("§7").append(getStatName(entry.getKey())).append(": §f+").append(entry.getValue());
        }
        return sb.toString();
    }
    
    /**
     * Получить название характеристики
     */
    private String getStatName(String stat) {
        switch (stat.toLowerCase()) {
            case "strength": return "Сила";
            case "dexterity": return "Ловкость";
            case "intelligence": return "Интеллект";
            case "health": return "Здоровье";
            case "damage": return "Урон";
            case "armor": return "Броня";
            case "crit_chance": return "Шанс крита";
            case "crit_damage": return "Крит. урон";
            case "dodge": return "Уклонение";
            case "spell_damage": return "Магический урон";
            case "mana": return "Мана";
            default: return stat;
        }
    }
}
