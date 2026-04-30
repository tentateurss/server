package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Южная горная гряда — каменистый барьер, окаймляющий город Эликий с юга.
 *
 * <p><b>Геометрия</b>:
 * <ul>
 *   <li>Полоса по {@code z &gt; 50}, {@code x ∈ [-200, 200]} — это область
 *       за южными воротами, начинающаяся на 15 блоков южнее точки спавна
 *       игрока (z=38) и доходящая до z≈200 в самом низком случае.</li>
 *   <li>Высота: 50-60 блоков над baseline (y=70). Пик 130 — выше любого
 *       обычного дерева, видно с любой точки города.</li>
 *   <li>Профиль: {@link SimplexOctaveGenerator} с 4 октавами — даёт
 *       плавные хребты с второстепенными пиками. Ущелья в местах низких
 *       значений шума — это и есть «проход» в горы для PR 4 (дороги).</li>
 *   <li>Чем дальше от {@code z=50} в плюс — тем выше поднимается рельеф;
 *       это нужно, чтобы был плавный подъём от равнины города к пикам,
 *       а не «обрыв».</li>
 * </ul>
 *
 * <p><b>Материалы</b>:
 * <ul>
 *   <li>{@code y=70..72} — внешний фундамент: чередование STONE/COBBLESTONE
 *       (создаёт «осыпь» у подножия).</li>
 *   <li>{@code y=72..pikY-3} — основной массив STONE с прожилками
 *       ANDESITE (10%) и DEEPSLATE (5% при y&lt;90) — даёт «слоистую»
 *       визуальную текстуру.</li>
 *   <li>{@code peakY-3..peakY-1} — слой DIRT (под травой).</li>
 *   <li>{@code peakY} — GRASS_BLOCK (если pikY &lt; 110), либо STONE
 *       (если pikY ≥ 110, верхушка-голая-скала).</li>
 *   <li>На очень высоких пиках (peakY ≥ 120) последние 1-2 блока — SNOW_BLOCK.</li>
 * </ul>
 *
 * <p><b>Производительность</b>: полоса 401×~150 блоков при средней высоте
 * 30 блоков ≈ 1.8M операций RegionPainter, ~7-8 минут на старте свежего
 * мира. Это разовая стоимость; PDC-маркер
 * {@link WorldGenerator#GENERATED_FLAG} не даст повториться.
 *
 * <p><b>Что НЕ делает</b>: пещеры, руды, реки/ручьи (это всё PR 5),
 * деревья на склонах (PR 5), декорации (PR 5).
 */
public final class WorldMountains {

    /** Полоса гор по Z: от z=130 (80 блоков южнее стены z≈100) до z=380. */
    private static final int Z_MIN = 130;
    private static final int Z_MAX = 380;

    /** По X — от x=-400 до x=400 (видны из любой точки города). */
    private static final int X_MIN = -400;
    private static final int X_MAX =  400;

    /** Базовый y, на котором сидит вся flatSettings-травa. */
    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70

    /** Минимальная и максимальная высота вершин над baseline. */
    private static final int PEAK_MIN = 30;  // y≈100, низкие холмы у подножия
    private static final int PEAK_MAX = 100; // y≈170, главные пики

    private final Plugin plugin;
    private final RegionPainter painter;
    private final SimplexOctaveGenerator noise;

    public WorldMountains(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.noise = new SimplexOctaveGenerator(rng.nextLong(), 4);
        // scale маленький → крупные складки, большой → мелкая зернистость.
        // 0.008 под ×2-масштаб даёт хребты «ярких» размеров.
        this.noise.setScale(0.008);
    }

    public void build() {
        plugin.getLogger().info(
                "WorldMountains: строю южную гряду z="
                + Z_MIN + ".." + Z_MAX
                + ", x=" + X_MIN + ".." + X_MAX + "…");

        long ops = 0;
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = Z_MIN; z <= Z_MAX; z++) {
                int peakY = computePeakY(x, z);
                if (peakY <= Y_BASE) continue; // ниже земли — не строим

                ops += stackColumn(x, z, peakY);
            }
        }
        plugin.getLogger().info(
                "WorldMountains: подготовлено " + ops + " блок-операций.");
    }

    /**
     * Высота пика в столбце {@code (x, z)}. Учитывает базовый шум +
     * «нарастание» от Z_MIN к Z_MAX (плавный подъём от равнины к
     * центру гряды) + «спад» по краям X (горы сужаются у границ).
     */
    private int computePeakY(int x, int z) {
        // Базовый шум [-1..1].
        double n = noise.noise(x, z, 0.5, 0.5, true);

        // Нормировка к [0..1].
        double n01 = (n + 1.0) / 2.0;

        // Профиль по Z: ramp от 0 (на z=Z_MIN) до 1 (на середине) и обратно
        // до 0.3 (на z=Z_MAX). Это даёт «прижатую к городу» гряду.
        double zRel = (double) (z - Z_MIN) / (Z_MAX - Z_MIN); // [0..1]
        double zRamp;
        if (zRel < 0.5) {
            zRamp = zRel * 2.0; // 0..1
        } else {
            zRamp = 1.0 - (zRel - 0.5) * 1.4; // 1..0.3
        }
        zRamp = Math.max(0.0, Math.min(1.0, zRamp));

        // Профиль по X: спад на краях.
        double xRel = (double) Math.abs(x) / X_MAX; // [0..1]
        double xRamp = 1.0 - xRel * xRel * 0.6; // 0.4..1.0

        double height = (PEAK_MIN + n01 * (PEAK_MAX - PEAK_MIN))
                * zRamp * xRamp;

        // Перерывы: если шум очень низкий, оставить «ущелье» (гора пропущена).
        if (n01 < 0.18) return Y_BASE; // низина — пропускаем столб

        return Y_BASE + (int) Math.round(height);
    }

    /**
     * Поставить столб блоков от {@code y = Y_BASE + 1} до {@code peakY}.
     * Возвращает число операций, чтобы можно было их подсчитать в логе.
     */
    private long stackColumn(int x, int z, int peakY) {
        long count = 0;

        // Слой осыпи у подножия (y=70..72) — оверlay на flatSettings-grass.
        for (int y = Y_BASE + 1; y <= Y_BASE + 2 && y < peakY; y++) {
            painter.place(x, y, z,
                    ((x + z) & 1) == 0 ? Material.STONE : Material.COBBLESTONE);
            count++;
        }

        // Основной массив скалы.
        for (int y = Y_BASE + 3; y < peakY - 3; y++) {
            Material m = pickRockMaterial(x, y, z);
            painter.place(x, y, z, m);
            count++;
        }

        // Земляной слой.
        for (int y = Math.max(Y_BASE + 3, peakY - 3); y < peakY; y++) {
            painter.place(x, y, z, Material.DIRT);
            count++;
        }

        // Поверхность.
        Material surface;
        if (peakY >= Y_BASE + 50) { // y ≥ 120 — снежная вершина
            surface = Material.SNOW_BLOCK;
        } else if (peakY >= Y_BASE + 40) { // y ≥ 110 — голая скала
            surface = Material.STONE;
        } else {
            surface = Material.GRASS_BLOCK;
        }
        painter.place(x, peakY, z, surface);
        count++;

        return count;
    }

    /**
     * Выбор материала для основного массива. Простая псевдо-стохастика
     * через гэш координат (детерминированно, стабильно между запусками).
     */
    private Material pickRockMaterial(int x, int y, int z) {
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int bucket = Math.floorMod(hash, 100);
        if (y < Y_BASE + 20 && bucket < 5) return Material.DEEPSLATE;
        if (bucket < 10) return Material.ANDESITE;
        if (bucket < 12) return Material.GRAVEL;
        return Material.STONE;
    }
}
