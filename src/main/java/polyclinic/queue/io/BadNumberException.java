package polyclinic.queue.io;

public class BadNumberException extends CsvParseException {

    private final String fieldName;
    private final String invalidValue;

    public BadNumberException(int lineNumber, String fieldName, String invalidValue) {
        super(lineNumber, String.format(
                "Неверный формат числа в поле '%s': '%s'", fieldName, invalidValue));
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
    }

    @Override
    public String getErrorCode() {
        return "BAD_NUMBER";
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}