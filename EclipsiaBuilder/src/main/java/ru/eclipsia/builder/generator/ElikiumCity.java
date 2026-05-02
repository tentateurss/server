package ru.eclipsia.builder.generator;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Оркестратор интерьера города Эликий. Координирует под-генераторы:
 * <ol>
 *   <li>{@link ElikiumStreets} — извилистые главные улицы + переулки;</li>
 *   <li>{@link ElikiumPlazas} — собор-площадь + рыночная площадь;</li>
 *   <li>{@link ElikiumNamedBuildings} — 5 уникальных POI
 *       (таверна, кузница, лавка, гильдия, склад) + ворота-будки;</li>
 *   <li>{@link ElikiumHouses} — плотные кварталы жилых домов
 *       (4 материальные семьи × 4 типа крыш);</li>
 *   <li>{@link ElikiumDecor} — мелочи (бочки, цветочные горшки,
 *       дрова, телеги, лозы) у входов и вдоль улиц.</li>
 * </ol>
 *
 * <p>Запускается в {@code phase5PointsOfInterest} {@link WorldGenerator},
 * до фазы 6 (стены и собор). Любой конфликт с собором/стеной перезаписывается
 * этими крупными структурами в фазе 6.
 *
 * <p><b>Стиль</b> — мрачная средневековая готика по референсу:
 * тёмные камни (DEEPSLATE_BRICKS, COBBLED_DEEPSLATE, POLISHED_BLACKSTONE),
 * тёмное дерево (DARK_OAK, SPRUCE), фиолетовые витражи, золотые акценты.
 *
 * <p><b>Метаданные</b> — после {@link #build()} объект хранит список
 * Footprint-ов и POI-якорей, которые {@link WorldGenerator#phase7FloatingText}
 * читает для расстановки FloatingText-вывесок.
 */
public final class ElikiumCity {

    static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70

    /** Зона исключения собора (с буфером) для всех под-генераторов. */
    static final int CATHEDRAL_X_MIN = WorldGenerator.CATHEDRAL_X - 34;
    static final int CATHEDRAL_X_MAX = WorldGenerator.CATHEDRAL_X + 34;
    static final int CATHEDRAL_Z_MIN = WorldGenerator.CATHEDRAL_Z - 46;
    static final int CATHEDRAL_Z_MAX = WorldGenerator.CATHEDRAL_Z + 46;

    /** POI с известным якорем для вывесок. */
    public static final class POI {
        public final String title;
        public final String subtitle;
        public final int x;
        public final int y;
        public final int z;
        public POI(String title, String subtitle, int x, int y, int z) {
            this.title = title;
            this.subtitle = subtitle;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Прямоугольный footprint занятой зоны. */
    public static final class Footprint {
        public final int xMin, zMin, xMax, zMax;
        public Footprint(int xMin, int zMin, int xMax, int zMax) {
            this.xMin = xMin; this.zMin = zMin;
            this.xMax = xMax; this.zMax = zMax;
        }
        public boolean overlaps(Footprint o, int buffer) {
            return xMin - buffer <= o.xMax && xMax + buffer >= o.xMin
                && zMin - buffer <= o.zMax && zMax + buffer >= o.zMin;
        }
        public boolean contains(int x, int z) {
            return x >= xMin && x <= xMax && z >= zMin && z <= zMax;
        }
    }

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;

    final Set<Long> streetCells = new HashSet<>();
    final List<Footprint> occupied = new ArrayList<>();
    final List<POI> pois = new ArrayList<>();

    public ElikiumCity(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
    }

    public List<POI> getPois() { return pois; }

    static long packCoord(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static boolean insideCathedralZone(int x, int z) {
        return x >= CATHEDRAL_X_MIN && x <= CATHEDRAL_X_MAX
            && z >= CATHEDRAL_Z_MIN && z <= CATHEDRAL_Z_MAX;
    }

    /** Полная застройка города: улицы, площади, POI, дома, декор. */
    public void build() {
        plugin.getLogger().info("ElikiumCity: строю улицы, площади, POI, дома, декор…");

        long ops = 0;
        ElikiumStreets streets = new ElikiumStreets(plugin, painter, rng, this);
        ops += streets.build();

        ElikiumPlazas plazas = new ElikiumPlazas(plugin, painter, rng, this);
        ops += plazas.build();

        ElikiumNamedBuildings named = new ElikiumNamedBuildings(plugin, painter, rng, this);
        ops += named.build();

        ElikiumHouses houses = new ElikiumHouses(plugin, painter, rng, this);
        ops += houses.build();

        ElikiumDecor decor = new ElikiumDecor(plugin, painter, rng, this);
        ops += decor.build();

        plugin.getLogger().info("ElikiumCity: ~" + ops
                + " блок-операций (улицы, площади, " + pois.size()
                + " POI/домов, декор).");
    }
}
