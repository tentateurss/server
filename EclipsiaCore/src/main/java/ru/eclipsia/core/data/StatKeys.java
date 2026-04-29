package ru.eclipsia.core.data;

/**
 * Канонические имена ключей в {@link PlayerProfile#getStat(String)}.
 *
 * <p>Все статы (включая новые из расширенной RPG-системы PoE-style) хранятся
 * в одном Map&lt;String,Integer&gt; внутри профиля, что позволяет добавлять
 * новые статы без миграции схемы. Эта константная таблица — единственный
 * источник правды по именам, чтобы между EclipsiaCore / Items / Skills /
 * Perks не было опечаток типа "fire_resistance" vs "fire_resist".
 *
 * <p>Старые базовые статы Сила/Ловкость/Интеллект остаются (растут с уровнем
 * класса). Новые добавляются ПОВЕРХ, в основном через лор экипировки и
 * перки дерева.
 */
public final class StatKeys {

    private StatKeys() { /* utility */ }

    // ===== БАЗОВЫЕ (растут с уровнем класса) =====
    public static final String STRENGTH     = "strength";
    public static final String DEXTERITY    = "dexterity";
    public static final String INTELLIGENCE = "intelligence";

    // ===== РЕСУРСЫ (помимо currentMana/maxMana и aegis/maxAegis в профиле) =====
    /** Бонус к максимуму HP (поверх базовой формулы класса). */
    public static final String HEALTH_BONUS = "health";
    /** Бонус к максимуму маны (поверх базовой формулы класса). */
    public static final String MANA_BONUS   = "mana";

    // ===== РЕГЕНЕРАЦИЯ (за тик, тик = 1 сек в CustomRegenerationListener) =====
    public static final String HEALTH_REGEN = "health_regen";
    public static final String MANA_REGEN   = "mana_regen";
    public static final String AEGIS_REGEN  = "aegis_regen";
    /** Секунды от последнего урона до начала восстановления Эгиды. База 5. */
    public static final String AEGIS_DELAY  = "aegis_delay";

    // ===== ЗАЩИТА =====
    /** Рейтинг брони. {@code reduction = armour / (armour + damage * 5)}, кап 90 %. */
    public static final String ARMOUR        = "armour";
    /** Рейтинг уклонения. {@code chance = ev / (ev + atkLevel*10)}, кап 75 %. */
    public static final String EVASION       = "evasion";
    /** Шанс блока (требует щит в OFFHAND), кап 75 %. */
    public static final String BLOCK_CHANCE  = "block_chance";
    /** Доля поглощённого блоком урона, %. */
    public static final String BLOCK_AMOUNT  = "block_amount";
    /** Резисты — прямые проценты, кап 75 % (90 % с пассивками). */
    public static final String FIRE_RESIST       = "fire_resist";
    public static final String COLD_RESIST       = "cold_resist";
    public static final String LIGHTNING_RESIST  = "lightning_resist";

    // ===== АТАКА =====
    /** Базовый физ. урон с оружия + бонусы. */
    public static final String PHYSICAL_DAMAGE   = "physical_damage";
    /** Стихийный «добавочный» урон, прибавляется к любому навыку. */
    public static final String FIRE_DAMAGE       = "fire_damage";
    public static final String COLD_DAMAGE       = "cold_damage";
    public static final String LIGHTNING_DAMAGE  = "lightning_damage";
    /** Шанс крита, % (кап 95 %). */
    public static final String CRIT_CHANCE       = "crit_chance";
    /** Множитель крита, % (база 150 %). */
    public static final String CRIT_DAMAGE       = "crit_damage";
    /** Скорость атаки, % бонуса (1.0 = +1 %). */
    public static final String ATTACK_SPEED      = "attack_speed";

    // ===== ДВИЖЕНИЕ =====
    /** Скорость бега, % бонуса. */
    public static final String MOVE_SPEED        = "move_speed";
}
