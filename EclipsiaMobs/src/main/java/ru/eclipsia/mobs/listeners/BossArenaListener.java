package ru.eclipsia.mobs.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;
import ru.eclipsia.mobs.EclipsiaMobs;
import ru.eclipsia.mobs.boss.GatekeeperArena;

/**
 * Защита арены Хранителя Врат от посторонних мобов.
 *
 * <p>Решает две связанные проблемы из лога 16:17 (тестовая сессия):
 * <ol>
 *   <li>Iron golem (босс) по ванильной AI «дружелюбен к игроку и атакует
 *       враждебных мобов» — он отвлекался на спавн-зомби/пауков, заходивших
 *       в арену. Здесь {@link EntityTargetEvent} отменяется, если босс
 *       пытается таргетиться на не-игрока.</li>
 *   <li>Мобы из соседних beach_forest_* зон забредали в круг арены до того,
 *       как SpawnManager.cleanupExclusions (раз в 2с) их подметал. Здесь
 *       мы (а) запрещаем CreatureSpawnEvent внутри арены полностью,
 *       (б) каждые 0.5с подметаем хостайл-мобов в радиусе арены —
 *       включая вандальные mob'ы без нашего PDC-тэга.</li>
 * </ol>
 *
 * <p>Радиус подметания = {@link #ARENA_RADIUS} (22) — чуть больше, чем
 * {@code beach_arena} exclusion (18), потому что центр exclusion в config
 * был выставлен на (0, 80), а реальный центр арены / спавна босса —
 * на (0, 95). Считаем от факта-центра арены ({@link GatekeeperArena}).
 */
public final class BossArenaListener implements Listener {

    /** Радиус «безопасной» зоны вокруг центра арены. */
    private static final double ARENA_RADIUS = 22.0;
    private static final double ARENA_RADIUS_SQ = ARENA_RADIUS * ARENA_RADIUS;

    private final EclipsiaMobs plugin;
    private BukkitTask sweepTask;

    public BossArenaListener(EclipsiaMobs plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (sweepTask != null) sweepTask.cancel();
        // 10 тиков = 0.5 секунды. Лёгкая задача (одна цилиндровая проверка
        // в одном мире), TPS не страдает.
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepArena, 20L, 10L);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    /**
     * Босс не должен таргетиться ни на кого, кроме игроков.
     * Без этого iron-golem каждый ~5с пере-выбирал ближайшего хостайл-моба
     * как цель и уходил от игрока к зомби на краю арены.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (!hasMeta(entity, "eclipsia_boss")) return;
        Entity target = event.getTarget();
        if (target instanceof Player) return;
        // Любой не-игрок (включая null = «сбросить цель») — отменяем смену цели.
        // null в getTarget() означает «забыть текущую цель» — это норм, не блочим.
        if (target == null) return;
        event.setCancelled(true);
    }

    /**
     * Запрет спавна (любого: природного или плагин-вызванного) внутри арены.
     * Кастом-мобы EclipsiaMobs.SpawnManager уже проверяют {@code isInExclusion},
     * но (а) ванильный спавн стороной идёт мимо них, (б) у нас exclusion-центр
     * был сдвинут от реального центра арены — здесь страхуемся.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent event) {
        Location loc = event.getLocation();
        if (!isInArena(loc)) return;
        // Самого босса/миньонов спавн НЕ блокируем.
        Entity e = event.getEntity();
        if (hasMeta(e, "eclipsia_boss") || hasMeta(e, "eclipsia_minion")) return;
        // CUSTOM = плагинный спавн через World#spawnEntity — у GatekeeperBoss
        // он используется. Но мы уже проверили eclipsia_boss/minion выше.
        event.setCancelled(true);
    }

    private void sweepArena() {
        World world = Bukkit.getWorld(GatekeeperArena.ARENA_WORLD);
        if (world == null) return;
        Location centre = new Location(world,
                GatekeeperArena.ARENA_X + 0.5,
                GatekeeperArena.ARENA_Y,
                GatekeeperArena.ARENA_Z + 0.5);
        for (Entity e : world.getNearbyEntities(centre, ARENA_RADIUS, 64.0, ARENA_RADIUS)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e instanceof Player) continue;
            if (hasMeta(e, "eclipsia_boss") || hasMeta(e, "eclipsia_minion")) continue;
            // Цилиндр (без y), как SpawnManager.isInExclusion.
            double dx = e.getLocation().getX() - centre.getX();
            double dz = e.getLocation().getZ() - centre.getZ();
            if (dx * dx + dz * dz > ARENA_RADIUS_SQ) continue;
            // Чистим только агрессивных/пассивных мобов — не трогаем дроп-итемы,
            // экспу, стрелы, упавших коров и пр. (хотя коров на арене и не бывает).
            if (!(e instanceof Mob)) continue;
            // На всякий случай: если это вдруг IronGolem без метки босса —
            // тоже не трогаем (вдруг плагин восстанавливал состояние).
            if (e instanceof org.bukkit.entity.IronGolem) continue;
            e.remove();
        }
    }

    private boolean isInArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!GatekeeperArena.ARENA_WORLD.equals(loc.getWorld().getName())) return false;
        double dx = loc.getX() - GatekeeperArena.ARENA_X - 0.5;
        double dz = loc.getZ() - GatekeeperArena.ARENA_Z - 0.5;
        return dx * dx + dz * dz <= ARENA_RADIUS_SQ;
    }

    private boolean hasMeta(Entity e, String key) {
        for (MetadataValue v : e.getMetadata(key)) {
            if (v.getOwningPlugin() == plugin) return true;
        }
        return false;
    }
}
