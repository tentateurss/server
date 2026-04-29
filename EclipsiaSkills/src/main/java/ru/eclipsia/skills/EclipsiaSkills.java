package ru.eclipsia.skills;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.skills.command.SkillsCommand;
import ru.eclipsia.skills.manager.SkillManager;
import ru.eclipsia.skills.listener.SkillListener;
import ru.eclipsia.skills.listener.CustomRegenerationListener;
import ru.eclipsia.skills.listener.HUDActionBarListener;
import ru.eclipsia.skills.listener.EclipseBookListener;

/**
 * Главный класс плагина EclipsiaSkills
 * Управляет системой эклипс-навыков
 */
public class EclipsiaSkills extends JavaPlugin {
    
    private static EclipsiaSkills instance;
    private EclipsiaAPI api;
    private SkillManager skillManager;
    private CustomRegenerationListener regenListener;
    private HUDActionBarListener hudListener;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Проверяем зависимости
        if (Bukkit.getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден! Отключение плагина...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        if (Bukkit.getPluginManager().getPlugin("EclipsiaItems") == null) {
            getLogger().severe("EclipsiaItems не найден! Отключение плагина...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Получаем API
        api = EclipsiaAPI.getInstance();
        
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        
        // Инициализируем менеджер навыков
        skillManager = new SkillManager(this);
        
        // Регистрируем команды
        getCommand("skills").setExecutor(new SkillsCommand(this));
        getCommand("giveskill").setExecutor(new ru.eclipsia.skills.command.GiveSkillCommand(this));
        
        // Регистрируем слушатели
        Bukkit.getPluginManager().registerEvents(new SkillListener(this), this);

        // Кастомная регенерация (HP/мана/Эгида общим тиком — заменяет
        // отдельный ManaRegenerationListener).
        regenListener = new CustomRegenerationListener(this);
        Bukkit.getPluginManager().registerEvents(regenListener, this);
        regenListener.start();

        // Единый ActionBar с HP/Эгидой/маной/статами — заменяет BossBar маны.
        hudListener = new HUDActionBarListener(this);
        Bukkit.getPluginManager().registerEvents(hudListener, this);
        hudListener.start();

        // Эклипс-книги (выбор стартового навыка / поддержки после босса).
        Bukkit.getPluginManager().registerEvents(new EclipseBookListener(this), this);

        getLogger().info("EclipsiaSkills успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        if (regenListener != null) regenListener.stop();
        if (hudListener != null) hudListener.stop();
        getLogger().info("EclipsiaSkills выключен");
    }
    
    public static EclipsiaSkills getInstance() {
        return instance;
    }
    
    public EclipsiaAPI getAPI() {
        return api;
    }
    
    public SkillManager getSkillManager() {
        return skillManager;
    }
}
