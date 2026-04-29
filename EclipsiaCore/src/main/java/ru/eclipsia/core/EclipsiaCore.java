package ru.eclipsia.core;

import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.commands.AdminCommand;
import ru.eclipsia.core.commands.ClassCommand;
import ru.eclipsia.core.commands.DataCommand;
import ru.eclipsia.core.commands.ProfileCommand;
import ru.eclipsia.core.commands.ResourcePackCommand;
import ru.eclipsia.core.commands.StatsCommand;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.listeners.ClassSelectionListener;
import ru.eclipsia.core.listeners.NoHungerListener;
import ru.eclipsia.core.listeners.PlayerDataListener;
import ru.eclipsia.core.listeners.ProfileGUIListener;
import ru.eclipsia.core.listeners.StatsGUIListener;
import ru.eclipsia.core.permissions.PermissionManager;
import ru.eclipsia.core.resourcepack.ResourcePackManager;

/**
 * Главный класс плагина EclipsiaCore
 */
public class EclipsiaCore extends JavaPlugin {
    
    private ResourcePackManager resourcePackManager;
    
    @Override
    public void onEnable() {
        getLogger().info("=================================");
        getLogger().info("  EclipsiaCore v" + getDescription().getVersion());
        getLogger().info("  Загрузка плагина...");
        getLogger().info("=================================");
        
        // Сохраняем дефолтные конфиги
        saveDefaultConfig();
        
        // Инициализируем менеджеры
        try {
            PermissionManager.initialize(this);
            DataManager.initialize(this);
            ClassManager.initialize(this);
            
            // Инициализируем менеджер ресурс-пака
            resourcePackManager = new ResourcePackManager(this);
            
            getLogger().info("✓ Менеджеры инициализированы");
        } catch (Exception e) {
            getLogger().severe("✗ Ошибка инициализации менеджеров!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Регистрируем команды
        registerCommands();
        
        // Регистрируем слушатели
        registerListeners();
        
        getLogger().info("=================================");
        getLogger().info("  EclipsiaCore успешно загружен!");
        getLogger().info("  Хранилище: " + DataManager.getInstance().getStorage().getStorageType());
        getLogger().info("=================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Выключение EclipsiaCore...");
        
        // Сохраняем все данные и закрываем соединения
        if (DataManager.getInstance() != null) {
            DataManager.getInstance().onDisable();
        }
        
        getLogger().info("EclipsiaCore выключен");
    }
    
    private void registerCommands() {
        var classCmd = getCommand("class");
        var statsCmd = getCommand("stats");
        var profileCmd = getCommand("profile");
        var resourcepackCmd = getCommand("resourcepack");
        var dataCmd = getCommand("data");
        var adminCmd = getCommand("admin");
        
        if (classCmd != null) {
            classCmd.setExecutor(new ClassCommand());
            getLogger().info("✓ Команда /class зарегистрирована");
        }
        if (statsCmd != null) {
            statsCmd.setExecutor(new StatsCommand());
            getLogger().info("✓ Команда /stats зарегистрирована");
        }
        if (profileCmd != null) {
            profileCmd.setExecutor(new ProfileCommand());
            getLogger().info("✓ Команда /profile зарегистрирована");
        }
        if (resourcepackCmd != null) {
            resourcepackCmd.setExecutor(new ResourcePackCommand(resourcePackManager));
            getLogger().info("✓ Команда /resourcepack зарегистрирована");
        }
        if (dataCmd != null) {
            dataCmd.setExecutor(new DataCommand());
            getLogger().info("✓ Команда /data зарегистрирована");
        }
        if (adminCmd != null) {
            AdminCommand adminExecutor = new AdminCommand();
            adminCmd.setExecutor(adminExecutor);
            getLogger().info("✓ Команда /admin зарегистрирована с executor: " + adminExecutor.getClass().getName());
        } else {
            getLogger().warning("✗ Не удалось зарегистрировать команду /admin - getCommand вернул null");
        }
        
        getLogger().info("✓ Команды зарегистрированы");
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerDataListener(), this);
        getServer().getPluginManager().registerEvents(new ClassSelectionListener(), this);
        getServer().getPluginManager().registerEvents(new StatsGUIListener(), this);
        getServer().getPluginManager().registerEvents(new ProfileGUIListener(), this);
        getServer().getPluginManager().registerEvents(resourcePackManager, this);
        getServer().getPluginManager().registerEvents(new NoHungerListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.core.listeners.StatsApplyListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.core.listeners.StatsCombatListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.core.listeners.HideVanillaHUDListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.core.listeners.WeatherTimeListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.core.listener.RegionTitleListener(this), this);
        
        getLogger().info("✓ Слушатели зарегистрированы");
    }
}
