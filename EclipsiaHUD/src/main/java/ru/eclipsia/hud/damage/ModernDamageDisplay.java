package ru.eclipsia.hud.damage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import ru.eclipsia.core.combat.DamageType;
import ru.eclipsia.hud.theme.Theme;

/**
 * Современная версия {@code DamageDisplay} (EclipsiaCore) на {@link TextDisplay}
 * вместо ArmorStand.
 *
 * <p>Преимущества над ArmorStand:
 * <ul>
 *   <li>цвет/жирность задаются Component-ом, не legacy §-кодами;</li>
 *   <li>billboard сам поворачивает цифру к игроку;</li>
 *   <li>можно отмасштабировать шрифт (полезно для крита: 1.4× смотрится сильнее);</li>
 *   <li>фон полупрозрачный — цифра не теряется на ярких текстурах.</li>
 * </ul>
 *
 * <p>Старый {@code DamageDisplay} НЕ удаляется этим PR — он работает,
 * и переключение происходит через {@code damage-numbers.mode} в config.yml:
 * {@code legacy} / {@code modern} / {@code both}. Совместимый API позволит
 * мигрировать вызовы пошагово.
 */
public final class ModernDamageDisplay {

    private static volatile Plugin plugin;
    private static volatile ConfigurationSection cfg;

    private ModernDamageDisplay() { /* utility */ }

    public static void init(Plugin owner, ConfigurationSection config) {
        plugin = owner;
        cfg = config;
    }

    /**
     * Показать число урона над сущностью.
     *
     * <p>Маршрутизация:
     * <ul>
     *   <li>{@code mode=legacy} → делегирует в {@link ru.eclipsia.core.combat.DamageDisplay#show};</li>
     *   <li>{@code mode=modern} → TextDisplay-рендер;</li>
     *   <li>{@code mode=both} → оба (для A/B).</li>
     * </ul>
     */
    public static void show(LivingEntity entity, double damage, DamageType type) {
        if (plugin == null || cfg == null || entity == null || entity.isDead()) return;
        if (damage < 0.5) return;
        if (!cfg.getBoolean("enabled", true)) return;

        String mode = cfg.getString("mode", "modern");
        if ("legacy".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            ru.eclipsia.core.combat.DamageDisplay.show(entity, damage, type);
        }
        if ("modern".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            spawnTextDisplay(entity, damage, type);
        }
    }

    private static void spawnTextDisplay(LivingEntity entity, double damage, DamageType type) {
        int ttl = Math.max(5, cfg.getInt("ttl-ticks", 30));
        double scale = cfg.getDouble("scale", 0.8);
        double scatter = Math.max(0.0, cfg.getDouble("scatter-radius", 0.7));

        // позиция чуть выше головы + рандомный сдвиг
        Location loc = entity.getLocation().add(
                (Math.random() - 0.5) * scatter,
                entity.getHeight() + 0.4,
                (Math.random() - 0.5) * scatter
        );

        // Текст: для крита — жирный + восклицательный, для прочих — обычный.
        boolean isCrit = (type == DamageType.CRIT);
        Component base = Component.text(formatDamage(damage), Theme.damageColor(type));
        final Component text = isCrit
                ? base.decorate(TextDecoration.BOLD)
                      .append(Component.text("!", Theme.damageColor(DamageType.CRIT)))
                : base;

        try {
            TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, e -> {
                e.text(text);
                e.setBillboard(Billboard.CENTER);
                e.setShadowed(true);
                e.setDefaultBackground(false);
                // прозрачный фон с лёгкой подложкой для читаемости
                e.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));

                float s = (float) (isCrit ? scale * 1.4 : scale);
                Transformation tf = e.getTransformation();
                tf.getScale().set(s, s, s);
                e.setTransformation(tf);
            });

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (td.isValid()) td.remove();
            }, ttl);
        } catch (Throwable t) {
            plugin.getLogger().warning("ModernDamageDisplay spawn failed: " + t.getMessage());
        }
    }

    private static String formatDamage(double damage) {
        // Целые показываем без точки, дробные округляем до 0.1
        if (Math.abs(damage - Math.rint(damage)) < 0.05) {
            return String.valueOf((long) Math.round(damage));
        }
        return String.format(java.util.Locale.US, "%.1f", damage);
    }
}
