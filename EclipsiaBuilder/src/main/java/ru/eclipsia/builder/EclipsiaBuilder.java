package ru.eclipsia.builder;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.builder.manager.StructureManager;
import ru.eclipsia.builder.command.BuildCommand;
import ru.eclipsia.builder.generator.BeachGenerator;
import ru.eclipsia.builder.generator.BeachParticles;
import ru.eclipsia.builder.generator.SpireParticles;
import ru.eclipsia.builder.generator.WorldGenerator;
import ru.eclipsia.builder.listener.CampRespawnListener;
import ru.eclipsia.builder.listener.WaterGuardListener;
import ru.eclipsia.builder.listener.WorldProtectListener;
import ru.eclipsia.builder.listener.WorldBorderListener;

/**
 * Главный класс плагина EclipsiaBuilder
 * Управляет генерацией структур мира
 */
public class EclipsiaBuilder extends JavaPlugin {
    
    private static EclipsiaBuilder instance;
    private StructureManager structureManager;
    private WorldBorderListener borderListener;
    private BeachParticles beachParticles;
    private SpireParticles spireParticles;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        
        // Проверяем наличие Multiverse-Core
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            getLogger().warning("Multiverse-Core не найден! Создание миров будет пропущено.");
        } else {
            getLogger().info("Multiverse-Core обнаружен, создание плоских миров...");
            createFlatWorlds();
        }
        
        // Слушатель собственных границ (без зависимости от WorldGuard).
        borderListener = new WorldBorderListener(this);
        Bukkit.getPluginManager().registerEvents(borderListener, this);

        // Water-guard: возврат игрока из воды (мир beach).
        Bukkit.getPluginManager().registerEvents(new WaterGuardListener(this), this);

        // v7: тотальная защита мира beach от изменений (только админы могут
        // ломать/ставить).
        Bukkit.getPluginManager().registerEvents(new WorldProtectListener(this), this);

        // v8: респавн в лагере + keepInventory на смерти в beach.
        Bukkit.getPluginManager().registerEvents(new CampRespawnListener(this), this);

        // Инициализируем менеджер структур
        structureManager = new StructureManager(this);
        structureManager.loadStructures();

        // Строим все структуры один раз при старте сервера, после того
        // как Multiverse завершит создание/загрузку миров (~40 тиков).
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Сначала запускаем большую процедурную генерацию Берега —
            // BeachGenerator идемпотентен (PDC-маркер), запускать можно
            // на каждом старте без последствий.
            generateBeachIfPresent();

            // Затем (через 60 тиков ≈ 3 секунды после старта Берега) —
            // основной мир с городом Эликий. WorldGenerator тоже
            // идемпотентен через свой PDC-маркер; запускаем после Берега,
            // чтобы пиковая нагрузка от двух больших заливок RegionPainter
            // не складывалась в один тик.
            Bukkit.getScheduler().runTaskLater(this,
                    this::generateMainWorldIfPresent, 60L);

            // Затем — старые «маленькие» структуры (хаб, лагеря в world).
            int built = structureManager.buildAll();
            getLogger().info("Построено структур: " + built);

            // Запускаем атмосферные частицы для мира beach (v3).
            beachParticles = new BeachParticles(this);
            beachParticles.start();

            // Атмосферные частицы шпиля Эликия (PR 2). Шедулер сам ничего
            // не делает, пока WorldGenerator не выставит координаты шпиля
            // через статические поля — поэтому стартовать можно сразу.
            spireParticles = new SpireParticles(this);
            spireParticles.start();
        }, 40L);

        // Регистрируем команды
        BuildCommand buildCommand = new BuildCommand(this);
        getCommand("build").setExecutor(buildCommand);
        getCommand("build").setTabCompleter(buildCommand);
        
        getLogger().info("EclipsiaBuilder успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        if (beachParticles != null) {
            beachParticles.stop();
        }
        if (spireParticles != null) {
            spireParticles.stop();
        }
        getLogger().info("EclipsiaBuilder выключен");
    }
    
    public static EclipsiaBuilder getInstance() {
        return instance;
    }
    
    public StructureManager getStructureManager() {
        return structureManager;
    }

    public WorldBorderListener getBorderListener() {
        return borderListener;
    }
    
    /**
     * Создать плоские миры через Multiverse-Core
     */
    private void createFlatWorlds() {
        try {
            // Используем команды Multiverse для создания миров
            Bukkit.getScheduler().runTask(this, () -> {
                // Multiverse-Core 5.x синтаксис:
                //   mv create <name> <environment> --world-type flat
                // (старый MV4 принимал FLAT как environment, что в MV5
                //  выдаёт ошибку "Please specify one of (NORMAL, NETHER,
                //  THE_END, CUSTOM)". environment теперь — измерение,
                //  плоскость задаётся отдельным флагом --world-type flat.)
                createFlatWorldIfMissing("lobby");
                createFlatWorldIfMissing("beach");
                createFlatWorldIfMissing("world");
                
                getLogger().info("Все плоские миры созданы!");
            });
        } catch (Exception e) {
            getLogger().severe("Ошибка при создании миров: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Создать плоский мир через MV5 если его ещё нет.
     * Проверяет результат — если после команды мир так и не появился
     * в Bukkit.getWorld(), пишет предупреждение в лог.
     */
    private void createFlatWorldIfMissing(String name) {
        if (Bukkit.getWorld(name) != null) {
            getLogger().info("Мир '" + name + "' уже загружен, пропуск.");
            return;
        }

        // Создаём/загружаем мир напрямую через Bukkit API — это надёжнее
        // команды `mv create`, которая в MV5 порой игнорирует --world-type.
        // Если папка с level.dat существует, Bukkit просто загрузит мир
        // из неё; если нет — сгенерирует свежий FLAT.
        //
        // ВАЖНО: на Paper 1.20.4 `WorldType.FLAT` без явных
        // generatorSettings приводит к ошибке "No key layers in MapLike[{}]"
        // и пустому миру. Поэтому передаём JSON со слоями (bedrock + dirt +
        // grass = 4 блока земли, поверхность на y=4 в системе FLAT).
        String flatSettings = "{"
                + "\"layers\":["
                + "{\"block\":\"minecraft:bedrock\",\"height\":1},"
                + "{\"block\":\"minecraft:dirt\",\"height\":2},"
                + "{\"block\":\"minecraft:grass_block\",\"height\":1}"
                + "],"
                + "\"biome\":\"minecraft:plains\","
                + "\"features\":false,"
                + "\"lakes\":false"
                + "}";
        WorldCreator wc = new WorldCreator(name)
                .type(WorldType.FLAT)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generatorSettings(flatSettings);

        World world = Bukkit.createWorld(wc);
        if (world == null) {
            getLogger().warning("Не удалось создать/загрузить мир '" + name + "'.");
            return;
        }
        getLogger().info("Мир '" + name + "' готов (FLAT, dim=" + world.getEnvironment() + ").");

        // Импортируем мир в Multiverse, чтобы команды `/mv tp <name>`,
        // gamerules через MV и т.п. работали. Если мир уже есть в worlds.yml —
        // mv import пропустит без ошибки. Запускаем после короткой задержки,
        // чтобы Bukkit точно зарегистрировал мир.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mv import " + name + " normal");
        }, 10L);
    }

    /**
     * Запустить процедурную генерацию Берега, если мир {@code beach} загружен.
     * BeachGenerator самостоятельно проверяет PDC-маркер и не дублирует
     * генерацию при повторных стартах.
     *
     * <p>Регистрирует декоративную WorldBorder через
     * {@link WorldBorderListener}: 300×300 от центра (0,0).
     */
    private void generateBeachIfPresent() {
        World beach = Bukkit.getWorld("beach");
        if (beach == null) {
            getLogger().warning("Мир 'beach' не загружен, генерация пропущена.");
            return;
        }

        // С v2: настоящая Bukkit WorldBorder ставится самим BeachGenerator.
        // Прямоугольный fallback оставляем — на случай, если игрок
        // как-то «прыгнет» через WB (нет такого, но пусть будет).
        borderListener.registerBorder(beach.getName(),
                -170, -195, 170, 275,
                "§5Вы не можете покинуть Берег. Победите Хранителя Врат.");

        BeachGenerator gen = new BeachGenerator(this, beach);
        gen.generate(null);
    }

    /**
     * Принудительно перегенерировать мир Берега. Используется командой
     * {@code /build regen-beach}. Сбрасывает PDC-маркер; блоки старого
     * мира остаются — новый ландшафт ляжет поверх. Для полной чистоты
     * лучше удалять папку мира, но это требует перезапуска.
     */
    public boolean forceRegenerateBeach() {
        World beach = Bukkit.getWorld("beach");
        if (beach == null) {
            getLogger().warning("Мир 'beach' не загружен — нельзя перегенерировать.");
            return false;
        }
        BeachGenerator gen = new BeachGenerator(this, beach);
        gen.resetMarker();
        gen.generate(null);
        return true;
    }

    /**
     * Запустить процедурную генерацию основного мира (город Эликий + округа),
     * если мир {@code world} загружен. {@link WorldGenerator} проверяет
     * PDC-маркер и пропускает заливку, если мир уже сгенерирован.
     */
    private void generateMainWorldIfPresent() {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().warning("Мир 'world' не загружен, генерация Эликия пропущена.");
            return;
        }
        WorldGenerator gen = new WorldGenerator(this, world);
        gen.generate(null);
    }

    /**
     * Принудительно перегенерировать основной мир (Эликий). Аналогично
     * {@link #forceRegenerateBeach()}: сбрасывает PDC-маркер и запускает
     * заливку, которая ложится поверх старых блоков.
     */
    public boolean forceRegenerateWorld() {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().warning("Мир 'world' не загружен — нельзя перегенерировать.");
            return false;
        }
        WorldGenerator gen = new WorldGenerator(this, world);
        gen.resetMarker();
        gen.generate(null);
        return true;
    }
}
