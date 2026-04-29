package ru.eclipsia.perks.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.events.ClassSelectedEvent;
import ru.eclipsia.perks.node.PerkNode;
import ru.eclipsia.perks.player.PlayerPerkData;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

/**
 * Слушатель для автоматической разблокировки стартового узла
 */
public class ClassStartNodeListener implements Listener {
    
    private final PlayerPerkManager playerManager;
    private final PerkTreeManager treeManager;
    
    public ClassStartNodeListener(PlayerPerkManager playerManager, PerkTreeManager treeManager) {
        this.playerManager = playerManager;
        this.treeManager = treeManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем через 2 тика после входа (после загрузки данных)
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("EclipsiaPerks"),
            () -> checkAndUnlockStartNode(player),
            2L
        );
    }

    /**
     * Игрок только что выбрал класс через /class GUI.
     * Без этого хука первый класс-выбор не давал стартовый узел до
     * следующего PlayerJoin (друг видел пустое дерево).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClassSelected(ClassSelectedEvent event) {
        checkAndUnlockStartNode(event.getPlayer());
    }
    
    /**
     * Проверить и разблокировать стартовый узел если нужно
     */
    private void checkAndUnlockStartNode(Player player) {
        try {
            // Получаем класс игрока
            EclipsiaAPI api = EclipsiaAPI.getInstance();
            PlayerData coreData = api.getPlayerData(player);
            
            if (coreData == null || coreData.getClassName() == null) {
                return; // Класс еще не выбран
            }
            
            String className = coreData.getClassName();
            PlayerPerkData perkData = playerManager.getPlayerData(player.getUniqueId());
            
            // Если у игрока уже есть взятые узлы - ничего не делаем
            if (perkData.getAllocatedCount() > 0) {
                return;
            }
            
            // Получаем стартовый узел для класса
            String startNodeId = treeManager.getStartNodeForClass(className);
            if (startNodeId == null) {
                return;
            }
            
            PerkNode startNode = treeManager.getNode(startNodeId);
            if (startNode == null) {
                return;
            }
            
            // Автоматически разблокируем стартовый узел (бесплатно)
            if (perkData.allocateNode(startNodeId, 0)) {
                player.sendMessage("§a✓ Разблокирован стартовый узел: " + startNode.getName());
                player.sendMessage("§7Откройте §e/perks §7чтобы начать прокачку");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
