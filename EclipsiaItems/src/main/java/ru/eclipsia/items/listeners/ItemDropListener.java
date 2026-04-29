package ru.eclipsia.items.listeners;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.generator.ItemGenerator;
import ru.eclipsia.core.api.EclipsiaAPI;

import java.util.Random;

/**
 * Обработчик дропа предметов с мобов
 */
public class ItemDropListener implements Listener {
    
    private final EclipsiaItems plugin;
    private final ItemGenerator generator;
    private final Random random;
    
    public ItemDropListener(EclipsiaItems plugin, ItemGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
        this.random = new Random();
    }
    
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        // Проверяем что это не игрок
        if (event.getEntity() instanceof Player) {
            return;
        }
        
        // Проверяем что есть убийца
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        
        // Проверяем включен ли дроп
        if (!plugin.getConfig().getBoolean("drop.enabled", true)) {
            return;
        }
        
        // Получаем уровень моба (из PDC если это кастомный моб)
        LivingEntity mob = event.getEntity();
        int mobLevel = getMobLevel(mob);
        
        // Рассчитываем шанс дропа
        int baseChance = plugin.getConfig().getInt("drop.drop-chance", 30);
        int levelBonus = plugin.getConfig().getInt("drop.level-bonus", 2);
        int totalChance = baseChance + (mobLevel * levelBonus);
        
        // Проверяем шанс
        if (random.nextInt(100) >= totalChance) {
            return;
        }
        
        // Определяем количество предметов
        int maxItems = plugin.getConfig().getInt("drop.max-items-per-mob", 2);
        int itemCount = 1 + random.nextInt(maxItems);
        
        // Получаем класс игрока
        String playerClass = EclipsiaAPI.getInstance().getPlayerClassName(killer);
        
        // Генерируем предметы
        for (int i = 0; i < itemCount; i++) {
            // Уровень предмета = уровень моба +/- случайное смещение
            int minOffset = plugin.getConfig().getInt("generation.item-level-range.min-offset", -2);
            int maxOffset = plugin.getConfig().getInt("generation.item-level-range.max-offset", 2);
            int offset = minOffset + random.nextInt(maxOffset - minOffset + 1);
            int itemLevel = Math.max(1, mobLevel + offset);
            
            // Генерируем предмет
            ItemStack item;
            if (playerClass != null && !playerClass.isEmpty()) {
                item = generator.generateItemForClass(playerClass, itemLevel);
            } else {
                item = generator.generateItem(itemLevel);
            }
            
            if (item != null) {
                // Дропаем предмет
                event.getDrops().add(item);
                
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Дроп предмета: " + item.getItemMeta().getDisplayName() + 
                                          " (уровень " + itemLevel + ") с моба уровня " + mobLevel);
                }
            }
        }
    }
    
    /**
     * Получить уровень моба
     */
    private int getMobLevel(LivingEntity mob) {
        // Пытаемся получить уровень из PDC (если это кастомный моб из EclipsiaMobs)
        if (plugin.getServer().getPluginManager().getPlugin("EclipsiaMobs") != null) {
            try {
                // Проверяем есть ли у моба метка кастомного моба
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                    plugin.getServer().getPluginManager().getPlugin("EclipsiaMobs"), 
                    "custom_mob_id"
                );
                
                if (mob.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                    // Это кастомный моб, пытаемся получить его уровень
                    // TODO: Добавить API в EclipsiaMobs для получения уровня моба
                    // Пока возвращаем базовый уровень на основе типа моба
                    return getDefaultMobLevel(mob);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка получения уровня кастомного моба: " + e.getMessage());
            }
        }
        
        // Для обычных мобов возвращаем базовый уровень
        return getDefaultMobLevel(mob);
    }
    
    /**
     * Получить базовый уровень моба по типу
     */
    private int getDefaultMobLevel(LivingEntity mob) {
        switch (mob.getType()) {
            case ZOMBIE:
            case SKELETON:
                return 5;
            case SPIDER:
            case CAVE_SPIDER:
                return 7;
            case CREEPER:
                return 10;
            case ENDERMAN:
                return 15;
            case BLAZE:
            case WITHER_SKELETON:
                return 20;
            default:
                return 1;
        }
    }
}
