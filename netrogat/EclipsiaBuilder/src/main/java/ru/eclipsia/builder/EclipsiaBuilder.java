package ru.eclipsia.builder;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.builder.manager.StructureManager;
import ru.eclipsia.builder.command.BuildCommand;
import ru.eclipsia.builder.generator.BeachGenerator;
import ru.eclipsia.builder.listener.WorldBorderListener;

/**
 * Главный класс плагина EclipsiaBuilder
 * Управляет генерацией структур мира
 */
public class EclipsiaBuilder extends JavaPlugin {
    
    private static EclipsiaBuilder instance;
    private StructureManager structureManager;
    private WorldBorderListener borderListener;
    
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

            // Затем — старые «маленькие» структуры (хаб, лагеря в world).
            int built = structureManager.buildAll();
            getLogger().info("Построено структур: " + built);
        }, 40L);

        // Регистрируем команды
        BuildCommand buildCommand = new BuildCommand(this);
        getCommand("build").setExecutor(buildCommand);
        getCommand("build").setTabCompleter(buildCommand);
        
        getLogger().info("EclipsiaBuilder успешно загружен!");
    }
    
    @Override
    public void onDisable() {
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

        // Регистрируем границу: 280×280 (10-блоковый отступ от пиков
        // горной стены на z=130, и от моря на z=-150).
        borderListener.registerBorder(beach.getName(),
                -140, -140, 140, 140,
                "§5Вы не можете покинуть Берег. Победите Хранителя Врат.");

        BeachGenerator gen = new BeachGenerator(this, beach);
        gen.generate(null);
    }
}
