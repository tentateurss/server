package ru.eclipsia.skills.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import ru.eclipsia.skills.eclipse.EclipseItem;

import java.util.HashMap;
import java.util.Map;

/**
 * Класс для сериализации/десериализации навыков игрока
 */
public class SkillData {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<Integer, String> skillSlots; // slot -> skillId
    private final Map<String, String[]> supportSlots; // "skillSlot" -> [support1Id, support2Id]
    /**
     * Маппинг слотов хотбара в слоты навыков. Ключ — индекс слота хотбара (0..8),
     * значение — индекс слота навыка (0..4). Сохраняется в JSON, чтобы при
     * перезаходе игрок мог сразу использовать ПКМ-навыки без переоткрытия GUI.
     */
    private final Map<Integer, Integer> hotbarMapping;

    public SkillData() {
        this.skillSlots = new HashMap<>();
        this.supportSlots = new HashMap<>();
        this.hotbarMapping = new HashMap<>();
    }

    public SkillData(Map<Integer, String> skillSlots, Map<String, String[]> supportSlots) {
        this(skillSlots, supportSlots, new HashMap<>());
    }

    public SkillData(Map<Integer, String> skillSlots,
                     Map<String, String[]> supportSlots,
                     Map<Integer, Integer> hotbarMapping) {
        this.skillSlots = new HashMap<>(skillSlots);
        this.supportSlots = new HashMap<>(supportSlots);
        this.hotbarMapping = new HashMap<>(hotbarMapping);
    }

    /** Записать маппинг hotbarSlot → skillSlot. */
    public void setHotbarMapping(int hotbarSlot, int skillSlot) {
        hotbarMapping.put(hotbarSlot, skillSlot);
    }

    /** Получить копию маппинга. */
    public Map<Integer, Integer> getHotbarMapping() {
        return new HashMap<>(hotbarMapping);
    }
    
    /**
     * Установить навык в слот
     */
    public void setSkill(int slot, String skillId) {
        if (skillId == null) {
            skillSlots.remove(slot);
        } else {
            skillSlots.put(slot, skillId);
        }
    }
    
    /**
     * Получить ID навыка из слота
     */
    public String getSkill(int slot) {
        return skillSlots.get(slot);
    }
    
    /**
     * Установить поддержку к навыку
     */
    public void setSupport(int skillSlot, int supportIndex, String supportId) {
        String key = String.valueOf(skillSlot);
        String[] supports = supportSlots.getOrDefault(key, new String[2]);
        
        if (supportIndex >= 0 && supportIndex < 2) {
            supports[supportIndex] = supportId;
            supportSlots.put(key, supports);
        }
    }
    
    /**
     * Получить поддержки для навыка
     */
    public String[] getSupports(int skillSlot) {
        return supportSlots.getOrDefault(String.valueOf(skillSlot), new String[2]);
    }
    
    /**
     * Сериализация в JSON
     */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.add("skills", GSON.toJsonTree(skillSlots));
        json.add("supports", GSON.toJsonTree(supportSlots));
        json.add("hotbar", GSON.toJsonTree(hotbarMapping));
        return GSON.toJson(json);
    }

    /**
     * Десериализация из JSON. Совместимо со старыми сохранениями без поля
     * {@code hotbar} — в этом случае hotbarMapping будет восстановлен в
     * SkillManager сканированием хотбара по material.
     */
    public static SkillData fromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new SkillData();
        }

        try {
            JsonObject json = GSON.fromJson(jsonString, JsonObject.class);

            Map<Integer, String> skills = new HashMap<>();
            if (json.has("skills")) {
                JsonObject skillsObj = json.getAsJsonObject("skills");
                for (String key : skillsObj.keySet()) {
                    skills.put(Integer.parseInt(key), skillsObj.get(key).getAsString());
                }
            }

            Map<String, String[]> supports = new HashMap<>();
            if (json.has("supports")) {
                JsonObject supportsObj = json.getAsJsonObject("supports");
                for (String key : supportsObj.keySet()) {
                    supports.put(key, GSON.fromJson(supportsObj.get(key), String[].class));
                }
            }

            Map<Integer, Integer> hotbar = new HashMap<>();
            if (json.has("hotbar")) {
                JsonObject hotbarObj = json.getAsJsonObject("hotbar");
                for (String key : hotbarObj.keySet()) {
                    hotbar.put(Integer.parseInt(key), hotbarObj.get(key).getAsInt());
                }
            }

            return new SkillData(skills, supports, hotbar);
        } catch (Exception e) {
            e.printStackTrace();
            return new SkillData();
        }
    }

    /**
     * Очистить все навыки
     */
    public void clear() {
        skillSlots.clear();
        supportSlots.clear();
        hotbarMapping.clear();
    }
}
