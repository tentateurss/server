package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.items.menu.PermanentArrow;

/**
 * Обработчик постоянной стрелы
 */
public class PermanentArrowListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем класс игрока
        ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
        String playerClass = api.getPlayerClassName(player);
        
        // Выдаем стрелу только лучникам
        if (!"archer".equalsIgnoreCase(playerClass)) {
            return;
        }
        
        // Проверяем есть ли стрела в правом верхнем углу инвентаря (слот 17)
        // Слот 17 = 2 ряд, последняя ячейка справа
        ItemStack arrowSlot = player.getInventory().getItem(17);
        
        if (arrowSlot == null || !PermanentArrow.isPermanentArrow(arrowSlot)) {
            // Если там что-то есть, перемещаем в первый свободный слот
            if (arrowSlot != null) {
                player.getInventory().addItem(arrowSlot);
            }
            
            // Выдаем постоянную стрелу
            player.getInventory().setItem(17, PermanentArrow.createPermanentArrow());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        
        // Запрещаем выбрасывать постоянную стрелу
        if (PermanentArrow.isPermanentArrow(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете выбросить магическую стрелу!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        int slot = event.getSlot();
        
        // Если кликнули по постоянной стреле
        if (currentItem != null && PermanentArrow.isPermanentArrow(currentItem)) {
            // Разрешаем перемещать только в слот 17
            if (slot != 17) {
                // Если это не слот 17 - разрешаем взять стрелу
                return;
            }
        }
        
        // Если пытаются положить постоянную стрелу
        if (cursorItem != null && PermanentArrow.isPermanentArrow(cursorItem)) {
            // Разрешаем класть только в слот 17
            if (slot != 17) {
                event.setCancelled(true);
                player.sendMessage("§cМагическая стрела должна находиться в правом верхнем углу инвентаря!");
                return;
            }
        }
        
        // Запрещаем класть что-то в слот 17 кроме постоянной стрелы
        if (slot == 17 && event.getRawSlot() == event.getSlot()) {
            if (cursorItem != null && !PermanentArrow.isPermanentArrow(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage("§cВ эту ячейку можно положить только магическую стрелу!");
            }
        }
    }
}
