package ru.eclipsia.hud;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.hud.api.EclipsiaHUDAPI;
import ru.eclipsia.hud.bossbar.BossBarRegistry;
import ru.eclipsia.hud.command.HudCommand;
import ru.eclipsia.hud.damage.ModernDamageDisplay;
import ru.eclipsia.hud.floatlabel.FloatingLabelService;
import ru.eclipsia.hud.listener.JoinWelcomeListener;
import ru.eclipsia.hud.region.RegionEnterListener;
import ru.eclipsia.hud.region.RegionRegistry;
import ru.eclipsia.hud.sidebar.SidebarService;
import ru.eclipsia.hud.tablist.TabListService;
import ru.eclipsia.hud.title.TitleCinematicService;

/**
 * Корневой плагин EclipsiaHUD.
 *
 * <p>Аддитивный модуль — не трогает существующий ActionBar HUD из
 * EclipsiaSkills и XP-bossbar из EclipsiaItems. Добавляет:
 * sidebar / tablist / title-кинематик / floating labels / damage numbers /
 * multi-bossbar реестр / region-enter title с конфиг-реестром.
 *
 * <p>Все сервисы запускаются в {@code onEnable} в строго определённом
 * порядке: сначала «бесшумные» (Theme не нуждается, BossBarRegistry —
 * только Listener), затем периодические (Sidebar/TabList со своими
 * BukkitTask), последним — публичный API.
 */
public final class EclipsiaHUD extends JavaPlugin {

    private static EclipsiaHUD instance;

    private SidebarService sidebar;
    private TabListService tablist;
    private BossBarRegistry bossbars;
    private TitleCinematicService titles;
    private FloatingLabelService labels;
    private RegionRegistry regions;
    private RegionEnterListener regionListener;
    private JoinWelcomeListener welcomeListener;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден — отключаюсь.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        startAll();

        var cmd = getCommand("hud");
        if (cmd != null) {
            HudCommand executor = new HudCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("EclipsiaHUD v" + getDescription().getVersion() + " загружен");
        getLogger().info("Регионов в реестре: " + regions.size());
    }

    @Override
    public void onDisable() {
        stopAll();
        EclipsiaHUDAPI.unregister();
        getLogger().info("EclipsiaHUD выключен");
    }

    public static EclipsiaHUD getInstance() {
        return instance;
    }

    /**
     * Перезагрузить config.yml без рестарта плагина. Останавливает все
     * сервисы, перечитывает конфиг, запускает заново.
     */
    public void reloadEverything() {
        stopAll();
        reloadConfig();
        startAll();
    }

    private void startAll() {
        var cfg = getConfig();
        ConfigurationSection sidebarCfg = section(cfg, "sidebar");
        ConfigurationSection tablistCfg = section(cfg, "tablist");
        ConfigurationSection titlesCfg = section(cfg, "titles");
        ConfigurationSection regionsCfg = section(cfg, "regions");
        ConfigurationSection labelsCfg = section(cfg, "floating-labels");
        ConfigurationSection damageCfg = section(cfg, "damage-numbers");

        bossbars = new BossBarRegistry();
        Bukkit.getPluginManager().registerEvents(bossbars, this);

        titles = new TitleCinematicService(titlesCfg);

        sidebar = new SidebarService(this, sidebarCfg);
        Bukkit.getPluginManager().registerEvents(sidebar, this);
        sidebar.start();

        tablist = new TabListService(this, tablistCfg);
        Bukkit.getPluginManager().registerEvents(tablist, this);
        tablist.start();

        labels = new FloatingLabelService(this, labelsCfg);

        regions = new RegionRegistry(regionsCfg);
        regionListener = new RegionEnterListener(regions);
        Bukkit.getPluginManager().registerEvents(regionListener, this);

        welcomeListener = new JoinWelcomeListener(this, titles);
        Bukkit.getPluginManager().registerEvents(welcomeListener, this);

        ModernDamageDisplay.init(this, damageCfg);

        EclipsiaHUDAPI.register(new EclipsiaHUDAPI(sidebar, tablist, bossbars, titles, labels));
    }

    private void stopAll() {
        if (sidebar != null) sidebar.stop();
        if (tablist != null) tablist.stop();
        if (bossbars != null) bossbars.shutdown();
        if (labels != null) labels.shutdown();
        // listeners unregistered automatically via HandlerList.unregisterAll on disable
    }

    /** Безопасное получение секции — возвращает пустую, если её нет в конфиге. */
    private ConfigurationSection section(org.bukkit.configuration.file.FileConfiguration cfg, String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        if (s != null) return s;
        return cfg.createSection(path);
    }
}
