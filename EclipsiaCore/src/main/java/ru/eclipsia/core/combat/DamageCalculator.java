package ru.eclipsia.core.combat;

import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Расчёт защитных слоёв при получении урона ИГРОКОМ.
 *
 * <p>Цепочка применяется в порядке (как в PoE):
 * <ol>
 *   <li>Уклонение (evasion → шанс) — может полностью обнулить удар.</li>
 *   <li>Блок (block_chance + block_amount) — снижает входящий урон.</li>
 *   <li>Эгида — поглощает «чистый» урон ПЕРЕД здоровьем.</li>
 *   <li>Броня — только для PHYSICAL.</li>
 *   <li>Резисты — только для FIRE/COLD/LIGHTNING (кап 75 %).</li>
 * </ol>
 *
 * <p>Класс stateless, чтобы тесты могли его дёргать без подъёма всего сервера.
 * Изменения Эгиды возвращаются через {@link Result} — вызывающий код
 * (StatsCombatListener) сам персистит обновлённый профиль.
 */
public final class DamageCalculator {

    /** Жёсткие капы из ТЗ. */
    public static final double DODGE_CAP   = 0.75;
    public static final double ARMOUR_CAP  = 0.90;
    /**
     * Базовый кап резистов; используется только как фоллбэк, если у игрока
     * не задан персональный *_resist_max. Реальный потолок берётся из
     * {@link StatKeys#FIRE_RESIST_MAX} / COLD_RESIST_MAX / LIGHTNING_RESIST_MAX
     * (в процентах) и зажат сверху {@link StatKeys#ABSOLUTE_RESIST_CAP_PCT}.
     */
    public static final double RESIST_CAP  = 0.75;
    public static final double BLOCK_CAP   = 0.75;
    public static final double CRIT_CAP    = 0.95;

    /**
     * Включает подробный лог защитной цепочки в server.log. Включается
     * через системное свойство -Declipsia.combat.debug=true или /teststats
     * (см. EclipsiaTests). По умолчанию выключен.
     */
    public static volatile boolean DEBUG =
            Boolean.getBoolean("eclipsia.combat.debug");

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("Eclipsia/Combat");

    private DamageCalculator() { /* utility */ }

    /**
     * Время последнего получения урона игроком (в ms). Используется
     * CustomRegenerationListener'ом, чтобы стартовать реген Эгиды только
     * после {@code aegis_delay} секунд тишины. Не персистится в JSON
     * профиля — потерять при рестарте OK (просто будет реген сразу).
     */
    private static final ConcurrentHashMap<UUID, Long> LAST_DAMAGE_MS = new ConcurrentHashMap<>();

    public static void markDamaged(UUID playerId) {
        LAST_DAMAGE_MS.put(playerId, System.currentTimeMillis());
    }

    public static long getLastDamageMs(UUID playerId) {
        return LAST_DAMAGE_MS.getOrDefault(playerId, 0L);
    }

    public static void forgetPlayer(UUID playerId) {
        LAST_DAMAGE_MS.remove(playerId);
    }

    /**
     * Применить все защиты защитника-игрока к входящему урону.
     *
     * @param defender       профиль защитника
     * @param incomingDamage сырой урон до защит
     * @param type           тип урона (важен для брони и резистов)
     * @param attackerLevel  уровень атакующего (для формулы уклонения)
     */
    public static Result calculateForPlayer(PlayerProfile defender,
                                            double incomingDamage,
                                            DamageType type,
                                            int attackerLevel) {
        if (defender == null || incomingDamage <= 0) {
            return Result.passthrough(incomingDamage);
        }

        double damage = incomingDamage;
        StringBuilder dbg = DEBUG ? new StringBuilder("[DamageCalc] ")
                .append("type=").append(type)
                .append(" inc=").append(String.format("%.2f", incomingDamage))
                .append(" lvlAtk=").append(attackerLevel)
                : null;

        // 1. УКЛОНЕНИЕ (только удары, а не AOE/огонь — но проверки типа здесь нет;
        // вызывающий код решает, нужно ли применять защиту вообще).
        int evasion = defender.getStat(StatKeys.EVASION);
        if (evasion > 0 && type != DamageType.TRUE) {
            double dodgeChance = (double) evasion
                    / (evasion + Math.max(1, attackerLevel) * 10.0);
            dodgeChance = Math.min(dodgeChance, DODGE_CAP);
            if (DEBUG) dbg.append(" eva=").append(evasion)
                    .append("(").append(String.format("%.0f%%", dodgeChance * 100)).append(")");
            if (ThreadLocalRandom.current().nextDouble() < dodgeChance) {
                if (DEBUG) LOG.info(dbg.append(" → DODGED").toString());
                return Result.dodged();
            }
        }

        // 2. БЛОК (требует шита; здесь чисто статом — наличие щита проверяет caller).
        boolean blocked = false;
        int blockChance = defender.getStat(StatKeys.BLOCK_CHANCE);
        if (blockChance > 0 && type != DamageType.TRUE) {
            double chance = Math.min(blockChance / 100.0, BLOCK_CAP);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                int amt = Math.min(100, Math.max(0, defender.getStat(StatKeys.BLOCK_AMOUNT)));
                damage *= (1.0 - amt / 100.0);
                blocked = true;
            }
        }

        // 3. ЭГИДА — ест чистый урон ДО брони/резистов (классика ES в PoE).
        int aegisBefore = defender.getAegis();
        int aegisAfter  = aegisBefore;
        if (aegisBefore > 0) {
            if (damage <= aegisBefore) {
                aegisAfter = (int) Math.round(aegisBefore - damage);
                damage = 0;
            } else {
                damage -= aegisBefore;
                aegisAfter = 0;
            }
        }

        // 4. БРОНЯ — только PHYSICAL.
        if (damage > 0 && type == DamageType.PHYSICAL) {
            int armour = defender.getStat(StatKeys.ARMOUR);
            if (armour > 0) {
                double reduction = (double) armour / (armour + damage * 5.0);
                reduction = Math.min(reduction, ARMOUR_CAP);
                damage *= (1.0 - reduction);
            }
        }

        // 5. РЕЗИСТЫ — только FIRE/COLD/LIGHTNING. Кап индивидуальный:
        //    *_resist_max в Map статов (по умолчанию 75 %), но не выше
        //    абсолютного потолка ABSOLUTE_RESIST_CAP_PCT (90 %).
        if (damage > 0) {
            String resistKey = switch (type) {
                case FIRE      -> StatKeys.FIRE_RESIST;
                case COLD      -> StatKeys.COLD_RESIST;
                case LIGHTNING -> StatKeys.LIGHTNING_RESIST;
                default        -> null;
            };
            String capKey = switch (type) {
                case FIRE      -> StatKeys.FIRE_RESIST_MAX;
                case COLD      -> StatKeys.COLD_RESIST_MAX;
                case LIGHTNING -> StatKeys.LIGHTNING_RESIST_MAX;
                default        -> null;
            };
            if (resistKey != null) {
                int resist = defender.getStat(resistKey);
                int capPct = (capKey != null) ? defender.getStat(capKey) : 0;
                if (capPct <= 0) capPct = StatKeys.DEFAULT_RESIST_CAP_PCT;
                if (capPct > StatKeys.ABSOLUTE_RESIST_CAP_PCT) {
                    capPct = StatKeys.ABSOLUTE_RESIST_CAP_PCT;
                }
                double cap = capPct / 100.0;
                double frac = Math.min(resist / 100.0, cap);
                if (frac < 0) frac = 0;
                damage *= (1.0 - frac);
            }
        }

        if (DEBUG) {
            dbg.append(" block=").append(blocked)
                    .append(" aegis=").append(aegisBefore).append("→").append(aegisAfter)
                    .append(" final=").append(String.format("%.2f", Math.max(0, damage)));
            LOG.info(dbg.toString());
        }
        return new Result(false, blocked, Math.max(0, damage), aegisBefore, aegisAfter);
    }

    /**
     * Бросок крита для атакующего. Если крит — возвращает множитель
     * crit_damage / 100 (база 150% = 1.5x). Иначе 1.0.
     */
    public static double rollCrit(PlayerProfile attacker) {
        if (attacker == null) return 1.0;
        int chance = attacker.getStat(StatKeys.CRIT_CHANCE);
        if (chance <= 0) return 1.0;
        double frac = Math.min(chance / 100.0, CRIT_CAP);
        if (ThreadLocalRandom.current().nextDouble() >= frac) return 1.0;
        int mult = attacker.getStat(StatKeys.CRIT_DAMAGE);
        if (mult <= 0) mult = 150; // дефолт x1.5
        return mult / 100.0;
    }

    /** Иммутабельный результат прохождения урона через защиты. */
    public static final class Result {
        public final boolean dodged;
        public final boolean blocked;
        /** Финальный урон, который дойдёт до HP игрока. */
        public final double finalDamage;
        /** Эгида до и после удара (для обновления профиля). */
        public final int aegisBefore;
        public final int aegisAfter;

        public Result(boolean dodged, boolean blocked, double finalDamage,
                      int aegisBefore, int aegisAfter) {
            this.dodged = dodged;
            this.blocked = blocked;
            this.finalDamage = finalDamage;
            this.aegisBefore = aegisBefore;
            this.aegisAfter = aegisAfter;
        }

        public static Result dodged() {
            return new Result(true, false, 0, 0, 0);
        }

        public static Result passthrough(double damage) {
            return new Result(false, false, Math.max(0, damage), 0, 0);
        }

        public boolean aegisChanged() {
            return aegisBefore != aegisAfter;
        }
    }
}
