package ru.eclipsia.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.gui.MainMenuGUI;
import ru.eclipsia.items.menu.MenuBook;

/**
 * Обработчик книги меню.
 * Гарантирует, что книга всегда находится в слоте 8 хотбара и её нельзя:
 *  — выбросить,
 *  — переместить в другой слот (drag, shift-click, swap, number-key),
 *  — заменить другим предметом (swap с курсором, hotbar-swap).
 * Книга выдаётся на вход в мир, после респавна и после смены мира (reset/lobby).
 */
public class MenuBookListener implements Listener {

    private static final int MENU_SLOT = 8;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ensureMenuBook(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(EclipsiaItems.getInstance(),
                () -> ensureMenuBook(event.getPlayer()), 1L);
    }

    /**
     * При переходе между мирами (lobby → beach после /admin resetplayer,
     * или возврат в лобби) книга должна появиться заново, если её нет.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(EclipsiaItems.getInstance(),
                () -> ensureMenuBook(event.getPlayer()), 2L);
    }

    /**
     * Положить книгу в слот 8, если её там нет. Существующий предмет
     * из слота 8 переносится в свободный слот (или выбрасывается).
     */
    public static void ensureMenuBook(Player player) {
        if (!player.isOnline()) return;
        ItemStack slot = player.getInventory().getItem(MENU_SLOT);
        if (slot != null && MenuBook.isMenuBook(slot)) return;

        if (slot != null && !slot.getType().isAir()) {
            player.getInventory().setItem(MENU_SLOT, null);
            var overflow = player.getInventory().addItem(slot);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.getInventory().setItem(MENU_SLOT, MenuBook.createMenuBook());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !MenuBook.isMenuBook(item)) return;

        event.setCancelled(true);
        MainMenuGUI.open(event.getPlayer());
    }

    /**
     * Запрещаем выбрасывать книгу (Q / Ctrl+Q).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (MenuBook.isMenuBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете выбросить книгу меню!");
        }
    }

    /**
     * Запрещаем менять книгу местами с левой рукой (F).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (MenuBook.isMenuBook(event.getMainHandItem())
                || MenuBook.isMenuBook(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Защита от любого изменения слота 8 и перемещения самой книги.
     * Слот 8 — неприкосновенный, в нём должна жить только книга.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Работаем только в инвентаре самого игрока.
        if (event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            // Но всё равно блокируем shift-click книги из GUI в player inv.
            ItemStack cur = event.getCurrentItem();
            if (cur != null && MenuBook.isMenuBook(cur)) {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1. Любая попытка взять/переместить книгу — отмена.
        if (current != null && MenuBook.isMenuBook(current)) {
            event.setCancelled(true);
            return;
        }
        if (cursor != null && MenuBook.isMenuBook(cursor) && slot != MENU_SLOT) {
            event.setCancelled(true);
            return;
        }

        // 2. Любая модификация слота 8 (кроме размещения самой книги) — отмена.
        //    Сюда попадает: положить другой предмет, shift-click в слот 8,
        //    number-key swap (HOTBAR_SWAP), hotbar-swap через Mouse4/5.
        if (slot == MENU_SLOT) {
            if (cursor != null && MenuBook.isMenuBook(cursor)) {
                // Разрешаем только ставить саму книгу назад.
                return;
            }
            event.setCancelled(true);
            return;
        }

        // 3. Number-key: игрок нажал 9 на другом слоте — это свопнет содержимое
        //    со слотом 8. Блокируем, если целевой hotbar-слот = 8.
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == MENU_SLOT) {
            event.setCancelled(true);
            return;
        }

        // 4. SWAP_OFFHAND (F): если swap-источник = слот 8, отмена.
        if (event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
                && slot == MENU_SLOT) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрещаем drag-выделение, которое касается слота 8 или тащит книгу.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Курсор — сама книга меню: разрешаем только drag в слот 8 (фактически
        // бессмысленно — там уже должна быть книга — но страхуемся).
        if (MenuBook.isMenuBook(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }

        // Любой drag, затрагивающий слот 8 в инвентаре игрока, — отмена.
        int rawMenuSlot = event.getView().getBottomInventory().getType() == InventoryType.PLAYER
                ? event.getView().getTopInventory().getSize() + MENU_SLOT
                : MENU_SLOT;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == rawMenuSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
