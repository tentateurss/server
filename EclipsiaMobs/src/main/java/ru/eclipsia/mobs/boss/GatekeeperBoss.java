package ru.eclipsia.mobs.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.eclipsia.mobs.EclipsiaMobs;
import ru.eclipsia.mobs.experience.ExperienceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранитель Врат - первый босс на Берегу
 */
public class GatekeeperBoss {
    
    private final EclipsiaMobs plugin;
    private IronGolem boss;
    private Location spawnLocation;
    private boolean isActive = false;
    private BukkitRunnable abilityTask;
    
    // Фазы босса
    private static final double PHASE_2_HP = 0.66; // 66% HP
    private static final double PHASE_3_HP = 0.33; // 33% HP
    private int currentPhase = 1;
    
    public GatekeeperBoss(EclipsiaMobs plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Заспавнить босса
     */
    public void spawn(Location location) {
        if (isActive) {
            plugin.getLogger().warning("Хранитель Врат уже активен!");
            return;
        }
        
        this.spawnLocation = location;
        
        // Создаем железного голема как основу
        boss = (IronGolem) location.getWorld().spawnEntity(location, EntityType.IRON_GOLEM);
        
        // Настраиваем босса
        boss.setCustomName("§c§lХранитель Врат");
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setAI(true);
        
        // Устанавливаем характеристики
        boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(500.0);
        boss.setHealth(500.0);
        boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(15.0);
        boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3);
        
        // Добавляем эффекты
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        
        // Метаданные для идентификации
        boss.setMetadata("eclipsia_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, "gatekeeper"));
        boss.setMetadata("eclipsia_boss_level", new org.bukkit.metadata.FixedMetadataValue(plugin, 5));
        
        isActive = true;
        currentPhase = 1;
        
        // Запускаем способности
        startAbilities();
        
        // Объявление о спавне
        Bukkit.broadcastMessage("§c§l[!] Хранитель Врат пробудился на Берегу!");
        
        plugin.getLogger().info("Хранитель Врат заспавнен в " + location);
    }
    
    /**
     * Запустить способности босса
     */
    private void startAbilities() {
        abilityTask = new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (!isActive || boss == null || boss.isDead()) {
                    cancel();
                    return;
                }
                
                ticks++;
                
                // Проверяем фазу
                checkPhase();
                
                // Способности в зависимости от фазы
                switch (currentPhase) {
                    case 1:
                        // Фаза 1: Удар по земле каждые 10 секунд
                        if (ticks % 200 == 0) {
                            groundSlam();
                        }
                        break;
                        
                    case 2:
                        // Фаза 2: Удар по земле + призыв миньонов
                        if (ticks % 200 == 0) {
                            groundSlam();
                        }
                        if (ticks % 400 == 0) {
                            summonMinions();
                        }
                        break;
                        
                    case 3:
                        // Фаза 3: Все способности + берсерк
                        if (ticks % 150 == 0) {
                            groundSlam();
                        }
                        if (ticks % 300 == 0) {
                            summonMinions();
                        }
                        break;
                }
            }
        };
        
        abilityTask.runTaskTimer(plugin, 20L, 20L);
    }
    
    /**
     * Проверить фазу босса
     */
    private void checkPhase() {
        double hpPercent = boss.getHealth() / boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        
        if (currentPhase == 1 && hpPercent <= PHASE_2_HP) {
            enterPhase2();
        } else if (currentPhase == 2 && hpPercent <= PHASE_3_HP) {
            enterPhase3();
        }
    }
    
    /**
     * Войти во вторую фазу
     */
    private void enterPhase2() {
        currentPhase = 2;
        boss.setCustomName("§c§lХранитель Врат §7[Фаза 2]");
        
        // Увеличиваем урон
        boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(20.0);
        
        Bukkit.broadcastMessage("§c§l[!] Хранитель Врат разъярен!");
        
        // Эффект перехода
        boss.getWorld().strikeLightningEffect(boss.getLocation());
    }
    
    /**
     * Войти в третью фазу
     */
    private void enterPhase3() {
        currentPhase = 3;
        boss.setCustomName("§4§lХранитель Врат §7[Берсерк]");
        
        // Максимальный урон и скорость
        boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(25.0);
        boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.4);
        
        // Добавляем регенерацию
        boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1, false, false));
        
        Bukkit.broadcastMessage("§4§l[!] Хранитель Врат вошел в состояние берсерка!");
        
        // Эффект перехода
        boss.getWorld().strikeLightningEffect(boss.getLocation());
        boss.getWorld().createExplosion(boss.getLocation(), 0, false, false);
    }
    
    /**
     * Способность: Удар по земле
     */
    private void groundSlam() {
        Location loc = boss.getLocation();
        
        // Эффекты
        loc.getWorld().createExplosion(loc, 0, false, false);
        
        // Урон и отбрасывание игроков в радиусе
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= 8) {
                player.damage(10.0, boss);
                player.setVelocity(player.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5).setY(0.5));
                player.sendMessage("§c§lХранитель Врат использовал Удар по земле!");
            }
        }
    }
    
    /**
     * Способность: Призыв миньонов
     */
    private void summonMinions() {
        Location loc = boss.getLocation();
        
        // Спавним 3 зомби вокруг босса
        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2 / 3) * i;
            double x = loc.getX() + Math.cos(angle) * 3;
            double z = loc.getZ() + Math.sin(angle) * 3;
            Location spawnLoc = new Location(loc.getWorld(), x, loc.getY(), z);
            
            LivingEntity minion = (LivingEntity) loc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            minion.setCustomName("§7Страж Врат");
            minion.setCustomNameVisible(true);
            minion.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(50.0);
            minion.setHealth(50.0);
            minion.setMetadata("eclipsia_minion", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
        
        Bukkit.broadcastMessage("§c§lХранитель Врат призвал стражей!");
    }
    
    /**
     * Обработка смерти босса
     */
    public void onDeath(Player killer) {
        if (!isActive) return;
        
        isActive = false;
        
        if (abilityTask != null) {
            abilityTask.cancel();
        }
        
        // Награды
        if (killer != null) {
            // Опыт — через ExperienceManager, чтобы корректно обрабатывался
            // level-up и начисление очков статов/перков. EclipsiaAPI.addExperience
            // только пишет в поле experience без level-up.
            ExperienceManager.getInstance().addExperience(killer, 500);
            
            // Орбы
            plugin.getCoreAPI().addOrbs(killer, 100);
            
            // Предметы
            killer.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
            killer.getInventory().addItem(new ItemStack(Material.EMERALD, 5));

            // Дроп книги выбора эклипс-поддержки. Создаётся через единый
            // фабричный метод EclipseBook.createSupportBook() — он же
            // используется EclipseBookListener для распознавания книги
            // (PDC-ключ eclipsia:eclipse_book = "support"). Не дублируем
            // здесь NBT, чтобы изменения формата делались в одном месте.
            try {
                ItemStack book =
                        ru.eclipsia.skills.eclipse.EclipseBook.createSupportBook();
                killer.getInventory().addItem(book);
                killer.sendMessage("§d§l+ Книга выбора §fЭклипс Поддержки");
                killer.sendMessage("§7ПКМ по книге → откроется меню выбора поддержки.");
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось выдать книгу поддержки: " + t.getMessage());
            }

            killer.sendMessage("§a§lВы победили Хранителя Врат!");
            killer.sendMessage("§e+500 опыта, +100 орбов");
        }
        
        // Объявление
        Bukkit.broadcastMessage("§a§l[!] Хранитель Врат повержен! Путь с Берега открыт!");
        
        // Убираем границу WorldGuard
        removeBeachBorder();
        
        plugin.getLogger().info("Хранитель Врат побежден игроком: " + (killer != null ? killer.getName() : "неизвестно"));
    }
    
    /**
     * Убрать границу Берега
     */
    private void removeBeachBorder() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag beach_border entry allow -w beach");
            Bukkit.broadcastMessage("§aГраница Берега снята! Вы можете исследовать мир.");
        });
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public IronGolem getBoss() {
        return boss;
    }

    /**
     * Полный сброс состояния босса без награды (для admin reset).
     * Останавливает шедулер способностей, убирает физическую сущность,
     * сбрасывает {@code isActive}.
     */
    public void forceCleanup() {
        isActive = false;
        currentPhase = 1;
        if (abilityTask != null) {
            try { abilityTask.cancel(); } catch (Throwable ignored) {}
            abilityTask = null;
        }
        if (boss != null && !boss.isDead()) {
            try { boss.remove(); } catch (Throwable ignored) {}
        }
        boss = null;
    }
}
