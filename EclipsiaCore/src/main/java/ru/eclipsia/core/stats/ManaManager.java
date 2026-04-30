package ru.eclipsia.core.stats;

import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;

/**
 * Пересчёт максимальной маны и связанных параметров.
 *
 * <p>Источники maxMana:
 * <ul>
 *   <li>{@link PlayerProfile#getMaxMana()} — базовая мана класса
 *       (warrior 80 / archer 100 / mage 150);</li>
 *   <li>{@code mana} с экипировки (аффиксы «Мудрого», «Мага» и т.п.);</li>
 *   <li>{@code mana} с перков (узлы дерева).</li>
 * </ul>
 *
 * <p>В отличие от {@link AegisManager}, базовая мана класса хранится прямо
 * в {@code PlayerProfile.maxMana} и НЕ перезаписывается при пересчёте —
 * так мы не теряем «класс-source-of-truth» при горячей смене экипировки.
 * Итоговый максимум считается на лету через {@link #getMaxMana(Player)}.
 *
 * <p>Метод {@link #recompute(Player)} нужен только чтобы клампнуть
 * {@code currentMana ≤ newMax} (если игрок снял предмет с +mana и его
 * текущая мана оказалась выше нового максимума).
 *
 * <p>Все методы безопасны к вызову из основного потока.
 */
public final class ManaManager {

    private ManaManager() { /* utility */ }

    /** Итоговый максимум маны = база класса + экипировка + перки. */
    public static int getMaxMana(Player player) {
        if (player == null) return 0;
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile profile = api == null ? null : api.getActiveProfile(player);
        if (profile == null) return 0;
        return getMaxMana(player, profile);
    }

    /** То же что {@link #getMaxMana(Player)}, но из готового профиля. */
    public static int getMaxMana(Player player, PlayerProfile profile) {
        if (profile == null) return 0;
        int base = profile.getMaxMana();
        int eqBonus = player == null ? 0 : StatResolver.equipment(player, StatKeys.MANA_BONUS);
        int pkBonus = player == null ? 0 : StatResolver.perks(player.getUniqueId(), StatKeys.MANA_BONUS);
        return Math.max(0, base + eqBonus + pkBonus);
    }

    /**
     * Пересчитать current/max и при необходимости клампнуть текущую ману.
     *
     * <p>Вызывается после смены экипировки, изучения/сброса перка и при логине.
     */
    public static void recompute(Player player) {
        if (player == null) return;
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        if (api == null) return;
        PlayerProfile profile = api.getActiveProfile(player);
        if (profile == null) return;

        int newMax = getMaxMana(player, profile);
        int curMana = profile.getCurrentMana();
        int clampedCur = Math.min(curMana, newMax);
        if (clampedCur < 0) clampedCur = 0;

        if (clampedCur == curMana) {
            return; // мана уже в допустимых пределах — ничего не пишем
        }

        PlayerProfile updated = profile.toBuilder()
                .currentMana(clampedCur)
                .build();
        api.updateProfile(player, updated);
    }
}
