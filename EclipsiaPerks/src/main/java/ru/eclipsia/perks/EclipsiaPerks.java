package ru.eclipsia.perks;

import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.perks.commands.PerksCommand;
import ru.eclipsia.perks.gui.PerkTreeGUI;
import ru.eclipsia.perks.listeners.PerkTreeGUIListener;
import ru.eclipsia.perks.listeners.PlayerPerkListener;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

/**
 * Главный класс плагина EclipsiaPerks
 */
public class EclipsiaPerks extends JavaPlugin {
    
    private static EclipsiaPerks instance;
    
    private PerkTreeManager treeManager;
    private PlayerPerkManager playerManager;
    private PerkTreeGUI gui;
    private ru.eclipsia.perks.web.PerkWebAPI webApi;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Проверка зависимости EclipsiaCore
        if (getServer().getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден! Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Сохранение конфигов по умолчанию
        saveDefaultConfig();
        // perks.yml: если на диске нет МЕТКИ актуальной версии — перезаписываем.
        // Так у тестера на сервере всегда стоит свежее дерево, а его кастомы
        // (если бы они были) могли бы попасть только при ручной правке.
        try {
            java.io.File f = new java.io.File(getDataFolder(), "perks.yml");
            boolean needWrite = !f.exists();
            if (!needWrite) {
                String content = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                // Версионная метка генератора в шапке. Если её нет — файл устарел.
                // v2: добавили settings.start-level=1, чтобы игрок 1 lvl сразу мог
                // взять соседа стартового узла. Старые конфиги (без orbital-v2) перетираем.
                if (!content.contains("version-marker: orbital-v2")) {
                    needWrite = true;
                    getLogger().info("perks.yml устарел — перезаписываю свежим деревом.");
                }
            }
            if (needWrite) {
                if (f.exists()) f.delete();
                saveResource("perks.yml", true);
            }
        } catch (Exception e) {
            getLogger().warning("Не удалось проверить perks.yml: " + e.getMessage());
            saveResource("perks.yml", false);
        }
        
        // Инициализация менеджеров
        getLogger().info("Инициализация менеджеров...");
        
        treeManager = new PerkTreeManager(this);
        treeManager.loadPerkTree();
        
        playerManager = new PlayerPerkManager(this);
        
        gui = new PerkTreeGUI(treeManager, playerManager);
        
        // Регистрация команд
        getCommand("perks").setExecutor(new PerksCommand(this));
        getCommand("perkscode").setExecutor(new ru.eclipsia.perks.commands.PerksCodeCommand(this));

        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new PerkTreeGUIListener(gui, treeManager, playerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerPerkListener(playerManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.perks.listeners.ClassStartNodeListener(playerManager, treeManager), this);
        
        // Запуск Web API (для внешнего web-фронтенда дерева перков)
        if (getConfig().getBoolean("web.enabled", true)) {
            int port = getConfig().getInt("web.port", 8080);
            try {
                webApi = new ru.eclipsia.perks.web.PerkWebAPI(this, treeManager, playerManager);
                webApi.start(port);
            } catch (Exception e) {
                getLogger().warning("Не удалось запустить PerkWebAPI на порту " + port
                        + ": " + e.getMessage());
                webApi = null;
            }
        }

        getLogger().info("EclipsiaPerks успешно загружен!");
        getLogger().info("Загружено узлов перков: " + treeManager.getNodeCount());
    }
    
    @Override
    public void onDisable() {
        if (webApi != null) {
            webApi.stop();
        }
        getLogger().info("EclipsiaPerks отключен.");
    }
    
    public static EclipsiaPerks getInstance() {
        return instance;
    }
    
    public PerkTreeManager getTreeManager() {
        return treeManager;
    }
    
    public PlayerPerkManager getPlayerManager() {
        return playerManager;
    }
    
    public PerkTreeGUI getGui() {
        return gui;
    }
    
    /**
     * Перезагрузка конфигурации
     */
    public void reloadConfiguration() {
        reloadConfig();
        treeManager.loadPerkTree();
        getLogger().info("Конфигурация перезагружена!");
    }

    // =========================================================================
    // PUBLIC CROSS-PLUGIN API
    // =========================================================================

    /**
     * Сумма значений одного стата по всем изученным узлам игрока.
     * Используется EclipsiaCore#StatResolver через рефлексию (без compile-time deps).
     */
    public int getPerkStat(java.util.UUID uuid, String statKey) {
        if (uuid == null || statKey == null) return 0;
        ru.eclipsia.perks.player.PlayerPerkData data = playerManager.getPlayerData(uuid);
        if (data == null) return 0;
        int sum = 0;
        for (String nodeId : data.getAllocatedNodes()) {
            ru.eclipsia.perks.node.PerkNode node = treeManager.getNode(nodeId);
            if (node == null) continue;
            Integer v = node.getStats().get(statKey);
            if (v != null) sum += v;
        }
        return sum;
    }

    /** Все статы со всех изученных узлов игрока (для StatResolver#totals). */
    public java.util.Map<String, Integer> getAllPerkStats(java.util.UUID uuid) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        if (uuid == null) return out;
        ru.eclipsia.perks.player.PlayerPerkData data = playerManager.getPlayerData(uuid);
        if (data == null) return out;
        for (String nodeId : data.getAllocatedNodes()) {
            ru.eclipsia.perks.node.PerkNode node = treeManager.getNode(nodeId);
            if (node == null) continue;
            for (java.util.Map.Entry<String, Integer> e : node.getStats().entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * 6-значный код, который игрок вводит на web-странице вместе со своим ником
     * для авторизации. Поднимается лениво при первом обращении.
     */
    public int getOrCreatePlayerCode(java.util.UUID uuid) {
        return ru.eclipsia.perks.web.PerkAuthCodes.getOrCreate(uuid);
    }
}
