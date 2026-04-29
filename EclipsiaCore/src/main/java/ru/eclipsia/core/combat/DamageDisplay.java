package ru.eclipsia.core.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Всплывающие цифры урона над целью.
 *
 * <p>Реализовано на невидимых ArmorStand-маркерах (как делают почти все
 * RPG-плагины Bukkit). Стенд-маркер не имеет коллизии, не тикается, не
 * влияет на физику — лёгкий и безопасный. Удаляется через 1.5 секунды.
 *
 * <p>Цвет цифры зависит от типа урона ({@link DamageType}). Крит
 * показывается жирным красным с восклицательным знаком.
 */
public final class DamageDisplay {

    private static volatile Plugin plugin;
    private static volatile boolean enabled = true;

    private DamageDisplay() { /* utility */ }

    /**
     * Привязать к плагину для шедулинга удаления стендов. Вызвать один раз
     * из {@code onEnable()} EclipsiaCore.
     */
    public static void init(Plugin owner) {
        plugin = owner;
    }

    /** Глобально включить/выключить визуал — на случай конфликтов с другими плагинами. */
    public static void setEnabled(boolean on) { enabled = on; }

    /**
     * Показать число урона над {@code entity}.
     *
     * @param entity цель (моб или игрок)
     * @param damage итоговый урон (после всех защит)
     * @param type   тип для подкраски
     */
    public static void show(LivingEntity entity, double damage, DamageType type) {
        if (!enabled || plugin == null || entity == null || entity.isDead()) return;
        if (damage < 0.5) return; // микро-урон не спамим — иначе при иммунити-фрейме мусор лезет.

        String text;
        if (type == DamageType.CRIT) {
            text = String.format(Locale.US, "%s%.0f§c§l!", type.getColor(), damage);
        } else {
            text = String.format(Locale.US, "%s%.0f", type.getColor(), damage);
        }

        // Слегка случайный сдвиг, чтобы цифры от нескольких попаданий не
        // накладывались на одной координате и можно было прочитать каждую.
        Location loc = entity.getLocation().add(
                (Math.random() - 0.5) * 1.4,
                entity.getHeight() + 0.4,
                (Math.random() - 0.5) * 1.4
        );

        try {
            ArmorStand stand = (ArmorStand) entity.getWorld()
                    .spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(text);
            stand.setCanPickupItems(false);
            // setCanTick(false) уменьшает нагрузку — стенд не нужен в логике мира.
            try {
                stand.setCanTick(false);
            } catch (NoSuchMethodError ignored) {
                // на старых Paper нет — не критично.
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (stand.isValid()) stand.remove();
            }, 30L); // 30 тиков ≈ 1.5 секунды
        } catch (Throwable t) {
            // Любой сбой спавна — игнорируем, чтобы не валить боевой пайплайн.
            plugin.getLogger().warning("DamageDisplay spawn failed: " + t.getMessage());
        }
    }
}
