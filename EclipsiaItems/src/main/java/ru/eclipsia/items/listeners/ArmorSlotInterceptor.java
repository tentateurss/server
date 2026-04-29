package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.item.ItemSlot;

/**
 * Перехватывает предметы, которые попали в ванильные слоты брони
 * и перемещает их в кастомную экипировку
 */
public class ArmorSlotInterceptor implements Listener {
    
    private final EquipmentManager equipmentManager;
    
    public ArmorSlotInterceptor(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onArmorSlotClick(InventoryClickEvent event) {
        // Проверяем что это слот брони
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCursor();
        
        // Если игрок пытается положить предмет в слот брони
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            // Это кастомный предмет - перехватываем
            event.setCancelled(true);
            
            // Определяем слот по позиции
            ItemSlot targetSlot = getSlotFromArmorSlot(event.getSlot());
            if (targetSlot == null) {
                return;
            }
            
            // Убираем предмет из курсора
            ItemStack toEquip = item.clone();
            toEquip.setAmount(1);
            event.setCursor(null);
            
            // Экипируем в кастомную систему
            PlayerEquipment equipment = equipmentManager.getEquipment(player);
            ItemStack oldItem = equipment.equip(targetSlot, toEquip);
            equipmentManager.saveEquipment(player.getUniqueId());
            
            // Возвращаем старый предмет в инвентарь
            if (oldItem != null) {
                player.getInventory().addItem(oldItem);
            }
            
            player.sendMessage("§a✓ Экипировано в кастомную систему!");
        }
    }
    
    /**
     * Проверяет ванильные слоты брони после каждого тика
     * и перемещает кастомные предметы в GUI
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryInteract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Проверяем через тик, чтобы дать Minecraft обработать событие
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndMoveArmorToCustom(player);
            }
        }.runTaskLater(EclipsiaItems.getInstance(), 1L);
    }
    
    /**
     * Проверить ванильные слоты брони и переместить кастомные предметы
     */
    private void checkAndMoveArmorToCustom(Player player) {
        PlayerEquipment equipment = equipmentManager.getEquipment(player);
        boolean moved = false;
        
        // Проверяем каждый слот брони
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.hasItemMeta() && helmet.getItemMeta().hasLore()) {
            player.getInventory().setHelmet(null);
            ItemStack old = equipment.equip(ItemSlot.HEAD, helmet);
            if (old != null) player.getInventory().addItem(old);
            equipmentManager.saveEquipment(player.getUniqueId());
            moved = true;
        }
        
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.hasItemMeta() && chestplate.getItemMeta().hasLore()) {
            player.getInventory().setChestplate(null);
            ItemStack old = equipment.equip(ItemSlot.CHEST, chestplate);
            if (old != null) player.getInventory().addItem(old);
            equipmentManager.saveEquipment(player.getUniqueId());
            moved = true;
        }
        
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && leggings.hasItemMeta() && leggings.getItemMeta().hasLore()) {
            player.getInventory().setLeggings(null);
            ItemStack old = equipment.equip(ItemSlot.LEGS, leggings);
            if (old != null) player.getInventory().addItem(old);
            equipmentManager.saveEquipment(player.getUniqueId());
            moved = true;
        }
        
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.hasItemMeta() && boots.getItemMeta().hasLore()) {
            player.getInventory().setBoots(null);
            ItemStack old = equipment.equip(ItemSlot.FEET, boots);
            if (old != null) player.getInventory().addItem(old);
            equipmentManager.saveEquipment(player.getUniqueId());
            moved = true;
        }
        
        if (moved) {
            player.sendMessage("§e⚠ Кастомная броня перемещена в /equipment");
        }
    }
    
    /**
     * Определить ItemSlot по индексу слота брони
     */
    private ItemSlot getSlotFromArmorSlot(int slot) {
        // Слоты брони в инвентаре: 39=helmet, 38=chestplate, 37=leggings, 36=boots
        return switch (slot) {
            case 39 -> ItemSlot.HEAD;
            case 38 -> ItemSlot.CHEST;
            case 37 -> ItemSlot.LEGS;
            case 36 -> ItemSlot.FEET;
            default -> null;
        };
    }
}
