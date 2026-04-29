package ru.eclipsia.items.item;

/**
 * Слот экипировки предмета
 */
public enum ItemSlot {
    HAND("Оружие"),
    OFFHAND("Доп. оружие"),
    HEAD("Шлем"),
    CHEST("Нагрудник"),
    LEGS("Штаны"),
    FEET("Ботинки"),
    RING_1("Кольцо 1"),
    RING_2("Кольцо 2"),
    AMULET("Амулет"),
    BELT("Пояс");
    
    private final String displayName;
    
    ItemSlot(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Получить слот по названию
     */
    public static ItemSlot fromString(String name) {
        for (ItemSlot slot : values()) {
            if (slot.name().equalsIgnoreCase(name)) {
                return slot;
            }
        }
        return HAND;
    }
    
    /**
     * Является ли слот кольцом
     */
    public boolean isRing() {
        return this == RING_1 || this == RING_2;
    }
}
