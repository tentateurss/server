package ru.eclipsia.items.affix;

import java.util.List;

/**
 * Аффикс предмета (префикс или суффикс).
 *
 * <p>{@link #getStatKey()} возвращает каноническую строку, которую читает
 * {@code EquipmentBonusApplier.parseBonusLine} и {@code StatResolver}.
 * {@link #getDescription(int)} формирует красивый русский лор.
 */
public class Affix {

    private final String id;
    private final String name;
    private final AffixType type;
    private final int tier;
    private final int minValue;
    private final int maxValue;
    private final List<String> itemTypes;
    private final int minItemLevel;

    public Affix(String id, String name, AffixType type, int tier, int minValue, int maxValue,
                 List<String> itemTypes, int minItemLevel) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.tier = tier;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.itemTypes = itemTypes;
        this.minItemLevel = minItemLevel;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public AffixType getType() { return type; }
    public int getTier() { return tier; }
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }
    public List<String> getItemTypes() { return itemTypes; }
    public int getMinItemLevel() { return minItemLevel; }

    public boolean canApplyTo(String itemType) { return itemTypes.contains(itemType); }
    public boolean isValidForLevel(int itemLevel) { return itemLevel >= minItemLevel; }

    public int rollValue() {
        return minValue + (int) (Math.random() * (maxValue - minValue + 1));
    }

    /** Описание для лора предмета: «§7T1 §7Острый §8(§7Урон: §a+5§8)». */
    public String getDescription(int value) {
        Mapping m = mapping();
        String sign = value >= 0 ? "+" : "";
        String pct = m.percent ? "%" : "";
        String tierBadge = (type == AffixType.IMPLICIT) ? "§8[i] " : tierBadge();
        return tierBadge + "§7" + name + " §8(§7" + m.label + ": §a" + sign + value + pct + "§8)";
    }

    /** «§7T1»/«§9T2»/«§eT3»/«§6T4» — короткий префикс перед именем. */
    private String tierBadge() {
        String color = switch (tier) {
            case 1 -> "§7";
            case 2 -> "§9";
            case 3 -> "§e";
            case 4 -> "§6";
            default -> "§7";
        };
        return color + "T" + tier + " ";
    }

    /** Каноническое имя стата (для StatResolver). */
    public String getStatKey() { return mapping().key; }

    /** Подсказка для парсера лора. */
    public String getStatLabel() { return mapping().label; }

    public boolean isPercent() { return mapping().percent; }

    // =========================================================================
    // ID → stat key / label / percent
    // =========================================================================

    private static final class Mapping {
        final String key, label;
        final boolean percent;
        Mapping(String key, String label, boolean percent) {
            this.key = key; this.label = label; this.percent = percent;
        }
    }

    private Mapping mapping() {
        String s = id.toLowerCase();
        // ----- elemental damage -----
        if (s.startsWith("fire_damage"))      return new Mapping("fire_damage",      "Урон огнём",   false);
        if (s.startsWith("cold_damage"))      return new Mapping("cold_damage",      "Урон холодом", false);
        if (s.startsWith("lightning_damage")) return new Mapping("lightning_damage", "Урон молнией", false);
        if (s.startsWith("physical_damage"))  return new Mapping("physical_damage",  "Физ. урон",   false);

        // ----- resists -----
        if (s.startsWith("fire_resist"))      return new Mapping("fire_resist",      "Сопр. огню",     true);
        if (s.startsWith("cold_resist"))      return new Mapping("cold_resist",      "Сопр. холоду",   true);
        if (s.startsWith("lightning_resist")) return new Mapping("lightning_resist", "Сопр. молнии",   true);

        // ----- defense -----
        if (s.startsWith("evasion"))          return new Mapping("evasion",      "Уклонение",      false);
        if (s.startsWith("block_chance"))     return new Mapping("block_chance", "Шанс блока",      true);
        if (s.startsWith("block_amount"))     return new Mapping("block_amount", "Сила блока",      true);
        if (s.startsWith("aegis_regen"))      return new Mapping("aegis_regen",  "Реген Эгиды",    false);
        if (s.startsWith("aegis"))            return new Mapping("aegis",        "Эгида",          false);
        if (s.startsWith("armour") || s.startsWith("armor"))
                                              return new Mapping("armour",       "Броня",          false);

        // ----- regen / resources -----
        if (s.startsWith("health_regen"))     return new Mapping("health_regen", "Реген здоровья", false);
        if (s.startsWith("mana_regen"))       return new Mapping("mana_regen",   "Реген маны",     false);
        if (s.startsWith("mana"))             return new Mapping("mana",         "Мана",           false);
        if (s.startsWith("health"))           return new Mapping("health",       "Здоровье",       false);

        // ----- offence -----
        if (s.startsWith("crit_chance"))      return new Mapping("crit_chance",  "Шанс крита",     true);
        if (s.startsWith("crit_damage") || s.startsWith("crit"))
                                              return new Mapping("crit_damage",  "Крит. урон",     true);
        if (s.startsWith("attack_speed") || s.startsWith("speed"))
                                              return new Mapping("attack_speed", "Скор. атаки",    true);
        if (s.startsWith("damage"))           return new Mapping("damage",       "Урон",           false);

        // ----- movement -----
        if (s.startsWith("move_speed"))       return new Mapping("move_speed",   "Скор. бега",     true);

        // ----- core stats -----
        if (s.startsWith("strength"))         return new Mapping("strength",     "Сила",           false);
        if (s.startsWith("dexterity"))        return new Mapping("dexterity",    "Ловкость",       false);
        if (s.startsWith("intelligence"))     return new Mapping("intelligence", "Интеллект",      false);

        // unique items / fallback
        return new Mapping("damage", "Бонус", false);
    }
}
