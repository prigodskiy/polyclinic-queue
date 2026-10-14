package ru.student.clinicqueue.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class HomeVisitTicket extends Ticket implements Editable {
    private final String address;

    public HomeVisitTicket(
            String cardNumber,
            String patientName,
            String room,
            int urgency,
            LocalDateTime takenAt,
            String address
    ) {
        super(cardNumber, patientName, room, urgency, takenAt);
        this.address = Objects.requireNonNull(address, "address must not be null").strip();
    }

    @Override
    public TicketType getType() {
        return TicketType.HOME_VISIT;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public List<String> validate() {
        return TicketValidator.validate(this);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && address.equals(((HomeVisitTicket) other).address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), address);
    }
}
