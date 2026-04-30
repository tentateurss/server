package ru.eclipsia.mobs.listeners;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.mobs.EclipsiaMobs;
import ru.eclipsia.mobs.spawn.SpawnManager;

import java.util.Set;

/**
 * Гасим ванильный спавн враждебных мобов в наших стартовых мирах.
 * Кастомные мобы спавнятся через {@link SpawnManager}, а тот использует
 * {@link SpawnReason#CUSTOM} (Bukkit ставит CUSTOM по умолчанию из
 * {@code World.spawn(...)}). Поэтому в «наших» мирах режем всё, что НЕ CUSTOM.
 *
 * <p>Список «наших» миров берём из {@code mobs.yml → spawn-zones[*].world}.
 * Для прочих миров (vanilla overworld нерфить не хочется) логика остаётся
 * прежней — режем только NATURAL hostile.
 */
public class MobSpawnListener implements Listener {

    private static final Set<EntityType> VANILLA_HOSTILE = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.ENDERMAN, EntityType.WITCH,
            EntityType.SLIME, EntityType.PHANTOM, EntityType.DROWNED,
            EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER,
            EntityType.SILVERFISH, EntityType.PILLAGER, EntityType.VINDICATOR,
            EntityType.RAVAGER, EntityType.EVOKER, EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.MAGMA_CUBE,
            EntityType.GUARDIAN);

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        SpawnReason reason = event.getSpawnReason();
        // Наши собственные spawn'ы всегда CUSTOM/COMMAND и помечены PDC ZONE_ID_KEY
        // ещё до InitialSpawnEvent — не трогаем.
        if (reason == SpawnReason.CUSTOM || reason == SpawnReason.COMMAND) return;

        String worldName = event.getLocation().getWorld() == null
                ? "" : event.getLocation().getWorld().getName();
        boolean ourWorld = isManagedWorld(worldName);

        // В «наших» мирах режем ВСЕ нативные пути спавна враждебных мобов:
        // NATURAL, SPAWNER, REINFORCEMENTS, VILLAGE_INVASION, CHUNK_GEN, RAID,
        // PATROL, NETHER_PORTAL и т.п. Player-summon (BUILD_*, JOCKEY) пропускаем.
        if (ourWorld) {
            if (!VANILLA_HOSTILE.contains(event.getEntityType())) return;
            // PDC-метка наших мобов: если стоит — это уже наш спавн (на всякий случай)
            if (hasOurTag(event.getEntity())) return;
            event.setCancelled(true);
            return;
        }

        // Для обычных миров — старое поведение: режем NATURAL hostile.
        if (reason != SpawnReason.NATURAL) return;
        if (!VANILLA_HOSTILE.contains(event.getEntityType())) return;
        event.setCancelled(true);
    }

    private boolean hasOurTag(LivingEntity le) {
        try {
            Plugin plugin = EclipsiaMobs.getInstance();
            if (plugin == null) return false;
            NamespacedKey key = new NamespacedKey(plugin, SpawnManager.ZONE_ID_KEY);
            return le.getPersistentDataContainer().has(key, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isManagedWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) return false;
        try {
            return SpawnManager.getInstance().isManagedWorld(worldName);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
