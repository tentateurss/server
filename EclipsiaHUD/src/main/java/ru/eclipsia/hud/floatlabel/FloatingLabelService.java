package ru.eclipsia.hud.floatlabel;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Плавающие метки на базе {@link TextDisplay} (Minecraft 1.19.4+).
 *
 * <p>Это современная альтернатива ArmorStand-меткам:
 * <ul>
 *   <li>не имеют коллизии и hitbox'а,</li>
 *   <li>не тикаются как сущности в смысле AI,</li>
 *   <li>billboard-режим: всегда повёрнуты к игроку,</li>
 *   <li>поддерживают Component с цветами/decorations напрямую,</li>
 *   <li>можно задать прозрачный/полупрозрачный фон и масштаб шрифта.</li>
 * </ul>
 *
 * <p>Старый {@code DamageDisplay} из EclipsiaCore остаётся как fallback —
 * этот сервис ни одного его метода не трогает. Цифры урона в HUD теперь
 * рендерятся через отдельный {@code ModernDamageDisplay} (того же модуля).
 */
public final class FloatingLabelService {

    private final Plugin plugin;
    private final ConfigurationSection cfg;

    /** Все spawned labels, чтобы можно было очистить всё разом. */
    private final Set<UUID> alive = new HashSet<>();

    public FloatingLabelService(Plugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    /** @return true если функционал включён в config.yml. */
    public boolean isEnabled() {
        return cfg.getBoolean("enabled", true);
    }

    /**
     * Спавн метки в координате.
     *
     * @param location где появится метка
     * @param text     уже готовый Component
     * @param ttlTicks через сколько тиков убрать. {@code 0} = «навсегда».
     */
    public LabelHandle spawn(Location location, Component text, int ttlTicks) {
        if (!isEnabled() || location == null || location.getWorld() == null) return null;
        return doSpawn(location, text, ttlTicks);
    }

    /**
     * Спавн метки над сущностью. Метка статична по координате — если NPC
     * двигается, её придётся двигать вручную через {@link LabelHandle#move}.
     */
    public LabelHandle spawnOn(LivingEntity entity, Component text, int ttlTicks) {
        if (!isEnabled() || entity == null) return null;
        Location at = entity.getLocation().add(0, entity.getHeight() + 0.35, 0);
        return doSpawn(at, text, ttlTicks);
    }

    private LabelHandle doSpawn(Location at, Component text, int ttlTicks) {
        World world = at.getWorld();
        if (world == null) return null;

        int effectiveTtl = ttlTicks <= 0
                ? cfg.getInt("default-ttl-ticks", 60)
                : ttlTicks;
        boolean seeThrough = cfg.getBoolean("see-through-walls", true);
        double scale = cfg.getDouble("default-scale", 1.0);
        Color background = parseRgba(cfg.getString("default-background", "00000040"));

        TextDisplay td;
        try {
            td = world.spawn(at, TextDisplay.class, e -> {
                e.text(text == null ? Component.empty() : text);
                e.setBillboard(Billboard.CENTER);
                e.setSeeThrough(seeThrough);
                e.setShadowed(false);
                e.setDefaultBackground(false);
                if (background != null) e.setBackgroundColor(background);
                // масштаб шрифта через Transformation
                if (Math.abs(scale - 1.0) > 0.001) {
                    Transformation tf = e.getTransformation();
                    tf.getScale().set((float) scale, (float) scale, (float) scale);
                    e.setTransformation(tf);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("FloatingLabelService spawn failed: " + t.getMessage());
            return null;
        }

        UUID uuid = td.getUniqueId();
        alive.add(uuid);
        // TTL ≠ 0 — планируем удаление
        if (ttlTicks != 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (td.isValid()) td.remove();
                alive.remove(uuid);
            }, effectiveTtl);
        }
        return new LabelHandle(uuid);
    }

    /** Снять все живые метки. */
    public int clearAll() {
        int n = 0;
        for (UUID uuid : alive) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(uuid);
            if (e instanceof TextDisplay td && td.isValid()) {
                td.remove();
                n++;
            }
        }
        alive.clear();
        return n;
    }

    public void shutdown() {
        clearAll();
    }

    /**
     * Парсит цвет «RRGGBBAA» (8 hex). Возвращает {@code Color} с альфой
     * через {@code Color.fromARGB}. На некорректном входе — {@code null}
     * (TextDisplay будет с прозрачным фоном по дефолту).
     */
    private static Color parseRgba(String hex) {
        if (hex == null || hex.length() < 8) return null;
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            return Color.fromARGB(a, r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
