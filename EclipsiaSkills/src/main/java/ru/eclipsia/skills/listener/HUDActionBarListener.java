package ru.eclipsia.skills.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;
import ru.eclipsia.skills.EclipsiaSkills;

/**
 * Единый ActionBar для всех ресурсов и базовых статов.
 *
 * <p>Формат (из ТЗ):
 * <pre>
 * §c❤ 150/200 §7(+3) §5♦ 50/80 §9✦ 85/120 §7(+2)  §c⚔15 §a➹12 §9✦20
 * </pre>
 *
 * <p>Заменяет старый {@link ManaBarListener} с BossBar-маной — она съедала
 * слот босс-бара, в котором логичнее держать опыт. Теперь BossBar остаётся
 * чистым под опыт/боссов, а ресурсы видно одной строкой над хотбаром.
 */
public class HUDActionBarListener implements Listener {

    private static final long PERIOD_TICKS = 10L; // 0.5 сек

    private final EclipsiaSkills plugin;
    private BukkitTask task;
    private final LegacyComponentSerializer legacy =
            LegacyComponentSerializer.legacySection();

    public HUDActionBarListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        update(p);
                    } catch (Throwable t) {
                        // не валим тик из-за одного проблемного игрока
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

    private void update(Player player) {
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        if (profile == null) return;

        // HP
        var maxHpAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        int maxHp = (int) Math.round(maxHpAttr != null
                ? maxHpAttr.getValue() : player.getMaxHealth());
        int hp = (int) Math.round(player.getHealth());
        int hpRegenBonus = profile.getStat(StatKeys.HEALTH_REGEN);

        // Мана
        int curMana = profile.getCurrentMana();
        int maxMana = profile.getMaxMana();
        int manaRegenBonus = profile.getStat(StatKeys.MANA_REGEN);

        // Эгида
        int aegis = profile.getAegis();
        int maxAegis = profile.getMaxAegis();

        // Базовые статы (для контроля прокачки)
        int str = profile.getStat(StatKeys.STRENGTH);
        int dex = profile.getStat(StatKeys.DEXTERITY);
        int intl = profile.getStat(StatKeys.INTELLIGENCE);

        StringBuilder sb = new StringBuilder();
        sb.append("§c❤ ").append(hp).append('/').append(maxHp);
        if (hpRegenBonus > 0) {
            sb.append(" §7(+").append(hpRegenBonus).append(')');
        }
        if (maxAegis > 0) {
            sb.append("  §5♦ ").append(aegis).append('/').append(maxAegis);
        }
        sb.append("  §9✦ ").append(curMana).append('/').append(maxMana);
        if (manaRegenBonus > 0) {
            sb.append(" §7(+").append(manaRegenBonus).append(')');
        }
        sb.append("  §c⚔").append(str)
                .append(" §a➹").append(dex)
                .append(" §9✦").append(intl);

        Component text = legacy.deserialize(sb.toString());
        player.sendActionBar(text);
    }
}
