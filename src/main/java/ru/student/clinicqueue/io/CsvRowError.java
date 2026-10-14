package ru.student.clinicqueue.io;

public record CsvRowError(int lineNumber, CsvErrorCode code, String message) {
    @Override
    public String toString() {
        return "Строка %d [%s]: %s".formatted(lineNumber, code, message);
    }
}
