package ru.eclipsia.hud.sidebar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.core.data.StatKeys;
import ru.eclipsia.core.stats.ManaManager;
import ru.eclipsia.core.stats.StatResolver;
import ru.eclipsia.hud.theme.Theme;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player scoreboard sidebar (правая колонка).
 *
 * <p>До 15 строк, обновление по таймеру (по умолчанию раз в секунду).
 * Содержимое: класс / уровень / опыт / HP / Эгида / мана / зона / время.
 *
 * <p>Sidebar НЕ конкурирует с action bar HUD из EclipsiaSkills — это
 * другой канал вывода, дополняющий, а не заменяющий. Action bar показывает
 * «в моменте» (HP/MP/статы), sidebar — «обзор персонажа».
 *
 * <p>Каждый игрок получает СВОЙ Scoreboard (а не общий main()): иначе
 * объективы leak'нут между игроками и значения перетрутся.
 */
public final class SidebarService implements Listener {

    private static final int MAX_LINES = 15;

    private final Plugin plugin;
    private final ConfigurationSection cfg;
    private BukkitTask task;

    // Какие UUID игроков получают sidebar.
    private final Set<UUID> enabled = new HashSet<>();
    // У каждого игрока — свой Scoreboard, чтобы команды-«строки» не пересекались.
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public SidebarService(Plugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void start() {
        if (task != null || !cfg.getBoolean("enabled", true)) return;
        long period = Math.max(1L, cfg.getLong("period-ticks", 20L));

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!enabled.contains(p.getUniqueId())) continue;
                    try {
                        render(p);
                    } catch (Throwable t) {
                        // не валим тик из-за одного проблемного игрока
                        plugin.getLogger().warning("SidebarService render error for "
                                + p.getName() + ": " + t.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, period, period);

        // Подцепить уже залогиненных игроков (на reload-сценарий).
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (cfg.getBoolean("default-on", true)) {
                setVisible(p, true);
            }
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Возврат к main scoreboard, чтобы при выгрузке плагина sidebar не «прилип».
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
        enabled.clear();
    }

    public boolean isVisible(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void setVisible(Player player, boolean visible) {
        if (visible) {
            enabled.add(player.getUniqueId());
            render(player);
        } else {
            enabled.remove(player.getUniqueId());
            boards.remove(player.getUniqueId());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg.getBoolean("enabled", true)) return;
        if (cfg.getBoolean("default-on", true)) {
            setVisible(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enabled.remove(event.getPlayer().getUniqueId());
        boards.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Полный рендер sidebar'а для одного игрока. Stateful: используем
     * Team#suffix для дешёвых апдейтов одной строки (без пересборки всех
     * Objective-ов каждый тик).
     */
    private void render(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), k -> createBoard());

        // Гарантируем, что игрок реально смотрит на наш scoreboard.
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        // Собираем строки сверху вниз.
        List<Component> lines = composeLines(player);
        int n = Math.min(lines.size(), MAX_LINES);

        // У scoreboard sidebar число рядом со строкой = "score". Чтобы строки
        // шли в нужном порядке, ставим score = (n - i), и они отрисовываются
        // сверху вниз. Текст строки — это entry (имя цвета-кода-уникальное), а
        // содержимое реально приходит из Team#suffix.
        Objective obj = board.getObjective("eclipsia.sidebar");
        if (obj == null) return;

        for (int i = 0; i < n; i++) {
            String entry = entryFor(i);
            obj.getScore(entry).setScore(n - i);
            Team team = board.getTeam("eclipsia.line." + i);
            if (team != null) {
                team.suffix(lines.get(i));
            }
        }
        // если в этот рендер строк меньше, чем было — обнуляем «хвост»
        for (int i = n; i < MAX_LINES; i++) {
            String entry = entryFor(i);
            board.resetScores(entry);
        }
    }

    private Scoreboard createBoard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Component titleComp = Theme.mm(cfg.getString("title", "<gold>Eclipsia</gold>"));
        Objective obj = board.registerNewObjective(
                "eclipsia.sidebar", Criteria.DUMMY, titleComp);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Заводим команды для каждой потенциальной строки. Это разовая операция —
        // дальше мы меняем только suffix.
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = board.registerNewTeam("eclipsia.line." + i);
            team.addEntry(entryFor(i));
        }
        return board;
    }

    /**
     * Уникальный «entry» для строки. ChatColor-маркеры — единственный способ
     * получить разные entry, не показывая их пользователю (они невидимы).
     */
    private String entryFor(int i) {
        // §1 .. §f плюс §k/§l/§m/§n/§o => 15 уникальных невидимых маркеров.
        // Этого хватает на MAX_LINES = 15.
        char[] codes = {'1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        return "§" + codes[i] + "§r";
    }

    // ====== СОДЕРЖИМОЕ STRINGS ======

    private List<Component> composeLines(Player player) {
        EclipsiaAPI api = EclipsiaAPI.getInstance();
        PlayerProfile profile = api == null ? null : api.getActiveProfile(player);

        java.util.List<Component> lines = new java.util.ArrayList<>(MAX_LINES);

        // Header
        lines.add(Theme.mm("<dark_gray>━━━━━━━━━━━━━━━━</dark_gray>"));

        if (profile == null) {
            lines.add(Theme.mm("<gray>профиль не выбран</gray>"));
            lines.add(Theme.mm("<yellow>/profile</yellow> <gray>чтобы создать</gray>"));
            lines.add(Theme.mm("<dark_gray>━━━━━━━━━━━━━━━━</dark_gray>"));
            lines.add(Theme.mm("<gray>Мир: <white>" + player.getWorld().getName() + "</white></gray>"));
            return lines;
        }

        String classId = profile.getClassName();
        String classIcon = Theme.classIcon(classId);
        String classDisplay = Theme.classDisplayName(classId);

        // Класс
        lines.add(Component.text()
                .append(Theme.mm("<gray>Класс: </gray>"))
                .append(Component.text(classIcon + " ", Theme.classColor(classId)))
                .append(Component.text(classDisplay, Theme.classColor(classId)))
                .build());

        // Уровень
        lines.add(Theme.mm("<gray>Уровень: <gold>" + profile.getLevel() + "</gold></gray>"));

        // Опыт
        int exp = profile.getExperience();
        lines.add(Theme.mm("<gray>Опыт: <yellow>" + exp + "</yellow></gray>"));

        // Разделитель
        lines.add(Theme.mm("<dark_gray>————————————————</dark_gray>"));

        // HP
        int maxHp = (int) Math.round(maxHealth(player));
        int hp = (int) Math.round(player.getHealth());
        lines.add(Theme.mm("<red>❤</red> <gray>HP: <white>" + hp + "/" + maxHp + "</white></gray>"));

        // Эгида (если есть)
        int aegis = profile.getAegis();
        int maxAegis = profile.getMaxAegis();
        if (maxAegis > 0) {
            lines.add(Theme.mm("<light_purple>♦</light_purple> <gray>Эг: <white>"
                    + aegis + "/" + maxAegis + "</white></gray>"));
        }

        // Мана
        int curMana = profile.getCurrentMana();
        int maxMana = ManaManager.getMaxMana(player, profile);
        lines.add(Theme.mm("<blue>✦</blue> <gray>MP: <white>"
                + curMana + "/" + maxMana + "</white></gray>"));

        // Разделитель
        lines.add(Theme.mm("<dark_gray>————————————————</dark_gray>"));

        // Базовые статы (одна строка)
        int str = StatResolver.total(player, profile, StatKeys.STRENGTH);
        int dex = StatResolver.total(player, profile, StatKeys.DEXTERITY);
        int intl = StatResolver.total(player, profile, StatKeys.INTELLIGENCE);
        lines.add(Theme.mm("<red>⚔ " + str + "</red> <green>➹ " + dex + "</green> <blue>✦ " + intl + "</blue>"));

        // Зона
        lines.add(Theme.mm("<gray>Зона: <white>" + player.getWorld().getName() + "</white></gray>"));

        return lines;
    }

    private double maxHealth(Player player) {
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr == null ? player.getMaxHealth() : attr.getValue();
    }
}
