package ru.eclipsia.lobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.eclipsia.core.data.PlayerData;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.lobby.EclipsiaLobby;
import ru.eclipsia.lobby.gui.CharacterCreationGUI;
import ru.eclipsia.lobby.gui.CharacterSelectionGUI;

/**
 * Слушатель событий лобби
 */
public class LobbyListener implements Listener {
    
    private final EclipsiaLobby plugin;
    private final CharacterSelectionGUI selectionGUI;
    
    public LobbyListener(EclipsiaLobby plugin) {
        this.plugin = plugin;
        this.selectionGUI = new CharacterSelectionGUI(plugin);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!isInLobbyWorld(player)) return;

        // Сразу включаем lobby-режим: телепорт на безопасную высоту,
        // полёт, неуязвимость, скрытие других игроков.
        enterLobbyMode(player);

        // Откладываем GUI на ~1.5s: даём ресурс-паку и PlayerData подтянуться.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            handleLobbyEntry(player);
        }, 30L);
    }

    /**
     * Запускается при любом переходе игрока в/из мира lobby — в т.ч. после
     * /admin resetplayer + /mv tp lobby. Открывает GUI выбора и включает
     * lobby-режим. При выходе из лобби возвращает обычное состояние.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "lobby");
        boolean nowInLobby = player.getWorld().getName().equals(lobbyWorld);
        boolean wasInLobby = event.getFrom().getName().equals(lobbyWorld);

        if (nowInLobby && !wasInLobby) {
            enterLobbyMode(player);
            // GUI открываем через 5 тиков — клиент уже в новом мире.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) handleLobbyEntry(player);
            }, 5L);
        } else if (wasInLobby && !nowInLobby) {
            exitLobbyMode(player);
        }
    }

    private boolean isInLobbyWorld(Player p) {
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "lobby");
        return p.getWorld().getName().equals(lobbyWorld);
    }

    /**
     * Включить lobby-режим: безопасная позиция, неуязвимость, полёт,
     * adventure-режим (без ломания блоков), скрытие других игроков.
     */
    private void enterLobbyMode(Player player) {
        // Безопасный спавн (плоский мир, поверхность ≈ y=4).
        double sx = plugin.getConfig().getDouble("lobby.spawn.x", 0.5);
        double sy = plugin.getConfig().getDouble("lobby.spawn.y", 5);
        double sz = plugin.getConfig().getDouble("lobby.spawn.z", 0.5);
        World w = player.getWorld();
        Location safe = new Location(w, sx, sy, sz);
        // Если игрок появился в воздухе/в стене — телепорт на конфиг-спавн.
        if (player.getLocation().getY() < 0 || !player.getLocation().getBlock().isEmpty()) {
            player.teleport(safe);
        }

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGameMode(GameMode.ADVENTURE);

        // Скрываем других игроков и скрываемся для них.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.hidePlayer(plugin, other);
            other.hidePlayer(plugin, player);
        }
    }

    /**
     * Выключить lobby-режим — игрок выходит из лобби (например, телепорт
     * на Берег): обычное состояние, видимость восстановлена.
     */
    private void exitLobbyMode(Player player) {
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.showPlayer(plugin, other);
            other.showPlayer(plugin, player);
        }
    }

    /**
     * Урон в мире lobby полностью отключён (страховка к invulnerable).
     */
    @EventHandler
    public void onLobbyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (isInLobbyWorld(p)) event.setCancelled(true);
    }

    /**
     * Голод в лобби не уменьшается.
     */
    @EventHandler
    public void onLobbyHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (isInLobbyWorld(p)) event.setCancelled(true);
    }

    /**
     * Главная точка маршрутизации игрока в лобби.
     * Если есть активный профиль — телепорт на сохранённую локацию или Берег.
     * Если нет — открывается GUI выбора персонажа (3 ячейки), даже когда все
     * профили пусты: игрок кликает пустой слот → CharacterCreationGUI.
     * Метод вынесен наружу, чтобы его мог переиспользовать InventoryCloseEvent.
     */
    private void handleLobbyEntry(Player player) {
        plugin.getAPI().loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                // Если данных нет (новый игрок и storage не создал запись) —
                // всё равно показываем меню выбора с 3 пустыми слотами.
                if (data == null) {
                    plugin.getLogger().info("Lobby: данные игрока " + player.getName()
                            + " == null, открываю пустое меню выбора.");
                    selectionGUI.open(player);
                    return;
                }

                // Активный профиль есть → телепорт на его локацию или на Берег.
                if (data.getActiveSlot() != -1) {
                    PlayerProfile profile = data.getActiveProfile();
                    if (profile != null && profile.getLastLocation() != null) {
                        Location loc = parseLocation(profile.getLastLocation());
                        if (loc != null) {
                            player.teleport(loc);
                            return;
                        }
                    }
                    teleportToBeach(player);
                    return;
                }

                // Нет активного профиля → меню выбора (3 ячейки).
                // НИКОГДА не открываем CharacterCreationGUI напрямую: пользователь
                // должен сначала увидеть слоты персонажей.
                selectionGUI.open(player);
            });
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getAPI().getPlayerData(player);
        
        if (data == null || data.getActiveSlot() == -1) return;
        
        // Сохраняем текущую позицию в lastLocation активного профиля
        PlayerProfile profile = data.getActiveProfile();
        if (profile != null) {
            Location loc = player.getLocation();
            String locString = String.format("%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getPitch(), loc.getYaw()
            );
            
            PlayerProfile updated = profile.toBuilder()
                    .lastLocation(locString)
                    .lastPlayed(System.currentTimeMillis())
                    .build();
            
            PlayerData updatedData = data.toBuilder()
                    .setProfile(profile.getSlot(), updated)
                    .build();
            
            plugin.getAPI().savePlayerData(updatedData);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Блокируем движение в мире lobby
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "lobby");
        if (player.getWorld().getName().equals(lobbyWorld)) {
            // Разрешаем только поворот головы
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        
        // GUI выбора персонажа
        if (title.equals("§6Выбор персонажа")) {
            event.setCancelled(true);
            
            if (!CharacterSelectionGUI.isCharacterSlot(event.getRawSlot())) return;
            
            Integer slot = CharacterSelectionGUI.getSlotByIndex(event.getRawSlot());
            if (slot == null) return;
            
            PlayerProfile profile = plugin.getAPI().getProfile(player, slot);
            
            if (profile == null) {
                // Пустой слот - открываем меню создания
                player.closeInventory();
                new CharacterCreationGUI(slot).open(player);
            } else {
                // Есть профиль - переключаемся и телепортируем
                if (plugin.getAPI().switchProfile(player, slot)) {
                    player.closeInventory();
                    
                    if (profile.getLastLocation() != null) {
                        Location loc = parseLocation(profile.getLastLocation());
                        if (loc != null) {
                            player.teleport(loc);
                            player.sendMessage("§aВы вошли за персонажа §6" + getClassDisplayName(profile.getClassName()));
                            return;
                        }
                    }
                    
                    // Если lastLocation нет - телепортируем на спавн Берега
                    teleportToBeach(player);
                    player.sendMessage("§aВы вошли за персонажа §6" + getClassDisplayName(profile.getClassName()));
                }
            }
        }
        
        // GUI создания персонажа
        else if (title.equals("§6Создание персонажа")) {
            event.setCancelled(true);
            
            if (!CharacterCreationGUI.isClassSlot(event.getRawSlot())) return;
            
            String className = CharacterCreationGUI.getClassByIndex(event.getRawSlot());
            if (className == null) return;
            
            // Получаем целевой слот из GUI
            PlayerData data = plugin.getAPI().getPlayerData(player);
            if (data == null) return;
            
            int targetSlot = data.getFreeSlot();
            if (targetSlot == -1) {
                player.sendMessage("§cНет свободных слотов для персонажа!");
                player.closeInventory();
                return;
            }
            
            // Создаем профиль
            if (plugin.getAPI().createProfile(player, className)) {
                player.closeInventory();
                player.sendMessage("§aПерсонаж создан! Класс: §6" + getClassDisplayName(className));
                plugin.getLogger().info("Lobby: создан профиль " + className + " для " + player.getName()
                        + ", телепортирую на " + plugin.getConfig().getString("beach.world", "beach"));

                // Телепортируем на Берег с задержкой 2 тика, чтобы closeInventory
                // и сохранение профиля точно отработали раньше телепорта.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) teleportToBeach(player);
                }, 2L);

                // Выдаем стартовый навык
                giveStarterSkill(player, className);

                // Уведомляем подписчиков (EclipsiaPerks → автогаз стартового
                // узла дерева). Без этого после выбора класса в лобби
                // ClassStartNodeListener не срабатывал, потому что событие
                // фирилось только из /class в EclipsiaCore.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        Bukkit.getPluginManager().callEvent(
                                new ru.eclipsia.core.events.ClassSelectedEvent(player, className));
                    } catch (Throwable t) {
                        plugin.getLogger().warning(
                                "Не удалось зафайрить ClassSelectedEvent: " + t.getMessage());
                    }
                }, 4L);
            } else {
                player.sendMessage("§cОшибка создания персонажа!");
                plugin.getLogger().warning("Lobby: createProfile вернул false для "
                        + player.getName() + " (class=" + className + ")");
            }
        }
    }

    /**
     * Запрет закрытия GUI выбора/создания персонажа в мире lobby.
     * Если игрок ещё не имеет активного профиля и пытается закрыть инвентарь
     * (Esc) — мгновенно переоткрываем нужное меню.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        boolean lobbyMenu = title.equals("§6Выбор персонажа")
                || title.equals("§6Создание персонажа");
        if (!lobbyMenu) return;

        String lobbyWorld = plugin.getConfig().getString("lobby.world", "lobby");
        if (!player.getWorld().getName().equals(lobbyWorld)) return;

        // Если у игрока уже есть активный профиль (например, успешный create
        // только что выставил activeSlot и инициировал телепорт) — не мешаем.
        PlayerData data = plugin.getAPI().getPlayerData(player);
        if (data != null && data.getActiveSlot() != -1) return;

        // Иначе — переоткрываем меню выбора через 2 тика, НО только если
        // игрок не открыл другое lobby-меню (например CharacterCreationGUI).
        // Без этой проверки клик по пустому слоту в SelectionGUI приводит
        // к race condition: closeInventory → переоткрытие SelectionGUI поверх
        // только что открытого CreationGUI.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!player.getWorld().getName().equals(lobbyWorld)) return;
            PlayerData curData = plugin.getAPI().getPlayerData(player);
            if (curData != null && curData.getActiveSlot() != -1) return;

            // Если у игрока уже открыто какое-либо lobby-меню — не трогаем.
            String openTitle = player.getOpenInventory().getTitle();
            if (openTitle.equals("§6Выбор персонажа")
                    || openTitle.equals("§6Создание персонажа")) {
                return;
            }
            selectionGUI.open(player);
        }, 2L);
    }

    /**
     * Запрет выкидывать предметы в лобби — игрок там вообще не должен иметь
     * хот-бар активным (он в GUI), но на всякий случай.
     */
    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "lobby");
        if (event.getPlayer().getWorld().getName().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Телепортировать игрока на спавн Берега
     */
    private void teleportToBeach(Player player) {
        String worldName = plugin.getConfig().getString("beach.world", "beach");
        double x = plugin.getConfig().getDouble("beach.spawn.x", 0.5);
        // y=5: для плоского мира (поверхность y=4) безопасная высота.
        double y = plugin.getConfig().getDouble("beach.spawn.y", 5);
        double z = plugin.getConfig().getDouble("beach.spawn.z", 0.5);
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("teleportToBeach: мир '" + worldName
                    + "' не загружен! Игрок " + player.getName() + " остался в лобби.");
            player.sendMessage("§c[Ошибка] Мир '" + worldName + "' не существует. Сообщите админу.");
            return;
        }

        // Лог стека вызовов — чтобы понять, кто/откуда телепортирует игрока
        // на Берег. Особенно подозрительно, если это происходит уже после
        // портала Хранителя Врат (мир должен быть 'world', а нас сюда
        // зачем-то отправили обратно). Берём первые 6 фреймов выше нас.
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder trace = new StringBuilder();
        for (int i = 2; i < Math.min(st.length, 8); i++) {
            trace.append("\n        at ").append(st[i]);
        }
        plugin.getLogger().info("teleportToBeach: вызов для " + player.getName()
                + " currentWorld=" + player.getWorld().getName()
                + trace);

        // yaw=180 → лицо на юг (вниз по карте — туда уходит тропа в лес
        // к арене Хранителя Врат). pitch=0 — взгляд горизонтально.
        Location loc = new Location(world, x, y, z, 180f, 0f);
        boolean ok = player.teleport(loc);
        plugin.getLogger().info("teleportToBeach: " + player.getName() + " -> " + worldName
                + " (" + x + "," + y + "," + z + ") result=" + ok);
    }
    
    /**
     * Парсинг локации из строки
     */
    private Location parseLocation(String locString) {
        try {
            String[] parts = locString.split(":");
            if (parts.length != 6) return null;
            
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float pitch = Float.parseFloat(parts[4]);
            float yaw = Float.parseFloat(parts[5]);
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
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
     * Выдать игроку «Эклипс Навыка» — книгу выбора стартового навыка.
     * <p>Раньше навык вшивался автоматически по {@code className} (warrior →
     * melee_strike, archer → arrow_shot, mage → fireball). Теперь игрок
     * получает книгу-предмет, по ПКМ открывается GUI с 3 вариантами:
     * <i>Удар мечом / Выстрел / Огненный шар</i>. Логика выбора и вставки
     * навыка лежит в EclipsiaSkills/EclipseBookListener — здесь только
     * сама выдача предмета.
     *
     * <p>Параметр {@code className} оставлен в сигнатуре для совместимости
     * с уже существующими вызовами и для логов: класс сейчас не
     * ограничивает доступные навыки — игрок сам решает.
     */
    private void giveStarterSkill(Player player, String className) {
        // Отложенная выдача через 1 тик, чтобы профиль успел зафиксироваться
        // и инвентарь игрока был доступен после смены мира/гм.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPluginManager().getPlugin("EclipsiaSkills") == null) {
                plugin.getLogger().warning("EclipsiaSkills не загружен, эклипс-книга не выдана!");
                return;
            }

            org.bukkit.inventory.ItemStack book =
                    ru.eclipsia.skills.eclipse.EclipseBook.createSkillBook();

            // Кладём в инвентарь; если переполнен — роняем рядом, чтобы
            // игрок не остался без книги (важная стартовая награда).
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                    player.getInventory().addItem(book);
            if (!overflow.isEmpty()) {
                for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }

            player.sendMessage("§a✦ Вы получили §6Эклипс Навыка§a! §7ПКМ по книге — выбрать навык.");
            plugin.getLogger().info("[Lobby] Выдана Эклипс Книга навыка игроку "
                    + player.getName() + " (класс: " + className + ")");
        }, 1L);
    }
}
