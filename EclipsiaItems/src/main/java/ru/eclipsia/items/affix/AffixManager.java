package ru.eclipsia.items.affix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.items.rarity.ItemRarity;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Менеджер аффиксов.
 *
 * <p>Грузит три секции из {@code affixes.yml}: {@code prefixes:},
 * {@code suffixes:} и {@code implicits:}. Implicit-аффикс — «врождённый»
 * стат базы (амулет → +5 ко всем резистам и т.п.); он отдаётся
 * {@code ItemGenerator} независимо от редкости.
 *
 * <p>Метод {@link #rollAffixes(String, int, ItemRarity)} строит ролл по
 * правилам PoE: фильтр по тиру, отдельные слоты префикс/суффикс, бюджет
 * очков. Старый {@link #getRandomAffixes(String, int, int)} оставлен
 * только для совместимости с уник-итемами.
 */
public class AffixManager {

    /** Минимальный тир в пуле в зависимости от уровня предмета. */
    private static int minTierForItemLevel(int itemLevel) {
        if (itemLevel >= 35) return 3;
        if (itemLevel >= 20) return 2;
        return 1;
    }

    private final Plugin plugin;
    private final Map<String, Affix> prefixes;
    private final Map<String, Affix> suffixes;
    private final Map<String, Affix> implicits;
    private final Random random = new Random();
    
    public AffixManager(Plugin plugin) {
        this.plugin = plugin;
        this.prefixes = new HashMap<>();
        this.suffixes = new HashMap<>();
        this.implicits = new HashMap<>();
    }
    
    public void loadAffixes() {
        prefixes.clear();
        suffixes.clear();
        implicits.clear();

        File affixesFile = new File(plugin.getDataFolder(), "affixes.yml");
        if (!affixesFile.exists()) {
            plugin.getLogger().severe("Файл affixes.yml не найден!");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(affixesFile);

        loadSection(config, "prefixes",  AffixType.PREFIX,   prefixes);
        loadSection(config, "suffixes",  AffixType.SUFFIX,   suffixes);
        loadSection(config, "implicits", AffixType.IMPLICIT, implicits);

        plugin.getLogger().info("Загружено префиксов: "  + prefixes.size());
        plugin.getLogger().info("Загружено суффиксов: "  + suffixes.size());
        plugin.getLogger().info("Загружено implicit-аффиксов: " + implicits.size());
    }

    private void loadSection(FileConfiguration config, String path,
                             AffixType type, Map<String, Affix> target) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                Affix affix = loadAffix(key, section.getConfigurationSection(key), type);
                target.put(key, affix);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Ошибка загрузки аффикса " + path + "/" + key, e);
            }
        }
    }
    
    private Affix loadAffix(String id, ConfigurationSection section, AffixType type) {
        String name = section.getString("name", id);
        int tier = section.getInt("tier", 1);
        int minValue = section.getInt("min-value", 1);
        int maxValue = section.getInt("max-value", 10);
        List<String> itemTypes = section.getStringList("item-types");
        int minItemLevel = section.getInt("min-item-level", 1);
        
        return new Affix(id, name, type, tier, minValue, maxValue, itemTypes, minItemLevel);
    }

    public Affix getPrefix(String id) { return prefixes.get(id); }
    public Affix getSuffix(String id) { return suffixes.get(id); }
    public Affix getImplicit(String id) { return implicits.get(id); }

    public Collection<Affix> getAllPrefixes() { return prefixes.values(); }
    public Collection<Affix> getAllSuffixes() { return suffixes.values(); }
    public Collection<Affix> getAllImplicits() { return implicits.values(); }

    /** Implicit-аффиксы для конкретного типа предмета. */
    public List<Affix> getImplicitsFor(String itemType) {
        return implicits.values().stream()
                .filter(a -> a.canApplyTo(itemType))
                .collect(Collectors.toList());
    }

    /**
     * Старый ролл по фиксированному count — оставлен для уник-итемов
     * и совместимости. Для нового лута используй {@link #rollAffixes}.
     */
    public List<Affix> getRandomAffixes(String itemType, int itemLevel, int count) {
        List<Affix> result = new ArrayList<>();
        List<Affix> availablePrefixes = filtered(prefixes.values(), itemType, itemLevel, 99);
        List<Affix> availableSuffixes = filtered(suffixes.values(), itemType, itemLevel, 99);

        int prefixCount = count / 2;
        int suffixCount = count - prefixCount;

        Collections.shuffle(availablePrefixes);
        for (int i = 0; i < Math.min(prefixCount, availablePrefixes.size()); i++) {
            result.add(availablePrefixes.get(i));
        }
        Collections.shuffle(availableSuffixes);
        for (int i = 0; i < Math.min(suffixCount, availableSuffixes.size()); i++) {
            result.add(availableSuffixes.get(i));
        }
        return result;
    }

    /**
     * Ролл аффиксов по новой схеме: тир ≤ {@code rarity.maxAffixTier},
     * лимит префиксов/суффиксов ({@code maxPrefixes}/{@code maxSuffixes}),
     * стоимость в очках бюджета. Возвращает уже укомплектованный список,
     * без дубликатов по {@code statKey}.
     */
    public List<Affix> rollAffixes(String itemType, int itemLevel, ItemRarity rarity) {
        if (rarity == ItemRarity.NORMAL || rarity == ItemRarity.UNIQUE) {
            return Collections.emptyList();
        }

        int maxTier  = rarity.getMaxAffixTier();
        int minTier  = minTierForItemLevel(itemLevel);
        int budget   = rarity.getAffixBudget();
        int maxPre   = rarity.getMaxPrefixes();
        int maxSuf   = rarity.getMaxSuffixes();

        List<Affix> prefixPool = filtered(prefixes.values(), itemType, itemLevel, maxTier, minTier);
        List<Affix> suffixPool = filtered(suffixes.values(), itemType, itemLevel, maxTier, minTier);

        Collections.shuffle(prefixPool, random);
        Collections.shuffle(suffixPool, random);

        Set<String> usedStatKeys = new HashSet<>();
        List<Affix> result = new ArrayList<>();
        int prefixCount = 0;
        int suffixCount = 0;

        // Чередуем слоты пока есть бюджет и место
        while (budget > 0) {
            boolean tookSomething = false;

            if (prefixCount < maxPre) {
                Affix picked = pickFirstFitting(prefixPool, usedStatKeys, budget);
                if (picked != null) {
                    result.add(picked);
                    usedStatKeys.add(picked.getStatKey());
                    prefixPool.remove(picked);
                    budget -= ItemRarity.affixCost(picked.getTier());
                    prefixCount++;
                    tookSomething = true;
                }
            }
            if (budget > 0 && suffixCount < maxSuf) {
                Affix picked = pickFirstFitting(suffixPool, usedStatKeys, budget);
                if (picked != null) {
                    result.add(picked);
                    usedStatKeys.add(picked.getStatKey());
                    suffixPool.remove(picked);
                    budget -= ItemRarity.affixCost(picked.getTier());
                    suffixCount++;
                    tookSomething = true;
                }
            }

            if (!tookSomething) break;
        }
        return result;
    }

    /** Первый аффикс из списка, который влезает в бюджет и не дублирует статы. */
    private Affix pickFirstFitting(List<Affix> pool, Set<String> usedStats, int budget) {
        for (Affix a : pool) {
            if (usedStats.contains(a.getStatKey())) continue;
            if (ItemRarity.affixCost(a.getTier()) <= budget) return a;
        }
        return null;
    }

    private static List<Affix> filtered(Collection<Affix> src, String itemType,
                                        int itemLevel, int maxTier) {
        return filtered(src, itemType, itemLevel, maxTier, 1);
    }

    private static List<Affix> filtered(Collection<Affix> src, String itemType,
                                        int itemLevel, int maxTier, int minTier) {
        return src.stream()
                .filter(a -> a.canApplyTo(itemType))
                .filter(a -> a.isValidForLevel(itemLevel))
                .filter(a -> a.getTier() <= maxTier)
                .filter(a -> a.getTier() >= minTier)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public int getAffixCount() {
        return prefixes.size() + suffixes.size() + implicits.size();
    }
}
