package ru.eclipsia.hud.region;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import ru.eclipsia.hud.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Конфиг-реестр регионов для title-эффекта «вход в зону».
 *
 * <p>До этого PR логика жила в {@code EclipsiaCore/listener/RegionTitleListener}
 * с хардкодом миров {@code world}/{@code beach}, которых уже нет в репо
 * (удалены в PR #51). Здесь делаем то же самое, но через данные:
 * редактирование зон не требует пересборки и работает в любом мире.
 *
 * <p>Формат записи в {@code config.yml}:
 * <pre>
 * regions:
 *   list:
 *     - id: spawn
 *       world: dev_flat
 *       x1: -16
 *       z1: -16
 *       x2: 16
 *       z2: 16
 *       name: "&lt;gold&gt;Spawn&lt;/gold&gt;"
 * </pre>
 *
 * <p>Сравнение AABB — синхронное, без аллокаций, безопасно для
 * вызова {@code PlayerMoveEvent} c условием «только при смене блока».
 */
public final class RegionRegistry {

    /** Описание одной зоны. */
    public record Region(String id, String world, int minX, int minZ, int maxX, int maxZ,
                         Component name) {
        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!Objects.equals(loc.getWorld().getName(), world)) return false;
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private final List<Region> regions = new ArrayList<>();

    public RegionRegistry(ConfigurationSection cfg) {
        load(cfg);
    }

    public void reload(ConfigurationSection cfg) {
        regions.clear();
        load(cfg);
    }

    private void load(ConfigurationSection cfg) {
        if (cfg == null) return;
        List<Map<?, ?>> list = cfg.getMapList("list");
        for (Map<?, ?> raw : list) {
            try {
                String id = String.valueOf(raw.get("id"));
                String world = String.valueOf(raw.get("world"));
                int x1 = ((Number) raw.get("x1")).intValue();
                int z1 = ((Number) raw.get("z1")).intValue();
                int x2 = ((Number) raw.get("x2")).intValue();
                int z2 = ((Number) raw.get("z2")).intValue();
                int minX = Math.min(x1, x2);
                int maxX = Math.max(x1, x2);
                int minZ = Math.min(z1, z2);
                int maxZ = Math.max(z1, z2);
                Object rawName = raw.get("name");
                String nameStr = rawName == null ? id : String.valueOf(rawName);
                Component name = Theme.mm(nameStr);
                regions.add(new Region(id, world, minX, minZ, maxX, maxZ, name));
            } catch (Throwable t) {
                // одна битая запись — не валим весь реестр
                continue;
            }
        }
    }

    /** @return регион, в котором находится локация, либо {@code null}. */
    public Region resolve(Location location) {
        if (location == null) return null;
        for (Region r : regions) {
            if (r.contains(location)) return r;
        }
        return null;
    }

    public int size() {
        return regions.size();
    }
}
