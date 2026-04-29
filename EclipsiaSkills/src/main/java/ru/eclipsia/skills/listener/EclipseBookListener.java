package ru.eclipsia.skills.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.eclipse.EclipseBook;
import ru.eclipsia.skills.eclipse.EclipseItem;
import ru.eclipsia.skills.manager.SkillManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель «эклипс-книг»: ПКМ по книге открывает GUI выбора, после клика
 * по варианту нужный навык/поддержка зачисляется игроку, а книга съедается.
 *
 * <p>Поддерживаются два типа книг (см. {@link EclipseBook}):
 * <ul>
 *   <li>{@code skill} — выбор стартового навыка (3 варианта). Выдаётся
 *       Lobby после выбора класса.</li>
 *   <li>{@code support} — выбор эклипс-поддержки (3 варианта) + целевого
 *       навыка, к которому она крепится. Выдаётся при убийстве босса.</li>
 * </ul>
 *
 * <p>GUI распознаются по точному заголовку — это самый дешёвый способ
 * не плодить ещё один listener-класс на каждое окно. Заголовки:
 * <ul>
 *   <li>{@link #SKILL_GUI_TITLE} — выбор навыка</li>
 *   <li>{@link #SUPPORT_GUI_TITLE} — выбор поддержки</li>
 * </ul>
 *
 * <p>После выбора в книге оба варианта одинаково кладут <b>гем</b> (изумруд /
 * аметистовый осколок) в инвентарь игрока. Дальше игрок сам экипирует:
 * ПКМ по гему — быстрый автоэкип, либо drag/click в {@code SkillsGUI}.
 */
public class EclipseBookListener implements Listener {

    public static final String SKILL_GUI_TITLE        = "§6Выбор Эклипс-Навыка";
    public static final String SUPPORT_GUI_TITLE      = "§dВыбор Эклипс-Поддержки";

    /** Список ID стартовых навыков (соответствует {@link EclipseItem#fromId(String)}). */
    private static final String[] SKILL_IDS   = {"melee_strike_1", "arrow_shot_1", "fireball_1"};
    /** Список ID базовых поддержек. */
    private static final String[] SUPPORT_IDS = {"multi_shot_1", "explosion_1", "aoe_radius_1"};

    private final EclipsiaSkills plugin;

    public EclipseBookListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    //  ПКМ по книге → открыть нужный GUI
    // ============================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String kind = EclipseBook.getKind(inHand);
        if (kind == null) return;

        // Перехватываем — иначе заколдованная книга может проиграть звук
        // или открыть нативное GUI зачарования.
        event.setCancelled(true);

        if (EclipseBook.KIND_SKILL.equals(kind)) {
            openSkillGui(player);
        } else if (EclipseBook.KIND_SUPPORT.equals(kind)) {
            openSupportGui(player);
        }
    }

    // ============================================================
    //  Клик внутри GUI → применить выбор
    // ============================================================
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!isOurGui(title)) return;

        // Любой клик в наших GUI отменяем — это меню выбора, не инвентарь.
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        // Клик не по верхней инвентарной части (по своим вещам игрока) — игнор.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        if (SKILL_GUI_TITLE.equals(title)) {
            handleSkillChoice(player, event.getSlot());
        } else if (SUPPORT_GUI_TITLE.equals(title)) {
            handleSupportChoice(player, event.getSlot());
        }
    }

    private boolean isOurGui(String title) {
        return SKILL_GUI_TITLE.equals(title)
                || SUPPORT_GUI_TITLE.equals(title);
    }

    // ============================================================
    //  GUI 1 — выбор навыка
    // ============================================================
    private void openSkillGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, SKILL_GUI_TITLE);
        // Иконки в слотах 2/4/6 — визуально центрировано в строке из 9.
        inv.setItem(2, iconForSkill(SKILL_IDS[0]));
        inv.setItem(4, iconForSkill(SKILL_IDS[1]));
        inv.setItem(6, iconForSkill(SKILL_IDS[2]));
        player.openInventory(inv);
    }

    private void handleSkillChoice(Player player, int slot) {
        String chosenId = switch (slot) {
            case 2 -> SKILL_IDS[0];
            case 4 -> SKILL_IDS[1];
            case 6 -> SKILL_IDS[2];
            default -> null;
        };
        if (chosenId == null) return;

        EclipseItem skill = EclipseItem.fromId(chosenId);
        if (skill == null) {
            player.sendMessage("§cОшибка: навык '" + chosenId + "' не найден в реестре.");
            return;
        }

        // Снимаем книгу из руки и выдаём ИГРОКУ ГЕМ навыка (изумруд) в инвентарь.
        // Игрок сам экипирует его — ПКМ по гему или через GUI §6Навыки.
        if (!consumeEclipseBookFromHand(player, EclipseBook.KIND_SKILL)) {
            player.sendMessage("§cКнига не найдена в руке.");
            return;
        }

        giveOrDrop(player, skill.toItemStack());
        player.sendMessage("§a✦ Вы выбрали навык: §6" + skill.getName());
        player.sendMessage("§7ПКМ по гему — быстрый экип, §6/skills§7 — открыть GUI.");
        player.closeInventory();
    }

    // ============================================================
    //  GUI 2 — выбор поддержки
    // ============================================================
    private void openSupportGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, SUPPORT_GUI_TITLE);
        inv.setItem(2, iconForSupport(SUPPORT_IDS[0]));
        inv.setItem(4, iconForSupport(SUPPORT_IDS[1]));
        inv.setItem(6, iconForSupport(SUPPORT_IDS[2]));
        player.openInventory(inv);
    }

    private void handleSupportChoice(Player player, int slot) {
        String chosenId = switch (slot) {
            case 2 -> SUPPORT_IDS[0];
            case 4 -> SUPPORT_IDS[1];
            case 6 -> SUPPORT_IDS[2];
            default -> null;
        };
        if (chosenId == null) return;

        EclipseItem support = EclipseItem.fromId(chosenId);
        if (support == null) {
            player.sendMessage("§cОшибка: поддержка '" + chosenId + "' не найдена в реестре.");
            return;
        }

        if (!consumeEclipseBookFromHand(player, EclipseBook.KIND_SUPPORT)) {
            // Книга могла быть не в руке (например, игрок переложил) — ищем в инвентаре.
            if (!consumeEclipseBookFromInventory(player, EclipseBook.KIND_SUPPORT)) {
                player.sendMessage("§cКнига поддержки не найдена.");
                return;
            }
        }

        // Просто кладём гем поддержки (аметист) в инвентарь — игрок сам экипирует его
        // в свободный support-слот через GUI §6Навыки либо ПКМ по гему.
        giveOrDrop(player, support.toItemStack());
        player.sendMessage("§d✦ Вы выбрали поддержку: §f" + support.getName());
        player.sendMessage("§7ПКМ по гему — быстрый экип, §6/skills§7 — открыть GUI.");
        player.closeInventory();
    }

    /** Положить предмет в инвентарь, при переполнении — уронить рядом. */
    private void giveOrDrop(Player player, ItemStack stack) {
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // ============================================================
    //  Хелперы
    // ============================================================
    private ItemStack iconForSkill(String id) {
        EclipseItem skill = EclipseItem.fromId(id);
        if (skill == null) return placeholder(Material.BARRIER, "§c" + id);
        Material icon = switch (skill.getSkillClass()) {
            case MELEE_STRIKE -> Material.IRON_SWORD;
            case ARROW_SHOT   -> Material.BOW;
            case FIREBALL     -> Material.BLAZE_ROD;
        };
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + skill.getName());
            List<String> lore = new ArrayList<>(skill.getDescription() != null ? skill.getDescription() : List.of());
            lore.add("");
            lore.add("§eКлик — выбрать этот навык");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack iconForSupport(String id) {
        EclipseItem support = EclipseItem.fromId(id);
        if (support == null) return placeholder(Material.BARRIER, "§c" + id);
        // Все поддержки выглядят как аметистовый осколок
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d" + support.getName());
            List<String> lore = new ArrayList<>(support.getDescription() != null ? support.getDescription() : List.of());
            lore.add("");
            // Добавляем описание типа поддержки
            String typeDesc = switch (support.getSupportClass()) {
                case MULTI_SHOT -> "§7Тип: §eМультивыстрел";
                case EXPLOSION -> "§7Тип: §cВзрыв";
                case AOE_RADIUS -> "§7Тип: §aРадиус AOE";
            };
            lore.add(typeDesc);
            lore.add("");
            lore.add("§eКлик — выбрать эту поддержку");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack placeholder(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Снимает 1 экземпляр эклипс-книги нужного вида из основной руки. */
    private boolean consumeEclipseBookFromHand(Player player, String wantedKind) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!wantedKind.equals(EclipseBook.getKind(inHand))) return false;
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(amount - 1);
        }
        return true;
    }

    /** Снимает 1 экземпляр эклипс-книги нужного вида из любого слота инвентаря.
     *  Используется для GUI цели поддержки — в этот момент игрок может уже
     *  не держать книгу в руке. */
    private boolean consumeEclipseBookFromInventory(Player player, String wantedKind) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (wantedKind.equals(EclipseBook.getKind(it))) {
                int amount = it.getAmount();
                if (amount <= 1) {
                    inv.setItem(i, null);
                } else {
                    it.setAmount(amount - 1);
                }
                return true;
            }
        }
        return false;
    }

    // ============================================================
    //  Защита: книгу нельзя выбросить (как и иконки навыков).
    // ============================================================
    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (EclipseBook.isEclipseBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cНельзя выбросить эклипс-книгу — используйте ПКМ для выбора.");
        }
    }
}
