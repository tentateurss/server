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
     * Проверить и разблокировать стартовый узел если нужно.
     *
     * <p>Дополнительно: если игрок сменил класс (например, был warrior, стал
     * archer) — старый стартовый узел больше не соответствует. В этом случае
     * сбрасываем дерево полностью и выдаём новый стартовый узел; очки перков
     * возвращаются автоматически через PlayerPerkManager.updatePointsForLevel
     * на следующем входе/изменении уровня. До фикса игрок видел warrior-дерево
     * вместо archer-дерева, потому что start_warrior был в allocatedNodes ещё
     * с прошлого класса.
     */
    private void checkAndUnlockStartNode(Player player) {
        try {
            EclipsiaAPI api = EclipsiaAPI.getInstance();
            PlayerData coreData = api.getPlayerData(player);

            if (coreData == null || coreData.getClassName() == null) {
                return; // класс ещё не выбран
            }

            String className = coreData.getClassName();
            PlayerPerkData perkData = playerManager.getPlayerData(player.getUniqueId());

            String startNodeId = treeManager.getStartNodeForClass(className);
            if (startNodeId == null) {
                return;
            }
            PerkNode startNode = treeManager.getNode(startNodeId);
            if (startNode == null) {
                return;
            }

            // Если уже разблокирован нужный стартовый узел — ничего не делаем.
            if (perkData.getAllocatedNodes().contains(startNodeId)) {
                return;
            }

            // Если у игрока есть СТАРЫЙ стартовый узел от другого класса —
            // сбрасываем дерево и выдаём новый. Иначе он застрянет в дереве
            // не своего класса и не сможет ничего прокачать.
            boolean hadOtherStart = false;
            for (String otherClass : new String[]{"warrior", "archer", "mage"}) {
                if (otherClass.equalsIgnoreCase(className)) continue;
                String otherStartId = treeManager.getStartNodeForClass(otherClass);
                if (otherStartId != null && perkData.getAllocatedNodes().contains(otherStartId)) {
                    hadOtherStart = true;
                    break;
                }
            }
            if (hadOtherStart) {
                perkData.resetAll();
                int level = coreData.getLevel();
                playerManager.updatePointsForLevel(player.getUniqueId(), level);
                player.sendMessage("§e⚠ Класс изменился — дерево перков сброшено, точки возвращены.");
            }

            // Автоматически разблокируем стартовый узел текущего класса (бесплатно).
            if (perkData.allocateNode(startNodeId, 0)) {
                player.sendMessage("§a✓ Разблокирован стартовый узел: " + startNode.getName());
                player.sendMessage("§7Откройте §e/perks §7чтобы начать прокачку");
                playerManager.savePlayerData(player.getUniqueId());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
