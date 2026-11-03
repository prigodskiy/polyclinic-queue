package ru.polyclinic.queue.io;

/**
 * Коды ошибок при работе с CSV.
 */
public enum CsvErrorCode {
    FILE_NOT_FOUND("Файл не найден"),
    IO_ERROR("Ошибка ввода-вывода"),
    PARSE_ERROR("Ошибка разбора строки"),
    DUPLICATE_CARD("Дубликат номера карты"),
    CANCELLED("Операция отменена");  // ← НОВОЕ

    private final String description;

    CsvErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + ": " + description;
    }
}