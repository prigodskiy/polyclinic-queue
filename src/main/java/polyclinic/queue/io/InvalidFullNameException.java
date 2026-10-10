package polyclinic.queue.io;

public class InvalidFullNameException extends CsvParseException {
    private final String invalidValue;

    public InvalidFullNameException(int lineNumber, String invalidValue) {
        super(lineNumber, "Некорректное ФИО: " + invalidValue);
        this.invalidValue = invalidValue;
    }

    @Override
    public String getErrorCode() {
        return "INVALID_FULL_NAME";
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}