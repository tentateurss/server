package ru.eclipsia.core.stats;

import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;

/**
 * Пересчёт максимальной Эгиды и связанных параметров.
 *
 * <p>Источники maxAegis:
 * <ul>
 *   <li>{@code intelligence × 2} (профиль)</li>
 *   <li>{@code aegis} с экипировки</li>
 *   <li>{@code aegis} с перков</li>
 * </ul>
 *
 * <p>Источники aegisRegen:
 * <ul>
 *   <li>{@code aegis_regen} с экипировки</li>
 *   <li>{@code aegis_regen} с перков</li>
 * </ul>
 *
 * <p>Метод {@link #recompute(Player)} безопасен для частого вызова
 * (после allocate/deallocate перка, после смены экипировки, после
 * получения уровня). Он клампит текущую Эгиду к новому максимуму и
 * сохраняет профиль через {@link EclipsiaAPI#updateProfile(Player, PlayerProfile)}.
 */
public final class AegisManager {

    private AegisManager() { /* utility */ }

    private static final int INT_TO_AEGIS = 2;
    private static final int DEFAULT_AEGIS_DELAY = 5;

    /** Пересчитать maxAegis/aegisRegen и обновить профиль. */
    public static void recompute(Player player) {
        if (player == null) return;
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        if (api == null) return;
        PlayerProfile profile = api.getActiveProfile(player);
        if (profile == null) return;

        int intelligence = profile.getStat(StatKeys.INTELLIGENCE);
        int eqAegis = StatResolver.equipment(player, StatKeys.AEGIS);
        int pkAegis = StatResolver.perks(player.getUniqueId(), StatKeys.AEGIS);
        int newMax = Math.max(0, intelligence * INT_TO_AEGIS + eqAegis + pkAegis);

        int curAegis = Math.min(profile.getAegis(), newMax);
        if (newMax == 0) curAegis = 0;

        // Запишем maxAegis в дополнительный stat-ключ, чтобы он был виден через
        // PlayerProfile#getStat и StatResolver#totals (для HUD/debug).
        if (profile.getMaxAegis() == newMax && profile.getAegis() == curAegis) {
            return; // ничего не изменилось — пропускаем сейв
        }

        PlayerProfile updated = profile.toBuilder()
                .maxAegis(newMax)
                .aegis(curAegis)
                .build();
        api.updateProfile(player, updated);
    }

    /** Возвращает задержку перед регеном Эгиды (с учётом перков и экипировки). */
    public static int getDelaySeconds(Player player) {
        if (player == null) return DEFAULT_AEGIS_DELAY;
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile p = api == null ? null : api.getActiveProfile(player);
        int delay = StatResolver.total(player, p, StatKeys.AEGIS_DELAY);
        return delay <= 0 ? DEFAULT_AEGIS_DELAY : delay;
    }

    /** Скорость регена Эгиды (HP в секунду). */
    public static int getRegenPerSecond(Player player) {
        if (player == null) return 0;
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile p = api == null ? null : api.getActiveProfile(player);
        return Math.max(0, StatResolver.total(player, p, StatKeys.AEGIS_REGEN));
    }
}
