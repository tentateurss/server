package ru.eclipsia.lobby;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.lobby.command.CharacterCommand;
import ru.eclipsia.lobby.listener.LobbyListener;

/**
 * Главный класс плагина EclipsiaLobby
 * Управляет системой выбора и создания персонажей
 */
public class EclipsiaLobby extends JavaPlugin {
    
    private static EclipsiaLobby instance;
    private EclipsiaAPI api;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Проверяем зависимость от EclipsiaCore
        if (Bukkit.getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден! Отключение плагина...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Получаем API
        api = EclipsiaAPI.getInstance();
        
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        
        // Регистрируем команды
        CharacterCommand characterCommand = new CharacterCommand(this);
        getCommand("character").setExecutor(characterCommand);
        getCommand("character").setTabCompleter(characterCommand);
        
        // Регистрируем слушатели
        Bukkit.getPluginManager().registerEvents(new LobbyListener(this), this);
        
        getLogger().info("EclipsiaLobby успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("EclipsiaLobby выключен");
    }
    
    public static EclipsiaLobby getInstance() {
        return instance;
    }
    
    public EclipsiaAPI getAPI() {
        return api;
    }
}
