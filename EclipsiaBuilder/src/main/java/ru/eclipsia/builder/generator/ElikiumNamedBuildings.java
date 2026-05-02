package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * 5 уникальных POI-зданий Эликия v2 + 4 будки стражи у ворот.
 *
 * <p>Каждое здание имеет фиксированную координату (выбраны вне зоны
 * собора и площадей), отличается материалом и декором, регистрирует
 * свой footprint в {@link ElikiumCity#occupied} и POI-якорь в
 * {@link ElikiumCity#pois} (для FloatingText).
 *
 * <p>v2: улучшены пропорции, добавлен декор снаружи (бочки, горшки,
 * SOUL_TORCH на стенах), балконы, дымоходы с CAMPFIRE/LAVA, более
 * крупные вывески.
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
    // ТАВЕРНА "Золотая кружка" — 16×14, фахверк, балкон, дымоход
    // =========================================================================

    private long buildTavern(int cx, int cz) {
        long count = 0;
        int w = 20, d = 16;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 2, zMin, xMax + 2, zMax + 2));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 1-й этаж (5 блоков) — SPRUCE_PLANKS + DARK_OAK_LOG фахверк
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 5,
                Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG);
        // Большие окна YELLOW_STAINED_GLASS
        placeWindowRow(xMin, xMax, zMax, Y_BASE + 4, Material.YELLOW_STAINED_GLASS, 2);
        placeWindowRow(xMin, xMax, zMin, Y_BASE + 4, Material.YELLOW_STAINED_GLASS, 2);

        // 2-й этаж — фахверк
        // Перекрытие
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 7, z, Material.SPRUCE_PLANKS);
            }
        }
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 7, 4,
                Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG);
        placeWindowRow(xMin, xMax, zMax, Y_BASE + 9, Material.GLASS_PANE, 3);
        placeWindowRow(xMin, xMax, zMin, Y_BASE + 9, Material.GLASS_PANE, 3);

        // 3-й этаж (мансарда) — уже
        for (int x = xMin + 2; x <= xMax - 2; x++) {
            for (int z = zMin + 2; z <= zMax - 2; z++) {
                painter.place(x, Y_BASE + 11, z, Material.SPRUCE_PLANKS);
            }
        }
        count += buildFloor(xMin + 2, zMin + 2, xMax - 2, zMax - 2, Y_BASE + 11, 3,
                Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG);

        // Крыша двускатная DARK_OAK_STAIRS
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 11, true,
                Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);

        // Балкон 2-го этажа на южной стене
        for (int x = xMin + 4; x <= xMax - 4; x++) {
            painter.place(x, Y_BASE + 7, zMax + 1, Material.DARK_OAK_SLAB);
            painter.place(x, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);
            count += 2;
        }
        // Перила балкона с TRAPDOOR
        painter.place(xMin + 4, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);
        painter.place(xMax - 4, Y_BASE + 8, zMax + 1, Material.OAK_FENCE);

        // Дымоход с CAMPFIRE (высота 6 над крышей)
        int chX = xMin + 3, chZ = zMin + 2;
        for (int dy = 0; dy <= 8; dy++) {
            painter.place(chX, Y_BASE + 11 + dy, chZ, Material.COBBLESTONE_WALL);
        }
        painter.place(chX, Y_BASE + 20, chZ, Material.CAMPFIRE);
        count += 10;

        // Дверь
        carveDoor(cx, zMax, "north");
        // Фонарь над входом
        painter.place(cx, Y_BASE + 5, zMax, Material.SOUL_LANTERN);
        // Освещение внутри
        painter.place(cx, Y_BASE + 5, cz, Material.LANTERN);
        painter.place(cx, Y_BASE + 9, cz, Material.LANTERN);
        count += 3;

        // Декор перед входом: 2 бочки + лавка
        painter.place(cx - 2, Y_BASE + 1, zMax + 1, Material.BARREL);
        painter.place(cx - 3, Y_BASE + 1, zMax + 1, Material.BARREL);
        BlockData bench = Material.OAK_STAIRS.createBlockData("[facing=north,half=bottom]");
        painter.placeData(cx + 2, Y_BASE + 1, zMax + 1, bench);
        painter.placeData(cx + 3, Y_BASE + 1, zMax + 1, bench);
        count += 4;

        ctx.pois.add(new ElikiumCity.POI("§6Золотая Кружка",
                "таверна и постоялый двор",
                cx, Y_BASE + 7, zMax + 2));
        return count;
    }

    // =========================================================================
    // КУЗНИЦА — 12×10, COBBLESTONE+BLACKSTONE, дымоход с LAVA
    // =========================================================================

    private long buildSmithy(int cx, int cz) {
        long count = 0;
        int w = 16, d = 14;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 2, zMin, xMax + 2, zMax + 3));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // Стены: COBBLESTONE + POLISHED_BLACKSTONE, 10 блоков высотой
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 10,
                Material.COBBLESTONE, Material.POLISHED_BLACKSTONE);

        // Крыша BLACKSTONE_STAIRS
        count += buildGableRoof(xMin - 1, zMin, xMax + 1, zMax, Y_BASE + 12, false,
                Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICKS);

        // Массивный дымоход с LAVA внутри
        int chX = xMin + 2, chZ = zMin + 2;
        for (int dy = 0; dy <= 8; dy++) {
            painter.place(chX, Y_BASE + 10 + dy, chZ, Material.COBBLESTONE_WALL);
            painter.place(chX + 1, Y_BASE + 10 + dy, chZ, Material.COBBLESTONE_WALL);
            painter.place(chX, Y_BASE + 10 + dy, chZ + 1, Material.COBBLESTONE_WALL);
            painter.place(chX + 1, Y_BASE + 10 + dy, chZ + 1, Material.LAVA);
        }
        count += 36;

        // Большие открытые ворота 3 блока шириной (OAK_FENCE_GATE)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                painter.place(cx + dx, Y_BASE + 2 + dy, zMax, Material.AIR);
            }
        }
        // Арка над воротами DARK_OAK_LOG
        for (int dx = -2; dx <= 2; dx++) {
            painter.place(cx + dx, Y_BASE + 5, zMax, Material.DARK_OAK_LOG);
        }
        count += 5;

        // ANVIL, FURNACE, GRINDSTONE снаружи
        painter.place(cx - 3, Y_BASE + 2, zMax + 1, Material.ANVIL);
        painter.place(cx + 3, Y_BASE + 2, zMax + 1, Material.FURNACE);
        painter.place(cx - 3, Y_BASE + 2, zMax + 2, Material.GRINDSTONE);
        // Дрова (OAK_LOG) у стены
        for (int i = 0; i < 4; i++) {
            painter.place(xMin - 1, Y_BASE + 2 + i, zMin + 2, Material.OAK_LOG);
        }
        for (int i = 0; i < 3; i++) {
            painter.place(xMin - 1, Y_BASE + 2 + i, zMin + 3, Material.OAK_LOG);
        }
        // Внутри: LAVA_CAULDRON + FURNACE
        painter.place(cx - 3, Y_BASE + 2, cz, Material.ANVIL);
        painter.place(cx + 3, Y_BASE + 2, cz, Material.FURNACE);
        painter.place(cx - 3, Y_BASE + 2, cz - 2, Material.LAVA_CAULDRON);
        count += 13;

        // SOUL_TORCH на стенах
        painter.place(xMin, Y_BASE + 5, cz, Material.SOUL_TORCH);
        painter.place(xMax, Y_BASE + 5, cz, Material.SOUL_TORCH);
        // Освещение
        painter.place(cx, Y_BASE + 8, cz, Material.LANTERN);
        count += 3;

        ctx.pois.add(new ElikiumCity.POI("§7Кузница Эликия",
                "доспехи, оружие, ремонт",
                cx, Y_BASE + 7, zMax + 2));
        return count;
    }

    // =========================================================================
    // ЛАВКА АРТЕФАКТОВ — 10×8, витрины PURPLE_GLASS, башенка
    // =========================================================================

    private long buildArtifactShop(int cx, int cz) {
        long count = 0;
        int w = 14, d = 12;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 1, zMin, xMax + 1, zMax + 2));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 2 этажа DARK_OAK_PLANKS
        for (int floor = 0; floor < 2; floor++) {
            int yBase = Y_BASE + 2 + floor * 4;
            if (floor > 0) {
                for (int x = xMin; x <= xMax; x++) {
                    for (int z = zMin; z <= zMax; z++) {
                        painter.place(x, yBase - 1, z, Material.DARK_OAK_PLANKS);
                    }
                }
            }
            count += buildFloor(xMin, zMin, xMax, zMax, yBase, 4,
                    Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG);
        }

        // Витрины PURPLE_STAINED_GLASS на 1-м этаже (от пола до высоты 3)
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            painter.place(x, Y_BASE + 3, zMax, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(x, Y_BASE + 4, zMax, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(x, Y_BASE + 5, zMax, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(x, Y_BASE + 3, zMin, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(x, Y_BASE + 4, zMin, Material.PURPLE_STAINED_GLASS_PANE);
            painter.place(x, Y_BASE + 5, zMin, Material.PURPLE_STAINED_GLASS_PANE);
        }
        // За витринами — AMETHYST_BLOCK подсветка
        painter.place(cx, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        painter.place(cx - 2, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        painter.place(cx + 2, Y_BASE + 3, zMax - 1, Material.AMETHYST_BLOCK);
        count += 3;

        // Крыша DARK_OAK_STAIRS
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 10, false,
                Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);

        // Башенка 3×3 на крыше, высота 4+
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
        // Шпиль башенки
        painter.place(tx, Y_BASE + 20, tz, Material.AMETHYST_BLOCK);
        painter.place(tx, Y_BASE + 21, tz, Material.END_ROD);

        // Дверь
        carveDoor(cx, zMax, "north");
        painter.place(cx, Y_BASE + 5, zMax, Material.PURPLE_CARPET);
        // Освещение
        painter.place(cx, Y_BASE + 4, cz, Material.LANTERN);
        painter.place(cx, Y_BASE + 8, cz, Material.LANTERN);
        count += 6;

        // Бочка и горшок у входа
        painter.place(cx + 2, Y_BASE + 1, zMax + 1, Material.BARREL);
        painter.place(cx - 2, Y_BASE + 1, zMax + 1, Material.FLOWER_POT);
        count += 2;

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
        int w = 22, d = 16;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 4, zMin, xMax, zMax + 2));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // 3 этажа STONE_BRICKS + DARK_OAK столбы
        for (int floor = 0; floor < 3; floor++) {
            int yBase = Y_BASE + 2 + floor * 4;
            if (floor > 0) {
                for (int x = xMin; x <= xMax; x++) {
                    for (int z = zMin; z <= zMax; z++) {
                        painter.place(x, yBase - 1, z, Material.OAK_PLANKS);
                    }
                }
            }
            count += buildFloor(xMin, zMin, xMax, zMax, yBase, 4,
                    Material.STONE_BRICKS, Material.DARK_OAK_LOG);
        }

        // Окна с IRON_BARS решёткой на всех этажах
        for (int x = xMin + 2; x <= xMax - 2; x += 4) {
            painter.place(x, Y_BASE + 4, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 5, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 4, zMin, Material.IRON_BARS);
            painter.place(x, Y_BASE + 5, zMin, Material.IRON_BARS);
            painter.place(x, Y_BASE + 8, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 8, zMin, Material.IRON_BARS);
            painter.place(x, Y_BASE + 12, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 12, zMin, Material.IRON_BARS);
        }

        // Многоскатная крыша
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 14, true,
                Material.DARK_OAK_STAIRS, Material.STONE_BRICKS);

        // Угловая башня 5×5 на западном углу, высота 8 над крышей
        int btx = xMin - 2, btz = zMin + 2;
        for (int dy = 0; dy < 16; dy++) {
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
            painter.place(btx + dx, Y_BASE + 18, btz - 2, Material.STONE_BRICK_WALL);
            painter.place(btx + dx, Y_BASE + 18, btz + 2, Material.STONE_BRICK_WALL);
        }
        for (int dz = -2; dz <= 2; dz += 2) {
            painter.place(btx - 2, Y_BASE + 18, btz + dz, Material.STONE_BRICK_WALL);
            painter.place(btx + 2, Y_BASE + 18, btz + dz, Material.STONE_BRICK_WALL);
        }
        // Флаг PURPLE_BANNER на верху башни
        painter.place(btx, Y_BASE + 19, btz, Material.PURPLE_BANNER);
        // Окна башни
        painter.place(btx, Y_BASE + 8, btz - 2, Material.IRON_BARS);
        painter.place(btx, Y_BASE + 12, btz - 2, Material.IRON_BARS);

        // Двойная дверь на южной стене + арка над входом
        carveDoor(cx - 1, zMax, "north");
        carveDoor(cx, zMax, "north");
        for (int dx = -2; dx <= 1; dx++) {
            painter.place(cx + dx, Y_BASE + 5, zMax, Material.POLISHED_BLACKSTONE);
        }
        count += 4;

        // Трофеи на стенах
        painter.place(xMin + 3, Y_BASE + 6, zMax + 1, Material.SKELETON_SKULL);
        painter.place(xMax - 3, Y_BASE + 6, zMax + 1, Material.WITHER_SKELETON_SKULL);
        // Освещение
        painter.place(cx, Y_BASE + 5, cz, Material.LANTERN);
        painter.place(cx, Y_BASE + 9, cz, Material.LANTERN);
        painter.place(cx, Y_BASE + 13, cz, Material.LANTERN);

        // Декор снаружи: бочки, горшки
        painter.place(cx + 3, Y_BASE + 1, zMax + 1, Material.BARREL);
        painter.place(cx - 3, Y_BASE + 1, zMax + 1, Material.FLOWER_POT);
        // SOUL_TORCH на стенах
        painter.place(xMin, Y_BASE + 5, cz, Material.SOUL_TORCH);
        painter.place(xMax, Y_BASE + 5, cz, Material.SOUL_TORCH);
        count += 6;

        ctx.pois.add(new ElikiumCity.POI("§6Гильдия Искателей",
                "квесты и снаряжение",
                cx, Y_BASE + 9, zMax + 2));
        return count;
    }

    // =========================================================================
    // СКЛАД — 12×10, BLACKSTONE+DARK_OAK, пандус, внешний инвентарь
    // =========================================================================

    private long buildWarehouse(int cx, int cz) {
        long count = 0;
        int w = 16, d = 14;
        int xMin = cx - w / 2, xMax = cx + w / 2;
        int zMin = cz - d / 2, zMax = cz + d / 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 2, zMin - 3, xMax + 4, zMax));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
                count++;
            }
        }

        // Высокие стены (9) POLISHED_BLACKSTONE + DARK_OAK столбы
        count += buildFloor(xMin, zMin, xMax, zMax, Y_BASE + 2, 9,
                Material.POLISHED_BLACKSTONE, Material.DARK_OAK_LOG);
        // Маленькие окна под крышей (IRON_BARS)
        for (int x = xMin + 2; x <= xMax - 2; x += 3) {
            painter.place(x, Y_BASE + 7, zMax, Material.IRON_BARS);
            painter.place(x, Y_BASE + 7, zMin, Material.IRON_BARS);
        }

        // Двускатная крыша
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 11, true,
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
        // Пандус DARK_OAK_SLAB у входа
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

        // Освещение
        painter.place(cx, Y_BASE + 8, cz, Material.LANTERN);
        // SOUL_TORCH на стенах
        painter.place(xMin, Y_BASE + 5, cz, Material.SOUL_TORCH);
        painter.place(xMax, Y_BASE + 5, cz - 3, Material.SOUL_TORCH);
        count += 3;

        ctx.pois.add(new ElikiumCity.POI("§8Склад",
                "товары и припасы",
                xMax + 2, Y_BASE + 6, cz));
        return count;
    }

    // =========================================================================
    // БУДКИ СТРАЖИ У ВОРОТ — 3×3 OAK_PLANKS с крышей
    // =========================================================================

    private long buildAllGateBooths() {
        long count = 0;
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
        int xMin = cx - 2, xMax = cx + 2;
        int zMin = cz - 2, zMax = cz + 2;
        ctx.occupied.add(new ElikiumCity.Footprint(xMin - 1, zMin - 1, xMax + 1, zMax + 1));

        // Фундамент
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                painter.place(x, Y_BASE + 1, z, Material.COBBLED_DEEPSLATE);
            }
        }
        // Стены SPRUCE_PLANKS высотой 5
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean perim = (x == xMin || x == xMax || z == zMin || z == zMax);
                if (!perim) continue;
                for (int dy = 1; dy <= 5; dy++) {
                    painter.place(x, Y_BASE + dy, z, Material.SPRUCE_PLANKS);
                    count++;
                }
            }
        }
        // Угловые столбы DARK_OAK_LOG
        for (int sx : new int[]{-2, +2}) {
            for (int sz : new int[]{-2, +2}) {
                int x = cx + sx, z = cz + sz;
                for (int dy = 1; dy <= 6; dy++) {
                    painter.place(x, Y_BASE + dy, z, Material.DARK_OAK_LOG);
                }
            }
        }
        // Крыша будки — двускатная
        count += buildGableRoof(xMin, zMin, xMax, zMax, Y_BASE + 6,
                false, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
        // Дверь
        painter.place(cx, Y_BASE + 2, zMax, Material.AIR);
        painter.place(cx, Y_BASE + 3, zMax, Material.AIR);
        BlockData boothDoor = Material.DARK_OAK_DOOR.createBlockData(
                "[half=lower,facing=north,hinge=left]");
        BlockData boothDoorTop = Material.DARK_OAK_DOOR.createBlockData(
                "[half=upper,facing=north,hinge=left]");
        painter.placeData(cx, Y_BASE + 2, zMax, boothDoor);
        painter.placeData(cx, Y_BASE + 3, zMax, boothDoorTop);
        // Окошки
        painter.place(cx, Y_BASE + 4, zMin, Material.GLASS_PANE);
        painter.place(cx - 1, Y_BASE + 4, zMax, Material.GLASS_PANE);
        painter.place(cx + 1, Y_BASE + 4, zMax, Material.GLASS_PANE);
        // SOUL_LANTERN на крыше
        painter.place(cx, Y_BASE + 8, cz, Material.SOUL_LANTERN);
        // Котёл с водой и бочки рядом
        painter.place(cx + 3, Y_BASE + 2, cz, Material.WATER_CAULDRON);
        painter.place(cx - 3, Y_BASE + 2, cz, Material.BARREL);
        painter.place(cx - 3, Y_BASE + 2, cz + 1, Material.BARREL);
        count += 8;

        ctx.pois.add(new ElikiumCity.POI("§b" + gateName,
                "вход в Эликий",
                cx, Y_BASE + 6, cz));
        return count;
    }

    // =========================================================================
    // ОБЩИЕ УТИЛИТЫ
    // =========================================================================

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
        // Промежуточные столбы каждые 4 блока
        for (int x = xMin + 4; x <= xMax - 4; x += 4) {
            for (int dy = 0; dy < height; dy++) {
                painter.place(x, yBase + dy, zMin, pillarMat);
                painter.place(x, yBase + dy, zMax, pillarMat);
                count += 2;
            }
        }
        return count;
    }

    private long buildGableRoof(int xMin, int zMin, int xMax, int zMax,
                                 int yBase, boolean roofAlongZ,
                                 Material stairMat, Material fillMat) {
        long count = 0;
        int span = roofAlongZ ? (xMax - xMin) : (zMax - zMin);
        int roofH = span / 2 + 1;
        for (int rise = 0; rise <= roofH; rise++) {
            int y = yBase + rise;
            if (roofAlongZ) {
                int xL = xMin + rise, xR = xMax - rise;
                if (xL > xR) break;
                BlockData stairW = stairMat.createBlockData("[facing=east,half=bottom]");
                BlockData stairE = stairMat.createBlockData("[facing=west,half=bottom]");
                for (int z = zMin - 1; z <= zMax + 1; z++) {
                    if (xL == xR) {
                        painter.place(xL, y, z, fillMat);
                    } else {
                        painter.placeData(xL, y, z, stairW);
                        painter.placeData(xR, y, z, stairE);
                        for (int x = xL + 1; x < xR; x++) {
                            painter.place(x, y, z, fillMat);
                        }
                    }
                    count += (xR - xL + 1);
                }
                for (int x = xL; x <= xR; x++) {
                    painter.place(x, y, zMin, Material.DEEPSLATE_BRICKS);
                    painter.place(x, y, zMax, Material.DEEPSLATE_BRICKS);
                }
            } else {
                int zN = zMin + rise, zS = zMax - rise;
                if (zN > zS) break;
                BlockData stairN = stairMat.createBlockData("[facing=south,half=bottom]");
                BlockData stairS = stairMat.createBlockData("[facing=north,half=bottom]");
                for (int x = xMin - 1; x <= xMax + 1; x++) {
                    if (zN == zS) {
                        painter.place(x, y, zN, fillMat);
                    } else {
                        painter.placeData(x, y, zN, stairN);
                        painter.placeData(x, y, zS, stairS);
                        for (int z = zN + 1; z < zS; z++) {
                            painter.place(x, y, z, fillMat);
                        }
                    }
                    count += (zS - zN + 1);
                }
                for (int z = zN; z <= zS; z++) {
                    painter.place(xMin, y, z, Material.DEEPSLATE_BRICKS);
                    painter.place(xMax, y, z, Material.DEEPSLATE_BRICKS);
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
