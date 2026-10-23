package ru.polyclinic.queue.datastructure;

/** Дубликат: талон с таким № карты уже стоит в очереди. */
public class DuplicateTicketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String cardNumber;

    public DuplicateTicketException(String cardNumber) {
        super("Талон с картой " + cardNumber + " уже в очереди");
        this.cardNumber = cardNumber;
    }

    public String getCardNumber() {
        return cardNumber;
    }
}