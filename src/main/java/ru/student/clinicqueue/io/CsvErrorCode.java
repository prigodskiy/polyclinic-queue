package ru.student.clinicqueue.io;

public enum CsvErrorCode {
    IO_ERROR,
    MISSING_HEADER,
    WRONG_FIELD_COUNT,
    MALFORMED_QUOTES,
    UNKNOWN_TYPE,
    BAD_NUMBER,
    BAD_DATE_TIME,
    UNEXPECTED_DATA,
    INVALID_TICKET
}
