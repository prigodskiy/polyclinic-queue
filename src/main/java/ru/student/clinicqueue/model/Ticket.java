package ru.student.clinicqueue.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public abstract class Ticket {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final String cardNumber;
    private final String patientName;
    private final String room;
    private final int urgency;
    private final LocalDateTime takenAt;

    protected Ticket(String cardNumber, String patientName, String room, int urgency, LocalDateTime takenAt) {
        this.cardNumber = normalize(cardNumber, "cardNumber");
        this.patientName = normalize(patientName, "patientName");
        this.room = normalize(room, "room");
        this.urgency = urgency;
        this.takenAt = takenAt;
    }

    public abstract TicketType getType();

    public String getCardNumber() {
        return cardNumber;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getRoom() {
        return room;
    }

    public int getUrgency() {
        return urgency;
    }

    public String getUrgencyText() {
        return Integer.toString(urgency);
    }

    public LocalDateTime getTakenAt() {
        return takenAt;
    }

    public String getTakenAtText() {
        return takenAt == null ? "" : DISPLAY_TIME.format(takenAt);
    }

    private static String normalize(String value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null").strip();
    }

    @Override
    public String toString() {
        return "%s: %s, кабинет %s".formatted(getType().getDisplayName(), patientName, room);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Ticket ticket = (Ticket) other;
        return urgency == ticket.urgency
                && cardNumber.equals(ticket.cardNumber)
                && patientName.equals(ticket.patientName)
                && room.equals(ticket.room)
                && Objects.equals(takenAt, ticket.takenAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, patientName, room, urgency, takenAt);
    }
}
