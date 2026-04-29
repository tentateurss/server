package ru.eclipsia.skills;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.skills.command.SkillsCommand;
import ru.eclipsia.skills.manager.SkillManager;
import ru.eclipsia.skills.listener.SkillListener;
import ru.eclipsia.skills.listener.ManaRegenerationListener;
import ru.eclipsia.skills.listener.ManaBarListener;
import ru.eclipsia.skills.listener.EclipseBookListener;

/**
 * Главный класс плагина EclipsiaSkills
 * Управляет системой эклипс-навыков
 */
public class EclipsiaSkills extends JavaPlugin {
    
    private static EclipsiaSkills instance;
    private EclipsiaAPI api;
    private SkillManager skillManager;
    private ManaBarListener manaBarListener;
    
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
        Bukkit.getPluginManager().registerEvents(new ManaRegenerationListener(this), this);
        
        manaBarListener = new ManaBarListener(this);
        Bukkit.getPluginManager().registerEvents(manaBarListener, this);

        // Эклипс-книги (выбор стартового навыка / поддержки после босса).
        Bukkit.getPluginManager().registerEvents(new EclipseBookListener(this), this);

        getLogger().info("EclipsiaSkills успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        if (manaBarListener != null) {
            manaBarListener.shutdown();
        }
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
