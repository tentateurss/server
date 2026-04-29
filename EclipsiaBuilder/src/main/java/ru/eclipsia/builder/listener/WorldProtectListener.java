package ru.eclipsia.builder.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import ru.eclipsia.builder.EclipsiaBuilder;

/**
 * v7: Тотальная защита мира {@code beach} от изменений.
 *
 * <p>Игроки НЕ могут ломать/ставить/жечь/заливать блоки. Огонь,
 * декай листвы, рост растений, поршни и взрывы — всё блокируется.
 * Админы (с пермишеном {@code eclipsia.builder.admin} или OP в creative)
 * могут ломать/ставить как обычно — для оперативных правок мира.
 *
 * <p>v11: Применяется ко всем игровым мирам, кроме явно разрешённых
 * (лобби/админские). По умолчанию защищаются {@code beach} и
 * {@code elikium}; остальные — разрешены.
 */
public final class WorldProtectListener implements Listener {

    /** v11: набор защищённых миров. Админы ({@link #ADMIN_BYPASS_PERM})
     *  или OP в Creative обходят защиту. */
    public static final java.util.Set<String> PROTECTED_WORLDS =
            java.util.Set.of("beach", "elikium");

    /** Совместимость с v7: держим старую константу, указывая на первый
     *  мир списка — чтобы любые внешние ссылки не сломались. */
    public static final String PROTECTED_WORLD = "beach";

    public static final String ADMIN_BYPASS_PERM = "eclipsia.builder.admin";

    private final EclipsiaBuilder plugin;

    public WorldProtectListener(EclipsiaBuilder plugin) {
        this.plugin = plugin;
    }

    private boolean isProtected(org.bukkit.World w) {
        return w != null && PROTECTED_WORLDS.contains(w.getName());
    }

    private boolean isProtected(org.bukkit.block.Block b) {
        return b != null && isProtected(b.getWorld());
    }

    private boolean canBypass(Player p) {
        if (p == null) return false;
        if (p.hasPermission(ADMIN_BYPASS_PERM)) return true;
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE;
    }

    // ========================== Player actions ==========================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!isProtected(e.getBlock())) return;
        if (canBypass(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                "§cЭтот мир не изменяется."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isProtected(e.getBlock())) return;
        if (canBypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!isProtected(e.getBlockClicked())) return;
        if (canBypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!isProtected(e.getBlockClicked())) return;
        if (canBypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!isProtected(e.getClickedBlock())) return;
        if (canBypass(e.getPlayer())) return;
        // Блокируем «physical» (плиты давления, фермерскую землю) и
        // нажимание на блоки которые меняют состояние.
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
            return;
        }
        // Блокируем огниво / корзинку / TNT-взрыватель.
        if (e.getMaterial() == Material.FLINT_AND_STEEL
                || e.getMaterial() == Material.FIRE_CHARGE
                || e.getMaterial() == Material.LAVA_BUCKET
                || e.getMaterial() == Material.WATER_BUCKET
                || e.getMaterial() == Material.POWDER_SNOW_BUCKET) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!isProtected(e.getEntity().getWorld())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (!isProtected(e.getEntity().getWorld())) return;
        if (canBypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    // ========================== Environment ==========================

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!isProtected(e.getEntity().getWorld())) return;
        e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        if (!isProtected(e.getWorld())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!isProtected(e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (!isProtected(e.getWorld())) return;
        // Дождя нет в beach — там вечная буря-ясность по сценарию.
        if (e.toWeatherState()) e.setCancelled(true);
    }
}
