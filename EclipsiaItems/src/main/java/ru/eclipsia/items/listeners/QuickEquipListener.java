package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.item.ItemSlot;
import ru.eclipsia.items.menu.MenuBook;

/**
 * Listener for quick equipment via RMB
 */
public class QuickEquipListener implements Listener {
    
    private final EquipmentManager equipmentManager;
    
    public QuickEquipListener(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        if (MenuBook.isMenuBook(item)) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        
        if (!isCustomItem(meta)) {
            return;
        }
        
        ItemSlot targetSlot = determineItemSlot(meta);
        
        if (targetSlot == null) {
            return;
        }

        // FIX: cancel ВСЕГДА для кастомных предметов с распознанным слотом —
        // иначе ванильное поведение ПКМ авто-экипирует броню в armor-slot,
        // даже если уровень не подходит (баг 1.2).
        event.setCancelled(true);

        if (!checkRequirements(player, meta)) {
            return;
        }
        
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        
        if (targetSlot == ItemSlot.RING_1) {
            if (equipment.getItem(ItemSlot.RING_1) == null) {
                targetSlot = ItemSlot.RING_1;
            } else if (equipment.getItem(ItemSlot.RING_2) == null) {
                targetSlot = ItemSlot.RING_2;
            } else {
                targetSlot = ItemSlot.RING_1;
            }
        }
        
        ItemStack toEquip = item.clone();
        toEquip.setAmount(1);
        
        int originalAmount = item.getAmount();
        
        // FIXED: Remove from hand FIRST, then equip
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.isSimilar(item)) {
            if (originalAmount > 1) {
                handItem.setAmount(originalAmount - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
        
        ItemStack oldItem = equipment.equip(targetSlot, toEquip);
        
        equipmentManager.saveEquipment(player.getUniqueId());
        
        if (oldItem != null) {
            player.getInventory().addItem(oldItem);
        }
        
        String slotName = getSlotDisplayName(targetSlot);
        player.sendMessage("§a✓ Экипировано: " + meta.getDisplayName() + " §7→ " + slotName);
    }
    
    private ItemSlot determineItemSlot(ItemMeta meta) {
        for (String line : meta.getLore()) {
            String cleaned = line.replaceAll("§.", "").trim();
            
            if (cleaned.startsWith("Тип:") || cleaned.startsWith("Type:")) {
                String type = cleaned.substring(cleaned.indexOf(":") + 1).trim().toLowerCase();
                
                if (type.contains("шлем") || type.contains("helmet")) {
                    return ItemSlot.HEAD;
                }
                if (type.contains("нагрудник") || type.contains("chestplate")) {
                    return ItemSlot.CHEST;
                }
                if (type.contains("штаны") || type.contains("leggings") || type.contains("поножи")) {
                    return ItemSlot.LEGS;
                }
                if (type.contains("ботинки") || type.contains("boots") || type.contains("сапоги")) {
                    return ItemSlot.FEET;
                }
                if (type.contains("меч") || type.contains("sword")) {
                    return ItemSlot.HAND;
                }
                if (type.contains("лук") || type.contains("bow")) {
                    return ItemSlot.HAND;
                }
                if (type.contains("посох") || type.contains("staff")) {
                    return ItemSlot.HAND;
                }
                if (type.contains("амулет") || type.contains("amulet") || type.contains("ожерелье")) {
                    return ItemSlot.AMULET;
                }
                if (type.contains("кольцо") || type.contains("ring")) {
                    return ItemSlot.RING_1;
                }
                if (type.contains("пояс") || type.contains("belt")) {
                    return ItemSlot.BELT;
                }
            }
        }
        
        return null;
    }
    
    private boolean checkRequirements(Player player, ItemMeta meta) {
        ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
        
        int playerLevel = api.getPlayerLevel(player);
        
        for (String line : meta.getLore()) {
            String cleaned = line.replaceAll("§.", "").trim();
            
            if (cleaned.startsWith("Требуется уровень:") || cleaned.startsWith("Required level:")) {
                try {
                    String levelStr = cleaned.substring(cleaned.indexOf(":") + 1).trim();
                    int requiredLevel = Integer.parseInt(levelStr);
                    
                    if (playerLevel < requiredLevel) {
                        player.sendMessage("§cНедостаточный уровень!");
                        player.sendMessage("§7Требуется: §e" + requiredLevel + " §7| Ваш: §e" + playerLevel);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
            }
        }
        
        return true;
    }
    
    private boolean matchesClass(String playerClass, String requiredClass) {
        if (requiredClass == null || requiredClass.isEmpty()) {
            return true;
        }
        
        String normalizedPlayer = normalizeClassName(playerClass);
        String normalizedRequired = normalizeClassName(requiredClass);
        
        return normalizedPlayer.equalsIgnoreCase(normalizedRequired);
    }
    
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
    
    private boolean isCustomItem(ItemMeta meta) {
        if (!meta.hasLore()) {
            return false;
        }
        
        for (String line : meta.getLore()) {
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
}
