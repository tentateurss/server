package ru.eclipsia.core.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event, fired когда игрок впервые выбрал класс через /class GUI.
 *
 * Подписчики из других модулей (EclipsiaPerks → стартовый узел,
 * EclipsiaSkills → стартовый навык-эклипс, EclipsiaItems → стартовый
 * комплект) могут выдать игроку контент, специфичный классу.
 */
public class ClassSelectedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String classId;

    public ClassSelectedEvent(Player player, String classId) {
        this.player = player;
        this.classId = classId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getClassId() {
        return classId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
