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
        saveResource("perks.yml", false);
        
        // Инициализация менеджеров
        getLogger().info("Инициализация менеджеров...");
        
        treeManager = new PerkTreeManager(this);
        treeManager.loadPerkTree();
        
        playerManager = new PlayerPerkManager(this);
        
        gui = new PerkTreeGUI(treeManager, playerManager);
        
        // Регистрация команд
        getCommand("perks").setExecutor(new PerksCommand(gui, treeManager));
        
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
}
