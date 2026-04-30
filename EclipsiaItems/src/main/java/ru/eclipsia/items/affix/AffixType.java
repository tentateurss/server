package ru.eclipsia.items.affix;

/**
 * Тип аффикса.
 *
 * <p>{@link #IMPLICIT} — «врождённый» аффикс базового предмета: всегда
 * присутствует, не считается ни префиксом, ни суффиксом, не занимает
 * слот при ролле редкости.
 */
public enum AffixType {
    PREFIX,
    SUFFIX,
    IMPLICIT
}
