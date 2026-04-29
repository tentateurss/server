package ru.eclipsia.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Иммутабельный класс для хранения данных игрока.
 * Поддерживает сериализацию в JSON для легкой миграции между хранилищами.
 * 
 * СИСТЕМА ПРОФИЛЕЙ:
 * - Игрок может иметь до 3 профилей (слоты 0, 1, 2)
 * - activeSlot указывает на текущий активный профиль
 * - Все данные (класс, уровень, статы, экипировка, перки) хранятся в профилях
 */
public class PlayerData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final UUID uuid;
    private final int activeSlot; // -1 если не выбран
    private final List<PlayerProfile> profiles; // Максимум 3 профиля
    private final long lastSave;
    
    private PlayerData(Builder builder) {
        this.uuid = builder.uuid;
        this.activeSlot = builder.activeSlot;
        this.profiles = new ArrayList<>(builder.profiles);
        this.lastSave = builder.lastSave;
    }
    
    // Getters
    public UUID getUuid() { return uuid; }
    public int getActiveSlot() { return activeSlot; }
    public List<PlayerProfile> getProfiles() { return new ArrayList<>(profiles); }
    public long getLastSave() { return lastSave; }
    
    // Методы-делегаты к активному профилю
    public PlayerProfile getActiveProfile() {
        if (activeSlot < 0 || activeSlot >= profiles.size()) return null;
        return profiles.get(activeSlot);
    }
    
    public PlayerProfile getProfile(int slot) {
        if (slot < 0 || slot >= profiles.size()) return null;
        return profiles.get(slot);
    }
    
    public String getClassName() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getClassName() : null;
    }
    
    public int getLevel() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getLevel() : 1;
    }
    
    public int getExperience() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getExperience() : 0;
    }
    
    public int getFreeStatPoints() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getFreeStatPoints() : 0;
    }
    
    public int getStat(String statName) {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getStat(statName) : 0;
    }
    
    public Map<String, Integer> getStats() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getStats() : new HashMap<>();
    }
    
    public int getOrbs() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getOrbs() : 0;
    }
    
    public String getEquipmentData() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getEquipmentData() : null;
    }
    
    public String getPerkData() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getPerkData() : null;
    }
    
    public int getSkillPoints() {
        PlayerProfile profile = getActiveProfile();
        return profile != null ? profile.getSkillPoints() : 0;
    }
    
    public boolean hasFreeSlot() {
        return profiles.stream().anyMatch(p -> p == null);
    }
    
    public int getFreeSlot() {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i) == null) return i;
        }
        return -1;
    }
    
    /**
     * Сериализация в JSON строку
     */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.addProperty("activeSlot", activeSlot);
        json.addProperty("lastSave", lastSave);
        
        JsonArray profilesArray = new JsonArray();
        for (PlayerProfile profile : profiles) {
            if (profile != null) {
                profilesArray.add(GSON.fromJson(profile.toJson(), JsonObject.class));
            } else {
                // Баг: GSON.fromJson("null", JsonObject.class) кидает JsonSyntaxException.
                // Нужно добавлять JsonNull напрямую.
                profilesArray.add(JsonNull.INSTANCE);
            }
        }
        json.add("profiles", profilesArray);
        
        return GSON.toJson(json);
    }
    
    /**
     * Десериализация из JSON строки
     */
    public static PlayerData fromJson(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        
        Builder builder = new Builder(UUID.fromString(obj.get("uuid").getAsString()));
        
        // Проверяем новый формат с профилями
        if (obj.has("profiles")) {
            builder.activeSlot(obj.get("activeSlot").getAsInt());
            builder.lastSave(obj.get("lastSave").getAsLong());
            
            JsonArray profilesArray = obj.getAsJsonArray("profiles");
            for (int i = 0; i < profilesArray.size(); i++) {
                if (!profilesArray.get(i).isJsonNull()) {
                    PlayerProfile profile = PlayerProfile.fromJson(profilesArray.get(i).toString());
                    builder.setProfile(i, profile);
                }
            }
        } else {
            // МИГРАЦИЯ: старый формат без профилей
            // Создаем один профиль в слоте 0 из старых данных
            PlayerProfile.Builder profileBuilder = new PlayerProfile.Builder(0);
            
            if (obj.has("className") && !obj.get("className").isJsonNull()) {
                profileBuilder.className(obj.get("className").getAsString());
            }
            
            profileBuilder.level(obj.get("level").getAsInt())
                   .experience(obj.get("experience").getAsInt())
                   .freeStatPoints(obj.get("freeStatPoints").getAsInt())
                   .orbs(obj.has("orbs") ? obj.get("orbs").getAsInt() : 0)
                   .createdAt(obj.get("lastSave").getAsLong())
                   .lastPlayed(obj.get("lastSave").getAsLong());
            
            if (obj.has("stats")) {
                JsonObject statsObj = obj.getAsJsonObject("stats");
                for (String key : statsObj.keySet()) {
                    profileBuilder.stat(key, statsObj.get(key).getAsInt());
                }
            }
            
            if (obj.has("equipmentData") && !obj.get("equipmentData").isJsonNull()) {
                profileBuilder.equipmentData(obj.get("equipmentData").getAsString());
            }
            if (obj.has("perkData") && !obj.get("perkData").isJsonNull()) {
                profileBuilder.perkData(obj.get("perkData").getAsString());
            }
            
            PlayerProfile migratedProfile = profileBuilder.build();
            
            builder.activeSlot(0)
                   .setProfile(0, migratedProfile)
                   .lastSave(obj.get("lastSave").getAsLong());
        }
        
        return builder.build();
    }
    
    /**
     * Создание нового игрока с дефолтными значениями
     */
    public static PlayerData createNew(UUID uuid) {
        return new Builder(uuid)
                .activeSlot(-1)
                .lastSave(System.currentTimeMillis())
                .build();
    }
    
    /**
     * Builder для создания модифицированных копий
     */
    public Builder toBuilder() {
        Builder builder = new Builder(uuid)
                .activeSlot(activeSlot)
                .lastSave(lastSave);
        
        for (int i = 0; i < profiles.size(); i++) {
            builder.setProfile(i, profiles.get(i));
        }
        
        return builder;
    }
    
    public static class Builder {
        private final UUID uuid;
        private int activeSlot = -1;
        private final List<PlayerProfile> profiles = Arrays.asList(null, null, null);
        private long lastSave = System.currentTimeMillis();
        
        public Builder(UUID uuid) {
            this.uuid = uuid;
        }
        
        public Builder activeSlot(int slot) {
            this.activeSlot = slot;
            return this;
        }
        
        public Builder setProfile(int slot, PlayerProfile profile) {
            if (slot >= 0 && slot < 3) {
                profiles.set(slot, profile);
            }
            return this;
        }
        
        public Builder lastSave(long timestamp) {
            this.lastSave = timestamp;
            return this;
        }
        
        // ОБРАТНАЯ СОВМЕСТИМОСТЬ: методы для старого API.
        // Все методы пишут в АКТИВНЫЙ профиль (activeSlot), а не в slot 0.
        // Если активного профиля нет — fallback на slot 0 (для миграции).
        private int targetSlot() {
            return (activeSlot >= 0 && activeSlot < 3) ? activeSlot : 0;
        }

        public Builder className(String className) {
            int slot = targetSlot();
            if (profiles.get(slot) == null && className != null) {
                profiles.set(slot, PlayerProfile.createNew(slot, className));
                if (activeSlot == -1) activeSlot = slot;
            }
            return this;
        }

        public Builder level(int level) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().level(level).build());
            }
            return this;
        }

        public Builder experience(int experience) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().experience(experience).build());
            }
            return this;
        }

        public Builder freeStatPoints(int points) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().freeStatPoints(points).build());
            }
            return this;
        }

        public Builder stat(String name, int value) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().stat(name, value).build());
            }
            return this;
        }

        public Builder stats(Map<String, Integer> stats) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().stats(stats).build());
            }
            return this;
        }

        public Builder orbs(int orbs) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().orbs(orbs).build());
            }
            return this;
        }

        public Builder equipmentData(String equipmentData) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().equipmentData(equipmentData).build());
            }
            return this;
        }

        public Builder perkData(String perkData) {
            int slot = targetSlot();
            if (profiles.get(slot) != null) {
                profiles.set(slot, profiles.get(slot).toBuilder().perkData(perkData).build());
            }
            return this;
        }
        
        public PlayerData build() {
            return new PlayerData(this);
        }
    }
}
