package ru.eclipsia.perks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.eclipsia.perks.gui.PerkTreeGUI;
import ru.eclipsia.perks.node.PerkNode;
import ru.eclipsia.perks.player.PlayerPerkData;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

/**
 * Обработчик GUI дерева перков
 */
public class PerkTreeGUIListener implements Listener {
    
    private final PerkTreeGUI gui;
    private final PerkTreeManager treeManager;
    private final PlayerPerkManager playerManager;
    
    public PerkTreeGUIListener(PerkTreeGUI gui, PerkTreeManager treeManager, PlayerPerkManager playerManager) {
        this.gui = gui;
        this.treeManager = treeManager;
        this.playerManager = playerManager;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        // Проверяем что это GUI дерева перков
        if (!event.getView().getTitle().equals("§6Дерево перков")) return;
        
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        
        // Если клик вне GUI
        if (slot >= event.getInventory().getSize()) {
            return;
        }
        
        // Кнопки навигации камеры (нижний ряд UI)
        switch (slot) {
            case PerkTreeGUI.SLOT_CAMERA_UP    -> { gui.moveCamera(player, 0, -PerkTreeGUI.CAMERA_STEP); return; }
            case PerkTreeGUI.SLOT_CAMERA_DOWN  -> { gui.moveCamera(player, 0,  PerkTreeGUI.CAMERA_STEP); return; }
            case PerkTreeGUI.SLOT_CAMERA_LEFT  -> { gui.moveCamera(player, -PerkTreeGUI.CAMERA_STEP, 0); return; }
            case PerkTreeGUI.SLOT_CAMERA_RIGHT -> { gui.moveCamera(player,  PerkTreeGUI.CAMERA_STEP, 0); return; }
            case PerkTreeGUI.SLOT_CAMERA_RECENTER -> { gui.recenterCamera(player); return; }
        }

        // Кнопка сброса всех перков
        if (slot == PerkTreeGUI.SLOT_RESET_ALL && event.getClick() == ClickType.RIGHT) {
            handleReset(player);
            return;
        }

        // Остальная информационная панель (нижний ряд) — игнорируем
        if (slot >= 45) {
            return;
        }
        
        // Клик по узлу
        PerkNode node = gui.getNodeAtSlot(player, slot);
        if (node == null) {
            return;
        }
        
        PlayerPerkData data = playerManager.getPlayerData(player);
        
        // ЛКМ - взять узел
        if (event.getClick() == ClickType.LEFT) {
            handleAllocateNode(player, node, data);
        }
        // ПКМ - сбросить узел
        else if (event.getClick() == ClickType.RIGHT) {
            handleDeallocateNode(player, node, data);
        }
    }
    
    /**
     * Взять узел
     */
    private void handleAllocateNode(Player player, PerkNode node, PlayerPerkData data) {
        // Проверяем что узел еще не взят
        if (data.hasNode(node.getId())) {
            player.sendMessage("§cЭтот узел уже взят!");
            return;
        }
        
        // Проверяем что узел доступен
        if (!treeManager.canAllocateNode(node.getId(), data.getAllocatedNodes())) {
            player.sendMessage("§cСначала возьмите соседний узел!");
            return;
        }
        
        // Проверяем что хватает очков
        int cost = node.getCost();
        if (data.getAvailablePoints() < cost) {
            player.sendMessage("§cНедостаточно очков! Нужно: " + cost);
            return;
        }
        
        // Берем узел
        if (data.allocateNode(node.getId(), cost)) {
            player.sendMessage("§a✓ Узел взят: " + node.getName());
            
            // Применяем бонусы
            applyNodeBonuses(player, node);
            
            // Обновляем GUI
            gui.refresh(player);
        } else {
            player.sendMessage("§cОшибка при взятии узла!");
        }
    }
    
    /**
     * НОВОЕ: Сбросить отдельный узел (ПКМ)
     */
    private void handleDeallocateNode(Player player, PerkNode node, PlayerPerkData data) {
        // Проверяем что узел взят
        if (!data.hasNode(node.getId())) {
            player.sendMessage("§cЭтот узел не взят!");
            return;
        }
        
        // Проверяем что это не стартовый узел
        if (node.getCost() == 0) {
            player.sendMessage("§cНельзя сбросить стартовый узел класса!");
            return;
        }
        
        // Проверяем что от этого узла не зависят другие взятые узлы
        if (!canDeallocateNode(node.getId(), data)) {
            player.sendMessage("§cСначала сбросьте зависимые узлы!");
            player.sendMessage("§7От этого узла зависят другие взятые узлы");
            return;
        }
        
        // Убираем узел
        data.deallocateNode(node.getId());
        
        // Возвращаем очко (если узел платный)
        if (node.getCost() > 0) {
            data.addPoints(node.getCost());
        }
        
        player.sendMessage("§a✓ Узел сброшен: " + node.getName());
        player.sendMessage("§7Очков возвращено: " + node.getCost());
        
        // Убираем бонусы узла
        removeNodeBonuses(player, node);
        
        // Сохраняем данные перков
        playerManager.savePlayerData(player.getUniqueId());
        
        // Обновляем GUI
        gui.refresh(player);
    }
    
    /**
     * НОВОЕ: Проверить можно ли сбросить узел (нет зависимых)
     */
    private boolean canDeallocateNode(String nodeId, PlayerPerkData data) {
        PerkNode node = treeManager.getNode(nodeId);
        if (node == null) return false;
        
        // Получаем все взятые узлы
        java.util.Set<String> allocatedNodes = data.getAllocatedNodes();
        
        // Проверяем каждый взятый узел
        for (String allocatedId : allocatedNodes) {
            if (allocatedId.equals(nodeId)) continue; // Пропускаем сам узел
            
            PerkNode allocatedNode = treeManager.getNode(allocatedId);
            if (allocatedNode == null) continue;
            
            // Проверяем зависит ли взятый узел от удаляемого
            if (allocatedNode.getConnections().contains(nodeId)) {
                // Проверяем есть ли альтернативный путь к этому узлу
                if (!hasAlternativePath(allocatedId, nodeId, allocatedNodes)) {
                    return false; // Нет альтернативного пути - нельзя удалить
                }
            }
        }
        
        return true;
    }
    
    /**
     * НОВОЕ: Проверить есть ли альтернативный путь к узлу
     */
    private boolean hasAlternativePath(String targetNodeId, String excludeNodeId, java.util.Set<String> allocatedNodes) {
        PerkNode targetNode = treeManager.getNode(targetNodeId);
        if (targetNode == null) return false;
        
        // Проверяем все связи целевого узла
        for (String connectionId : targetNode.getConnections()) {
            if (connectionId.equals(excludeNodeId)) continue; // Пропускаем исключаемый узел
            
            // Если связь взята - есть альтернативный путь
            if (allocatedNodes.contains(connectionId)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * НОВОЕ: Убрать бонусы от узла
     */
    private void removeNodeBonuses(Player player, PerkNode node) {
        if (node.getStats().isEmpty()) return;
        
        try {
            ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
            ru.eclipsia.core.data.PlayerData data = api.getPlayerData(player);
            
            if (data != null) {
                // Создаем новые статы с убранными бонусами
                java.util.Map<String, Integer> newStats = new java.util.HashMap<>(data.getStats());
                
                for (java.util.Map.Entry<String, Integer> entry : node.getStats().entrySet()) {
                    String statName = entry.getKey();
                    int bonus = entry.getValue();
                    
                    // Убираем бонус (может быть отрицательным для дебаффов)
                    int currentValue = newStats.getOrDefault(statName, 0);
                    newStats.put(statName, currentValue - bonus);
                }
                
                // Обновляем данные игрока
                ru.eclipsia.core.data.PlayerData updatedData = data.toBuilder()
                    .stats(newStats)
                    .build();
                
                ru.eclipsia.core.data.DataManager.getInstance().savePlayer(updatedData);
                
                // Применяем бонусы статов
                ru.eclipsia.core.stats.StatsBonusApplier.applyAllBonuses(player);
                
                // НОВОЕ: Применяем бонусы экипировки
                reapplyEquipmentBonuses(player);
                
                // Показываем что потерял игрок
                player.sendMessage("§7Потеряно:");
                for (String line : node.getStatsDescription().split("\n")) {
                    player.sendMessage("  " + line);
                }
            }
        } catch (Exception e) {
            player.sendMessage("§cОшибка удаления бонусов!");
            e.printStackTrace();
        }
    }
    
    /**
     * Сбросить все узлы
     */
    private void handleReset(Player player) {
        PlayerPerkData data = playerManager.getPlayerData(player);
        
        if (data.getAllocatedCount() == 0) {
            player.sendMessage("§cУ вас нет взятых узлов!");
            return;
        }
        
        // ИСПРАВЛЕНО: Считаем только платные узлы (без стартового)
        int totalNodes = data.getAllocatedCount();
        int paidNodes = 0;
        
        // Подсчитываем сколько узлов было куплено за очки
        for (String nodeId : data.getAllocatedNodes()) {
            PerkNode node = treeManager.getNode(nodeId);
            if (node != null && node.getCost() > 0) {
                paidNodes++;
            }
        }
        
        // Получаем все взятые узлы для пересчета статов
        java.util.Set<String> allocatedNodes = new java.util.HashSet<>(data.getAllocatedNodes());
        
        // Сбрасываем все узлы (теперь не добавляет очки автоматически)
        data.resetAll();
        
        // ИСПРАВЛЕНО: Вручную возвращаем только платные очки
        data.addPoints(paidNodes);
        
        // ИСПРАВЛЕНО: Добавляем обратно стартовый узел класса
        try {
            ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
            ru.eclipsia.core.data.PlayerData coreData = api.getPlayerData(player);
            
            if (coreData != null && coreData.getClassName() != null) {
                String startNodeId = treeManager.getStartNodeForClass(coreData.getClassName());
                if (startNodeId != null) {
                    // Автоматически разблокируем стартовый узел (бесплатно)
                    data.allocateNode(startNodeId, 0);
                    player.sendMessage("§7Стартовый узел класса восстановлен");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        player.sendMessage("§a✓ Сброшено узлов: " + totalNodes);
        player.sendMessage("§7Очков возвращено: " + paidNodes);
        
        // Пересчитываем статы игрока
        recalculatePlayerStats(player, allocatedNodes);
        
        // Сохраняем данные перков
        playerManager.savePlayerData(player.getUniqueId());
        
        // Обновляем GUI
        gui.refresh(player);
    }
    
    /**
     * Пересчитать статы игрока после сброса
     */
    private void recalculatePlayerStats(Player player, java.util.Set<String> removedNodes) {
        try {
            ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
            ru.eclipsia.core.data.PlayerData data = api.getPlayerData(player);
            
            if (data != null) {
                // Получаем базовые статы класса
                ru.eclipsia.core.classes.PlayerClass playerClass = ru.eclipsia.core.classes.ClassManager.getInstance().getClass(data.getClassName());
                
                if (playerClass != null) {
                    // Рассчитываем базовые статы для текущего уровня
                    java.util.Map<String, Integer> newStats = new java.util.HashMap<>();
                    
                    for (String statName : new String[]{"strength", "dexterity", "intelligence"}) {
                        int baseValue = playerClass.calculateStat(statName, data.getLevel());
                        newStats.put(statName, baseValue);
                    }
                    
                    // Вычитаем статы от сброшенных узлов
                    for (String nodeId : removedNodes) {
                        PerkNode node = treeManager.getNode(nodeId);
                        if (node != null) {
                            for (java.util.Map.Entry<String, Integer> entry : node.getStats().entrySet()) {
                                String statName = entry.getKey();
                                int bonus = entry.getValue();
                                
                                if (newStats.containsKey(statName)) {
                                    int currentValue = newStats.get(statName);
                                    newStats.put(statName, currentValue - bonus);
                                }
                            }
                        }
                    }
                    
                    // Обновляем данные игрока
                    ru.eclipsia.core.data.PlayerData updatedData = data.toBuilder()
                        .stats(newStats)
                        .build();
                    
                    ru.eclipsia.core.data.DataManager.getInstance().savePlayer(updatedData);
                    
                    // Применяем бонусы статов
                    ru.eclipsia.core.stats.StatsBonusApplier.applyAllBonuses(player);
                    
                    // НОВОЕ: Применяем бонусы экипировки
                    reapplyEquipmentBonuses(player);
                    
                    player.sendMessage("§7Статы пересчитаны");
                }
            }
        } catch (Exception e) {
            player.sendMessage("§cОшибка пересчета статов!");
            e.printStackTrace();
        }
    }
    
    /**
     * Применить бонусы от узла
     */
    private void applyNodeBonuses(Player player, PerkNode node) {
        if (node.getStats().isEmpty()) return;
        
        // Интеграция с EclipsiaCore для применения бонусов к статам
        try {
            ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
            ru.eclipsia.core.data.PlayerData data = api.getPlayerData(player);
            
            if (data != null) {
                // Создаем новые статы с добавленными бонусами
                java.util.Map<String, Integer> newStats = new java.util.HashMap<>(data.getStats());
                
                for (java.util.Map.Entry<String, Integer> entry : node.getStats().entrySet()) {
                    String statName = entry.getKey();
                    int bonus = entry.getValue();
                    
                    // Добавляем бонус к текущему значению
                    int currentValue = newStats.getOrDefault(statName, 0);
                    newStats.put(statName, currentValue + bonus);
                }
                
                // Обновляем данные игрока
                ru.eclipsia.core.data.PlayerData updatedData = data.toBuilder()
                    .stats(newStats)
                    .build();
                
                ru.eclipsia.core.data.DataManager.getInstance().savePlayer(updatedData);
                
                // Применяем бонусы статов
                ru.eclipsia.core.stats.StatsBonusApplier.applyAllBonuses(player);
                
                // НОВОЕ: Применяем бонусы экипировки
                reapplyEquipmentBonuses(player);
                
                // Показываем что получил игрок
                player.sendMessage("§7Получено:");
                for (String line : node.getStatsDescription().split("\n")) {
                    player.sendMessage("  " + line);
                }
            }
        } catch (Exception e) {
            player.sendMessage("§cОшибка применения бонусов!");
            e.printStackTrace();
        }
    }
    
    /**
     * НОВОЕ: Переприменить бонусы экипировки
     */
    private void reapplyEquipmentBonuses(Player player) {
        try {
            // Проверяем доступен ли плагин EclipsiaItems
            org.bukkit.plugin.Plugin itemsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("EclipsiaItems");
            if (itemsPlugin != null && itemsPlugin.isEnabled()) {
                // Получаем EquipmentManager через рефлексию
                Class<?> itemsClass = Class.forName("ru.eclipsia.items.EclipsiaItems");
                Object itemsInstance = itemsClass.getMethod("getInstance").invoke(null);
                Object equipmentManager = itemsClass.getMethod("getEquipmentManager").invoke(itemsInstance);
                
                // Получаем экипировку игрока
                Class<?> managerClass = Class.forName("ru.eclipsia.items.equipment.EquipmentManager");
                Object equipment = managerClass.getMethod("getEquipment", org.bukkit.entity.Player.class)
                        .invoke(equipmentManager, player);
                
                // Применяем бонусы
                Class<?> applierClass = Class.forName("ru.eclipsia.items.equipment.EquipmentBonusApplier");
                applierClass.getMethod("applyBonuses", org.bukkit.entity.Player.class, 
                        Class.forName("ru.eclipsia.items.equipment.PlayerEquipment"))
                        .invoke(null, player, equipment);
            }
        } catch (Exception e) {
            // Игнорируем если плагин не найден
        }
    }
}
