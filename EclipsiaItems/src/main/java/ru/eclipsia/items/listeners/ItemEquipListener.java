package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.core.api.EclipsiaAPI;

/**
 * Обработчик экипировки предметов
 */
public class ItemEquipListener implements Listener {
    
    private final EclipsiaItems plugin;
    
    public ItemEquipListener(EclipsiaItems plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        
        // Проверяем это кастомный предмет
        if (!isCustomItem(meta)) {
            return;
        }
        
        // Проверяем требования
        if (!checkRequirements(player, meta)) {
            event.setCancelled(true);
            player.sendMessage("§cВы не соответствуете требованиям этого предмета!");
        }
    }
    
    /**
     * Проверить является ли предмет кастомным
     */
    private boolean isCustomItem(ItemMeta meta) {
        if (!meta.hasLore()) {
            return false;
        }
        
        // Проверяем есть ли в лоре строка с редкостью
        for (String line : meta.getLore()) {
            if (line.contains("Обычный") || line.contains("Магический") || 
                line.contains("Редкий") || line.contains("Уникальный")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Проверить требования предмета
     */
    private boolean checkRequirements(Player player, ItemMeta meta) {
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        
        String playerClass = api.getPlayerClassName(player);
        int playerLevel = api.getPlayerLevel(player);
        
        for (String line : meta.getLore()) {
            // Проверка класса
            if (line.contains("Требуется класс:")) {
                String requiredClass = extractRequirement(line);
                if (!matchesClass(playerClass, requiredClass)) {
                    return false;
                }
            }
            
            // Проверка уровня
            if (line.contains("Требуется уровень:")) {
                try {
                    int requiredLevel = Integer.parseInt(extractRequirement(line));
                    if (playerLevel < requiredLevel) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // Игнорируем ошибки парсинга
                }
            }
        }
        
        return true;
    }
    
    /**
     * Извлечь требование из строки лора
     */
    private String extractRequirement(String line) {
        // Убираем цветовые коды и извлекаем значение после ":"
        String cleaned = line.replaceAll("§.", "");
        int colonIndex = cleaned.indexOf(":");
        if (colonIndex != -1 && colonIndex < cleaned.length() - 1) {
            return cleaned.substring(colonIndex + 1).trim();
        }
        return "";
    }
    
    /**
     * Проверить соответствие класса
     */
    private boolean matchesClass(String playerClass, String requiredClass) {
        if (requiredClass.isEmpty()) {
            return true;
        }
        
        // Нормализуем названия классов
        String normalizedPlayer = normalizeClassName(playerClass);
        String normalizedRequired = normalizeClassName(requiredClass);
        
        return normalizedPlayer.equalsIgnoreCase(normalizedRequired);
    }
    
    /**
     * Нормализовать название класса
     */
    private String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        
        String lower = className.toLowerCase();
        if (lower.contains("воин") || lower.contains("warrior")) {
            return "warrior";
        } else if (lower.contains("лучник") || lower.contains("archer")) {
            return "archer";
        } else if (lower.contains("маг") || lower.contains("mage")) {
            return "mage";
        }
        
        return className.toLowerCase();
    }
}
