package ru.eclipsia.hud.floatlabel;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.UUID;

/**
 * Управляемая «ручка» для плавающей метки (TextDisplay).
 *
 * <p>Спавн делается через {@link FloatingLabelService#spawn}, потом метку
 * можно двигать/перекрашивать/удалять руками. TTL обрабатывается в сервисе.
 */
public final class LabelHandle {

    private final UUID entityUuid;

    LabelHandle(UUID uuid) {
        this.entityUuid = uuid;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    /** Получить живой TextDisplay (или {@code null}, если уже удалён). */
    public TextDisplay entity() {
        Entity e = Bukkit.getEntity(entityUuid);
        return (e instanceof TextDisplay td && td.isValid()) ? td : null;
    }

    public boolean isAlive() {
        return entity() != null;
    }

    /** Сменить текст метки. */
    public void setText(Component text) {
        TextDisplay td = entity();
        if (td != null) td.text(text);
    }

    /** Сдвинуть метку (например, прицепить к движущемуся NPC). */
    public void move(Location location) {
        TextDisplay td = entity();
        if (td != null && location != null) td.teleport(location);
    }

    /** Снять метку немедленно. */
    public void remove() {
        TextDisplay td = entity();
        if (td != null) td.remove();
    }
}
