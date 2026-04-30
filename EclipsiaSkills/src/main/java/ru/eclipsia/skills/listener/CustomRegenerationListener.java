package ru.eclipsia.skills.listener;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.eclipsia.core.combat.DamageCalculator;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;
import ru.eclipsia.skills.EclipsiaSkills;

/**
 * Кастомная регенерация HP/маны/Эгиды для всех онлайн-игроков.
 *
 * <p>Раз в секунду перебираем игроков и:
 * <ul>
 *   <li>HP: 0.5 % максимума + бонус {@code health_regen} с экипировки/перков;</li>
 *   <li>Мана: 0.5 % максимума + бонус {@code mana_regen};</li>
 *   <li>Эгида: {@code aegis_regen}, но только если прошло
 *       {@code aegis_delay} секунд (база 5) с момента последнего урона.</li>
 * </ul>
 *
 * Ванильная регенерация HP уже отключена в {@code NoHungerListener},
 * так что наш реген — единственный источник восстановления HP.
 *
 * <p>Заменяет {@link ManaRegenerationListener}: теперь все три ресурса
 * капают одним общим тиком, а не тремя независимыми.
 */
public class CustomRegenerationListener implements Listener {

    private static final long PERIOD_TICKS = 20L; // 1 секунда

    private final EclipsiaSkills plugin;
    private BukkitTask task;

    public CustomRegenerationListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        tickPlayer(player);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Regen tick failed for "
                                + player.getName() + ": " + t.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickPlayer(Player player) {
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        if (profile == null) return;

        // ===== HP =====
        var maxHpAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : player.getMaxHealth();
        double hp = player.getHealth();
        if (hp > 0 && hp < maxHp) {
            double base = maxHp * 0.005; // 0.5 %/сек
            int bonus = profile.getStat(StatKeys.HEALTH_REGEN);
            double regen = base + bonus;
            if (regen > 0) {
                player.setHealth(Math.min(maxHp, hp + regen));
            }
        }

        // Если игрок мёртв или AFK — обновлять профиль не имеет смысла,
        // но это лишь оптимизация. Дальше — мана и Эгида.

        boolean profileDirty = false;
        var b = profile.toBuilder();

        // ===== МАНА =====
        // maxMana = база класса + экипировка + перки (см. ManaManager).
        // До фикса экипировка с «Мана:+N» вообще не повышала максимум —
        // парсер EquipmentBonusApplier клал значение в bonuses["mana"],
        // но никто это число не применял.
        int curMana = profile.getCurrentMana();
        int maxMana = ru.eclipsia.core.stats.ManaManager.getMaxMana(player, profile);
        if (curMana > maxMana) {
            // Игрок снял предмет с +mana — клампим текущее значение.
            curMana = maxMana;
            b.currentMana(curMana);
            profileDirty = true;
        }
        if (curMana < maxMana) {
            double base = maxMana * 0.005;
            int bonus = profile.getStat(StatKeys.MANA_REGEN);
            int regen = (int) Math.round(base + bonus);
            if (regen > 0) {
                b.currentMana(Math.min(maxMana, curMana + regen));
                profileDirty = true;
            }
        }

        // ===== ЭГИДА =====
        int aegis = profile.getAegis();
        int maxAegis = profile.getMaxAegis();
        if (aegis < maxAegis) {
            int delaySec = Math.max(0, profile.getStat(StatKeys.AEGIS_DELAY));
            if (delaySec == 0) delaySec = 5; // дефолт ТЗ
            long sinceDamage = System.currentTimeMillis()
                    - DamageCalculator.getLastDamageMs(player.getUniqueId());
            if (sinceDamage >= delaySec * 1000L) {
                int regen = profile.getStat(StatKeys.AEGIS_REGEN);
                if (regen > 0) {
                    b.aegis(Math.min(maxAegis, aegis + regen));
                    profileDirty = true;
                }
            }
        }

        if (profileDirty) {
            // Без updateProfile — пишем в кэш через builder, чтобы не дёргать
            // PDC/диск каждую секунду. Сохранение произойдёт по своему таймеру
            // или на quit-хуке.
            plugin.getAPI().updateProfile(player, b.build());
        }
    }
}
