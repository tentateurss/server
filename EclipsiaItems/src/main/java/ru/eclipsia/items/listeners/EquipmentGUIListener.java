package ru.eclipsia.items.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.core.api.EclipsiaAPI;
import ru.eclipsia.items.EclipsiaItems;
import ru.eclipsia.items.equipment.EquipmentBonusApplier;
import ru.eclipsia.items.equipment.EquipmentManager;
import ru.eclipsia.items.equipment.PlayerEquipment;
import ru.eclipsia.items.gui.EquipmentGUI;
import ru.eclipsia.items.item.ItemSlot;

/**
 * Обработчик GUI экипировки.
 *
 * <p>Использует whitelist-стратегию: ВСЯ активность в окне отменяется, а затем
 * вручную обрабатываются только 2 разрешённых сценария:
 * <ol>
 *   <li>Игрок кликает по слоту экипировки с курсором в руках, в котором лежит
 *       КАСТОМНЫЙ предмет — экипируем (с проверкой уровня и слота);</li>
 *   <li>Игрок кликает по слоту экипировки пустым курсором, и в слоте лежит
 *       РЕАЛЬНЫЙ кастомный предмет (не заглушка) — снимаем.</li>
 * </ol>
 * Всё остальное (shift-click из нижнего инвентаря, drag, double-click,
 * number-key swap, F-swap, drop key, клик по фону, попытка взять заглушку)
 * заблокировано.
 *
 * <p>Заглушки (placeholder) определяются по тому, что у них НЕТ маркера
 * редкости в лоре ({@link #isCustomItem}); они физически часть GUI и не
 * должны утекать в инвентарь.
 */
public class EquipmentGUIListener implements Listener {

    private static final String GUI_TITLE = "§6Экипировка";

    private final EclipsiaItems plugin;
    private final EquipmentManager equipmentManager;

    public EquipmentGUIListener(EclipsiaItems plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
    }

    // =========================================================================
    // CLICK
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isOurGui(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ВСЕ клики в этом GUI блокируются по умолчанию. Разрешённое поведение
        // ниже выполняется вручную.
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // 1) Клик в нижнем инвентаре игрока. Запрещаем shift-click и любые
        //    действия которые могут переместить предмет в верхний (наш) GUI.
        //    Всё в нижнем — кроме MOVE_TO_OTHER_INVENTORY и COLLECT_TO_CURSOR
        //    — разрешаем (игрок может рассматривать инвентарь, перекладывать
        //    предметы внутри себя). Но запрет shift-click сохраняем чтобы
        //    кастомные предметы не уходили в наш GUI неконтролируемо.
        if (rawSlot >= topSize) {
            InventoryAction action = event.getAction();
            // Внутри player inventory разрешаем перемещения, кроме тех что
            // взаимодействуют с верхним.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR) {
                // Блокируем — игрок пытается через нижний инвентарь воздействовать
                // на верхний (shift-click, double-click).
                return;
            }
            // Number-key swap, в котором hotbarButton указывает в верхний слот
            // (rawSlot < topSize) — здесь не достижим, мы в нижнем. Number-key
            // swap внутри player inventory меняет местами слоты — разрешаем.
            event.setCancelled(false);
            return;
        }

        // 2) Клик внутри нашего GUI (rawSlot < topSize).
        ItemSlot equipSlot = EquipmentGUI.getSlotByIndex(rawSlot);
        if (equipSlot == null) {
            // Клик по фону — оставляем cancelled.
            return;
        }

        // Запрещаем number-key swap (игрок жмёт 1-9 над слотом GUI — мы НЕ
        // хотим чтобы заглушка ушла в hotbar).
        if (event.getClick() == ClickType.NUMBER_KEY) return;
        // Запрещаем double-click (собрать одинаковые в курсор).
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;
        // Запрещаем drop-key Q (попытка выкинуть заглушку из слота GUI).
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) return;
        // Запрещаем F-swap с offhand.
        if (event.getClick() == ClickType.SWAP_OFFHAND) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;
        boolean currentHasItem = current != null && current.getType() != Material.AIR;

        // === Сценарий A: игрок кладёт предмет в слот ===
        if (cursorHasItem) {
            if (!isCustomItem(cursor)) {
                player.sendMessage("§cЭто не кастомный предмет!");
                return;
            }

            ItemSlot itemSlot = getItemSlot(cursor);
            if (!canEquipToSlot(itemSlot, equipSlot)) {
                player.sendMessage("§cЭтот предмет нельзя экипировать в этот слот!");
                return;
            }
            if (!checkRequirements(player, cursor)) {
                player.sendMessage("§cВы не соответствуете требованиям этого предмета!");
                return;
            }

            // Экипируем. Старый предмет (если был кастомный) идёт на курсор;
            // если в слоте была заглушка — она просто пропадает.
            PlayerEquipment equipment = equipmentManager.getEquipment(player);
            ItemStack newItem = cursor.clone();
            newItem.setAmount(1);
            ItemStack oldItem = equipment.equip(equipSlot, newItem);

            event.getView().getTopInventory().setItem(rawSlot, newItem);

            // Курсор: уменьшаем количество текущего и заменяем на oldItem
            // (если был реальный предмет).
            int leftover = cursor.getAmount() - 1;
            if (leftover > 0) {
                ItemStack remainder = cursor.clone();
                remainder.setAmount(leftover);
                // Сначала отдаём остаток курсором; oldItem отправляется в инвентарь.
                player.setItemOnCursor(remainder);
                if (oldItem != null && isCustomItem(oldItem)) {
                    var leftover2 = player.getInventory().addItem(oldItem);
                    leftover2.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
                }
            } else {
                // Курсор пуст — туда кладём старый предмет (если был реальный).
                if (oldItem != null && isCustomItem(oldItem)) {
                    player.setItemOnCursor(oldItem);
                } else {
                    player.setItemOnCursor(null);
                }
            }

            EquipmentBonusApplier.applyBonuses(player, equipment);
            player.sendMessage("§aПредмет экипирован!");
            return;
        }

        // === Сценарий B: игрок забирает предмет из слота (курсор пуст) ===
        if (currentHasItem) {
            // Заглушку забрать НЕЛЬЗЯ.
            if (!isCustomItem(current)) {
                return;
            }

            PlayerEquipment equipment = equipmentManager.getEquipment(player);
            equipment.unequip(equipSlot);

            // На место предмета ставим заглушку.
            ItemStack placeholder = createSlotPlaceholder(equipSlot);
            event.getView().getTopInventory().setItem(rawSlot, placeholder);
            player.setItemOnCursor(current.clone());

            EquipmentBonusApplier.applyBonuses(player, equipment);
            player.sendMessage("§aПредмет снят!");
        }
    }

    // =========================================================================
    // DRAG (распределение через зажатую ЛКМ/ПКМ)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isOurGui(event.getView().getTitle())) return;
        // Drag всегда отменяем — для GUI экипировки он не нужен,
        // а через него можно положить предмет одновременно во все слоты.
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // =========================================================================
    // CLOSE
    // =========================================================================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isOurGui(event.getView().getTitle())) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Если у игрока на курсоре заглушка (теоретически невозможно при
        // нашем whitelist, но защищаемся от любых обходов клиента) — удаляем.
        ItemStack onCursor = player.getItemOnCursor();
        if (onCursor != null && onCursor.getType() != Material.AIR && !isCustomItem(onCursor)) {
            player.setItemOnCursor(null);
        }

        // На всякий случай чистим инвентарь от утекших заглушек.
        purgePlaceholders(player);

        equipmentManager.saveEquipment(player.getUniqueId());
    }

    /**
     * Удаляет из инвентаря игрока любые предметы, которые выглядят как
     * заглушки слотов GUI (леки. шлем/нагрудник/штаны/ботинки/деревянный
     * меч/щит/стекло) И при этом НЕ являются кастомными (нет редкости в
     * лоре). Это страховка от любых будущих способов вытащить заглушку.
     */
    private void purgePlaceholders(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (looksLikePlaceholder(it) && !isCustomItem(it)) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) player.getInventory().setContents(contents);
    }

    /** Похож на заглушку слота? Проверяем материал + лор «§8Пусто». */
    private boolean looksLikePlaceholder(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        for (String line : meta.getLore()) {
            String cleaned = line.replaceAll("§.", "").trim();
            if (cleaned.equalsIgnoreCase("Пусто") || cleaned.equalsIgnoreCase("Empty")) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ
    // =========================================================================

    private boolean isOurGui(String title) {
        return GUI_TITLE.equals(title);
    }

    /**
     * Кастомный предмет имеет в лоре маркер редкости.
     */
    private boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;

        for (String line : meta.getLore()) {
            String cleaned = line.replaceAll("§.", "");
            if (cleaned.contains("Обычный") || cleaned.contains("Магический")
                    || cleaned.contains("Редкий") || cleaned.contains("Уникальный")
                    || cleaned.contains("Common") || cleaned.contains("Magic")
                    || cleaned.contains("Rare") || cleaned.contains("Unique")) {
                return true;
            }
        }
        return false;
    }

    private ItemSlot getItemSlot(ItemStack item) {
        String materialName = item.getType().name();

        if (materialName.contains("SWORD") || materialName.contains("AXE")
                || materialName.contains("BOW") || materialName.equals("STICK")
                || materialName.equals("BLAZE_ROD") || materialName.equals("SHIELD")
                || materialName.contains("CROSSBOW") || materialName.contains("TRIDENT")) {
            return ItemSlot.HAND;
        } else if (materialName.contains("HELMET")) {
            return ItemSlot.HEAD;
        } else if (materialName.contains("CHESTPLATE")) {
            return ItemSlot.CHEST;
        } else if (materialName.contains("LEGGINGS")) {
            return ItemSlot.LEGS;
        } else if (materialName.contains("BOOTS")) {
            return ItemSlot.FEET;
        } else if (materialName.equals("EMERALD") || materialName.equals("DIAMOND")) {
            return ItemSlot.AMULET;
        } else if (materialName.contains("GOLD_INGOT") || materialName.contains("GOLD_NUGGET")) {
            return ItemSlot.RING_1;
        } else if (materialName.equals("LEATHER")) {
            return ItemSlot.BELT;
        }
        return ItemSlot.HAND;
    }

    private boolean canEquipToSlot(ItemSlot itemSlot, ItemSlot targetSlot) {
        if (itemSlot == ItemSlot.RING_1 || itemSlot == ItemSlot.RING_2) {
            return targetSlot == ItemSlot.RING_1 || targetSlot == ItemSlot.RING_2;
        }
        if (itemSlot == ItemSlot.HAND || itemSlot == ItemSlot.OFFHAND) {
            return targetSlot == ItemSlot.HAND || targetSlot == ItemSlot.OFFHAND;
        }
        return itemSlot == targetSlot;
    }

    private boolean checkRequirements(Player player, ItemStack item) {
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return true;

        EclipsiaAPI api = EclipsiaAPI.getInstance();
        int playerLevel = api.getPlayerLevel(player);

        for (String line : meta.getLore()) {
            String cleaned = line.replaceAll("§.", "").trim();
            if (cleaned.startsWith("Требуется уровень:") || cleaned.startsWith("Required level:")) {
                try {
                    int requiredLevel = Integer.parseInt(
                            cleaned.substring(cleaned.indexOf(":") + 1).trim());
                    if (playerLevel < requiredLevel) return false;
                } catch (NumberFormatException ignored) { /* пропускаем */ }
            }
        }
        return true;
    }

    private ItemStack createSlotPlaceholder(ItemSlot slot) {
        Material material = switch (slot) {
            case HEAD -> Material.LEATHER_HELMET;
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case LEGS -> Material.LEATHER_LEGGINGS;
            case FEET -> Material.LEATHER_BOOTS;
            case HAND -> Material.WOODEN_SWORD;
            case OFFHAND -> Material.SHIELD;
            case AMULET -> Material.PURPLE_STAINED_GLASS_PANE;
            case RING_1, RING_2 -> Material.YELLOW_STAINED_GLASS_PANE;
            case BELT -> Material.BROWN_STAINED_GLASS_PANE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7" + slot.getDisplayName());
            meta.setLore(java.util.Arrays.asList("§8Пусто", "§7Перетащите предмет сюда"));
            item.setItemMeta(meta);
        }
        return item;
    }
}
