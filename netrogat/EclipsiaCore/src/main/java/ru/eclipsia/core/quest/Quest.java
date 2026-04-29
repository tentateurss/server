package ru.eclipsia.core.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Базовый класс квеста
 */
public class Quest {
    
    private final String id;
    private final String name;
    private final String description;
    private final QuestType type;
    private final int requiredLevel;
    private final Map<String, Object> objectives;
    private final Map<String, Object> rewards;
    
    public Quest(String id, String name, String description, QuestType type, int requiredLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.requiredLevel = requiredLevel;
        this.objectives = new HashMap<>();
        this.rewards = new HashMap<>();
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public QuestType getType() { return type; }
    public int getRequiredLevel() { return requiredLevel; }
    public Map<String, Object> getObjectives() { return objectives; }
    public Map<String, Object> getRewards() { return rewards; }
    
    /**
     * Типы квестов
     */
    public enum QuestType {
        KILL_MOBS,      // Убить X мобов
        COLLECT_ITEMS,  // Собрать X предметов
        TALK_TO_NPC,    // Поговорить с NPC
        REACH_LOCATION, // Достичь локации
        KILL_BOSS       // Убить босса
    }
    
    /**
     * Builder для создания квестов
     */
    public static class Builder {
        private final Quest quest;
        
        public Builder(String id, String name, String description, QuestType type, int requiredLevel) {
            this.quest = new Quest(id, name, description, type, requiredLevel);
        }
        
        public Builder objective(String key, Object value) {
            quest.objectives.put(key, value);
            return this;
        }
        
        public Builder reward(String key, Object value) {
            quest.rewards.put(key, value);
            return this;
        }
        
        public Quest build() {
            return quest;
        }
    }
}
