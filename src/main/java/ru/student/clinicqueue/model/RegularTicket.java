package ru.student.clinicqueue.model;

import java.time.LocalDateTime;
import java.util.List;

public final class RegularTicket extends Ticket implements Editable {
    public RegularTicket(String cardNumber, String patientName, String room, int urgency, LocalDateTime takenAt) {
        super(cardNumber, patientName, room, urgency, takenAt);
    }

    @Override
    public TicketType getType() {
        return TicketType.REGULAR;
    }

    @Override
    public List<String> validate() {
        return TicketValidator.validate(this);
    }
}
