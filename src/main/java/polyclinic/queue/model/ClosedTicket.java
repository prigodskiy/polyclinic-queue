package polyclinic.queue.model;

import java.time.LocalDateTime;

public final class ClosedTicket extends Ticket {

    private final LocalDateTime closedAt;

    public ClosedTicket(String cardNumber, String fullName, int office,
                        int urgency, LocalDateTime closedAt) {
        super(cardNumber, fullName, office, urgency);
        if (closedAt == null) {
            throw new IllegalArgumentException("Closed time cannot be null");
        }
        this.closedAt = closedAt;
    }

    ClosedTicket(String cardNumber, String fullName, int office,
                 int urgency, LocalDateTime takenAt, LocalDateTime closedAt) {
        super(cardNumber, fullName, office, urgency, takenAt);
        if (closedAt == null) {
            throw new IllegalArgumentException("Closed time cannot be null");
        }
        this.closedAt = closedAt;
    }

    public static ClosedTicket createWithTime(String cardNumber, String fullName,
                                              int office, int urgency,
                                              LocalDateTime takenAt, LocalDateTime closedAt) {
        return new ClosedTicket(cardNumber, fullName, office, urgency, takenAt, closedAt);
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    @Override
    public String toString() {
        return String.format("ClosedTicket{card='%s', patient='%s', office=%d, urgency=%d, closedAt=%s}",
                getCardNumber(), getFullName(), getOffice(), getUrgency(), closedAt);
    }
}