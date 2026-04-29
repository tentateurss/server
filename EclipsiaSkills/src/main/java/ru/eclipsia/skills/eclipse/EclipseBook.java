package ru.eclipsia.skills.eclipse;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Фабрика «эклипс-книг» — предметов, по ПКМ открывающих GUI выбора
 * (3 навыка ИЛИ 3 поддержки).
 *
 * <p>Книги маркируются PDC-ключом {@code eclipsia:eclipse_book} со значением
 * {@code "skill"} или {@code "support"}. Это единственный признак, по
 * которому EclipseBookListener определяет нужно ли открывать выбор и какой
 * именно. Любые отображаемые имена/лор могут меняться — поведение к ним
 * не привязано.
 */
public final class EclipseBook {

    public static final String NAMESPACE = "eclipsia";
    public static final String KEY = "eclipse_book";
    public static final String KIND_SKILL = "skill";
    public static final String KIND_SUPPORT = "support";

    private EclipseBook() {}

    /** PDC-ключ для маркировки книги. Создаётся без плагина, чтобы факт
     *  «это эклипс-книга» одинаково читался любым плагином EclipsiaXxx. */
    public static NamespacedKey key() {
        return new NamespacedKey(NAMESPACE, KEY);
    }

    /**
     * Создать книгу выбора стартового навыка.
     * Выдаётся при выборе класса (см. EclipsiaLobby.LobbyListener).
     */
    public static ItemStack createSkillBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lЭклипс Навыка");
            meta.setLore(Arrays.asList(
                    "",
                    "§7ПКМ — выбрать стартовый навык",
                    "§71 из 3: §fУдар мечом§7, §fВыстрел§7, §fОгненный шар",
                    "",
                    "§8Предмет Eclipsia"));
            meta.setCustomModelData(9001);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(key(), PersistentDataType.STRING, KIND_SKILL);
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * Создать книгу выбора эклипс-поддержки.
     * Выдаётся при убийстве босса Хранитель Врат (см. GatekeeperBoss.onDeath).
     */
    public static ItemStack createSupportBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lЭклипс Поддержки");
            meta.setLore(Arrays.asList(
                    "",
                    "§7ПКМ — выбрать поддержку и целевой навык",
                    "§71 из 3: §fМультивыстрел§7, §fВзрыв§7, §fРадиус AOE§7",
                    "",
                    "§8Награда за победу над Хранителем Врат",
                    "§8Предмет Eclipsia"));
            meta.setCustomModelData(9002);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(key(), PersistentDataType.STRING, KIND_SUPPORT);
            book.setItemMeta(meta);
        }
        return book;
    }

    /** Является ли предмет эклипс-книгой (любого вида). */
    public static boolean isEclipseBook(ItemStack item) {
        return getKind(item) != null;
    }

    /** Получить вид книги ({@link #KIND_SKILL}/{@link #KIND_SUPPORT}) или null. */
    public static String getKind(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }
}
