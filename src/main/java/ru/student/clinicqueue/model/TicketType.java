package ru.student.clinicqueue.model;

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
