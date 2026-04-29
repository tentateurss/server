package ru.eclipsia.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.classes.PlayerClass;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.events.ClassSelectedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Обработчик кликов в GUI выбора класса
 */
public class ClassSelectionListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (!event.getView().getTitle().equals("§8Выбор класса")) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // Определяем класс по материалу
        String classId = getClassIdByMaterial(clicked.getType());
        
        if (classId == null) return;
        
        PlayerClass playerClass = ClassManager.getInstance().getClass(classId);
        if (playerClass == null) return;
        
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        if (data == null) return;
        
        // Проверяем, не выбран ли уже класс (блокируем повторный выбор)
        if (data.getClassName() != null && !data.getClassName().isEmpty()) {
            player.sendMessage("§cВы уже выбрали класс: " + ClassManager.getInstance().getClass(data.getClassName()).getDisplayName());
            player.sendMessage("§7Изменить класс невозможно!");
            player.closeInventory();
            return;
        }
        
        // Проверяем, не выбран ли уже этот класс
        if (classId.equals(data.getClassName())) {
            player.sendMessage("§eУ вас уже выбран этот класс!");
            player.closeInventory();
            return;
        }
        
        // Устанавливаем класс и базовые статы
        Map<String, Integer> baseStats = new HashMap<>();
        playerClass.getBaseStats().forEach((stat, value) -> {
            baseStats.put(stat, playerClass.calculateStat(stat, data.getLevel()));
        });
        
        PlayerData updatedData = data.toBuilder()
                .className(classId)
                .stats(baseStats)
                .build();
        
        DataManager.getInstance().savePlayer(updatedData);
        
        // Обновляем здоровье игрока
        double health = playerClass.calculateHealth(data.getLevel());
        player.setMaxHealth(health);
        player.setHealth(health);
        
        player.closeInventory();
        player.sendMessage("§a✓ Вы выбрали класс: " + playerClass.getDisplayName());
        player.sendMessage("§7Используйте §f/stats §7для просмотра характеристик");

        // Уведомляем подписчиков (Perks → стартовый узел, Skills → стартовый
        // эклипс, и т.д.). Делаем это с задержкой 5 тиков чтобы savePlayer
        // (async) успел отработать и кэш был согласованным.
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("EclipsiaCore"),
            () -> Bukkit.getPluginManager().callEvent(new ClassSelectedEvent(player, classId)),
            5L
        );
    }
    
    private String getClassIdByMaterial(Material material) {
        return switch (material) {
            case IRON_SWORD -> "warrior";
            case BOW -> "archer";
            case BLAZE_ROD -> "mage";
            default -> null;
        };
    }
}
