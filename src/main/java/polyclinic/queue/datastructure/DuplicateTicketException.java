package polyclinic.queue.datastructure;

public class DuplicateTicketException extends RuntimeException {

    private final String cardNumber;

    public DuplicateTicketException(String cardNumber) {
        super("Талон с номером карты '" + cardNumber + "' уже существует в очереди");
        this.cardNumber = cardNumber;
    }

    public String getCardNumber() {
        return cardNumber;
    }
}