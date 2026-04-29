package ru.eclipsia.builder.generator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Низкоуровневый «художник по миру»: накапливает операции (заливка региона,
 * круг, линия-тропа, разброс декораций) в очередь и применяет их асинхронными
 * батчами, чтобы не фризить TPS при генерации больших локаций.
 *
 * <p>Все геометрические операции работают в плоскости XZ и принимают абсолютные
 * мировые координаты. Блоки выставляются с {@code applyPhysics=false}, что
 * позволяет ставить «висящие» декорации (травы, факелы) без падений.
 *
 * <p>Перед началом работы вызвать {@link #begin()}, после описания всей сцены —
 * {@link #flush(Runnable)}. Между ними можно вызывать любые методы рисования
 * в любом порядке. Каждый блок попадает в очередь как операция-функция,
 * вычисляющая {@link BlockData} в момент применения — это позволяет
 * рандомизировать состав поверхности.
 */
public final class RegionPainter {

    /** Сколько блоков ставить за один тик. Подобрано так, чтобы tick stay <50ms. */
    private static final int BLOCKS_PER_TICK = 4_000;

    private final Plugin plugin;
    private final World world;
    private final Random rng;
    private final Deque<BlockOp> ops = new ArrayDeque<>();

    public RegionPainter(Plugin plugin, World world, long seed) {
        this.plugin = plugin;
        this.world = world;
        this.rng = new Random(seed);
    }

    public World world() {
        return world;
    }

    public Random rng() {
        return rng;
    }

    /** Очистить очередь — на случай повторного использования. */
    public void begin() {
        ops.clear();
    }

    /**
     * Запустить асинхронную заливку. Каждый тик обрабатывается до
     * {@link #BLOCKS_PER_TICK} операций, по завершении вызывается
     * {@code onFinish} (опционально, может быть {@code null}).
     */
    public void flush(Runnable onFinish) {
        final int total = ops.size();
        plugin.getLogger().info("RegionPainter: запланировано " + total + " блок-операций");
        new BukkitRunnable() {
            int placed = 0;

            @Override
            public void run() {
                int budget = BLOCKS_PER_TICK;
                while (budget-- > 0 && !ops.isEmpty()) {
                    BlockOp op = ops.pollFirst();
                    op.apply(world);
                    placed++;
                }
                if (ops.isEmpty()) {
                    plugin.getLogger().info("RegionPainter: завершено (" + placed + "/" + total + ")");
                    cancel();
                    if (onFinish != null) onFinish.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ===================== Примитивы =====================

    /**
     * Заполнить прямоугольный параллелепипед. Координаты включительные.
     * Если {@code overwriteAir} = false — блоки воздуха не трогаем (для слоёв).
     */
    public void fillBox(int x1, int y1, int z1, int x2, int y2, int z2,
                        Supplier<Material> mat) {
        int xMin = Math.min(x1, x2), xMax = Math.max(x1, x2);
        int yMin = Math.min(y1, y2), yMax = Math.max(y1, y2);
        int zMin = Math.min(z1, z2), zMax = Math.max(z1, z2);
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    enqueue(x, y, z, mat);
                }
            }
        }
    }

    /** Заполнить горизонтальный диск (в плоскости XZ) на одной y-координате. */
    public void fillDisk(int cx, int y, int cz, int radius, Supplier<Material> mat) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) {
                    enqueue(cx + dx, y, cz + dz, mat);
                }
            }
        }
    }

    /** Заполнить кольцо (XZ): радиус-область между rInner и rOuter. */
    public void fillRing(int cx, int y, int cz, int rInner, int rOuter, Supplier<Material> mat) {
        int rIn2 = rInner * rInner, rOut2 = rOuter * rOuter;
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 >= rIn2 && d2 <= rOut2) {
                    enqueue(cx + dx, y, cz + dz, mat);
                }
            }
        }
    }

    /**
     * Линия в плоскости XZ заданной ширины. Используется для троп, дорог,
     * рек. {@code y} — единая высота, {@code halfWidth} — полуширина (общая
     * ширина = 2*halfWidth+1).
     */
    public void path(int x1, int z1, int x2, int z2, int y, int halfWidth,
                     Supplier<Material> mat) {
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        int cx = x1, cz = z1;
        while (true) {
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                    enqueue(cx + ox, y, cz + oz, mat);
                }
            }
            if (cx == x2 && cz == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 < dx)  { err += dx; cz += sz; }
        }
    }

    /**
     * Заполнить вертикальный «забор-стену» вдоль линии XZ.
     * {@code yBase} — нижний уровень, {@code height} — высота стены.
     */
    public void wall(int x1, int z1, int x2, int z2, int yBase, int height,
                     Supplier<Material> mat) {
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        int cx = x1, cz = z1;
        while (true) {
            for (int dy = 0; dy < height; dy++) {
                enqueue(cx, yBase + dy, cz, mat);
            }
            if (cx == x2 && cz == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; cx += sx; }
            if (e2 < dx)  { err += dx; cz += sz; }
        }
    }

    /**
     * Случайно разбросать декорации (одиночные блоки) внутри диска.
     * {@code chance} ∈ [0..1] — вероятность каждой клетки.
     */
    public void scatterInDisk(int cx, int y, int cz, int radius, double chance,
                              Supplier<Material> mat) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                if (rng.nextDouble() < chance) {
                    enqueue(cx + dx, y, cz + dz, mat);
                }
            }
        }
    }

    /** Поставить один блок. */
    public void place(int x, int y, int z, Material mat) {
        enqueue(x, y, z, () -> mat);
    }

    /** Поставить блок с произвольным {@link BlockData} (для facing/waterlogged/...). */
    public void placeData(int x, int y, int z, BlockData data) {
        ops.addLast(new BlockOp(x, y, z, null, data));
    }

    /**
     * Разместить высокую вертикальную колонну.
     */
    public void column(int x, int yBase, int z, int height, Supplier<Material> mat) {
        for (int dy = 0; dy < height; dy++) {
            enqueue(x, yBase + dy, z, mat);
        }
    }

    /**
     * Очистить вертикальный объём до воздуха (например, перед постройкой).
     */
    public void clearAir(int x1, int y1, int z1, int x2, int y2, int z2) {
        fillBox(x1, y1, z1, x2, y2, z2, () -> Material.AIR);
    }

    /**
     * Соорудить «холм» — конус из выбранного материала. Используется для
     * горных хребтов и кочек в лесу.
     */
    public void hill(int cx, int yBase, int cz, int radius, int height,
                     Supplier<Material> mat) {
        for (int dy = 0; dy < height; dy++) {
            int rAtY = (int) Math.round(radius * (1.0 - (double) dy / height));
            if (rAtY < 1) rAtY = 1;
            int r2 = rAtY * rAtY;
            for (int dx = -rAtY; dx <= rAtY; dx++) {
                for (int dz = -rAtY; dz <= rAtY; dz++) {
                    if (dx * dx + dz * dz <= r2) {
                        enqueue(cx + dx, yBase + dy, cz + dz, mat);
                    }
                }
            }
        }
    }

    // ===================== Внутренности =====================

    private void enqueue(int x, int y, int z, Supplier<Material> mat) {
        ops.addLast(new BlockOp(x, y, z, mat, null));
    }

    /** Атомарная операция: «поставить блок типа M в точке (x,y,z)». */
    private static final class BlockOp {
        final int x, y, z;
        final Supplier<Material> mat;   // динамически вычисляемый материал
        final BlockData data;           // или фиксированный BlockData

        BlockOp(int x, int y, int z, Supplier<Material> mat, BlockData data) {
            this.x = x; this.y = y; this.z = z;
            this.mat = mat; this.data = data;
        }

        void apply(World world) {
            Block b = world.getBlockAt(x, y, z);
            if (data != null) {
                b.setBlockData(data, false);
            } else if (mat != null) {
                Material m = mat.get();
                if (m != null && b.getType() != m) {
                    b.setType(m, false);
                }
            }
        }
    }

    /**
     * Утилита: «взвешенный» Supplier из набора материалов.
     * Используется для шумной поверхности (BLACKSTONE 70%, BASALT 20%, ...).
     */
    public static Supplier<Material> weighted(Random rng, Object... pairs) {
        if ((pairs.length & 1) != 0) {
            throw new IllegalArgumentException("weighted: ожидаются пары (Material, weight)");
        }
        List<Material> mats = new ArrayList<>(pairs.length / 2);
        int[] weights = new int[pairs.length / 2];
        int total = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            mats.add((Material) pairs[i]);
            int w = ((Number) pairs[i + 1]).intValue();
            weights[i / 2] = w;
            total += w;
        }
        final int totalFinal = total;
        return () -> {
            int r = rng.nextInt(totalFinal);
            int acc = 0;
            for (int i = 0; i < weights.length; i++) {
                acc += weights[i];
                if (r < acc) return mats.get(i);
            }
            return mats.get(mats.size() - 1);
        };
    }

    /** Утилита: helper-метод чтобы создать {@link Location} в текущем мире. */
    public Location loc(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** Регистрация на main-thread пакетного применения через Bukkit (если нужен). */
    public void runOnMain(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    /**
     * Найти высоту первого твёрдого (не воздух/не вода) блока сверху вниз.
     * Возвращает {@code y} верхнего твёрдого блока или {@code fallback} если
     * не нашли в диапазоне {@code [yMin..yMax]}.
     *
     * <p>Использовать после того, как ландшафт уже залит на ОСНОВНОМ потоке
     * (т.е. после {@link #flush(Runnable)} ландшафтной фазы) — иначе данные
     * мира могут быть ещё не консистентны.
     */
    public int getSurfaceY(int x, int z, int yMax, int yMin, int fallback) {
        for (int y = yMax; y >= yMin; y--) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (m != Material.AIR && m != Material.CAVE_AIR
                    && m != Material.VOID_AIR && m != Material.WATER) {
                return y;
            }
        }
        return fallback;
    }

    /** Удобная перегрузка: ищет в диапазоне y=200..-64, fallback = 4. */
    public int getSurfaceY(int x, int z) {
        return getSurfaceY(x, z, 200, -64, 4);
    }
}
