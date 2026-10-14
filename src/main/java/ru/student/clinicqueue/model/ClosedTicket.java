package ru.student.clinicqueue.model;

import java.time.LocalDateTime;

public final class ClosedTicket extends Ticket {
    public ClosedTicket(String cardNumber, String patientName, String room, int urgency, LocalDateTime takenAt) {
        super(cardNumber, patientName, room, urgency, takenAt);
    }

    @Override
    public TicketType getType() {
        return TicketType.CLOSED;
    }
}
