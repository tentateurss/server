package ru.eclipsia.perks.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Данные перков игрока
 */
public class PlayerPerkData {
    
    private static final Gson GSON = new GsonBuilder().create();
    
    private final UUID playerUUID;
    private int availablePoints;
    private final Set<String> allocatedNodes;
    
    public PlayerPerkData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.availablePoints = 0;
        this.allocatedNodes = new HashSet<>();
    }
    
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    
    public int getAvailablePoints() {
        return availablePoints;
    }
    
    public void setAvailablePoints(int points) {
        this.availablePoints = points;
    }
    
    public void addPoints(int points) {
        this.availablePoints += points;
    }
    
    public void removePoints(int points) {
        this.availablePoints = Math.max(0, this.availablePoints - points);
    }
    
    public Set<String> getAllocatedNodes() {
        return new HashSet<>(allocatedNodes);
    }
    
    /**
     * Взять узел
     */
    public boolean allocateNode(String nodeId, int cost) {
        if (availablePoints < cost) {
            return false;
        }
        
        if (allocatedNodes.contains(nodeId)) {
            return false;
        }
        
        allocatedNodes.add(nodeId);
        availablePoints -= cost;
        return true;
    }
    
    /**
     * Сбросить узел
     */
    public boolean deallocateNode(String nodeId, int cost) {
        if (!allocatedNodes.contains(nodeId)) {
            return false;
        }
        
        allocatedNodes.remove(nodeId);
        availablePoints += cost;
        return true;
    }
    
    /**
     * НОВОЕ: Сбросить узел (без возврата очков - делается вручную)
     */
    public boolean deallocateNode(String nodeId) {
        if (!allocatedNodes.contains(nodeId)) {
            return false;
        }
        
        allocatedNodes.remove(nodeId);
        return true;
    }
    
    /**
     * Проверить взят ли узел
     */
    public boolean hasNode(String nodeId) {
        return allocatedNodes.contains(nodeId);
    }
    
    /**
     * Сбросить все узлы
     * ИСПРАВЛЕНО: Не возвращает очки - это делается вручную в handleReset
     */
    public void resetAll() {
        allocatedNodes.clear();
        // НЕ добавляем очки здесь - они добавляются в PerkTreeGUIListener
    }
    
    /**
     * Получить количество взятых узлов
     */
    public int getAllocatedCount() {
        return allocatedNodes.size();
    }
    
    /**
     * ДОБАВЛЕНО: Сериализация в JSON
     */
    public String toJson() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", playerUUID.toString());
            json.addProperty("availablePoints", availablePoints);
            
            JsonArray nodes = new JsonArray();
            for (String nodeId : allocatedNodes) {
                nodes.add(nodeId);
            }
            json.add("allocatedNodes", nodes);
            
            return GSON.toJson(json);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    /**
     * ДОБАВЛЕНО: Десериализация из JSON
     */
    public void fromJson(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            
            if (obj.has("availablePoints")) {
                this.availablePoints = obj.get("availablePoints").getAsInt();
            }
            
            if (obj.has("allocatedNodes")) {
                allocatedNodes.clear();
                JsonArray nodes = obj.getAsJsonArray("allocatedNodes");
                for (int i = 0; i < nodes.size(); i++) {
                    allocatedNodes.add(nodes.get(i).getAsString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
