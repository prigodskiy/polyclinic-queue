package polyclinic.queue.model;

import java.time.LocalDateTime;

public final class ClosedHomeVisitTicket extends HomeVisitTicket {

    private final LocalDateTime closedAt;

    public ClosedHomeVisitTicket(String cardNumber, String fullName, String office,
                                 int urgency, String address, LocalDateTime closedAt) {
        super(cardNumber, fullName, office, urgency, address);
        if (closedAt == null) {
            throw new IllegalArgumentException("Closed time cannot be null");
        }
        this.closedAt = closedAt;
    }

    public static ClosedHomeVisitTicket createClosed(String cardNumber, String fullName,
                                                     String office, int urgency,
                                                     String address, LocalDateTime takenAt) {
        return new ClosedHomeVisitTicket(cardNumber, fullName, office, urgency, address,
                takenAt, LocalDateTime.now());
    }

    ClosedHomeVisitTicket(String cardNumber, String fullName, String office,
                          int urgency, String address, LocalDateTime takenAt, LocalDateTime closedAt) {
        super(cardNumber, fullName, office, urgency, address, takenAt);
        if (closedAt == null) {
            throw new IllegalArgumentException("Closed time cannot be null");
        }
        this.closedAt = closedAt;
    }

    public static ClosedHomeVisitTicket createWithTime(String cardNumber, String fullName,
                                                       String office, int urgency,
                                                       String address, LocalDateTime takenAt,
                                                       LocalDateTime closedAt) {
        return new ClosedHomeVisitTicket(cardNumber, fullName, office, urgency, address, takenAt, closedAt);
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    @Override
    public String toString() {
        return String.format("ClosedHomeVisitTicket{card='%s', patient='%s', office='%s', urgency=%d, address='%s', closedAt=%s}",
                getCardNumber(), getFullName(), getOffice(), getUrgency(), getAddress(), closedAt);
    }
}