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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.eclipsia.mobs.EclipsiaMobs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    /**
     * После убийства Хранителя игрок переносится в основной мир.
     * Раньше был отдельный мир {@code "elikium"}, но ассеты Эликия
     * процедурно генерируются прямо в мире {@code "world"}
     * ({@code WorldGenerator}); отдельный мир оказался лишним.
     */
    public static final String ELIKIUM_WORLD = "world";

    /**
     * Точка появления игрока перед ЮЖНЫМИ воротами Эликия (мир {@code world}).
     *
     * <p>Южные ворота города стоят на (0, 113) — игрок появляется на (0, 118),
     * то есть на 5 блоков южнее ворот. Через арку он видит улицы города и
     * силуэт собора (центр (45, -15)). Координаты согласованы с
     * {@code WorldGenerator.SPAWN_X/Y/Z}.
     */
    // v32: спавн перенесён в боковой грот (-25, 70..78, 132..148).
    //      Игрок появляется внутри пещеры лицом на восток, идёт через
    //      туннель и выходит в каньон через большую арку на x=-10.
    public static final double ELIKIUM_SPAWN_X = -22.5;
    public static final double ELIKIUM_SPAWN_Y = 71.0;
    /**
     * z=140.5 — обновлено в v32: центр нового бокового грота
     * в западной стене каньона. v29: z=130.5 (в чистом каньоне).
     * Раньше z=118.5 — но это попадало внутрь толщины стены (z=116..124),
     * игрок появлялся в блоке. После выравнивания ворот на полигоне
     * (см. WorldGenerator.SOUTH_GATE) спавн перенесён на 12 блоков южнее
     * — в южный каньон, перед аркой ворот.
     */
    public static final double ELIKIUM_SPAWN_Z = 140.5;

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
    /** Время последнего показа сообщения «мир не готов» — чтобы при движении
     *  внутри радиуса портала не спамить чат/лог на каждом блоке. */
    private final Map<UUID, Long> lastWorldMissingMs = new HashMap<>();
    private static final long WORLD_MISSING_COOLDOWN_MS = 3000L;

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
        UUID uuid = event.getPlayer().getUniqueId();
        recentlyTriggered.remove(uuid);
        // Чистим throttle «мир не готов», чтобы при ре-логине игрок
        // получил актуальное состояние и не молчал 3 секунды.
        lastWorldMissingMs.remove(uuid);
    }

    /** Чистим in-memory state при выходе — иначе map'ы растут на каждый
     *  уникальный UUID, который хоть раз попадал в портал/триггер. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        recentlyTriggered.remove(uuid);
        recentlyTeleported.remove(uuid);
        lastWorldMissingMs.remove(uuid);
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
        // Чистим и map throttle сообщения «мир не готов», иначе после
        // /admin resetplayer игрок до 3 секунд не увидит сообщения о
        // незагруженном мире.
        lastWorldMissingMs.remove(uuid);
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

        // Точка прибытия — фиксированные координаты перед северными воротами
        // Эликия (мир "world", PR 1 WorldGenerator). Раньше использовался
        // getSpawnLocation() запасных миров — но spawn у плоского мира
        // обычно (0,4,0), что для нашего города-плато на y=70 неверно
        // и игрок падал внутрь стен / в стену.
        World elikium = Bukkit.getWorld(ELIKIUM_WORLD);
        if (elikium == null) {
            // Раньше тут был fallback на 'lobby' — это БАГ: попадая в lobby,
            // игрок мгновенно перехватывался LobbyListener.handleLobbyEntry
            // (PlayerChangedWorldEvent → handleLobbyEntry → teleportToBeach,
            // если у профиля нет lastLocation), и его выбрасывало обратно
            // на Берег. Лучше честно сказать «мир не готов» и не двигать
            // игрока, чем телепортировать его в неработающий пайплайн.
            //
            // Важно: НЕ добавляем сюда recentlyTeleported — иначе игрок
            // на 5 секунд молча залочен (PlayerMoveEvent ничего не делает),
            // а ему сказано «попробуйте снова». Используем отдельный
            // throttle (3 сек) чтобы не спамить чат на каждом MoveEvent.
            long now = System.currentTimeMillis();
            Long last = lastWorldMissingMs.get(uuid);
            if (last == null || now - last > WORLD_MISSING_COOLDOWN_MS) {
                lastWorldMissingMs.put(uuid, now);
                player.sendMessage("§cЦелевой мир '" + ELIKIUM_WORLD
                        + "' ещё не загружен. Подождите и попробуйте снова.");
                plugin.getLogger().warning("[GatekeeperArena] Bukkit.getWorld('"
                        + ELIKIUM_WORLD + "') == null при попытке портал-телепорта игрока "
                        + player.getName());
            }
            return;
        }

        // Cooldown ставим только после успешного резолва мира — чтобы
        // не блокировать ретрай при «мир ещё не готов».
        recentlyTeleported.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> recentlyTeleported.remove(uuid), 20L * 5L);

        // yaw=180 в Bukkit = −Z (север). Игрок стоит ЮЖНЕЕ города (z=118),
        // южные ворота — на z=113, собор — на (45, -15). Чтобы он смотрел
        // строго на ворота / собор, ставим yaw=180.
        Location target = new Location(elikium,
                ELIKIUM_SPAWN_X, ELIKIUM_SPAWN_Y, ELIKIUM_SPAWN_Z,
                180f, 0f);

        plugin.getLogger().info("[GatekeeperArena] Портал-ТП: " + player.getName()
                + " " + player.getLocation().getWorld().getName()
                + "(" + (int) player.getLocation().getX() + ","
                + (int) player.getLocation().getY() + ","
                + (int) player.getLocation().getZ() + ") -> "
                + target.getWorld().getName()
                + "(" + (int) target.getX() + ","
                + (int) target.getY() + ","
                + (int) target.getZ() + ")");

        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1f, 0.7f);
        boolean ok = player.teleport(target);
        plugin.getLogger().info("[GatekeeperArena] teleport result=" + ok
                + ", после ТП игрок в " + player.getWorld().getName()
                + "(" + (int) player.getLocation().getX() + ","
                + (int) player.getLocation().getY() + ","
                + (int) player.getLocation().getZ() + ")");
        player.sendMessage("§dВы перенесены в §5Эликий§d.");
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
