package polyclinic.queue.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Ticket implements Comparable<Ticket>, Editable {

    private final String cardNumber;
    private final String fullName;
    private final String office;
    private final int urgency;
    private final LocalDateTime takenAt;

    public Ticket(String cardNumber, String fullName, String office, int urgency) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number cannot be empty");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
        if (office == null || office.isBlank()) {
            throw new IllegalArgumentException("Office cannot be empty");
        }
        if (urgency < 0 || urgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }

        this.cardNumber = cardNumber;
        this.fullName = fullName;
        this.office = office;
        this.urgency = urgency;
        this.takenAt = LocalDateTime.now();
    }

    Ticket(String cardNumber, String fullName, String office, int urgency, LocalDateTime takenAt) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number cannot be empty");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
        if (office == null || office.isBlank()) {
            throw new IllegalArgumentException("Office cannot be empty");
        }
        if (urgency < 0 || urgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }
        if (takenAt == null) {
            throw new IllegalArgumentException("TakenAt cannot be null");
        }

        this.cardNumber = cardNumber;
        this.fullName = fullName;
        this.office = office;
        this.urgency = urgency;
        this.takenAt = takenAt;
    }

    public String getCardNumber() { return cardNumber; }
    public String getFullName() { return fullName; }
    public String getOffice() { return office; }
    public int getUrgency() { return urgency; }
    public LocalDateTime getTakenAt() { return takenAt; }

    public Ticket withUrgency(int newUrgency) {
        if (newUrgency < 0 || newUrgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }
        return new Ticket(this.cardNumber, this.fullName, this.office, newUrgency, this.takenAt);
    }

    public Ticket withChanges(String newFullName, String newOffice, Integer newUrgency) {
        String fullName = (newFullName != null) ? newFullName : this.fullName;
        String office = (newOffice != null) ? newOffice : this.office;
        int urgency = (newUrgency != null) ? newUrgency : this.urgency;

        return new Ticket(this.cardNumber, fullName, office, urgency, this.takenAt);
    }

    public static Ticket createWithTime(String cardNumber, String fullName,
                                        String office, int urgency, LocalDateTime takenAt) {
        return new Ticket(cardNumber, fullName, office, urgency, takenAt);
    }

    @Override
    public int compareTo(Ticket other) {
        if (other == null) throw new NullPointerException("Cannot compare with null");
        int urgencyCompare = Integer.compare(this.urgency, other.urgency);
        if (urgencyCompare != 0) return urgencyCompare;
        return this.takenAt.compareTo(other.takenAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return urgency == ticket.urgency
                && Objects.equals(cardNumber, ticket.cardNumber)
                && Objects.equals(fullName, ticket.fullName)
                && Objects.equals(office, ticket.office);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, fullName, office, urgency);
    }

    @Override
    public String toString() {
        return String.format("Ticket{card='%s', patient='%s', office='%s', urgency=%d, time=%s}",
                cardNumber, fullName, office, urgency, takenAt);
    }
}