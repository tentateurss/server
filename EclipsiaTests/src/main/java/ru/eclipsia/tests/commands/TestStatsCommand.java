package ru.eclipsia.tests.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /teststats — выдача всех статов для тестирования RPG-системы.
 *
 * <p>Подкоманды:
 * <ul>
 *   <li>{@code /teststats max} — выставить 999 во все известные ключи статов.</li>
 *   <li>{@code /teststats addall <amount>} — добавить amount ко всем статам.</li>
 *   <li>{@code /teststats set <key> <value>} — выставить точное значение.</li>
 *   <li>{@code /teststats reset} — обнулить ВСЕ статы профиля.</li>
 *   <li>{@code /teststats show} — распечатать в чат текущие статы профиля.</li>
 * </ul>
 *
 * <p>После любой мутации сразу вызывается {@code StatsBonusApplier}, чтобы
 * атрибуты Bukkit (HP, ATTACK_DAMAGE) и Эгида пересчитались моментально.
 */
public class TestStatsCommand implements CommandExecutor {

    /** Полный набор ключей, которые умеет двигать ванильный игровой контур. */
    private static final String[] ALL_KEYS = new String[]{
            StatKeys.STRENGTH, StatKeys.DEXTERITY, StatKeys.INTELLIGENCE,
            StatKeys.HEALTH_BONUS, StatKeys.MANA_BONUS, StatKeys.AEGIS,
            StatKeys.HEALTH_REGEN, StatKeys.MANA_REGEN, StatKeys.AEGIS_REGEN,
            StatKeys.AEGIS_DELAY,
            StatKeys.PHYSICAL_DAMAGE, StatKeys.FIRE_DAMAGE,
            StatKeys.COLD_DAMAGE, StatKeys.LIGHTNING_DAMAGE,
            StatKeys.ATTACK_SPEED, StatKeys.MOVE_SPEED,
            StatKeys.CRIT_CHANCE, StatKeys.CRIT_DAMAGE,
            StatKeys.EVASION, StatKeys.BLOCK_CHANCE, StatKeys.BLOCK_AMOUNT,
            StatKeys.ARMOUR,
            StatKeys.FIRE_RESIST, StatKeys.COLD_RESIST, StatKeys.LIGHTNING_RESIST
    };

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько в игре");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile profile = api.getActiveProfile(player);
        if (profile == null) {
            player.sendMessage("§cУ тебя нет активного профиля. Создай персонажа сначала.");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "max" -> {
                Map<String, Integer> next = new LinkedHashMap<>(profile.getStats());
                for (String k : ALL_KEYS) next.put(k, 999);
                applyAndNotify(player, profile, next, "Все статы → §e999");
            }
            case "addall" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/teststats addall <amount>");
                    return true;
                }
                int amount;
                try { amount = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    player.sendMessage("§cНе число: " + args[1]);
                    return true;
                }
                Map<String, Integer> next = new LinkedHashMap<>(profile.getStats());
                for (String k : ALL_KEYS) next.merge(k, amount, Integer::sum);
                applyAndNotify(player, profile, next, "+" + amount + " ко всем статам");
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage("§e/teststats set <key> <value>");
                    player.sendMessage("§7Ключи: §f" + String.join(", ", ALL_KEYS));
                    return true;
                }
                String key = args[1].toLowerCase();
                int value;
                try { value = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) {
                    player.sendMessage("§cНе число: " + args[2]);
                    return true;
                }
                Map<String, Integer> next = new LinkedHashMap<>(profile.getStats());
                next.put(key, value);
                applyAndNotify(player, profile, next, key + " = " + value);
            }
            case "reset" -> {
                applyAndNotify(player, profile, new LinkedHashMap<>(),
                        "Все статы обнулены");
            }
            case "show" -> {
                player.sendMessage("§6═══ Статы профиля ═══");
                Map<String, Integer> stats = profile.getStats();
                if (stats.isEmpty()) {
                    player.sendMessage("§7(пусто)");
                } else {
                    stats.forEach((k, v) -> player.sendMessage("§7" + k + ": §f" + v));
                }
                player.sendMessage("§6════════════════════");
            }
            default -> help(player);
        }
        return true;
    }

    private void applyAndNotify(Player player, PlayerProfile profile,
                                Map<String, Integer> nextStats, String msg) {
        PlayerProfile updated = profile.toBuilder().stats(nextStats).build();
        EclipsiaAPI.getInstance().updateProfile(player, updated);
        try {
            ru.eclipsia.core.stats.StatsBonusApplier.applyAllBonuses(player);
        } catch (Throwable ignored) {
        }
        player.sendMessage("§a✓ " + msg + " §7(применено мгновенно)");
    }

    private void help(Player p) {
        p.sendMessage("§6═══ /teststats ═══");
        p.sendMessage("§e/teststats max §7— все статы на 999");
        p.sendMessage("§e/teststats addall <n> §7— +n ко всем");
        p.sendMessage("§e/teststats set <key> <val> §7— конкретное значение");
        p.sendMessage("§e/teststats reset §7— обнулить все");
        p.sendMessage("§e/teststats show §7— показать текущие");
    }
}
