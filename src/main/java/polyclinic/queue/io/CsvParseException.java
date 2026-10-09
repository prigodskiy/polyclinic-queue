package polyclinic.queue.io;

public class CsvParseException extends Exception {

    private final int lineNumber;

    public CsvParseException(int lineNumber, String message) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getErrorCode() {
        return "CSV_PARSE_ERROR";
    }
}