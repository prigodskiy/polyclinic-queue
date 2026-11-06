package ru.polyclinic.queue.model;

import java.time.LocalDateTime;

public final class ClosedTicket extends Ticket implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String address;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ClosedTicket(
            @com.fasterxml.jackson.annotation.JsonProperty("id") long id,
            @com.fasterxml.jackson.annotation.JsonProperty("cardNumber") String cardNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("fullName") String fullName,
            @com.fasterxml.jackson.annotation.JsonProperty("office") String office,
            @com.fasterxml.jackson.annotation.JsonProperty("urgency") int urgency,
            @com.fasterxml.jackson.annotation.JsonProperty("takenAt") LocalDateTime takenAt,
            @com.fasterxml.jackson.annotation.JsonProperty("address") String address) {
        super(id, cardNumber, fullName, office, urgency, takenAt);
        this.address = (address == null || address.isBlank()) ? null : address.strip();
    }

    @Override
    public TicketType getType() {
        return TicketType.CLOSED;
    }

    public String getAddressOrNull() {
        return address;
    }

    public boolean isHomeVisit() {
        return address != null;
    }

    @Override
    public String toString() {
        return "ClosedTicket{card='%s', patient='%s', office='%s', urgency=%d, address='%s', time=%s}"
                .formatted(getCardNumber(), getFullName(), getOffice(),
                        getUrgency(), address == null ? "" : address, getTakenAt());
    }
}