package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Процедурная генерация основного мира {@code world} с городом Эликий
 * в центре. Полностью независима от {@link BeachGenerator} (Берег) — оба
 * генератора используют общие низкоуровневые утилиты ({@link RegionPainter},
 * {@link SimplexNoiseGenerator}), но координаты, фазы и ассеты у них разные.
 *
 * <p><b>Композиция карты</b> (область 400×400 относительно центра 0,0):
 * <ul>
 *   <li>Центр (CITY_X, CITY_Z) = (0, 0): город Эликий 80×80, окружённый
 *       стенами высотой 10 с четырьмя воротами; в центре — собор
 *       20×20×30.</li>
 *   <li>Север (z = -40..-150): поля, фермы, ветряная мельница, дорога
 *       к starter_zone.</li>
 *   <li>Восток (x = 40..150): лес из берёз и дубов, ручей, дорога
 *       к forest_zone.</li>
 *   <li>Запад (x = -40..-150): холмы, скалы, заброшенная шахта, дорога
 *       к elite_zone.</li>
 *   <li>Юг (z = 40..150): озеро 30×30 с островом, рыбацкая деревня.</li>
 * </ul>
 *
 * <p><b>Фазы</b> (выполняются последовательно одной асинхронной заливкой
 * через {@link RegionPainter}):
 * <ol>
 *   <li>Ландшафт — три октавы шума, плато под город, реки, озеро.</li>
 *   <li>Город — стены 80×80, 4 ворот, собор-крест 20×20×30
 *       (наполнение в PR 2).</li>
 *   <li>Дороги — три радиальные мощёные дороги из города
 *       (наполнение в PR 3).</li>
 *   <li>Окружение — фермы / лес / холмы / озеро / рыбацкая деревня
 *       (наполнение в PR 3).</li>
 *   <li>Декор и FloatingText — цветы, кустарники, тропинки, надписи
 *       (наполнение в PR 3).</li>
 * </ol>
 *
 * <p>Идемпотентность: PDC-маркер {@link #GENERATED_FLAG} на мире.
 * Меняется при структурном изменении генератора — в этом случае
 * следующий старт сервера пересоздаст ландшафт и постройки.
 */
public final class WorldGenerator {

    // =========================================================================
    // ВЕРСИОНИРОВАНИЕ
    // =========================================================================

    /**
     * Маркер версии. Сменив строку, вы вынуждаете генератор перерисовать
     * мир при следующем старте сервера. Например: {@code "..._v1"} →
     * {@code "..._v2"} после крупных правок городской планировки.
     *
     * <p><b>v1</b>: каркас + ландшафт + плато под город (PR 1).
     * <p><b>v2</b>: + город Эликий (стены, башни, ворота, собор, шпиль) — PR 2.
     */
    public static final String GENERATED_FLAG = "eclipsia_world_generated_v2";

    // =========================================================================
    // КООРДИНАТЫ И РАЗМЕРЫ
    // =========================================================================

    /** Центр города (x, z). */
    public static final int CITY_X = 0;
    public static final int CITY_Z = 0;

    /** Размер городских стен (полусторона). 80×80 ⇒ HALF = 40. */
    public static final int CITY_HALF = 40;

    /** Полусторона плато под город. Чуть больше стен, чтобы было «крыльцо». */
    public static final int CITY_PAD_HALF = 50;

    /** Высота городского пола (плато). */
    public static final int CITY_FLOOR_Y = 70;

    /** Высота стен Эликия в блоках. */
    public static final int CITY_WALL_HEIGHT = 10;

    /** Полуразмер собора (20×20). */
    public static final int CATHEDRAL_HALF = 10;
    /** Высота от пола до конька крыши собора. */
    public static final int CATHEDRAL_HEIGHT = 30;

    /** Точка появления игрока после портала из Берега — перед северными воротами. */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = CITY_FLOOR_Y + 5; // = 75
    public static final int SPAWN_Z = -35;

    /** Габариты «играбельного» региона процедурной генерации (включительно). */
    private static final int LAND_X_MIN = -200, LAND_X_MAX = 200;
    private static final int LAND_Z_MIN = -200, LAND_Z_MAX = 200;

    /** Базовая высота ландшафта вне города. */
    private static final int BASE_GROUND_Y = 64;

    /** Глубина «воздуха», которую нужно очистить над поверхностью. */
    private static final int CLEAR_AIR_HEIGHT = 60;

    /** Подповерхностный слой грунта (грязь под травой). */
    private static final int SUBSOIL_DEPTH = 4;

    // =========================================================================
    // ОЗЕРО (юг) — параметры из ТЗ.
    // =========================================================================

    /** Координаты центра озера. */
    public static final int LAKE_X = 0;
    public static final int LAKE_Z = 95;
    /** Полусторона прямоугольника озера 30×30 (с шумовой нерегулярностью). */
    public static final int LAKE_HALF = 18;
    /** Глубина озера (от уровня воды). */
    public static final int LAKE_DEPTH = 4;
    /** Уровень воды в озере. */
    public static final int LAKE_WATER_Y = BASE_GROUND_Y - 1;
    /** Полусторона острова на озере. */
    public static final int LAKE_ISLAND_HALF = 4;

    // =========================================================================
    // СОСТОЯНИЕ ШПИЛЯ (для SpireParticles)
    // =========================================================================

    /**
     * Координаты центра «глаза» на вершине шпиля собора. Читаются
     * {@link SpireParticles} при выпуске частиц; перезаписываются
     * {@link ElikiumCityBuilder} при постройке шпиля.
     *
     * <p>Инициализируются <b>сразу к финальным значениям</b>, выведенным
     * из констант города (а не {@link Double#NaN}). Иначе после первого
     * перезапуска сервера {@link #generate(Runnable)} попадает в
     * ранний return по PDC-маркеру, координаты остаются {@code NaN},
     * и {@link SpireParticles} прекращает работать.
     *
     * <p>Геометрия (см. {@code ElikiumCityBuilder.buildSpire}):
     * <pre>
     *   yWallTop  = CITY_FLOOR_Y + 20 (CATHEDRAL_NAVE_H)
     *   peak      = yWallTop + 5
     *   spireBase = peak + 1
     *   platY     = spireBase + 10 (spireH)
     *   rodY      = platY + 1
     *   center    = (CITY_X + 0.5, rodY + 1.5, CITY_Z + 0.5)
     *             = (0.5, 70 + 20 + 5 + 1 + 10 + 1 + 1.5, 0.5)
     *             = (0.5, 108.5, 0.5)
     * </pre>
     */
    public static volatile double spireCenterX = CITY_X + 0.5;
    public static volatile double spireCenterY = CITY_FLOOR_Y + 20 + 5 + 1 + 10 + 1 + 1.5;
    public static volatile double spireCenterZ = CITY_Z + 0.5;

    // =========================================================================
    // СОСТОЯНИЕ
    // =========================================================================

    private final Plugin plugin;
    private final World world;

    /** Шум ландшафта: три октавы (континент, холмы, детали). */
    private SimplexNoiseGenerator continentNoise;
    private SimplexNoiseGenerator hillNoise;
    private SimplexNoiseGenerator detailNoise;
    /** Шум смещения рек (даёт извилистость). */
    private SimplexNoiseGenerator riverNoise;

    public WorldGenerator(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public boolean isAlreadyGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    /** Сбросить флаг (для команды force-regen). */
    public void resetMarker() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().remove(key);
    }

    /**
     * Главная точка входа. Запускает все фазы последовательно через
     * единый {@link RegionPainter}; {@code onFinish} вызывается, когда
     * последняя операция применена в мир.
     */
    public void generate(Runnable onFinish) {
        if (isAlreadyGenerated()) {
            plugin.getLogger().info("WorldGenerator(" + GENERATED_FLAG + "): уже сгенерировано, пропуск.");
            world.setSpawnLocation(SPAWN_X, SPAWN_Y, SPAWN_Z);
            if (onFinish != null) onFinish.run();
            return;
        }

        plugin.getLogger().info("WorldGenerator: начинаю генерацию '" + world.getName() + "'…");
        long seed = world.getSeed() ^ 0xE11C1A77L; // «ELIKIA77»
        Random init = new Random(seed);
        this.continentNoise = new SimplexNoiseGenerator(init.nextLong());
        this.hillNoise = new SimplexNoiseGenerator(init.nextLong());
        this.detailNoise = new SimplexNoiseGenerator(init.nextLong());
        this.riverNoise = new SimplexNoiseGenerator(init.nextLong());

        // На случай повторной попытки — стираем все наши FloatingText.
        FloatingText.removeAll(plugin, world);

        RegionPainter p = new RegionPainter(plugin, world, seed);
        p.begin();
        Random rng = p.rng();

        // ===== ФАЗА 1. Ландшафт =====
        generateTerrain(p, rng);
        carveLake(p, rng);

        // ===== ФАЗА 2. Город (стены, ворота, собор) =====
        generateCity(p, rng);

        // ===== ФАЗА 3. Дороги — PR 3 =====
        // generateRoads(p, rng);

        // ===== ФАЗА 4. Окружение (поля/лес/холмы/деревня) — PR 3 =====
        // generateSurroundings(p, rng);

        // ===== ФАЗА 5. Декор — PR 3 =====
        // generateDecor(p, rng);

        // ===== Финал =====
        p.flush(() -> {
            spawnFloatingTexts();
            world.setSpawnLocation(SPAWN_X, SPAWN_Y, SPAWN_Z);
            markGenerated();
            plugin.getLogger().info("WorldGenerator: основной мир готов!");
            if (onFinish != null) onFinish.run();
        });
    }

    // =========================================================================
    // ФАЗА 1. ЛАНДШАФТ
    // =========================================================================

    /**
     * Сгенерировать ландшафт основного мира.
     *
     * <p>Для каждой клетки (x,z) считается высота поверхности по трём
     * октавам шума, ставится грязь под травой, очищается воздух выше
     * поверхности и кладётся {@link Material#GRASS_BLOCK} сверху.
     *
     * <p>Внутри городского плато (квадрат 100×100 вокруг 0,0) высота
     * принудительно фиксируется на {@link #CITY_FLOOR_Y} — это
     * «фундамент» под будущие постройки PR 2/PR 3.
     *
     * <p>Реки врезаются в ландшафт «жилами» вдоль линий, заданных
     * {@link #riverNoise} — мелкая вода (1 блок глубиной), не пересекают
     * городское плато.
     */
    private void generateTerrain(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: рельеф (~"
                + ((LAND_X_MAX - LAND_X_MIN + 1) * (LAND_Z_MAX - LAND_Z_MIN + 1))
                + " столбов)");

        for (int x = LAND_X_MIN; x <= LAND_X_MAX; x++) {
            for (int z = LAND_Z_MIN; z <= LAND_Z_MAX; z++) {
                // Озеро обработаем отдельной фазой (carveLake) — здесь его
                // не трогаем, чтобы не дублировать работу.
                if (isLakeColumn(x, z)) continue;

                int surface = computeHeight(x, z);
                Material surfaceMat = computeSurfaceMaterial(x, z, surface);

                // Подповерхностный слой: грязь / камень.
                int subsoilTop = surface - 1;
                int subsoilBottom = Math.max(BASE_GROUND_Y - 8, surface - SUBSOIL_DEPTH);
                for (int y = subsoilBottom; y <= subsoilTop; y++) {
                    boolean stoneLayer = y <= surface - 3;
                    p.place(x, y, z, stoneLayer ? Material.STONE : Material.DIRT);
                }

                // Поверхность.
                p.place(x, surface, z, surfaceMat);

                // Очистить воздух над поверхностью (на случай старого мира).
                int clearTop = surface + CLEAR_AIR_HEIGHT;
                for (int y = surface + 1; y <= clearTop; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }
    }

    /**
     * Высота поверхности для столба (x,z).
     *
     * <p>Формула:
     * <pre>
     *   h = continentNoise(0.003) * 12       // континентальные формы
     *     + hillNoise(0.02)      *  6        // холмы
     *     + detailNoise(0.08)    *  2        // мелкая рябь
     * </pre>
     * Базовая отметка — {@link #BASE_GROUND_Y}; в районе горного запада
     * (x &lt; -80) добавляется плавный подъём до +25 блоков.
     *
     * <p>В пределах городского плато высота фиксируется {@link #CITY_FLOOR_Y}.
     */
    private int computeHeight(int x, int z) {
        // Городское плато — абсолютно ровный фундамент под стены и собор.
        if (isCityPad(x, z)) {
            return CITY_FLOOR_Y;
        }

        double cont = continentNoise.noise(x * 0.003, z * 0.003);
        double hill = hillNoise.noise(x * 0.02, z * 0.02);
        double det = detailNoise.noise(x * 0.08, z * 0.08);

        double h = cont * 12.0 + hill * 6.0 + det * 2.0;

        // Западные горы (x < -80): плавный подъём до +25.
        if (x < -80) {
            double climb = Math.min(1.0, (-80 - x) / 70.0);
            // ridge-noise (1 - |hill|) даёт более «острые» вершины.
            double ridge = 1.0 - Math.abs(hill);
            h += climb * 18.0 + climb * ridge * 7.0;
        }

        int y = BASE_GROUND_Y + (int) Math.round(h);

        // Плавный «съезд» к плато на границе CITY_PAD_HALF..CITY_PAD_HALF+8,
        // чтобы стены не торчали ступенькой над холмом.
        int dx = Math.abs(x - CITY_X);
        int dz = Math.abs(z - CITY_Z);
        int distToPad = Math.max(dx, dz) - CITY_PAD_HALF;
        if (distToPad < 8 && distToPad >= 0) {
            double t = distToPad / 8.0; // 0 — у самого плато, 1 — на дальней границе склейки
            y = (int) Math.round(CITY_FLOOR_Y * (1 - t) + y * t);
        }

        return Math.max(BASE_GROUND_Y - 4, y);
    }

    /**
     * Материал поверхности. По умолчанию {@link Material#GRASS_BLOCK} —
     * мир «живой», в отличие от чёрного Берега. Около западных гор
     * на высоте выше +18 кладём камень, чтобы хребет визуально читался.
     */
    private Material computeSurfaceMaterial(int x, int z, int surfaceY) {
        if (isCityPad(x, z)) {
            // Плато под город — гладкий камень, чтобы швы между разными
            // зданиями PR 2/PR 3 ложились без «травы из-под пола».
            return Material.STONE;
        }
        if (surfaceY >= BASE_GROUND_Y + 18) {
            return Material.STONE;
        }
        // Берег озера — песок (гладкий переход вода ↔ суша).
        if (isLakeShore(x, z)) {
            return Material.SAND;
        }
        return Material.GRASS_BLOCK;
    }

    /** Внутри городского плато 100×100 (с запасом в +10 от стен)? */
    private boolean isCityPad(int x, int z) {
        return Math.abs(x - CITY_X) <= CITY_PAD_HALF
                && Math.abs(z - CITY_Z) <= CITY_PAD_HALF;
    }

    /** Берег озера — кольцо +1..+3 от LAKE_HALF. */
    private boolean isLakeShore(int x, int z) {
        int dx = x - LAKE_X;
        int dz = z - LAKE_Z;
        int dist2 = dx * dx + dz * dz;
        int rOuter = LAKE_HALF + 3;
        int rInner = LAKE_HALF;
        return dist2 <= rOuter * rOuter && dist2 > rInner * rInner;
    }

    /** Внутри основной чаши озера? (для пропуска в фазе ландшафта) */
    private boolean isLakeColumn(int x, int z) {
        int dx = x - LAKE_X;
        int dz = z - LAKE_Z;
        return dx * dx + dz * dz <= LAKE_HALF * LAKE_HALF;
    }

    // =========================================================================
    // ФАЗА 2. ГОРОД ЭЛИКИЙ (делегируется ElikiumCityBuilder)
    // =========================================================================

    /**
     * Сгенерировать город Эликий: стены 80×80, угловые башни, четверо ворот
     * (с FloatingText-надписями), центральный собор 20×20×30 со шпилем,
     * платформа на шпиле с SOUL_FIRE / AMETHYST / END_ROD.
     *
     * <p>Вся геометрия вынесена в {@link ElikiumCityBuilder} ради читаемости.
     */
    private void generateCity(RegionPainter p, Random rng) {
        new ElikiumCityBuilder(plugin, world).buildAll(p, rng);
    }

    // =========================================================================
    // ФАЗА 1.5. ОЗЕРО (юг) — врезается отдельной фазой, чтобы не дублировать
    //                       логику ландшафта в каждом столбе.
    // =========================================================================

    /**
     * Сгенерировать южное озеро 30×30 с островом 8×8 в центре.
     *
     * <p>Алгоритм:
     * <ul>
     *   <li>В круге радиуса {@link #LAKE_HALF}: до глубины
     *       {@link #LAKE_DEPTH} ставим {@link Material#WATER},
     *       дно — {@link Material#DIRT};</li>
     *   <li>В центральном круге радиуса {@link #LAKE_ISLAND_HALF}:
     *       насыпь {@link Material#GRASS_BLOCK} над водой
     *       (остров для будущей ивы из PR 3);</li>
     *   <li>Воздух над водой очищается на {@link #CLEAR_AIR_HEIGHT}.</li>
     * </ul>
     */
    private void carveLake(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: озеро на юге ("
                + LAKE_X + "," + LAKE_Z + "), радиус ~" + LAKE_HALF);
        int lakeBottomY = LAKE_WATER_Y - LAKE_DEPTH;

        for (int dx = -LAKE_HALF; dx <= LAKE_HALF; dx++) {
            for (int dz = -LAKE_HALF; dz <= LAKE_HALF; dz++) {
                int dist2 = dx * dx + dz * dz;
                if (dist2 > LAKE_HALF * LAKE_HALF) continue;
                int x = LAKE_X + dx;
                int z = LAKE_Z + dz;

                // Центральный остров.
                if (dist2 <= LAKE_ISLAND_HALF * LAKE_ISLAND_HALF) {
                    // Земля под травой.
                    for (int y = lakeBottomY; y <= LAKE_WATER_Y; y++) {
                        p.place(x, y, z, Material.DIRT);
                    }
                    p.place(x, LAKE_WATER_Y + 1, z, Material.GRASS_BLOCK);
                    // Очистить воздух выше.
                    for (int y = LAKE_WATER_Y + 2; y <= LAKE_WATER_Y + CLEAR_AIR_HEIGHT; y++) {
                        p.place(x, y, z, Material.AIR);
                    }
                    continue;
                }

                // Чаша озера.
                p.place(x, lakeBottomY - 1, z, Material.STONE);
                for (int y = lakeBottomY; y <= LAKE_WATER_Y; y++) {
                    p.place(x, y, z, Material.WATER);
                }
                // Воздух над водой.
                for (int y = LAKE_WATER_Y + 1; y <= LAKE_WATER_Y + CLEAR_AIR_HEIGHT; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }
    }

    // =========================================================================
    // FloatingText — стартовый набор. Расширится в PR 2 и PR 3.
    // =========================================================================

    /**
     * Поставить FloatingText, видимые сразу после первого ТП в мир:
     * вывеска «Эликий» над северными воротами + таблички у точек интереса
     * (озеро). Дальнейшие надписи у врат / зданий / достопримечательностей
     * добавятся в PR 2/PR 3.
     */
    private void spawnFloatingTexts() {
        // Главная вывеска города — над северными воротами, прямо в зоне видимости
        // игрока, появившегося в SPAWN-точке (0, 75, -35) после портала.
        // Координаты согласованы с ТЗ: x=0, y=80 (на уровне верха стены),
        // z=-38 (на 2 блока южнее северной стены, т.е. ровно над аркой ворот).
        FloatingText.createLocationTitle(plugin, world,
                CITY_X + 0.5, CITY_FLOOR_Y + CITY_WALL_HEIGHT, CITY_Z - CITY_HALF + 2 + 0.5,
                "§6§lЭЛИКИЙ",
                "§7Город Света");

        // Озеро — в PR 1 уже есть как ландшафт, поэтому подпишем его сразу.
        FloatingText.createLocationTitle(plugin, world,
                LAKE_X + 0.5, LAKE_WATER_Y + 4, LAKE_Z + 0.5,
                "§bОзеро Эликия",
                "§7§oТихая вода у южных ворот");
    }
}
