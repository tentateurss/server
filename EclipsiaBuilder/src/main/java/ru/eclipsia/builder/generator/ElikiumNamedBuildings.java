package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * 5 уникальных POI-зданий Эликия + 4 будки стражи у ворот.
 *
 * <p>Каждое здание имеет фиксированную координату (выбраны вне зоны
 * собора и площадей), отличается материалом и декором, регистрирует
 * свой footprint в {@link ElikiumCity#occupied} (чтобы дома не наезжали)
 * и POI-якорь в {@link ElikiumCity#pois} (для FloatingText вывески).
 *
 * <ol>
 *   <li><b>Таверна «Золотая кружка»</b> (-30, -25), 16×14, фахверк
 *       OAK_PLANKS+DARK_OAK_LOG, балкон 2-го этажа, дымоход с CAMPFIRE;</li>
 *   <li><b>Кузница</b> (105, 50), 13×11, COBBLESTONE+POLISHED_BLACKSTONE,
 *       массивный дымоход с LAVA, ANVIL+FURNACE+CAULDRON+GRINDSTONE;</li>
 *   <li><b>Лавка артефактов</b> (-30, 30), 11×9, DARK_OAK+PURPLE_GLASS
 *       витрины, башенка 3×3 на крыше;</li>
 *   <li><b>Гильдия искателей</b> (105, -50), 18×14, STONE_BRICKS+DARK_OAK,
 *       угловая башня с PURPLE_BANNER, MOB_HEAD трофеи;</li>
 *   <li><b>Склад</b> (-105, 5), 13×11, POLISHED_BLACKSTONE+DARK_OAK,
 *       пандус, BARREL+CHEST+HAY_BALE снаружи.</li>
 * </ol>
 */
public final class ElikiumNamedBuildings {

    private static final int Y_BASE = ElikiumCity.Y_BASE;

    private final Plugin plugin;
    private final RegionPainter painter;
    private final Random rng;
    private final ElikiumCity ctx;

    public ElikiumNamedBuildings(Plugin plugin, RegionPainter painter, Random rng, ElikiumCity ctx) {
        this.plugin = plugin;
        this.painter = painter;
        this.rng = rng;
        this.ctx = ctx;
    }

    public long build() {
        long count = 0;
        count += buildTavern(-30, -25);
        count += buildSmithy(105, 55);
        count += buildArtifactShop(-30, 25);
        count += buildGuildhall(105, -50);
        count += buildWarehouse(-100, 5);
        count += buildAllGateBooths();
        return count;
    }

    // =========================================================================
    // ТАВЕРНА — 16×14, фахверк, балкон, дымоход
    // =========================================================================

    private long buildTavern(int cx, int cz) {
        long count = 0;
        int w = 16, d = 14;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin, zMin, xMax, zMax));

        // Фундамент — COBBLED_DEEPSLATE
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 1-й этаж (5 блоков высотой) — фахверк OAK_PLANKS со столбами DARK_OAK_LOG
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 5,
                Material.OAK_PLANKS, Material.DARK_OAK_LOG);
        // Большие окна YELLOW_STAINED_GLASS на 1-м этаже
        placeWindowRow(xMin, xMax, zMax, Y_BASE + 4, Material.YELLOW_STAINED_GLASS, 2);
        placeWindowRow(xMin, xMax, zMin, Y_BASE + 4, Material.YELLOW_STAINED_GLASS, 2);

        // 2-й этаж — фахверк
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 7, 4,
                Material.OAK_PLANKS, Material.DARK_OAK_LOG);
        // Меньшие окна 2-го этажа
        placeWindowRow(xMin, xMax, zMax, Y_BASE + 9, Material.GLASS_PANE, 3);
        placeWindowRow(xMin, xMax, zMin, Y_BASE + 9, Material.GLASS_PANE, 3);

        // 3-й этаж (мансарда) — короткий
        count += buildFloor(xMin + 2, zMin + 2, xMax - 2, zMax - 2, Y_BASE + 11, 3,
                Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG);

        // Крыша двускатная DARK_OAK_STAIRS вдоль X
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 11, true,
                Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);

        // Балкон 2-го этажа на южной стене
        for (int x = xMin + 4; x <= xMax - 4; x++) {
            painter.place(x, Y_BASE + 7, zMax + 1, Material.DARK_OAK_SLAB);
            painter.place(x, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);
            count += 2;
        }
        painter.place(xMin + 4, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);
        painter.place(xMax - 4, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);

        // Дымоход на крыше
        int chX = xMin + 3, chZ = zMin + 2;
        for (int dy = 0; dy <= 6; dy++) {
            painter.place(chX, Y_BASE + 11 + dy, chZ, Material.COBBLESTONE);
        }
        painter.place(chX, Y_BASE + 18, chZ, Material.CAMPFIRE);
        count += 8;

        // Дверь — главный вход на южной стене
        carveDoor(cx, zMax, "north");
        // Фонарь над входом
        painter.place(cx, Y_BASE + 5, zMax, Material.SOUL_LANTERN);
        // Внутреннее освещение
        painter.place(cx, Y_BASE + 5, cz, Material.LANTERN);
        count += 2;

        // POI-якорь (вывеска)
        ctx.pois.add(new ElikiumCity.POI("§6Золотая Кружка",
                "таверна и постоялый двор",
                cx, Y_BASE + 7, zMax + 2));
        return count;
    }

    // =========================================================================
    // КУЗНИЦА — 13×11, COBBLESTONE+BLACKSTONE, дымоход с LAVA
    // =========================================================================

    private long buildSmithy(int cx, int cz) {
        long count = 0;
        int w = 13, d = 11;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin, zMin, xMax, zMax));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // Стены: COBBLESTONE с POLISHED_BLACKSTONE столбами по углам
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 8,
                Material.COBBLESTONE, Material.POLISHED_BLACKSTONE);

        // Крыша плоская BLACKSTONE_STAIRS — лёгкий наклон
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 10, z, Material.POLISHED_BLACKSTONE_BRICKS);
                count++;
            }
        }
        // Конёк
        for (int x = xMin; x <= xMax; x++) {
            painter.place(x, Y_BASE + 11, cz, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            count++;
        }

        // Большой дымоход на крыше с LAVA внутри
        int chX = xMin + 2, chZ = zMin + 2;
        for (int dy = 0; dy <= 8; dy++) {
            painter.place(chX, Y_BASE + 10 + dy, chZ, Material.COBBLESTONE_WALL);
            painter.place(chX + 1, Y_BASE + 10 + dy, chZ, Material.COBBLESTONE_WALL);
            painter.place(chX, Y_BASE + 10 + dy, chZ + 1, Material.COBBLESTONE_WALL);
            painter.place(chX + 1, Y_BASE + 10 + dy, chZ + 1, Material.LAVA);
        }
        count += 36;

        // Большие открытые ворота на южной стене (3×3)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                painter.place(cx + dx, Y_BASE + 2 + dy, zMax, Material.AIR);
            }
        }
        // Над воротами — аркой DARK_OAK_LOG
        painter.place(cx - 2, Y_BASE + 5, zMax, Material.DARK_OAK_LOG);
        painter.place(cx + 2, Y_BASE + 5, zMax, Material.DARK_OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) {
            painter.place(cx + dx, Y_BASE + 5, zMax, Material.DARK_OAK_LOG);
        }
        count += 5;

        // Внутри: ANVIL, FURNACE, CAULDRON с LAVA
        painter.place(cx - 3, Y_BASE + 2, cz, Material.ANVIL);
        painter.place(cx + 3, Y_BASE + 2, cz, Material.FURNACE);
        painter.place(cx - 3, Y_BASE + 2, cz - 2, Material.LAVA_CAULDRON);
        // Снаружи: GRINDSTONE + дрова
        painter.place(xMax - 1, Y_BASE + 2, zMax + 2, Material.GRINDSTONE);
        for (int i = 0; i < 4; i++) {
            painter.place(xMin - 1, Y_BASE + 2 + i, zMin + 2, Material.OAK_LOG);
        }
        count += 7;

        ctx.pois.add(new ElikiumCity.POI("§7Кузница Эликия",
                "доспехи, оружие, ремонт",
                cx, Y_BASE + 7, zMax + 2));
        return count;
    }

    // =========================================================================
    // ЛАВКА АРТЕФАКТОВ — 11×9, витрины PURPLE_GLASS, башенка
    // =========================================================================

    private long buildArtifactShop(int cx, int cz) {
        long count = 0;
        int w = 11, d = 9;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin, zMin, xMax, zMax));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 1-й этаж — DARK_OAK_PLANKS с витринами PURPLE_STAINED_GLASS
        for (int floor = 0; floor < 2; floor++) {
            int yBase = Y_BASE + 2 + floor * 4;
            count += buildFloor(xMin, zMin, xMax, zMax, yBase, 4,
                    Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG);
        }
        // Витрины PURPLE_STAINED_GLASS на 1-м этаже (большие)
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            painter.place(x, Y_BASE + 3, zMax, Material.PURPLE_STAINED_GLASS);
            painter.place(x, Y_BASE + 4, zMax, Material.PURPLE_STAINED_GLASS);
            painter.place(x, Y_BASE + 3, zMin, Material.PURPLE_STAINED_GLASS);
            painter.place(x, Y_BASE + 4, zMin, Material.PURPLE_STAINED_GLASS);
        }
        // Внутри витрин — AMETHYST_BLOCK подсветка
        painter.place(cx, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        painter.place(cx - 2, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        painter.place(cx + 2, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        count += 3;

        // Крыша
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 10, false,
                Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);

        // Башенка на крыше 3×3, высота 4
        int tx = cx, tz = cz;
        for (int dy = 0; dy <= 5; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean perim = (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                    if (!perim) continue;
                    painter.place(tx + dx, Y_BASE + 14 + dy, tz + dz,
                            Material.DARK_OAK_PLANKS);
                    count++;
                }
            }
        }
        // Окно башенки
        painter.place(tx, Y_BASE + 16, tz - 1, Material.PURPLE_STAINED_GLASS);
        painter.place(tx, Y_BASE + 16, tz + 1, Material.PURPLE_STAINED_GLASS);
        // Шпиль башенки END_ROD
        painter.place(tx, Y_BASE + 20, tz, Material.AMETHYST_BLOCK);
        painter.place(tx, Y_BASE + 21, tz, Material.END_ROD);

        // Дверь
        carveDoor(cx, zMax, "north");
        painter.place(cx, Y_BASE + 5, zMax, Material.PURPLE_CARPET);
        // Внутреннее освещение
        painter.place(cx, Y_BASE + 4, cz, Material.LANTERN);
        count += 5;

        ctx.pois.add(new ElikiumCity.POI("§dАртефакты Эликия",
                "редкости и реликвии",
                cx, Y_BASE + 7, zMax + 2));
        return count;
    }

    // =========================================================================
    // ГИЛЬДИЯ ИСКАТЕЛЕЙ — 18×14, угловая башня, баннеры, трофеи
    // =========================================================================

    private long buildGuildhall(int cx, int cz) {
        long count = 0;
        int w = 18, d = 14;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 4, zMin, xMax, zMax));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 3 этажа STONE_BRICKS с DARK_OAK столбами
        for (int floor = 0; floor < 3; floor++) {
            int yBase = Y_BASE + 2 + floor * 4;
            count += buildFloor(xMin, zMin, xMax, zMax, yBase, 4,
                    Material.STONE_BRICKS, Material.DARK_OAK_LOG);
        }
        // Окна с IRON_BARS решёткой
        for (int x = xMin + 2; x <= xMax - 2; x += 4) {
            painter.place(x, Y_BASE + 4, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 5, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 4, zMin, Material.IRON_BARS);
            painter.place(x, Y_BASE + 5, zMin, Material.IRON_BARS);
            painter.place(x, Y_BASE + 8, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 8, zMin, Material.IRON_BARS);
        }

        // Многоскатная крыша
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 14, true,
                Material.DARK_OAK_STAIRS, Material.STONE_BRICKS);

        // Угловая башня 5×5 на западном углу
        int btx = xMin - 2, btz = zMin + 2;
        for (int dy = 0; dy < 14; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean perim = (Math.abs(dx) == 2 || Math.abs(dz) == 2);
                    if (!perim) continue;
                    painter.place(btx + dx, Y_BASE + 2 + dy, btz + dz,
                            Material.STONE_BRICKS);
                    count++;
                }
            }
        }
        // Зубцы башни
        for (int dx = -2; dx <= 2; dx += 2) {
            painter.place(btx + dx, Y_BASE + 16, btz - 2, Material.STONE_BRICK_WALL);
            painter.place(btx + dx, Y_BASE + 16, btz + 2, Material.STONE_BRICK_WALL);
        }
        for (int dz = -2; dz <= 2; dz += 2) {
            painter.place(btx - 2, Y_BASE + 16, btz + dz, Material.STONE_BRICK_WALL);
            painter.place(btx + 2, Y_BASE + 16, btz + dz, Material.STONE_BRICK_WALL);
        }
        // Флаг PURPLE_BANNER на верху башни
        painter.place(btx, Y_BASE + 17, btz, Material.PURPLE_BANNER);

        // Двойная дверь на южной стене
        carveDoor(cx - 1, zMax, "north");
        carveDoor(cx, zMax, "north");
        // Над дверью — арка
        for (int dx = -2; dx <= 1; dx++) {
            painter.place(cx + dx, Y_BASE + 5, zMax, Material.POLISHED_BLACKSTONE);
        }
        count += 4;

        // Трофеи на стенах: SKELETON_SKULL и WITHER_SKELETON_SKULL на DARK_OAK_FENCE
        painter.place(xMin + 3, Y_BASE + 6, zMax + 1, Material.SKELETON_SKULL);
        painter.place(xMax - 3, Y_BASE + 6, zMax + 1, Material.WITHER_SKELETON_SKULL);
        // Внутреннее освещение
        painter.place(cx, Y_BASE + 5, cz, Material.LANTERN);
        painter.place(cx, Y_BASE + 9, cz, Material.LANTERN);

        ctx.pois.add(new ElikiumCity.POI("§6Гильдия Искателей",
                "квесты и снаряжение",
                cx, Y_BASE + 9, zMax + 2));
        return count;
    }

    // =========================================================================
    // СКЛАД — 13×11, BLACKSTONE+DARK_OAK, пандус, внешний инвентарь
    // =========================================================================

    private long buildWarehouse(int cx, int cz) {
        long count = 0;
        int w = 13, d = 11;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin, zMin - 3, xMax + 4, zMax));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // Высокие стены (7) BLACKSTONE с DARK_OAK столбами
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 7,
                Material.POLISHED_BLACKSTONE, Material.DARK_OAK_LOG);
        // Маленькие окна под крышей
        for (int x = xMin + 2; x <= xMax - 2; x += 3) {
            painter.place(x, Y_BASE + 7, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 7, zMin, Material.IRON_BARS);
        }

        // Двускатная крыша
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 9, true,
                Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);

        // Двойная дверь на восточной стене
        for (int dz = -1; dz <= 0; dz++) {
            painter.place(xMax, Y_BASE + 2, cz + dz, Material.AIR);
            painter.place(xMax, Y_BASE + 3, cz + dz, Material.AIR);
        }
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=west,hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=west,hinge=left]");
        painter.placeData(xMax, Y_BASE + 2, cz, door);
        painter.placeData(xMax, Y_BASE + 3, cz, doorTop);
        // Пандус
        for (int dz = -2; dz <= 1; dz++) {
            painter.place(xMax + 1, Y_BASE + 1, cz + dz, Material.DARK_OAK_SLAB);
        }

        // Снаружи: HAY_BALE 4 шт, BARREL 6 шт, CHEST 3 шт
        for (int i = 0; i < 4; i++) {
            painter.place(xMin - 1, Y_BASE + 2 + (i / 2), zMin + i % 2 * 2, Material.HAY_BLOCK);
        }
        for (int i = 0; i < 6; i++) {
            painter.place(xMax + 2 + i / 3, Y_BASE + 2 + i % 3, cz + 2, Material.BARREL);
        }
        for (int i = 0; i < 3; i++) {
            painter.place(xMin + 1 + i, Y_BASE + 2, zMax + 1, Material.CHEST);
        }
        count += 13;

        // Внутреннее освещение
        painter.place(cx, Y_BASE + 8, cz, Material.LANTERN);

        ctx.pois.add(new ElikiumCity.POI("§8Склад",
                "товары и припасы",
                xMax + 2, Y_BASE + 6, cz));
        return count;
    }

    // =========================================================================
    // БУДКИ СТРАЖИ У ВОРОТ
    // =========================================================================

    private long buildAllGateBooths() {
        long count = 0;
        // South gate
        count += buildGateBooth(WorldGenerator.SOUTH_GATE[0] + 6,
                WorldGenerator.SOUTH_GATE[1] - 6, "Южные ворота");
        count += buildGateBooth(WorldGenerator.NORTH_GATE[0] - 6,
                WorldGenerator.NORTH_GATE[1] + 6, "Северные ворота");
        count += buildGateBooth(WorldGenerator.EAST_GATE[0] - 6,
                WorldGenerator.EAST_GATE[1] - 6, "Восточные ворота");
        count += buildGateBooth(WorldGenerator.WEST_GATE[0] + 6,
                WorldGenerator.WEST_GATE[1] + 6, "Западные ворота");
        return count;
    }

    private long buildGateBooth(int cx, int cz, String gateName) {
        long count = 0;
        int xMin = cx - 1, xMax = cx + 1;
        int zMin = cz - 1, zMax = cz + 1;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin, zMin, xMax, zMax));

        // Стены OAK_PLANKS высотой 4
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                if (!perim) continue;
                for (int dy = 1; dy <= 4; dy++) {
                    painter.place(x, Y_BASE + dy, z, Material.OAK_PLANKS);
                    count++;
                }
            }
        }
        // Угловые столбы DARK_OAK_LOG
        for (int sx : new int[]{-1, +1}) {
            for (int sz : new int[]{-1, +1}) {
                int x = cx + sx, z = cz + sz;
                for (int dy = 1; dy <= 4; dy++) {
                    painter.place(x, Y_BASE + dy, z, Material.DARK_OAK_LOG);
                }
            }
        }
        // Крыша (плоская)
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 5, z, Material.DARK_OAK_PLANKS);
                count++;
            }
        }
        // Дверь по центру
        painter.place(cx, Y_BASE + 1, zMax, Material.AIR);
        painter.place(cx, Y_BASE + 2, zMax, Material.AIR);
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=north,hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=north,hinge=left]");
        painter.placeData(cx, Y_BASE + 1, zMax, door);
        painter.placeData(cx, Y_BASE + 2, zMax, doorTop);
        // Окошко
        painter.place(cx, Y_BASE + 3, zMax - 2, Material.GLASS_PANE);
        // SOUL_TORCH на крыше
        painter.place(cx, Y_BASE + 6, cz, Material.SOUL_TORCH);
        // Котёл с водой рядом
        painter.place(cx + 2, Y_BASE + 1, cz, Material.WATER_CAULDRON);
        count += 5;

        ctx.pois.add(new ElikiumCity.POI("§b" + gateName,
                "вход в Эликий",
                cx, Y_BASE + 6, cz));
        return count;
    }

    // =========================================================================
    // ОБЩИЕ УТИЛИТЫ
    // =========================================================================

    /** Этаж: периметр заполняется wallMat, углы и каждые 4 блока — pillarMat. */
    private long buildFloor(int xMin, int zMin, int xMax, int zMax,
                             int yBase, int height,
                             Material wallMat, Material pillarMat) {
        long count = 0;
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                if (!perim) continue;
                boolean corner = (x == xMin || x == xMax) && (z == zMin || z == zMax);
                Material mat = corner ? pillarMat : wallMat;
                for (int dy = 0; dy < height; dy++) {
                    painter.place(x, yBase + dy, z, mat);
                    count++;
                }
            }
        }
        // Промежуточные столбы каждые 4 блока (только на длинных стенах)
        for (int x = xMin + 4; x <= xMax - 4; x += 4) {
            for (int dy = 0; dy < height; dy++) {
                painter.place(x, yBase + dy, zMin, pillarMat);
                painter.place(x, yBase + dy, zMax, pillarMat);
                count += 2;
            }
        }
        return count;
    }

    /** Двускатная крыша вдоль оси Z (если roofAlongZ) или X. */
    private long buildGableRoof(int xMin, int zMin, int xMax, int zMax,
                                 int yBase, boolean roofAlongZ,
                                 Material stairMat, Material fillMat) {
        long count = 0;
        int span = roofAlongZ ? (xMax - xMin) : (zMax - zMin);
        int roofH = span / 2 + 1;
        int cx = (xMin + xMax) / 2, cz = (zMin + zMax) / 2;
        for (int rise = 0; rise <= roofH; rise++) {
            int y = yBase + rise;
            if (roofAlongZ) {
                int dx = (span / 2) - rise;
                if (dx < 0) break;
                int xL = cx - dx, xR = cx + dx;
                for (int z = zMin; z <= zMax; z++) {
                    painter.place(xL, y, z, fillMat);
                    if (xR != xL) painter.place(xR, y, z, fillMat);
                    count += (xR != xL) ? 2 : 1;
                }
            } else {
                int dz = (span / 2) - rise;
                if (dz < 0) break;
                int zN = cz - dz, zS = cz + dz;
                for (int x = xMin; x <= xMax; x++) {
                    painter.place(x, y, zN, fillMat);
                    if (zS != zN) painter.place(x, y, zS, fillMat);
                    count += (zS != zN) ? 2 : 1;
                }
            }
        }
        // Каменные торцы (фронтоны)
        for (int rise = 0; rise <= roofH; rise++) {
            int y = yBase + rise;
            if (roofAlongZ) {
                int dx = (span / 2) - rise;
                if (dx < 0) break;
                for (int x = cx - dx; x <= cx + dx; x++) {
                    painter.place(x, y, zMin, fillMat);
                    painter.place(x, y, zMax, fillMat);
                    count += 2;
                }
            } else {
                int dz = (span / 2) - rise;
                if (dz < 0) break;
                for (int z = cz - dz; z <= cz + dz; z++) {
                    painter.place(xMin, y, z, fillMat);
                    painter.place(xMax, y, z, fillMat);
                    count += 2;
                }
            }
        }
        return count;
    }

    private void placeWindowRow(int xMin, int xMax, int z, int y,
                                Material glassMat, int step) {
        for (int x = xMin + 2; x <= xMax - 2; x += step) {
            painter.place(x, y, z, glassMat);
            painter.place(x, y + 1, z, glassMat);
        }
    }

    private void carveDoor(int x, int z, String facing) {
        painter.place(x, Y_BASE + 2, z, Material.AIR);
        painter.place(x, Y_BASE + 3, z, Material.AIR);
        BlockData door = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=" + facing + ",hinge=left]");
        BlockData doorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=" + facing + ",hinge=left]");
        painter.placeData(x, Y_BASE + 2, z, door);
        painter.placeData(x, Y_BASE + 3, z, doorTop);
    }
}
