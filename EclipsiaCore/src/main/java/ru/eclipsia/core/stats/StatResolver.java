package ru.eclipsia.core.stats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.data.PlayerProfile;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Универсальный аггрегатор RPG-статов для DamageCalculator, регена и HUD.
 *
 * <p>Объединяет три источника:
 * <ol>
 *   <li>{@link PlayerProfile#getStat(String)} — базовые статы класса
 *       (strength/dexterity/intelligence) и всё, что туда писали другие
 *       подсистемы (например, перенесённые бонусы при сохранении).</li>
 *   <li><b>Экипировка</b> — лор предметов парсится в
 *       {@code ru.eclipsia.items.equipment.EquipmentBonusApplier#getStatBonus}.
 *       Резолвится через рефлексию, чтобы EclipsiaCore не зависел от
 *       EclipsiaItems напрямую.</li>
 *   <li><b>Перки</b> — суммирование {@code stats} всех изученных узлов в
 *       {@code ru.eclipsia.perks.player.PlayerPerkData}. Тоже рефлексия,
 *       поскольку перки опциональны и грузятся позже Core.</li>
 * </ol>
 *
 * <p>Класс полностью thread-safe (никакого мутабельного состояния), но методы
 * читают плагины, поэтому корректнее всего вызывать его из основного потока.
 *
 * <p>Дешевизна: рефлексивные дескрипторы кэшируются один раз; если плагины
 * не загружены — методы возвращают 0.
 */
public final class StatResolver {

    private StatResolver() { /* utility */ }

    // ----- Кэш доступа к EclipsiaItems#EquipmentBonusApplier#getStatBonus -----
    private static volatile Method itemsGetStatBonus;
    private static volatile boolean itemsResolveFailed = false;

    // ----- Кэш доступа к EclipsiaPerks#getStatBonus(uuid, key) -----
    private static volatile Object perksPlugin;
    private static volatile Method perksGetStatBonus;
    private static volatile boolean perksResolveFailed = false;

    /**
     * Сумма всех источников по одному ключу для конкретного игрока.
     *
     * @param player  объект игрока (может быть null если оффлайн)
     * @param profile активный профиль (может быть null)
     * @param key     ключ стата (см. {@link ru.eclipsia.core.data.StatKeys})
     */
    public static int total(Player player, PlayerProfile profile, String key) {
        if (key == null) return 0;
        int v = 0;
        if (profile != null) v += profile.getStat(key);
        if (player != null)  v += equipment(player, key);
        if (player != null)  v += perks(player.getUniqueId(), key);
        return v;
    }

    /** То же что {@link #total(Player, PlayerProfile, String)}, но достаёт профиль сам. */
    public static int total(Player player, String key) {
        if (player == null || key == null) return 0;
        ru.eclipsia.core.api.EclipsiaAPI api = ru.eclipsia.core.api.EclipsiaAPI.getInstance();
        PlayerProfile profile = api == null ? null : api.getActiveProfile(player);
        return total(player, profile, key);
    }

    /** Все статы (read-only snapshot). Объединяет ключи из всех трёх источников. */
    public static Map<String, Integer> totals(Player player, PlayerProfile profile) {
        Map<String, Integer> out = new HashMap<>();
        if (profile != null) {
            for (Map.Entry<String, Integer> e : profile.getStats().entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        if (player != null) {
            Map<String, Integer> eq = equipmentAll(player);
            for (Map.Entry<String, Integer> e : eq.entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            Map<String, Integer> pk = perksAll(player.getUniqueId());
            for (Map.Entry<String, Integer> e : pk.entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return out;
    }

    // =========================================================================
    // EQUIPMENT
    // =========================================================================

    public static int equipment(Player player, String key) {
        if (player == null || key == null) return 0;
        Method m = resolveItemsMethod();
        if (m == null) return 0;
        try {
            Object res = m.invoke(null, player, key);
            return res instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Integer> equipmentAll(Player player) {
        if (player == null) return new HashMap<>();
        try {
            Plugin items = Bukkit.getPluginManager().getPlugin("EclipsiaItems");
            if (items == null) return new HashMap<>();
            Class<?> applier = Class.forName("ru.eclipsia.items.equipment.EquipmentBonusApplier",
                    true, items.getClass().getClassLoader());
            Method m = applier.getMethod("getAllBonuses", Player.class);
            Object res = m.invoke(null, player);
            if (res instanceof Map<?, ?> map) {
                Map<String, Integer> out = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() instanceof String k && e.getValue() instanceof Integer v) {
                        out.put(k, v);
                    }
                }
                return out;
            }
        } catch (Throwable t) {
            // ignore
        }
        return new HashMap<>();
    }

    private static Method resolveItemsMethod() {
        if (itemsGetStatBonus != null) return itemsGetStatBonus;
        if (itemsResolveFailed) return null;
        try {
            Plugin items = Bukkit.getPluginManager().getPlugin("EclipsiaItems");
            if (items == null) return null;
            Class<?> applier = Class.forName("ru.eclipsia.items.equipment.EquipmentBonusApplier",
                    true, items.getClass().getClassLoader());
            itemsGetStatBonus = applier.getMethod("getStatBonus", Player.class, String.class);
            return itemsGetStatBonus;
        } catch (Throwable t) {
            itemsResolveFailed = true;
            return null;
        }
    }

    // =========================================================================
    // PERKS
    // =========================================================================

    public static int perks(UUID uuid, String key) {
        if (uuid == null || key == null) return 0;
        Method m = resolvePerksMethod();
        if (m == null) return 0;
        try {
            Object res = m.invoke(perksPlugin, uuid, key);
            return res instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Integer> perksAll(UUID uuid) {
        if (uuid == null) return new HashMap<>();
        try {
            Plugin perks = Bukkit.getPluginManager().getPlugin("EclipsiaPerks");
            if (perks == null) return new HashMap<>();
            Method m = perks.getClass().getMethod("getAllPerkStats", UUID.class);
            Object res = m.invoke(perks, uuid);
            if (res instanceof Map<?, ?> map) {
                Map<String, Integer> out = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() instanceof String k && e.getValue() instanceof Integer v) {
                        out.put(k, v);
                    }
                }
                return out;
            }
        } catch (Throwable t) {
            // ignore
        }
        return new HashMap<>();
    }

    private static Method resolvePerksMethod() {
        if (perksGetStatBonus != null) return perksGetStatBonus;
        if (perksResolveFailed) return null;
        try {
            Plugin perks = Bukkit.getPluginManager().getPlugin("EclipsiaPerks");
            if (perks == null) return null;
            perksPlugin = perks;
            perksGetStatBonus = perks.getClass().getMethod("getPerkStat", UUID.class, String.class);
            return perksGetStatBonus;
        } catch (Throwable t) {
            perksResolveFailed = true;
            return null;
        }
    }

    /** Сбросить кэш рефлексии (на /reload). */
    public static void invalidateCache() {
        itemsGetStatBonus = null;
        itemsResolveFailed = false;
        perksPlugin = null;
        perksGetStatBonus = null;
        perksResolveFailed = false;
    }
}
