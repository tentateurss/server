package ru.eclipsia.perks.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.perks.node.NodeType;
import ru.eclipsia.perks.node.PerkNode;
import ru.eclipsia.perks.player.PlayerPerkData;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

import java.util.*;

/**
 * GUI дерева перков
 */
public class PerkTreeGUI {
    
    private static final String TITLE = "§6Дерево перков";
    private static final int SIZE = 54; // 6 рядов
    
    // Размер видимой области в "ячейках" (5 рядов под узлы; нижний ряд — UI).
    // Дерево перков логически до 2000×2000 — viewport позволяет ходить камерой.
    private static final int VIEW_WIDTH = 9;
    private static final int VIEW_HEIGHT = 5; // последний ряд (5) — для UI

    /** Шаг прокрутки камеры на одно нажатие стрелки. */
    public static final int CAMERA_STEP = 3;

    // Слоты UI-панели (нижний ряд):
    public static final int SLOT_CAMERA_RECENTER = 45;
    public static final int SLOT_CAMERA_UP       = 46;
    public static final int SLOT_CAMERA_LEFT     = 48;
    public static final int SLOT_INFO_POINTS     = 49;
    public static final int SLOT_CAMERA_RIGHT    = 50;
    public static final int SLOT_CAMERA_DOWN     = 52;
    public static final int SLOT_RESET_ALL       = 53;
    
    private final PerkTreeManager treeManager;
    private final PlayerPerkManager playerManager;
    
    // Камера (смещение просмотра)
    private final Map<UUID, Integer> cameraX = new HashMap<>();
    private final Map<UUID, Integer> cameraY = new HashMap<>();
    
    public PerkTreeGUI(PerkTreeManager treeManager, PlayerPerkManager playerManager) {
        this.treeManager = treeManager;
        this.playerManager = playerManager;
    }
    
    /**
     * Открыть GUI для игрока
     */
    public void open(Player player, String startNode) {
        PlayerPerkData data = playerManager.getPlayerData(player);
        
        // Центрируем камеру на стартовом узле
        PerkNode start = treeManager.getNode(startNode);
        if (start != null) {
            cameraX.put(player.getUniqueId(), start.getX() - VIEW_WIDTH / 2);
            cameraY.put(player.getUniqueId(), start.getY() - VIEW_HEIGHT / 2);
        } else {
            cameraX.put(player.getUniqueId(), 0);
            cameraY.put(player.getUniqueId(), 0);
        }
        
        showTree(player);
    }
    
    /**
     * Показать дерево
     */
    private void showTree(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        PlayerPerkData data = playerManager.getPlayerData(player);
        
        int camX = cameraX.getOrDefault(player.getUniqueId(), 0);
        int camY = cameraY.getOrDefault(player.getUniqueId(), 0);
        
        // Отображаем узлы в видимой области
        for (PerkNode node : treeManager.getAllNodes()) {
            int screenX = node.getX() - camX;
            int screenY = node.getY() - camY;
            
            // Проверяем что узел в видимой области
            if (screenX >= 0 && screenX < VIEW_WIDTH && screenY >= 0 && screenY < VIEW_HEIGHT) {
                int slot = screenY * VIEW_WIDTH + screenX;
                if (slot >= 0 && slot < SIZE) {
                    inv.setItem(slot, createNodeItem(node, data));
                }
            }
        }
        
        // Добавляем информационную панель внизу (последний ряд)
        addInfoPanel(inv, player, data);
        
        player.openInventory(inv);
    }
    
    /**
     * Создать предмет для узла
     */
    private ItemStack createNodeItem(PerkNode node, PlayerPerkData data) {
        boolean allocated = data.hasNode(node.getId());
        boolean canAllocate = treeManager.canAllocateNode(node.getId(), data.getAllocatedNodes());
        
        Material material;
        String nameColor;
        
        if (allocated) {
            // Взятый узел - зеленый
            material = getMaterialForType(node.getType(), true);
            nameColor = "§a";
        } else if (canAllocate) {
            // Доступный узел - желтый
            material = getMaterialForType(node.getType(), false);
            nameColor = "§e";
        } else {
            // Недоступный узел - серый
            material = Material.GRAY_STAINED_GLASS_PANE;
            nameColor = "§7";
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(nameColor + node.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§8" + node.getType().getDisplayName());
            lore.add("");
            
            // Бонусы
            if (!node.getStats().isEmpty()) {
                lore.add("§6Бонусы:");
                for (String line : node.getStatsDescription().split("\n")) {
                    lore.add(line);
                }
                lore.add("");
            }
            
            // Статус
            if (allocated) {
                lore.add("§a✓ Взято");
            } else if (canAllocate) {
                lore.add("§eСтоимость: §f" + node.getCost() + " очко");
                lore.add("§7ЛКМ - взять узел");
            } else {
                lore.add("§cНедоступно");
                lore.add("§7Сначала возьмите соседний узел");
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Получить материал для типа узла
     */
    private Material getMaterialForType(NodeType type, boolean allocated) {
        if (allocated) {
            return switch (type) {
                case START -> Material.LIME_STAINED_GLASS;
                case SMALL -> Material.LIME_STAINED_GLASS_PANE;
                case MEDIUM -> Material.LIME_CONCRETE;
                case NOTABLE -> Material.LIME_TERRACOTTA;
                case KEYSTONE -> Material.DIAMOND; // ИСПРАВЛЕНО: было LIME_GLAZED_TERRACOTTA
            };
        } else {
            return switch (type) {
                case START -> Material.WHITE_STAINED_GLASS;
                case SMALL -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                case MEDIUM -> Material.BLUE_CONCRETE;
                case NOTABLE -> Material.ORANGE_TERRACOTTA;
                case KEYSTONE -> Material.DIAMOND; // ИСПРАВЛЕНО: было RED_GLAZED_TERRACOTTA
            };
        }
    }
    
    /**
     * Добавить информационную панель и кнопки навигации камерой.
     */
    private void addInfoPanel(Inventory inv, Player player, PlayerPerkData data) {
        int camX = cameraX.getOrDefault(player.getUniqueId(), 0);
        int camY = cameraY.getOrDefault(player.getUniqueId(), 0);

        // Кнопка центрирования камеры на стартовом узле класса.
        inv.setItem(SLOT_CAMERA_RECENTER, makeIcon(Material.ENDER_EYE,
                "§b⌂ К стартовому узлу",
                "§7ЛКМ — вернуться к стартовой",
                "§7точке вашего класса"));

        // Стрелки навигации
        inv.setItem(SLOT_CAMERA_UP, makeIcon(Material.PAPER,
                "§e↑ Вверх",
                "§7Переместить камеру вверх",
                "§8x=" + camX + ", y=" + camY));
        inv.setItem(SLOT_CAMERA_LEFT, makeIcon(Material.PAPER,
                "§e← Влево",
                "§7Переместить камеру влево",
                "§8x=" + camX + ", y=" + camY));
        inv.setItem(SLOT_CAMERA_RIGHT, makeIcon(Material.PAPER,
                "§e→ Вправо",
                "§7Переместить камеру вправо",
                "§8x=" + camX + ", y=" + camY));
        inv.setItem(SLOT_CAMERA_DOWN, makeIcon(Material.PAPER,
                "§e↓ Вниз",
                "§7Переместить камеру вниз",
                "§8x=" + camX + ", y=" + camY));

        // Доступные очки
        ItemStack points = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta pointsMeta = points.getItemMeta();
        if (pointsMeta != null) {
            pointsMeta.setDisplayName("§6Доступно очков: §e" + data.getAvailablePoints());
            pointsMeta.setLore(Arrays.asList(
                "§7Взято узлов: §f" + data.getAllocatedCount(),
                "§7Получайте очки за уровень"
            ));
            points.setItemMeta(pointsMeta);
        }
        inv.setItem(SLOT_INFO_POINTS, points);

        // Кнопка сброса
        ItemStack reset = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = reset.getItemMeta();
        if (resetMeta != null) {
            resetMeta.setDisplayName("§cСбросить все перки");
            resetMeta.setLore(Arrays.asList(
                "§7ПКМ — сбросить все узлы",
                "§7Вернёт все потраченные очки"
            ));
            reset.setItemMeta(resetMeta);
        }
        inv.setItem(SLOT_RESET_ALL, reset);
    }

    /**
     * Помощник для создания иконки UI-кнопки.
     */
    private ItemStack makeIcon(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(loreLines));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Сдвинуть камеру и перерисовать GUI.
     * dx/dy — смещение в логических координатах дерева.
     */
    public void moveCamera(Player player, int dx, int dy) {
        UUID id = player.getUniqueId();
        cameraX.put(id, cameraX.getOrDefault(id, 0) + dx);
        cameraY.put(id, cameraY.getOrDefault(id, 0) + dy);
        showTree(player);
    }

    /**
     * Вернуть камеру к стартовому узлу текущего класса игрока.
     */
    public void recenterCamera(Player player) {
        try {
            ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
            ru.eclipsia.core.data.PlayerData coreData = api.getPlayerData(player);
            String className = coreData != null ? coreData.getClassName() : null;
            String startNodeId = className != null ? treeManager.getStartNodeForClass(className) : null;
            PerkNode start = startNodeId != null ? treeManager.getNode(startNodeId) : null;
            if (start != null) {
                cameraX.put(player.getUniqueId(), start.getX() - VIEW_WIDTH / 2);
                cameraY.put(player.getUniqueId(), start.getY() - VIEW_HEIGHT / 2);
            } else {
                cameraX.put(player.getUniqueId(), 0);
                cameraY.put(player.getUniqueId(), 0);
            }
        } catch (Exception e) {
            cameraX.put(player.getUniqueId(), 0);
            cameraY.put(player.getUniqueId(), 0);
        }
        showTree(player);
    }
    
    /**
     * Получить узел по слоту в GUI
     */
    public PerkNode getNodeAtSlot(Player player, int slot) {
        if (slot >= 45) return null; // Информационная панель
        
        int camX = cameraX.getOrDefault(player.getUniqueId(), 0);
        int camY = cameraY.getOrDefault(player.getUniqueId(), 0);
        
        int screenX = slot % VIEW_WIDTH;
        int screenY = slot / VIEW_WIDTH;
        
        int worldX = screenX + camX;
        int worldY = screenY + camY;
        
        // Ищем узел с этими координатами
        for (PerkNode node : treeManager.getAllNodes()) {
            if (node.getX() == worldX && node.getY() == worldY) {
                return node;
            }
        }
        
        return null;
    }
    
    /**
     * Обновить GUI
     */
    public void refresh(Player player) {
        showTree(player);
    }
}
