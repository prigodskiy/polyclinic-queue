package ru.polyclinic.queue.io;

/** Коды ошибок загрузки CSV — один enum вместо десятка классов-исключений. */
public enum CsvErrorCode {
    IO_ERROR,
    MISSING_HEADER,
    WRONG_FIELD_COUNT,
    MALFORMED_QUOTES,
    UNKNOWN_TYPE,
    BAD_NUMBER,
    BAD_DATE_TIME,
    MISSING_ADDRESS,
    INVALID_TICKET
}