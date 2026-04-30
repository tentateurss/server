package ru.eclipsia.builder.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Процедурная генерация города Эликий и базового ландшафта в мире
 * {@code world}. Полностью независима от {@link BeachGenerator} —
 * совпадает с ним только по архитектурному паттерну (8 фаз +
 * {@link RegionPainter} + PDC-маркер + {@link FloatingText}).
 *
 * <p><b>Игровой контекст</b>: игрок убивает Хранителя Врат на Береге,
 * подходит к появившемуся порталу-арке (мир {@code beach}, точка
 * {@code (0.5, 16, 154.5)}) и телепортируется в этот мир на точку
 * {@link #SPAWN_X}/{@link #SPAWN_Y}/{@link #SPAWN_Z} = {@code (0, 75, 38)} —
 * прямо ПЕРЕД южными воротами Эликия. Через арку видны улицы и собор.
 *
 * <p><b>8 фаз генерации</b> (порядок строгий — каждая фаза опирается на
 * предыдущую):
 * <ol>
 *   <li>{@link #phase1Landscape ЛАНДШАФТ} — мостовая под городом
 *       (POLISHED_DEEPSLATE на y=70 внутри городского полигона). Внешний
 *       мир остаётся плоским из {@code flatSettings} EclipsiaBuilder
 *       (bedrock 5 + stone 55 + dirt 9 + grass 1 = ровный y=0..70).</li>
 *   <li>{@link #phase2Vegetation РАСТИТЕЛЬНОСТЬ} — для города почти
 *       пусто (несколько горшков); внешние биомы — отдельный PR.</li>
 *   <li>{@link #phase3Decorations ДЕКОРАЦИИ} — мелкие детали (бочки,
 *       цветы, дрова); реализация в PR 5.</li>
 *   <li>{@link #phase4Paths УЛИЦЫ} — изгибающиеся улицы 3-7 блоков
 *       шириной с фонарями; реализация в PR 4.</li>
 *   <li>{@link #phase5PointsOfInterest ТОЧКИ ИНТЕРЕСА} — таверна,
 *       кузница, лавка, гильдия, склад, рынок, колодец, жилые дома;
 *       реализация в PR 4.</li>
 *   <li>{@link #phase6Structures КРУПНЫЕ СТРУКТУРЫ} — стена-полигон 22
 *       вершины с воротами и башнями (PR 2), собор со шпилем-глазом
 *       (PR 3), южные горы (PR 2).</li>
 *   <li>{@link #phase7FloatingText НАДПИСИ} — названия ворот, зданий,
 *       площади, границ карты; реализация в PR 5.</li>
 *   <li>{@link #phase8Spawnpoint ТОЧКА СПАВНА} — фиксируем
 *       {@link World#setSpawnLocation} на (0, 75, 38) и помечаем мир
 *       PDC-маркером {@link #GENERATED_FLAG}.</li>
 * </ol>
 *
 * <p><b>Идемпотентность</b>: если PDC-маркер уже выставлен, генератор
 * сразу возвращается. Чтобы заставить мир регенерироваться, надо либо
 * сбросить маркер ({@link #resetMarker()}), либо удалить папку мира.
 * Версия маркера привязана к структуре генератора — при крупных правках
 * (например, +PR 2) {@link #GENERATED_FLAG} инкрементируется и старые
 * миры пересоздаются автоматически.
 */
public final class WorldGenerator {

    // =========================================================================
    // ВЕРСИОНИРОВАНИЕ
    // =========================================================================

    /**
     * PDC-ключ на мире: «Эликий уже сгенерирован». Версия меняется при
     * структурных правках, чтобы сервер пересобрал город при апдейте.
     *
     * <p><b>v6</b>: PR 1 — полный rewrite. Скелет 8 фаз, мостовая под
     * городом (POLISHED_DEEPSLATE), точка спавна перед южными воротами
     * (0, 75, 38), плоский внешний мир из flatSettings.
     *
     * <p><b>v7</b>: PR 2 — стена-полигон 22 вершины из COBBLED_DEEPSLATE +
     * DEEPSLATE_BRICKS, 4 ворот с арками, башни через 18-22 блока
     * ({@link ElikiumWall}), южная горная гряда z=50..190 / x=±200,
     * h=18..60 ({@link WorldMountains}, шум SimplexOctaveGenerator).
     */
    public static final String GENERATED_FLAG = "eclipsia_world_generated_v7";

    // =========================================================================
    // ГЕОМЕТРИЯ ГОРОДА
    // =========================================================================

    /** Уровень мостовой города (соответствует {@code flatSettings} мира). */
    public static final int CITY_FLOOR_Y = 70;

    /**
     * Полигон городской стены — 22 вершины, неправильная форма (НЕ квадрат).
     * Координаты {@code (x, z)} обходятся по часовой стрелке. Используется:
     * <ul>
     *   <li>в фазе 1 — для замощения внутренней площади
     *       (POLISHED_DEEPSLATE);</li>
     *   <li>в фазе 6 (PR 2) — для трассировки самой стены и расстановки
     *       башен через 15-20 блоков по периметру.</li>
     * </ul>
     */
    public static final int[][] CITY_POLYGON = {
            {-42, -40}, {-38, -48}, {-20, -50}, {  0, -48}, { 20, -50},
            { 42, -45}, { 48, -35}, { 50, -15}, { 48,   0}, { 50,  15},
            { 45,  35}, { 40,  38}, { 30,  40}, { 15,  42}, {  0,  40},
            {-15,  42}, {-30,  40}, {-40,  38}, {-45,  35},
            {-48,  15}, {-50,   0}, {-48, -15}, {-42, -40},
    };

    // =========================================================================
    // КООРДИНАТЫ ЗДАНИЙ (для PR 2-5)
    // =========================================================================

    /** Центр собора. Принципиально НЕ в (0,0): смещён на восток (см. ТЗ). */
    public static final int CATHEDRAL_X = 15;
    public static final int CATHEDRAL_Z = -5;

    /** Координаты ворот: {@code [x, z]}. */
    public static final int[] SOUTH_GATE = {  0,  35 };
    public static final int[] NORTH_GATE = {  0, -45 };
    public static final int[] EAST_GATE  = { 45,   0 };
    public static final int[] WEST_GATE  = { -40, -5 };

    // =========================================================================
    // ТОЧКА СПАВНА ИГРОКА
    // =========================================================================

    /**
     * Точка появления игрока после портала. Игрок стоит ПЕРЕД южными
     * воротами (z=35): на z=38 он смотрит на арку, за аркой — улицы
     * города и силуэт собора.
     *
     * <p>Сюда же телепортирует {@code GatekeeperArena} после убийства
     * Хранителя — координаты должны совпадать.
     */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = CITY_FLOOR_Y + 5; // 75
    public static final int SPAWN_Z = 38;

    // =========================================================================
    // КООРДИНАТЫ ШПИЛЯ (для SpireParticles)
    // =========================================================================

    /**
     * Координаты «глаза» на вершине шпиля собора — читаются
     * {@link SpireParticles}. До PR 3 (постройка собора) указывают на
     * место, где он будет: {@code (CATHEDRAL_X+0.5, CITY_FLOOR_Y+51-1+1.5,
     * CATHEDRAL_Z+0.5)}. После постройки в PR 3 будут переписаны на
     * фактическую точку.
     */
    public static volatile double spireCenterX = CATHEDRAL_X + 0.5;
    public static volatile double spireCenterY = CITY_FLOOR_Y + 51 + 1.5;
    public static volatile double spireCenterZ = CATHEDRAL_Z + 0.5;

    // =========================================================================
    // СОСТОЯНИЕ
    // =========================================================================

    private final Plugin plugin;
    private final World world;

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

    /** Сбросить маркер (используется командой принудительной регенерации). */
    public void resetMarker() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().remove(key);
    }

    // =========================================================================
    // ГЛАВНАЯ ТОЧКА ВХОДА
    // =========================================================================

    /**
     * Запустить генерацию всех 8 фаз. {@code onFinish} вызывается, когда
     * последняя операция RegionPainter применена в мир.
     */
    public void generate(Runnable onFinish) {
        if (isAlreadyGenerated()) {
            plugin.getLogger().info("WorldGenerator(" + GENERATED_FLAG
                    + "): уже сгенерировано, пропуск.");
            world.setSpawnLocation(SPAWN_X, SPAWN_Y, SPAWN_Z);
            if (onFinish != null) onFinish.run();
            return;
        }

        plugin.getLogger().info("WorldGenerator: начинаю генерацию '"
                + world.getName() + "' (" + GENERATED_FLAG + ")…");
        long seed = world.getSeed() ^ 0xE11C1A77L; // «ELIKIA77»
        Random rng = new Random(seed);

        // На случай повторной попытки — стираем все наши FloatingText.
        FloatingText.removeAll(plugin, world);

        RegionPainter p = new RegionPainter(plugin, world, seed);
        p.begin();

        // ===== Фаза 1: ландшафт (мостовая внутри полигона) =====
        phase1Landscape(p, rng);

        // ===== Фаза 2: растительность (заглушка) =====
        phase2Vegetation(p, rng);

        // ===== Фаза 3: декорации (заглушка) =====
        phase3Decorations(p, rng);

        // ===== Фаза 4: улицы (заглушка) =====
        phase4Paths(p, rng);

        // ===== Фаза 5: точки интереса — здания (заглушка) =====
        phase5PointsOfInterest(p, rng);

        // ===== Фаза 6: крупные структуры — стена, ворота, собор (заглушка) =====
        phase6Structures(p, rng);

        // ===== Фаза 7: FloatingText (заглушка) =====
        // Запустится после flush() в onFinish — сущности можно спавнить
        // только когда блоки уже на месте.

        // Финал: применяем все накопленные операции одной асинхронной заливкой.
        p.flush(() -> {
            phase7FloatingText(rng);
            phase8Spawnpoint();
            markGenerated();
            plugin.getLogger().info("WorldGenerator: '" + world.getName()
                    + "' готов (8 фаз).");
            if (onFinish != null) onFinish.run();
        });
    }

    // =========================================================================
    // ФАЗА 1: ЛАНДШАФТ
    // =========================================================================

    /**
     * Замостить внутреннюю площадь городского полигона блоками
     * {@link Material#POLISHED_DEEPSLATE} на уровне {@link #CITY_FLOOR_Y}.
     *
     * <p>Внешний мир (за пределами полигона) НЕ трогается — он плоский
     * по {@code flatSettings} (bedrock 5 + stone 55 + dirt 9 + grass 1,
     * поверхность y=70). В PR 2-5 здесь добавятся горы, реки, биомы.
     *
     * <p>Также очищаем 60 блоков воздуха над мостовой — на случай, если
     * мир уже был заселён сущностями/деревьями ванильной генерации.
     */
    private void phase1Landscape(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase1: замощение городского полигона…");

        // Bounding box полигона (см. CITY_POLYGON): x ∈ [-50..50], z ∈ [-50..42].
        int xMin = -50, xMax = 50;
        int zMin = -50, zMax = 42;

        int paved = 0;
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                if (!isInsideCityPolygon(x, z)) continue;
                p.place(x, CITY_FLOOR_Y, z, Material.POLISHED_DEEPSLATE);
                // Чистый воздух над мостовой (на 30 блоков — высота собора + запас).
                for (int dy = 1; dy <= 30; dy++) {
                    p.place(x, CITY_FLOOR_Y + dy, z, Material.AIR);
                }
                paved++;
            }
        }
        plugin.getLogger().info("WorldGenerator/phase1: замощено " + paved
                + " блоков мостовой города.");
    }

    // =========================================================================
    // ФАЗА 2: РАСТИТЕЛЬНОСТЬ (PR 5 — внешние биомы; внутри города почти пусто)
    // =========================================================================

    private void phase2Vegetation(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase2: растительность — TODO в PR 5.");
    }

    // =========================================================================
    // ФАЗА 3: ДЕКОРАЦИИ (PR 5 — бочки, цветы, дрова, телега, колокол)
    // =========================================================================

    private void phase3Decorations(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase3: декорации — TODO в PR 5.");
    }

    // =========================================================================
    // ФАЗА 4: УЛИЦЫ (PR 4 — изгибающиеся 3-7 блоков, фонари)
    // =========================================================================

    private void phase4Paths(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase4: улицы и переулки — TODO в PR 4.");
    }

    // =========================================================================
    // ФАЗА 5: ТОЧКИ ИНТЕРЕСА — ЗДАНИЯ (PR 4)
    // =========================================================================

    private void phase5PointsOfInterest(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase5: здания — TODO в PR 4.");
    }

    // =========================================================================
    // ФАЗА 6: КРУПНЫЕ СТРУКТУРЫ — СТЕНА, ВОРОТА, СОБОР (PR 2 + PR 3)
    // =========================================================================

    private void phase6Structures(RegionPainter p, Random rng) {
        plugin.getLogger().info(
                "WorldGenerator/phase6: стена + горы (собор — TODO в PR 3).");

        // PR 2 / часть A: стена-полигон + 4 ворот + башни.
        new ElikiumWall(plugin, p, rng).build();

        // PR 2 / часть B: южные горы (z=50..190, x=±200, h=18..60).
        new WorldMountains(plugin, p, rng).build();

        // PR 3: собор на (15, -5) — пока заглушка, будет в следующем PR.
    }

    // =========================================================================
    // ФАЗА 7: FLOATING TEXT (PR 5)
    // =========================================================================

    private void phase7FloatingText(Random rng) {
        plugin.getLogger().info("WorldGenerator/phase7: FloatingText — TODO в PR 5.");
    }

    // =========================================================================
    // ФАЗА 8: ТОЧКА СПАВНА
    // =========================================================================

    /**
     * Зафиксировать точку спавна мира на {@link #SPAWN_X}/{@link #SPAWN_Y}/
     * {@link #SPAWN_Z} = (0, 75, 38) — игрок появится прямо перед южными
     * воротами Эликия (ворота на z=35, спавн на z=38, лицом на север).
     */
    private void phase8Spawnpoint() {
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);
        world.setSpawnLocation(spawn);
        plugin.getLogger().info("WorldGenerator/phase8: spawn = ("
                + SPAWN_X + ", " + SPAWN_Y + ", " + SPAWN_Z + ")");
    }

    // =========================================================================
    // УТИЛИТЫ ПОЛИГОНА
    // =========================================================================

    /**
     * Точка-в-полигоне (ray casting). Используется для замощения внутренней
     * площади города и (в PR 2) для трассировки стены.
     *
     * <p>Алгоритм классический: пускаем горизонтальный луч в +X из точки
     * (px, pz) и считаем количество пересечений с рёбрами полигона —
     * нечётное число пересечений = точка внутри.
     */
    public static boolean isInsideCityPolygon(int px, int pz) {
        boolean inside = false;
        int n = CITY_POLYGON.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = CITY_POLYGON[i][0], zi = CITY_POLYGON[i][1];
            int xj = CITY_POLYGON[j][0], zj = CITY_POLYGON[j][1];
            boolean intersect = ((zi > pz) != (zj > pz))
                    && (px < (double) (xj - xi) * (pz - zi) / (zj - zi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
}
