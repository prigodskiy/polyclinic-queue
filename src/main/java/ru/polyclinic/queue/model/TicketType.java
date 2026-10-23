package ru.polyclinic.queue.model;

/** Тип талона — единое имя для CSV, таблицы GUI и switch'ей. Без магических строк. */
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
}