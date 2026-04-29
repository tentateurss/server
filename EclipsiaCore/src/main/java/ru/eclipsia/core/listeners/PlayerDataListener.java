package ru.eclipsia.core.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;

/**
 * Обработчик входа/выхода игроков для загрузки/сохранения данных
 */
public class PlayerDataListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DataManager.getInstance().loadPlayer(event.getPlayer().getUniqueId())
                .thenAccept(data -> {
                    if (data.getClassName() == null) {
                        event.getPlayer().sendMessage("§e§l⚠ §eДобро пожаловать на Eclipsia!");
                        event.getPlayer().sendMessage("§7Выберите класс персонажа: §f/class");
                    } else {
                        event.getPlayer().sendMessage("§aДанные загружены успешно!");
                    }
                })
                .exceptionally(ex -> {
                    event.getPlayer().sendMessage("§cОшибка загрузки данных. Обратитесь к администратору.");
                    ex.printStackTrace();
                    return null;
                });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DataManager.getInstance().unloadPlayer(event.getPlayer().getUniqueId())
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }
}
