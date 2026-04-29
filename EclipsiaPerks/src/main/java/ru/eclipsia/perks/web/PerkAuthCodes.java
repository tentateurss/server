package ru.eclipsia.perks.web;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player 6-значные коды авторизации для web-дерева перков.
 *
 * <p>Игрок логинится в web по паре {@code nickname + code}. Никнейм
 * резолвится через {@code Bukkit.getOfflinePlayer(name)}, код — генерируется
 * сервером при первом обращении и держится в памяти до перезапуска.
 *
 * <p>В сетевом протоколе передаётся ровно 6 цифр (000000…999999), padding
 * нулями. Этого достаточно: пара {nick,code} рандомизирована, попытки
 * подбора в realtime ограничены сетевым RTT, а ставки — только перки одного
 * персонажа Minecraft. Если хочется hardening, можно ограничить /api/perks/auth
 * в reverse-proxy.
 *
 * <p>Если игрок забыл/не может найти код — пусть выйдет и зайдёт снова, либо
 * выполнит {@code /perkscode} (сгенерируется заново и придёт в чат).
 */
public final class PerkAuthCodes {

    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<UUID, Integer> CODES = new ConcurrentHashMap<>();

    private PerkAuthCodes() { /* utility */ }

    /** Получить (или создать) 6-значный код игрока. */
    public static int getOrCreate(UUID uuid) {
        return CODES.computeIfAbsent(uuid, k -> RNG.nextInt(1_000_000));
    }

    /** Принудительно сбросить код игрока (например, после /perkscode). */
    public static int regenerate(UUID uuid) {
        int code = RNG.nextInt(1_000_000);
        CODES.put(uuid, code);
        return code;
    }

    /** Проверить пару (uuid, code). */
    public static boolean verify(UUID uuid, int code) {
        Integer expected = CODES.get(uuid);
        return expected != null && expected.intValue() == code;
    }

    /** Удалить код при выходе игрока (по желанию). */
    public static void forget(UUID uuid) {
        CODES.remove(uuid);
    }

    /** Форматировать код с лидирующими нулями. */
    public static String format(int code) {
        return String.format("%06d", code);
    }
}
