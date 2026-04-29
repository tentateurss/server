package ru.eclipsia.mobs.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.eclipsia.mobs.EclipsiaMobs;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Арена Хранителя Врат: автоматический спавн при подходе игрока,
 * пост-обработка после убийства босса (очистка неба, активация
 * портала в арке).
 *
 * <p>Состояние «босс повержен» сохраняется в PDC мира {@code beach}
 * через ключ {@link #DEFEATED_FLAG}, чтобы после рестарта сервера
 * арена оставалась «чистой» и босс повторно не появлялся.
 *
 * <p>Координаты арены синхронизированы с
 * {@link ru.eclipsia.mobs.boss.GatekeeperArena#ARENA_WORLD} и
 * BeachGenerator (центр x=0, y=5, z=80, радиус 12, арка обсидиана
 * по z=77, y=7..11).
 */
public final class GatekeeperArena implements Listener {

    public static final String ARENA_WORLD = "beach";
    public static final int ARENA_X = 0;
    public static final int ARENA_Y = 5;
    public static final int ARENA_Z = 80;
    /** Радиус триггера авто-спавна. Чуть больше визуального диска (12). */
    private static final int TRIGGER_RADIUS = 15;
    /** PDC-флаг «босс уже побеждён на этом мире». */
    private static final String DEFEATED_FLAG = "eclipsia_gatekeeper_defeated";
    /** PDC-флаг «постобработка применена» (чтобы не спамить setTime). */
    private static final String POSTPROCESS_FLAG = "eclipsia_gatekeeper_postproc";

    /** Арка портала: X=-2..2, Y=7..10, Z=77 (интерьер обсидиановой арки). */
    private static final int ARCH_X_MIN = -2;
    private static final int ARCH_X_MAX = 2;
    private static final int ARCH_Y_MIN = 7;
    private static final int ARCH_Y_MAX = 10;
    private static final int ARCH_Z = 77;

    private final EclipsiaMobs plugin;
    /** Игроки, которым уже попытались заспавнить босса — чтобы не дёргать каждую тику. */
    private final Set<UUID> recentlyTriggered = new HashSet<>();

    public GatekeeperArena(EclipsiaMobs plugin) {
        this.plugin = plugin;
    }

    /** Один раз при загрузке: если босс уже был убит, применить постобработку. */
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World beach = Bukkit.getWorld(ARENA_WORLD);
            if (beach == null) return;
            if (isDefeated(beach)) {
                applyPostProcess(beach, /* announce */ false);
            }
        }, 80L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Чистим trigger-кэш игрока при релогине, чтобы он мог снова
        // вызвать босса, если предыдущая попытка не прошла.
        recentlyTriggered.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Срабатываем только при смене блока (иначе каждый тик).
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!ARENA_WORLD.equals(world.getName())) return;

        // Если босс уже убит — больше не спавним.
        if (isDefeated(world)) return;

        // Уже идёт бой.
        BossManager mgr = BossManager.getInstance();
        if (mgr != null && mgr.isGatekeeperActive()) return;

        // Проверяем расстояние до центра арены (XZ, без учёта Y).
        double dx = player.getLocation().getX() - ARENA_X;
        double dz = player.getLocation().getZ() - ARENA_Z;
        if (dx * dx + dz * dz > TRIGGER_RADIUS * TRIGGER_RADIUS) {
            return;
        }

        // Антиспам: не дёргаем спавн чаще одного раза в минуту для одного
        // и того же игрока.
        UUID uuid = player.getUniqueId();
        if (recentlyTriggered.contains(uuid)) return;
        recentlyTriggered.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> recentlyTriggered.remove(uuid), 20L * 60L);

        spawnBoss(world, player);
    }

    private void spawnBoss(World world, Player trigger) {
        GatekeeperBoss boss = BossManager.getInstance().getGatekeeper();
        if (boss.isActive()) return;

        Location spawnLoc = new Location(world, ARENA_X + 0.5, ARENA_Y + 1,
                ARENA_Z + 0.5);
        // Раскат грома для эффекта появления.
        world.strikeLightningEffect(spawnLoc);
        Bukkit.broadcastMessage("§c§l[!] §fИгрок " + trigger.getName()
                + " §cпотревожил Хранителя Врат...");
        boss.spawn(spawnLoc);
    }

    // ====================== Постобработка на смерть босса ======================

    /** Вызывается из {@link ru.eclipsia.mobs.listeners.BossDeathListener}. */
    public void onBossDefeated(World world) {
        if (world == null) return;
        markDefeated(world);
        applyPostProcess(world, /* announce */ true);
    }

    private void applyPostProcess(World world, boolean announce) {
        if (isPostProcessApplied(world) && !announce) {
            // Уже чинили — просто восстанавливаем видимое состояние.
        }
        // 1) Небо: вечный полдень + ясная погода.
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        // gamerule doDaylightCycle=false, чтобы время не скатывалось в ночь.
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");

        // 2) Портал в арке активен.
        activatePortal(world);

        markPostProcessApplied(world);

        if (announce) {
            Bukkit.broadcastMessage("§a§lНебо над Берегом просветлело!");
            Bukkit.broadcastMessage("§dПортал в арене Хранителя Врат пробудился...");
        }
    }

    private void activatePortal(World world) {
        // Ставим блоки NETHER_PORTAL с осью X (портал «смотрит» на север/юг,
        // т.е. в плоскости XY, нормаль вдоль Z). Это визуально активирует
        // обсидиановую арку на северной стороне арены.
        // Batch через небольшой таймер, чтобы не ронять TPS.
        new BukkitRunnable() {
            int dy = ARCH_Y_MIN;
            int dx = ARCH_X_MIN;

            @Override
            public void run() {
                Block b = world.getBlockAt(dx, dy, ARCH_Z);
                b.setType(Material.NETHER_PORTAL, false);
                if (b.getBlockData() instanceof Orientable o) {
                    o.setAxis(org.bukkit.Axis.X);
                    b.setBlockData(o, false);
                }
                dx++;
                if (dx > ARCH_X_MAX) {
                    dx = ARCH_X_MIN;
                    dy++;
                    if (dy > ARCH_Y_MAX) {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    // ====================== PDC-флаги ======================

    public boolean isDefeated(World world) {
        NamespacedKey key = new NamespacedKey(plugin, DEFEATED_FLAG);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markDefeated(World world) {
        NamespacedKey key = new NamespacedKey(plugin, DEFEATED_FLAG);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isPostProcessApplied(World world) {
        NamespacedKey key = new NamespacedKey(plugin, POSTPROCESS_FLAG);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markPostProcessApplied(World world) {
        NamespacedKey key = new NamespacedKey(plugin, POSTPROCESS_FLAG);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }
}
