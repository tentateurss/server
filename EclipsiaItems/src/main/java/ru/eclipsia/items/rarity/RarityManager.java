package ru.eclipsia.items.rarity;

import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * Менеджер редкости предметов.
 *
 * <p>Шансы редкости больше не плоские: с ростом уровня моба сдвиг идёт в
 * сторону Rare/Unique. Дополнительно учитывается стат игрока {@code magic_find}
 * (в %), который повышает шанс выпадения Magic+ за счёт Normal.
 */
public class RarityManager {

    private final Plugin plugin;
    private final Random random;

    public RarityManager(Plugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /** Старая сигнатура — теперь зовёт новую с level=1, magicFind=0. */
    public ItemRarity getRandomRarity() {
        return getRandomRarity(1, 0);
    }

    /**
     * Получить случайную редкость с учётом уровня моба и Magic Find игрока.
     *
     * <p>Базовые шансы берутся из {@code config.yml} (низкоуровневый и
     * высокоуровневый профили), между ними линейная интерполяция по
     * уровню моба относительно границы {@code level-scaling.cap-level}.
     * После этого шанс Normal уменьшается на {@code magicFind}%, разница
     * пропорционально пересыпается в Magic/Rare/Unique.
     */
    public ItemRarity getRandomRarity(int mobLevel, int magicFindPercent) {
        // ----- Базовые профили -----
        int lowNormal = plugin.getConfig().getInt("generation.rarity-chances.normal", 70);
        int lowMagic  = plugin.getConfig().getInt("generation.rarity-chances.magic",  25);
        int lowRare   = plugin.getConfig().getInt("generation.rarity-chances.rare",   5);
        int lowUnique = plugin.getConfig().getInt("generation.rarity-chances.unique", 0);

        int hiNormal  = plugin.getConfig().getInt("generation.rarity-chances-high.normal", 20);
        int hiMagic   = plugin.getConfig().getInt("generation.rarity-chances-high.magic",  35);
        int hiRare    = plugin.getConfig().getInt("generation.rarity-chances-high.rare",   35);
        int hiUnique  = plugin.getConfig().getInt("generation.rarity-chances-high.unique", 10);

        int capLevel  = plugin.getConfig().getInt("generation.level-scaling.cap-level", 50);

        double t = clamp01((double) Math.max(0, mobLevel - 1) / Math.max(1, capLevel - 1));
        double pNormal = lerp(lowNormal, hiNormal, t);
        double pMagic  = lerp(lowMagic,  hiMagic,  t);
        double pRare   = lerp(lowRare,   hiRare,   t);
        double pUnique = lerp(lowUnique, hiUnique, t);

        // ----- Magic Find: % переноса с Normal в Magic+ -----
        double mf = Math.max(0, magicFindPercent) / 100.0;
        double moved = pNormal * mf;
        pNormal -= moved;
        // распределяем поровну в magic/rare/unique
        pMagic  += moved * 0.5;
        pRare   += moved * 0.4;
        pUnique += moved * 0.1;

        double total = pNormal + pMagic + pRare + pUnique;
        if (total <= 0) return ItemRarity.NORMAL;

        double roll = random.nextDouble() * total;
        if (roll < pNormal)                       return ItemRarity.NORMAL;
        if (roll < pNormal + pMagic)              return ItemRarity.MAGIC;
        if (roll < pNormal + pMagic + pRare)      return ItemRarity.RARE;
        return ItemRarity.UNIQUE;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    /**
     * @deprecated с переходом на бюджетный ролл количество аффиксов больше
     *             не используется. Метод оставлен для совместимости.
     */
    @Deprecated
    public int getAffixCount(ItemRarity rarity) {
        return switch (rarity) {
            case NORMAL -> 0;
            case MAGIC  -> 2;
            case RARE   -> 5;
            case UNIQUE -> 0;
        };
    }
}
