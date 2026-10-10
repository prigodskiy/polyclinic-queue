package polyclinic.queue.io;

public class InvalidOfficeException extends CsvParseException {
    private final String invalidValue;

    public InvalidOfficeException(int lineNumber, String invalidValue) {
        super(lineNumber, "Некорректный номер кабинета: " + invalidValue);
        this.invalidValue = invalidValue;
    }

    @Override
    public String getErrorCode() {
        return "INVALID_OFFICE";
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}