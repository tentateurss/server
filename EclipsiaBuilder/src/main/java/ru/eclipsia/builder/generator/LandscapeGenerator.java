package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;

/**
 * Процедурный генератор ландшафта Берега на основе Simplex-шума
 * (встроенный {@link SimplexNoiseGenerator} Bukkit — 0 зависимостей).
 *
 * <p>Использует 4 шумовых поля:
 * <ul>
 *   <li>{@link #heightNoise} (freq 0.005) — крупные формы рельефа;</li>
 *   <li>{@link #detailNoise} (freq 0.030) — мелкие детали поверхности;</li>
 *   <li>{@link #caveNoise} (freq 0.040) — 3D-пещеры в горах/лесу;</li>
 *   <li>{@link #biomeNoise} (freq 0.003) — границы между зонами.</li>
 * </ul>
 *
 * <p>Зоны идут с севера на юг (по оси Z):
 * <pre>
 *   z &lt; -100     → ocean    (вода + мелкие волны)
 *   z = -100..-50  → beach    (чёрный песок + дюны ±3)
 *   z = -50..100   → forest   (подзол + холмы ±11)
 *   z &gt; 100      → mountain (камень + подъём до +45)
 * </pre>
 *
 * <p>Используется {@link BeachGenerator} как первая фаза генерации:
 * перед расстановкой декораций ландшафт «прокатывается» по всему региону.
 */
public final class LandscapeGenerator {

    /** Базовая высота поверхности (минимум). */
    public static final int BASE_Y = 4;
    /** Бедрок-низ. */
    public static final int BEDROCK_Y = -64;

    private final World world;
    private final SimplexNoiseGenerator heightNoise;
    private final SimplexNoiseGenerator detailNoise;
    private final SimplexNoiseGenerator caveNoise;
    private final SimplexNoiseGenerator biomeNoise;

    public LandscapeGenerator(World world, long seed) {
        this.world = world;
        Random rng = new Random(seed);
        this.heightNoise = new SimplexNoiseGenerator(rng.nextLong());
        this.detailNoise = new SimplexNoiseGenerator(rng.nextLong());
        this.caveNoise   = new SimplexNoiseGenerator(rng.nextLong());
        this.biomeNoise  = new SimplexNoiseGenerator(rng.nextLong());
    }

    public World getWorld() {
        return world;
    }

    /** Зона по координатам (с переходом по biomeNoise для естественной границы). */
    public Zone getZone(int x, int z) {
        // Шумовая «дрожь» границы зон ±20 блоков
        double jitter = biomeNoise.noise(x * 0.003, z * 0.003) * 20.0;
        double zz = z + jitter;
        // Дополнительно: всё, что дальше playable-радиуса по X (|x| > 130
        // с шумовым размазыванием) — океан, чтобы локация чувствовалась
        // как «остров посреди бесконечного моря».
        double xJitter = biomeNoise.noise(z * 0.0035, x * 0.0035) * 25.0;
        double absX = Math.abs(x) + xJitter;
        if (absX > 130) return Zone.OCEAN;
        if (zz < -90) return Zone.OCEAN;
        if (zz <= -50) return Zone.BEACH;
        if (zz <= 110) return Zone.FOREST;
        return Zone.MOUNTAIN;
    }

    /**
     * Высота поверхности (верхний твёрдый блок) для столба (x,z).
     * Гарантирует {@code y >= BASE_Y}.
     */
    public int getHeight(int x, int z) {
        Zone zone = getZone(x, z);
        double h = heightNoise.noise(x * 0.005, z * 0.005);          // [-1..1]
        double d = detailNoise.noise(x * 0.030, z * 0.030);           // [-1..1]

        return switch (zone) {
            case OCEAN -> {
                // ОЧЕНЬ глубокое дно. Реальный океан с провалами.
                // База -22..-28 + рябь на дне.
                int y = -22 + (int) Math.round(h * 4.0 + d * 2.0);
                yield Math.max(BEDROCK_Y + 4, y);
            }
            case BEACH -> {
                // База +1, дюны ±3.
                int y = BASE_Y + 1 + (int) Math.round(h * 2.0 + d * 1.0);
                yield Math.max(BASE_Y, y);
            }
            case FOREST -> {
                // Холмы ±9 от base+2 — мягче, чем раньше.
                int y = BASE_Y + 2 + (int) Math.round(h * 7.0 + d * 3.0);
                yield Math.max(BASE_Y, y);
            }
            case MOUNTAIN -> {
                // Плавный пологий хребет (раньше резко вверх до +45 — «волна»).
                // climb растёт по 100..170 от 0 до 1, плато после 170.
                double climb = Math.min(1.0, Math.max(0, z - 110) / 60.0);
                // Ridge-noise (1-|n|) для острых пиков.
                double ridge = 1.0 - Math.abs(h);
                int y = BASE_Y + 2 + (int) Math.round(
                        climb * 18.0          // плавный подъём
                        + ridge * climb * 14  // пики только в дальней части
                        + d * 3.0);
                yield Math.max(BASE_Y, y);
            }
        };
    }

    /** Материал поверхности для зоны. */
    public Material getSurfaceBlock(int x, int z) {
        return switch (getZone(x, z)) {
            case OCEAN -> Material.WATER;
            case BEACH -> Material.BLACK_CONCRETE_POWDER;
            case FOREST -> Material.PODZOL;
            case MOUNTAIN -> Material.STONE;
        };
    }

    /**
     * 3D-пещера? Только в FOREST/MOUNTAIN, не трогаем последние 3 блока
     * под поверхностью и не трогаем сам поверхностный слой.
     */
    public boolean isCave(int x, int y, int z) {
        Zone zone = getZone(x, z);
        if (zone == Zone.OCEAN || zone == Zone.BEACH) return false;

        int surface = getHeight(x, z);
        if (y >= surface - 3) return false; // защитный слой
        if (y <= BEDROCK_Y + 2) return false;

        double n = caveNoise.noise(x * 0.04, y * 0.04, z * 0.04);
        return n > 0.30;
    }

    /**
     * Сгенерировать столб в (x,z) от бедрока до поверхности.
     * Безопасно для использования вне основного потока — только setBlockData
     * с physics=false.
     *
     * <p>Слои:
     * <pre>
     *   y = BEDROCK_Y .. BEDROCK_Y+1 — BEDROCK
     *   y = BEDROCK_Y+2 .. surface-3 — STONE (с пещерами AIR)
     *   y = surface-2 .. surface-1   — DIRT
     *   y = surface                  — getSurfaceBlock()
     *   y = surface+1.. (для OCEAN до BASE_Y+1) — WATER заливка
     * </pre>
     */
    public void generateColumn(World w, int x, int z) {
        Zone zone = getZone(x, z);
        int surface = getHeight(x, z);

        // Бедрок (2 слоя).
        w.getBlockAt(x, BEDROCK_Y, z).setType(Material.BEDROCK, false);
        w.getBlockAt(x, BEDROCK_Y + 1, z).setType(Material.BEDROCK, false);

        // Камень/пещеры до surface-3.
        for (int y = BEDROCK_Y + 2; y < surface - 2; y++) {
            Material mat = isCave(x, y, z) ? Material.AIR : Material.STONE;
            w.getBlockAt(x, y, z).setType(mat, false);
        }
        // Слой земли.
        for (int y = Math.max(BEDROCK_Y + 2, surface - 2); y < surface; y++) {
            if (zone == Zone.OCEAN) {
                w.getBlockAt(x, y, z).setType(Material.GRAVEL, false);
            } else {
                w.getBlockAt(x, y, z).setType(Material.DIRT, false);
            }
        }
        // Поверхность.
        w.getBlockAt(x, surface, z).setType(getSurfaceBlock(x, z), false);

        // Океан: залить воду до BASE_Y+1 включительно (зеркало воды).
        if (zone == Zone.OCEAN) {
            for (int y = surface + 1; y <= BASE_Y + 1; y++) {
                w.getBlockAt(x, y, z).setType(Material.WATER, false);
            }
        }
    }

    /** Зоны Берега. */
    public enum Zone { OCEAN, BEACH, FOREST, MOUNTAIN }
}
