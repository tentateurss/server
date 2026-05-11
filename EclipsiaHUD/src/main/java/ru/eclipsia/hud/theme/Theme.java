package ru.eclipsia.hud.theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ru.eclipsia.core.combat.DamageType;

import java.util.Locale;

/**
 * Централизованная палитра и хелперы рендера компонентов.
 *
 * <p>Цвета классов, рарностей и типов урона раньше были рассыпаны по разным
 * местам ({@code DamageType.getColor()}, {@code PlayerHUDManager.getClassDisplayName()},
 * сообщения в чате). Этот класс — единственный источник правды.
 *
 * <p>Адаптеры:
 * <ul>
 *   <li>{@link #mm(String)} — десериализация MiniMessage. Все шаблоны в
 *       {@code config.yml} проходят через него.</li>
 *   <li>{@link #classColor(String)} — цвет для названия класса.</li>
 *   <li>{@link #damageColor(DamageType)} — цвет цифры урона.</li>
 *   <li>{@link #rarityColor(String)} — цвет рарности предмета (по аналогии с PoE).</li>
 * </ul>
 */
public final class Theme {

    private Theme() { /* utility */ }

    // ====== ПАЛИТРА ======

    /** Акцент сервера (золото) — для логотипа, бордюров sidebar. */
    public static final TextColor ACCENT_GOLD    = TextColor.color(0xFFD166);
    /** Акцент сервера (малиновый) — пара к ACCENT_GOLD для градиентов. */
    public static final TextColor ACCENT_CRIMSON = TextColor.color(0xEF476F);
    /** Холодный акцент (бирюза) — мана, магия, эгида. */
    public static final TextColor ACCENT_CYAN    = TextColor.color(0x06D6A0);
    public static final TextColor ACCENT_AQUA    = TextColor.color(0x118AB2);
    public static final TextColor ACCENT_DARK    = TextColor.color(0x073B4C);
    /** Универсальный «безопасный» серый для подписей. */
    public static final TextColor SOFT_GRAY      = TextColor.color(0x8C9BA5);

    // ====== КЛАССЫ ======

    private static final TextColor CLASS_WARRIOR = TextColor.color(0xE07A5F); // тёплый красный
    private static final TextColor CLASS_ARCHER  = TextColor.color(0x81B29A); // мшисто-зелёный
    private static final TextColor CLASS_MAGE    = TextColor.color(0x3D5A80); // тёмно-синий

    /**
     * Цвет, ассоциированный с классом. Используется в sidebar/tablist/title.
     *
     * @param classId идентификатор класса ({@code warrior}, {@code archer}, {@code mage})
     * @return {@link NamedTextColor#WHITE} если класс не распознан.
     */
    public static TextColor classColor(String classId) {
        if (classId == null) return NamedTextColor.WHITE;
        return switch (classId.toLowerCase(Locale.ROOT)) {
            case "warrior" -> CLASS_WARRIOR;
            case "archer"  -> CLASS_ARCHER;
            case "mage"    -> CLASS_MAGE;
            default        -> NamedTextColor.WHITE;
        };
    }

    /** Человекочитаемое имя класса для UI. */
    public static String classDisplayName(String classId) {
        if (classId == null) return "—";
        return switch (classId.toLowerCase(Locale.ROOT)) {
            case "warrior" -> "Воин";
            case "archer"  -> "Лучник";
            case "mage"    -> "Маг";
            default        -> classId;
        };
    }

    /** Иконка-юникод класса (используется в sidebar и tablist). */
    public static String classIcon(String classId) {
        if (classId == null) return "✦";
        return switch (classId.toLowerCase(Locale.ROOT)) {
            case "warrior" -> "⚔";
            case "archer"  -> "➹";
            case "mage"    -> "✦";
            default        -> "✦";
        };
    }

    // ====== РАРНОСТЬ ======

    /**
     * PoE-style рарности. Те же градации, что в {@code RarityManager}
     * (Normal/Magic/Rare/Unique). Если в будущем добавится Set/Legendary —
     * расширяй здесь и нигде больше.
     */
    public static TextColor rarityColor(String rarity) {
        if (rarity == null) return NamedTextColor.WHITE;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "NORMAL"   -> TextColor.color(0xFFFFFF);
            case "MAGIC"    -> TextColor.color(0x6699FF);
            case "RARE"     -> TextColor.color(0xFFFF77);
            case "UNIQUE"   -> TextColor.color(0xAF6025);
            case "SET"      -> TextColor.color(0x00FF80);
            case "LEGENDARY"-> TextColor.color(0xFF6BFF);
            default         -> NamedTextColor.WHITE;
        };
    }

    // ====== ТИПЫ УРОНА ======

    private static final TextColor DMG_PHYSICAL  = TextColor.color(0xF1F1F1);
    private static final TextColor DMG_FIRE      = TextColor.color(0xFF7043);
    private static final TextColor DMG_COLD      = TextColor.color(0x4FC3F7);
    private static final TextColor DMG_LIGHTNING = TextColor.color(0xFFEE58);
    private static final TextColor DMG_CRIT      = TextColor.color(0xFF1744);
    private static final TextColor DMG_TRUE      = TextColor.color(0xCE93D8);

    public static TextColor damageColor(DamageType type) {
        if (type == null) return DMG_PHYSICAL;
        return switch (type) {
            case PHYSICAL  -> DMG_PHYSICAL;
            case FIRE      -> DMG_FIRE;
            case COLD      -> DMG_COLD;
            case LIGHTNING -> DMG_LIGHTNING;
            case CRIT      -> DMG_CRIT;
            case TRUE      -> DMG_TRUE;
        };
    }

    // ====== MINIMESSAGE ======

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Десериализация MiniMessage-строки в Component. */
    public static Component mm(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return MM.deserialize(input);
    }

    /** То же, но с поддержкой плейсхолдеров. */
    public static Component mm(String input, java.util.Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) return Component.empty();
        String out = input;
        for (var e : placeholders.entrySet()) {
            out = out.replace("<" + e.getKey() + ">", e.getValue());
        }
        return MM.deserialize(out);
    }
}
