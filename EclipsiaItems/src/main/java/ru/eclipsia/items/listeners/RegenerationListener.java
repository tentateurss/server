package ru.eclipsia.items.listeners;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.item.ItemSlot;

/**
 * Слушатель для регенерации здоровья от аффиксов
 */
public class RegenerationListener implements Listener {
    
    private final Plugin plugin;
    private final EquipmentManager equipmentManager;
    
    public RegenerationListener(Plugin plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
    }
    
    /**
     * Запустить таймер регенерации при входе игрока
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Запускаем таймер регенерации через 20 тиков (1 секунда)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startRegenerationTask(player);
        }, 20L);
    }
    
    /**
     * Запустить задачу регенерации для игрока
     */
    private void startRegenerationTask(Player player) {
        // Регенерация каждые 2 секунды (40 тиков)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            
            // Получаем бонус регенерации от экипировки
            double regenBonus = getRegenerationBonus(player);
            
            if (regenBonus > 0) {
                // Применяем регенерацию
                AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (healthAttr != null) {
                    double maxHealth = healthAttr.getValue();
                    double currentHealth = player.getHealth();
                    
                    if (currentHealth < maxHealth) {
                        double newHealth = Math.min(currentHealth + regenBonus, maxHealth);
                        player.setHealth(newHealth);
                        
                        // ActionBar НЕ пишем — единая HUD-строка живёт в
                        // HUDActionBarListener (EclipsiaSkills); старая надпись
                        // "+X HP" на 1с подменяла HUD и вызывала мерцание.
                    }
                }
            }
            
        }, 40L, 40L); // Каждые 2 секунды
    }
    
    /**
     * Получить бонус регенерации от экипировки
     */
    private double getRegenerationBonus(Player player) {
        PlayerEquipment equipment = equipmentManager.getEquipment(player.getUniqueId());
        if (equipment == null) return 0.0;
        
        double totalRegen = 0.0;
        
        // Проходим по всем слотам экипировки
        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack item = equipment.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasLore()) continue;
            
            // Парсим лор для поиска регенерации
            for (String line : meta.getLore()) {
                String cleaned = line.replaceAll("§.", "");
                
                if (cleaned.contains("Регенерация:") || cleaned.contains("Восстановление:")) {
                    int value = extractValue(cleaned);
                    totalRegen += value;
                }
            }
        }
        
        return totalRegen;
    }
    
    /**
     * Извлечь числовое значение из строки
     */
    private int extractValue(String line) {
        try {
            String[] parts = line.split(":");
            if (parts.length < 2) return 0;
            
            String valuePart = parts[1].trim().replaceAll("[^0-9]", "");
            return Integer.parseInt(valuePart);
        } catch (Exception e) {
            return 0;
        }
    }
}
