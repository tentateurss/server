package ru.eclipsia.skills.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.data.SkillData;
import ru.eclipsia.skills.eclipse.EclipseItem;

import java.util.*;

/**
 * Менеджер навыков игроков
 */
public class SkillManager {
    
    private final EclipsiaSkills plugin;
    private final Map<UUID, PlayerSkills> playerSkills;
    
    public SkillManager(EclipsiaSkills plugin) {
        this.plugin = plugin;
        this.playerSkills = new HashMap<>();
    }
    
    /**
     * Сбросить кэш навыков игрока. Вызывается при /admin resetplayer,
     * чтобы старые навыки предыдущего персонажа не утекли в новый профиль.
     */
    public void clearCache(UUID uuid) {
        playerSkills.remove(uuid);
    }

    /**
     * Получить навыки игрока
     */
    public PlayerSkills getPlayerSkills(Player player) {
        PlayerSkills skills = playerSkills.get(player.getUniqueId());
        
        // Если навыки не загружены - загружаем из профиля
        if (skills == null) {
            skills = loadSkillsFromProfile(player);
            playerSkills.put(player.getUniqueId(), skills);
        }
        
        return skills;
    }
    
    /**
     * Загрузить навыки из профиля игрока.
     * Использует {@link EclipseItem#fromId(String)} как реестр всех известных
     * эклипсов. Неизвестные id логируются и пропускаются.
     */
    private PlayerSkills loadSkillsFromProfile(Player player) {
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        PlayerSkills skills = new PlayerSkills();

        if (profile == null || profile.getEquipmentData() == null) {
            return skills;
        }

        SkillData data = SkillData.fromJson(profile.getEquipmentData());

        for (int i = 0; i < 5; i++) {
            String skillId = data.getSkill(i);
            if (skillId != null) {
                EclipseItem skill = EclipseItem.fromId(skillId);
                if (skill != null) {
                    skills.skillSlots[i] = skill;
                } else {
                    plugin.getLogger().warning("Неизвестный skill id в профиле игрока "
                            + player.getName() + ": " + skillId);
                }
            }

            String[] supports = data.getSupports(i);
            for (int j = 0; j < 2; j++) {
                if (supports[j] != null) {
                    EclipseItem support = EclipseItem.fromId(supports[j]);
                    if (support != null) {
                        skills.supportSlots[i][j] = support;
                    } else {
                        plugin.getLogger().warning("Неизвестный support id в профиле игрока "
                                + player.getName() + ": " + supports[j]);
                    }
                }
            }
        }

        // 1) Применяем сохранённый hotbarMapping из SkillData (новый формат).
        Map<Integer, Integer> savedMapping = data.getHotbarMapping();
        for (Map.Entry<Integer, Integer> e : savedMapping.entrySet()) {
            int hotbarSlot = e.getKey();
            int skillSlot = e.getValue();
            if (hotbarSlot < 0 || hotbarSlot > 8) continue;
            if (skillSlot < 0 || skillSlot >= 5) continue;
            if (skills.skillSlots[skillSlot] == null) continue;
            skills.hotbarMapping.put(hotbarSlot, skillSlot);
        }

        // 2) Fallback: для старых сохранений или если в хотбаре уже стоят
        //    иконки, но маппинг пуст — восстанавливаем сканированием.
        if (skills.hotbarMapping.isEmpty()) {
            restoreHotbarMappingFromInventory(player, skills);
        }

        return skills;
    }

    /**
     * Сканирует хотбар игрока и восстанавливает {@code hotbarMapping} по
     * material иконки. Используется как fallback для миграции старых
     * сохранений (без поля {@code hotbar} в SkillData).
     *
     * <p>Логика: для каждого слота 0..8, если в нём предмет с типом
     * IRON_SWORD/BOW/BLAZE_ROD и есть display name (признак иконки),
     * ищем в skillSlots соответствующий навык по {@link EclipseItem.SkillClass}
     * и связываем.
     */
    private void restoreHotbarMappingFromInventory(Player player, PlayerSkills skills) {
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack item = player.getInventory().getItem(hotbarSlot);
            if (item == null || item.getType().isAir()) continue;
            if (!item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) continue;

            EclipseItem.SkillClass needClass = switch (item.getType()) {
                case IRON_SWORD -> EclipseItem.SkillClass.MELEE_STRIKE;
                case BOW -> EclipseItem.SkillClass.ARROW_SHOT;
                case BLAZE_ROD -> EclipseItem.SkillClass.FIREBALL;
                default -> null;
            };
            if (needClass == null) continue;

            // Ищем первый skillSlot такого класса, ещё не привязанный к хотбару.
            for (int skillSlot = 0; skillSlot < 5; skillSlot++) {
                EclipseItem skill = skills.skillSlots[skillSlot];
                if (skill == null) continue;
                if (skill.getSkillClass() != needClass) continue;
                if (skills.hotbarMapping.containsValue(skillSlot)) continue;
                skills.hotbarMapping.put(hotbarSlot, skillSlot);
                break;
            }
        }
    }
    
    /**
     * Сохранить навыки в профиль игрока
     */
    public void saveSkillsToProfile(Player player) {
        PlayerSkills skills = playerSkills.get(player.getUniqueId());
        if (skills == null) return;
        
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        if (profile == null) return;
        
        SkillData data = new SkillData();
        
        // Сохраняем навыки
        for (int i = 0; i < 5; i++) {
            if (skills.skillSlots[i] != null) {
                data.setSkill(i, skills.skillSlots[i].getId());
            }
            
            // Сохраняем поддержки
            for (int j = 0; j < 2; j++) {
                if (skills.supportSlots[i][j] != null) {
                    data.setSupport(i, j, skills.supportSlots[i][j].getId());
                }
            }
        }

        // Сохраняем маппинг хотбара (баг 3 — переживание перезахода).
        for (Map.Entry<Integer, Integer> e : skills.hotbarMapping.entrySet()) {
            data.setHotbarMapping(e.getKey(), e.getValue());
        }
        
        // Обновляем профиль
        PlayerProfile updated = profile.toBuilder()
                .equipmentData(data.toJson())
                .build();
        
        plugin.getAPI().updateProfile(player, updated);
    }
    
    /**
     * Вставить навык в слот
     */
    public boolean insertSkill(Player player, int slot, EclipseItem skill) {
        if (slot < 0 || slot >= 5) return false;
        if (skill.getType() != EclipseItem.EclipseType.SKILL_GEM) return false;
        
        PlayerSkills skills = getPlayerSkills(player);
        skills.skillSlots[slot] = skill;
        
        // Создаем иконку в первом свободном слоте хотбара
        // Используем узнаваемые предметы вместо эклипсов
        int hotbarSlot = findFreeHotbarSlot(player);
        if (hotbarSlot != -1) {
            ItemStack icon = createSkillIcon(skill);
            player.getInventory().setItem(hotbarSlot, icon);
            skills.hotbarMapping.put(hotbarSlot, slot);
            
            player.sendMessage("§aНавык §6" + skill.getName() + " §aвставлен в слот хотбара §e" + (hotbarSlot + 1));
            
            // Сохраняем в профиль
            saveSkillsToProfile(player);
            return true;
        } else {
            player.sendMessage("§cНет свободных слотов в хотбаре!");
            return false;
        }
    }
    
    /**
     * Создать иконку навыка для хотбара (без CustomModelData)
     */
    private ItemStack createSkillIcon(EclipseItem skill) {
        // Используем узнаваемые предметы для каждого класса
        Material iconMaterial = switch (skill.getSkillClass()) {
            case MELEE_STRIKE -> Material.IRON_SWORD;
            case ARROW_SHOT -> Material.BOW;
            case FIREBALL -> Material.BLAZE_ROD;
        };
        
        ItemStack icon = new ItemStack(iconMaterial);
        ItemMeta meta = icon.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(skill.toItemStack().getItemMeta().getDisplayName());
            meta.setLore(skill.toItemStack().getItemMeta().getLore());
            // НЕ устанавливаем CustomModelData для иконок в хотбаре
            icon.setItemMeta(meta);
        }
        
        return icon;
    }
    
    /**
     * Вставить поддержку к навыку
     */
    public boolean insertSupport(Player player, int skillSlot, int supportSlot, EclipseItem support) {
        if (skillSlot < 0 || skillSlot >= 5) return false;
        if (supportSlot < 0 || supportSlot >= 2) return false;
        if (support.getType() != EclipseItem.EclipseType.SUPPORT_GEM) return false;
        
        PlayerSkills skills = getPlayerSkills(player);
        EclipseItem skill = skills.skillSlots[skillSlot];
        
        if (skill == null) {
            player.sendMessage("§cСначала вставьте навык в этот слот!");
            return false;
        }
        
        // Проверяем совместимость (пока все поддержки совместимы со всеми навыками)
        skills.supportSlots[skillSlot][supportSlot] = support;
        player.sendMessage("§aПоддержка §d" + support.getName() + " §aдобавлена к навыку §6" + skill.getName());
        
        // Сохраняем в профиль
        saveSkillsToProfile(player);
        return true;
    }
    
    /**
     * Снять поддержку с навыка и вернуть её (для возврата в инвентарь
     * вызывающим кодом — GUI/команды). Если слот пуст, возвращает null
     * и состояние не меняется.
     */
    public EclipseItem removeSupport(Player player, int skillSlot, int supportSlot) {
        if (skillSlot < 0 || skillSlot >= 5) return null;
        if (supportSlot < 0 || supportSlot >= 2) return null;
        PlayerSkills skills = getPlayerSkills(player);
        EclipseItem removed = skills.supportSlots[skillSlot][supportSlot];
        if (removed == null) return null;
        skills.supportSlots[skillSlot][supportSlot] = null;
        saveSkillsToProfile(player);
        return removed;
    }

    /**
     * Удалить навык из слота
     */
    public void removeSkill(Player player, int slot) {
        if (slot < 0 || slot >= 5) return;
        
        PlayerSkills skills = getPlayerSkills(player);
        skills.skillSlots[slot] = null;
        skills.supportSlots[slot][0] = null;
        skills.supportSlots[slot][1] = null;
        
        // Убираем иконку из хотбара
        for (Map.Entry<Integer, Integer> entry : skills.hotbarMapping.entrySet()) {
            if (entry.getValue() == slot) {
                player.getInventory().setItem(entry.getKey(), null);
                skills.hotbarMapping.remove(entry.getKey());
                break;
            }
        }
        
        // Сохраняем в профиль
        saveSkillsToProfile(player);
    }
    
    /**
     * Получить активный навык по слоту хотбара
     */
    public ActiveSkill getActiveSkill(Player player, int hotbarSlot) {
        PlayerSkills skills = getPlayerSkills(player);
        Integer skillSlot = skills.hotbarMapping.get(hotbarSlot);
        
        if (skillSlot == null) return null;
        
        EclipseItem skill = skills.skillSlots[skillSlot];
        if (skill == null) return null;
        
        List<EclipseItem> supports = new ArrayList<>();
        if (skills.supportSlots[skillSlot][0] != null) {
            supports.add(skills.supportSlots[skillSlot][0]);
        }
        if (skills.supportSlots[skillSlot][1] != null) {
            supports.add(skills.supportSlots[skillSlot][1]);
        }
        
        return new ActiveSkill(skill, supports);
    }
    
    /**
     * Найти свободный слот в хотбаре
     */
    private int findFreeHotbarSlot(Player player) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Класс для хранения навыков игрока
     */
    public static class PlayerSkills {
        public final EclipseItem[] skillSlots = new EclipseItem[5];
        public final EclipseItem[][] supportSlots = new EclipseItem[5][2];
        public final Map<Integer, Integer> hotbarMapping = new HashMap<>(); // hotbarSlot -> skillSlot
    }
    
    /**
     * Класс для активного навыка с поддержками
     */
    public static class ActiveSkill {
        private final EclipseItem skill;
        private final List<EclipseItem> supports;
        
        public ActiveSkill(EclipseItem skill, List<EclipseItem> supports) {
            this.skill = skill;
            this.supports = supports;
        }
        
        public EclipseItem getSkill() {
            return skill;
        }
        
        public List<EclipseItem> getSupports() {
            return supports;
        }
        
        public boolean hasSupport(EclipseItem.SupportClass supportClass) {
            return supports.stream().anyMatch(s -> s.getSupportClass() == supportClass);
        }
    }
}
