package ru.eclipsia.skills.eclipse;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс для эклипс-навыков (активные навыки и поддержки)
 */
public class EclipseItem {

    /**
     * Ключ PersistentDataContainer, в котором хранится id эклипса в ItemStack.
     * Позволяет надёжно опознавать эклипс независимо от лора/имени.
     * Ленивая инициализация: NamespacedKey требует загруженный плагин,
     * в тестах/инициализации может не быть Bukkit.
     */
    private static NamespacedKey eclipseIdKey;

    private static NamespacedKey eclipseIdKey() {
        if (eclipseIdKey == null) {
            eclipseIdKey = new NamespacedKey("eclipsia", "eclipse_id");
        }
        return eclipseIdKey;
    }

    private final String id;
    private final String name;
    private final EclipseType type;
    private final SkillClass skillClass;
    private final SupportClass supportClass;
    private final int level;
    private final int manaCost;
    private final int cooldownTicks;
    private final double baseDamage;
    private final List<String> description;
    
    private EclipseItem(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.type = builder.type;
        this.skillClass = builder.skillClass;
        this.supportClass = builder.supportClass;
        this.level = builder.level;
        this.manaCost = builder.manaCost;
        this.cooldownTicks = builder.cooldownTicks;
        this.baseDamage = builder.baseDamage;
        this.description = new ArrayList<>(builder.description);
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public EclipseType getType() { return type; }
    public SkillClass getSkillClass() { return skillClass; }
    public SupportClass getSupportClass() { return supportClass; }
    public int getLevel() { return level; }
    public int getManaCost() { return manaCost; }
    public int getCooldownTicks() { return cooldownTicks; }
    public double getBaseDamage() { return baseDamage; }
    public List<String> getDescription() { return new ArrayList<>(description); }
    
    /**
     * Создать ItemStack из эклипса
     */
    public ItemStack toItemStack() {
        Material material = type == EclipseType.SKILL_GEM ? Material.EMERALD : Material.AMETHYST_SHARD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Название с цветом
            String displayName = type == EclipseType.SKILL_GEM 
                ? getSkillClassColor() + name
                : "§d" + name;
            meta.setDisplayName(displayName);
            
            // Лор
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Тип: §f" + (type == EclipseType.SKILL_GEM ? "Навык" : "Поддержка"));
            lore.add("§7Уровень: §e" + level);
            lore.add("");
            
            // Описание
            for (String line : description) {
                lore.add("§7" + line);
            }
            
            lore.add("");
            
            if (type == EclipseType.SKILL_GEM) {
                lore.add("§7Стоимость маны: §9" + manaCost);
                lore.add("§7Кулдаун: §e" + (cooldownTicks / 20.0) + "с");
                lore.add("§7Базовый урон: §c" + baseDamage);
            }
            
            meta.setLore(lore);
            
            // CustomModelData для ресурс-пака
            if (type == EclipseType.SKILL_GEM) {
                int customModelData = switch (skillClass) {
                    case MELEE_STRIKE -> 1;
                    case ARROW_SHOT -> 2;
                    case FIREBALL -> 3;
                };
                meta.setCustomModelData(customModelData);
            } else {
                int customModelData = switch (supportClass) {
                    case AOE_RADIUS -> 10;
                    case MULTI_SHOT -> 11;
                    case EXPLOSION -> 12;
                };
                meta.setCustomModelData(customModelData);
            }

            // Сохраняем id в PDC, чтобы любой плагин (например, EclipsiaItems
            // в EquipmentGUI) мог надёжно восстановить EclipseItem из ItemStack
            // через {@link #fromItemStack(ItemStack)}.
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(eclipseIdKey(), PersistentDataType.STRING, id);

            item.setItemMeta(meta);
        }
        
        return item;
    }

    /**
     * Восстановить EclipseItem из ItemStack, ранее созданного через
     * {@link #toItemStack()}. Возвращает null, если предмет не является
     * эклипсом или его id не зарегистрирован в {@link #fromId(String)}.
     */
    public static EclipseItem fromItemStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(eclipseIdKey(), PersistentDataType.STRING);
        if (id == null) return null;
        return fromId(id);
    }
    
    /**
     * Получить цвет класса навыка
     */
    private String getSkillClassColor() {
        if (skillClass == null) return "§f";
        return switch (skillClass) {
            case MELEE_STRIKE -> "§c";
            case ARROW_SHOT -> "§a";
            case FIREBALL -> "§9";
        };
    }
    
    /**
     * Типы эклипсов
     */
    public enum EclipseType {
        SKILL_GEM,    // Активный навык
        SUPPORT_GEM   // Поддержка
    }
    
    /**
     * Классы навыков
     */
    public enum SkillClass {
        MELEE_STRIKE,  // Удар в ближнем бою
        ARROW_SHOT,    // Выстрел из лука
        FIREBALL       // Огненный шар
    }
    
    /**
     * Классы поддержек
     */
    public enum SupportClass {
        AOE_RADIUS,    // Увеличение радиуса
        MULTI_SHOT,    // Множественные снаряды
        EXPLOSION      // Взрыв при попадании
    }
    
    /**
     * Builder для создания эклипсов
     */
    public static class Builder {
        private String id;
        private String name;
        private EclipseType type;
        private SkillClass skillClass;
        private SupportClass supportClass;
        private int level = 1;
        private int manaCost = 0;
        private int cooldownTicks = 0;
        private double baseDamage = 0;
        private List<String> description = new ArrayList<>();
        
        public Builder(String id, String name, EclipseType type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
        
        public Builder skillClass(SkillClass skillClass) {
            this.skillClass = skillClass;
            return this;
        }
        
        public Builder supportClass(SupportClass supportClass) {
            this.supportClass = supportClass;
            return this;
        }
        
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        
        public Builder manaCost(int manaCost) {
            this.manaCost = manaCost;
            return this;
        }
        
        public Builder cooldownTicks(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }
        
        public Builder baseDamage(double baseDamage) {
            this.baseDamage = baseDamage;
            return this;
        }
        
        public Builder description(String... lines) {
            this.description = List.of(lines);
            return this;
        }
        
        public EclipseItem build() {
            return new EclipseItem(this);
        }
    }
    
    /**
     * Создать стартовые навыки для классов
     */
    public static EclipseItem createStarterSkill(String className) {
        return switch (className.toLowerCase()) {
            case "warrior" -> fromId("melee_strike_1");
            case "archer"  -> fromId("arrow_shot_1");
            case "mage"    -> fromId("fireball_1");
            default        -> null;
        };
    }

    /**
     * Восстановить эклипс по его id. Реестр содержит все навыки и поддержки,
     * которые могут быть сохранены в профиле игрока. Если новый эклипс
     * добавляется в игру — он должен быть зарегистрирован здесь, иначе
     * SkillManager не сможет восстановить его при загрузке профиля.
     *
     * @return EclipseItem или null если id неизвестен
     */
    public static EclipseItem fromId(String id) {
        if (id == null) return null;
        return switch (id) {
            // ===== SKILL GEMS =====
            case "melee_strike_1", "melee_strike_test" -> new Builder(id, "Удар мечом", EclipseType.SKILL_GEM)
                    .skillClass(SkillClass.MELEE_STRIKE)
                    .level(1).manaCost(10).cooldownTicks(40).baseDamage(15.0)
                    .description("Мощный удар мечом", "по врагам перед собой")
                    .build();

            case "arrow_shot_1", "arrow_shot_test" -> new Builder(id, "Выстрел", EclipseType.SKILL_GEM)
                    .skillClass(SkillClass.ARROW_SHOT)
                    .level(1).manaCost(8).cooldownTicks(20).baseDamage(12.0)
                    .description("Быстрый выстрел", "из лука")
                    .build();

            case "fireball_1", "fireball_test" -> new Builder(id, "Огненный шар", EclipseType.SKILL_GEM)
                    .skillClass(SkillClass.FIREBALL)
                    .level(1).manaCost(15).cooldownTicks(60).baseDamage(20.0)
                    .description("Запускает огненный шар", "во врагов")
                    .build();

            // ===== SUPPORT GEMS =====
            case "aoe_test", "aoe_radius_1" -> new Builder(id, "Радиус AOE", EclipseType.SUPPORT_GEM)
                    .supportClass(SupportClass.AOE_RADIUS).level(1)
                    .description("Увеличивает радиус действия")
                    .build();

            case "multishot_test", "multi_shot_1" -> new Builder(id, "Мультивыстрел", EclipseType.SUPPORT_GEM)
                    .supportClass(SupportClass.MULTI_SHOT).level(1)
                    .description("Выпускает несколько снарядов")
                    .build();

            case "explosion_test", "explosion_1" -> new Builder(id, "Взрыв", EclipseType.SUPPORT_GEM)
                    .supportClass(SupportClass.EXPLOSION).level(1)
                    .description("Добавляет взрыв при попадании")
                    .build();

            default -> null;
        };
    }
}
