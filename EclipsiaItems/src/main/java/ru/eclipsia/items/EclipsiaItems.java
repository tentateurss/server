package ru.eclipsia.items;

import org.bukkit.plugin.java.JavaPlugin;
import ru.eclipsia.items.affix.AffixManager;
import ru.eclipsia.items.commands.EquipmentCommand;
import ru.eclipsia.items.commands.ItemAdminCommand;
import ru.eclipsia.items.commands.ItemCommand;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.generator.ItemGenerator;
import ru.eclipsia.items.gui.EquipmentGUI;
import ru.eclipsia.items.hud.PlayerHUDManager;
import ru.eclipsia.items.item.ItemManager;
import ru.eclipsia.items.listeners.EquipmentGUIListener;
import ru.eclipsia.items.listeners.ItemDropListener;
import ru.eclipsia.items.listeners.ItemEquipListener;
import ru.eclipsia.items.rarity.RarityManager;

/**
 * Главный класс плагина EclipsiaItems
 */
public class EclipsiaItems extends JavaPlugin {
    
    private static EclipsiaItems instance;
    
    private AffixManager affixManager;
    private ItemManager itemManager;
    private RarityManager rarityManager;
    private ItemGenerator itemGenerator;
    private EquipmentManager equipmentManager;
    private EquipmentGUI equipmentGUI;
    private PlayerHUDManager hudManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Проверка зависимости EclipsiaCore
        if (getServer().getPluginManager().getPlugin("EclipsiaCore") == null) {
            getLogger().severe("EclipsiaCore не найден! Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Сохранение конфигов по умолчанию
        saveDefaultConfig();
        saveResource("affixes.yml", false);
        saveResource("items.yml", false);
        
        // Инициализация менеджеров
        getLogger().info("Инициализация менеджеров...");
        
        affixManager = new AffixManager(this);
        affixManager.loadAffixes();
        
        itemManager = new ItemManager(this);
        itemManager.loadItems();
        
        rarityManager = new RarityManager(this);
        
        itemGenerator = new ItemGenerator(this, affixManager, itemManager, rarityManager);
        
        equipmentManager = new EquipmentManager(this);
        
        equipmentGUI = new EquipmentGUI(equipmentManager);
        
        hudManager = new PlayerHUDManager(this);
        hudManager.start();
        
        // Регистрация команд
        getCommand("item").setExecutor(new ItemCommand(this, itemGenerator));
        getCommand("itemadmin").setExecutor(new ItemAdminCommand(this));
        getCommand("equipment").setExecutor(new EquipmentCommand(equipmentGUI));
        getCommand("fixmenu").setExecutor(new ru.eclipsia.items.commands.FixMenuCommand());
        getCommand("testsword").setExecutor(new ru.eclipsia.items.commands.TestSwordCommand());
        
        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new ItemDropListener(this, itemGenerator), this);
        getServer().getPluginManager().registerEvents(new ItemEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new EquipmentGUIListener(this, equipmentManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.PlayerEquipmentListener(equipmentManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.ItemDurabilityListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.HandItemListener(equipmentManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.MenuBookListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.PermanentArrowListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.MainMenuGUIListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.CurrencyGUIListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.BlockVanillaArmorListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.ArmorSyncListener(equipmentManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.InfiniteBowListener(), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.RegenerationListener(this, equipmentManager), this);
        getServer().getPluginManager().registerEvents(new ru.eclipsia.items.listeners.QuickEquipListener(equipmentManager), this);
        
        getLogger().info("EclipsiaItems успешно загружен!");
        getLogger().info("Загружено аффиксов: " + affixManager.getAffixCount());
        getLogger().info("Загружено базовых предметов: " + itemManager.getItemCount());
    }
    
    @Override
    public void onDisable() {
        if (hudManager != null) {
            hudManager.stop();
        }
        getLogger().info("EclipsiaItems отключен.");
    }
    
    public static EclipsiaItems getInstance() {
        return instance;
    }
    
    public AffixManager getAffixManager() {
        return affixManager;
    }
    
    public ItemManager getItemManager() {
        return itemManager;
    }
    
    public RarityManager getRarityManager() {
        return rarityManager;
    }
    
    public ItemGenerator getItemGenerator() {
        return itemGenerator;
    }
    
    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }
    
    public PlayerHUDManager getHudManager() {
        return hudManager;
    }
    
    /**
     * Перезагрузка конфигурации
     */
    public void reloadConfiguration() {
        reloadConfig();
        affixManager.loadAffixes();
        itemManager.loadItems();
        getLogger().info("Конфигурация перезагружена!");
    }
}
