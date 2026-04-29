package ru.eclipsia.mobs.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import ru.eclipsia.mobs.EclipsiaMobs;
import ru.eclipsia.mobs.experience.ExperienceManager;
import ru.eclipsia.mobs.mob.CustomMob;
import ru.eclipsia.mobs.mob.MobManager;
import ru.eclipsia.mobs.orbs.OrbManager;

import java.util.UUID;

/**
 * Обработчик смерти мобов.
 *
 * <p>Дополнительно слушает {@link EntityDamageByEntityEvent} на {@code MONITOR},
 * чтобы всегда запомнить последнего урона-от-игрока в metadata
 * {@code eclipse_last_damager}. Это спасает случай, когда урон пришёл
 * от навыка через {@link LivingEntity#damage(double, org.bukkit.entity.Entity)}
 * или через проджектайл, и после смерти моба {@code getKiller()} вернул
 * {@code null} (Paper иногда теряет killer, если финальный dmg был .damage(...)).
 */
public class MobDeathListener implements Listener {

    private static final String LAST_DAMAGER_META = "eclipse_last_damager";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!MobManager.getInstance().isCustomMob(victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        victim.setMetadata(LAST_DAMAGER_META,
                new FixedMetadataValue(EclipsiaMobs.getInstance(),
                        attacker.getUniqueId().toString()));
    }

    /** Игрок — прямой атакер или стрелок проджектайла. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        // Проверяем что это кастомный моб
        if (!MobManager.getInstance().isCustomMob(event.getEntity())) {
            return;
        }

        // Получаем данные моба
        CustomMob mob = MobManager.getInstance().getCustomMobFromEntity(event.getEntity());
        if (mob == null) return;

        // Получаем убийцу: сначала стандартный API, затем metadata от SkillListener
        // (eclipse_killer, выставляется в момент навыка), затем fallback
        // eclipse_last_damager (последний игрок, нанёсший любой урон).
        Player killer = resolveKiller(event.getEntity());
        if (killer == null) {
            EclipsiaMobs.getInstance().getLogger().warning(
                    "Моб " + mob.getId() + " (" + event.getEntityType()
                            + ") умер, но не удалось определить убийцу-игрока — XP не выдан.");
            return;
        }

        // Даем опыт
        ExperienceManager.getInstance().addExperience(killer, mob.getExperience());

        // Даем орбы
        OrbManager.getInstance().dropOrbs(killer, event.getEntity().getLocation(), mob);

        // Очищаем дефолтный дроп
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * Возвращает игрока, которому нужно зачесть убийство. Приоритет:
     * <ol>
     *   <li>{@link LivingEntity#getKiller()} (стандартный Bukkit-механизм);</li>
     *   <li>metadata {@code eclipse_killer} — UUID атакующего (SkillListener
     *       выставляет его в момент применения навыка);</li>
     *   <li>metadata {@code eclipse_last_damager} — последний игрок, нанёсший
     *       мобу любой урон (на случай, если .damage(...) «потерял» killer).</li>
     * </ol>
     */
    private Player resolveKiller(LivingEntity victim) {
        Player byApi = victim.getKiller();
        if (byApi != null) return byApi;

        Player byMeta = playerFromMeta(victim, "eclipse_killer");
        if (byMeta != null) return byMeta;

        Player byLastDamager = playerFromMeta(victim, LAST_DAMAGER_META);
        if (byLastDamager != null) return byLastDamager;

        // Последний шанс: ближайший выживший игрок в радиусе 15 блоков.
        // Защищает кейс смерти моба от навыка с радиусом, когда метаданные
        // не успели проставиться (или жертва была заспавнена недавно).
        return findNearbyPlayer(victim);
    }

    /** Ближайший игрок в выживании/приключении в радиусе 15 блоков. */
    private Player findNearbyPlayer(LivingEntity mob) {
        return mob.getWorld()
                .getNearbyEntities(mob.getLocation(), 15, 15, 15)
                .stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> p.getGameMode() == org.bukkit.GameMode.SURVIVAL
                        || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(mob.getLocation()),
                        b.getLocation().distanceSquared(mob.getLocation())))
                .orElse(null);
    }

    private Player playerFromMeta(LivingEntity victim, String key) {
        if (!victim.hasMetadata(key)) return null;
        for (MetadataValue mv : victim.getMetadata(key)) {
            String raw = mv.asString();
            if (raw == null || raw.isEmpty()) continue;
            try {
                UUID id = UUID.fromString(raw);
                Player p = Bukkit.getPlayer(id);
                if (p != null) return p;
            } catch (IllegalArgumentException ignored) {
                // некорректный UUID — пробуем следующее значение
            }
        }
        return null;
    }
}
