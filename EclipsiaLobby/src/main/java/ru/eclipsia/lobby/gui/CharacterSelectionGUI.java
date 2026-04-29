package ru.eclipsia.lobby.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.lobby.EclipsiaLobby;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI выбора персонажа
 */
public class CharacterSelectionGUI {
    
    private static final String TITLE = "§6Выбор персонажа";
    private static final int SIZE = 27;
    
    private static final int SLOT_0 = 11;
    private static final int SLOT_1 = 13;
    private static final int SLOT_2 = 15;
    
    private final EclipsiaLobby plugin;
    
    public CharacterSelectionGUI(EclipsiaLobby plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Открыть GUI для игрока
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Получаем профили игрока
        List<PlayerProfile> profiles = plugin.getAPI().getProfiles(player);
        
        // Слот 0
        if (profiles.size() > 0 && profiles.get(0) != null) {
            inv.setItem(SLOT_0, createProfileItem(player, profiles.get(0)));
        } else {
            inv.setItem(SLOT_0, createEmptySlotItem(0));
        }
        
        // Слот 1
        if (profiles.size() > 1 && profiles.get(1) != null) {
            inv.setItem(SLOT_1, createProfileItem(player, profiles.get(1)));
        } else {
            inv.setItem(SLOT_1, createEmptySlotItem(1));
        }
        
        // Слот 2
        if (profiles.size() > 2 && profiles.get(2) != null) {
            inv.setItem(SLOT_2, createProfileItem(player, profiles.get(2)));
        } else {
            inv.setItem(SLOT_2, createEmptySlotItem(2));
        }
        
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
            if (i != SLOT_0 && i != SLOT_1 && i != SLOT_2) {
                inv.setItem(i, glass);
            }
        }
    }
    
    /**
     * Создать иконку профиля
     */
    private ItemStack createProfileItem(Player player, PlayerProfile profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            meta.setOwningPlayer(player);
            
            String className = getClassDisplayName(profile.getClassName());
            meta.setDisplayName("§6" + className + " §7Ур." + profile.getLevel());
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Сила: §c" + profile.getStat("strength"));
            lore.add("§7Ловкость: §a" + profile.getStat("dexterity"));
            lore.add("§7Интеллект: §9" + profile.getStat("intelligence"));
            lore.add("");
            lore.add("§7Орбы: §6" + profile.getOrbs());
            lore.add("");
            lore.add("§fНажмите для входа");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Создать иконку пустого слота
     */
    private ItemStack createEmptySlotItem(int slot) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§7Пустая ячейка");
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Слот: §e" + slot);
            lore.add("");
            lore.add("§fНажмите чтобы создать");
            lore.add("§fнового персонажа");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Получить отображаемое имя класса
     */
    private String getClassDisplayName(String className) {
        return switch (className.toLowerCase()) {
            case "warrior" -> "Воин";
            case "archer" -> "Лучник";
            case "mage" -> "Маг";
            default -> className;
        };
    }
    
    /**
     * Получить слот по индексу в GUI
     */
    public static Integer getSlotByIndex(int index) {
        return switch (index) {
            case SLOT_0 -> 0;
            case SLOT_1 -> 1;
            case SLOT_2 -> 2;
            default -> null;
        };
    }
    
    /**
     * Проверить является ли индекс слотом персонажа
     */
    public static boolean isCharacterSlot(int index) {
        return index == SLOT_0 || index == SLOT_1 || index == SLOT_2;
    }
}
