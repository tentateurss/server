package ru.eclipsia.mobs.boss;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;
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

    /**
     * Полный сброс состояния Хранителя Врат.
     * <p>Используется командой /admin resetplayer: после неё в мире не должны
     * висеть бесхозные клоны босса и {@link GatekeeperBoss#isActive()} должен
     * вернуться в {@code false}, иначе {@link GatekeeperArena} либо
     * откажется спавнить нового, либо наоборот наплодит дубликатов.
     *
     * <p>Шаги:
     * <ol>
     *   <li>Принудительно сбросить {@code isActive} и убить активный
     *       {@link org.bukkit.entity.IronGolem}, если он есть.</li>
     *   <li>Пройтись по всем мирам и удалить любые сущности с метаданными
     *       {@code eclipsia_boss=gatekeeper} (это «оставшиеся» клоны
     *       после прошлых тестов / краша / ручного /kill).</li>
     *   <li>Удалить связанных миньонов с метаданными {@code eclipsia_minion}
     *       во всех мирах — они тоже могли остаться.</li>
     * </ol>
     */
    public void resetGatekeeper() {
        GatekeeperBoss boss = activeBosses.get("gatekeeper");
        if (boss != null) {
            try { boss.forceCleanup(); } catch (Throwable ignored) {}
        }

        int removedBosses = 0;
        int removedMinions = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof LivingEntity)) continue;
                if (hasMeta(e, "eclipsia_boss")) {
                    e.remove();
                    removedBosses++;
                } else if (hasMeta(e, "eclipsia_minion")) {
                    e.remove();
                    removedMinions++;
                }
            }
        }
        plugin.getLogger().info("BossManager.resetGatekeeper(): убрано боссов=" + removedBosses
                + ", миньонов=" + removedMinions);
    }

    private boolean hasMeta(Entity e, String key) {
        for (MetadataValue v : e.getMetadata(key)) {
            if (v.getOwningPlugin() == plugin) return true;
        }
        return false;
    }
}
