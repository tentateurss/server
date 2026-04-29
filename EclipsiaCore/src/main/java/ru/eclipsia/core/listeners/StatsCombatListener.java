package ru.eclipsia.core.listeners;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.combat.DamageCalculator;
import ru.eclipsia.core.combat.DamageDisplay;
import ru.eclipsia.core.combat.DamageType;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.stats.StatsBonusApplier;

/**
 * Боевой слушатель: применяет защиты игрока (уклонение, блок, Эгида,
 * броня, резисты) при получении урона и показывает всплывающие цифры
 * урона над целью.
 *
 * <p>Урон навыков (мили/стрелы/фаербол) обрабатывается отдельным
 * пайплайном в EclipsiaSkills (см. {@code SkillListener}); DamageDisplay
 * по мобам — там же. Здесь — урон, который ПОЛУЧАЕТ игрок, и обычная
 * физическая атака от лука (вне навыка-эклипса).
 */
public class StatsCombatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // ===== ИГРОК — ЦЕЛЬ =====
        if (event.getEntity() instanceof Player defender) {
            DamageCalculator.markDamaged(defender.getUniqueId());
            applyDefences(event, defender);
            // НЕ показываем DamageDisplay над игроком — попадание видно по
            // мерцанию красного экрана и по HUD. До фикса цифра вылетала
            // и над мобом, и над игроком, и часто дублировалась.
            return;
        }

        // ===== ИГРОК — АТАКУЮЩИЙ (обычная физическая атака, не эклипс-навык) =====
        // Если урон уже был нанесён эклипс-скиллом (handleArrowHit/handleFireballHit/
        // handleMeleeStrike) — там уже показан DamageDisplay, и мы НЕ должны
        // делать ещё один + крит-бросок поверх. Метку ставит сам SkillListener.
        if (event.getEntity().hasMetadata("eclipse_skill_dmg")) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Arrow arrow) {
            // Эклипс-стрелы помечены метаданными; их урон считает SkillListener,
            // и он сам вызывает DamageDisplay. Здесь обрабатываем только
            // ванильные/неэклипс-стрелы и бонус ловкости.
            if (arrow.hasMetadata("eclipse_shooter")) {
                return;
            }
            if (arrow.getShooter() instanceof Player shooter) {
                attacker = shooter;
                double bowDamageBonus = StatsBonusApplier.getBowDamageBonus(shooter);
                if (bowDamageBonus > 0) {
                    event.setDamage(event.getDamage() + bowDamageBonus);
                }
            }
        }

        // Крит-бросок и DamageDisplay по мобу для обычных атак.
        if (attacker != null && event.getEntity() instanceof LivingEntity victim
                && !(victim instanceof Player)) {
            PlayerProfile prof = EclipsiaAPI.getInstance().getActiveProfile(attacker);
            double mult = DamageCalculator.rollCrit(prof);
            if (mult > 1.0) {
                event.setDamage(event.getDamage() * mult);
                DamageDisplay.show(victim, event.getDamage(), DamageType.CRIT);
            } else {
                DamageDisplay.show(victim, event.getDamage(), DamageType.PHYSICAL);
            }
        }
    }

    /** Применить уклонение/блок/Эгиду к {@code event.damage}. */
    private void applyDefences(EntityDamageByEntityEvent event, Player defender) {
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        if (api == null) return;
        PlayerProfile profile = api.getActiveProfile(defender);
        if (profile == null) return;

        // Прокси-формула: уровень атакующего ≈ его HP / 2 (грубо). Для игроков
        // и боссов с метаданными можно докрутить позже.
        int attackerLevel = 1;
        if (event.getDamager() instanceof LivingEntity le) {
            attackerLevel = (int) Math.max(1, le.getMaxHealth() / 2.0);
        }

        DamageType type = DamageType.PHYSICAL; // по умолчанию; стихии — это эклипс-навыки.
        DamageCalculator.Result res = DamageCalculator.calculateForPlayer(
                profile, event.getDamage(), type, attackerLevel);

        if (res.dodged) {
            event.setCancelled(true);
            defender.sendMessage("§a⚡ Уклонение!");
            return;
        }
        if (res.blocked) {
            defender.sendMessage("§b\uD83D\uDEE1 Блок!");
        }

        // Обновляем Эгиду в профиле, если она съела часть урона.
        if (res.aegisChanged()) {
            PlayerProfile updated = profile.toBuilder()
                    .aegis(res.aegisAfter)
                    .build();
            api.updateProfile(defender, updated);
        }

        event.setDamage(res.finalDamage);
        if (res.finalDamage <= 0) {
            // Эгида полностью съела урон — событие отменяем, чтобы игрок
            // не получил никаких эффектов knockback'а от голого нуля.
            event.setCancelled(true);
        }
    }
}
