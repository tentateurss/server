package ru.eclipsia.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.core.classes.ClassManager;
import ru.eclipsia.core.classes.PlayerClass;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * Команда /class - выбор и управление классом
 */
public class ClassCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        openClassSelectionGUI(player);
        return true;
    }
    
    private void openClassSelectionGUI(Player player) {
        PlayerData data = DataManager.getInstance().getCachedPlayer(player.getUniqueId());
        
        if (data == null) {
            player.sendMessage("§cОшибка загрузки данных. Попробуйте перезайти.");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, "§8Выбор класса");
        
        ClassManager classManager = ClassManager.getInstance();
        int slot = 11;
        
        for (String classId : classManager.getClassIds()) {
            PlayerClass playerClass = classManager.getClass(classId);
            
            ItemStack item = createClassItem(playerClass, data.getClassName());
            gui.setItem(slot, item);
            slot += 2;
        }
        
        player.openInventory(gui);
    }
    
    private ItemStack createClassItem(PlayerClass playerClass, String currentClass) {
        Material material = Material.getMaterial(playerClass.getIcon());
        if (material == null) material = Material.STONE;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(playerClass.getDisplayName());

        // CustomModelData для подмены текстуры через ресурс-пак.
        // Должно совпадать с overrides в resourcepack/assets/minecraft/models/item/{material}.json:
        //   warrior → 200 (iron_sword.json),
        //   archer  → 201 (bow.json),
        //   mage    → 202 (blaze_rod.json).
        // Без ресурс-пака CMD просто игнорируется и виден ванильный материал.
        switch (playerClass.getId()) {
            case "warrior" -> meta.setCustomModelData(200);
            case "archer"  -> meta.setCustomModelData(201);
            case "mage"    -> meta.setCustomModelData(202);
        }
        
        List<String> lore = new ArrayList<>(playerClass.getDescription());
        lore.add("");
        lore.add("§7Базовые характеристики:");
        
        playerClass.getBaseStats().forEach((stat, value) -> {
            String statName = switch (stat) {
                case "strength" -> "Сила";
                case "dexterity" -> "Ловкость";
                case "intelligence" -> "Интеллект";
                default -> stat;
            };
            lore.add("§8• §f" + statName + ": §a" + value);
        });
        
        lore.add("");
        lore.add("§7Здоровье: §c" + playerClass.getStartingHealth() + " HP");
        
        if (playerClass.getId().equals(currentClass)) {
            lore.add("");
            lore.add("§a§l✓ ВЫБРАН");
        } else {
            lore.add("");
            lore.add("§eНажмите, чтобы выбрать");
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
}
