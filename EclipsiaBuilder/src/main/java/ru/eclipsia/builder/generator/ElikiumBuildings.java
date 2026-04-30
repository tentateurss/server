package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Интерьер города Эликий: жилые/общественные здания, мощёные улицы,
 * рыночные прилавки, колодец. Делегируется из {@link WorldGenerator}'а
 * фазой 3 «город — наполнение». Стены и собор уже стоят к этому моменту
 * (PR 2, см. {@link ElikiumCityBuilder}).
 *
 * <p>Стиль: камень + тёмное дерево, фиолетовое стекло в магических местах,
 * золото — акцент на «государственных» зданиях (банк, гильдия). NPC-жителей
 * не спавним (вне Citizens их нечего повесить); вывески — через
 * {@link FloatingText}.
 *
 * <p>Расположение зданий по углам внутри стен 80×80:
 * <pre>
 *   СЗ (-25,-25): Таверна "Золотая кружка" 12×10 (2 этажа)
 *   СВ ( 25,-25): Кузница 10×8 (с горном)
 *   ЮЗ (-25, 25): Лавка артефактов 8×8
 *   ЮВ ( 25, 25): Гильдия искателей 12×10 (2 этажа)
 *   З  (-35, 0): Банк 8×6
 *   плюс 8 жилых домов 6×6 по периметру.
 *   Юг от собора: рынок (4 прилавка)
 *   Запад от собора: колодец 3×3
 *   Улицы 5–7 шириной POLISHED_DEEPSLATE.
 * </pre>
 */
public final class ElikiumBuildings {

    private static final int CX = WorldGenerator.CITY_X;
    private static final int CZ = WorldGenerator.CITY_Z;
    private static final int FLOOR_Y = WorldGenerator.CITY_FLOOR_Y;

    private final Plugin plugin;
    private final World world;

    public ElikiumBuildings(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void buildAll(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: фаза 3 — здания и улицы Эликия…");

        // Сначала улицы, потом здания: здания сажают свой пол поверх
        // мостовой и аккуратно перекрывают её.
        buildStreets(p);

        buildTavern(p);
        buildSmithy(p);
        buildArtifactsShop(p);
        buildAdventurersGuild(p);
        buildBank(p);
        buildHouses(p, rng);
        buildMarket(p);
        buildWell(p);
    }

    // =========================================================================
    // УЛИЦЫ (POLISHED_DEEPSLATE) — 4 радиальных проспекта от центра к воротам
    // =========================================================================

    /**
     * Прокладывает 4 главных проспекта (от собора к воротам) шириной 7 и
     * 4 диагональных подъезда шириной 5 к угловым кварталам. Площадь 30×30
     * вокруг собора уже залита {@link ElikiumCityBuilder}, остаётся
     * соединить её с воротами и угловыми зданиями.
     */
    private void buildStreets(RegionPainter p) {
        int yStreet = FLOOR_Y;
        int half = WorldGenerator.CITY_HALF;

        // Главные проспекты (от ±15 до ±40 по соответствующей оси), ширина 7.
        layStreetSegment(p, CX, 16, CX, half - 1, 3, yStreet); // юг
        layStreetSegment(p, CX, -(half - 1), CX, -16, 3, yStreet); // север
        layStreetSegment(p, 16, CZ, half - 1, CZ, 3, yStreet); // восток
        layStreetSegment(p, -(half - 1), CZ, -16, CZ, 3, yStreet); // запад

        // Диагонали к 4 угловым зданиям (через -15..-15 и т.п.). Ширина 2.
        for (int sx : new int[] { -1, 1 }) {
            for (int sz : new int[] { -1, 1 }) {
                int x1 = sx * 17;
                int z1 = sz * 17;
                int x2 = sx * 30;
                int z2 = sz * 30;
                layStreetSegment(p, x1, z1, x2, z2, 2, yStreet);
            }
        }
    }

    private void layStreetSegment(RegionPainter p, int x1, int z1, int x2, int z2,
                                   int halfWidth, int yStreet) {
        // Bresenham + ширина: используем path() из RegionPainter.
        p.path(x1, z1, x2, z2, yStreet, halfWidth, () -> Material.POLISHED_DEEPSLATE);
        // Каждые 8 блоков на проспектах ставим уличный фонарь
        // (OAK_FENCE столб + SOUL_LANTERN). Только если halfWidth>=3,
        // т.е. на главных проспектах.
        if (halfWidth >= 3) {
            placeLamps(p, x1, z1, x2, z2, halfWidth, yStreet);
        }
    }

    private void placeLamps(RegionPainter p, int x1, int z1, int x2, int z2,
                             int halfWidth, int yStreet) {
        int dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return;
        double ux = dx / len, uz = dz / len;
        // Перпендикуляр (для смещения фонаря к краю мостовой).
        double px = -uz, pz = ux;
        for (double t = 8; t < len - 1; t += 8) {
            int cx = (int) Math.round(x1 + ux * t);
            int cz = (int) Math.round(z1 + uz * t);
            for (int sign : new int[] { -1, 1 }) {
                int lx = (int) Math.round(cx + px * (halfWidth + 1) * sign);
                int lz = (int) Math.round(cz + pz * (halfWidth + 1) * sign);
                for (int dy = 1; dy <= 4; dy++) {
                    p.place(lx, yStreet + dy, lz, Material.OAK_FENCE);
                }
                p.place(lx, yStreet + 5, lz, Material.SOUL_LANTERN);
            }
        }
    }

    // =========================================================================
    // ВСПОМОГАТЕЛЬНОЕ — простая «коробка» здания.
    // =========================================================================

    /**
     * Построить параллелепипед-коробку: пол ({@code floor}), стены
     * ({@code wall}) высотой {@code height}, крыша ({@code roof}),
     * пустота внутри. Координаты задаются ЦЕНТРОМ-min (НЗ-угол).
     *
     * <p>Возвращает массив [xMin,zMin,xMax,zMax,topY] для удобной
     * расстановки декора.
     */
    private int[] buildBox(RegionPainter p, int xMin, int zMin, int xSize, int zSize,
                            int height, Material floor, Material wall, Material roof) {
        int xMax = xMin + xSize - 1;
        int zMax = zMin + zSize - 1;
        int yBase = FLOOR_Y;
        int topY = yBase + height;

        // Пол.
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                p.place(x, yBase, z, floor);
            }
        }
        // Стены — только периметр.
        for (int dy = 1; dy <= height; dy++) {
            for (int x = xMin; x <= xMax; x++) {
                p.place(x, yBase + dy, zMin, wall);
                p.place(x, yBase + dy, zMax, wall);
            }
            for (int z = zMin; z <= zMax; z++) {
                p.place(xMin, yBase + dy, z, wall);
                p.place(xMax, yBase + dy, z, wall);
            }
        }
        // Очищаем интерьер.
        for (int dy = 1; dy < height; dy++) {
            for (int x = xMin + 1; x <= xMax - 1; x++) {
                for (int z = zMin + 1; z <= zMax - 1; z++) {
                    p.place(x, yBase + dy, z, Material.AIR);
                }
            }
        }
        // Крыша (плоская).
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                p.place(x, topY, z, roof);
            }
        }
        return new int[] { xMin, zMin, xMax, zMax, topY };
    }

    /** Дверной проём 1×2 в указанной стене (южной или северной). */
    private void cutDoor(RegionPainter p, int xMin, int xMax, int zMin, int zMax,
                          boolean atSouth, int doorOffsetFromCenter) {
        int doorX = (xMin + xMax) / 2 + doorOffsetFromCenter;
        int doorZ = atSouth ? zMax : zMin;
        for (int dy = 1; dy <= 2; dy++) {
            p.place(doorX, FLOOR_Y + dy, doorZ, Material.AIR);
        }
    }

    /** Окно (1×1 GLASS_PANE) в указанной точке. */
    private void cutWindow(RegionPainter p, int x, int yOff, int z, Material glass) {
        p.place(x, FLOOR_Y + yOff, z, glass);
    }

    // =========================================================================
    // ТАВЕРНА «Золотая кружка» — СЗ (-25, -25), 12×10, 2 этажа.
    // =========================================================================

    private void buildTavern(RegionPainter p) {
        int xMin = -25 - 6, zMin = -25 - 5;
        int[] b = buildBox(p, xMin, zMin, 12, 10, 5,
                Material.OAK_PLANKS, Material.OAK_LOG, Material.DARK_OAK_PLANKS);
        // Второй этаж: пол на y=4 из oak_planks, оставив проём над лестницей.
        int yMezzanine = FLOOR_Y + 4;
        for (int x = b[0] + 1; x <= b[2] - 1; x++) {
            for (int z = b[1] + 1; z <= b[3] - 1; z++) {
                if (Math.abs(x - (b[0] + 2)) <= 1 && Math.abs(z - (b[1] + 2)) <= 1) continue;
                p.place(x, yMezzanine, z, Material.OAK_PLANKS);
            }
        }
        // Стены до 8 (двухэтажная).
        for (int dy = 6; dy <= 8; dy++) {
            for (int x = b[0]; x <= b[2]; x++) {
                p.place(x, FLOOR_Y + dy, b[1], Material.OAK_LOG);
                p.place(x, FLOOR_Y + dy, b[3], Material.OAK_LOG);
            }
            for (int z = b[1]; z <= b[3]; z++) {
                p.place(b[0], FLOOR_Y + dy, z, Material.OAK_LOG);
                p.place(b[2], FLOOR_Y + dy, z, Material.OAK_LOG);
            }
        }
        // Новая крыша на y=9 (DARK_OAK_PLANKS).
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                p.place(x, FLOOR_Y + 9, z, Material.DARK_OAK_PLANKS);
            }
        }
        // Дверь на юг.
        cutDoor(p, b[0], b[2], b[1], b[3], true, 0);
        // Окна на 1-м этаже.
        cutWindow(p, b[0] + 2, 2, b[3], Material.GLASS_PANE);
        cutWindow(p, b[0] + 4, 2, b[3], Material.GLASS_PANE);
        cutWindow(p, b[0] + 8, 2, b[3], Material.GLASS_PANE);
        cutWindow(p, b[0] + 10, 2, b[3], Material.GLASS_PANE);
        // Камин (LAVA + COBBLESTONE truba).
        int fxc = b[0] + 1, fzc = b[1] + 1;
        for (int dy = 1; dy <= 6; dy++) {
            p.place(fxc, FLOOR_Y + dy, fzc, Material.COBBLESTONE);
        }
        p.place(fxc + 1, FLOOR_Y + 1, fzc, Material.NETHERRACK);
        p.place(fxc + 1, FLOOR_Y + 2, fzc, Material.FIRE);
        // Барная стойка (3 OAK_SLAB).
        for (int dx = 0; dx < 3; dx++) {
            p.place(b[0] + 4 + dx, FLOOR_Y + 1, b[1] + 5, Material.OAK_SLAB);
        }
        // Стол со стульями.
        p.place(b[0] + 8, FLOOR_Y + 1, b[3] - 2, Material.OAK_FENCE);
        p.place(b[0] + 8, FLOOR_Y + 2, b[3] - 2, Material.OAK_PRESSURE_PLATE);
        // Вывеска снаружи.
        FloatingText.createSign(plugin, world,
                (b[0] + b[2]) / 2.0 + 0.5, FLOOR_Y + 6, b[3] + 1.5,
                "§6Золотая кружка");
    }

    // =========================================================================
    // КУЗНИЦА — СВ (25, -25), 10×8, 1 этаж.
    // =========================================================================

    private void buildSmithy(RegionPainter p) {
        int xMin = 25 - 5, zMin = -25 - 4;
        int[] b = buildBox(p, xMin, zMin, 10, 8, 5,
                Material.STONE_BRICKS, Material.COBBLESTONE, Material.POLISHED_BLACKSTONE);
        cutDoor(p, b[0], b[2], b[1], b[3], true, 0);
        // Окна.
        cutWindow(p, b[0] + 2, 2, b[3], Material.IRON_BARS);
        cutWindow(p, b[0] + 7, 2, b[3], Material.IRON_BARS);
        // Горн: CAULDRON + LAVA + ANVIL.
        p.place(b[0] + 2, FLOOR_Y + 1, b[1] + 2, Material.NETHERRACK);
        p.place(b[0] + 2, FLOOR_Y + 2, b[1] + 2, Material.FIRE);
        p.place(b[0] + 3, FLOOR_Y + 1, b[1] + 2, Material.CAULDRON);
        p.place(b[0] + 7, FLOOR_Y + 1, b[1] + 2, Material.ANVIL);
        // Стенд с инструментами (chest + chiseled stone).
        p.place(b[0] + 1, FLOOR_Y + 1, b[3] - 1, Material.CHEST);
        p.place(b[0] + 8, FLOOR_Y + 1, b[3] - 1, Material.SMITHING_TABLE);
        // Дымоход (POLISHED_BLACKSTONE столбик до крыши).
        for (int dy = 1; dy <= 6; dy++) {
            p.place(b[0] + 2, FLOOR_Y + dy, b[1] + 2, Material.POLISHED_BLACKSTONE);
        }
        FloatingText.createSign(plugin, world,
                (b[0] + b[2]) / 2.0 + 0.5, FLOOR_Y + 6, b[3] + 1.5, "§7Кузница");
    }

    // =========================================================================
    // ЛАВКА АРТЕФАКТОВ — ЮЗ (-25, 25), 8×8, 1 этаж.
    // =========================================================================

    private void buildArtifactsShop(RegionPainter p) {
        int xMin = -25 - 4, zMin = 25 - 4;
        int[] b = buildBox(p, xMin, zMin, 8, 8, 5,
                Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG, Material.PURPLE_STAINED_GLASS);
        cutDoor(p, b[0], b[2], b[1], b[3], false, 0); // дверь на север (к собору)
        // Витрины с PURPLE_STAINED_GLASS.
        for (int x = b[0] + 1; x <= b[2] - 1; x++) {
            cutWindow(p, x, 2, b[1], Material.PURPLE_STAINED_GLASS);
            cutWindow(p, x, 3, b[1], Material.PURPLE_STAINED_GLASS);
        }
        // Полки с AMETHYST_SHARD: используем AMETHYST_CLUSTER на каменных подставках.
        for (int dx = 1; dx <= 6; dx += 2) {
            p.place(b[0] + dx, FLOOR_Y + 1, b[3] - 1, Material.SMOOTH_STONE_SLAB);
            p.place(b[0] + dx, FLOOR_Y + 2, b[3] - 1, Material.AMETHYST_CLUSTER);
        }
        // Лампа в центре.
        p.place(b[0] + 3, FLOOR_Y + 4, b[1] + 3, Material.SOUL_LANTERN);
        FloatingText.createSign(plugin, world,
                (b[0] + b[2]) / 2.0 + 0.5, FLOOR_Y + 6, b[1] - 0.5, "§dАртефакты");
    }

    // =========================================================================
    // ГИЛЬДИЯ ИСКАТЕЛЕЙ — ЮВ (25, 25), 12×10, 2 этажа.
    // =========================================================================

    private void buildAdventurersGuild(RegionPainter p) {
        int xMin = 25 - 6, zMin = 25 - 5;
        int[] b = buildBox(p, xMin, zMin, 12, 10, 5,
                Material.STONE_BRICKS, Material.STONE_BRICKS, Material.DARK_OAK_PLANKS);
        // Второй этаж.
        int yMez = FLOOR_Y + 4;
        for (int x = b[0] + 1; x <= b[2] - 1; x++) {
            for (int z = b[1] + 1; z <= b[3] - 1; z++) {
                if (Math.abs(x - (b[0] + 2)) <= 1 && Math.abs(z - (b[3] - 2)) <= 1) continue;
                p.place(x, yMez, z, Material.DARK_OAK_PLANKS);
            }
        }
        // Стены 6..8.
        for (int dy = 6; dy <= 8; dy++) {
            for (int x = b[0]; x <= b[2]; x++) {
                p.place(x, FLOOR_Y + dy, b[1], Material.STONE_BRICKS);
                p.place(x, FLOOR_Y + dy, b[3], Material.STONE_BRICKS);
            }
            for (int z = b[1]; z <= b[3]; z++) {
                p.place(b[0], FLOOR_Y + dy, z, Material.STONE_BRICKS);
                p.place(b[2], FLOOR_Y + dy, z, Material.STONE_BRICKS);
            }
        }
        // Новая крыша.
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                p.place(x, FLOOR_Y + 9, z, Material.DARK_OAK_PLANKS);
            }
        }
        cutDoor(p, b[0], b[2], b[1], b[3], false, 0); // на север (к собору)
        // Доска заданий: два блока DARK_OAK_PLANKS у входа.
        p.place(b[0] + 1, FLOOR_Y + 1, b[1] + 1, Material.DARK_OAK_PLANKS);
        p.place(b[0] + 1, FLOOR_Y + 2, b[1] + 1, Material.DARK_OAK_PLANKS);
        // Трофеи (декоративные skull на стенах) — используем zombie/skeleton head.
        p.place(b[0] + 4, FLOOR_Y + 3, b[3] - 1, Material.ZOMBIE_HEAD);
        p.place(b[0] + 8, FLOOR_Y + 3, b[3] - 1, Material.SKELETON_SKULL);
        FloatingText.createSign(plugin, world,
                (b[0] + b[2]) / 2.0 + 0.5, FLOOR_Y + 6, b[1] - 0.5, "§6Гильдия");
    }

    // =========================================================================
    // БАНК — Запад (-35, 0), 8×6.
    // =========================================================================

    private void buildBank(RegionPainter p) {
        int xMin = -35 - 4, zMin = 0 - 3;
        int[] b = buildBox(p, xMin, zMin, 8, 6, 5,
                Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.GOLD_BLOCK);
        cutDoor(p, b[0], b[2], b[1], b[3], false, 0); // на север... (к улице)
        // Решётка с сундуками: IRON_BARS поперёк, за ней 3 сундука.
        for (int x = b[0] + 1; x <= b[2] - 1; x++) {
            p.place(x, FLOOR_Y + 1, b[1] + 2, Material.IRON_BARS);
            p.place(x, FLOOR_Y + 2, b[1] + 2, Material.IRON_BARS);
        }
        for (int dx = 1; dx <= 5; dx += 2) {
            p.place(b[0] + dx, FLOOR_Y + 1, b[1] + 1, Material.CHEST);
        }
        // Золотая лампа для пафоса.
        p.place(b[0] + 3, FLOOR_Y + 4, b[1] + 4, Material.SHROOMLIGHT);
        FloatingText.createSign(plugin, world,
                (b[0] + b[2]) / 2.0 + 0.5, FLOOR_Y + 6, b[1] - 0.5, "§6Банк");
    }

    // =========================================================================
    // 8 ЖИЛЫХ ДОМОВ 6×6 по периметру (но внутри стен).
    // =========================================================================

    private void buildHouses(RegionPainter p, Random rng) {
        int[][] coords = {
                { -32, -10 }, { -32, 10 },
                {  32, -10 }, {  32, 10 },
                { -10, -32 }, {  10, -32 },
                { -10,  32 }, {  10,  32 },
        };
        for (int i = 0; i < coords.length; i++) {
            int cx = coords[i][0];
            int cz = coords[i][1];
            int xMin = cx - 3, zMin = cz - 3;
            int[] b = buildBox(p, xMin, zMin, 6, 6, 4,
                    Material.STONE_BRICKS, Material.DARK_OAK_PLANKS, Material.DARK_OAK_PLANKS);
            // Двускатная мини-крыша.
            for (int x = b[0]; x <= b[2]; x++) {
                for (int z = b[1]; z <= b[3]; z++) {
                    p.place(x, FLOOR_Y + 5, z, Material.DARK_OAK_PLANKS);
                }
            }
            // Дверь на сторону, ближнюю к собору (центру 0,0).
            boolean doorAtSouth = cz < 0;
            cutDoor(p, b[0], b[2], b[1], b[3], doorAtSouth, 0);
            // Два окна напротив двери.
            int wzOpp = doorAtSouth ? b[1] : b[3];
            cutWindow(p, b[0] + 1, 2, wzOpp, Material.GLASS_PANE);
            cutWindow(p, b[2] - 1, 2, wzOpp, Material.GLASS_PANE);
            // Цветок в горшке у двери.
            int doorX = (b[0] + b[2]) / 2;
            int potZ = doorAtSouth ? b[3] + 1 : b[1] - 1;
            p.place(doorX + 1, FLOOR_Y + 1, potZ, Material.FLOWER_POT);
        }
    }

    // =========================================================================
    // РЫНОК — южная площадь перед собором (4 прилавка).
    // =========================================================================

    private void buildMarket(RegionPainter p) {
        int yBase = FLOOR_Y;
        // Прилавки — 4 шт. слева и справа от южного проспекта (z в районе 18..22).
        int[][] stalls = {
                { -8, 18 }, {  8, 18 },
                { -8, 22 }, {  8, 22 },
        };
        Material[] canopyColors = {
                Material.RED_WOOL, Material.BLUE_WOOL,
                Material.YELLOW_WOOL, Material.GREEN_WOOL,
        };
        String[] labels = { "§cЕда", "§9Зелья", "§eОружие", "§aБроня" };
        for (int i = 0; i < stalls.length; i++) {
            int sx = stalls[i][0];
            int sz = stalls[i][1];
            // Прилавок (3×1).
            for (int dx = -1; dx <= 1; dx++) {
                p.place(sx + dx, yBase + 1, sz, Material.OAK_SLAB);
            }
            // Столбы навеса.
            for (int dy = 1; dy <= 3; dy++) {
                p.place(sx - 1, yBase + dy, sz - 1, Material.OAK_FENCE);
                p.place(sx + 1, yBase + dy, sz - 1, Material.OAK_FENCE);
            }
            // Навес.
            for (int dx = -1; dx <= 1; dx++) {
                p.place(sx + dx, yBase + 4, sz, canopyColors[i]);
                p.place(sx + dx, yBase + 4, sz - 1, canopyColors[i]);
            }
            // Вывеска прилавка.
            FloatingText.createSign(plugin, world,
                    sx + 0.5, yBase + 5, sz + 0.5, labels[i]);
        }
    }

    // =========================================================================
    // КОЛОДЕЦ — западная площадь.
    // =========================================================================

    private void buildWell(RegionPainter p) {
        int wx = -20, wz = 0;
        int yBase = FLOOR_Y;
        // Каменное основание 3×3, ров с водой по центру.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(wx + dx, yBase + 1, wz + dz, Material.COBBLESTONE);
            }
        }
        // Углубление 1×1×2 с водой.
        p.place(wx, yBase + 1, wz, Material.WATER);
        // Столбы крыши (4 угла), высота 3.
        for (int[] off : new int[][] { {-1, -1}, {1, -1}, {-1, 1}, {1, 1} }) {
            for (int dy = 2; dy <= 4; dy++) {
                p.place(wx + off[0], yBase + dy, wz + off[1], Material.OAK_FENCE);
            }
        }
        // Крыша 3×3 OAK_PLANKS на y=5.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(wx + dx, yBase + 5, wz + dz, Material.OAK_PLANKS);
            }
        }
    }
}
