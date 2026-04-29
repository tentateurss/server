package ru.eclipsia.lobby.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI создания персонажа
 */
public class CharacterCreationGUI {
    
    private static final String TITLE = "§6Создание персонажа";
    private static final int SIZE = 27;
    
    private static final int SLOT_WARRIOR = 11;
    private static final int SLOT_ARCHER = 13;
    private static final int SLOT_MAGE = 15;
    
    private final int targetSlot;
    
    public CharacterCreationGUI(int targetSlot) {
        this.targetSlot = targetSlot;
    }
    
    /**
     * Открыть GUI для игрока
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Добавляем кнопки выбора класса.
        // CustomModelData 200/201/202 → ресурс-пак подменяет текстуры
        // на class_warrior / class_archer / class_mage. Без ресурс-пака
        // отображается ванильный материал.
        inv.setItem(SLOT_WARRIOR, createClassItem(
            Material.IRON_SWORD, 200,
            "§cВоин",
            "§7Мастер ближнего боя",
            "",
            "§8• §7Высокая сила",
            "§8• §7Большой запас здоровья",
            "§8• §7Тяжелая броня",
            "",
            "§eНажмите для выбора"
        ));

        inv.setItem(SLOT_ARCHER, createClassItem(
            Material.BOW, 201,
            "§aЛучник",
            "§7Мастер дальнего боя",
            "",
            "§8• §7Высокая ловкость",
            "§8• §7Быстрые атаки",
            "§8• §7Уклонение от ударов",
            "",
            "§eНажмите для выбора"
        ));

        inv.setItem(SLOT_MAGE, createClassItem(
            Material.BLAZE_ROD, 202,
            "§9Маг",
            "§7Мастер магии",
            "",
            "§8• §7Высокий интеллект",
            "§8• §7Мощные заклинания",
            "§8• §7Большой запас маны",
            "",
            "§eНажмите для выбора"
        ));
        
        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < SIZE; i++) {
            if (i != SLOT_WARRIOR && i != SLOT_ARCHER && i != SLOT_MAGE) {
                inv.setItem(i, glass);
            }
        }
    }
    
    /**
     * Создать иконку класса с CustomModelData для подмены текстуры
     * через ресурс-пак.
     *
     * @param material      базовый материал (IRON_SWORD/BOW/BLAZE_ROD)
     * @param customModelData CMD для override в ресурс-паке (200/201/202),
     *                        или 0 чтобы не выставлять
     */
    private ItemStack createClassItem(Material material, int customModelData, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);

            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            item.setItemMeta(meta);
        }

        return item;
    }
    
    /**
     * Получить ID класса по индексу в GUI
     */
    public static String getClassByIndex(int index) {
        return switch (index) {
            case SLOT_WARRIOR -> "warrior";
            case SLOT_ARCHER -> "archer";
            case SLOT_MAGE -> "mage";
            default -> null;
        };
    }
    
    /**
     * Проверить является ли индекс слотом класса
     */
    public static boolean isClassSlot(int index) {
        return index == SLOT_WARRIOR || index == SLOT_ARCHER || index == SLOT_MAGE;
    }
    
    public int getTargetSlot() {
        return targetSlot;
    }
}
