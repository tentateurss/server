package ru.eclipsia.items.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.item.ItemSlot;

/**
 * Постоянно мониторит ванильные слоты брони и синхронизирует с кастомной экипировкой
 */
public class ArmorSyncListener implements Listener {
    
    private final EquipmentManager equipmentManager;
    
    public ArmorSyncListener(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Проверяем ПКМ по аксессуарам (амулет, кольцо, пояс)
        if (item != null && isCustomItem(item)) {
            ItemSlot slot = determineAccessorySlot(item);
            if (slot != null) {
                // Это аксессуар - экипируем сразу
                event.setCancelled(true);
                equipAccessory(player, item, slot);
                return;
            }
        }
        
        // Проверяем через 2 тика после любого взаимодействия (для брони)
        Bukkit.getScheduler().runTaskLater(EclipsiaItems.getInstance(), () -> {
            syncArmorToCustomEquipment(player);
        }, 2L);
    }
    
    /**
     * Экипировать аксессуар
     */
    private void equipAccessory(Player player, ItemStack item, ItemSlot slot) {
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        
        // Клонируем предмет
        ItemStack toEquip = item.clone();
        toEquip.setAmount(1);
        
        // Убираем из руки
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        
        // Экипируем
        ItemStack oldItem = equipment.equip(slot, toEquip);
        equipmentManager.saveEquipment(player.getUniqueId());
        
        // Возвращаем старый предмет
        if (oldItem != null) {
            player.getInventory().addItem(oldItem);
        }
        
        String slotName = getSlotDisplayName(slot);
        player.sendMessage("§a✓ Экипировано: " + item.getItemMeta().getDisplayName() + " §7→ " + slotName);
    }
    
    /**
     * Определить слот аксессуара по лору
     */
    private ItemSlot determineAccessorySlot(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return null;
        }
        
        for (String line : item.getItemMeta().getLore()) {
            String cleaned = line.replaceAll("§.", "").trim().toLowerCase();
            
            if (cleaned.contains("тип:")) {
                if (cleaned.contains("амулет") || cleaned.contains("amulet") || cleaned.contains("ожерелье")) {
                    return ItemSlot.AMULET;
                }
                if (cleaned.contains("кольцо") || cleaned.contains("ring")) {
                    return ItemSlot.RING_1;
                }
                if (cleaned.contains("пояс") || cleaned.contains("belt")) {
                    return ItemSlot.BELT;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Получить отображаемое имя слота
     */
    private String getSlotDisplayName(ItemSlot slot) {
        return switch (slot) {
            case HEAD -> "§7Голова";
            case CHEST -> "§7Грудь";
            case LEGS -> "§7Ноги";
            case FEET -> "§7Ботинки";
            case HAND -> "§7Оружие";
            case OFFHAND -> "§7Доп. оружие";
            case AMULET -> "§7Амулет";
            case RING_1, RING_2 -> "§7Кольцо";
            case BELT -> "§7Пояс";
        };
    }
    
    /**
     * Синхронизировать ванильную броню с кастомной экипировкой
     */
    private void syncArmorToCustomEquipment(Player player) {
        if (!player.isOnline()) {
            return;
        }
        
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        boolean synced = false;
        
        // Проверяем шлем
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && isCustomItem(helmet)) {
            player.getInventory().setHelmet(null);
            ItemStack old = equipment.equip(ItemSlot.HEAD, helmet);
            if (old != null) {
                player.getInventory().addItem(old);
            }
            synced = true;
        }
        
        // Проверяем нагрудник
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && isCustomItem(chestplate)) {
            player.getInventory().setChestplate(null);
            ItemStack old = equipment.equip(ItemSlot.CHEST, chestplate);
            if (old != null) {
                player.getInventory().addItem(old);
            }
            synced = true;
        }
        
        // Проверяем штаны
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && isCustomItem(leggings)) {
            player.getInventory().setLeggings(null);
            ItemStack old = equipment.equip(ItemSlot.LEGS, leggings);
            if (old != null) {
                player.getInventory().addItem(old);
            }
            synced = true;
        }
        
        // Проверяем ботинки
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && isCustomItem(boots)) {
            player.getInventory().setBoots(null);
            ItemStack old = equipment.equip(ItemSlot.FEET, boots);
            if (old != null) {
                player.getInventory().addItem(old);
            }
            synced = true;
        }
        
        if (synced) {
            equipmentManager.saveEquipment(player.getUniqueId());
            player.sendMessage("§a✓ Броня экипирована в кастомную систему!");
            player.sendMessage("§7Откройте §e/equipment §7чтобы увидеть экипировку");
        }
    }
    
    /**
     * Проверить является ли предмет кастомным
     */
    private boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        if (!item.getItemMeta().hasLore()) {
            return false;
        }
        
        // Проверяем есть ли в лоре строка с редкостью
        for (String line : item.getItemMeta().getLore()) {
            String cleaned = line.replaceAll("§.", "");
            if (cleaned.contains("Обычный") || cleaned.contains("Магический") || 
                cleaned.contains("Редкий") || cleaned.contains("Уникальный") ||
                cleaned.contains("Common") || cleaned.contains("Magic") ||
                cleaned.contains("Rare") || cleaned.contains("Unique")) {
                return true;
            }
        }
        
        return false;
    }
}
