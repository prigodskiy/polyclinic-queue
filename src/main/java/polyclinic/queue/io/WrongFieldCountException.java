package polyclinic.queue.io;

public class WrongFieldCountException extends CsvParseException {

    private final int expectedCount;
    private final int actualCount;

    public WrongFieldCountException(int lineNumber, int expectedCount, int actualCount) {
        super(lineNumber, String.format(
                "Ожидается %d полей, найдено %d", expectedCount, actualCount));
        this.expectedCount = expectedCount;
        this.actualCount = actualCount;
    }

    @Override
    public String getErrorCode() {
        return "WRONG_FIELD_COUNT";
    }

    public int getExpectedCount() {
        return expectedCount;
    }

    public int getActualCount() {
        return actualCount;
    }
}