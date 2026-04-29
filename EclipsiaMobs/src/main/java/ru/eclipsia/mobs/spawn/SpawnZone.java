package ru.eclipsia.mobs.spawn;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Зона спавна мобов
 */
public class SpawnZone {
    
    private final String id;
    private final String world;
    private final Location center;
    private final double radius;
    private final int minLevel;
    private final int maxLevel;
    private final List<String> mobs;
    private final int spawnRate;
    private final int maxMobs;

    public SpawnZone(String id, ConfigurationSection config) {
        this.id = id;
        this.world = config.getString("world", "world");
        
        ConfigurationSection centerSection = config.getConfigurationSection("center");
        this.center = new Location(
            null, // World будет установлен позже
            centerSection.getDouble("x", 0),
            centerSection.getDouble("y", 70),
            centerSection.getDouble("z", 0)
        );
        
        this.radius = config.getDouble("radius", 50);
        this.minLevel = config.getInt("min-level", 1);
        this.maxLevel = config.getInt("max-level", 10);
        this.mobs = config.getStringList("mobs");
        this.spawnRate = config.getInt("spawn-rate", 5);
        // Per-zone override (arena → 1 для единственного босса).
        // 0 или отсутствие поля = использовать глобальный лимит.
        this.maxMobs = config.getInt("max-mobs", 0);
    }
    
    /**
     * Проверить находится ли локация в зоне
     */
    public boolean isInZone(Location location) {
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        
        return location.distance(getCenterWithWorld(location.getWorld())) <= radius;
    }
    
    /**
     * Получить случайную локацию в зоне.
     * <p>Алгоритм поиска безопасного Y:
     * <ol>
     *   <li>Стартуем с {@code center.getY() + 6} (выше уровня леса).</li>
     *   <li>Идём вниз до первого твёрдого блока («земли»).</li>
     *   <li>Проверяем, что над землёй <b>2 блока воздуха</b> (для туловища
     *       и головы любого моба).</li>
     *   <li>Если воздуха нет (попали в дерево/стену) — возвращаем
     *       {@code null}: SpawnManager пропустит этот тик и попробует ещё раз.</li>
     * </ol>
     * Это устраняет suffocation мобов внутри блоков структур и падения
     * с верхушек мёртвых деревьев — moб появляется только на чистой
     * поверхности.
     */
    public Location getRandomLocation(org.bukkit.World world) {
        double angle = Math.random() * 2 * Math.PI;
        double distance = Math.random() * radius;

        int x = (int) Math.round(center.getX() + distance * Math.cos(angle));
        int z = (int) Math.round(center.getZ() + distance * Math.sin(angle));

        // Расширенный диапазон поиска поверхности: раньше окно было ±6/−4
        // и на гористом Береге с перепадом высот 4..40 половина точек не
        // находила землю — мобы не спавнились. Теперь смотрим широкое
        // окно вокруг центра зоны.
        int startY = Math.min(world.getMaxHeight() - 2, (int) center.getY() + 40);
        int minY   = Math.max(world.getMinHeight() + 1, (int) center.getY() - 20);

        // Ищем сверху вниз твёрдый блок (листья/стекло пропускаем).
        int groundY = -1;
        for (int y = startY; y >= minY; y--) {
            org.bukkit.Material m = world.getBlockAt(x, y, z).getType();
            if (!m.isSolid()) continue;
            if (m.name().endsWith("LEAVES") || m == org.bukkit.Material.GLASS) continue;
            groundY = y;
            break;
        }
        if (groundY < 0) return null;

        int feetY = groundY + 1;
        // Проверяем 2 блока свободного воздуха над землёй.
        if (world.getBlockAt(x, feetY,     z).getType().isSolid()) return null;
        if (world.getBlockAt(x, feetY + 1, z).getType().isSolid()) return null;

        return new Location(world, x + 0.5, feetY, z + 0.5);
    }
    
    /**
     * Получить случайного моба из зоны
     */
    public String getRandomMob() {
        if (mobs.isEmpty()) return null;
        return mobs.get((int) (Math.random() * mobs.size()));
    }
    
    /**
     * Проверить подходит ли уровень игрока для зоны
     */
    public boolean isLevelSuitable(int playerLevel) {
        return playerLevel >= minLevel && playerLevel <= maxLevel;
    }
    
    private Location getCenterWithWorld(org.bukkit.World world) {
        Location loc = center.clone();
        loc.setWorld(world);
        return loc;
    }
    
    // Getters
    public String getId() { return id; }
    public String getWorldName() { return world; }
    public Location getCenter() { return center; }
    public double getRadius() { return radius; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public List<String> getMobs() { return mobs; }
    public int getSpawnRate() { return spawnRate; }
    public int getMaxMobs() { return maxMobs; }
}
