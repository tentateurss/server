package ru.eclipsia.skills.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.skills.eclipse.EclipseItem;
import ru.eclipsia.skills.manager.SkillManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI управления навыками и эклипсами
 * 5 навыков + 10 слотов поддержек (по 2 на каждый навык)
 */
public class SkillsGUI {
    
    private static final String TITLE = "§6Навыки";
    private static final int SIZE = 54; // 6 рядов
    
    // Слоты навыков (вертикально по левой стороне)
    private static final int[] SKILL_SLOTS = {10, 19, 28, 37, 46};
    
    // Слоты поддержек (по 2 справа от каждого навыка)
    private static final int[][] SUPPORT_SLOTS = {
        {12, 13},  // Поддержки для навыка 0
        {21, 22},  // Поддержки для навыка 1
        {30, 31},  // Поддержки для навыка 2
        {39, 40},  // Поддержки для навыка 3
        {48, 49}   // Поддержки для навыка 4
    };
    
    private final SkillManager skillManager;
    
    public SkillsGUI(SkillManager skillManager) {
        this.skillManager = skillManager;
    }
    
    /**
     * Открыть GUI для игрока
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        
        // Заполняем фон
        fillBackground(inv);
        
        // Загружаем навыки игрока
        SkillManager.PlayerSkills skills = skillManager.getPlayerSkills(player);
        
        // Отображаем навыки
        for (int i = 0; i < 5; i++) {
            EclipseItem skill = skills.skillSlots[i];
            if (skill != null) {
                inv.setItem(SKILL_SLOTS[i], skill.toItemStack());
            } else {
                inv.setItem(SKILL_SLOTS[i], createSkillPlaceholder(i));
            }
            
            // Отображаем поддержки
            for (int j = 0; j < 2; j++) {
                EclipseItem support = skills.supportSlots[i][j];
                if (support != null) {
                    inv.setItem(SUPPORT_SLOTS[i][j], support.toItemStack());
                } else {
                    inv.setItem(SUPPORT_SLOTS[i][j], createSupportPlaceholder(i, j));
                }
            }
        }
        
        player.openInventory(inv);
    }
    
    /**
     * Заполнить фон
     */
    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < SIZE; i++) {
            // Пропускаем слоты навыков и поддержек
            if (isSkillSlot(i) || isSupportSlot(i)) {
                continue;
            }
            inv.setItem(i, glass);
        }
    }
    
    /**
     * Создать заглушку для пустого слота навыка
     */
    private ItemStack createSkillPlaceholder(int slot) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aСлот навыка #" + (slot + 1));
            List<String> lore = new ArrayList<>();
            lore.add("§8Пусто");
            lore.add("");
            lore.add("§7Перетащите эклипс-навык сюда");
            lore.add("§7или используйте ПКМ с навыком");
            lore.add("§7в руке для вставки");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Создать заглушку для пустого слота поддержки
     */
    private ItemStack createSupportPlaceholder(int skillSlot, int supportSlot) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dПоддержка #" + (supportSlot + 1));
            List<String> lore = new ArrayList<>();
            lore.add("§8Пусто");
            lore.add("");
            lore.add("§7Перетащите эклипс-поддержку сюда");
            lore.add("§7для усиления навыка");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Проверить является ли слот слотом навыка
     */
    public static boolean isSkillSlot(int slot) {
        for (int skillSlot : SKILL_SLOTS) {
            if (skillSlot == slot) return true;
        }
        return false;
    }
    
    /**
     * Проверить является ли слот слотом поддержки
     */
    public static boolean isSupportSlot(int slot) {
        for (int[] supportPair : SUPPORT_SLOTS) {
            for (int supportSlot : supportPair) {
                if (supportSlot == slot) return true;
            }
        }
        return false;
    }
    
    /**
     * Получить индекс навыка по слоту в GUI
     */
    public static int getSkillIndex(int slot) {
        for (int i = 0; i < SKILL_SLOTS.length; i++) {
            if (SKILL_SLOTS[i] == slot) return i;
        }
        return -1;
    }
    
    /**
     * Получить индексы (навык, поддержка) по слоту в GUI
     */
    public static int[] getSupportIndex(int slot) {
        for (int i = 0; i < SUPPORT_SLOTS.length; i++) {
            for (int j = 0; j < SUPPORT_SLOTS[i].length; j++) {
                if (SUPPORT_SLOTS[i][j] == slot) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }
}
