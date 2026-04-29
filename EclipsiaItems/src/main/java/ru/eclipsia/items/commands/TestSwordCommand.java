package ru.eclipsia.items.commands;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

/**
 * Команда для выдачи админского меча для тестов
 */
public class TestSwordCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }
        
        if (!sender.hasPermission("eclipsia.admin")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Создаем меч
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        
        // Переливающееся название (градиент от красного к фиолетовому)
        meta.setDisplayName("§4§lЛ§c§lе§6§lг§e§lе§a§lн§b§lд§9§lа§5§lр§d§lн§5§lы§9§lй §b§lЗ§3§lа§1§lт§9§lм§5§lи§d§lт§5§lе§d§lл§5§lь");
        
        // Лор
        meta.setLore(Arrays.asList(
            "",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§7Легендарное оружие для тестирования",
            "§7Уничтожает любого врага одним ударом",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "",
            "§c⚔ Урон: §f999999",
            "§a✦ Скорость атаки: §f100",
            "",
            "§6§lЛЕГЕНДАРНЫЙ ПРЕДМЕТ",
            "§8Только для администраторов"
        ));
        
        // Огромный урон
        AttributeModifier damageModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic.attack_damage",
            999999,
            AttributeModifier.Operation.ADD_NUMBER
        );
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageModifier);
        
        // Скорость атаки
        AttributeModifier speedModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic.attack_speed",
            100,
            AttributeModifier.Operation.ADD_NUMBER
        );
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedModifier);
        
        // Неразрушимый
        meta.setUnbreakable(true);
        
        // Скрываем все флаги
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        
        sword.setItemMeta(meta);
        
        // Выдаем игроку
        player.getInventory().addItem(sword);
        player.sendMessage("§6§l✦ §eВы получили §4§lЛегендарный Затмитель§e!");
        player.sendMessage("§7Используйте с осторожностью - убивает с одного удара!");
        
        return true;
    }
}
