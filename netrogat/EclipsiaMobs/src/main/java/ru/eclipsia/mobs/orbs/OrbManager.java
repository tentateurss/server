package ru.eclipsia.mobs.orbs;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.mobs.mob.CustomMob;

import java.util.Random;

/**
 * Менеджер орбов (валюты)
 * Теперь орбы хранятся в PlayerData, а не как физические предметы
 */
public class OrbManager {
    
    private static OrbManager instance;
    
    private final Plugin plugin;
    private final Random random;
    private final EclipsiaAPI coreAPI;
    
    private int dropChance;
    private int minAmount;
    private int maxAmount;
    private int levelBonus;
    
    private OrbManager(Plugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.coreAPI = EclipsiaAPI.getInstance();
        loadConfig();
    }
    
    public static void initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("OrbManager уже инициализирован!");
        }
        instance = new OrbManager(plugin);
    }
    
    public static OrbManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("OrbManager не инициализирован!");
        }
        return instance;
    }
    
    private void loadConfig() {
        if (plugin == null) return;
        dropChance = plugin.getConfig().getInt("orbs.drop-chance", 100);
        minAmount = plugin.getConfig().getInt("orbs.min-amount", 1);
        maxAmount = plugin.getConfig().getInt("orbs.max-amount", 5);
        levelBonus = plugin.getConfig().getInt("orbs.level-bonus", 10);
    }
    
    /**
     * Дроп орбов с моба (теперь напрямую в PlayerData)
     */
    public void dropOrbs(Player killer, Location location, CustomMob mob) {
        CustomMob.DropConfig drops = mob.getDrops();
        
        // Проверяем шанс дропа
        if (random.nextInt(100) >= drops.getOrbChance()) {
            return;
        }
        
        // Рассчитываем количество орбов
        int amount = random.nextInt(drops.getMaxOrbs() - drops.getMinOrbs() + 1) + drops.getMinOrbs();
        
        // Бонус за уровень моба
        int bonus = (int) (amount * (mob.getLevel() * levelBonus / 100.0));
        amount += bonus;
        
        // Добавляем орбы напрямую в PlayerData
        coreAPI.addOrbs(killer, amount);
        
        // Сообщение игроку
        killer.sendMessage("§6+ " + amount + " орбов §7(Всего: §6" + coreAPI.getPlayerOrbs(killer) + "§7)");
    }
    
    /**
     * Дать орбы игроку напрямую
     */
    public void giveOrbs(Player player, int amount) {
        coreAPI.addOrbs(player, amount);
        player.sendMessage("§aВы получили §6" + amount + " орбов §7(Всего: §6" + coreAPI.getPlayerOrbs(player) + "§7)");
    }
    
    /**
     * Забрать орбы у игрока
     */
    public boolean takeOrbs(Player player, int amount) {
        if (coreAPI.removeOrbs(player, amount)) {
            player.sendMessage("§cСписано §6" + amount + " орбов §7(Осталось: §6" + coreAPI.getPlayerOrbs(player) + "§7)");
            return true;
        } else {
            player.sendMessage("§cНедостаточно орбов! §7(Нужно: §6" + amount + "§7, У вас: §6" + coreAPI.getPlayerOrbs(player) + "§7)");
            return false;
        }
    }
    
    /**
     * Получить количество орбов игрока
     */
    public int getOrbs(Player player) {
        return coreAPI.getPlayerOrbs(player);
    }
}
