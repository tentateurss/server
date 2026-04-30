package ru.eclipsia.items.item;

import ru.eclipsia.items.affix.Affix;
import ru.eclipsia.items.affix.AffixType;
import ru.eclipsia.items.rarity.ItemRarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кастомный предмет с аффиксами.
 *
 * <p>Хранит три категории аффиксов:
 * <ul>
 *   <li>{@code implicits} — врождённые статы базы, есть на любой редкости</li>
 *   <li>{@code prefixes} — ролл по бюджету; добавляются в название слева</li>
 *   <li>{@code suffixes} — ролл по бюджету; добавляются в название справа</li>
 * </ul>
 */
public class CustomItem {

    private final BaseItem baseItem;
    private final ItemRarity rarity;
    private final int itemLevel;
    private final Map<Affix, Integer> implicits;
    private final Map<Affix, Integer> prefixes;
    private final Map<Affix, Integer> suffixes;

    public CustomItem(BaseItem baseItem, ItemRarity rarity, int itemLevel) {
        this.baseItem = baseItem;
        this.rarity = rarity;
        this.itemLevel = itemLevel;
        this.implicits = new LinkedHashMap<>();
        this.prefixes = new LinkedHashMap<>();
        this.suffixes = new LinkedHashMap<>();
    }

    public void addAffix(Affix affix, int value) {
        switch (affix.getType()) {
            case IMPLICIT -> implicits.put(affix, value);
            case PREFIX   -> prefixes.put(affix, value);
            case SUFFIX   -> suffixes.put(affix, value);
        }
    }

    public BaseItem getBaseItem()   { return baseItem; }
    public ItemRarity getRarity()   { return rarity; }
    public int getItemLevel()       { return itemLevel; }

    /** Все аффиксы (implicit + префикс + суффикс) — для лора в порядке вывода. */
    public Map<Affix, Integer> getAffixes() {
        Map<Affix, Integer> out = new LinkedHashMap<>();
        out.putAll(implicits);
        out.putAll(prefixes);
        out.putAll(suffixes);
        return out;
    }

    public Map<Affix, Integer> getImplicits() { return new LinkedHashMap<>(implicits); }
    public Map<Affix, Integer> getPrefixes()  { return new LinkedHashMap<>(prefixes); }
    public Map<Affix, Integer> getSuffixes()  { return new LinkedHashMap<>(suffixes); }

    /**
     * Полное название предмета. Для редких/уникальных дописывается
     * «(ил X)» в конце — для трейда / быстрой оценки.
     */
    public String getFullName() {
        StringBuilder name = new StringBuilder();
        name.append(rarity.getColor());

        // Префикс (первый, если есть)
        for (Affix affix : prefixes.keySet()) {
            name.append(affix.getName()).append(' ');
            break;
        }

        name.append(baseItem.getName());

        // Суффикс (первый, если есть)
        for (Affix affix : suffixes.keySet()) {
            name.append(' ').append(affix.getName());
            break;
        }

        // Item level в название для редких и уникальных
        if (rarity == ItemRarity.RARE || rarity == ItemRarity.UNIQUE) {
            name.append(" §8(ил ").append(itemLevel).append(')');
        }
        return name.toString();
    }

    public int getTotalDamage() {
        int total = baseItem.getBaseDamage();
        for (Map.Entry<Affix, Integer> entry : allAffixesEntries()) {
            if (entry.getKey().getId().contains("damage")) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public int getTotalArmor() {
        int total = baseItem.getBaseArmor();
        for (Map.Entry<Affix, Integer> entry : allAffixesEntries()) {
            if (entry.getKey().getId().contains("armor")) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public int getHealthBonus() { return sumByIdSubstring("health"); }
    public int getCritBonus()   { return sumByIdSubstring("crit"); }
    public int getSpeedBonus()  { return sumByIdSubstring("speed"); }

    private int sumByIdSubstring(String needle) {
        int total = 0;
        for (Map.Entry<Affix, Integer> entry : allAffixesEntries()) {
            if (entry.getKey().getId().contains(needle)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private Iterable<Map.Entry<Affix, Integer>> allAffixesEntries() {
        List<Map.Entry<Affix, Integer>> all = new ArrayList<>(implicits.size()
                + prefixes.size() + suffixes.size());
        all.addAll(implicits.entrySet());
        all.addAll(prefixes.entrySet());
        all.addAll(suffixes.entrySet());
        return all;
    }
}
