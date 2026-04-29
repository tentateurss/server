package ru.eclipsia.core.resourcepack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import ru.eclipsia.core.EclipsiaCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер ресурс-пака
 */
public class ResourcePackManager implements Listener {
    
    private final EclipsiaCore plugin;
    private final Map<UUID, Boolean> resourcePackStatus = new HashMap<>();
    
    private boolean enabled;
    private String url;
    private String sha1;
    private String promptMessage;
    private boolean required;
    private boolean checkOnJoin;
    
    public ResourcePackManager(EclipsiaCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * Загрузить конфигурацию
     */
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        url = plugin.getConfig().getString("resource-pack.url", "");
        sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        promptMessage = plugin.getConfig().getString("resource-pack.prompt-message", 
            "§6Для полного игрового опыта рекомендуется установить ресурс-пак!");
        required = plugin.getConfig().getBoolean("resource-pack.required", false);
        checkOnJoin = plugin.getConfig().getBoolean("resource-pack.check-on-join", true);
        
        if (enabled && url.isEmpty()) {
            plugin.getLogger().warning("Ресурс-пак включен, но URL не указан!");
            enabled = false;
        }
    }
    
    /**
     * Обработка входа игрока
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || !checkOnJoin) return;
        
        Player player = event.getPlayer();
        
        // Проверяем статус ресурс-пака игрока
        Boolean hasResourcePack = resourcePackStatus.get(player.getUniqueId());
        
        // Если игрок новый или статус неизвестен, предлагаем установить
        if (hasResourcePack == null || !hasResourcePack) {
            offerResourcePack(player);
        }
    }
    
    /**
     * Обработка статуса ресурс-пака
     */
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                resourcePackStatus.put(uuid, true);
                player.sendMessage("§a✓ Ресурс-пак успешно загружен!");
                plugin.getLogger().info("Игрок " + player.getName() + " загрузил ресурс-пак");
                break;
                
            case DECLINED:
                resourcePackStatus.put(uuid, false);
                if (required) {
                    player.kickPlayer("§cДля игры на сервере требуется установка ресурс-пака!");
                    plugin.getLogger().info("Игрок " + player.getName() + " отклонил обязательный ресурс-пак");
                } else {
                    player.sendMessage("§eВы отклонили установку ресурс-пака.");
                    player.sendMessage("§7Некоторые элементы могут отображаться некорректно.");
                    plugin.getLogger().info("Игрок " + player.getName() + " отклонил ресурс-пак");
                }
                break;
                
            case FAILED_DOWNLOAD:
                resourcePackStatus.put(uuid, false);
                player.sendMessage("§cОшибка загрузки ресурс-пака!");
                player.sendMessage("§7Проверьте подключение к интернету и попробуйте перезайти.");
                plugin.getLogger().warning("Игрок " + player.getName() + " не смог загрузить ресурс-пак");
                break;
                
            case ACCEPTED:
                player.sendMessage("§eЗагрузка ресурс-пака...");
                plugin.getLogger().info("Игрок " + player.getName() + " принял ресурс-пак");
                break;
                
            case DOWNLOADED:
                player.sendMessage("§aРесурс-пак загружен, применение...");
                break;
                
            case INVALID_URL:
                resourcePackStatus.put(uuid, false);
                player.sendMessage("§cОшибка: неверный URL ресурс-пака!");
                plugin.getLogger().severe("Неверный URL ресурс-пака для игрока " + player.getName());
                break;
                
            case FAILED_RELOAD:
                resourcePackStatus.put(uuid, false);
                player.sendMessage("§cОшибка перезагрузки ресурс-пака!");
                plugin.getLogger().warning("Игрок " + player.getName() + " не смог перезагрузить ресурс-пак");
                break;
                
            case DISCARDED:
                resourcePackStatus.put(uuid, false);
                plugin.getLogger().info("Игрок " + player.getName() + " отменил загрузку ресурс-пака");
                break;
        }
    }
    
    /**
     * Предложить игроку установить ресурс-пак
     */
    public void offerResourcePack(Player player) {
        if (!enabled) {
            player.sendMessage("§cРесурс-пак отключен в конфигурации.");
            return;
        }
        
        if (url.isEmpty()) {
            player.sendMessage("§cURL ресурс-пака не настроен!");
            return;
        }
        
        // Отправляем сообщение игроку
        player.sendMessage("§8§m                                        ");
        player.sendMessage(promptMessage);
        
        if (required) {
            player.sendMessage("§c§lВНИМАНИЕ: §cРесурс-пак обязателен для игры!");
        } else {
            player.sendMessage("§7Вы можете отклонить установку, но некоторые");
            player.sendMessage("§7элементы могут отображаться некорректно.");
        }
        
        player.sendMessage("§8§m                                        ");
        
        // Отправляем ресурс-пак
        // Сигнатура: setResourcePack(String url, String hash, boolean required, Component prompt)
        if (sha1 != null && !sha1.isEmpty()) {
            // С SHA-1 хешем
            player.setResourcePack(url, sha1, required, net.kyori.adventure.text.Component.text(promptMessage));
        } else {
            // Без SHA-1 хеша (пустая строка вместо null)
            player.setResourcePack(url, "", required, net.kyori.adventure.text.Component.text(promptMessage));
        }
        
        plugin.getLogger().info("Предложен ресурс-пак игроку " + player.getName());
    }
    
    /**
     * Проверить, установлен ли ресурс-пак у игрока
     */
    public boolean hasResourcePack(Player player) {
        return resourcePackStatus.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Принудительно отправить ресурс-пак игроку
     */
    public void forceResourcePack(Player player) {
        if (!enabled) {
            player.sendMessage("§cРесурс-пак отключен в конфигурации.");
            return;
        }
        
        offerResourcePack(player);
    }
    
    /**
     * Очистить статус ресурс-пака игрока
     */
    public void clearStatus(UUID uuid) {
        resourcePackStatus.remove(uuid);
    }
    
    /**
     * Получить количество игроков с установленным ресурс-паком
     */
    public int getPlayersWithResourcePack() {
        return (int) resourcePackStatus.values().stream().filter(status -> status).count();
    }
    
    /**
     * Проверить, включен ли ресурс-пак
     */
    public boolean isEnabled() {
        return enabled;
    }
}
