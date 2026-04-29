package ru.eclipsia.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Иммутабельный класс для хранения данных одного профиля персонажа.
 * Игрок может иметь до 3 профилей (слоты 0, 1, 2).
 */
public class PlayerProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final int slot;
    private final String className;
    private final String displayName;
    private final int level;
    private final int experience;
    private final int freeStatPoints;
    private final int skillPoints;
    private final int orbs;
    private final int currentMana;
    private final int maxMana;
    private final Map<String, Integer> stats;
    private final String lastLocation; // формат: "world:x:y:z:pitch:yaw"
    private final String equipmentData; // JSON экипировки
    private final String perkData; // JSON перков
    private final long createdAt;
    private final long lastPlayed;
    
    private PlayerProfile(Builder builder) {
        this.slot = builder.slot;
        this.className = builder.className;
        this.displayName = builder.displayName;
        this.level = builder.level;
        this.experience = builder.experience;
        this.freeStatPoints = builder.freeStatPoints;
        this.skillPoints = builder.skillPoints;
        this.orbs = builder.orbs;
        this.currentMana = builder.currentMana;
        this.maxMana = builder.maxMana;
        this.stats = new HashMap<>(builder.stats);
        this.lastLocation = builder.lastLocation;
        this.equipmentData = builder.equipmentData;
        this.perkData = builder.perkData;
        this.createdAt = builder.createdAt;
        this.lastPlayed = builder.lastPlayed;
    }
    
    // Getters
    public int getSlot() { return slot; }
    public String getClassName() { return className; }
    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public int getExperience() { return experience; }
    public int getFreeStatPoints() { return freeStatPoints; }
    public int getSkillPoints() { return skillPoints; }
    public int getOrbs() { return orbs; }
    public int getCurrentMana() { return currentMana; }
    public int getMaxMana() { return maxMana; }
    public Map<String, Integer> getStats() { return new HashMap<>(stats); }
    public int getStat(String statName) { return stats.getOrDefault(statName, 0); }
    public String getLastLocation() { return lastLocation; }
    public String getEquipmentData() { return equipmentData; }
    public String getPerkData() { return perkData; }
    public long getCreatedAt() { return createdAt; }
    public long getLastPlayed() { return lastPlayed; }
    
    /**
     * Сериализация в JSON строку
     */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("slot", slot);
        json.addProperty("className", className);
        json.addProperty("displayName", displayName);
        json.addProperty("level", level);
        json.addProperty("experience", experience);
        json.addProperty("freeStatPoints", freeStatPoints);
        json.addProperty("skillPoints", skillPoints);
        json.addProperty("orbs", orbs);
        json.addProperty("currentMana", currentMana);
        json.addProperty("maxMana", maxMana);
        json.add("stats", GSON.toJsonTree(stats));
        
        if (lastLocation != null && !lastLocation.isEmpty()) {
            json.addProperty("lastLocation", lastLocation);
        }
        if (equipmentData != null && !equipmentData.isEmpty()) {
            json.addProperty("equipmentData", equipmentData);
        }
        if (perkData != null && !perkData.isEmpty()) {
            json.addProperty("perkData", perkData);
        }
        
        json.addProperty("createdAt", createdAt);
        json.addProperty("lastPlayed", lastPlayed);
        
        return GSON.toJson(json);
    }
    
    /**
     * Десериализация из JSON строки
     */
    public static PlayerProfile fromJson(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        
        Builder builder = new Builder(obj.get("slot").getAsInt());
        
        builder.className(obj.get("className").getAsString())
               .displayName(obj.has("displayName") ? obj.get("displayName").getAsString() : obj.get("className").getAsString())
               .level(obj.get("level").getAsInt())
               .experience(obj.get("experience").getAsInt())
               .freeStatPoints(obj.get("freeStatPoints").getAsInt())
               .skillPoints(obj.has("skillPoints") ? obj.get("skillPoints").getAsInt() : 0)
               .orbs(obj.has("orbs") ? obj.get("orbs").getAsInt() : 0)
               .currentMana(obj.has("currentMana") ? obj.get("currentMana").getAsInt() : 100)
               .maxMana(obj.has("maxMana") ? obj.get("maxMana").getAsInt() : 100)
               .createdAt(obj.get("createdAt").getAsLong())
               .lastPlayed(obj.get("lastPlayed").getAsLong());
        
        if (obj.has("stats")) {
            JsonObject statsObj = obj.getAsJsonObject("stats");
            for (String key : statsObj.keySet()) {
                builder.stat(key, statsObj.get(key).getAsInt());
            }
        }
        
        if (obj.has("lastLocation") && !obj.get("lastLocation").isJsonNull()) {
            builder.lastLocation(obj.get("lastLocation").getAsString());
        }
        if (obj.has("equipmentData") && !obj.get("equipmentData").isJsonNull()) {
            builder.equipmentData(obj.get("equipmentData").getAsString());
        }
        if (obj.has("perkData") && !obj.get("perkData").isJsonNull()) {
            builder.perkData(obj.get("perkData").getAsString());
        }
        
        return builder.build();
    }
    
    /**
     * Создание нового профиля с дефолтными значениями
     */
    public static PlayerProfile createNew(int slot, String className) {
        long now = System.currentTimeMillis();
        
        // Базовая мана зависит от класса
        int baseMana = switch (className.toLowerCase()) {
            case "warrior" -> 80;
            case "archer" -> 100;
            case "mage" -> 150;
            default -> 100;
        };
        
        return new Builder(slot)
                .className(className)
                .displayName(className)
                .level(1)
                .experience(0)
                .freeStatPoints(0)
                .skillPoints(1) // Стартовый навык
                .orbs(0)
                .currentMana(baseMana)
                .maxMana(baseMana)
                .createdAt(now)
                .lastPlayed(now)
                .build();
    }
    
    /**
     * Builder для создания модифицированных копий
     */
    public Builder toBuilder() {
        return new Builder(slot)
                .className(className)
                .displayName(displayName)
                .level(level)
                .experience(experience)
                .freeStatPoints(freeStatPoints)
                .skillPoints(skillPoints)
                .orbs(orbs)
                .currentMana(currentMana)
                .maxMana(maxMana)
                .stats(stats)
                .lastLocation(lastLocation)
                .equipmentData(equipmentData)
                .perkData(perkData)
                .createdAt(createdAt)
                .lastPlayed(lastPlayed);
    }
    
    public static class Builder {
        private final int slot;
        private String className;
        private String displayName;
        private int level = 1;
        private int experience = 0;
        private int freeStatPoints = 0;
        private int skillPoints = 0;
        private int orbs = 0;
        private int currentMana = 100;
        private int maxMana = 100;
        private final Map<String, Integer> stats = new HashMap<>();
        private String lastLocation = null;
        private String equipmentData = null;
        private String perkData = null;
        private long createdAt = System.currentTimeMillis();
        private long lastPlayed = System.currentTimeMillis();
        
        public Builder(int slot) {
            this.slot = slot;
        }
        
        public Builder className(String className) {
            this.className = className;
            return this;
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        
        public Builder experience(int experience) {
            this.experience = experience;
            return this;
        }
        
        public Builder freeStatPoints(int points) {
            this.freeStatPoints = points;
            return this;
        }
        
        public Builder skillPoints(int points) {
            this.skillPoints = points;
            return this;
        }
        
        public Builder orbs(int orbs) {
            this.orbs = orbs;
            return this;
        }
        
        public Builder currentMana(int currentMana) {
            this.currentMana = currentMana;
            return this;
        }
        
        public Builder maxMana(int maxMana) {
            this.maxMana = maxMana;
            return this;
        }
        
        public Builder stat(String name, int value) {
            this.stats.put(name, value);
            return this;
        }
        
        public Builder stats(Map<String, Integer> stats) {
            this.stats.clear();
            this.stats.putAll(stats);
            return this;
        }
        
        public Builder lastLocation(String lastLocation) {
            this.lastLocation = lastLocation;
            return this;
        }
        
        public Builder equipmentData(String equipmentData) {
            this.equipmentData = equipmentData;
            return this;
        }
        
        public Builder perkData(String perkData) {
            this.perkData = perkData;
            return this;
        }
        
        public Builder createdAt(long timestamp) {
            this.createdAt = timestamp;
            return this;
        }
        
        public Builder lastPlayed(long timestamp) {
            this.lastPlayed = timestamp;
            return this;
        }
        
        public PlayerProfile build() {
            return new PlayerProfile(this);
        }
    }
}
