package ru.eclipsia.builder.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import java.util.Random;

/**
 * Три внешние дороги, ведущие из города Эликий в большой мир.
 *
 * <ul>
 *   <li><b>Северная</b> ({@code x=0, z=-40..-150}) — к {@code starter_zone};
 *       поля и мельница по бокам.</li>
 *   <li><b>Восточная</b> ({@code x=40..150, z=0}) — к {@code forest_zone};
 *       лес, ручей с мостиком, охотничий домик.</li>
 *   <li><b>Западная</b> ({@code x=-40..-150, z=0}) — к {@code elite_zone};
 *       холмы, заброшенная шахта.</li>
 * </ul>
 *
 * <p>Сами дороги — это {@code GRAVEL} с {@code COBBLESTONE} обочиной,
 * шириной 5 блоков. Уровень укладки выровнен на {@link WorldGenerator#CITY_FLOOR_Y}-1
 * — ровно на тот же {@code y=69}, на котором лежит трава вокруг плато.
 * На каждых 12 блоках по дороге — фонарь ({@link Material#OAK_FENCE}
 * + {@link Material#SOUL_LANTERN}). На отметках 50 и 100 от ворот —
 * указатели {@link FloatingText} «← Город» / «Зона →».
 */
public final class WorldRoads {

    private static final int CX = WorldGenerator.CITY_X;
    private static final int CZ = WorldGenerator.CITY_Z;
    private static final int HALF = WorldGenerator.CITY_HALF;
    /** Уровень дороги — на 1 ниже плато (внутри города плато на 70, снаружи — трава на 69). */
    private static final int ROAD_Y = WorldGenerator.CITY_FLOOR_Y - 1;
    private static final int ROAD_HALF_W = 2;        // ширина 5 блоков
    private static final int LAMP_PERIOD = 12;
    private static final int ROAD_LENGTH = 110;       // 40..150

    private final Plugin plugin;
    private final World world;

    public WorldRoads(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void buildAll(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator: фаза 4 — дороги к зонам…");

        buildRoadNorth(p, rng);
        buildRoadEast(p, rng);
        buildRoadWest(p, rng);
    }

    // =========================================================================
    // СЕВЕРНАЯ ДОРОГА (к полям/starter_zone)
    // =========================================================================

    private void buildRoadNorth(RegionPainter p, Random rng) {
        int x1 = CX, z1 = CZ - HALF - 1;          // (0, -41) — у северных ворот
        int x2 = CX, z2 = CZ - HALF - ROAD_LENGTH; // (0, -150)

        // Подложка — COBBLESTONE (под дорожным полотном) + GRAVEL верх.
        layRoadSegment(p, x1, z1, x2, z2);
        placeLanternsAlongZ(p, x1, z1, x2, z2);

        FloatingText.createSign(plugin, world,
                x1 + 0.5, ROAD_Y + 3, z1 - 49.5, "§7← Город  §fПоля  §a→");
        FloatingText.createSign(plugin, world,
                x1 + 0.5, ROAD_Y + 3, z1 - 99.5, "§7← Город  §aStarter Zone §a→");
    }

    // =========================================================================
    // ВОСТОЧНАЯ ДОРОГА (к лесу/forest_zone) — извилистая
    // =========================================================================

    private void buildRoadEast(RegionPainter p, Random rng) {
        int x1 = CX + HALF + 1, z1 = CZ;
        int x2 = CX + HALF + ROAD_LENGTH, z2 = CZ;

        // Извилистая: 3 прямых отрезка с отклонениями ±5.
        int midX1 = x1 + 35, midZ1 = z1 - 5;
        int midX2 = x1 + 70, midZ2 = z1 + 5;

        // Обочина — COBBLESTONE+GRAVEL шириной 7 (имитируем рваные края).
        // Кладём ПЕРВОЙ, чтобы потом узкая центральная полоса
        // GRAVEL-а её перекрыла, а не наоборот (RegionPainter
        // обрабатывает операции в порядке постановки в очередь).
        p.path(x1, z1, midX1, midZ1, ROAD_Y, ROAD_HALF_W + 1,
                () -> rng.nextDouble() < 0.4 ? Material.COBBLESTONE : Material.GRAVEL);
        p.path(midX1, midZ1, midX2, midZ2, ROAD_Y, ROAD_HALF_W + 1,
                () -> rng.nextDouble() < 0.4 ? Material.COBBLESTONE : Material.GRAVEL);
        p.path(midX2, midZ2, x2, z2, ROAD_Y, ROAD_HALF_W + 1,
                () -> rng.nextDouble() < 0.4 ? Material.COBBLESTONE : Material.GRAVEL);

        // Полотно — чистый GRAVEL шириной 5 поверх обочины.
        p.path(x1, z1, midX1, midZ1, ROAD_Y, ROAD_HALF_W, () -> Material.GRAVEL);
        p.path(midX1, midZ1, midX2, midZ2, ROAD_Y, ROAD_HALF_W, () -> Material.GRAVEL);
        p.path(midX2, midZ2, x2, z2, ROAD_Y, ROAD_HALF_W, () -> Material.GRAVEL);

        placeLanternsAlongX(p, x1, midZ1, midX2, midZ2);

        FloatingText.createSign(plugin, world,
                x1 + 49.5, ROAD_Y + 3, z1 + 0.5, "§7← Город  §fЛес  §2→");
        FloatingText.createSign(plugin, world,
                x1 + 99.5, ROAD_Y + 3, z1 + 0.5, "§7← Город  §2Forest Zone §2→");

        // Деревянный мостик через «ручей» на середине: BIRCH_PLANKS 5×3 над
        // понижением y. Ручей сам мы выложим в WorldSurroundings.
        int bx = x1 + 50, bz = z1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(bx + dx, ROAD_Y + 1, bz + dz, Material.OAK_PLANKS);
            }
        }
        // Перила.
        for (int dx = -2; dx <= 2; dx++) {
            p.place(bx + dx, ROAD_Y + 2, bz - 1, Material.OAK_FENCE);
            p.place(bx + dx, ROAD_Y + 2, bz + 1, Material.OAK_FENCE);
        }
    }

    // =========================================================================
    // ЗАПАДНАЯ ДОРОГА (к холмам/elite_zone)
    // =========================================================================

    private void buildRoadWest(RegionPainter p, Random rng) {
        int x1 = CX - HALF - 1, z1 = CZ;
        int x2 = CX - HALF - ROAD_LENGTH, z2 = CZ;
        layRoadSegment(p, x1, z1, x2, z2);
        placeLanternsAlongX(p, x1, z1, x2, z2);

        FloatingText.createSign(plugin, world,
                x1 - 49.5, ROAD_Y + 3, z1 + 0.5, "§7← Город  §fХолмы  §c→");
        FloatingText.createSign(plugin, world,
                x1 - 99.5, ROAD_Y + 3, z1 + 0.5, "§7← Город  §cElite Zone §c→");
    }

    // =========================================================================
    // Хелперы
    // =========================================================================

    private void layRoadSegment(RegionPainter p, int x1, int z1, int x2, int z2) {
        // Полотно (GRAVEL шириной 5).
        p.path(x1, z1, x2, z2, ROAD_Y, ROAD_HALF_W, () -> Material.GRAVEL);
        // Обочина (COBBLESTONE на крае шириной 7).
        p.path(x1, z1, x2, z2, ROAD_Y, ROAD_HALF_W + 1, () -> Material.COBBLESTONE);
        // Поверх обочины снова GRAVEL по центру (узкая полоса).
        p.path(x1, z1, x2, z2, ROAD_Y, ROAD_HALF_W - 1, () -> Material.GRAVEL);
    }

    private void placeLanternsAlongZ(RegionPainter p, int x1, int z1, int x2, int z2) {
        int dz = (z2 - z1);
        int steps = Math.abs(dz) / LAMP_PERIOD;
        int sign = (int) Math.signum(dz);
        for (int i = 1; i <= steps; i++) {
            int z = z1 + i * LAMP_PERIOD * sign;
            for (int side : new int[] { -ROAD_HALF_W - 2, ROAD_HALF_W + 2 }) {
                int lx = x1 + side;
                for (int dy = 1; dy <= 4; dy++) {
                    p.place(lx, ROAD_Y + dy, z, Material.OAK_FENCE);
                }
                p.place(lx, ROAD_Y + 5, z, Material.SOUL_LANTERN);
            }
        }
    }

    private void placeLanternsAlongX(RegionPainter p, int x1, int z1, int x2, int z2) {
        int dx = (x2 - x1);
        int steps = Math.abs(dx) / LAMP_PERIOD;
        int sign = (int) Math.signum(dx);
        for (int i = 1; i <= steps; i++) {
            int x = x1 + i * LAMP_PERIOD * sign;
            for (int side : new int[] { -ROAD_HALF_W - 2, ROAD_HALF_W + 2 }) {
                int lz = z1 + side;
                for (int dy = 1; dy <= 4; dy++) {
                    p.place(x, ROAD_Y + dy, lz, Material.OAK_FENCE);
                }
                p.place(x, ROAD_Y + 5, lz, Material.SOUL_LANTERN);
            }
        }
    }
}
