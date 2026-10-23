package ru.polyclinic.queue.io;

import java.io.Serial;

/** Ошибка чтения/записи CSV с кодом (уровень «максимум» лабы 1). */
public final class TicketCsvException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CsvErrorCode code;

    public TicketCsvException(CsvErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public TicketCsvException(CsvErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public CsvErrorCode getCode() {
        return code;
    }
}
