package ru.polyclinic.queue.io;

public record CsvRowError(int lineNumber, CsvErrorCode code, String message) {
    @Override
    public String toString() {
        return "строка %d [%s]: %s".formatted(lineNumber, code, message);
    }
}
