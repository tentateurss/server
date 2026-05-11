package ru.eclipsia.hud.api;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import ru.eclipsia.core.combat.DamageType;
import ru.eclipsia.hud.bossbar.BossBarRegistry;
import ru.eclipsia.hud.floatlabel.FloatingLabelService;
import ru.eclipsia.hud.floatlabel.LabelHandle;
import ru.eclipsia.hud.sidebar.SidebarService;
import ru.eclipsia.hud.tablist.TabListService;
import ru.eclipsia.hud.title.TitleCinematicService;

/**
 * Публичный API EclipsiaHUD. Другие плагины зовут отсюда.
 *
 * <p>Пример вызова из EclipsiaMobs при повышении уровня:
 * <pre>{@code
 * EclipsiaHUDAPI api = EclipsiaHUDAPI.getInstance();
 * if (api != null) {
 *     api.showLevelUp(player, newLevel);
 * }
 * }</pre>
 *
 * <p>Если EclipsiaHUD не установлен, метод просто возвращает {@code null} —
 * вызывающий код проверяет на null и продолжает работать как раньше.
 * Это сохраняет «мягкую» зависимость: HUD можно отключить без падения сервера.
 */
public final class EclipsiaHUDAPI {

    private static volatile EclipsiaHUDAPI instance;

    private final SidebarService sidebar;
    private final TabListService tablist;
    private final BossBarRegistry bossbars;
    private final TitleCinematicService titles;
    private final FloatingLabelService labels;

    public EclipsiaHUDAPI(SidebarService sidebar,
                          TabListService tablist,
                          BossBarRegistry bossbars,
                          TitleCinematicService titles,
                          FloatingLabelService labels) {
        this.sidebar = sidebar;
        this.tablist = tablist;
        this.bossbars = bossbars;
        this.titles = titles;
        this.labels = labels;
    }

    /** Зарегистрировать API инстанс (вызов из {@code EclipsiaHUD#onEnable()}). */
    public static void register(EclipsiaHUDAPI api) {
        instance = api;
    }

    /** Снять регистрацию (вызов из {@code onDisable()}). */
    public static void unregister() {
        instance = null;
    }

    /**
     * @return активный инстанс или {@code null}, если EclipsiaHUD выключен.
     */
    public static EclipsiaHUDAPI getInstance() {
        return instance;
    }

    // ====== TITLES ======

    /** Кинематик «Повышение уровня». */
    public void showLevelUp(Player player, int newLevel) {
        titles.showLevelUp(player, newLevel);
    }

    /** Кинематик «Босс пробуждается». */
    public void showBossSpawn(Player player, String bossName) {
        titles.showBossSpawn(player, bossName);
    }

    /** Кинематик «Вход в регион» — обычно вызывается RegionEnterListener'ом. */
    public void showRegionEnter(Player player, Component regionName) {
        titles.showRegionEnter(player, regionName);
    }

    /** Welcome-title при подключении. */
    public void showWelcome(Player player) {
        titles.showWelcome(player);
    }

    // ====== SIDEBAR ======

    /** Включить/выключить sidebar для конкретного игрока. */
    public void setSidebarVisible(Player player, boolean visible) {
        sidebar.setVisible(player, visible);
    }

    public boolean isSidebarVisible(Player player) {
        return sidebar.isVisible(player);
    }

    // ====== TABLIST ======

    public void setTabListVisible(Player player, boolean visible) {
        tablist.setVisible(player, visible);
    }

    // ====== BOSS BAR REGISTRY ======

    /**
     * Показать/обновить per-player bossbar по ключу. Если бара с этим ключом
     * нет — он создаётся и показывается. Если есть — обновляется в месте.
     */
    public void showBossBar(Player player, String key, BossBar bar) {
        bossbars.show(player, key, bar);
    }

    /** Скрыть bossbar по ключу. */
    public void hideBossBar(Player player, String key) {
        bossbars.hide(player, key);
    }

    /** Получить bossbar по ключу (или null). */
    public BossBar getBossBar(Player player, String key) {
        return bossbars.get(player, key);
    }

    // ====== FLOATING LABELS ======

    /** Спавн плавающей метки в координате. TTL в тиках; {@code 0} = бессрочно. */
    public LabelHandle spawnLabel(Location location, Component text, int ttlTicks) {
        return labels.spawn(location, text, ttlTicks);
    }

    /** Спавн метки, привязанной к сущности (NPC / босс / точка интереса). */
    public LabelHandle spawnLabelOn(LivingEntity entity, Component text, int ttlTicks) {
        return labels.spawnOn(entity, text, ttlTicks);
    }

    /** Снять все плавающие метки. Полезно для команды /hud labels clear. */
    public int clearAllLabels() {
        return labels.clearAll();
    }

    // ====== DAMAGE NUMBERS ======

    /**
     * Показать всплывающую цифру урона над целью.
     *
     * <p>Реальный рендер выбирается из config.yml (legacy ArmorStand
     * через {@code DamageDisplay} в EclipsiaCore либо новый TextDisplay
     * через {@code ModernDamageDisplay} в этом модуле).
     *
     * <p>Совместимо со старыми вызовами {@code DamageDisplay.show(...)} —
     * можно либо мигрировать их на этот метод, либо оставить как есть:
     * legacy-режим продолжит работать.
     */
    public void showDamage(LivingEntity entity, double damage, DamageType type) {
        ru.eclipsia.hud.damage.ModernDamageDisplay.show(entity, damage, type);
    }
}
