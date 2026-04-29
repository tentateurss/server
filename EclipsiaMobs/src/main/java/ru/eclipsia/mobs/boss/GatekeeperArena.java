package ru.eclipsia.mobs.boss;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.eclipsia.mobs.EclipsiaMobs;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Арена Хранителя Врат: автоматический спавн при подходе игрока,
 * пост-обработка после убийства босса (активация частиц-портала в стене).
 *
 * <p>v7 (per-player, particle portal):
 * <ul>
 *   <li>Никакого Nether-портала и обсидиана не строится — за боссом
 *       просто гладкая стена из BLACK_WOOL.</li>
 *   <li>После убийства босса в стене запускается шедулер частиц
 *       (PORTAL/DRAGON_BREATH/SOUL_FIRE_FLAME) — визуальный «портал».</li>
 *   <li>Listener PlayerMoveEvent: когда игрок подходит ближе 2.5 блока
 *       к точке портала — телепорт в мир {@code elikium}.</li>
 *   <li>Триггер босса и PDC «defeated» хранятся per-player.</li>
 * </ul>
 */
public final class GatekeeperArena implements Listener {

    public static final String ARENA_WORLD = "beach";
    public static final String ELIKIUM_WORLD = "elikium";
    public static final int ARENA_X = 0;
    /** floorY арены в BeachGenerator = GROUND_Y(4) + 8 = 12. Спавним на +1. */
    public static final int ARENA_Y = 13;
    public static final int ARENA_Z = 95;
    /** Радиус триггера авто-спавна. */
    private static final int TRIGGER_RADIUS = 18;

    /** PDC-флаг на игроке: убил Хранителя. */
    private static final String PLAYER_DEFEATED = "eclipsia_gatekeeper_defeated";
    /** PDC-флаг на мире: портал когда-либо был открыт. */
    private static final String WORLD_PORTAL_OPEN = "eclipsia_gatekeeper_portal_open";

    /** «Лицевая» сторона стены, перед которой будут крутиться частицы. */
    private static final double PORTAL_X = 0.5;
    private static final double PORTAL_Y = 16.0;
    private static final double PORTAL_Z = 154.5;
    /** Радиус срабатывания телепорта в Эликий. */
    private static final double TELEPORT_RADIUS = 2.5;

    private final EclipsiaMobs plugin;
    private final Set<UUID> recentlyTriggered = new HashSet<>();
    private final Set<UUID> recentlyTeleported = new HashSet<>();

    /** Запущен ли уже шедулер частиц портала (один на сервер). */
    private boolean portalParticlesActive = false;

    public GatekeeperArena(EclipsiaMobs plugin) {
        this.plugin = plugin;
    }

    /** Один раз при загрузке: если портал уже открывался — сразу заводим частицы. */
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World beach = Bukkit.getWorld(ARENA_WORLD);
            if (beach == null) return;
            if (isPortalOpenedInWorld(beach)) {
                activatePortal(beach);
            }
        }, 80L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recentlyTriggered.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Сброс per-player состояния арены (используется /admin resetplayer).
     * Убирает игрока из {@code recentlyTriggered}/{@code recentlyTeleported}
     * и снимает PDC «победил Хранителя» — тогда следующий заход на арену
     * корректно поднимет нового босса.
     */
    public void resetPlayerState(java.util.UUID uuid) {
        recentlyTriggered.remove(uuid);
        recentlyTeleported.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!ARENA_WORLD.equals(world.getName())) return;

        // ====== Триггер босса ======
        tryTriggerBoss(player, world);

        // ====== Подход к порталу: телепорт в Эликий ======
        tryTeleportToElikium(player, world);
    }

    private void tryTriggerBoss(Player player, World world) {
        if (hasDefeated(player)) return;
        BossManager mgr = BossManager.getInstance();
        if (mgr != null && mgr.isGatekeeperActive()) return;

        double dx = player.getLocation().getX() - ARENA_X;
        double dz = player.getLocation().getZ() - ARENA_Z;
        if (dx * dx + dz * dz > TRIGGER_RADIUS * TRIGGER_RADIUS) return;

        UUID uuid = player.getUniqueId();
        if (recentlyTriggered.contains(uuid)) return;
        recentlyTriggered.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> recentlyTriggered.remove(uuid), 20L * 30L);
        spawnBoss(world, player);
    }

    private void tryTeleportToElikium(Player player, World world) {
        // v11: телепорт срабатывает ТОЛЬКО после победы над Хранителем.
        // Раньше срабатывало «всегда» — и игрок мог проскочить через
        // arena_wall глитчами и попасть в Эликий без боя. Теперь портал
        // физически существует только после onBossDefeated().
        if (!hasDefeated(player)) return;
        if (!isPortalOpenedInWorld(world)) return;

        Location loc = player.getLocation();
        double dx = loc.getX() - PORTAL_X;
        double dy = loc.getY() - PORTAL_Y;
        double dz = loc.getZ() - PORTAL_Z;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > TELEPORT_RADIUS * TELEPORT_RADIUS) return;

        UUID uuid = player.getUniqueId();
        if (recentlyTeleported.contains(uuid)) return;
        recentlyTeleported.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> recentlyTeleported.remove(uuid), 20L * 5L);

        // v8: fallback — если elikium нет, кидаем в lobby или world.
        World elikium = Bukkit.getWorld(ELIKIUM_WORLD);
        if (elikium == null) elikium = Bukkit.getWorld("lobby");
        if (elikium == null) elikium = Bukkit.getWorld("world");
        if (elikium == null) {
            player.sendMessage("§cНи один целевой мир (elikium/lobby/world) не загружен.");
            return;
        }
        Location target = elikium.getSpawnLocation().clone().add(0.5, 0, 0.5);
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1f, 0.7f);
        player.teleport(target);
        player.sendMessage("§dВы перенесены в §5" + elikium.getName() + "§d.");
    }

    private void spawnBoss(World world, Player trigger) {
        GatekeeperBoss boss = BossManager.getInstance().getGatekeeper();
        if (boss.isActive()) return;

        // v7: ОЧИСТКА воздуха над платформой (5×5×4) — иначе босс
        // застревает в декоративных блоках арены при появлении.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    Block b = world.getBlockAt(ARENA_X + dx, ARENA_Y + dy, ARENA_Z + dz);
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        Location spawnLoc = new Location(world, ARENA_X + 0.5, ARENA_Y + 1, ARENA_Z + 0.5);
        world.strikeLightningEffect(spawnLoc);
        Bukkit.broadcastMessage("§c§l[!] §fИгрок " + trigger.getName()
                + " §cпотревожил Хранителя Врат...");
        boss.spawn(spawnLoc);
    }

    // ====================== Постобработка на смерть босса ======================

    public void onBossDefeated(World world, Player killer) {
        if (world == null) return;

        if (killer != null) {
            markDefeated(killer);
            Location bossLoc = new Location(world, ARENA_X, ARENA_Y, ARENA_Z);
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(bossLoc) <= 30) {
                    markDefeated(p);
                }
            }
        }

        if (!isPortalOpenedInWorld(world)) {
            markPortalOpenedInWorld(world);
            activatePortal(world);
            Bukkit.broadcastMessage("§dВ стене за ареной зашевелились странные частицы. Подойдите...");
        }
    }

    /**
     * v7: Активация портала = просто запуск шедулера частиц у точки портала.
     * Никаких блоков не меняется. Телепорт в Эликий — на approach.
     */
    private void activatePortal(World world) {
        if (portalParticlesActive) return;
        portalParticlesActive = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World w = Bukkit.getWorld(ARENA_WORLD);
            if (w == null) return;
            Location center = new Location(w, PORTAL_X, PORTAL_Y, PORTAL_Z);
            // Кольцо PORTAL-частиц
            for (int i = 0; i < 8; i++) {
                double a = (System.currentTimeMillis() * 0.005 + i * Math.PI / 4) % (Math.PI * 2);
                double r = 1.4;
                double px = PORTAL_X + Math.cos(a) * r;
                double py = PORTAL_Y + Math.sin(a) * r;
                w.spawnParticle(Particle.PORTAL, px, py, PORTAL_Z, 1, 0, 0, 0, 0);
            }
            // Центральный «глаз» из soul_fire / dragon_breath
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 3, 0.4, 0.4, 0.05, 0.01);
            w.spawnParticle(Particle.DRAGON_BREATH, center, 2, 0.6, 0.6, 0.1, 0.0);
            // Редкое пробивание края — END_ROD как сияние
            if (Math.random() < 0.15) {
                w.spawnParticle(Particle.END_ROD, center, 1, 0.8, 0.8, 0.1, 0.02);
            }
        }, 40L, 2L);
    }

    // ====================== PDC-флаги ======================

    public boolean hasDefeated(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_DEFEATED);
        Byte v = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    public void markDefeated(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_DEFEATED);
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    public void clearDefeated(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_DEFEATED);
        player.getPersistentDataContainer().remove(key);
    }

    private boolean isPortalOpenedInWorld(World world) {
        NamespacedKey key = new NamespacedKey(plugin, WORLD_PORTAL_OPEN);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markPortalOpenedInWorld(World world) {
        NamespacedKey key = new NamespacedKey(plugin, WORLD_PORTAL_OPEN);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    @SuppressWarnings("unused")
    private static Color unused() { return Color.fromRGB(0, 0, 0); }
}
