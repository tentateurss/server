package ru.eclipsia.mobs.boss;

import ru.eclipsia.mobs.EclipsiaMobs;

import java.util.HashMap;
import java.util.Map;

/**
 * Менеджер боссов
 */
public class BossManager {
    
    private static BossManager instance;
    private final EclipsiaMobs plugin;
    private final Map<String, GatekeeperBoss> activeBosses;
    
    private BossManager(EclipsiaMobs plugin) {
        this.plugin = plugin;
        this.activeBosses = new HashMap<>();
    }
    
    public static void initialize(EclipsiaMobs plugin) {
        if (instance == null) {
            instance = new BossManager(plugin);
        }
    }
    
    public static BossManager getInstance() {
        return instance;
    }
    
    /**
     * Получить или создать Хранителя Врат
     */
    public GatekeeperBoss getGatekeeper() {
        return activeBosses.computeIfAbsent("gatekeeper", k -> new GatekeeperBoss(plugin));
    }
    
    /**
     * Проверить активен ли Хранитель Врат
     */
    public boolean isGatekeeperActive() {
        GatekeeperBoss boss = activeBosses.get("gatekeeper");
        return boss != null && boss.isActive();
    }
}
