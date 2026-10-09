package polyclinic.queue.io;

public class InvalidUrgencyException extends CsvParseException {

    private final int invalidValue;

    public InvalidUrgencyException(int lineNumber, int invalidValue) {
        super(lineNumber, String.format(
                "Срочность должна быть в диапазоне 0-3, получено: %d", invalidValue));
        this.invalidValue = invalidValue;
    }

    @Override
    public String getErrorCode() {
        return "INVALID_URGENCY";
    }

    public int getInvalidValue() {
        return invalidValue;
    }
}