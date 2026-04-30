package ru.eclipsia.builder.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Утилита для размещения «парящих надписей» (TextDisplay) в мире.
 *
 * <p>По сути — обёртка над Paper {@link TextDisplay}: ставит сущность
 * с многострочным текстом, помечает её PDC-ключом плагина и умеет
 * массово удалять собственные надписи (для регенерации мира).
 *
 * <p>Идемпотентность достигается тем, что перед спавном новой надписи
 * мы убираем все TextDisplay-сущности с тем же {@link #FT_KEY}-PDC
 * в радиусе 0.6 блока — повторная генерация не наплодит дубликатов.
 *
 * <p>Используется {@code WorldGenerator} (надписи на воротах города,
 * указатели на дорогах, вывески домов). {@code BeachGenerator} имеет
 * свой собственный {@code spawnHologram} и сюда сознательно не
 * мигрирован, чтобы не трогать рабочий код Берега.
 */
public final class FloatingText {

    /** Все надписи, поставленные через эту утилиту, помечаются этим PDC-ключом. */
    private static final String FT_KEY_PATH = "eclipsia_floating_text";

    private FloatingText() {
        // utility, no instances
    }

    /**
     * Базовый метод: поставить TextDisplay в указанной точке.
     *
     * <p>Перед спавном удаляет другие FloatingText-надписи в радиусе 0.6 блока.
     * Многострочный текст разделяется переводом строки.
     *
     * @return ссылка на созданную сущность (не {@code null}).
     */
    public static TextDisplay create(Plugin plugin, World world,
                                     double x, double y, double z,
                                     String... lines) {
        if (plugin == null || world == null) {
            throw new IllegalArgumentException("plugin/world не должны быть null");
        }
        Location loc = new Location(world, x, y, z);
        removeNearby(plugin, world, loc, 0.6);

        TextDisplay td = world.spawn(loc, TextDisplay.class);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        td.text(Component.text(sb.toString()));
        td.setBillboard(Display.Billboard.CENTER);
        td.setBackgroundColor(Color.fromARGB(180, 0, 0, 0));
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setPersistent(true);
        try {
            td.setBrightness(new Display.Brightness(8, 6));
        } catch (Throwable ignored) {
            // старые версии Paper без Brightness API
        }
        // Помечаем своим PDC-ключом, чтобы потом уметь массово удалять.
        td.getPersistentDataContainer().set(
                key(plugin), PersistentDataType.BYTE, (byte) 1);
        return td;
    }

    /**
     * Двухстрочная «вывеска места»: жирный заголовок + подзаголовок.
     * Используется для названий локаций (городские ворота, мельница, озеро).
     */
    public static TextDisplay createLocationTitle(Plugin plugin, World world,
                                                  double x, double y, double z,
                                                  String title, String subtitle) {
        return create(plugin, world, x, y, z, title, subtitle);
    }

    /**
     * Одно-строчная вывеска (например, у дома: «Таверна»).
     */
    public static TextDisplay createSign(Plugin plugin, World world,
                                         double x, double y, double z,
                                         String line) {
        return create(plugin, world, x, y, z, line);
    }

    /**
     * Удалить ВСЕ FloatingText-надписи (помеченные этой утилитой) в указанном
     * мире. Используется при принудительной регенерации.
     *
     * @return количество удалённых сущностей.
     */
    public static int removeAll(Plugin plugin, World world) {
        if (plugin == null || world == null) return 0;
        NamespacedKey key = key(plugin);
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                td.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Удалить FloatingText в радиусе вокруг точки (внутренняя дедупликация). */
    private static void removeNearby(Plugin plugin, World world,
                                     Location loc, double radius) {
        NamespacedKey key = key(plugin);
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                td.remove();
            }
        }
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, FT_KEY_PATH);
    }
}
