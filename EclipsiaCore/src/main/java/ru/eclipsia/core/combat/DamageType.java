package ru.eclipsia.core.combat;

/**
 * Тип урона для расчёта защит и отображения цифр над целью.
 *
 * <p>Цвет — визуальный канал в {@link DamageDisplay}; для поглощения Эгидой
 * тип не важен (Эгида ест ВСЁ первой), для брони важен только PHYSICAL,
 * для резистов — FIRE/COLD/LIGHTNING.
 */
public enum DamageType {
    PHYSICAL("§f"),     // белый
    FIRE("§6"),         // оранжевый
    COLD("§b"),         // голубой
    LIGHTNING("§e"),    // жёлтый
    /** Псевдо-тип для отображения: красный жирный с восклицательным знаком. */
    CRIT("§c§l"),
    /** Урон, не классифицируемый как физ/стихийный — без брони и без резистов (например, чистый, true damage). */
    TRUE("§5");

    private final String color;

    DamageType(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }
}
