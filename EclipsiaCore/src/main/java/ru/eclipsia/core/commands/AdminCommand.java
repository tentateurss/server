package ru.eclipsia.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.eclipsia.core.data.DataManager;
import ru.eclipsia.core.permissions.PermissionManager;

/**
 * Команда /admin - административные команды
 */
public class AdminCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // Логируем кто вызвал команду
            Bukkit.getLogger().info("[AdminCommand] Sender: " + sender.getName() + ", Type: " + sender.getClass().getSimpleName());
            
            if (!PermissionManager.getInstance().checkAdmin(sender)) {
                return true;
            }
            
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "reload" -> handleReload(sender);
                case "info" -> handleInfo(sender);
                case "setlevel" -> handleSetLevel(sender, args);
                case "addstats" -> handleAddStats(sender, args);
                case "addorbs" -> handleAddOrbs(sender, args);
                case "resetplayer" -> handleResetPlayer(sender, args);
                default -> sendHelp(sender);
            }
            
            return true;
        } catch (Exception e) {
            sender.sendMessage("§c✗ Ошибка выполнения команды: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== ECLIPSIA ADMIN ===");
        sender.sendMessage("§e/admin reload §7- Перезагрузить конфиги");
        sender.sendMessage("§e/admin info §7- Информация о сервере");
        sender.sendMessage("§e/admin setlevel <игрок> <уровень> §7- Установить уровень");
        sender.sendMessage("§e/admin addstats <игрок> <очки> §7- Добавить очки статов");
        sender.sendMessage("§e/admin addorbs <игрок> <количество> §7- Добавить орбы");
        sender.sendMessage("§e/admin resetplayer <игрок> §7- Сбросить данные игрока");
    }
    
    private void handleReload(CommandSender sender) {
        sender.sendMessage("§eПерезагрузка конфигурации...");
        
        try {
            // Перезагрузка конфига плагина
            Bukkit.getPluginManager().getPlugin("EclipsiaCore").reloadConfig();
            
            // Перезагрузка менеджеров
            PermissionManager.getInstance().reload();
            
            sender.sendMessage("§a✓ Конфигурация перезагружена!");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Ошибка перезагрузки: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleInfo(CommandSender sender) {
        var cacheStats = DataManager.getInstance().getCacheStats();
        
        sender.sendMessage("§6§l=== ECLIPSIA INFO ===");
        sender.sendMessage("§7Версия: §eEclipsiaCore v0.1.0");
        sender.sendMessage("§7Хранилище: §e" + DataManager.getInstance().getStorage().getStorageType());
        sender.sendMessage("§7Игроков в кэше: §e" + cacheStats.totalCached());
        sender.sendMessage("§7Онлайн игроков: §e" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Администраторов: §e" + PermissionManager.getInstance().getAdmins().size());
    }
    
    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /admin setlevel <игрок> <уровень>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        try {
            int level = Integer.parseInt(args[2]);
            if (level < 1 || level > 100) {
                sender.sendMessage("§cУровень должен быть от 1 до 100");
                return;
            }
            
            var data = DataManager.getInstance().getCachedPlayer(target.getUniqueId());
            if (data == null) {
                sender.sendMessage("§cДанные игрока не загружены");
                return;
            }
            
            // ИСПРАВЛЕНО: Рассчитываем очки статов за разницу уровней
            int levelDiff = level - data.getLevel();
            int statPointsPerLevel = 1; // 1 очко за уровень
            int newStatPoints = data.getFreeStatPoints() + (levelDiff * statPointsPerLevel);
            
            var updatedData = data.toBuilder()
                    .level(level)
                    .freeStatPoints(Math.max(0, newStatPoints))
                    .build();
            
            DataManager.getInstance().savePlayer(updatedData);
            
            sender.sendMessage("§a✓ Уровень игрока " + target.getName() + " установлен на " + level);
            if (levelDiff > 0) {
                sender.sendMessage("§7Добавлено очков статов: §a+" + (levelDiff * statPointsPerLevel));
                target.sendMessage("§eАдминистратор установил ваш уровень на " + level);
                target.sendMessage("§aВы получили §6" + (levelDiff * statPointsPerLevel) + " §aочков характеристик!");
            } else {
                target.sendMessage("§eАдминистратор установил ваш уровень на " + level);
            }
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат уровня: " + args[2]);
        }
    }
    
    private void handleAddStats(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /admin addstats <игрок> <очки>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        try {
            int points = Integer.parseInt(args[2]);
            if (points < 1 || points > 100) {
                sender.sendMessage("§cКоличество очков должно быть от 1 до 100");
                return;
            }
            
            var data = DataManager.getInstance().getCachedPlayer(target.getUniqueId());
            if (data == null) {
                sender.sendMessage("§cДанные игрока не загружены");
                return;
            }
            
            var updatedData = data.toBuilder()
                    .freeStatPoints(data.getFreeStatPoints() + points)
                    .build();
            
            DataManager.getInstance().savePlayer(updatedData);
            
            sender.sendMessage("§a✓ Игроку " + target.getName() + " добавлено " + points + " очков статов");
            target.sendMessage("§eАдминистратор добавил вам §6" + points + " §eочков характеристик!");
            target.sendMessage("§7Используйте §f/stats add <стат> §7для распределения");
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат количества: " + args[2]);
        }
    }
    
    private void handleAddOrbs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /admin addorbs <игрок> <количество>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        try {
            int orbs = Integer.parseInt(args[2]);
            if (orbs < 1) {
                sender.sendMessage("§cКоличество орбов должно быть больше 0");
                return;
            }
            
            var data = DataManager.getInstance().getCachedPlayer(target.getUniqueId());
            if (data == null) {
                sender.sendMessage("§cДанные игрока не загружены");
                return;
            }
            
            var updatedData = data.toBuilder()
                    .orbs(data.getOrbs() + orbs)
                    .build();
            
            DataManager.getInstance().savePlayer(updatedData);
            
            sender.sendMessage("§a✓ Игроку " + target.getName() + " добавлено " + orbs + " орбов");
            target.sendMessage("§eАдминистратор добавил вам §6" + orbs + " §eорбов!");
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат количества: " + args[2]);
        }
    }
    
    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /admin resetplayer <игрок>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }
        
        // Создаем новые данные игрока
        var newData = ru.eclipsia.core.data.PlayerData.createNew(target.getUniqueId());
        
        // Сохраняем
        DataManager.getInstance().savePlayer(newData);
        
        // ПОЛНАЯ очистка инвентаря: основная сетка + хотбар + броня + оффхенд.
        // Без этого старые иконки навыков и кастомные предметы могут пережить ресет.
        target.getInventory().clear();
        target.getInventory().setHelmet(null);
        target.getInventory().setChestplate(null);
        target.getInventory().setLeggings(null);
        target.getInventory().setBoots(null);
        target.getInventory().setItemInOffHand(null);
        target.setItemOnCursor(null);
        // Закрыть открытое окно (например, EquipmentGUI с курсором в руках).
        target.closeInventory();

        // Сначала чистим AttributeModifier'ы от StatsBonusApplier — иначе
        // если игрок до ресета натыкал /teststats max, его HP/attack_damage
        // останутся «прокачанными», и переход на свежий профиль будет
        // выглядеть как «ресет не работает».
        try {
            ru.eclipsia.core.stats.StatsBonusApplier.removeAllBonuses(target);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[AdminCommand] removeAllBonuses failed: " + t.getMessage());
        }
        // Заодно вычистим PDC, в который StatsBonusApplier пишет evasion и
        // spell_damage_bonus — они читаются listener'ами при следующих ударах.
        try {
            org.bukkit.plugin.Plugin core = Bukkit.getPluginManager().getPlugin("EclipsiaCore");
            if (core != null) {
                target.getPersistentDataContainer().remove(
                        new org.bukkit.NamespacedKey(core, "evasion_chance"));
                target.getPersistentDataContainer().remove(
                        new org.bukkit.NamespacedKey(core, "spell_damage_bonus"));
            }
        } catch (Throwable ignored) {}

        // Сбрасываем здоровье
        target.setHealth(20.0);
        target.setMaxHealth(20.0);

        // Сбрасываем кэш и хранилище экипировки: без этого старая экипировка
        // (PlayerEquipment в памяти + AttributeModifier'ы) переживёт ресет
        // и продолжит давать бонусы новому персонажу. EclipsiaItems — soft-dep,
        // обращаемся через рефлексию.
        try {
            Class<?> itemsMain = Class.forName("ru.eclipsia.items.EclipsiaItems");
            Object itemsPlugin = itemsMain.getMethod("getInstance").invoke(null);
            if (itemsPlugin != null) {
                Object eqMgr = itemsMain.getMethod("getEquipmentManager").invoke(itemsPlugin);
                if (eqMgr != null) {
                    eqMgr.getClass().getMethod("clearEquipment", Player.class)
                            .invoke(eqMgr, target);
                }
            }
        } catch (ClassNotFoundException ignored) {
            // EclipsiaItems не установлен.
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить экипировку: " + e.getMessage());
        }

        // Сбрасываем кэш навыков: без этого старые навыки прежнего персонажа
        // утекают в только что созданный профиль. EclipsiaSkills — soft-dep,
        // поэтому обращаемся через рефлексию.
        try {
            Class<?> skillsMain = Class.forName("ru.eclipsia.skills.EclipsiaSkills");
            Object skillsPlugin = skillsMain.getMethod("getInstance").invoke(null);
            if (skillsPlugin != null) {
                Object mgr = skillsMain.getMethod("getSkillManager").invoke(skillsPlugin);
                if (mgr != null) {
                    mgr.getClass().getMethod("clearCache", java.util.UUID.class)
                            .invoke(mgr, target.getUniqueId());
                }
            }
        } catch (ClassNotFoundException ignored) {
            // EclipsiaSkills не установлен — пропускаем.
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить кэш навыков: " + e.getMessage());
        }
        
        // v9: Сброс PDC «убил Хранителя» — без этого после /admin resetplayer
        // босс не заспавнится, потому что флаг победы остаётся на игроке.
        // EclipsiaMobs — soft-dep, обращаемся через ключ.
        try {
            org.bukkit.plugin.Plugin mobs = Bukkit.getPluginManager().getPlugin("EclipsiaMobs");
            if (mobs != null) {
                org.bukkit.NamespacedKey defeatedKey =
                        new org.bukkit.NamespacedKey(mobs, "eclipsia_gatekeeper_defeated");
                target.getPersistentDataContainer().remove(defeatedKey);
                Bukkit.getLogger().info("[AdminCommand] PDC сброшен для " + target.getName());
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить PDC босса: " + e.getMessage());
        }

        // v11: Полный сброс состояния Хранителя:
        //  — убрать оставшиеся в мире сущности босса/миньонов;
        //  — сбросить per-player triggered/teleported в GatekeeperArena;
        //  — обнулить isActive на singleton GatekeeperBoss.
        // Без этого после ресета на арене продолжали ходить старые клоны,
        // а портал в Эликий мог сработать у только что сброшенного игрока.
        try {
            Class<?> bossMgr = Class.forName("ru.eclipsia.mobs.boss.BossManager");
            Object mgrInstance = bossMgr.getMethod("getInstance").invoke(null);
            if (mgrInstance != null) {
                bossMgr.getMethod("resetGatekeeper").invoke(mgrInstance);
            }
        } catch (ClassNotFoundException ignored) {
            // EclipsiaMobs не установлен.
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить Хранителя: " + e.getMessage());
        }
        // v12: Сброс дерева перков. Без этого после /admin resetplayer
        // web-tree продолжает показывать старые узлы — допустим, archer
        // c прокачкой воина. EclipsiaPerks — soft-dep, обращаемся через
        // рефлексию.
        try {
            Class<?> perksMain = Class.forName("ru.eclipsia.perks.EclipsiaPerks");
            Object perksPlugin = perksMain.getMethod("getInstance").invoke(null);
            if (perksPlugin != null) {
                Object pm = perksMain.getMethod("getPlayerManager").invoke(perksPlugin);
                if (pm != null) {
                    pm.getClass().getMethod("resetTree", java.util.UUID.class)
                            .invoke(pm, target.getUniqueId());
                    Bukkit.getLogger().info("[AdminCommand] perkTree сброшен для " + target.getName());
                }
            }
        } catch (ClassNotFoundException ignored) {
            // EclipsiaPerks не установлен.
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить perkTree: " + e.getMessage());
        }

        try {
            Class<?> mobsMain = Class.forName("ru.eclipsia.mobs.EclipsiaMobs");
            Object mobsPlugin = mobsMain.getMethod("getInstance").invoke(null);
            if (mobsPlugin != null) {
                Object arena = mobsMain.getMethod("getGatekeeperArena").invoke(mobsPlugin);
                if (arena != null) {
                    arena.getClass()
                            .getMethod("resetPlayerState", java.util.UUID.class)
                            .invoke(arena, target.getUniqueId());
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdminCommand] Не удалось сбросить arena state: " + e.getMessage());
        }

        // Телепортируем в мир lobby (если он есть) — там EclipsiaLobby через
        // PlayerChangedWorldEvent сам откроет GUI выбора персонажа и включит
        // lobby-режим (неуязвимость, полёт, скрытие других игроков).
        World lobby = Bukkit.getWorld("lobby");
        if (lobby != null) {
            target.teleport(new org.bukkit.Location(lobby, 0.5, 5, 0.5));
        } else {
            target.teleport(target.getWorld().getSpawnLocation());
        }

        sender.sendMessage("§a✓ Данные игрока " + target.getName() + " сброшены!");
        target.sendMessage("§e§l⚠ Ваши данные были сброшены администратором!");
        target.sendMessage("§7Выберите персонажа в открывшемся меню.");
    }
}
