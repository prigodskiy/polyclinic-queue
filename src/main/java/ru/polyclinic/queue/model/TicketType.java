package ru.polyclinic.queue.model;

public enum TicketType {
    REGULAR("Обычный"),
    HOME_VISIT("На дому"),
    CLOSED("Закрытый");

    private final String displayName;

    TicketType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    // НОВОЕ: Поиск типа талона по строке (понимает и "REGULAR", и "Обычный")
    public static TicketType fromString(String text) {
        for (TicketType type : TicketType.values()) {
            if (type.name().equalsIgnoreCase(text) || type.displayName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип талона: " + text);
    }
}