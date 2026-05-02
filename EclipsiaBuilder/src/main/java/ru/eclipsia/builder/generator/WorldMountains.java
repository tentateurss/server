package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Горный массив, окружающий город Эликий со ВСЕХ сторон. Город Эликий
 * лежит в горной долине, плотно зажатой утёсами; единственный широкий
 * проход — южный каньон, ведущий от точки спавна игрока (z=118) прямо
 * к южным воротам (z=113). На севере, востоке и западе горы подходят
 * вплотную к стенам (5-9 блоков от стены до подножия скалы), отрезая
 * любые «островные» виды на пустоту.
 *
 * <p><b>Геометрия (PR 4 — горная долина)</b>:
 * <ul>
 *   <li>Покрытие: квадрат {@code x ∈ [-600..600], z ∈ [-600..600]}.
 *       Внутри городского полигона ничего не строится (там мостовая).</li>
 *   <li>Вокруг полигона — «скирт» рокота: пологий подъём 0..15 блоков
 *       начинается в 6-9 блоках от стены и переходит в крутой склон.</li>
 *   <li>{@link #ridgeAmplitude(int, int)} даёт максимальную высоту скалы
 *       над baseline — у стен 35-50 блоков, дальше до 90-130 (пики выше
 *       облаков).</li>
 *   <li>Южный каньон: коридор шириной 60 блоков (x∈[-30..30]) от стены
 *       до z≈260. Дно каньона = {@link #CITY_FLOOR_Y}, стенки каньона
 *       обрывистые (rock cliff), в стиле референса (тёмное узкое
 *       ущелье с фонарями и статуями).</li>
 *   <li>Северный, восточный и западный обводы плотно прилегают к стенам.
 *       Между стеной и подножием скалы остаётся «беговая дорожка»
 *       шириной {@link #SKIRT_WIDTH} = 7 блоков для FloatingText/декора.</li>
 * </ul>
 *
 * <p><b>Материалы</b>:
 * <ul>
 *   <li>{@code y=70..72} — DEEPSLATE/COBBLED_DEEPSLATE (тёмная осыпь
 *       у подножия, под цвет стен и собора).</li>
 *   <li>{@code y=73..peakY-3} — COBBLED_DEEPSLATE с прожилками
 *       DEEPSLATE/TUFF/AMETHYST_BLOCK (готические аметистовые жилы
 *       как на референсе).</li>
 *   <li>{@code peakY-3..peakY-1} — DIRT под голой скалой / SNOW_BLOCK
 *       на самых высоких пиках.</li>
 *   <li>{@code peakY} — STONE (голый утёс, без травы — мрачно как
 *       у Эликия).</li>
 *   <li>На вершинах ≥ y=150 последний блок — SNOW_BLOCK.</li>
 * </ul>
 *
 * <p><b>Производительность</b>: квадрат 1201×1201 столбов с пропуском
 * городского полигона ≈ 1.4M столбов. При средней высоте 25-30 блоков
 * в горах — ~25M операций RegionPainter (заметно дороже старой v9
 * версии, но критично для атмосферы). Выполняется однократно при
 * первом запуске мира; маркер {@link WorldGenerator#GENERATED_FLAG} v29+
 * не даёт перегенерировать.
 */
public final class WorldMountains {

    /** Граница ландшафта по обеим осям — мир «бесконечен» за этим квадратом
     *  (flat-settings продолжают давать y=70 grass, что нас устраивает).
     *  v30: сжато с ±550 до ±260 чтобы не выжирать heap. Город ±150,
     *  скирт ещё +25, потом 80 блоков «горного кольца» — итого 260. */
    private static final int X_MIN = -260;
    private static final int X_MAX =  260;
    private static final int Z_MIN = -260;
    private static final int Z_MAX =  290; // +30 на юге для каньона до z=280

    /** Максимальная дистанция от стены, на которой ещё имеет смысл
     *  что-то ставить. Дальше — невидимо при render distance ~10
     *  и просто жрёт RAM. */
    private static final int MAX_DIST_FROM_WALL = 105;

    /** Базовый y, на котором сидит вся flatSettings-травa. */
    private static final int Y_BASE = WorldGenerator.CITY_FLOOR_Y; // 70

    /**
     * Расстояние от стены полигона до подножия скал. Внутри этого
     * «скирта» — мостовая POLISHED_DEEPSLATE/ANDESITE (чтобы у игрока
     * был ровный кольцевой бульвар вдоль внутренней стороны стены).
     * Снаружи скирта — постепенный подъём в скалу.
     */
    private static final int SKIRT_WIDTH = 7;

    /** Полу-ширина южного каньона по X (от центра 0). */
    private static final int CANYON_HALF_WIDTH = 30;
    /** Сколько блоков на юг от стены прорезает каньон. */
    private static final int CANYON_DEPTH = 160; // z=120..280 (подальше открытое плато)

    /** Минимальная и максимальная высота вершин над baseline. */
    private static final int PEAK_NEAR  = 50;  // y=120 — близкие к стенам утёсы
    private static final int PEAK_FAR   = 130; // y=200 — главные пики далеко

    private final Plugin plugin;
    private final RegionPainter painter;
    private final SimplexOctaveGenerator noise;
    private final SimplexOctaveGenerator detail;
    private final Random rng;

    public WorldMountains(Plugin plugin, RegionPainter painter, Random rng) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.noise  = new SimplexOctaveGenerator(rng.nextLong(), 4);
        this.detail = new SimplexOctaveGenerator(rng.nextLong(), 2);
        this.noise.setScale(0.012);
        this.detail.setScale(0.06);
    }

    public void build() {
        plugin.getLogger().info(
                "WorldMountains: строю горный обвод вокруг Эликия "
                + "(x=" + X_MIN + ".." + X_MAX
                + ", z=" + Z_MIN + ".." + Z_MAX
                + ", maxDist=" + MAX_DIST_FROM_WALL + ")…");

        long ops = 0;
        long skipped = 0;
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = Z_MIN; z <= Z_MAX; z++) {
                // Внутри городского полигона ничего не строим — там
                // мостовая POLISHED_DEEPSLATE из phase1.
                if (WorldGenerator.isInsideCityPolygon(x, z)) continue;

                // Жёсткий cap на дистанцию: дальше MAX_DIST_FROM_WALL
                // мы вне видимости и просто едим heap. Однако: если
                // столб попал в южный каньон (он длинный), пропускать
                // нельзя — тогда стенки каньона не построятся.
                double distToWall = WorldGenerator.distanceToCityPolygon(x, z);
                boolean inCanyonZone = (z >= 120 && z <= 120 + CANYON_DEPTH
                        && Math.abs(x) <= CANYON_HALF_WIDTH + 30);
                if (distToWall > MAX_DIST_FROM_WALL && !inCanyonZone) {
                    skipped++;
                    continue;
                }

                int peakY = computePeakY(x, z);
                if (peakY <= Y_BASE) continue; // в плоской долине ничего не ставим

                ops += stackColumn(x, z, peakY);
            }
        }
        plugin.getLogger().info(
                "WorldMountains: подготовлено " + ops + " блок-операций, "
                + "пропущено " + skipped + " далёких столбов "
                + "(горный обвод + южный каньон).");
    }

    /**
     * Высота пика для столбца {@code (x, z)} с учётом близости к
     * городскому полигону, направления (юг/прочее) и шума.
     *
     * <p>Логика:
     * <ol>
     *   <li>Если столбец принадлежит «беговой дорожке» (расстояние до
     *       полигона ≤ {@link #SKIRT_WIDTH}) — высота = baseline (плоско).</li>
     *   <li>Если столбец принадлежит южному каньону (см.
     *       {@link #insideSouthCanyon}) — высота = baseline + лёгкая
     *       рябь дна каньона (мостовая каменная, плоская).</li>
     *   <li>Иначе — растущий профиль от подножия (skirt edge) до
     *       плато гор (peak зависит от {@link #ridgeAmplitude}).</li>
     * </ol>
     */
    private int computePeakY(int x, int z) {
        double distToWall = WorldGenerator.distanceToCityPolygon(x, z);

        // 1) Беговая дорожка вдоль стены — плоско (мостовая).
        if (distToWall <= SKIRT_WIDTH) {
            return Y_BASE;
        }

        // 2) Южный каньон — плоское дно, скалы только за стенками.
        if (insideSouthCanyon(x, z, distToWall)) {
            return Y_BASE; // плоская канва каньона
        }

        // 3) Скирт-склон: первые 18 блоков от стены — пологий подъём
        //    (foothills). Дальше — настоящие горы.
        double skirtT = Math.min(1.0, (distToWall - SKIRT_WIDTH) / 18.0);
        double skirtRise = skirtT * skirtT * 12.0; // 0..12 блоков плавного подъёма

        // 4) Базовый шум для пика. На границах каньона (cz ~ CANYON_HALF_WIDTH)
        //    высота скал максимальна — это «обрывы» каньона.
        double n = noise.noise(x, z, 0.5, 0.5, true); // [-1..1]
        double n01 = (n + 1.0) / 2.0;

        // Ridge-noise (1-|n|) для острых хребтов как на референсе.
        double ridge = 1.0 - Math.abs(n);

        // Пики выше вдалеке от города (gameplay-открытость близких видов).
        double farRamp = Math.min(1.0, distToWall / 80.0); // 0..1 за 80 блоков
        double peakAmp = PEAK_NEAR + farRamp * (PEAK_FAR - PEAK_NEAR);

        // Стенки каньона особенно высоки — обрывистые скалы по бокам корридора.
        double canyonBoost = canyonWallBoost(x, z);

        double height = skirtRise
                      + n01 * peakAmp * 0.5
                      + ridge * peakAmp * 0.5
                      + canyonBoost
                      + detail.noise(x, z, 0.5, 0.5, true) * 4.0;

        // Шумовые «ущелья» внутри гор — местами пропускаем столб (низина).
        if (n01 < 0.16 && skirtT > 0.5 && canyonBoost < 1.0) return Y_BASE;

        return Y_BASE + (int) Math.round(height);
    }

    /**
     * В южном каньоне? Каньон — это «коридор» от южной стены города
     * на юг шириной {@code 2 * CANYON_HALF_WIDTH} и глубиной
     * {@link #CANYON_DEPTH}.
     */
    private static boolean insideSouthCanyon(int x, int z, double distToWall) {
        // Каньон стартует от южной вершины полигона (0, 120) и тянется
        // на CANYON_DEPTH блоков на юг. Игрок появляется на (0, 75, 130)
        // в начале каньона и идёт к воротам (0, 120) на север.
        if (z < 120) return false;
        if (z > 120 + CANYON_DEPTH) return false;
        if (Math.abs(x) > CANYON_HALF_WIDTH) return false;
        // Центральная часть каньона — пол всегда плоский.
        return distToWall > 0.0;
    }

    /**
     * Прибавка к высоте на стенках каньона: сразу за центральным коридором
     * (|x| > CANYON_HALF_WIDTH) и в пределах глубины каньона стенки
     * взлетают вверх особенно резко (это и есть «обрыв»).
     */
    private double canyonWallBoost(int x, int z) {
        if (z < 120 || z > 120 + CANYON_DEPTH) return 0.0;
        int absX = Math.abs(x);
        if (absX <= CANYON_HALF_WIDTH) return 0.0;
        if (absX >= CANYON_HALF_WIDTH + 30) return 0.0;
        // Линейный буст от 0 до +35 в первых 12 блоках за коридором,
        // потом плавный спад. Делает «утёсы».
        int over = absX - CANYON_HALF_WIDTH;
        if (over <= 12) {
            return over * 3.0; // 0..36
        } else {
            return Math.max(0.0, 36.0 - (over - 12) * 1.5); // 36..пада
        }
    }

    /** Амплитуда для не-каньонных мест (используется в noise scaling). */
    private double ridgeAmplitude(int x, int z) {
        double n = noise.noise(x, z, 0.5, 0.5, true);
        return PEAK_NEAR + ((n + 1.0) / 2.0) * (PEAK_FAR - PEAK_NEAR);
    }

    /**
     * Поставить столб блоков от {@code y = Y_BASE + 1} до {@code peakY}.
     * Под полностью плоскими местами (peakY == Y_BASE) ничего не делаем —
     * там уже flat-grass из ванилы или мостовая.
     */
    private long stackColumn(int x, int z, int peakY) {
        long count = 0;

        // Если высота равна baseline — ничего не строим (плоско).
        if (peakY <= Y_BASE) return 0;

        // Слой осыпи у подножия (y=70..72) — оверlay на flatSettings-grass.
        for (int y = Y_BASE + 1; y <= Y_BASE + 2 && y < peakY; y++) {
            painter.place(x, y, z,
                    ((x + z) & 1) == 0
                            ? Material.COBBLED_DEEPSLATE
                            : Material.DEEPSLATE);
            count++;
        }

        // Основной массив скалы.
        for (int y = Y_BASE + 3; y < peakY - 2; y++) {
            Material m = pickRockMaterial(x, y, z);
            painter.place(x, y, z, m);
            count++;
        }

        // Поверхность.
        Material surface;
        if (peakY >= Y_BASE + 80) { // y ≥ 150 — снежная вершина
            surface = Material.SNOW_BLOCK;
        } else if (peakY >= Y_BASE + 40) { // голая скала
            surface = Material.COBBLED_DEEPSLATE;
        } else {
            // Низкие холмы у самых стен — TUFF (тёмный, готический,
            // не контрастирует с травой ванилы).
            surface = ((x + z) & 1) == 0 ? Material.TUFF : Material.COBBLED_DEEPSLATE;
        }
        painter.place(x, peakY, z, surface);
        count++;

        // Декоративные аметистовые жилы на крутых склонах (1.5%).
        // Аметисты как на референсе — фиолетовые «трещины» в скалах.
        if (peakY >= Y_BASE + 25 && rng.nextInt(64) == 0) {
            int veinY = Y_BASE + 3 + rng.nextInt(peakY - Y_BASE - 5);
            painter.place(x, veinY, z, Material.AMETHYST_BLOCK);
            count++;
        }

        return count;
    }

    /**
     * Выбор материала для основного массива. Простая псевдо-стохастика
     * через хэш координат (детерминированно, стабильно между запусками).
     */
    private Material pickRockMaterial(int x, int y, int z) {
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int bucket = Math.floorMod(hash, 100);
        // Готическая палитра скал — без обычного STONE, чтобы цвет
        // совпадал со стенами Эликия.
        if (y < Y_BASE + 30 && bucket < 8) return Material.DEEPSLATE;
        if (bucket < 6) return Material.TUFF;
        if (bucket < 10) return Material.BLACKSTONE;
        if (bucket < 13) return Material.GRAVEL;
        return Material.COBBLED_DEEPSLATE;
    }
}
