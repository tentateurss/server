package ru.eclipsia.mobs;

import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.mobs.boss.GatekeeperArena;
import ru.eclipsia.mobs.mob.MobManager;
import ru.eclipsia.mobs.experience.ExperienceManager;
import ru.eclipsia.mobs.spawn.SpawnManager;
import ru.eclipsia.mobs.commands.MobCommand;
import ru.eclipsia.mobs.commands.ExpCommand;
import ru.eclipsia.mobs.listeners.MobDeathListener;
import ru.eclipsia.mobs.listeners.MobSpawnListener;

/**
 * Главный класс плагина EclipsiaMobs
 */
public class EclipsiaMobs extends JavaPlugin {
    
    private static EclipsiaMobs instance;
    private EclipsiaAPI coreAPI;
    private GatekeeperArena gatekeeperArena;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("=================================");
        getLogger().info("  EclipsiaMobs v" + getDescription().getVersion());
        getLogger().info("  Загрузка плагина...");
        getLogger().info("=================================");
        
        // Проверяем наличие EclipsiaCore
        if (!checkDependencies()) {
            getLogger().severe("✗ EclipsiaCore не найден! Отключение плагина...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Получаем API EclipsiaCore
        coreAPI = EclipsiaAPI.getInstance();
        getLogger().info("✓ Подключение к EclipsiaCore установлено");
        
        // Сохраняем дефолтные конфиги
        saveDefaultConfig();
        saveResource("mobs.yml", false);
        
        // Инициализируем менеджеры
        try {
            MobManager.initialize(this);
            ExperienceManager.initialize(this);
            SpawnManager.initialize(this);
            ru.eclipsia.mobs.orbs.OrbManager.initialize(this);
            ru.eclipsia.mobs.boss.BossManager.initialize(this);
            ru.eclipsia.mobs.spawn.StructureSpawnManager.initialize(this);
            
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
        getLogger().info("  EclipsiaMobs успешно загружен!");
        getLogger().info("  Загружено мобов: " + MobManager.getInstance().getMobCount());
        getLogger().info("  Загружено зон: " + SpawnManager.getInstance().getZoneCount());
        getLogger().info("=================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Выключение EclipsiaMobs...");
        
        // Останавливаем спавн мобов
        if (SpawnManager.getInstance() != null) {
            SpawnManager.getInstance().shutdown();
        }
        
        getLogger().info("EclipsiaMobs выключен");
    }
    
    private boolean checkDependencies() {
        return getServer().getPluginManager().getPlugin("EclipsiaCore") != null;
    }
    
    private void registerCommands() {
        getCommand("mob").setExecutor(new MobCommand());
        getCommand("exp").setExecutor(new ExpCommand());
        getCommand("boss").setExecutor(new ru.eclipsia.mobs.commands.BossCommand());
        
        getLogger().info("✓ Команды зарегистрированы");
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MobDeathListener(), this);
        getServer().getPluginManager().registerEvents(new MobSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.mobs.listeners.MobHealthBarListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.mobs.listeners.BossDeathListener(), this);

        // Арена Хранителя Врат: авто-спавн при подходе + постобработка
        // (небо, портал) при смерти босса.
        gatekeeperArena = new GatekeeperArena(this);
        getServer().getPluginManager().registerEvents(gatekeeperArena, this);
        gatekeeperArena.onEnable();

        getLogger().info("✓ Слушатели зарегистрированы");
    }
    
    public static EclipsiaMobs getInstance() {
        return instance;
    }
    
    public EclipsiaAPI getCoreAPI() {
        return coreAPI;
    }

    public GatekeeperArena getGatekeeperArena() {
        return gatekeeperArena;
    }
}
