package ru.eclipsia.items.equipment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.eclipsia.items.item.ItemSlot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Экипировка игрока
 */
public class PlayerEquipment {
    
    private static final Gson GSON = new GsonBuilder().create();
    
    private final UUID playerUUID;
    private final Map<ItemSlot, ItemStack> equipment;
    
    public PlayerEquipment(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.equipment = new HashMap<>();
    }
    
    /**
     * Экипировать предмет в слот
     */
    public ItemStack equip(ItemSlot slot, ItemStack item) {
        // Если слот - кольцо, пытаемся найти свободный слот
        if (slot == ItemSlot.RING_1 || slot == ItemSlot.RING_2) {
            if (!equipment.containsKey(ItemSlot.RING_1)) {
                return equipment.put(ItemSlot.RING_1, item);
            } else if (!equipment.containsKey(ItemSlot.RING_2)) {
                return equipment.put(ItemSlot.RING_2, item);
            } else {
                // Оба слота заняты, заменяем первое кольцо
                return equipment.put(ItemSlot.RING_1, item);
            }
        }
        
        return equipment.put(slot, item);
    }
    
    /**
     * Снять предмет из слота
     */
    public ItemStack unequip(ItemSlot slot) {
        return equipment.remove(slot);
    }
    
    /**
     * Получить предмет из слота
     */
    public ItemStack getItem(ItemSlot slot) {
        return equipment.get(slot);
    }
    
    /**
     * Проверить занят ли слот
     */
    public boolean hasItem(ItemSlot slot) {
        return equipment.containsKey(slot);
    }
    
    /**
     * Получить всю экипировку
     */
    public Map<ItemSlot, ItemStack> getAllEquipment() {
        return new HashMap<>(equipment);
    }
    
    /**
     * Очистить всю экипировку
     */
    public void clear() {
        equipment.clear();
    }
    
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    
    /**
     * ДОБАВЛЕНО: Сериализация в JSON
     */
    public String toJson() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", playerUUID.toString());
            
            JsonObject items = new JsonObject();
            for (Map.Entry<ItemSlot, ItemStack> entry : equipment.entrySet()) {
                if (entry.getValue() != null) {
                    String serialized = serializeItemStack(entry.getValue());
                    items.addProperty(entry.getKey().name(), serialized);
                }
            }
            json.add("items", items);
            
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
            
            if (obj.has("items")) {
                JsonObject items = obj.getAsJsonObject("items");
                equipment.clear();
                
                for (String key : items.keySet()) {
                    try {
                        ItemSlot slot = ItemSlot.valueOf(key);
                        String serialized = items.get(key).getAsString();
                        ItemStack item = deserializeItemStack(serialized);
                        if (item != null) {
                            equipment.put(slot, item);
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки десериализации отдельных предметов
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Сериализация ItemStack в Base64
     */
    private String serializeItemStack(ItemStack item) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
    
    /**
     * Десериализация ItemStack из Base64
     */
    private ItemStack deserializeItemStack(String data) throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}
