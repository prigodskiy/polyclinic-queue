package ru.polyclinic.queue.datastructure;

public class DuplicateTicketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String cardNumber;

    public DuplicateTicketException(String cardNumber) {
        super("талон с картой " + cardNumber + " уже в очереди");
        this.cardNumber = cardNumber;
    }

    public String getCardNumber() {
        return cardNumber;
    }
}